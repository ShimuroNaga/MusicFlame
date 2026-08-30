package com.music.musicflame.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.PresetReverb
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.music.musicflame.R
import com.music.musicflame.widget.MusicFlameVinylWidgetProvider
import com.music.musicflame.widget.MusicFlameWidgetProvider
import com.music.musicflame.widget.WidgetPrefs
import android.media.AudioManager

@OptIn(UnstableApi::class)
class MusicPlaybackService : MediaSessionService() {

    companion object {
        // Públicas para que MusicPlayerManager pueda pedir el audioSessionId sin duplicar strings
        const val CUSTOM_COMMAND_GET_AUDIO_SESSION_ID = "com.music.musicflame.GET_AUDIO_SESSION_ID"
        const val KEY_AUDIO_SESSION_ID = "audio_session_id"

        // Cada cuánto se revisa si la línea de letra activa cambió mientras suena
        // la canción. No hace falta más precisión que esto para que el widget se
        // sienta "en vivo": las líneas LRC casi nunca duran menos de un par de
        // segundos, y actualizar el widget más seguido solo gasta batería sin
        // aportar nada perceptible.
        private const val LYRICS_TICK_INTERVAL_MS = 400L
    }

    private var mediaSession: MediaSession? = null
    lateinit var player: ExoPlayer

    // EFECTOS DE AUDIO
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var presetReverb: PresetReverb? = null

    // MEMORIA DE VALORES
    private var currentBass = 0f
    private var currentVirtualizer = 0f
    private var currentBands = floatArrayOf(0f, 0f, 0f, 0f, 0f)
    private var currentLoudness = 0f
    private var currentReverb = 0

    private lateinit var sharedPrefs: SharedPreferences

    // --- LETRA EN VIVO EN EL WIDGET ---
    private lateinit var lyricsRepo: LyricsRepository
    private lateinit var lyricsSettingsRepo: SettingsRepository
    private var currentParsedLyrics: ParsedLyrics = ParsedLyrics.EMPTY
    private var lastAppliedLyricIndex: Int = -1
    private var lyricsTickingActive = false
    private val lyricsTickHandler = Handler(Looper.getMainLooper())
    private val lyricsTickRunnable = object : Runnable {
        override fun run() {
            updateLyricLinesIfNeeded(player.currentMediaItem?.mediaId)
            lyricsTickHandler.postDelayed(this, LYRICS_TICK_INTERVAL_MS)
        }
    }

    // NOTIFICACIÓN Y ACCIONES CUSTOM
    private val CUSTOM_COMMAND_FAVORITE = "com.music.musicflame.FAVORITE"
    private val CUSTOM_COMMAND_CYCLE_MODE = "com.music.musicflame.CYCLE_MODE" // NUEVO: ÚNICO BOTÓN CÍCLICO

    private var isCurrentSongFavorite = false
    private val PREF_FAVORITES_KEY = "favorite_songs_set"


    // RECEPTOR DEL "MISIL" DE DATOS DESDE LA UI
    private val eqUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.music.musicflame.UPDATE_EQ") {
                if (intent.hasExtra("bass_boost")) {
                    currentBass = intent.getFloatExtra("bass_boost", 0f)
                    currentVirtualizer = intent.getFloatExtra("virtualizer", 0f)
                    currentLoudness = intent.getFloatExtra("loudness", 0f)
                    currentReverb = intent.getIntExtra("reverb", 0)

                    for (i in 0 until 5) {
                        currentBands[i] = intent.getFloatExtra("eq_band_$i", 0f)
                    }
                }
                applyAudioSettings()
            }
        }
    }

    // RECEPTOR DE DESCONEXIÓN DE AUDÍFONOS/BLUETOOTH (pausa automática)
    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                val pauseOnDisconnect = sharedPrefs.getBoolean("pause_on_disconnect", true)
                if (pauseOnDisconnect && player.isPlaying) {
                    player.pause()
                }
            }
        }
    }

    // RECEPTOR DE PANTALLA APAGADA/ENCENDIDA (ahorro de batería del widget Vinilo).
    // ACTION_SCREEN_OFF/ON son "implicit broadcasts": Android ya no los entrega a
    // receivers declarados en el Manifest desde la API 26, así que SÍ o SÍ hay que
    // registrarlos así, en runtime, igual que noisyReceiver de arriba.
    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> MusicFlameVinylWidgetProvider.onScreenStateChanged(this@MusicPlaybackService, isScreenOnNow = false)
                Intent.ACTION_SCREEN_ON -> MusicFlameVinylWidgetProvider.onScreenStateChanged(this@MusicPlaybackService, isScreenOnNow = true)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        sharedPrefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        lyricsRepo = LyricsRepository(this)
        lyricsSettingsRepo = SettingsRepository(this)

        // Cargar valores iniciales
        currentBass = sharedPrefs.getFloat("bass_boost", 0f)
        currentVirtualizer = sharedPrefs.getFloat("virtualizer", 0f)
        currentLoudness = sharedPrefs.getFloat("loudness_enhancer", 0f)
        currentReverb = sharedPrefs.getInt("reverb_preset", 0)

        for (i in 0 until 5) {
            currentBands[i] = sharedPrefs.getFloat("eq_band_$i", 0f)
        }

        val filter = IntentFilter("com.music.musicflame.UPDATE_EQ")
        registerReceiver(eqUpdateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)

        // Registro del receiver de "audio ruidoso" (desconexión de Bluetooth/audífonos).
        // Este SÍ debe registrarse SIN Context.RECEIVER_NOT_EXPORTED, porque
        // ACTION_AUDIO_BECOMING_NOISY lo dispara el propio sistema Android, no nuestra app.
        val noisyFilter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        registerReceiver(noisyReceiver, noisyFilter)

        // Registro del receiver de pantalla apagada/encendida (mismo motivo que
        // noisyReceiver: son broadcasts del propio sistema, sin NOT_EXPORTED).
        val screenFilter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        registerReceiver(screenStateReceiver, screenFilter)

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .build()

        // Listener para actualizar efectos y refrescar la notificación si el estado cambia por otro medio
        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                if (audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
                    initAudioEffects(audioSessionId)
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    val sessionId = player.audioSessionId
                    if (sessionId != C.AUDIO_SESSION_ID_UNSET) {
                        initAudioEffects(sessionId)
                    }
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                super.onMediaItemTransition(mediaItem, reason)
                checkIfCurrentSongIsFavorite()
                syncWidgetState()
                loadLyricsForCurrentSong()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                syncWidgetState()
                if (isPlaying) startLyricsTickingIfNeeded() else stopLyricsTicking()
            }

            // NUEVO: única enganchada real que hace falta para la letra en vivo del
            // widget. El tick periódico de arriba cubre el avance normal de la
            // canción, pero no reacciona a saltos (el usuario arrastra la barra de
            // progreso, toca una línea de la letra en FullScreenPlayer, o un
            // "repetir una" reinicia la posición a 0): onPositionDiscontinuity SÍ
            // se dispara siempre en esos casos, así que lo usamos para resincronizar
            // la línea activa al instante en vez de esperar hasta 400ms.
            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                updateLyricLinesIfNeeded(player.currentMediaItem?.mediaId, force = true)
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                mediaSession?.setCustomLayout(getCustomLayout())
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                mediaSession?.setCustomLayout(getCustomLayout())
            }
        })

        mediaSession = MediaSession.Builder(this, player)
            .setCallback(CustomMediaSessionCallback())
            // ARREGLO carátula en la notificación: sin este BitmapLoader, Media3
            // intenta cargar directamente la Uri "de fábrica" de MediaStore por
            // álbum (deprecada en Android 10+) y falla en silencio. Ver
            // SongArtBitmapLoader/SongArtLoader para el detalle del fallback.
            .setBitmapLoader(SongArtBitmapLoader(this))
            .build()

        // Registramos el proveedor de notificación personalizado (ver la clase
        // OrderedMediaNotificationProvider al final del archivo) para fijar el orden
        // exacto de los botones: Cíclico - Anterior - Play/Pausa - Siguiente - Favorito.
        setMediaNotificationProvider(OrderedMediaNotificationProvider(this))
    }

    /**
     * Escribe el estado actual (canción + play/pause) en el "buzón" que lee el widget
     * de home screen, y le pide que se repinte. Barato de llamar: si el usuario no tiene
     * el widget añadido, refreshAllWidgets() no hace nada.
     */
    private fun syncWidgetState() {
        val mediaItem = player.currentMediaItem
        val hasSong = mediaItem != null
        val metadata = mediaItem?.mediaMetadata

        WidgetPrefs.save(
            context = this,
            hasSong = hasSong,
            title = metadata?.title?.toString() ?: "",
            artist = metadata?.artist?.toString() ?: "",
            albumArtUri = metadata?.artworkUri?.toString(),
            isPlaying = player.isPlaying,
            mediaId = mediaItem?.mediaId
        )
        MusicFlameWidgetProvider.refreshAllWidgets(this)

        // Widget "Vinilo" (catálogo cosmético punto 5): mismo punto de enganche,
        // pinta el estado y arranca/detiene el giro del disco según corresponda.
        MusicFlameVinylWidgetProvider.onPlaybackStateChanged(
            context = this,
            isPlaying = player.isPlaying,
            hasSong = hasSong,
            albumArtUri = metadata?.artworkUri?.toString(),
            mediaId = mediaItem?.mediaId
        )
    }

    /**
     * Carga y parsea la letra guardada de la canción que acaba de empezar a
     * sonar (lectura local, LyricsRepository ya la resolvió de antemano vía
     * LyricsView/scanLibrary; acá NO se busca online, solo se lee lo que ya
     * hay guardado). Si no hay letra o no está sincronizada, deja
     * currentParsedLyrics vacía y el widget simplemente sigue mostrando el
     * nombre del artista como siempre.
     */
    private fun loadLyricsForCurrentSong() {
        val mediaItem = player.currentMediaItem
        val mediaId = mediaItem?.mediaId
        val songId = mediaId?.toLongOrNull()

        lastAppliedLyricIndex = -1
        WidgetPrefs.clearLyricsLines(this)

        currentParsedLyrics = songId?.let { id ->
            lyricsRepo.getLyrics(id)?.let { stored -> LyricsParser.parse(stored.raw) }
        } ?: ParsedLyrics.EMPTY

        stopLyricsTicking()
        updateLyricLinesIfNeeded(mediaId, force = true)
        if (player.isPlaying) startLyricsTickingIfNeeded()
    }

    /**
     * Recalcula la línea activa para la posición actual de reproducción y, si
     * cambió (o si [force]), la guarda para el widget junto con hasta 2 líneas
     * siguientes de contexto. No hace nada si la letra no está sincronizada,
     * si el usuario apagó "Letra en el widget" en Ajustes, o si no hay ningún
     * widget añadido (barato de llamar siempre desde el tick y desde
     * onPositionDiscontinuity).
     */
    private fun updateLyricLinesIfNeeded(mediaId: String?, force: Boolean = false) {
        if (mediaId == null) return
        if (!currentParsedLyrics.isSynced || currentParsedLyrics.lines.isEmpty()) return
        if (!lyricsSettingsRepo.isLyricsInWidgetEnabled()) return
        if (!MusicFlameWidgetProvider.hasWidgets(this)) return

        val activeIndex = currentParsedLyrics.activeIndex(player.currentPosition)
        if (!force && activeIndex == lastAppliedLyricIndex) return
        lastAppliedLyricIndex = activeIndex

        // Antes de la primera marca de tiempo (activeIndex == -1) mostramos las
        // primeras líneas igual, para que el widget no se quede vacío desde el
        // segundo 0 de la canción.
        // Antes solo se generaban 3 líneas (lo único que consumían wide/compact).
        // Ahora se generan hasta WidgetPrefs.MAX_LYRICS_CONTEXT_LINES para que la
        // variante cuadrada tenga contexto de sobra; wide/compact no cambian en
        // nada porque solo leen lines[0..2] como siempre.
        val startIndex = if (activeIndex >= 0) activeIndex else 0
        val lines = (startIndex until startIndex + WidgetPrefs.MAX_LYRICS_CONTEXT_LINES)
            .mapNotNull { currentParsedLyrics.lines.getOrNull(it)?.text }

        WidgetPrefs.saveLyricsLines(this, mediaId, lines)
        MusicFlameWidgetProvider.refreshAllWidgets(this)
    }

    /** Arranca el tick de 400ms SOLO si de verdad hace falta (barato de llamar seguido). */
    private fun startLyricsTickingIfNeeded() {
        if (lyricsTickingActive) return
        if (!player.isPlaying) return
        if (!currentParsedLyrics.isSynced || currentParsedLyrics.lines.isEmpty()) return
        if (!lyricsSettingsRepo.isLyricsInWidgetEnabled()) return
        if (!MusicFlameWidgetProvider.hasWidgets(this)) return

        lyricsTickingActive = true
        lyricsTickHandler.postDelayed(lyricsTickRunnable, LYRICS_TICK_INTERVAL_MS)
    }

    private fun stopLyricsTicking() {
        if (!lyricsTickingActive) return
        lyricsTickingActive = false
        lyricsTickHandler.removeCallbacks(lyricsTickRunnable)
    }

    private fun checkIfCurrentSongIsFavorite() {
        val currentMediaId = player.currentMediaItem?.mediaId ?: return
        val favoritesSet = sharedPrefs.getStringSet(PREF_FAVORITES_KEY, mutableSetOf()) ?: mutableSetOf()

        isCurrentSongFavorite = favoritesSet.contains(currentMediaId)
        mediaSession?.setCustomLayout(getCustomLayout())
    }

    // --- LÓGICA DE LA NOTIFICACIÓN Y BOTONES EN 2DO PLANO ---
    private inner class CustomMediaSessionCallback : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val connectionResult = super.onConnect(session, controller)

            // Declaramos nuestros comandos personalizados actualizados
            val sessionCommands = connectionResult.availableSessionCommands.buildUpon()
                .add(SessionCommand(CUSTOM_COMMAND_FAVORITE, Bundle.EMPTY))
                .add(SessionCommand(CUSTOM_COMMAND_CYCLE_MODE, Bundle.EMPTY))
                .add(SessionCommand(CUSTOM_COMMAND_GET_AUDIO_SESSION_ID, Bundle.EMPTY))
                .build()

            return MediaSession.ConnectionResult.accept(
                sessionCommands,
                connectionResult.availablePlayerCommands
            )
        }

        override fun onPostConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ) {
            session.setCustomLayout(controller, getCustomLayout())
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            // NUEVO: el visualizador de audio en la UI necesita el audioSessionId real de
            // ExoPlayer para engancharse con android.media.audiofx.Visualizer. El MediaController
            // no expone esta propiedad (no es parte de la interfaz Player genérica), así que se
            // la mandamos como un comando de sesión aparte, con return inmediato.
            if (customCommand.customAction == CUSTOM_COMMAND_GET_AUDIO_SESSION_ID) {
                val resultBundle = Bundle().apply {
                    putInt(KEY_AUDIO_SESSION_ID, player.audioSessionId)
                }
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS, resultBundle))
            }

            var layoutNeedsUpdate = false

            when (customCommand.customAction) {
                CUSTOM_COMMAND_FAVORITE -> {
                    val currentMediaId = player.currentMediaItem?.mediaId
                    if (currentMediaId != null) {
                        isCurrentSongFavorite = !isCurrentSongFavorite

                        val favoritesSet = sharedPrefs.getStringSet(PREF_FAVORITES_KEY, mutableSetOf())?.toMutableSet() ?: mutableSetOf()
                        if (isCurrentSongFavorite) {
                            favoritesSet.add(currentMediaId)
                        } else {
                            favoritesSet.remove(currentMediaId)
                        }
                        sharedPrefs.edit().putStringSet(PREF_FAVORITES_KEY, favoritesSet).apply()

                        val intent = Intent("com.music.musicflame.FAVORITES_CHANGED")
                        intent.setPackage(packageName)
                        intent.putExtra("mediaId", currentMediaId)
                        intent.putExtra("isFavorite", isCurrentSongFavorite)
                        sendBroadcast(intent)

                        layoutNeedsUpdate = true
                    }
                }
                CUSTOM_COMMAND_CYCLE_MODE -> {
                    // 1. Averiguamos en qué estado estamos actualmente
                    val currentCycleState = if (player.shuffleModeEnabled) 1 else {
                        when (player.repeatMode) {
                            Player.REPEAT_MODE_ALL -> 2
                            Player.REPEAT_MODE_ONE -> 3
                            else -> 0
                        }
                    }

                    // 2. Pasamos al siguiente estado (0, 1, 2, 3 -> vuelve a 0)
                    val nextState = (currentCycleState + 1) % 4

                    // 3. Aplicamos la orden al reproductor
                    when (nextState) {
                        0 -> { player.shuffleModeEnabled = false; player.repeatMode = Player.REPEAT_MODE_OFF }
                        1 -> { player.shuffleModeEnabled = true; player.repeatMode = Player.REPEAT_MODE_OFF }
                        2 -> { player.shuffleModeEnabled = false; player.repeatMode = Player.REPEAT_MODE_ALL }
                        3 -> { player.shuffleModeEnabled = false; player.repeatMode = Player.REPEAT_MODE_ONE }
                    }
                    layoutNeedsUpdate = true
                }
            }

            if (layoutNeedsUpdate) {
                // Antes esto se llamaba dos veces seguidas (una por controller y otra global),
                // lo que disparaba dos actualizaciones de notificación casi simultáneas y
                // provocaba que el sistema "pisara" un render con el otro, dejando botones
                // trabados o superpuestos. Con una sola llamada global es suficiente: se
                // propaga a todos los controllers conectados (incluida la notificación).
                session.setCustomLayout(getCustomLayout())
            }

            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }

    private fun getCustomLayout(): ImmutableList<CommandButton> {
        // NOTA: el orden final y los índices de vista compacta ya NO se deciden acá.
        // Esta lista solo declara QUÉ botones personalizados existen (Favorito y
        // Cíclico); el ORDEN real en el que aparecen en la notificación (Cíclico -
        // Anterior - Play/Pausa - Siguiente - Favorito) lo arma
        // OrderedMediaNotificationProvider.getMediaButtons() al final de este archivo,
        // que es el que de verdad decide cómo se intercalan con los controles nativos.

        // 1. Botón Favorito (Corazón)
        val favoriteIcon = if (isCurrentSongFavorite) R.drawable.ic_favorite_on else R.drawable.ic_favorite_off
        val favoriteButton = CommandButton.Builder()
            .setDisplayName("Favorito")
            .setSessionCommand(SessionCommand(CUSTOM_COMMAND_FAVORITE, Bundle.EMPTY))
            .setIconResId(favoriteIcon)
            .build()

        // 2. Botón Cíclico Único (Calculamos el estado actual)
        val cycleState = if (player.shuffleModeEnabled) 1 else {
            when (player.repeatMode) {
                Player.REPEAT_MODE_ALL -> 2
                Player.REPEAT_MODE_ONE -> 3
                else -> 0
            }
        }

        // Le damos icono y texto en base al orden: Normal -> Aleatorio -> Repetir Todo -> Repetir Una
        val (cycleIcon, cycleTitle) = when (cycleState) {
            0 -> Pair(R.drawable.ic_straight_arrow, "Normal")
            1 -> Pair(R.drawable.ic_shuffle, "Aleatorio")
            2 -> Pair(R.drawable.ic_autorenew, "Repetir Todo")
            3 -> Pair(R.drawable.ic_autoplay, "Repetir Una")
            else -> Pair(R.drawable.ic_straight_arrow, "Normal")
        }

        val cycleButton = CommandButton.Builder()
            .setDisplayName(cycleTitle)
            .setSessionCommand(SessionCommand(CUSTOM_COMMAND_CYCLE_MODE, Bundle.EMPTY))
            .setIconResId(cycleIcon)
            .build()

        // El reproductor nativo añade automáticamente Anterior, Play/Pausa y Siguiente
        return ImmutableList.of(cycleButton, favoriteButton)
    }

    // --- LÓGICA DE AUDIO EFECTOS ---
    private fun initAudioEffects(audioSessionId: Int) {
        equalizer?.release()
        bassBoost?.release()
        virtualizer?.release()
        loudnessEnhancer?.release()
        presetReverb?.release()

        try {
            equalizer = Equalizer(1000, audioSessionId)
            bassBoost = BassBoost(1000, audioSessionId)
            virtualizer = Virtualizer(1000, audioSessionId)
            loudnessEnhancer = LoudnessEnhancer(audioSessionId)
            presetReverb = PresetReverb(1000, audioSessionId)

            applyAudioSettings()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun applyAudioSettings() {
        try {
            bassBoost?.let { boost ->
                if (boost.strengthSupported) {
                    boost.enabled = true
                    boost.setStrength((currentBass * 10).toInt().toShort())
                }
            }

            virtualizer?.let { virt ->
                if (virt.strengthSupported) {
                    virt.enabled = true
                    virt.setStrength((currentVirtualizer * 10).toInt().toShort())
                }
            }

            loudnessEnhancer?.let { loud ->
                loud.enabled = true
                loud.setTargetGain((currentLoudness * 20).toInt())
            }

            presetReverb?.let { reverb ->
                reverb.enabled = true
                val presetValue = when (currentReverb) {
                    0 -> PresetReverb.PRESET_NONE
                    1 -> PresetReverb.PRESET_SMALLROOM
                    2 -> PresetReverb.PRESET_MEDIUMROOM
                    3 -> PresetReverb.PRESET_LARGEROOM
                    4 -> PresetReverb.PRESET_MEDIUMHALL
                    5 -> PresetReverb.PRESET_LARGEHALL
                    6 -> PresetReverb.PRESET_PLATE
                    else -> PresetReverb.PRESET_NONE
                }
                reverb.preset = presetValue
            }

            equalizer?.let { eq ->
                eq.enabled = true
                for (i in 0 until eq.numberOfBands) {
                    val level = currentBands[i]
                    val minMilliBels = eq.bandLevelRange[0]
                    val maxMilliBels = eq.bandLevelRange[1]

                    val calculatedMilliBels = if (level >= 0) {
                        (level * maxMilliBels).toInt().toShort()
                    } else {
                        (kotlin.math.abs(level) * minMilliBels).toInt().toShort()
                    }
                    eq.setBandLevel(i.toShort(), calculatedMilliBels)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    // NUEVO: controla explícitamente qué pasa cuando el usuario cierra la app desde "recientes".
    // Si tiene activado "Reproducir en segundo plano", no hacemos nada: el foreground service
    // sigue vivo y ExoPlayer sigue sonando. Si lo tiene desactivado, cortamos todo aquí mismo
    // en vez de depender del comportamiento por defecto de MediaSessionService.
    override fun onTaskRemoved(rootIntent: Intent?) {
        val playInBackground = sharedPrefs.getBoolean("play_in_background", true)
        if (!playInBackground) {
            player.pause()
            player.stop()
            stopSelf()
        }
    }

    override fun onDestroy() {
        stopLyricsTicking()
        MusicFlameVinylWidgetProvider.stopRotation()
        unregisterReceiver(eqUpdateReceiver)
        unregisterReceiver(noisyReceiver)
        unregisterReceiver(screenStateReceiver)
        equalizer?.release()
        bassBoost?.release()
        virtualizer?.release()
        loudnessEnhancer?.release()
        presetReverb?.release()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    /**
     * Proveedor de notificación personalizado. Controla el ORDEN EXACTO de los botones
     * y arregla el bug de los botones "trabados"/reordenados al azar (visto en las
     * capturas: Favorito, Anterior, Pausa, Cíclico... y Siguiente directamente
     * desaparecido).
     *
     * Por defecto, Media3 arma la notificación así: primero SIEMPRE Anterior /
     * Play-Pausa / Siguiente (en ese orden fijo, sin forma de moverlos), y DESPUÉS
     * pega los botones personalizados (Cíclico, Favorito) al final. Por eso nunca se
     * podía dejar el Cíclico primero solo reordenando getCustomLayout().
     *
     * Además, antes solo se marcaba el índice de vista compacta en el botón de
     * Favorito (índice 0) y en ningún otro. Eso desactivaba por completo la selección
     * automática que hace Media3 para Anterior/Play-Pausa/Siguiente (que solo se
     * activa si NINGÚN botón declara un índice explícito), dejando el resultado en
     * manos de cómo cada fabricante arma su propio widget de notificación/pantalla de
     * bloqueo — lo que se veía como orden inestable y el botón de Siguiente
     * "comido".
     *
     * Ahora se arma la lista completa a mano, en el orden pedido:
     *   [Cíclico] - [Anterior] - [Play/Pausa] - [Siguiente] - [Favorito]
     * y se fija el índice de vista compacta EXPLÍCITO en los 3 controles nativos
     * (0, 1 y 2), para que siempre viajen juntos y en orden en la vista compacta,
     * dejando Cíclico y Favorito solo para la vista expandida/completa.
     */
    private inner class OrderedMediaNotificationProvider(context: Context) :
        DefaultMediaNotificationProvider(context) {

        override fun getMediaButtons(
            session: MediaSession,
            playerCommands: Player.Commands,
            customLayout: ImmutableList<CommandButton>,
            showPauseButton: Boolean
        ): ImmutableList<CommandButton> {
            val cycleButton = customLayout.firstOrNull {
                it.sessionCommand?.customAction == CUSTOM_COMMAND_CYCLE_MODE
            }
            val favoriteButton = customLayout.firstOrNull {
                it.sessionCommand?.customAction == CUSTOM_COMMAND_FAVORITE
            }

            val buttons = ImmutableList.Builder<CommandButton>()

            // 1. Cíclico (mezclar / repetir todo / repetir una), primero de todos.
            cycleButton?.let { buttons.add(it) }

            // 2. Anterior — índice de vista compacta fijo en 0.
            if (playerCommands.containsAny(
                    Player.COMMAND_SEEK_TO_PREVIOUS,
                    Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM
                )
            ) {
                val extras = Bundle().apply { putInt(COMMAND_KEY_COMPACT_VIEW_INDEX, 0) }
                buttons.add(
                    CommandButton.Builder()
                        .setPlayerCommand(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                        .setIconResId(R.drawable.ic_widget_skip_previous)
                        .setDisplayName("Anterior")
                        .setExtras(extras)
                        .build()
                )
            }

            // 3. Play/Pausa — índice de vista compacta fijo en 1.
            if (playerCommands.contains(Player.COMMAND_PLAY_PAUSE)) {
                val extras = Bundle().apply { putInt(COMMAND_KEY_COMPACT_VIEW_INDEX, 1) }
                buttons.add(
                    CommandButton.Builder()
                        .setPlayerCommand(Player.COMMAND_PLAY_PAUSE)
                        .setIconResId(if (showPauseButton) R.drawable.ic_widget_pause else R.drawable.ic_widget_play)
                        .setDisplayName(if (showPauseButton) "Pausar" else "Reproducir")
                        .setExtras(extras)
                        .build()
                )
            }

            // 4. Siguiente — índice de vista compacta fijo en 2. Este es justo el botón
            // que se estaba perdiendo en tus capturas: al darle un índice explícito
            // (igual que Anterior y Play/Pausa) queda garantizado que viaje siempre
            // junto a ellos en la vista compacta, sin depender del criterio del
            // fabricante del teléfono.
            if (playerCommands.containsAny(
                    Player.COMMAND_SEEK_TO_NEXT,
                    Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM
                )
            ) {
                val extras = Bundle().apply { putInt(COMMAND_KEY_COMPACT_VIEW_INDEX, 2) }
                buttons.add(
                    CommandButton.Builder()
                        .setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                        .setIconResId(R.drawable.ic_widget_skip_next)
                        .setDisplayName("Siguiente")
                        .setExtras(extras)
                        .build()
                )
            }

            // 5. Favorito, al final de todos, tal como se pidió.
            favoriteButton?.let { buttons.add(it) }

            return buttons.build()
        }
    }
}