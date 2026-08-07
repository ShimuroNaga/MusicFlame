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
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.music.musicflame.R
import android.media.AudioManager

@OptIn(UnstableApi::class)
class MusicPlaybackService : MediaSessionService() {

    companion object {
        // Públicas para que MusicPlayerManager pueda pedir el audioSessionId sin duplicar strings
        const val CUSTOM_COMMAND_GET_AUDIO_SESSION_ID = "com.music.musicflame.GET_AUDIO_SESSION_ID"
        const val KEY_AUDIO_SESSION_ID = "audio_session_id"
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

    override fun onCreate() {
        super.onCreate()

        sharedPrefs = getSharedPreferences("settings", Context.MODE_PRIVATE)

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
            .build()
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
                session.setCustomLayout(controller, getCustomLayout())
                session.setCustomLayout(getCustomLayout())
            }

            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }

    private fun getCustomLayout(): ImmutableList<CommandButton> {
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
        unregisterReceiver(eqUpdateReceiver)
        unregisterReceiver(noisyReceiver)
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
}