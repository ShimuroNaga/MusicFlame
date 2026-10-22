package com.music.musicflame.together

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.MutableData
import com.google.firebase.database.Query
import com.google.firebase.database.Transaction
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
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import java.io.File
import java.security.SecureRandom
import java.util.UUID
import kotlin.math.abs
import com.music.musicflame.R

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
 *   rooms/{code}/meta     { hostUid, hostName, createdAt, controllerUid }
 *   rooms/{code}/members/{uid} { name, online, joinedAt }
 *   rooms/{code}/owners/{uid}  { name, since }          (hasta MAX_OWNERS propietarios; el anfitrión es uno)
 *   rooms/{code}/banned/{uid}  = true                    (expulsados: no pueden volver a ESTA sala)
 *   rooms/{code}/queue/{id}    { title, artist, duration, addedBy, addedByName, pos, addedAt }
 *   rooms/{code}/skipVotes/{uid} = trackId              (votos para saltar la canción actual)
 *   rooms/{code}/state    { title, artist, duration, isPlaying, positionMs, updatedAt, filePath,
 *                           trackId, addedBy, addedByName }
 *
 * v2 (varios propietarios, "opción B"):
 *  - Solo los PROPIETARIOS eligen canciones. Quien tiene el control (controllerUid) es el dueño de
 *    la canción que suena y es el ÚNICO que publica `state`; los demás lo siguen.
 *  - Si otro propietario elige una canción mientras suena una, va a la lista de espera (queue).
 *    Si nadie tiene el control, la reproduce al instante (toma el control con una transacción).
 *  - Al terminar la canción el control pasa al autor de la siguiente de la cola (solo él tiene el
 *    archivo). Si la cola se vacía, el control queda libre.
 *  - Más de la mitad de los propietarios conectados puede saltar la canción votando; el
 *    controlador puede saltarla solo.
 *  - Si el anfitrión o el controlador se caen, el propietario conectado más antiguo asciende.
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
        val missing: String? = null,
        /** Icono de la persona (JPEG pequeño en base64), si lo puso en Ajustes → Cuenta. */
        val avatar: String? = null,
        /** Hora (del servidor) en que entró; decide quién sobra si la sala se pasa de MAX_MEMBERS. */
        val joinedAt: Long = 0L
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

    /** Un propietario de la sala (puede elegir canciones y votar). */
    data class Owner(val uid: String, val name: String, val since: Long)

    /** Una canción en la lista de espera. [pos] decide el orden (menor = antes). */
    data class QueueItem(
        val id: String,
        val title: String,
        val artist: String,
        val duration: Long,
        val addedBy: String,
        val addedByName: String,
        val pos: Double
    )

    private data class HostState(
        val title: String,
        val artist: String,
        val duration: Long,
        val isPlaying: Boolean,
        val positionMs: Long,
        val updatedAt: Long,
        val filePath: String? = null,
        val trackId: String? = null,
        val addedBy: String? = null,
        val addedByName: String? = null
    ) {
        val key: String get() = songKey(title, artist)
    }

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("together_prefs", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    init { current = this }

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
    private var uploadPromptState by mutableStateOf<UploadPrompt?>(null)
    var uploadPrompt: UploadPrompt?
        get() = uploadPromptState
        private set(value) {
            uploadPromptState = value
            syncUploadNotification(value)
        }
    private var notifiedPrompt: UploadPrompt? = null
    /** "Subiendo…" / "Descargando…" mientras hay una transferencia en curso. */
    var transferText by mutableStateOf<String?>(null)
        private set
    private val _busy = mutableStateOf(false)
    val busy: State<Boolean> = _busy

    // ---------- Propietarios, lista de espera y votos ----------
    val owners = mutableStateListOf<Owner>()
    val queue = mutableStateListOf<QueueItem>()
    /** Quién tiene el control ahora (dueño de la canción que suena). null = nadie, sala libre. */
    var controllerUid by mutableStateOf<String?>(null)
        private set
    /** Nombre de quien puso la canción que suena (para la etiqueta "añadida por"). */
    var nowAddedByName by mutableStateOf<String?>(null)
        private set
    /** Votos válidos para saltar la canción actual. */
    var skipVotes by mutableStateOf(0)
        private set
    var iVotedSkip by mutableStateOf(false)
        private set

    // ---------- Chat de la sala ----------
    /** Un mensaje del chat. Si [isSystem], es un aviso discreto ([event] = "mute"/"unmute", [text] = nombre afectado). */
    data class ChatMessage(
        val id: String,
        val uid: String,
        val name: String,
        val text: String,
        val at: Long,
        val isSystem: Boolean = false,
        val event: String? = null
    )

    val chat = mutableStateListOf<ChatMessage>()
    private val chatMutedUids = mutableStateListOf<String>()
    /** Iconos vistos en la sala (uid -> base64), para que los mensajes de quien ya se fue conserven su icono. */
    val chatAvatars = mutableStateMapOf<String, String>()
    /** true mientras el panel del chat está abierto (no se avisa ni se cuentan no leídos). */
    var chatVisible by mutableStateOf(false)
        private set
    var chatUnread by mutableStateOf(0)
        private set
    /** La notificación del chat pide abrir el chat directamente (lo consume TogetherScreen). */
    var openChatRequested by mutableStateOf(false)
    /** true mientras el chat está a pantalla completa: MainActivity oculta sus barras y el chat trae las suyas. */
    var chatScreenOpen by mutableStateOf(false)

    private var chatQuery: Query? = null
    private var chatListener: ValueEventListener? = null
    private var chatMutedRef: DatabaseReference? = null
    private var chatMutedListener: ValueEventListener? = null
    private var chatBaselineDone = false
    private val chatUnreadLines = ArrayList<String>()
    private var lastChatSentAt = 0L

    val iAmChatMuted: Boolean get() = myUid()?.let { it in chatMutedUids } == true
    fun isChatMuted(uid: String): Boolean = uid in chatMutedUids

    private var overflowJob: Job? = null

    private fun myUid(): String? = try { auth.currentUser?.uid } catch (_: Throwable) { null }
    val isOwner: Boolean get() = myUid()?.let { me -> owners.any { it.uid == me } || (owners.isEmpty() && isHost) } == true
    val isController: Boolean get() = controllerUid != null && controllerUid == myUid()
    val ownersOnline: Int get() = owners.count { o -> members.any { it.uid == o.uid && it.online } }
    /** Votos necesarios para saltar: más de la mitad de los propietarios conectados. */
    val skipNeeded: Int get() = ownersOnline / 2 + 1
    val canAddOwner: Boolean get() = owners.size < MAX_OWNERS
    /** Mi uid de Firebase (para saber qué elementos de la cola son míos). */
    val currentUid: String? get() = myUid()

    /** Canción que suena ahora en la sala, si existe en este teléfono (para pintar su carátula). */
    fun nowPlayingSong(): Song? {
        nowPlayingText // se lee a propósito: así la pantalla se actualiza al cambiar la canción
        if (isController) return playerManager.currentSong.value
        val st = latestHostState ?: return null
        return findLocalSong(st) ?: downloadedSongFor(st)
    }

    /** Carátula/archivo de un elemento de la cola, si la canción está en este teléfono. */
    fun localSongFor(item: QueueItem): Song? =
        myQueuedSongs[item.id] ?: findLocalSong(item.title, item.artist, item.duration)

    /** Botón "Saltar canción" (solo el controlador; los demás propietarios votan). */
    fun skipCurrent() {
        if (isController) scope.launch { advanceQueue() }
    }

    /** Código llegado por enlace musicflame://sala/CODIGO. MainActivity lo deja aquí. */
    var pendingDeepLinkCode by mutableStateOf<String?>(null)

    /** La UI lo asigna para mostrar Toasts. */
    var onToast: ((String) -> Unit)? = null

    /** Enlace https (tocable en WhatsApp, etc.); la página docs/sala/ abre la app con musicflame://sala/CODIGO. */
    val roomLink: String? get() = roomCode?.let { WEB_LINK_BASE + it }
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

    private var ownersRef: DatabaseReference? = null
    private var queueRef: DatabaseReference? = null
    private var votesRef: DatabaseReference? = null
    private var ownersListener: ValueEventListener? = null
    private var queueListener: ValueEventListener? = null
    private var votesListener: ValueEventListener? = null
    private val voteMap = mutableMapOf<String, String>()          // uid -> trackId votado
    private val myQueuedSongs = mutableMapOf<String, Song>()      // id en la cola -> mi canción (la que subí)
    private var bypassInterceptor = false                         // true mientras la sala misma reproduce
    private var claiming = false                                  // estoy tomando el control (transacción)
    private var iHaveTrack = false                                // estoy reproduciendo una canción de la sala
    private var lastAdvanceAt = 0L
    private var advancing = false
    private var idleJob: Job? = null
    private var takeoverJob: Job? = null
    private var promotionJob: Job? = null
    /** Id de la canción actual de la sala (cambia con cada canción, aunque sea la misma repetida). */
    private var currentTrackId by mutableStateOf<String?>(null)
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
        // Ganchos del reproductor: así tocar una canción o "siguiente" respeta las reglas de la sala.
        playerManager.playInterceptor = { song -> onPlayRequested(song) }
        playerManager.skipInterceptor = { onSkipRequested() }
        playerManager.onTrackEnded = { onTrackEnded() }
        // Si quedó una sala guardada de la sesión anterior, se ofrece reconectar (no se une sola).
        if (roomCode != null) { status = Status.DISCONNECTED; statusDetail = appContext.getString(R.string.tgm_saved_room) }
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
        if (status == Status.CONNECTED) db.getReference("rooms/$code/members/$uid/name").setValue(userName.ifBlank { appContext.getString(R.string.tgm_guest) })
    }

    /** Publica (o quita) mi icono en la sala abierta. Lo llama TogetherAvatar al guardar/borrar. */
    fun refreshAvatar() {
        val code = roomCode ?: return
        val uid = auth.currentUser?.uid ?: return
        if (status != Status.CONNECTED) return
        val b64 = TogetherAvatar.load(appContext)
        db.getReference("rooms/$code/members/$uid/avatar").setValue(b64)
    }

    /** "+ Crear sala". */
    fun createRoom() {
        if (_busy.value) return
        _busy.value = true
        scope.launch {
            try {
                if (roomCode != null) leaveInternal(removeRemote = true)
                status = Status.CONNECTING; statusDetail = appContext.getString(R.string.tgm_creating)
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
                    appContext.getString(R.string.tgm_create_failed)
                )
                hostUid = user.uid
                // El anfitrión es el primer propietario de la sala.
                try {
                    db.getReference("rooms/$code/owners/${user.uid}").setValue(
                        mapOf("name" to displayName(), "since" to ServerValue.TIMESTAMP)
                    ).await()
                } catch (t: Throwable) { Log.w(TAG, "No se pudo registrar al anfitrión como propietario", t) }
                persistRoom(code, true)
                attach(code, user.uid)
                toast(appContext.getString(R.string.tgm_room_created, code))
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
        if (code.length < 4) { toast(appContext.getString(R.string.tgm_invalid_code)); return }
        if (_busy.value) return
        _busy.value = true
        scope.launch {
            try {
                if (roomCode != null && roomCode != code) leaveInternal(removeRemote = true)
                status = Status.CONNECTING; statusDetail = appContext.getString(R.string.tgm_joining)
                val user = ensureAuth()
                val meta = db.getReference("rooms/$code/meta").get().await()
                if (!meta.exists()) {
                    status = Status.IDLE; statusDetail = ""
                    toast(appContext.getString(R.string.tgm_room_not_found))
                    return@launch
                }
                if (isBanned(code, user.uid)) {
                    status = Status.IDLE; statusDetail = ""
                    toast(appContext.getString(R.string.tgm_banned_here))
                    return@launch
                }
                hostUid = meta.child("hostUid").getValue(String::class.java)
                persistRoom(code, hostUid == user.uid)
                attach(code, user.uid)
                toast(appContext.getString(R.string.tgm_connected_to, code))
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
                    toast(appContext.getString(R.string.tgm_room_gone))
                    leaveInternal(removeRemote = false)
                    return@launch
                }
                if (isBanned(code, user.uid)) {
                    toast(appContext.getString(R.string.tgm_banned_here))
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
        if (isController) releaseControlNow()   // la sala queda libre y el siguiente de la cola toma el turno
        detach()
        if (uid != null) {
            // Queda offline (y sin "missing"); la lista de la sala ya no lo muestra.
            try {
                db.getReference("rooms/$code/members/$uid")
                    .updateChildren(mapOf<String, Any?>("online" to false, "missing" to null))
            } catch (_: Throwable) {}
        }
        status = Status.DISCONNECTED
        statusDetail = appContext.getString(R.string.tgm_disconnected_hint)
        nowPlayingText = null; missingSongText = null; nowAddedByName = null
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
                    toast(appContext.getString(R.string.tgm_room_gone))
                    leaveInternal(removeRemote = false)
                    return@launch
                }
                if (isBanned(code, user.uid)) {
                    toast(appContext.getString(R.string.tgm_banned_here))
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
        playerManager.playInterceptor = null
        playerManager.skipInterceptor = null
        playerManager.onTrackEnded = null
        if (isController) releaseControlNow()
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

        // 2) Meta: si desaparece, el anfitrión cerró la sala. También trae quién es el anfitrión
        //    actual (puede cambiar por traspaso o ascenso) y quién tiene el control de la música.
        metaRef = db.getReference("rooms/$code/meta").also { ref ->
            metaListener = object : ValueEventListener {
                override fun onDataChange(s: DataSnapshot) {
                    if (!s.exists()) {
                        if (status == Status.CONNECTED) {
                            scope.launch {
                                toast(appContext.getString(R.string.tgm_host_closed))
                                leaveInternal(removeRemote = false)
                            }
                        }
                        return
                    }
                    val me = auth.currentUser?.uid
                    val newHost = s.child("hostUid").getValue(String::class.java)
                    if (newHost != null && newHost != hostUid) {
                        hostUid = newHost
                        val nowHost = newHost == me
                        val wasHost = isHost
                        persistRoom(code, nowHost)
                        val remapped = members.map { it.copy(isHost = it.uid == newHost) }
                            .sortedWith(compareByDescending<Member> { it.isHost }.thenBy { it.name.lowercase() })
                        members.clear(); members.addAll(remapped)
                        if (nowHost && !wasHost) toast(appContext.getString(R.string.tgq_you_are_host_now))
                    }
                    val oldCtrl = controllerUid
                    val newCtrl = s.child("controllerUid").getValue(String::class.java)
                    controllerUid = newCtrl
                    if (newCtrl == me && oldCtrl != me && !claiming && !iHaveTrack) {
                        // Me pasaron el control (o lo tomé por relevo): toca mi canción de la cola.
                        scope.launch { delay(300); takeTurn() }
                    }
                    if (newCtrl != me && oldCtrl == me) iHaveTrack = false
                    evaluateIdle(); evaluateTakeover(); evaluatePromotion()
                    evaluateMissing(); recomputeVotes()
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
                            name = c.child("name").getValue(String::class.java) ?: appContext.getString(R.string.tgm_guest),
                            online = true,
                            isHost = id == hostUid,
                            missing = c.child("missing").getValue(String::class.java),
                            avatar = c.child("avatar").getValue(String::class.java),
                            joinedAt = (c.child("joinedAt").value as? Number)?.toLong() ?: 0L
                        )
                    }.sortedWith(compareByDescending<Member> { it.isHost }.thenBy { it.name.lowercase() })
                    members.clear(); members.addAll(list)
                    list.forEach { m -> m.avatar?.let { a -> chatAvatars[m.uid] = a } }
                    // Tope de MAX_MEMBERS conectados: sobran los que entraron al final (el anfitrión nunca).
                    val over = TogetherQueueLogic.overflowUids(
                        list.map { TogetherQueueLogic.OnlineEntry(it.uid, it.joinedAt, it.isHost) }, MAX_MEMBERS
                    )
                    if (over.isNotEmpty()) {
                        if (uid in over) {
                            scope.launch {
                                toast(appContext.getString(R.string.tgm_room_full, MAX_MEMBERS))
                                leaveInternal(removeRemote = true)
                            }
                            return
                        }
                        // Respaldo: quien cumple las reglas se sale solo (arriba). Si pasado un margen sigue
                        // sobrando alguien (cliente modificado), el anfitrión lo saca y lo bloquea.
                        if (isHost) scheduleOverflowEnforcement(code)
                    }
                    if (status == Status.CONNECTING) {
                        status = Status.CONNECTED; statusDetail = ""
                    }
                    evaluateMissing()
                    evaluateIdle(); evaluateTakeover(); evaluatePromotion(); recomputeVotes()
                }
                override fun onCancelled(e: DatabaseError) {
                    scope.launch { handleMembersCancelled(code, uid, e) }
                }
            }
            ref.addValueEventListener(membersListener!!)
        }

        // 4) Propietarios.
        ownersRef = db.getReference("rooms/$code/owners").also { ref ->
            ownersListener = object : ValueEventListener {
                override fun onDataChange(s: DataSnapshot) {
                    val list = s.children.mapNotNull { c ->
                        val id = c.key ?: return@mapNotNull null
                        Owner(
                            uid = id,
                            name = c.child("name").getValue(String::class.java) ?: appContext.getString(R.string.tgm_guest),
                            since = (c.child("since").value as? Number)?.toLong() ?: 0L
                        )
                    }.sortedWith(compareBy<Owner>({ it.since }, { it.uid }))
                    owners.clear(); owners.addAll(list)
                    // Salas creadas con la versión anterior: el anfitrión aún no figura como propietario.
                    if (hostUid == uid && list.none { it.uid == uid }) {
                        db.getReference("rooms/$code/owners/$uid")
                            .setValue(mapOf("name" to displayName(), "since" to ServerValue.TIMESTAMP))
                    }
                    evaluateIdle(); evaluateTakeover(); evaluatePromotion(); recomputeVotes()
                }
                override fun onCancelled(e: DatabaseError) {}
            }
            ref.addValueEventListener(ownersListener!!)
        }

        // 5) Lista de espera.
        queueRef = db.getReference("rooms/$code/queue").also { ref ->
            queueListener = object : ValueEventListener {
                override fun onDataChange(s: DataSnapshot) {
                    val list = s.children.mapNotNull { c ->
                        val id = c.key ?: return@mapNotNull null
                        val title = c.child("title").getValue(String::class.java) ?: return@mapNotNull null
                        QueueItem(
                            id = id,
                            title = title,
                            artist = c.child("artist").getValue(String::class.java) ?: "",
                            duration = (c.child("duration").value as? Number)?.toLong() ?: 0L,
                            addedBy = c.child("addedBy").getValue(String::class.java) ?: return@mapNotNull null,
                            addedByName = c.child("addedByName").getValue(String::class.java) ?: "",
                            pos = (c.child("pos").value as? Number)?.toDouble() ?: 0.0
                        )
                    }.sortedWith(compareBy<QueueItem>({ it.pos }, { it.id }))
                    queue.clear(); queue.addAll(list)
                    myQueuedSongs.keys.retainAll(list.map { it.id }.toSet())
                    evaluateIdle()
                }
                override fun onCancelled(e: DatabaseError) {}
            }
            ref.addValueEventListener(queueListener!!)
        }

        // 6) Votos para saltar.
        votesRef = db.getReference("rooms/$code/skipVotes").also { ref ->
            votesListener = object : ValueEventListener {
                override fun onDataChange(s: DataSnapshot) {
                    voteMap.clear()
                    s.children.forEach { c ->
                        val id = c.key ?: return@forEach
                        c.getValue(String::class.java)?.let { voteMap[id] = it }
                    }
                    recomputeVotes()
                }
                override fun onCancelled(e: DatabaseError) {}
            }
            ref.addValueEventListener(votesListener!!)
        }

        // 6b) Chat de la sala: últimos CHAT_LIMIT mensajes + lista de expulsados del chat.
        chatBaselineDone = false
        chatQuery = db.getReference("rooms/$code/chat").limitToLast(CHAT_LIMIT).also { q ->
            chatListener = object : ValueEventListener {
                override fun onDataChange(s: DataSnapshot) {
                    val me = myUid()
                    val list = s.children.mapNotNull { c ->
                        val id = c.key ?: return@mapNotNull null
                        val sender = c.child("uid").getValue(String::class.java) ?: return@mapNotNull null
                        val text = c.child("text").getValue(String::class.java) ?: return@mapNotNull null
                        ChatMessage(
                            id = id,
                            uid = sender,
                            name = c.child("name").getValue(String::class.java) ?: "",
                            text = text,
                            at = (c.child("at").value as? Number)?.toLong() ?: serverNow(),
                            isSystem = c.child("type").getValue(String::class.java) == "sys",
                            event = c.child("event").getValue(String::class.java)
                        )
                    }
                    val known = chat.map { it.id }.toHashSet()
                    // El primer snapshot es el historial: no cuenta como "nuevo".
                    val fresh = if (chatBaselineDone) list.filter { it.id !in known && !it.isSystem && it.uid != me } else emptyList()
                    chat.clear(); chat.addAll(list)
                    chatBaselineDone = true
                    if (fresh.isNotEmpty() && !chatVisible) onChatArrived(fresh)
                }
                override fun onCancelled(e: DatabaseError) {
                    Log.w(TAG, "chat cancelled: ${e.message}")
                }
            }
            q.addValueEventListener(chatListener!!)
        }
        chatMutedRef = db.getReference("rooms/$code/chatMuted").also { ref ->
            chatMutedListener = object : ValueEventListener {
                override fun onDataChange(s: DataSnapshot) {
                    val wasMuted = iAmChatMuted
                    val ids = s.children.mapNotNull { it.key }
                    chatMutedUids.clear(); chatMutedUids.addAll(ids)
                    if (!wasMuted && iAmChatMuted) toast(appContext.getString(R.string.tgc_you_muted))
                }
                override fun onCancelled(e: DatabaseError) {}
            }
            ref.addValueEventListener(chatMutedListener!!)
        }

        // 7) Estado de reproducción. Todos lo escuchan; solo el controlador lo publica.
        stateRef = db.getReference("rooms/$code/state")
        stateListener = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                if (isController) return // soy yo quien publica; no me sigo a mí mismo
                val st = parseState(s)
                if (st == null) {
                    // Sala libre: nadie está reproduciendo.
                    latestHostState = null
                    nowPlayingText = null; nowAddedByName = null; missingSongText = null
                    reportMissing(null)
                    recomputeVotes()
                    return
                }
                latestHostState = st
                nowAddedByName = st.addedByName
                recomputeVotes()
                scope.launch { applyHostState(st) }
            }
            override fun onCancelled(e: DatabaseError) {
                Log.w(TAG, "state cancelled: ${e.message}")
            }
        }
        stateRef!!.addValueEventListener(stateListener!!)
        startPublishing()
    }

    private fun detach() {
        overflowJob?.cancel(); overflowJob = null
        publishJobs.forEach { it.cancel() }; publishJobs.clear()
        metaListener?.let { metaRef?.removeEventListener(it) }
        membersListener?.let { membersRef?.removeEventListener(it) }
        stateListener?.let { stateRef?.removeEventListener(it) }
        connectedListener?.let { connectedRef?.removeEventListener(it) }
        ownersListener?.let { ownersRef?.removeEventListener(it) }
        queueListener?.let { queueRef?.removeEventListener(it) }
        votesListener?.let { votesRef?.removeEventListener(it) }
        chatListener?.let { chatQuery?.removeEventListener(it) }
        chatMutedListener?.let { chatMutedRef?.removeEventListener(it) }
        chatListener = null; chatQuery = null; chatMutedListener = null; chatMutedRef = null
        chatBaselineDone = false
        chat.clear(); chatMutedUids.clear(); chatAvatars.clear()
        chatUnread = 0; chatUnreadLines.clear()
        TogetherNotifications.cancelChat(appContext)
        metaListener = null; membersListener = null; stateListener = null; connectedListener = null
        ownersListener = null; queueListener = null; votesListener = null
        metaRef = null; membersRef = null; stateRef = null; connectedRef = null
        ownersRef = null; queueRef = null; votesRef = null
        idleJob?.cancel(); takeoverJob?.cancel(); promotionJob?.cancel()
        idleJob = null; takeoverJob = null; promotionJob = null
        latestHostState = null; downloadingKey = null
    }

    private suspend fun leaveInternal(removeRemote: Boolean) {
        val code = roomCode
        val uid = auth.currentUser?.uid
        val wasHost = isHost
        // Si yo tenía el control, lo suelto para que el siguiente de la cola siga la música.
        if (isController) releaseControlNow()
        // Evita que un borrado de presencia pendiente ensucie la sala después de salir.
        try { myPresenceRef?.onDisconnect()?.cancel() } catch (_: Throwable) {}
        myPresenceRef = null
        val myQueueIds = queue.filter { it.addedBy == uid }.map { it.id }
        detach()
        // Limpieza de archivos compartidos: borra lo subido (cualquier propietario puede haber subido) y lo descargado.
        deleteUploadedFiles()
        clearDownloads()
        if (removeRemote && code != null && uid != null) {
            try {
                if (wasHost) {
                    db.getReference("rooms/$code").removeValue().await()
                } else {
                    // Mis canciones en espera ya no se podrían reproducir (solo yo tengo el archivo).
                    if (myQueueIds.isNotEmpty()) {
                        db.getReference("rooms/$code/queue")
                            .updateChildren(myQueueIds.associateWith { null as Any? }).await()
                    }
                    try { db.getReference("rooms/$code/owners/$uid").removeValue().await() } catch (_: Throwable) {}
                    db.getReference("rooms/$code/members/$uid").removeValue().await()
                }
            } catch (t: Throwable) {
                Log.w(TAG, "No se pudo limpiar la sala", t)
            }
        }
        roomCode = null; isHost = false; hostUid = null
        members.clear(); owners.clear(); queue.clear(); voteMap.clear(); myQueuedSongs.clear()
        controllerUid = null; currentTrackId = null; iHaveTrack = false; claiming = false
        skipVotes = 0; iVotedSkip = false; nowAddedByName = null
        nowPlayingText = null; missingSongText = null
        uploadPrompt = null; transferText = null
        declinedKeys.clear(); reportedMissing = null
        status = Status.IDLE; statusDetail = ""
        prefs.edit().remove(KEY_ROOM).remove(KEY_IS_HOST).apply()
    }

    // ================= Controlador: publicar =================

    private fun startPublishing() {
        // Publica al instante cuando cambia la canción, play/pausa o el id de pista…
        publishJobs += scope.launch {
            snapshotFlow {
                val s = playerManager.currentSong.value
                Triple(s?.let { songKey(it.title, it.artist) }, playerManager.isPlayingState.value, currentTrackId)
            }.collect { publishNow(); evaluateMissing() }
        }
        // …y cada 3 s para que los demás corrijan el desfase (y detecten saltos de barra).
        publishJobs += scope.launch {
            while (isActive) {
                delay(PUBLISH_EVERY_MS)
                if (isController && playerManager.isPlayingState.value) publishNow()
            }
        }
    }

    /** Solo publica quien tiene el control y está reproduciendo una canción de la sala. */
    private fun publishNow() {
        if (!isController || !iHaveTrack) return
        val ref = stateRef ?: return
        val me = myUid() ?: return
        val trackId = currentTrackId ?: return
        val song = playerManager.currentSong.value ?: return
        // Si ya subí ESTA canción, los demás la descargan desde filePath.
        val filePath = uploadedPaths[songKey(song.title, song.artist)]
        val data = mutableMapOf<String, Any>(
            "title" to song.title,
            "artist" to song.artist,
            "duration" to playerManager.duration.coerceAtLeast(0L),
            "isPlaying" to playerManager.isPlayingState.value,
            "positionMs" to playerManager.currentPosition,
            "updatedAt" to ServerValue.TIMESTAMP,
            "trackId" to trackId,
            "addedBy" to me,
            "addedByName" to displayName()
        )
        if (filePath != null) data["filePath"] = filePath
        nowPlayingText = songDisplay(song.title, song.artist)
        nowAddedByName = displayName()
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
            filePath = s.child("filePath").getValue(String::class.java),
            trackId = s.child("trackId").getValue(String::class.java),
            addedBy = s.child("addedBy").getValue(String::class.java),
            addedByName = s.child("addedByName").getValue(String::class.java)
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
            playLocal(mine)
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

    private fun findLocalSong(st: HostState): Song? = findLocalSong(st.title, st.artist, st.duration)

    private fun findLocalSong(title: String, artist: String, duration: Long): Song? {
        val all = SongLibraryHolder.songs
        all.firstOrNull { songKey(it.title, it.artist) == songKey(title, artist) }?.let { return it }
        // Respaldo: mismo título y duración parecida (±3 s), por si el artista está escrito distinto.
        val t = normalize(title)
        return all.firstOrNull {
            normalize(it.title) == t && (duration <= 0L || abs(it.duration - duration) <= 3000L)
        }
    }

    /** Programa "offline al caerse" y me marca online. Las dos llamadas salen en orden por la misma conexión. */
    private fun registerPresence(myRef: DatabaseReference) {
        myRef.onDisconnect().updateChildren(mapOf<String, Any?>("online" to false, "missing" to null))
        myRef.updateChildren(
            mapOf(
                "name" to displayName(),
                "online" to true,
                "joinedAt" to ServerValue.TIMESTAMP,
                "avatar" to TogetherAvatar.load(appContext)
            )
        ).addOnFailureListener {
            fail(it, appContext.getString(R.string.tgm_join_failed, MAX_MEMBERS))
        }
    }

    // ================= Propietarios, lista de espera y votos =================

    private enum class Claim { WON, LOST, ERROR }

    private fun onlineUids(): Set<String> = members.filter { it.online }.map { it.uid }.toSet()

    /** Reproduce SIN pasar por el interceptor (lo usa la propia sala). */
    private fun playLocal(song: Song) {
        bypassInterceptor = true
        try { playerManager.playSong(song, listOf(song)) } finally { bypassInterceptor = false }
    }

    /** Gancho de MusicPlayerManager.playSong: true = la sala se encargó (no reproducir aquí). */
    private fun onPlayRequested(song: Song): Boolean {
        if (bypassInterceptor) return false
        if (roomCode == null || status != Status.CONNECTED) return false
        if (!isOwner) { toast(appContext.getString(R.string.tgq_only_owners)); return true }
        if (isController) { playAsController(song); return true }
        scope.launch { claimOrEnqueue(song) }
        return true
    }

    /** Gancho de skipNext: el controlador salta; otro propietario vota; un oyente no puede. */
    private fun onSkipRequested(): Boolean {
        if (roomCode == null || status != Status.CONNECTED) return false
        if (!isOwner) { toast(appContext.getString(R.string.tgq_only_owners)); return true }
        if (isController) { scope.launch { advanceQueue() }; return true }
        if (controllerUid == null) return false      // sala libre: nada que votar
        toggleSkipVote()
        return true
    }

    /** Fin de canción (o repetición): si yo tengo el control, sigue la cola. */
    private fun onTrackEnded() {
        if (roomCode == null || status != Status.CONNECTED || !isController || !iHaveTrack) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastAdvanceAt < 2500) return       // evita doble aviso (fin de pista + repetición)
        lastAdvanceAt = now
        scope.launch { advanceQueue() }
    }

    /** Elijo una canción sin tener el control: si la sala está libre la tomo; si no, va a la espera. */
    private suspend fun claimOrEnqueue(song: Song) {
        val code = roomCode ?: return
        val me = myUid() ?: return
        claiming = true
        try {
            when (claimTransaction(code, me)) {
                Claim.WON -> { controllerUid = me; playAsController(song) }
                Claim.LOST -> enqueue(song)
                Claim.ERROR -> {
                    // Casi seguro: las reglas de Firebase aún no tienen la versión con propietarios.
                    toast(appContext.getString(R.string.tgq_rules_hint))
                    playLocal(song)
                }
            }
        } finally {
            claiming = false
        }
    }

    private suspend fun claimTransaction(code: String, me: String): Claim = suspendCoroutine { cont ->
        db.getReference("rooms/$code/meta/controllerUid").runTransaction(object : Transaction.Handler {
            override fun doTransaction(d: MutableData): Transaction.Result {
                return if (d.getValue(String::class.java) == null) { d.value = me; Transaction.success(d) }
                else Transaction.abort()
            }
            override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                cont.resume(when { error != null -> Claim.ERROR; committed -> Claim.WON; else -> Claim.LOST })
            }
        })
    }

    /** Cambia [path] de [from] a [to] solo si todavía vale [from] (relevos de control y de anfitrión). */
    private suspend fun swapValue(path: String, from: String, to: String?): Boolean = suspendCoroutine { cont ->
        db.getReference(path).runTransaction(object : Transaction.Handler {
            override fun doTransaction(d: MutableData): Transaction.Result {
                return if (d.getValue(String::class.java) == from) { d.value = to; Transaction.success(d) }
                else Transaction.abort()
            }
            override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                cont.resume(error == null && committed)
            }
        })
    }

    private var currentTrackKey: String? = null

    /** Soy el controlador: esta canción es MÍA y la publico para todos. */
    private fun playAsController(song: Song) {
        val key = songKey(song.title, song.artist)
        val prevKey = currentTrackKey
        if (prevKey != null && prevKey != key) scope.launch { cleanupUpload(prevKey) }
        currentTrackKey = key
        iHaveTrack = true
        lastAdvanceAt = SystemClock.elapsedRealtime()
        playLocal(song)
        currentTrackId = UUID.randomUUID().toString()   // después de playLocal: se publica con la canción nueva
        nowPlayingText = songDisplay(song.title, song.artist)
        nowAddedByName = displayName()
        missingSongText = null; reportMissing(null)
        latestHostState = null
        clearSkipVotes()
    }

    /** Añade mi canción a la lista de espera (queda al final). */
    private suspend fun enqueue(song: Song) {
        val code = roomCode ?: return
        val me = myUid() ?: return
        if (queue.size >= TogetherQueueLogic.MAX_QUEUE) {
            toast(appContext.getString(R.string.tgq_queue_full, TogetherQueueLogic.MAX_QUEUE)); return
        }
        try {
            val pos = TogetherQueueLogic.positionBetween(queue.maxOfOrNull { it.pos }, null)
            val ref = db.getReference("rooms/$code/queue").push()
            val id = ref.key ?: return
            myQueuedSongs[id] = song
            ref.setValue(
                mapOf(
                    "title" to song.title.take(200),
                    "artist" to song.artist.take(200),
                    "duration" to song.duration,
                    "addedBy" to me,
                    "addedByName" to displayName(),
                    "pos" to pos,
                    "addedAt" to ServerValue.TIMESTAMP
                )
            ).await()
            toast(appContext.getString(R.string.tgq_added_to_queue, song.title))
        } catch (t: Throwable) {
            fail(t)
        }
    }

    /**
     * Termina la canción actual y sigue la cola. Solo lo ejecuta el controlador.
     *  - Si la siguiente (de un autor conectado) es mía: la reproduzco.
     *  - Si es de otro: le paso el control (solo él tiene el archivo).
     *  - Si no hay nadie: la sala queda libre.
     */
    private suspend fun advanceQueue() {
        if (advancing) return
        advancing = true
        try {
            val me = myUid() ?: return
            if (roomCode == null || !isController) return
            cleanupUpload(currentTrackKey); currentTrackKey = null
            clearSkipVotes()
            val skipped = mutableSetOf<String>()
            while (true) {
                val items = queue.filter { it.id !in skipped }
                val idx = TogetherQueueLogic.firstPlayableIndex(items.map { it.addedBy }, onlineUids())
                if (idx < 0) { playerManager.pause(); playerManager.seekTo(0); releaseControlNow(); return }
                val next = items[idx]
                if (next.addedBy == me) {
                    val song = myQueuedSongs[next.id] ?: findLocalSong(next.title, next.artist, next.duration)
                    removeQueueItem(next.id)
                    if (song == null) {
                        skipped += next.id
                        toast(appContext.getString(R.string.tgq_song_not_found, next.title))
                        continue
                    }
                    playAsController(song)
                    return
                }
                handOff(next.addedBy)
                return
            }
        } finally {
            advancing = false
        }
    }

    /** Me pasaron el control sin canción en curso: toca la primera de la cola que me corresponda. */
    private suspend fun takeTurn() {
        if (!isController || iHaveTrack || claiming || advancing) return
        advanceQueue()
    }

    private suspend fun handOff(toUid: String) {
        val code = roomCode ?: return
        val me = myUid() ?: return
        // Antes de soltar el control limpio lo publicado: nadie debe seguir una canción que ya terminó.
        try { db.getReference("rooms/$code/state").removeValue() } catch (_: Throwable) {}
        if (swapValue("rooms/$code/meta/controllerUid", me, toUid)) {
            iHaveTrack = false; lastHostPlaying = null; lastRequestedKey = null
            playerManager.pause()
        }
    }

    /** Suelto el control (sala libre). No espera respuesta: sirve también al salir o desconectar. */
    private fun releaseControlNow() {
        val code = roomCode ?: return
        if (!isController) return
        iHaveTrack = false; lastHostPlaying = null; lastRequestedKey = null
        nowPlayingText = null; nowAddedByName = null
        try {
            db.getReference("rooms/$code/state").removeValue()
            db.getReference("rooms/$code/skipVotes").removeValue()
            db.getReference("rooms/$code/meta/controllerUid").removeValue()
        } catch (t: Throwable) {
            Log.w(TAG, "No se pudo soltar el control", t)
        }
    }

    private fun clearSkipVotes() {
        val code = roomCode ?: return
        if (!isController) return
        try { db.getReference("rooms/$code/skipVotes").removeValue() } catch (_: Throwable) {}
    }

    /** Cuenta los votos válidos (propietarios conectados, para la canción actual) y salta si alcanzan. */
    private fun recomputeVotes() {
        val trackId = if (isController) currentTrackId else latestHostState?.trackId
        if (trackId == null) { skipVotes = 0; iVotedSkip = false; return }
        val onlineOwners = owners.map { it.uid }.filter { it in onlineUids() }.toSet()
        val valid = voteMap.filter { (uid, t) -> t == trackId && uid in onlineOwners && uid != controllerUid }.keys
        skipVotes = valid.size
        iVotedSkip = myUid()?.let { it in valid } == true
        if (isController && iHaveTrack && TogetherQueueLogic.skipPasses(valid.size, onlineOwners.size)) {
            scope.launch { advanceQueue() }
        }
    }

    /** Propietario (no controlador): vota para saltar la canción actual, o retira su voto. */
    fun toggleSkipVote() {
        val code = roomCode ?: return
        val me = myUid() ?: return
        if (!isOwner || isController) return
        val trackId = latestHostState?.trackId ?: return
        val ref = db.getReference("rooms/$code/skipVotes/$me")
        if (voteMap[me] == trackId) ref.removeValue() else ref.setValue(trackId)
    }

    /** Quitar de la cola: cada quien lo suyo; el anfitrión puede quitar cualquiera. */
    fun removeFromQueue(item: QueueItem) {
        if (item.addedBy != myUid() && !isHost) return
        removeQueueItem(item.id)
    }

    private fun removeQueueItem(id: String) {
        val code = roomCode ?: return
        queue.removeAll { it.id == id }
        myQueuedSongs.remove(id)
        try { db.getReference("rooms/$code/queue/$id").removeValue() } catch (_: Throwable) {}
    }

    /** Arrastrar: mueve el elemento [from] al lugar [to] (solo propietarios). */
    fun moveQueueItem(from: Int, to: Int) {
        val code = roomCode ?: return
        if (!isOwner) return
        val items = queue.toList()
        if (from !in items.indices || to !in items.indices || from == to) return
        val moved = items[from]
        val positions = items.map { it.pos }
        val rest = positions.filterIndexed { i, _ -> i != from }
        if (TogetherQueueLogic.needsRenumber(rest.getOrNull(to - 1), rest.getOrNull(to))) {
            // Las posiciones quedaron demasiado juntas: se renumera toda la cola de una vez.
            val order = items.toMutableList().also { it.removeAt(from); it.add(to, moved) }
            val nums = TogetherQueueLogic.renumbered(order.size)
            val update = HashMap<String, Any?>()
            order.forEachIndexed { i, q -> update["${q.id}/pos"] = nums[i] }
            db.getReference("rooms/$code/queue").updateChildren(update)
        } else {
            val np = TogetherQueueLogic.newPositionForMove(positions, from, to)
            db.getReference("rooms/$code/queue/${moved.id}/pos").setValue(np)
        }
    }

    /** Borra de Supabase el archivo que subí para una canción que ya terminó. */
    private suspend fun cleanupUpload(key: String?) {
        if (key == null) return
        val path = uploadedPaths.remove(key) ?: return
        persistUploadedPaths()
        try { SupabaseSalas.delete(listOf(path)) } catch (t: Throwable) { Log.w(TAG, "No se pudo borrar el archivo", t) }
    }

    // ---------- Administración de propietarios (solo anfitrión) ----------

    fun addOwner(uid: String) {
        val code = roomCode ?: return
        if (!isHost) return
        if (!canAddOwner) { toast(appContext.getString(R.string.tgq_max_owners, MAX_OWNERS)); return }
        val name = members.firstOrNull { it.uid == uid }?.name ?: return
        db.getReference("rooms/$code/owners/$uid")
            .setValue(mapOf("name" to name, "since" to ServerValue.TIMESTAMP))
    }

    fun removeOwner(uid: String) {
        val code = roomCode ?: return
        if (!isHost || uid == myUid()) return
        val theirQueue = queue.filter { it.addedBy == uid }.associate { it.id to (null as Any?) }
        db.getReference("rooms/$code/owners/$uid").removeValue()
        if (theirQueue.isNotEmpty()) db.getReference("rooms/$code/queue").updateChildren(theirQueue)
        db.getReference("rooms/$code/skipVotes/$uid").removeValue()
    }

    /**
     * Expulsar: la persona sale de la sala y ya no puede volver a entrar a ESTA sala
     * (a las demás sí). Solo el anfitrión; las reglas de Firebase también lo exigen.
     */
    // ================= Chat =================

    /** La pantalla del chat avisa si está abierta: al abrirla se borran los no leídos y la notificación. */
    fun setChatOpen(visible: Boolean) {
        chatVisible = visible
        if (visible) {
            chatUnread = 0; chatUnreadLines.clear()
            TogetherNotifications.cancelChat(appContext)
        }
    }

    private fun onChatArrived(fresh: List<ChatMessage>) {
        chatUnread += fresh.size
        fresh.forEach { chatUnreadLines.add("${it.name}: ${it.text}") }
        while (chatUnreadLines.size > 5) chatUnreadLines.removeAt(0)
        TogetherNotifications.showChat(appContext, chatUnread, chatUnreadLines.toList())
    }

    private fun chatFailed(t: Throwable) {
        Log.w(TAG, "Chat", t)
        val denied = t.message?.contains("permission denied", true) == true
        toast(appContext.getString(if (denied) R.string.tgm_firebase_denied else R.string.tgm_no_internet))
    }

    /** Envía [text] al chat de la sala. Devuelve true si se mandó (para que la UI limpie la caja). */
    fun sendChat(text: String): Boolean {
        val code = roomCode ?: return false
        val me = myUid() ?: return false
        if (status != Status.CONNECTED) return false
        val t = text.trim().take(CHAT_MAX_CHARS)
        if (t.isEmpty()) return false
        if (iAmChatMuted) { toast(appContext.getString(R.string.tgc_you_muted)); return false }
        val now = SystemClock.elapsedRealtime()
        if (now - lastChatSentAt < CHAT_MIN_INTERVAL_MS) return false   // anti-spam
        lastChatSentAt = now
        db.getReference("rooms/$code/chat").push().setValue(
            mapOf("uid" to me, "name" to displayName(), "text" to t, "at" to ServerValue.TIMESTAMP)
        ).addOnFailureListener { chatFailed(it) }
        return true
    }

    /** Cada quien borra lo suyo; los propietarios pueden borrar cualquier mensaje (no los avisos del sistema). */
    fun canDeleteChat(m: ChatMessage): Boolean = !m.isSystem && (m.uid == myUid() || isOwner)

    fun deleteChat(m: ChatMessage) {
        val code = roomCode ?: return
        if (!canDeleteChat(m)) return
        db.getReference("rooms/$code/chat/${m.id}").removeValue().addOnFailureListener { chatFailed(it) }
    }

    /** Un propietario puede expulsar/perdonar del chat a quien no sea propietario ni él mismo. */
    fun canModerateChat(m: ChatMessage): Boolean =
        isOwner && !m.isSystem && m.uid != myUid() && m.uid != hostUid && owners.none { it.uid == m.uid }

    /** Expulsa (muted = true) o perdona (false) a [m].uid del chat y deja un aviso discreto. */
    fun setChatMuted(m: ChatMessage, muted: Boolean) {
        val code = roomCode ?: return
        val me = myUid() ?: return
        if (!canModerateChat(m)) return
        val key = db.getReference("rooms/$code/chat").push().key ?: return
        val updates = HashMap<String, Any?>()
        updates["chatMuted/${m.uid}"] = if (muted) true else null
        updates["chat/$key"] = mapOf(
            "uid" to me,
            "name" to displayName(),
            "type" to "sys",
            "event" to (if (muted) "mute" else "unmute"),
            "text" to m.name.take(40),
            "at" to ServerValue.TIMESTAMP
        )
        db.getReference("rooms/$code").updateChildren(updates).addOnFailureListener { chatFailed(it) }
    }

    fun kickMember(uid: String) {
        val code = roomCode ?: return
        if (!isHost || uid == myUid()) return
        val name = members.firstOrNull { it.uid == uid }?.name ?: return
        val updates = HashMap<String, Any?>()
        updates["banned/$uid"] = true
        updates["members/$uid"] = null
        updates["owners/$uid"] = null
        queue.filter { it.addedBy == uid }.forEach { updates["queue/${it.id}"] = null }
        if (controllerUid == uid) {
            // Era quien controlaba la música: la sala queda libre y sigue el siguiente de la cola.
            updates["state"] = null
            updates["skipVotes"] = null
            updates["meta/controllerUid"] = null
        } else {
            updates["skipVotes/$uid"] = null
        }
        db.getReference("rooms/$code").updateChildren(updates)
            .addOnSuccessListener { toast(appContext.getString(R.string.tgq_kicked_toast, name)) }
            .addOnFailureListener { fail(it) }
    }

    /** Espera un margen (para no tocar a quien se está saliendo solo) y expulsa a los que aún sobran. */
    private fun scheduleOverflowEnforcement(code: String) {
        if (overflowJob?.isActive == true) return
        overflowJob = scope.launch {
            delay(OVERFLOW_GRACE_MS)
            if (roomCode != code || !isHost) return@launch
            val still = TogetherQueueLogic.overflowUids(
                members.map { TogetherQueueLogic.OnlineEntry(it.uid, it.joinedAt, it.isHost) }, MAX_MEMBERS
            )
            if (still.isEmpty()) return@launch
            val updates = HashMap<String, Any?>()
            still.forEach { extra ->
                updates["banned/$extra"] = true
                updates["members/$extra"] = null
                updates["owners/$extra"] = null
                updates["skipVotes/$extra"] = null
            }
            try { db.getReference("rooms/$code").updateChildren(updates).await() } catch (t: Throwable) {
                Log.w(TAG, "No se pudo aplicar el tope de miembros", t)
            }
        }
    }

    private suspend fun isBanned(code: String, uid: String): Boolean =
        try { db.getReference("rooms/$code/banned/$uid").get().await().exists() } catch (_: Throwable) { false }

    /** Al expulsarme pierdo el permiso de lectura: Firebase cancela el listener y aquí lo distingo de un fallo real. */
    private suspend fun handleMembersCancelled(code: String, uid: String, e: DatabaseError) {
        if (roomCode != code) return
        if (isBanned(code, uid)) {
            toast(appContext.getString(R.string.tgm_kicked_you))
            leaveInternal(removeRemote = false)
        } else {
            fail(e.toException(), appContext.getString(R.string.tgm_no_permission))
        }
    }

    /** Cambio de anfitrión: el elegido pasa a ser propietario (si no lo era) y anfitrión. */
    fun transferHost(uid: String) {
        val code = roomCode ?: return
        if (!isHost || uid == myUid()) return
        val name = members.firstOrNull { it.uid == uid }?.name ?: return
        scope.launch {
            try {
                if (owners.none { it.uid == uid }) {
                    if (!canAddOwner) { toast(appContext.getString(R.string.tgq_max_owners, MAX_OWNERS)); return@launch }
                    db.getReference("rooms/$code/owners/$uid")
                        .setValue(mapOf("name" to name, "since" to ServerValue.TIMESTAMP)).await()
                }
                db.getReference("rooms/$code/meta/hostName").setValue(name)
                db.getReference("rooms/$code/meta/hostUid").setValue(uid).await()
                toast(appContext.getString(R.string.tgq_host_changed, name))
            } catch (t: Throwable) {
                fail(t)
            }
        }
    }

    // ---------- Relevos automáticos ----------

    /** Sala libre y la primera de la cola es MÍA (y su autor está conectado): tomo el control. */
    private fun evaluateIdle() {
        if (status != Status.CONNECTED || controllerUid != null || claiming) return
        val me = myUid() ?: return
        if (!isOwner || queue.isEmpty()) return
        val idx = TogetherQueueLogic.firstPlayableIndex(queue.map { it.addedBy }, onlineUids())
        if (idx < 0 || queue[idx].addedBy != me) return
        if (idleJob?.isActive == true) return
        idleJob = scope.launch {
            delay(600)
            if (controllerUid != null || claiming) return@launch
            val code = roomCode ?: return@launch
            claiming = true
            try {
                if (claimTransaction(code, me) == Claim.WON) {
                    controllerUid = me; iHaveTrack = false
                    advanceQueue()
                }
            } finally {
                claiming = false
            }
        }
    }

    /** El controlador se cayó: el propietario conectado más antiguo toma el control tras una espera. */
    private fun evaluateTakeover() {
        if (status != Status.CONNECTED) return
        val ctrl = controllerUid
        if (ctrl == null || ctrl in onlineUids()) { takeoverJob?.cancel(); takeoverJob = null; return }
        val me = myUid() ?: return
        val winner = TogetherQueueLogic.oldestOnlineOwner(owners.associate { it.uid to it.since }, onlineUids(), exclude = ctrl)
        if (winner != me || takeoverJob?.isActive == true) return
        takeoverJob = scope.launch {
            delay(TAKEOVER_GRACE_MS)
            if (controllerUid != ctrl || ctrl in onlineUids()) return@launch
            val code = roomCode ?: return@launch
            claiming = true
            try {
                if (swapValue("rooms/$code/meta/controllerUid", ctrl, me)) {
                    controllerUid = me; iHaveTrack = false
                    try { db.getReference("rooms/$code/state").removeValue() } catch (_: Throwable) {}
                    advanceQueue()
                }
            } finally {
                claiming = false
            }
        }
    }

    /** El anfitrión se cayó: el propietario conectado más antiguo asciende a anfitrión (la sala sigue). */
    private fun evaluatePromotion() {
        if (status != Status.CONNECTED) return
        val host = hostUid
        if (host == null || host in onlineUids()) { promotionJob?.cancel(); promotionJob = null; return }
        val me = myUid() ?: return
        val winner = TogetherQueueLogic.oldestOnlineOwner(owners.associate { it.uid to it.since }, onlineUids(), exclude = host)
        if (winner != me || promotionJob?.isActive == true) return
        promotionJob = scope.launch {
            delay(PROMOTION_GRACE_MS)
            if (hostUid != host || host in onlineUids()) return@launch
            val code = roomCode ?: return@launch
            if (swapValue("rooms/$code/meta/hostUid", host, me)) {
                try { db.getReference("rooms/$code/meta/hostName").setValue(displayName()) } catch (_: Throwable) {}
            }
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

    /** Controlador: ¿a alguien le falta lo que suena? Si sí, prepara la pregunta de subir el archivo. */
    private fun evaluateMissing() {
        if (!isController || !iHaveTrack || status != Status.CONNECTED) { return }
        val song = playerManager.currentSong.value
        if (song == null) { uploadPrompt = null; return }
        val key = songKey(song.title, song.artist)
        val me = myUid()
        val who = members.filter { it.uid != me && it.online && it.missing == key }
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

    /** Mantiene la notificación del sistema (con Permitir / Negar) igual que el diálogo de la app. */
    private fun syncUploadNotification(p: UploadPrompt?) {
        if (p == notifiedPrompt) return
        notifiedPrompt = p
        try {
            if (p == null) TogetherNotifications.cancelUploadPrompt(appContext)
            else TogetherNotifications.showUploadPrompt(appContext, p)
        } catch (t: Throwable) {
            Log.w(TAG, "No se pudo mostrar la notificación de subida", t)
        }
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
            toast(appContext.getString(R.string.tgm_cannot_share))
            return
        }
        val ext = file.extension.lowercase().filter { it.isLetterOrDigit() }.take(5).ifBlank { "mp3" }
        val objectPath = "$code/${UUID.randomUUID()}.$ext"
        uploading = true
        transferText = "Subiendo «${song.title}»…"
        scope.launch {
            try {
                SupabaseSalas.upload(appContext, file, objectPath, SupabaseSalas.mimeFor(ext))
                uploadedPaths[key] = objectPath
                persistUploadedPaths()
                publishNow() // ahora el estado incluye filePath y los invitados la descargan
                toast(appContext.getString(R.string.tgm_song_shared, song.title))
            } catch (t: Throwable) {
                Log.e(TAG, "Falló la subida", t)
                declinedKeys += key
                toast(appContext.getString(R.string.tgm_upload_failed, t.message ?: appContext.getString(R.string.tgm_unknown_error)))
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
                if (!dest.isFile) SupabaseSalas.download(appContext, path, dest)
                downloaded[st.key] = Song(
                    id = -(abs(st.key.hashCode().toLong()) + 1000L), // id sintético negativo: no choca con la biblioteca
                    title = st.title,
                    artist = st.artist,
                    album = appContext.getString(com.music.musicflame.R.string.tg_title),
                    duration = st.duration,
                    path = dest.absolutePath
                )
                lastRequestedKey = null
                // Se aplica el ESTADO MÁS RECIENTE (la posición ya avanzó mientras se descargaba).
                latestHostState?.takeIf { it.key == st.key }?.let { applyHostState(it) }
            } catch (t: Throwable) {
                Log.e(TAG, "Falló la descarga", t)
                toast(appContext.getString(R.string.tgm_download_failed))
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
            ?: throw IllegalStateException(appContext.getString(R.string.tgm_firebase_signin_failed))
    }

    private fun displayName() = userName.trim().take(40).ifBlank { appContext.getString(R.string.tgm_guest) }

    private fun defaultName(): String =
        GoogleSignIn.getLastSignedInAccount(appContext)?.displayName ?: appContext.getString(R.string.tgm_guest)

    private fun persistRoom(code: String, host: Boolean) {
        roomCode = code; isHost = host
        prefs.edit().putString(KEY_ROOM, code).putBoolean(KEY_IS_HOST, host).apply()
    }

    private fun fail(t: Throwable, friendly: String? = null) {
        Log.e(TAG, "Error en el modo En compañía", t)
        val msg = friendly ?: when {
            t.message?.contains("permission denied", true) == true ->
                appContext.getString(R.string.tgm_firebase_denied)
            t.message?.contains("network", true) == true -> appContext.getString(R.string.tgm_no_internet)
            else -> t.message ?: appContext.getString(R.string.tgm_unknown_error_cap)
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
        /** Instancia activa, para que los botones de la notificación lleguen al manager. */
        @Volatile var current: TogetherManager? = null
        private const val CHAT_LIMIT = 100              // mensajes que se cargan/muestran
        private const val CHAT_MAX_CHARS = 300
        private const val CHAT_MIN_INTERVAL_MS = 700L
        const val MAX_OWNERS = 4                  // máximo 4 propietarios: el anfitrión + 3 más
        /** Página que abre la app con el código (ver docs/sala/index.html; hay que activar GitHub Pages). */
        const val WEB_LINK_BASE = "https://shimuronaga.github.io/MusicFlame/sala/#"
        private const val PUBLISH_EVERY_MS = 3000L
        private const val DRIFT_LIMIT_MS = 1500L
        private const val TAKEOVER_GRACE_MS = 8000L    // espera antes de relevar a un controlador caído
        private const val PROMOTION_GRACE_MS = 8000L   // espera antes de ascender si el anfitrión se cae
        private const val OVERFLOW_GRACE_MS = 5000L    // margen antes de expulsar a quien sobra del tope de miembros

        /** Acepta el código solo o el enlace musicflame://sala/ABC123. */
        fun parseCode(input: String): String =
            input.trim().substringAfterLast('/').substringBefore('?').uppercase().filter { it.isLetterOrDigit() }

        private fun songDisplay(title: String, artist: String) = if (artist.isBlank()) title else "$title – $artist"
        private fun normalize(s: String) = s.lowercase().filter { it.isLetterOrDigit() }
        private fun songKey(title: String, artist: String) = normalize(title) + "|" + normalize(artist)
    }
}
