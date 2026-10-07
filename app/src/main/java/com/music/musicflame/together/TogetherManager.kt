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
import java.io.File
import java.security.SecureRandom
import java.util.UUID
import kotlin.math.abs

/**
 * Modo "En compañía": varias personas escuchan lo mismo a la vez.
 *
 * Cómo funciona (v1):
 *  - Firebase Realtime Database solo guarda ESTADO (qué suena, play/pausa, posición).
 *    Cada persona reproduce el archivo desde su propia biblioteca.
 *  - Si a un invitado le falta la canción, lo marca en rooms/{code}/members/{uid}/missing;
 *    al anfitrión le sale "a tu amigo le falta esta canción, ¿subirla?". Si acepta, el archivo
 *    (máx. 25 MB) se sube al bucket "salas" de Supabase Storage, la ruta va en state/filePath,
 *    los invitados lo descargan solos y lo reproducen. Todo se borra al cerrar la sala.
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

    data class Member(
        val uid: String,
        val name: String,
        val online: Boolean,
        val isHost: Boolean,
        /** Clave de la canción que a este invitado le falta (la que suena en la sala), si aplica. */
        val missing: String? = null
    )

    /** Pregunta que se le muestra al anfitrión cuando a alguien le falta la canción. */
    data class UploadPrompt(
        val key: String,
        val title: String,
        val artist: String,
        val who: List<String>,
        val sizeBytes: Long,
        val tooBig: Boolean
    )

    private data class HostState(
        val title: String,
        val artist: String,
        val duration: Long,
        val isPlaying: Boolean,
        val positionMs: Long,
        val updatedAt: Long,
        val filePath: String? = null
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
    /** Anfitrión: alguien no tiene la canción que suena → preguntar si se sube. */
    var uploadPrompt by mutableStateOf<UploadPrompt?>(null)
        private set
    /** "Subiendo…" / "Descargando…" mientras hay una transferencia en curso. */
    var transferText by mutableStateOf<String?>(null)
        private set
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

    // Presencia (se re-registra cada vez que Firebase reconecta).
    private var connectedRef: DatabaseReference? = null
    private var connectedListener: ValueEventListener? = null
    private var myPresenceRef: DatabaseReference? = null

    // Compartir canciones (Supabase Storage).
    private val uploadedPaths = mutableMapOf<String, String>()   // songKey -> ruta en el bucket (anfitrión)
    private val declinedKeys = mutableSetOf<String>()            // canciones que el anfitrión rechazó subir
    private var uploading = false
    private val downloaded = mutableMapOf<String, Song>()        // songKey -> canción descargada (invitado)
    private var downloadingKey: String? = null
    private var latestHostState: HostState? = null
    private var reportedMissing: String? = null

    init {
        // Si quedó una sala guardada de la sesión anterior, se ofrece reconectar (no se une sola).
        if (roomCode != null) { status = Status.DISCONNECTED; statusDetail = "Sala guardada. Pulsa Conectar para volver." }
        // Archivos que quedaron en Supabase de una sesión anterior (la app se cerró sin cerrar la sala).
        cleanupOrphanUploads()
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
            // Queda offline (y sin "missing"); la lista de la sala ya no lo muestra.
            try {
                db.getReference("rooms/$code/members/$uid")
                    .updateChildren(mapOf<String, Any?>("online" to false, "missing" to null))
            } catch (_: Throwable) {}
        }
        status = Status.DISCONNECTED
        statusDetail = "Desconectado. Pulsa Conectar para volver."
        nowPlayingText = null; missingSongText = null
        uploadPrompt = null; transferText = null
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

        // 1) Presencia. Al caerse la conexión Firebase marca mi nodo offline (y quita "missing");
        //    el nodo NO se borra porque tus reglas exigen existir en members para leer la sala
        //    (si se borrara, al reconectar Firebase perdería el permiso de lectura).
        //    Los miembros offline NO se muestran (ver listener de miembros), así no quedan fantasmas.
        //    Se registra YA, antes de abrir los listeners, y de nuevo cada vez que Firebase reconecta.
        val myRef = db.getReference("rooms/$code/members/$uid")
        myPresenceRef = myRef
        reportedMissing = null
        registerPresence(myRef)
        connectedRef = db.getReference(".info/connected").also { ref ->
            connectedListener = object : ValueEventListener {
                override fun onDataChange(s: DataSnapshot) {
                    if (s.getValue(Boolean::class.java) != true) return
                    reportedMissing = null // tras reconectar hay que volver a avisar si me falta la canción
                    registerPresence(myRef)
                }
                override fun onCancelled(e: DatabaseError) {}
            }
            ref.addValueEventListener(connectedListener!!)
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
                        val online = c.child("online").getValue(Boolean::class.java) ?: false
                        // Los nodos viejos con online=false son fantasmas de versiones anteriores: se ignoran.
                        if (!online) return@mapNotNull null
                        Member(
                            uid = id,
                            name = c.child("name").getValue(String::class.java) ?: "Invitado",
                            online = true,
                            isHost = id == hostUid,
                            missing = c.child("missing").getValue(String::class.java)
                        )
                    }.sortedWith(compareByDescending<Member> { it.isHost }.thenBy { it.name.lowercase() })
                    members.clear(); members.addAll(list)
                    if (status == Status.CONNECTING) {
                        status = Status.CONNECTED; statusDetail = ""
                    }
                    evaluateMissing()
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
                    latestHostState = st
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
        connectedListener?.let { connectedRef?.removeEventListener(it) }
        metaListener = null; membersListener = null; stateListener = null; connectedListener = null
        metaRef = null; membersRef = null; stateRef = null; connectedRef = null
        latestHostState = null; downloadingKey = null
    }

    private suspend fun leaveInternal(removeRemote: Boolean) {
        val code = roomCode
        val uid = auth.currentUser?.uid
        val wasHost = isHost
        // Evita que un borrado de presencia pendiente ensucie la sala después de salir.
        try { myPresenceRef?.onDisconnect()?.cancel() } catch (_: Throwable) {}
        myPresenceRef = null
        detach()
        // Limpieza de archivos compartidos: borra lo subido (anfitrión) y lo descargado (invitado).
        if (wasHost) deleteUploadedFiles()
        clearDownloads()
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
        uploadPrompt = null; transferText = null
        declinedKeys.clear(); reportedMissing = null
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
            }.collect { publishNow(); evaluateMissing() }
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
        // Si el anfitrión ya subió ESTA canción, los invitados la descargan desde filePath.
        val filePath = uploadedPaths[songKey(song.title, song.artist)]
        val data = mutableMapOf<String, Any>(
            "title" to song.title,
            "artist" to song.artist,
            "duration" to playerManager.duration.coerceAtLeast(0L),
            "isPlaying" to playerManager.isPlayingState.value,
            "positionMs" to playerManager.currentPosition,
            "updatedAt" to ServerValue.TIMESTAMP
        )
        if (filePath != null) data["filePath"] = filePath
        ref.setValue(data)
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
            updatedAt = s.child("updatedAt").getValue(Long::class.java) ?: serverNow(),
            filePath = s.child("filePath").getValue(String::class.java)
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
            val mine = findLocalSong(st) ?: downloadedSongFor(st)
            if (mine == null) {
                missingSongText = nowPlayingText
                reportMissing(st.key)          // el anfitrión verá "¿subirla?"
                if (st.filePath != null) startDownload(st)  // ya la subió: se descarga sola
                return
            }
            missingSongText = null
            reportMissing(null)
            lastRequestedKey = st.key; lastRequestedAt = now
            playerManager.playSong(mine, listOf(mine))
            delay(900) // damos tiempo a que el reproductor cargue antes de saltar a la posición
            playerManager.seekTo(expectedPosition(st))
            if (!st.isPlaying) playerManager.pause()
            lastHostPlaying = st.isPlaying
            return
        }

        missingSongText = null
        reportMissing(null)
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

    /** Programa "offline al caerse" y me marca online. Las dos llamadas salen en orden por la misma conexión. */
    private fun registerPresence(myRef: DatabaseReference) {
        myRef.onDisconnect().updateChildren(mapOf<String, Any?>("online" to false, "missing" to null))
        myRef.updateChildren(
            mapOf(
                "name" to displayName(),
                "online" to true,
                "joinedAt" to ServerValue.TIMESTAMP
            )
        ).addOnFailureListener {
            fail(it, "No se pudo entrar (¿sala llena? máximo $MAX_MEMBERS personas)")
        }
    }

    // ================= Compartir canciones (Supabase) =================

    /** Invitado: avisa en mi nodo qué canción me falta (o la quita cuando ya la tengo). */
    private fun reportMissing(key: String?) {
        if (reportedMissing == key) return
        reportedMissing = key
        val code = roomCode ?: return
        val uid = auth.currentUser?.uid ?: return
        try {
            val ref = db.getReference("rooms/$code/members/$uid/missing")
            if (key == null) ref.removeValue() else ref.setValue(key)
        } catch (t: Throwable) {
            Log.w(TAG, "No se pudo avisar la canción faltante", t)
        }
    }

    /** Anfitrión: ¿a algún invitado le falta lo que suena? Si sí, prepara la pregunta. */
    private fun evaluateMissing() {
        if (!isHost || status != Status.CONNECTED) { return }
        val song = playerManager.currentSong.value
        if (song == null) { uploadPrompt = null; return }
        val key = songKey(song.title, song.artist)
        val who = members.filter { !it.isHost && it.online && it.missing == key }
        if (who.isEmpty()) { uploadPrompt = null; return }
        if (uploading || key in declinedKeys || uploadedPaths.containsKey(key)) return
        val file = File(song.path)
        if (!file.isFile) { uploadPrompt = null; return } // p. ej. una canción de YouTube: no hay archivo que subir
        val size = file.length()
        val current = uploadPrompt
        if (current != null && current.key == key && current.who == who.map { it.name } && current.sizeBytes == size) return
        uploadPrompt = UploadPrompt(
            key = key, title = song.title, artist = song.artist,
            who = who.map { it.name }, sizeBytes = size, tooBig = size > SupabaseSalas.MAX_BYTES
        )
    }

    /** El anfitrión pulsó "Subir". */
    fun confirmUpload() {
        val p = uploadPrompt ?: return
        uploadPrompt = null
        if (p.tooBig) { declinedKeys += p.key; return }
        startUpload(p.key)
    }

    /** El anfitrión pulsó "Ahora no" (no se vuelve a preguntar por esa canción). */
    fun dismissUploadPrompt() {
        uploadPrompt?.let { declinedKeys += it.key }
        uploadPrompt = null
    }

    private fun startUpload(key: String) {
        val code = roomCode ?: return
        val song = playerManager.currentSong.value ?: return
        if (songKey(song.title, song.artist) != key || uploading) return
        val file = File(song.path)
        if (!file.isFile || file.length() > SupabaseSalas.MAX_BYTES) {
            toast("Esa canción no se puede compartir (máximo 25 MB)")
            return
        }
        val ext = file.extension.lowercase().filter { it.isLetterOrDigit() }.take(5).ifBlank { "mp3" }
        val objectPath = "$code/${UUID.randomUUID()}.$ext"
        uploading = true
        transferText = "Subiendo «${song.title}»…"
        scope.launch {
            try {
                SupabaseSalas.upload(file, objectPath, SupabaseSalas.mimeFor(ext))
                uploadedPaths[key] = objectPath
                persistUploadedPaths()
                publishNow() // ahora el estado incluye filePath y los invitados la descargan
                toast("Canción compartida: «${song.title}»")
            } catch (t: Throwable) {
                Log.e(TAG, "Falló la subida", t)
                declinedKeys += key
                toast("No se pudo subir la canción: ${t.message ?: "error desconocido"}")
            } finally {
                uploading = false
                transferText = null
            }
        }
    }

    /** Invitado: descarga la canción que el anfitrión subió y la reproduce sincronizada. */
    private fun startDownload(st: HostState) {
        val path = st.filePath ?: return
        if (downloadingKey == st.key) return
        downloadingKey = st.key
        transferText = "Descargando «${st.title}»…"
        scope.launch {
            try {
                val dir = File(appContext.cacheDir, DOWNLOAD_DIR).apply { mkdirs() }
                val dest = File(dir, path.substringAfterLast('/'))
                if (!dest.isFile) SupabaseSalas.download(path, dest)
                downloaded[st.key] = Song(
                    id = -(abs(st.key.hashCode().toLong()) + 1000L), // id sintético negativo: no choca con la biblioteca
                    title = st.title,
                    artist = st.artist,
                    album = "En compañía",
                    duration = st.duration,
                    path = dest.absolutePath
                )
                lastRequestedKey = null
                // Se aplica el ESTADO MÁS RECIENTE (la posición ya avanzó mientras se descargaba).
                latestHostState?.takeIf { it.key == st.key }?.let { applyHostState(it) }
            } catch (t: Throwable) {
                Log.e(TAG, "Falló la descarga", t)
                toast("No se pudo descargar la canción del anfitrión")
            } finally {
                downloadingKey = null
                transferText = null
            }
        }
    }

    private fun downloadedSongFor(st: HostState): Song? {
        val song = downloaded[st.key] ?: return null
        return if (File(song.path).isFile) song else { downloaded.remove(st.key); null }
    }

    private fun clearDownloads() {
        // Si lo que suena es una canción descargada, se pausa antes de borrar el archivo.
        val current = playerManager.currentSong.value
        if (current != null && current.path.contains("/$DOWNLOAD_DIR/")) playerManager.pause()
        downloaded.clear()
        try { File(appContext.cacheDir, DOWNLOAD_DIR).deleteRecursively() } catch (_: Throwable) {}
    }

    private suspend fun deleteUploadedFiles() {
        val paths = uploadedPaths.values.toList()
        uploadedPaths.clear()
        persistUploadedPaths()
        if (paths.isNotEmpty()) {
            try { SupabaseSalas.delete(paths) } catch (t: Throwable) { Log.w(TAG, "No se pudieron borrar archivos", t) }
        }
    }

    private fun persistUploadedPaths() {
        prefs.edit().putStringSet(KEY_UPLOADED, uploadedPaths.values.toSet()).apply()
    }

    private fun cleanupOrphanUploads() {
        val orphans = prefs.getStringSet(KEY_UPLOADED, null)?.toList().orEmpty()
        if (orphans.isEmpty()) return
        prefs.edit().remove(KEY_UPLOADED).apply()
        scope.launch {
            try { SupabaseSalas.delete(orphans) } catch (_: Throwable) {}
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
        private const val KEY_UPLOADED = "uploaded_paths"
        private const val DOWNLOAD_DIR = "together"
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
