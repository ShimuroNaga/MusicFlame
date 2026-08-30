package com.music.musicflame.data

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.annotation.OptIn
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.music.musicflame.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
class MusicPlayerManager(private val context: Context) {

    private var mediaController: MediaController? = null
    private val statsRepo = StatsRepository(context)

    // SEGURO: evita que onDisconnected() dispare una reconexión después de que
    // release() ya cerró todo a propósito (p.ej. MainActivity.onDestroy()).
    // Sin esto, cerrar la app podría abrir una conexión nueva "fantasma" justo
    // cuando el manager ya se está por descartar.
    private var isReleased = false

    // NUEVO: acciones en espera de que el MediaController termine de conectar
    // (la conexión es async, ver init{}). Usado para reproducir un archivo
    // recibido por Intent (ACTION_VIEW) apenas arranca MainActivity, sin
    // carreras contra la conexión al servicio en segundo plano.
    private val pendingActions = mutableListOf<() -> Unit>()

    // --- MEMORIA INTERNA ---
    private var currentPlaylist = listOf<Song>()
    private val playbackHistory = mutableListOf<Int>()

    // --- ESTADOS GLOBALES REACTIVOS ---
    private val _currentSong = mutableStateOf<Song?>(null)
    val currentSong: State<Song?> = _currentSong

    private val _isPlayingState = mutableStateOf(false)
    val isPlayingState: State<Boolean> = _isPlayingState

    private val _cycleMode = mutableIntStateOf(0)
    val cycleMode: State<Int> = _cycleMode

    // NUEVO: audioSessionId real de ExoPlayer, para enganchar el Visualizer en la UI.
    // MediaController no expone esta propiedad directo, se pide vía comando personalizado.
    private val _audioSessionId = mutableIntStateOf(0)
    val audioSessionId: State<Int> = _audioSessionId

    // --- COLA (QUEUE) REACTIVA ---
    // _queue siempre refleja el ORDEN REAL DE REPRODUCCIÓN (respeta el shuffle de
    // ExoPlayer si está activo), para que la pantalla de cola muestre "la próxima
    // canción" tal cual va a sonar y no el orden crudo en el que se agregaron.
    // queueWindowIndices guarda, en paralelo, el índice real (window index) de
    // cada canción dentro del MediaController, que es el que necesita moveMediaItem
    // para reordenar de verdad (independiente de si están mezcladas o no).
    private val _queue: SnapshotStateList<Song> = mutableStateListOf()
    val queue: List<Song> get() = _queue
    private var queueWindowIndices: List<Int> = emptyList()

    private val _shuffleEnabledState = mutableStateOf(false)
    val shuffleEnabledState: State<Boolean> = _shuffleEnabledState

    // --- SLEEP TIMER ---
    private val managerScope = CoroutineScope(Dispatchers.Main)
    private var sleepTimerJob: Job? = null

    // Modo "fin de canción actual": no hay cuenta regresiva, se pausa en el próximo
    // onMediaItemTransition disparado por avance automático (fin de la canción).
    private var sleepTimerEndOfSong = false

    private val _sleepTimerActive = mutableStateOf(false)
    val sleepTimerActive: State<Boolean> = _sleepTimerActive

    private val _sleepTimerRemainingMs = mutableLongStateOf(0L)
    val sleepTimerRemainingMs: State<Long> = _sleepTimerRemainingMs

    private val _sleepTimerEndOfSongState = mutableStateOf(false)
    val sleepTimerEndOfSongActive: State<Boolean> = _sleepTimerEndOfSongState

    /** Arranca el temporizador de apagado con una duración fija en minutos. */
    fun startSleepTimer(minutes: Int) {
        cancelSleepTimer()
        sleepTimerEndOfSong = false
        _sleepTimerEndOfSongState.value = false
        _sleepTimerActive.value = true
        _sleepTimerRemainingMs.longValue = minutes * 60_000L

        sleepTimerJob = managerScope.launch {
            while (isActive && _sleepTimerRemainingMs.longValue > 0) {
                delay(1000L)
                _sleepTimerRemainingMs.longValue = (_sleepTimerRemainingMs.longValue - 1000L).coerceAtLeast(0L)
            }
            if (isActive) {
                pause()
                _sleepTimerActive.value = false
            }
        }
    }

    /** Arranca el temporizador en modo "pausar al terminar la canción actual". */
    fun startSleepTimerEndOfSong() {
        cancelSleepTimer()
        sleepTimerEndOfSong = true
        _sleepTimerEndOfSongState.value = true
        _sleepTimerActive.value = true
        _sleepTimerRemainingMs.longValue = 0L
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        sleepTimerEndOfSong = false
        _sleepTimerEndOfSongState.value = false
        _sleepTimerActive.value = false
        _sleepTimerRemainingMs.longValue = 0L
    }

    // Se llama desde el listener de onMediaItemTransition ya existente cuando la canción
    // cambia por avance automático (o sea, terminó sola). Si el modo "fin de canción" está
    // activo, pausamos y apagamos el temporizador.
    private fun handleSongEndedForSleepTimer() {
        if (sleepTimerEndOfSong) {
            pause()
            cancelSleepTimer()
        }
    }

    // --- ESTADÍSTICAS ---
    private var listenedAccumMs = 0L

    private fun incrementPlayCountForCurrent(controller: Player) {
        controller.currentMediaItem?.mediaId?.toLongOrNull()?.let { statsRepo.incrementPlayCount(it) }
    }

    // Vuelca a disco el tiempo acumulado (en memoria) escuchado de la canción actual.
    // Se llama antes de cambiar de canción y al liberar el manager, para no perder
    // los últimos segundos que todavía no se habían guardado.
    private fun flushListenedTime() {
        val song = _currentSong.value
        if (song != null && listenedAccumMs > 0L) {
            statsRepo.addListenedTime(song.id, listenedAccumMs)
        }
        listenedAccumMs = 0L
    }

    // Corre mientras el manager esté vivo: cada segundo, si hay música sonando, suma
    // tiempo escuchado a la canción actual. Para no escribir a SharedPreferences cada
    // segundo, solo se vuelca a disco cada 5 segundos acumulados (o al cambiar de
    // canción / liberar el manager, vía flushListenedTime()).
    //
    // IMPORTANTE: antes esto asumía que entre cada vuelta del loop pasaba EXACTAMENTE
    // 1 segundo real (listenedAccumMs += 1000L a ciegas). Cuando el sistema operativo
    // suspende/congela el proceso en segundo plano (ahorro de batería, Doze, etc.) y
    // luego lo reanuda, ese supuesto no se cumple: puede pasar mucho más tiempo real
    // entre una vuelta y otra sin que la canción haya sonado de verdad, e igual se
    // sumaba como si hubiera sonado. Eso era lo que inflaba canciones de 3 minutos a
    // "21 horas" o "40 horas" de escucha. Ahora medimos el tiempo real transcurrido
    // (wall-clock) entre ticks y limitamos cuánto se puede sumar por tick, así nunca
    // se cuenta más tiempo del que realmente pudo haber sonado.
    private fun startStatsTicker() {
        managerScope.launch {
            var lastTickAt = System.currentTimeMillis()
            while (isActive) {
                delay(1000L)
                val now = System.currentTimeMillis()
                val elapsedMs = now - lastTickAt
                lastTickAt = now

                val controller = mediaController
                val song = _currentSong.value
                // Consultamos el estado real del reproductor (controller.isPlaying),
                // no solo el flag cacheado _isPlayingState, para no arrastrar un
                // "reproduciendo" viejo si el proceso estuvo suspendido y el flag
                // no llegó a actualizarse a tiempo.
                val reallyPlaying = controller?.isPlaying == true

                if (reallyPlaying && song != null) {
                    // Tope de seguridad: como mucho contamos 1.5x el segundo esperado
                    // por tick. Cualquier salto mayor (proceso congelado, reloj del
                    // sistema alterado, etc.) se descarta en vez de sumarse entero.
                    val safeDeltaMs = elapsedMs.coerceIn(0L, 1500L)
                    listenedAccumMs += safeDeltaMs
                    if (listenedAccumMs >= 5000L) {
                        statsRepo.addListenedTime(song.id, listenedAccumMs)
                        listenedAccumMs = 0L
                    }
                }
            }
        }
    }

    val cycleIconRes: Int
        get() = when (_cycleMode.intValue) {
            0 -> R.drawable.ic_straight_arrow
            1 -> R.drawable.ic_shuffle
            2 -> R.drawable.ic_autorenew
            3 -> R.drawable.ic_autoplay
            else -> R.drawable.ic_straight_arrow
        }

    init {
        connectController()
        startStatsTicker()
    }

    // ARREGLO: antes el MediaController se construía UNA sola vez acá adentro y
    // nunca más. Si el sistema (Doze, gestor de batería del fabricante, poca RAM
    // mientras la app estaba en 2do plano) mataba el proceso de
    // MusicPlaybackService, el binder se cortaba pero `mediaController` seguía
    // apuntando a un controller "zombie": skipNext()/seekTo() (y cualquier otro
    // comando) dejaban de hacer efecto EN SILENCIO, sin excepción, porque nunca
    // nos enterábamos de la desconexión. Salir de la app y volver "arreglaba" el
    // síntoma solo cuando esa vuelta alcanzaba a recrear la Activity (y con
    // ella, un MusicPlayerManager nuevo). Ahora, con setListener(...) +
    // onDisconnected, en cuanto se corta la conexión reconstruimos el
    // MediaController solos, sin depender de que el usuario reabra la app.
    private fun connectController() {
        if (isReleased) return
        val sessionToken = SessionToken(
            context,
            ComponentName(context, MusicPlaybackService::class.java)
        )

        val controllerFuture = MediaController.Builder(context, sessionToken)
            .setListener(object : MediaController.Listener {
                override fun onDisconnected(controller: MediaController) {
                    if (mediaController === controller) {
                        mediaController = null
                    }
                    if (!isReleased) {
                        Log.w("MusicPlayerManager", "MediaController desconectado, reconectando...")
                        connectController()
                    }
                }
            })
            .buildAsync()
        controllerFuture.addListener({
            val controller = controllerFuture.get()
            mediaController = controller

            // Sincronizar el estado inicial al conectar
            syncCycleModeState(controller)

            // NUEVO: sincroniza la canción actual y si está reproduciendo AHORA MISMO.
            // Es clave cuando el servicio ya venía reproduciendo música en segundo plano
            // (p.ej. cerraste la app desde "recientes" y la vuelves a abrir): sin esto,
            // el mini-reproductor aparecía vacío hasta la siguiente canción.
            syncCurrentSongState(controller)
            _isPlayingState.value = controller.isPlaying
            _shuffleEnabledState.value = controller.shuffleModeEnabled
            requestAudioSessionId(controller)
            refreshQueue(controller)

            // Disparamos cualquier acción que haya quedado esperando la conexión
            // (por ejemplo, reproducir un archivo abierto desde otra app antes de
            // que el MediaController terminara de conectar).
            if (pendingActions.isNotEmpty()) {
                val actionsToRun = pendingActions.toList()
                pendingActions.clear()
                actionsToRun.forEach { it() }
            }

            controller.addListener(object : Player.Listener {
                private var lastIndex = controller.currentMediaItemIndex

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    val currentIndex = controller.currentMediaItemIndex
                    if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                        if (lastIndex != C.INDEX_UNSET) {
                            playbackHistory.add(lastIndex)
                        }
                    }
                    // La canción anterior terminó sola, ya sea avanzando a la siguiente
                    // (AUTO) o repitiéndose a sí misma con "repetir una" activo (REPEAT).
                    // En ambos casos, si el sleep timer está en modo "fin de canción
                    // actual", pausamos aquí; si no, el modo repetir-una la dejaba sonar
                    // para siempre sin que el timer se enterara.
                    if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO || reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) {
                        handleSongEndedForSleepTimer()
                    }
                    // ESTADÍSTICAS: solo contamos aquí el salto manual (SEEK, botón
                    // siguiente/anterior). El avance automático y el loop de "repetir
                    // una" se cuentan en onPositionDiscontinuity de abajo, para no
                    // duplicar el conteo (ambos callbacks se disparan para esos casos).
                    if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK) {
                        incrementPlayCountForCurrent(controller)
                    }
                    lastIndex = currentIndex

                    syncCurrentSongState(controller)
                    requestAudioSessionId(controller)
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _isPlayingState.value = isPlaying
                    // NUEVO: en cuanto detectamos que de verdad se pausó (pérdida de
                    // audio focus, botón de pausa, auriculares desconectados, etc.),
                    // volcamos a disco lo acumulado hasta este instante en vez de
                    // esperar a los 5 segundos de margen o a que cambie de canción.
                    // Así el tiempo escuchado queda al día apenas se pausa, sin dejar
                    // nada "flotando" en memoria que dependa de un tick futuro.
                    if (!isPlaying) {
                        flushListenedTime()
                    }
                }

                // NUEVO: vía de respaldo para el mismo propósito que onIsPlayingChanged.
                // En algunos dispositivos/fabricantes, el callback de "isPlaying" no
                // siempre llega de forma confiable a través del binder cuando la app
                // está en segundo plano o el sistema recorta procesos. Este callback
                // se dispara en más situaciones (por ejemplo, pérdida de audio focus
                // que baja playWhenReady sin que isPlaying se entere a tiempo), y acá
                // simplemente volvemos a preguntarle a Media3 su estado real
                // (controller.isPlaying, que ya combina playWhenReady + estado de
                // reproducción + foco de audio) en vez de asumir nada por nuestra
                // cuenta.
                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                    val reallyPlaying = controller.isPlaying
                    _isPlayingState.value = reallyPlaying
                    if (!reallyPlaying) {
                        flushListenedTime()
                    }
                }

                // NUEVO: misma idea, para cuando el reproductor pasa a BUFFERING, IDLE
                // o ENDED (ninguno de esos estados es "reproduciendo de verdad", aunque
                // playWhenReady siga en true). Volvemos a preguntar el estado real acá
                // también, como tercera red de seguridad.
                override fun onPlaybackStateChanged(playbackState: Int) {
                    val reallyPlaying = controller.isPlaying
                    _isPlayingState.value = reallyPlaying
                    if (!reallyPlaying) {
                        flushListenedTime()
                    }
                }

                // RESPALDO: en algunos dispositivos/versiones de Media3, cuando "repetir una
                // canción" está activo, el MediaController no reenvía onMediaItemTransition
                // con razón REPEAT de forma confiable. onPositionDiscontinuity con razón
                // AUTO_TRANSITION sí se dispara siempre que la canción termina y vuelve a
                // empezar sola (incluyendo el loop de repetir-una), así que lo usamos como
                // segunda vía para el sleep timer, y también como el lugar donde contamos
                // las reproducciones que ocurren solas (avance automático o repetir-una).
                override fun onPositionDiscontinuity(
                    oldPosition: Player.PositionInfo,
                    newPosition: Player.PositionInfo,
                    reason: Int
                ) {
                    if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) {
                        handleSongEndedForSleepTimer()
                        incrementPlayCountForCurrent(controller)
                    }
                }

                // NUEVO: Escuchamos cambios desde la notificación para actualizar la UI en vivo
                override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                    syncCycleModeState(controller)
                    _shuffleEnabledState.value = shuffleModeEnabled
                    // Si se activa/desactiva el mezclar (mix) desde el mini-reproductor o
                    // desde la pantalla de cola, se re-arma el orden mostrado al instante.
                    refreshQueue(controller)
                }

                override fun onRepeatModeChanged(repeatMode: Int) {
                    syncCycleModeState(controller)
                }

                // NUEVO: se dispara cada vez que la lista de reproducción cambia de
                // verdad (se agregan canciones, se quitan, o se reordenan con
                // moveMediaItem) y también cuando ExoPlayer recalcula el orden
                // mezclado. Es el punto central para mantener la cola sincronizada.
                override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                    refreshQueue(controller)
                }
            })

        }, MoreExecutors.directExecutor())
    }

    // NUEVO: Método que averigua el estado actual real de Media3 y actualiza el icono
    private fun syncCycleModeState(controller: Player) {
        if (controller.shuffleModeEnabled) {
            _cycleMode.intValue = 1 // Aleatorio
        } else {
            when (controller.repeatMode) {
                Player.REPEAT_MODE_ALL -> _cycleMode.intValue = 2 // Repetir todas
                Player.REPEAT_MODE_ONE -> _cycleMode.intValue = 3 // Repetir una
                else -> _cycleMode.intValue = 0 // Normal (Flecha derecha)
            }
        }
    }

    // NUEVO: Averigua qué canción está sonando ahora en el reproductor y actualiza el estado reactivo.
    // Si el MediaItem no está en nuestra playlist en memoria (por ejemplo, porque la Activity se
    // recreó y perdió la lista al ser cerrada desde "recientes"), reconstruye un Song "ligero" a
    // partir de los metadatos que ya guarda ExoPlayer, para que el mini-reproductor no quede vacío.
    private fun syncCurrentSongState(controller: Player) {
        val mediaItem = controller.currentMediaItem
        if (mediaItem == null) {
            flushListenedTime()
            _currentSong.value = null
            return
        }

        val mediaId = mediaItem.mediaId
        // Si la canción cambió, volcamos a disco el tiempo acumulado de la anterior
        // antes de perder la referencia (si es la misma canción -ej. solo se refrescan
        // metadatos-, no hacemos nada acá).
        if (_currentSong.value?.id?.toString() != mediaId) {
            flushListenedTime()
        }
        val songFromPlaylist = currentPlaylist.find { it.id.toString() == mediaId }
        _currentSong.value = songFromPlaylist ?: buildSongFromMediaItem(mediaItem)
    }

    // NUEVO: le pide al servicio (que sí tiene el ExoPlayer real) el audioSessionId actual.
    // Es async porque sendCustomCommand cruza al proceso/hilo del MediaSession vía Binder.
    private fun requestAudioSessionId(controller: MediaController) {
        val future = controller.sendCustomCommand(
            SessionCommand(MusicPlaybackService.CUSTOM_COMMAND_GET_AUDIO_SESSION_ID, Bundle.EMPTY),
            Bundle.EMPTY
        )
        future.addListener({
            try {
                val result = future.get()
                val sessionId = result.extras.getInt(MusicPlaybackService.KEY_AUDIO_SESSION_ID, 0)
                if (sessionId != 0) {
                    _audioSessionId.intValue = sessionId
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, MoreExecutors.directExecutor())
    }

    private fun buildSongFromMediaItem(mediaItem: MediaItem): Song {
        val id = mediaItem.mediaId.toLongOrNull() ?: 0L

        // Primero probamos encontrar la canción REAL en la librería ya cacheada
        // (con su albumArtUri legítimo, tal cual lo arma SongRepository), en
        // vez de reconstruir siempre una versión "liviana" a partir de los
        // metadatos crudos del propio MediaItem.
        SongLibraryHolder.songs.find { it.id == id }?.let { return it }

        val metadata = mediaItem.mediaMetadata
        return Song(
            id = id,
            title = metadata.title?.toString() ?: "Desconocido",
            artist = metadata.artist?.toString() ?: "",
            duration = 0L, // La UI usa playerManager.duration en vivo, no este campo
            path = mediaItem.localConfiguration?.uri?.toString() ?: "",
            // OJO: nunca propagamos metadata.artworkUri acá. Para canciones sin
            // carátula personalizada, buildMediaItem() le pone a esa Uri el
            // esquema "empaquetado" de SongArtLoader (pensado solo para que el
            // BitmapLoader de la notificación lo resuelva); ningún otro
            // consumidor de la app lo entiende (por ejemplo, FullScreenPlayer
            // carga song.albumArtUri directo con Coil, sin ese fallback), así
            // que filtrarla acá causaba carátulas rotas o "de otra canción".
            // La dejamos en null a propósito: la UI ya sabe mostrar su
            // placeholder cuando no hay Uri.
            albumArtUri = null
        )
    }

    // Extraído de playSong() para poder reutilizarlo también al agregar canciones
    // a la cola (addToQueue) sin duplicar la construcción de metadata/artwork.
    private fun buildMediaItem(s: Song): MediaItem {
        val artUriString = s.albumArtUri?.toString()
        val finalArtworkUri: Uri = when {
            // Carátula elegida a mano por el usuario: es una Uri ya cargable
            // tal cual (content://, file://...), se respeta sin tocar.
            s.hasCustomCover && !artUriString.isNullOrEmpty() -> Uri.parse(artUriString)
            // Carátula "de fábrica": en vez de pasar directo la Uri genérica de
            // MediaStore por álbum (la que casi nunca resuelve en Android 10+),
            // le pedimos a SongArtBitmapLoader que primero saque la carátula
            // embebida REAL de este archivo puntual, y solo si el archivo no
            // trae ninguna, caiga a esa Uri genérica como respaldo.
            s.path.isNotEmpty() -> SongArtLoader.embeddedArtUri(s.path, artUriString)
            !artUriString.isNullOrEmpty() -> Uri.parse(artUriString)
            else -> Uri.parse("android.resource://${context.packageName}/${R.mipmap.ic_launcher}")
        }

        val metadata = MediaMetadata.Builder()
            .setTitle(s.title)
            .setArtist(s.artist)
            .setArtworkUri(finalArtworkUri)
            .build()

        return MediaItem.Builder()
            .setMediaId(s.id.toString())
            .setUri(s.path)
            .setMediaMetadata(metadata)
            .build()
    }

    fun playSong(song: Song, songList: List<Song>) {
        currentPlaylist = songList

        val mediaItems = songList.map { s -> buildMediaItem(s) }

        val startIndex = songList.indexOf(song)

        // Antes de arrancar la nueva canción, volcamos el tiempo escuchado que
        // quedó pendiente de la que estaba sonando (si había alguna).
        flushListenedTime()

        mediaController?.apply {
            val indexToPlay = if (startIndex >= 0) startIndex else 0
            playbackHistory.clear()
            setMediaItems(mediaItems, indexToPlay, 0)
            prepare()
            play()
            refreshQueue(this)
        }

        // ESTADÍSTICAS: esto cubre la selección inicial/manual de una canción desde
        // cualquier lista (Songs, Mix, Playlist, etc.), que dispara la transición con
        // razón PLAYLIST_CHANGED y no la contaba el listener de arriba.
        // Canciones externas (abiertas desde otra app, id sintético negativo, ver
        // MainActivity.buildSongFromExternalUri) no viven en la biblioteca real, así
        // que no tiene sentido acumularles estadísticas de reproducción.
        if (song.id > 0) {
            statsRepo.incrementPlayCount(song.id)
        }
    }

    // NUEVO: agrega canciones AL FINAL de la cola actual sin interrumpir lo que está
    // sonando (a diferencia de playSong, no llama a setMediaItems ni prepare/play).
    // Usado por el botón "+" de la pantalla de Cola dentro del reproductor a pantalla completa.
    fun addToQueue(songs: List<Song>) {
        val controller = mediaController ?: return
        if (songs.isEmpty()) return
        currentPlaylist = currentPlaylist + songs
        controller.addMediaItems(songs.map { s -> buildMediaItem(s) })
        refreshQueue(controller)
    }

    // NUEVO: recalcula el orden visible de la cola (_queue) a partir del Timeline
    // real de Media3, recorriéndolo en el mismo orden en que ExoPlayer va a
    // reproducirlo (respeta el shuffle activo). Guarda en paralelo el "window
    // index" real de cada canción para poder reordenarla después con moveMediaItem.
    private fun refreshQueue(controller: Player) {
        val timeline = controller.currentTimeline
        if (timeline.isEmpty) {
            _queue.clear()
            queueWindowIndices = emptyList()
            return
        }

        val shuffled = controller.shuffleModeEnabled
        val window = Timeline.Window()
        val order = mutableListOf<Int>()
        var idx = timeline.getFirstWindowIndex(shuffled)
        while (idx != C.INDEX_UNSET) {
            order.add(idx)
            idx = timeline.getNextWindowIndex(idx, Player.REPEAT_MODE_OFF, shuffled)
        }

        val newQueue = order.map { windowIndex ->
            val mediaId = timeline.getWindow(windowIndex, window).mediaItem.mediaId
            currentPlaylist.find { it.id.toString() == mediaId } ?: buildSongFromMediaItem(window.mediaItem)
        }

        queueWindowIndices = order
        _queue.clear()
        _queue.addAll(newQueue)
    }

    // NUEVO: reordena la cola por arrastre (drag & drop) desde la pantalla de Cola.
    // fromDisplayIndex/toDisplayIndex son posiciones dentro de la lista MOSTRADA en
    // pantalla (_queue, que ya viene en orden de reproducción real incluyendo shuffle).
    // Se traducen al "window index" real que Media3 necesita para moveMediaItem, así
    // que la canción arrastrada pasa a sonar justo en su nueva posición, mezclada o no.
    fun moveQueueItem(fromDisplayIndex: Int, toDisplayIndex: Int) {
        val controller = mediaController ?: return
        if (fromDisplayIndex == toDisplayIndex) return
        if (fromDisplayIndex !in queueWindowIndices.indices || toDisplayIndex !in queueWindowIndices.indices) return

        val fromWindow = queueWindowIndices[fromDisplayIndex]
        val toWindow = queueWindowIndices[toDisplayIndex]
        controller.moveMediaItem(fromWindow, toWindow)

        // Reflejamos el cambio YA en la lista mostrada (optimista), sin esperar a que
        // onTimelineChanged llegue de vuelta: así el drag se siente instantáneo. Igual
        // refreshQueue() se termina llamando solo por el listener y corrige cualquier
        // diferencia (por ejemplo, si el shuffle reacomoda algo más al reordenar).
        val moved = _queue.removeAt(fromDisplayIndex)
        _queue.add(toDisplayIndex, moved)
    }

    /**
     * Ejecuta [action] apenas el MediaController esté listo. Si ya está
     * conectado, corre de inmediato; si no, se encola y se dispara solo
     * cuando la conexión async del init{} termine.
     */
    fun whenReady(action: () -> Unit) {
        if (mediaController != null) {
            action()
        } else {
            pendingActions.add(action)
        }
    }

    fun togglePlayPause() {
        mediaController?.let { if (it.isPlaying) it.pause() else it.play() }
    }

    fun pause() { mediaController?.pause() }

    fun skipNext() {
        val controller = mediaController
        if (controller == null) {
            // DIAGNÓSTICO: si esto aparece en logcat justo cuando el botón
            // "Siguiente" no responde, confirma que el MediaController estaba
            // desconectado en ese momento (ver connectController() más arriba).
            Log.w("MusicPlayerManager", "skipNext() ignorado: mediaController es null")
            return
        }
        val currentIndex = controller.currentMediaItemIndex
        if (currentIndex != C.INDEX_UNSET) {
            playbackHistory.add(currentIndex)
        }
        controller.seekToNextMediaItem()
    }

    fun skipPrevious() {
        val controller = mediaController ?: return
        val currentPos = controller.currentPosition
        if (currentPos > 3000) {
            controller.seekTo(0)
        } else if (playbackHistory.isNotEmpty()) {
            val previousIndex = playbackHistory.removeAt(playbackHistory.size - 1)
            controller.seekToDefaultPosition(previousIndex)
        } else {
            controller.seekToPreviousMediaItem()
        }
    }

    // El comportamiento desde la UI ahora está sincronizado
    fun toggleCycleMode() {
        val controller = mediaController ?: return
        val nextMode = (_cycleMode.intValue + 1) % 4

        when (nextMode) {
            0 -> {
                controller.shuffleModeEnabled = false
                controller.repeatMode = Player.REPEAT_MODE_OFF
            }
            1 -> {
                controller.shuffleModeEnabled = true
                controller.repeatMode = Player.REPEAT_MODE_OFF
            }
            2 -> {
                controller.shuffleModeEnabled = false
                controller.repeatMode = Player.REPEAT_MODE_ALL
            }
            3 -> {
                controller.shuffleModeEnabled = false
                controller.repeatMode = Player.REPEAT_MODE_ONE
            }
        }
    }

    fun seekTo(positionMs: Long) {
        val controller = mediaController
        if (controller == null) {
            // DIAGNÓSTICO: mismo caso que en skipNext(). Si ves este log cuando
            // arrastrar la barra no responde, confirma la desconexión.
            Log.w("MusicPlayerManager", "seekTo() ignorado: mediaController es null")
            return
        }
        controller.seekTo(positionMs)
    }
    fun release() {
        isReleased = true
        cancelSleepTimer()
        flushListenedTime()
        mediaController?.release()
        mediaController = null
    }

    val isPlaying: Boolean get() = mediaController?.isPlaying == true
    val isShuffleEnabled: Boolean get() = mediaController?.shuffleModeEnabled == true
    val currentPosition: Long get() = mediaController?.currentPosition ?: 0L
    val duration: Long get() = mediaController?.duration ?: 0L
}