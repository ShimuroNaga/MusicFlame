package com.music.musicflame.together

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import com.music.musicflame.data.MusicPlayerManager
import com.music.musicflame.data.Song
import com.music.musicflame.data.SongLibraryHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.security.SecureRandom
import kotlin.math.abs

/**
 * Modo "En compañía": varias personas escuchan lo mismo a la vez.
 *
 * Cómo funciona (v1):
 *  - Firebase Realtime Database solo guarda ESTADO (qué suena, play/pausa, posición).
 *    El audio NO viaja por internet: cada persona reproduce el archivo desde su propia
 *    biblioteca. Si alguien no tiene esa canción, se le avisa cuál es.
 *  - El anfitrión (quien crea la sala) publica su estado; los demás lo siguen.
 *  - Los relojes se alinean con .info/serverTimeOffset para compensar la latencia.
 *
 * Estructura en la base de datos:
 *   rooms/{code}/meta     { hostUid, hostName, createdAt }
 *   rooms/{code}/members/{uid} { name, online, joinedAt }
 *   rooms/{code}/state    { title, artist, duration, isPlaying, positionMs, updatedAt }
 */
class TogetherManager(
    context: Context,
    private val playerManager: MusicPlayerManager
) {
    enum class Status { IDLE, CONNECTING, CONNECTED, DISCONNECTED, ERROR }

    data class Member(val uid: String, val name: String, val online: Boolean, val isHost: Boolean)

    private data class HostState(
        val title: String,
        val artist: String,
        val duration: Long,
        val isPlaying: Boolean,
        val positionMs: Long,
        val updatedAt: Long
    ) {
        val key: String get() = songKey(title, artist)
    }

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("together_prefs", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val db: FirebaseDatabase by lazy { FirebaseDatabase.getInstance(DB_URL) }
    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()

    // ---------- Estado observable por la UI ----------
    var userName by mutableStateOf(prefs.getString(KEY_NAME, null) ?: defaultName())
        private set
    var roomCode by mutableStateOf<String?>(prefs.getString(KEY_ROOM, null))
        private set
    var status by mutableStateOf(Status.IDLE)
        private set
    var isHost by mutableStateOf(prefs.getBoolean(KEY_IS_HOST, false))
        private set
    var statusDetail by mutableStateOf("")
        private set
    /** Texto "Título – Artista" de lo que suena en la sala (para mostrar a los invitados). */
    var nowPlayingText by mutableStateOf<String?>(null)
        private set
    /** Si no tienes la canción del anfitrión, aquí va su nombre. */
    var missingSongText by mutableStateOf<String?>(null)
        private set
    val members = mutableStateListOf<Member>()
    private val _busy = mutableStateOf(false)
    val busy: State<Boolean> = _busy

    /** Código llegado por enlace musicflame://sala/CODIGO. MainActivity lo deja aquí. */
    var pendingDeepLinkCode by mutableStateOf<String?>(null)

    /** La UI lo asigna para mostrar Toasts. */
    var onToast: ((String) -> Unit)? = null

    val roomLink: String? get() = roomCode?.let { "musicflame://sala/$it" }
    val onlineCount: Int get() = members.count { it.online }

    // ---------- Internos ----------
    private var serverOffset = 0L
    private var offsetListener: ValueEventListener? = null
    private var metaRef: DatabaseReference? = null
    private var membersRef: DatabaseReference? = null
    private var stateRef: DatabaseReference? = null
    private var metaListener: ValueEventListener? = null
    private var membersListener: ValueEventListener? = null
    private var stateListener: ValueEventListener? = null
    private var publishJobs = mutableListOf<Job>()
    private var hostUid: String? = null
    private var lastHostPlaying: Boolean? = null
    private var lastRequestedKey: String? = null
    private var lastRequestedAt = 0L

    init {
        // Si quedó una sala guardada de la sesión anterior, se ofrece reconectar (no se une sola).
        if (roomCode != null) { status = Status.DISCONNECTED; statusDetail = "Sala guardada. Pulsa Conectar para volver." }
        // Escucha el desfase con el reloj del servidor (una sola vez).
        try {
            offsetListener = object : ValueEventListener {
                override fun onDataChange(s: DataSnapshot) { serverOffset = s.getValue(Long::class.java) ?: 0L }
                override fun onCancelled(e: DatabaseError) {}
            }
            db.getReference(".info/serverTimeOffset").addValueEventListener(offsetListener!!)
        } catch (t: Throwable) {
            Log.e(TAG, "Firebase no disponible", t)
        }
    }

    private fun serverNow() = System.currentTimeMillis() + serverOffset

    private fun toast(msg: String) { onToast?.invoke(msg) }

    // ================= API pública =================

    fun changeUserName(name: String) {
        userName = name.take(24)
        prefs.edit().putString(KEY_NAME, userName).apply()
        val code = roomCode ?: return
        val uid = auth.currentUser?.uid ?: return
        if (status == Status.CONNECTED) db.getReference("rooms/$code/members/$uid/name").setValue(userName.ifBlank { "Invitado" })
    }

    /** "+ Crear sala". */
    fun createRoom() {
        if (_busy.value) return
        _busy.value = true
        scope.launch {
            try {
                if (roomCode != null) leaveInternal(removeRemote = true)
                status = Status.CONNECTING; statusDetail = "Creando sala…"
                val user = ensureAuth()
                var created: String? = null
                repeat(5) {
                    if (created != null) return@repeat
                    val code = generateCode()
                    try {
                        db.getReference("rooms/$code/meta").setValue(
                            mapOf(
                                "hostUid" to user.uid,
                                "hostName" to displayName(),
                                "createdAt" to ServerValue.TIMESTAMP
                            )
                        ).await()
                        created = code
                    } catch (t: Throwable) {
                        Log.w(TAG, "No se pudo usar el código $code", t)
                    }
                }
                val code = created ?: throw IllegalStateException(
                    "No se pudo crear la sala. Revisa que el inicio de sesión de Firebase y las reglas estén activos."
                )
                hostUid = user.uid
                persistRoom(code, true)
                attach(code, user.uid)
                toast("Sala creada: $code")
            } catch (t: Throwable) {
                fail(t)
            } finally {
                _busy.value = false
            }
        }
    }

    /** Unirse pegando el código (o el enlace completo). */
    fun joinRoom(input: String) {
        val code = parseCode(input)
        if (code.length < 4) { toast("Pega un código válido"); return }
        if (_busy.value) return
        _busy.value = true
        scope.launch {
            try {
                if (roomCode != null && roomCode != code) leaveInternal(removeRemote = true)
                status = Status.CONNECTING; statusDetail = "Entrando a la sala…"
                val user = ensureAuth()
                val meta = db.getReference("rooms/$code/meta").get().await()
                if (!meta.exists()) {
                    status = Status.IDLE; statusDetail = ""
                    toast("No existe una sala con ese código")
                    return@launch
                }
                hostUid = meta.child("hostUid").getValue(String::class.java)
                persistRoom(code, hostUid == user.uid)
                attach(code, user.uid)
                toast("Conectado a la sala $code")
            } catch (t: Throwable) {
                fail(t)
            } finally {
                _busy.value = false
            }
        }
    }

    /** Botón Conectar (después de haberte desconectado, o al abrir la app con sala guardada). */
    fun connect() {
        val code = roomCode ?: return
        if (_busy.value || status == Status.CONNECTED) return
        _busy.value = true
        scope.launch {
            try {
                status = Status.CONNECTING; statusDetail = "Conectando…"
                val user = ensureAuth()
                val meta = db.getReference("rooms/$code/meta").get().await()
                if (!meta.exists()) {
                    toast("La sala ya no existe")
                    leaveInternal(removeRemote = false)
                    return@launch
                }
                hostUid = meta.child("hostUid").getValue(String::class.java)
                isHost = hostUid == user.uid
                attach(code, user.uid)
            } catch (t: Throwable) {
                fail(t)
            } finally {
                _busy.value = false
            }
        }
    }

    /** Botón Desconectar: dejas de sincronizar, pero conservas la sala para volver. */
    fun disconnect() {
        val code = roomCode ?: return
        val uid = auth.currentUser?.uid
        detach()
        if (uid != null) {
            try { db.getReference("rooms/$code/members/$uid/online").setValue(false) } catch (_: Throwable) {}
        }
        status = Status.DISCONNECTED
        statusDetail = "Desconectado. Pulsa Conectar para volver."
        nowPlayingText = null; missingSongText = null
    }

    /** Botón Reconectar: reinicia la conexión con Firebase y vuelve a sincronizar. */
    fun reconnect() {
        val code = roomCode ?: return
        if (_busy.value) return
        _busy.value = true
        scope.launch {
            try {
                status = Status.CONNECTING; statusDetail = "Reconectando…"
                detach()
                db.goOffline(); delay(400); db.goOnline()
                val user = ensureAuth()
                val meta = db.getReference("rooms/$code/meta").get().await()
                if (!meta.exists()) {
                    toast("La sala ya no existe")
                    leaveInternal(removeRemote = false)
                    return@launch
                }
                hostUid = meta.child("hostUid").getValue(String::class.java)
                isHost = hostUid == user.uid
                lastHostPlaying = null
                attach(code, user.uid)
                toast("Reconectado")
            } catch (t: Throwable) {
                fail(t)
            } finally {
                _busy.value = false
            }
        }
    }

    /** Salir de la sala del todo. Si eres anfitrión, la sala se cierra para todos. */
    fun leave() {
        scope.launch { leaveInternal(removeRemote = true) }
    }

    fun release() {
        detach()
        offsetListener?.let { try { db.getReference(".info/serverTimeOffset").removeEventListener(it) } catch (_: Throwable) {} }
        scope.cancel()
    }

    // ================= Conexión y listeners =================

    private fun attach(code: String, uid: String) {
        detach()
        roomCode = code
        isHost = hostUid == uid
        lastHostPlaying = null

        // 1) Presencia: me registro como miembro y marco "offline" si se cae la conexión.
        val myRef = db.getReference("rooms/$code/members/$uid")
        myRef.onDisconnect().updateChildren(mapOf("online" to false))
        myRef.updateChildren(
            mapOf(
                "name" to displayName(),
                "online" to true,
                "joinedAt" to ServerValue.TIMESTAMP
            )
        ).addOnFailureListener {
            fail(it, "No se pudo entrar (¿sala llena? máximo $MAX_MEMBERS personas)")
        }

        // 2) Meta: si desaparece, el anfitrión cerró la sala.
        metaRef = db.getReference("rooms/$code/meta").also { ref ->
            metaListener = object : ValueEventListener {
                override fun onDataChange(s: DataSnapshot) {
                    if (!s.exists() && status == Status.CONNECTED) {
                        scope.launch {
                            toast("El anfitrión cerró la sala")
                            leaveInternal(removeRemote = false)
                        }
                    }
                }
                override fun onCancelled(e: DatabaseError) {}
            }
            ref.addValueEventListener(metaListener!!)
        }

        // 3) Miembros.
        membersRef = db.getReference("rooms/$code/members").also { ref ->
            membersListener = object : ValueEventListener {
                override fun onDataChange(s: DataSnapshot) {
                    val list = s.children.mapNotNull { c ->
                        val id = c.key ?: return@mapNotNull null
                        Member(
                            uid = id,
                            name = c.child("name").getValue(String::class.java) ?: "Invitado",
                            online = c.child("online").getValue(Boolean::class.java) ?: false,
                            isHost = id == hostUid
                        )
                    }.sortedWith(compareByDescending<Member> { it.isHost }.thenBy { it.name.lowercase() })
                    members.clear(); members.addAll(list)
                    if (status == Status.CONNECTING) {
                        status = Status.CONNECTED; statusDetail = ""
                    }
                }
                override fun onCancelled(e: DatabaseError) {
                    fail(e.toException(), "Sin permiso para leer la sala")
                }
            }
            ref.addValueEventListener(membersListener!!)
        }

        // 4) Estado de reproducción.
        stateRef = db.getReference("rooms/$code/state")
        if (isHost) startPublishing() else {
            stateListener = object : ValueEventListener {
                override fun onDataChange(s: DataSnapshot) {
                    val st = parseState(s) ?: return
                    scope.launch { applyHostState(st) }
                }
                override fun onCancelled(e: DatabaseError) {
                    Log.w(TAG, "state cancelled: ${e.message}")
                }
            }
            stateRef!!.addValueEventListener(stateListener!!)
        }
    }

    private fun detach() {
        publishJobs.forEach { it.cancel() }; publishJobs.clear()
        metaListener?.let { metaRef?.removeEventListener(it) }
        membersListener?.let { membersRef?.removeEventListener(it) }
        stateListener?.let { stateRef?.removeEventListener(it) }
        metaListener = null; membersListener = null; stateListener = null
        metaRef = null; membersRef = null; stateRef = null
    }

    private suspend fun leaveInternal(removeRemote: Boolean) {
        val code = roomCode
        val uid = auth.currentUser?.uid
        detach()
        if (removeRemote && code != null && uid != null) {
            try {
                if (isHost) db.getReference("rooms/$code").removeValue().await()
                else db.getReference("rooms/$code/members/$uid").removeValue().await()
            } catch (t: Throwable) {
                Log.w(TAG, "No se pudo limpiar la sala", t)
            }
        }
        roomCode = null; isHost = false; hostUid = null
        members.clear()
        nowPlayingText = null; missingSongText = null
        status = Status.IDLE; statusDetail = ""
        prefs.edit().remove(KEY_ROOM).remove(KEY_IS_HOST).apply()
    }

    // ================= Anfitrión: publicar =================

    private fun startPublishing() {
        // Publica al instante cuando cambia la canción o play/pausa…
        publishJobs += scope.launch {
            snapshotFlow {
                val s = playerManager.currentSong.value
                Pair(s?.let { songKey(it.title, it.artist) }, playerManager.isPlayingState.value)
            }.collect { publishNow() }
        }
        // …y cada 3 s para que los demás corrijan el desfase (y detecten saltos de barra).
        publishJobs += scope.launch {
            while (isActive) {
                delay(PUBLISH_EVERY_MS)
                if (playerManager.isPlayingState.value) publishNow()
            }
        }
    }

    private fun publishNow() {
        val ref = stateRef ?: return
        val song = playerManager.currentSong.value ?: return
        ref.setValue(
            mapOf(
                "title" to song.title,
                "artist" to song.artist,
                "duration" to playerManager.duration.coerceAtLeast(0L),
                "isPlaying" to playerManager.isPlayingState.value,
                "positionMs" to playerManager.currentPosition,
                "updatedAt" to ServerValue.TIMESTAMP
            )
        )
    }

    // ================= Invitado: seguir al anfitrión =================

    private fun parseState(s: DataSnapshot): HostState? {
        if (!s.exists()) return null
        val title = s.child("title").getValue(String::class.java) ?: return null
        return HostState(
            title = title,
            artist = s.child("artist").getValue(String::class.java) ?: "",
            duration = s.child("duration").getValue(Long::class.java) ?: 0L,
            isPlaying = s.child("isPlaying").getValue(Boolean::class.java) ?: false,
            positionMs = s.child("positionMs").getValue(Long::class.java) ?: 0L,
            updatedAt = s.child("updatedAt").getValue(Long::class.java) ?: serverNow()
        )
    }

    private fun expectedPosition(st: HostState): Long {
        val elapsed = if (st.isPlaying) (serverNow() - st.updatedAt).coerceAtLeast(0L) else 0L
        return (st.positionMs + elapsed).coerceAtLeast(0L)
    }

    private suspend fun applyHostState(st: HostState) {
        nowPlayingText = if (st.artist.isBlank()) st.title else "${st.title} – ${st.artist}"
        val local = playerManager.currentSong.value
        val sameSong = local != null && songKey(local.title, local.artist) == st.key

        if (!sameSong) {
            // Evita lanzar playSong() varias veces seguidas mientras el reproductor carga.
            val now = SystemClock.elapsedRealtime()
            if (lastRequestedKey == st.key && now - lastRequestedAt < 4000) return
            val mine = findLocalSong(st)
            if (mine == null) {
                missingSongText = nowPlayingText
                return
            }
            missingSongText = null
            lastRequestedKey = st.key; lastRequestedAt = now
            playerManager.playSong(mine, listOf(mine))
            delay(900) // damos tiempo a que el reproductor cargue antes de saltar a la posición
            playerManager.seekTo(expectedPosition(st))
            if (!st.isPlaying) playerManager.pause()
            lastHostPlaying = st.isPlaying
            return
        }

        missingSongText = null
        val playing = playerManager.isPlayingState.value

        // Cambió play/pausa en el anfitrión → alineamos. (Si TÚ pausas a mano, no te forzamos a
        // seguir hasta que el anfitrión cambie play/pausa o la canción.)
        if (st.isPlaying != lastHostPlaying) {
            if (st.isPlaying && !playing) playerManager.togglePlayPause()
            if (!st.isPlaying && playing) playerManager.pause()
        }
        lastHostPlaying = st.isPlaying

        // Corrección de desfase.
        if (st.isPlaying && playerManager.isPlayingState.value) {
            val drift = abs(playerManager.currentPosition - expectedPosition(st))
            if (drift > DRIFT_LIMIT_MS) playerManager.seekTo(expectedPosition(st))
        } else if (!st.isPlaying) {
            if (abs(playerManager.currentPosition - st.positionMs) > DRIFT_LIMIT_MS) playerManager.seekTo(st.positionMs)
        }
    }

    private fun findLocalSong(st: HostState): Song? {
        val all = SongLibraryHolder.songs
        all.firstOrNull { songKey(it.title, it.artist) == st.key }?.let { return it }
        // Respaldo: mismo título y duración parecida (±3 s), por si el artista está escrito distinto.
        val t = normalize(st.title)
        return all.firstOrNull {
            normalize(it.title) == t && (st.duration <= 0L || abs(it.duration - st.duration) <= 3000L)
        }
    }

    // ================= Utilidades =================

    private suspend fun ensureAuth(): com.google.firebase.auth.FirebaseUser {
        auth.currentUser?.let { return it }
        // 1) Con tu cuenta de Google (si ya iniciaste sesión en la app).
        try {
            val token = GoogleSignIn.getLastSignedInAccount(appContext)?.idToken
            if (token != null) {
                val cred = GoogleAuthProvider.getCredential(token, null)
                auth.signInWithCredential(cred).await().user?.let { return it }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Auth con Google falló, uso anónimo", t)
        }
        // 2) Respaldo: sesión anónima (hay que activarla en Firebase → Authentication).
        return auth.signInAnonymously().await().user
            ?: throw IllegalStateException("No se pudo iniciar sesión en Firebase")
    }

    private fun displayName() = userName.trim().ifBlank { "Invitado" }

    private fun defaultName(): String =
        GoogleSignIn.getLastSignedInAccount(appContext)?.displayName ?: "Invitado"

    private fun persistRoom(code: String, host: Boolean) {
        roomCode = code; isHost = host
        prefs.edit().putString(KEY_ROOM, code).putBoolean(KEY_IS_HOST, host).apply()
    }

    private fun fail(t: Throwable, friendly: String? = null) {
        Log.e(TAG, "Error en el modo En compañía", t)
        val msg = friendly ?: when {
            t.message?.contains("permission denied", true) == true ->
                "Firebase rechazó la operación: revisa las reglas de la base de datos"
            t.message?.contains("network", true) == true -> "Sin conexión a internet"
            else -> t.message ?: "Error desconocido"
        }
        status = if (roomCode != null) Status.DISCONNECTED else Status.ERROR
        statusDetail = msg
        toast(msg)
    }

    private fun generateCode(): String {
        val rnd = SecureRandom()
        return buildString { repeat(CODE_LEN) { append(ALPHABET[rnd.nextInt(ALPHABET.length)]) } }
    }

    companion object {
        private const val TAG = "MusicFlameTogether"
        private const val DB_URL = "https://musicflame-x-789-default-rtdb.firebaseio.com"
        private const val KEY_NAME = "user_name"
        private const val KEY_ROOM = "room_code"
        private const val KEY_IS_HOST = "is_host"
        private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // sin 0/O/1/I para no confundir
        private const val CODE_LEN = 6
        const val MAX_MEMBERS = 8
        private const val PUBLISH_EVERY_MS = 3000L
        private const val DRIFT_LIMIT_MS = 1500L

        /** Acepta el código solo o el enlace musicflame://sala/ABC123. */
        fun parseCode(input: String): String =
            input.trim().substringAfterLast('/').substringBefore('?').uppercase().filter { it.isLetterOrDigit() }

        private fun normalize(s: String) = s.lowercase().filter { it.isLetterOrDigit() }
        private fun songKey(title: String, artist: String) = normalize(title) + "|" + normalize(artist)
    }
}
