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
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
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
import com.music.musicflame.audio.ProBiquadEqualizerAudioProcessor
import com.music.musicflame.audio.VolumeNormalizationAnalyzer
import com.music.musicflame.widget.MusicFlameVinylWidgetProvider
import com.music.musicflame.widget.MusicFlameWidgetProvider
import com.music.musicflame.widget.WidgetPrefs
import android.media.AudioManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    // ARREGLO GRATIS: guarda qué audioSessionId ya tiene los 5 efectos creados, para
    // no recrearlos (release() + constructor nuevo) en cada cambio de canción. Antes
    // onPlaybackStateChanged(STATE_READY) llamaba a initAudioEffects() en CADA
    // transición de canción, aunque el audioSessionId real de ExoPlayer casi siempre
    // es el mismo durante toda la sesión de reproducción — eso generaba un
    // micro-pop/glitch de audio en cada cambio de canción sin necesidad.
    private var currentAudioSessionId: Int = C.AUDIO_SESSION_ID_UNSET

    // EQ PRO (ETAPA 1 — motor biquad de 10 bandas en software, ver clase para detalle).
    // Es un AudioProcessor de Media3, así que vive DENTRO del pipeline de ExoPlayer
    // (RenderersFactory de abajo), NO junto a equalizer/bassBoost/etc de arriba, que son
    // efectos nativos de android.media.audiofx y siguen intactos para el EQ gratis de 5
    // bandas. Ahora mismo el gate está hardcodeado dentro de la propia clase para pruebas
    // (FORCE_ENABLED_FOR_TESTING); todavía NO está conectado a LicenseRepository.
    private val proEqualizerAudioProcessor = ProBiquadEqualizerAudioProcessor()

    // ETAPA 2 — normalización de volumen: caché por canción del gain calculado, y un scope
    // propio del servicio para poder analizar canciones en segundo plano (Dispatchers.IO)
    // sin bloquear el hilo principal ni el de audio. Se cancela en onDestroy().
    private lateinit var volumeNormalizationCacheRepo: VolumeNormalizationCacheRepository
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

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
                // EQ PRO: si el intent trae el extra del bypass A/B, es porque viene del
                // diálogo del EQ Pro (SettingsScreen -> ProEqualizerDialog); si no lo trae
                // (viene del diálogo del EQ gratis de siempre), no tocamos el bypass actual.
                if (intent.hasExtra("pro_eq_bypass")) {
                    proEqualizerAudioProcessor.setBypassed(intent.getBooleanExtra("pro_eq_bypass", false))
                }
                // Se llama SIEMPRE (venga de cualquiera de los dos diálogos), porque cualquiera
                // de los dos puede afectar si el nativo de 5 bandas debe apagarse (modo
                // exclusivo del Pro) y porque no cuesta nada releer 10 floats de SharedPreferences.
                syncProEqualizerState()
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
    /**
     * EQ PRO (ETAPA 1): RenderersFactory propia que reemplaza el AudioSink por defecto de
     * ExoPlayer por uno que incluye a proEqualizerAudioProcessor en su cadena de
     * AudioProcessors. Esto es lo único que hace falta para que Media3 empiece a pasar el
     * PCM real por nuestros filtros biquad antes de mandarlo al AudioTrack del sistema.
     *
     * No toca nada de los efectos nativos (equalizer/bassBoost/virtualizer/loudnessEnhancer/
     * presetReverb) que siguen enganchados por audioSessionId más abajo — ambos caminos
     * conviven: los nativos actúan a nivel de audioSessionId/hardware, este actúa antes,
     * a nivel de los bytes PCM dentro de ExoPlayer.
     */
    private fun buildProEqRenderersFactory(): RenderersFactory {
        return object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink? {
                return DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .setAudioProcessors(arrayOf(proEqualizerAudioProcessor))
                    .build()
            }
        }
    }

    /**
     * Sincroniza el motor del EQ Pro (proEqualizerAudioProcessor) con el estado real:
     * - Licencia: ProStatusHolder.isProUnlocked (mismo holder reactivo que ya usa el resto
     *   de la app — Theme.kt, FullScreenPlayer.kt, SettingsScreen.kt — para saber si el Pro
     *   está desbloqueado; MainActivity/SettingsScreen ya lo mantienen al día con
     *   ProStatusHolder.refresh() cada vez que cambia login o licencia).
     * - Las 10 bandas y el pre-amp, leídos de SharedPreferences (mismas keys que guarda
     *   ProEqualizerDialog antes de mandar el broadcast "UPDATE_EQ").
     * Se llama al arrancar el servicio, en cada cambio de canción, y cada vez que llega el
     * broadcast de actualización de EQ (venga del diálogo gratis o del Pro).
     */
    private fun syncProEqualizerState() {
        proEqualizerAudioProcessor.setProLicensed(ProStatusHolder.isProUnlocked)
        for (i in 0 until ProBiquadEqualizerAudioProcessor.BAND_COUNT) {
            proEqualizerAudioProcessor.setBandGainDb(i, sharedPrefs.getFloat("pro_eq_band_$i", 0f))
        }
        proEqualizerAudioProcessor.setPreAmpGainDb(sharedPrefs.getFloat("pro_eq_preamp", 0f))
    }

    /**
     * true si el EQ Pro debe tener el escenario COMPLETO para él solo: el usuario tiene Pro
     * desbloqueado Y dejó activado el "Modo PRO exclusivo" (por default sí, ver
     * ProEqualizerDialog). En ese caso el Equalizer NATIVO de 5 bandas se apaga por completo
     * en applyAudioSettings() — de lo contrario ambos quedarían sumándose (el nativo de 5
     * bandas del usuario + las 10 bandas del Pro encima), lo cual además de sonar raro hace
     * más difícil notar el efecto real del Pro por separado.
     */
    private fun isProExclusiveModeActive(): Boolean =
        ProStatusHolder.isProUnlocked && sharedPrefs.getBoolean("pro_eq_exclusive", true)

    /**
     * ETAPA 2 — aplica (o dispara el cálculo de) el gain de normalización de volumen de la
     * canción que acaba de empezar a sonar.
     *
     * Si ya está en caché, se aplica de inmediato (sin ningún trabajo de fondo). Si no está
     * en caché, se deja el EQ Pro en 0 dB de normalización mientras se analiza en segundo
     * plano (Dispatchers.IO) — esto evita que la canción nueva arranque con el gain de la
     * canción ANTERIOR pegado encima por error. En cuanto el análisis termina, se guarda en
     * caché y se aplica en caliente, PERO solo si para entonces la canción actual sigue
     * siendo la misma que arrancó el análisis (si el usuario ya cambió de canción, el
     * resultado se guarda para la próxima vez que suene y no se aplica a la que está sonando
     * ahora — evitaría "pisarle" su propio gain).
     */
    private fun applyVolumeNormalizationForCurrentSong() {
        val songId = player.currentMediaItem?.mediaId?.toLongOrNull() ?: return
        val filePath = SongLibraryHolder.songs.find { it.id == songId }?.path
        if (filePath.isNullOrEmpty()) return

        val cachedGainDb = volumeNormalizationCacheRepo.get(filePath)
        if (cachedGainDb != null) {
            proEqualizerAudioProcessor.setNormalizationGainDb(cachedGainDb)
            return
        }

        proEqualizerAudioProcessor.setNormalizationGainDb(0f)

        serviceScope.launch {
            val gainDb = withContext(Dispatchers.IO) {
                VolumeNormalizationAnalyzer.analyze(filePath)
            } ?: return@launch

            volumeNormalizationCacheRepo.set(filePath, gainDb)

            val stillSameSong = player.currentMediaItem?.mediaId?.toLongOrNull() == songId
            if (stillSameSong) {
                proEqualizerAudioProcessor.setNormalizationGainDb(gainDb)
            }
        }
    }

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
        volumeNormalizationCacheRepo = VolumeNormalizationCacheRepository(this)
        syncProEqualizerState()

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

        player = ExoPlayer.Builder(this, buildProEqRenderersFactory())
            .setAudioAttributes(audioAttributes, true)
            .build()

        // Listener para actualizar efectos y refrescar la notificación si el estado cambia por otro medio
        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                // Solo recrear los efectos si el audioSessionId realmente cambió.
                if (audioSessionId != C.AUDIO_SESSION_ID_UNSET && audioSessionId != currentAudioSessionId) {
                    initAudioEffects(audioSessionId)
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    val sessionId = player.audioSessionId
                    // Antes esto se disparaba en CADA canción porque STATE_READY se repite en
                    // cada transición, aunque el sessionId no cambie. Ahora solo recrea los
                    // efectos la primera vez (o si de verdad cambió); en canciones siguientes
                    // con el mismo sessionId, los efectos ya existentes se reutilizan tal cual.
                    if (sessionId != C.AUDIO_SESSION_ID_UNSET && sessionId != currentAudioSessionId) {
                        initAudioEffects(sessionId)
                    }
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                super.onMediaItemTransition(mediaItem, reason)
                checkIfCurrentSongIsFavorite()
                syncWidgetState()
                loadLyricsForCurrentSong()
                applyVolumeNormalizationForCurrentSong()
                // Barato (un boolean + 11 floats de SharedPreferences) y cubre el caso de
                // que la licencia se haya activado a media sesión sin pasar por el broadcast
                // UPDATE_EQ (ej. justo después de validar una key nueva en Ajustes).
                proEqualizerAudioProcessor.setProLicensed(ProStatusHolder.isProUnlocked)
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

            currentAudioSessionId = audioSessionId
            applyAudioSettings()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * ARREGLO GRATIS: antes BassBoost + la banda de graves del EQ + LoudnessEnhancer
     * podían subirse los tres al máximo al mismo tiempo sin ningún límite combinado,
     * lo que causa clipping/distorsión real (probablemente la razón #1 de "no suena
     * bien" en valores altos). Esto no toca lo que el usuario ve en los sliders —
     * siguen en su rango de siempre — solo calcula cuánto hay que recortar,
     * proporcionalmente, lo que de verdad se manda al hardware cuando la suma de
     * ganancias activas (boosts, no recortes) pasa cierto umbral.
     */
    private fun computeSafeGainScale(): Float {
        val bassNorm = (currentBass / 100f).coerceIn(0f, 1f)
        val eqPositiveSum = currentBands.filter { it > 0f }.sum().coerceAtLeast(0f)
        val loudnessNorm = currentLoudness.coerceAtLeast(0f)

        val totalBoost = bassNorm + eqPositiveSum + loudnessNorm
        val threshold = 1.6f

        return if (totalBoost > threshold) threshold / totalBoost else 1f
    }

    private fun applyAudioSettings() {
        try {
            val gainScale = computeSafeGainScale()

            bassBoost?.let { boost ->
                if (boost.strengthSupported) {
                    boost.enabled = true
                    boost.setStrength((currentBass * gainScale * 10).toInt().toShort())
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
                val scaledLoudness = if (currentLoudness > 0f) currentLoudness * gainScale else currentLoudness
                loud.setTargetGain((scaledLoudness * 20).toInt())
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
                // EQ PRO — modo exclusivo: si el usuario tiene Pro desbloqueado y dejó el
                // modo exclusivo activado (default), el nativo de 5 bandas se apaga por
                // completo y solo queda sonando el motor Pro de 10 bandas (ver
                // isProExclusiveModeActive). Si no, se comporta exactamente como siempre.
                eq.enabled = !isProExclusiveModeActive()
                // ARREGLO GRATIS: antes este for iba hasta eq.numberOfBands sin límite,
                // pero currentBands solo tiene 5 casillas. En celulares (ej. algunos
                // Samsung/Xiaomi con DSP propio) que reportan MÁS de 5 bandas reales,
                // esto tronaba con ArrayIndexOutOfBoundsException. Ahora nunca se lee
                // más allá de lo que currentBands realmente tiene.
                val bandCount = minOf(eq.numberOfBands.toInt(), currentBands.size)
                for (i in 0 until bandCount) {
                    val level = currentBands[i]
                    val scaledLevel = if (level > 0f) level * gainScale else level
                    val minMilliBels = eq.bandLevelRange[0]
                    val maxMilliBels = eq.bandLevelRange[1]

                    val calculatedMilliBels = if (scaledLevel >= 0) {
                        (scaledLevel * maxMilliBels).toInt().toShort()
                    } else {
                        (kotlin.math.abs(scaledLevel) * minMilliBels).toInt().toShort()
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
        // ETAPA 2: cancela cualquier análisis de normalización de volumen que siga corriendo
        // en segundo plano — si no, quedaría una coroutine viva intentando decodificar un
        // archivo aunque el servicio (y el player) ya no existan.
        serviceScope.cancel()
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