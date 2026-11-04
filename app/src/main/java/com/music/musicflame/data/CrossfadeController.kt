package com.music.musicflame.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.RenderersFactory
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Señales en memoria entre el servicio de reproducción y el MusicPlayerManager.
 * Ambos viven en el mismo proceso (el servicio no declara android:process), así que un objeto
 * compartido basta; no hace falta Binder ni broadcast.
 */
object CrossfadeSignal {

    /** true mientras el sleep timer está en modo "al terminar la canción": ahí no se hace crossfade. */
    @Volatile
    var suppressedBySleepTimer: Boolean = false

    @Volatile
    private var advanceMarkedAt: Long = 0L

    /** El servicio la llama justo antes de adelantar la transición por crossfade. */
    fun markAdvance() {
        advanceMarkedAt = SystemClock.elapsedRealtime()
    }

    /**
     * El manager la llama al recibir una transición con razón SEEK: devuelve true (una sola vez)
     * si esa transición la provocó el crossfade y no el usuario. Así el manager la trata como
     * "la canción terminó sola" (historial, sleep timer, En compañía, conteo de reproducciones).
     */
    fun consumeAdvance(): Boolean {
        val t = advanceMarkedAt
        if (t == 0L) return false
        advanceMarkedAt = 0L
        return SystemClock.elapsedRealtime() - t < ADVANCE_WINDOW_MS
    }

    private const val ADVANCE_WINDOW_MS = 4000L
}

/**
 * CROSSFADE (fundido entre canciones).
 *
 * Idea: NO se toca la cola, ni la MediaSession, ni el reproductor principal más que para saltar
 * a la siguiente canción. Funciona con un ExoPlayer "cola" temporal (sin foco de audio propio)
 * que toca el final de la canción que sale mientras el principal ya está en la siguiente:
 *
 *  1. CALENTAMIENTO (unos 5 s antes del fundido): se crea la cola con la misma cadena de audio
 *     que el principal (EQ Pro, normalización, 8D; ver MusicPlaybackService.buildTailRenderersFactory),
 *     empieza a sonar EN SILENCIO sobre la misma canción y se alinea con el principal midiendo
 *     la diferencia de posición y corrigiéndola con seeks (se mide, no se adivina).
 *  2. FUNDIDO: la cola ya va sincronizada, así que solo se le sube el volumen mientras el
 *     principal salta a la siguiente en silencio. Sin hueco ni salto al hacer el relevo.
 *     Curva de potencia igual (seno/coseno). El avance se calcula con la posición del
 *     principal, así que pausar, perder el foco o buscar mantiene todo sincronizado.
 *  3. Si durante el fundido el usuario salta de canción o mueve la barra hacia atrás, el
 *     fundido NO se corta en seco: la cola baja a cero y el principal sube a 100% en ~250 ms.
 *
 * Solo aplica a archivos locales (content:// o file://), sin recortes de Momentos, con 2+
 * canciones en la cola, sin "repetir una", fuera de salas En compañía y fuera del sleep timer
 * "al terminar la canción". En cualquier otro caso la transición es la de siempre. Las canciones
 * cortas usan un fundido proporcionalmente más corto (nunca más de 1/4 de su duración).
 */
@UnstableApi
class CrossfadeController(
    private val context: Context,
    private val player: ExoPlayer,
    private val settings: SettingsStore,
    /** Fábrica con la misma cadena de audio que el principal, con instancias nuevas. */
    private val tailRenderersFactory: () -> RenderersFactory
) {
    companion object {
        private const val TAG = "Crossfade"
        private const val KEY_ENABLED = "crossfade_enabled"
        private const val KEY_SECONDS = "crossfade_seconds"
        private const val KEY_STATUS = "crossfade_status"
        private const val DEFAULT_SECONDS = 5
        private const val MIN_SECONDS = 1
        private const val MAX_SECONDS = 12

        private const val IDLE_TICK_MS = 250L
        private const val WARM_TICK_MS = 100L
        private const val FADE_TICK_MS = 40L

        /** Cuánto antes de empezar el fundido se prepara y calienta la cola. */
        private const val PREPARE_AHEAD_MS = 5000L

        /** Canciones más cortas que esto no hacen crossfade. */
        private const val MIN_SONG_MS = 8000L

        /** Si queda menos que esto, no vale la pena un fundido: se deja la transición normal. */
        private const val MIN_FADE_MS = 800L

        /** Mínimo de tiempo restante para arrancar el calentamiento (si no, ruta de respaldo). */
        private const val MIN_WARM_MS = 700L

        /** Diferencia de posición cola/principal que se considera "sincronizada". */
        private const val SYNC_TOLERANCE_MS = 25L

        /** Máximo de correcciones por seek durante el calentamiento. */
        private const val MAX_SYNC_CORRECTIONS = 4

        /** Espera tras cada corrección para que la cola se estabilice antes de volver a medir. */
        private const val SYNC_SETTLE_MS = 350L

        /** Si la diferencia pasa de esto (el usuario buscó en el principal), se reinicia la cola. */
        private const val SYNC_RESET_MS = 2000L

        /**
         * SOLO RUTA DE RESPALDO (cuando la cola no alcanzó a calentarse): empieza esta cantidad
         * de ms adelante de la posición oída para compensar su arranque en frío.
         */
        private const val COLD_START_LEAD_MS = 80L

        /** Tolerancia para detectar que el usuario movió la barra hacia atrás durante el fundido. */
        private const val BACKWARD_TOLERANCE_MS = 400L

        /** Duración del cierre suave cuando el usuario interrumpe un fundido. */
        private const val SETTLE_MS = 250L
    }

    private val handler = Handler(Looper.getMainLooper())

    private var tail: ExoPlayer? = null
    private var tailKey: String? = null
    private var giveUpKey: String? = null

    // Calentamiento
    private var tailWarm = false
    private var tailSynced = false
    private var syncCorrections = 0
    private var lastCorrectionAt = 0L

    // Fundido
    private var fading = false
    private var fadeLenMs = 0L
    private var fadeIndex = C.INDEX_UNSET
    private var fadeMediaId: String? = null
    private var lastFadePos = 0L

    // Cierre suave
    private var settling = false
    private var settleStartedAt = 0L
    private var settleMainFrom = 1f
    private var settleTailFrom = 0f

    private var ticking = false
    private var released = false

    // Diagnóstico: último texto publicado (solo se escribe cuando cambia).
    private var lastStatus: String? = null

    private val tickRunnable = Runnable {
        ticking = false
        if (!released) {
            try {
                when {
                    fading -> onFadeTick()
                    settling -> onSettleTick()
                    else -> onIdleTick()
                }
            } catch (e: Exception) {
                // Nunca dejar el volumen principal bajo por un error aquí.
                Log.w(TAG, "Error en crossfade, se cancela", e)
                abortAndRestore()
            }
            scheduleTick()
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            // Pausa REAL antes del fundido (playWhenReady = false): la cola, que suena en silencio, se
            // libera y se vuelve a preparar sola al reanudar. OJO: isPlaying también pasa a false
            // cuando el principal solo está "cargando" (p. ej. justo al saltar a la siguiente
            // canción); eso NO es una pausa y no debe destruir la cola.
            if (!isPlaying && !player.playWhenReady && !fading && !settling) discardTail()
            scheduleTick()
        }
    }

    init {
        player.addListener(playerListener)
        scheduleTick()
    }

    /** Libera todo. Llamar ANTES de player.release(). */
    fun release() {
        released = true
        handler.removeCallbacksAndMessages(null)
        try {
            player.removeListener(playerListener)
        } catch (_: Exception) {
        }
        abortAndRestore()
    }

    // ---------------------------------------------------------------------------------------
    // Bucle
    // ---------------------------------------------------------------------------------------

    private fun scheduleTick() {
        if (released || ticking) return
        // Solo corre mientras suena música (o mientras dura un fundido): en pausa no gasta nada.
        if (!fading && !settling && !player.isPlaying) return
        ticking = true
        val delay = when {
            fading || settling -> FADE_TICK_MS
            tail != null -> WARM_TICK_MS
            else -> IDLE_TICK_MS
        }
        handler.postDelayed(tickRunnable, delay)
    }

    private fun fadeSeconds(): Int =
        settings.getInt(KEY_SECONDS, DEFAULT_SECONDS).coerceIn(MIN_SECONDS, MAX_SECONDS)

    /** Fundido efectivo: el pedido, pero nunca más de 1/4 de la canción (para las cortas). */
    private fun effectiveFadeMs(durationMs: Long): Long =
        min(fadeSeconds() * 1000L, durationMs / 4)

    private fun keyFor(item: MediaItem): String = "${player.currentMediaItemIndex}|${item.mediaId}"

    private fun inTogetherRoom(): Boolean =
        context.getSharedPreferences("together_prefs", Context.MODE_PRIVATE)
            .getString("room_code", null) != null

    /** Escribe el estado para la línea de diagnóstico de Ajustes (solo si cambió). */
    private fun publishStatus(text: String) {
        if (text == lastStatus) return
        lastStatus = text
        try {
            settings.edit().putString(KEY_STATUS, text).apply()
        } catch (_: Exception) {
        }
    }

    /** null si se puede hacer crossfade ahora; si no, el motivo (también se muestra en Ajustes). */
    private fun blockReason(): String? {
        if (!settings.getBoolean(KEY_ENABLED, false)) return "desactivado en Ajustes"
        if (CrossfadeSignal.suppressedBySleepTimer) return "temporizador en modo \"al terminar la canción\""
        if (!player.isPlaying) return "no está sonando"
        if (player.repeatMode == Player.REPEAT_MODE_ONE) return "\"repetir una\" activo"
        if (player.mediaItemCount < 2 || !player.hasNextMediaItem()) {
            return "no hay siguiente canción en la cola (${player.mediaItemCount} en cola)"
        }
        val dur = player.duration
        if (dur == C.TIME_UNSET || dur < MIN_SONG_MS) return "canción muy corta o sin duración conocida"
        val item = player.currentMediaItem ?: return "sin canción actual"
        val clip = item.clippingConfiguration
        if (clip.startPositionMs != 0L || clip.endPositionMs != C.TIME_END_OF_SOURCE) {
            return "fragmento de Momentos/Highlights"
        }
        // MusicFlame carga las canciones con la ruta del archivo (sin "file://"): esquema null/vacío
        // = archivo local. Solo se descartan las remotas y otros orígenes (http, rtsp, resource...).
        val scheme = item.localConfiguration?.uri?.scheme
        if (!scheme.isNullOrEmpty() && scheme != "content" && scheme != "file") {
            return "origen no local ($scheme)"
        }
        if (inTogetherRoom()) return "sala En compañía guardada/activa"
        return null
    }

    // ---------------------------------------------------------------------------------------
    // Fase 1: esperar, preparar y calentar la cola
    // ---------------------------------------------------------------------------------------

    private fun onIdleTick() {
        val reason = blockReason()
        if (reason != null) {
            publishStatus("No aplica: $reason")
            discardTail()
            return
        }
        val item = player.currentMediaItem ?: return
        val key = keyFor(item)
        if (key == giveUpKey) return
        if (tailKey != null && tailKey != key) discardTail()

        val dur = player.duration
        val pos = player.currentPosition
        val fadeMs = effectiveFadeMs(dur)
        val remaining = dur - pos
        if (remaining > fadeMs + PREPARE_AHEAD_MS) {
            publishStatus("Listo: mezclará al final de esta canción (fundido de ${fadeMs / 1000f} s)")
            return
        }

        if (tail == null) prepareTail(item, key, pos)
        val t = tail ?: return

        // La cola lista y con tiempo de sobra: arranca en silencio y se alinea con el principal.
        if (!tailWarm && t.playbackState == Player.STATE_READY && remaining > fadeMs + MIN_WARM_MS) {
            t.seekTo(pos)
            t.volume = 0f
            t.playWhenReady = true
            tailWarm = true
            tailSynced = false
            syncCorrections = 0
            lastCorrectionAt = SystemClock.elapsedRealtime()
            publishStatus("Calentando la cola en silencio…")
        }
        if (tailWarm) syncTail(t)

        if (remaining <= fadeMs) startFade(key, pos, dur, fadeMs)
    }

    private fun prepareTail(item: MediaItem, key: String, pos: Long) {
        discardTail()
        try {
            val t = ExoPlayer.Builder(context, tailRenderersFactory()).build()
            // Sin foco de audio propio: el principal ya lo tiene; si no, se pelearían.
            t.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ false
            )
            // Mismo audioSessionId: los efectos nativos (ecualizador, graves, etc.) también le llegan.
            val session = player.audioSessionId
            if (session != C.AUDIO_SESSION_ID_UNSET) t.audioSessionId = session
            t.addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    Log.w(TAG, "La cola falló, esta canción pasa sin fundido", error)
                    if (tail === t) {
                        giveUpKey = key
                        publishStatus("La cola falló (${error.errorCodeName}): esta canción pasa sin fundido")
                        abortAndRestore()
                    }
                }
            })
            t.volume = 0f
            t.playWhenReady = false
            t.setMediaItem(item, pos.coerceAtLeast(0L))
            t.prepare()
            tail = t
            tailKey = key
            tailWarm = false
            tailSynced = false
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo crear la cola", e)
            giveUpKey = key
            publishStatus("Esta canción pasó sin fundido: no se pudo crear la cola")
            discardTail()
        }
    }

    /**
     * Mide la diferencia de posición entre la cola (en silencio) y el principal, y la corrige con
     * seeks hasta quedar dentro de la tolerancia. Es lo que reemplaza al "salto de ~80 ms" de la
     * primera versión: ya no se adivina cuánto tarda en arrancar, se mide en el dispositivo.
     */
    private fun syncTail(t: ExoPlayer) {
        if (!t.isPlaying) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastCorrectionAt < SYNC_SETTLE_MS) return
        val drift = t.currentPosition - player.currentPosition
        when {
            abs(drift) > SYNC_RESET_MS -> {
                // El usuario movió la barra del principal: se vuelve a preparar desde cero.
                discardTail()
            }
            abs(drift) <= SYNC_TOLERANCE_MS -> {
                tailSynced = true
                publishStatus("Cola sincronizada (diferencia ${drift} ms, $syncCorrections ajustes)")
            }
            syncCorrections < MAX_SYNC_CORRECTIONS -> {
                tailSynced = false
                syncCorrections++
                lastCorrectionAt = now
                t.seekTo((t.currentPosition - drift).coerceAtLeast(0L))
            }
            else -> {
                tailSynced = false // no logró alinearse: se usará la ruta de respaldo
                publishStatus("Cola sin alinear (diferencia ${drift} ms): ruta de respaldo")
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    // Fase 2: el fundido
    // ---------------------------------------------------------------------------------------

    private fun startFade(key: String, pos: Long, dur: Long, fadeMs: Long) {
        val t = tail ?: return
        val remaining = dur - pos
        val hot = tailWarm && tailSynced && t.isPlaying
        if (remaining < MIN_FADE_MS || (!hot && t.playbackState != Player.STATE_READY)) {
            // La cola no alcanzó a prepararse (p. ej. el usuario buscó al final) o ya casi
            // termina: esta canción pasa con la transición normal.
            giveUpKey = key
            publishStatus("Esta canción pasó sin fundido: la cola no estaba lista")
            discardTail()
            return
        }

        val oldIndex = player.currentMediaItemIndex
        fadeLenMs = min(remaining, fadeMs.coerceAtLeast(MIN_FADE_MS))

        if (!hot) {
            // Respaldo: la cola arranca en frío desde la posición oída (+ compensación).
            t.seekTo(pos + COLD_START_LEAD_MS)
            t.playWhenReady = true
        }
        t.volume = 1f

        // El principal salta a la siguiente, en silencio. El manager la tratará como
        // "terminó sola" gracias a CrossfadeSignal.
        // `fading` se marca ANTES del salto: ExoPlayer llama a los listeners DENTRO de
        // seekToNextMediaItem() (de forma síncrona), y a esa altura ya debe constar que hay un fundido.
        fading = true
        player.volume = 0f
        CrossfadeSignal.markAdvance()
        player.seekToNextMediaItem()

        if (player.currentMediaItemIndex == oldIndex) {
            // El salto no se aplicó: restaurar todo y no reintentar en esta canción.
            giveUpKey = key
            abortAndRestore()
            return
        }

        publishStatus("Mezclando: fundido de ${fadeLenMs / 1000f} s (${if (hot) "cola caliente" else "ruta de respaldo"})")
        fadeIndex = player.currentMediaItemIndex
        fadeMediaId = player.currentMediaItem?.mediaId
        lastFadePos = 0L
    }

    private fun onFadeTick() {
        val t = tail
        if (t == null) {
            publishStatus("Último fundido: se perdió la cola de la canción que sale")
            abortAndRestore()
            return
        }
        val pos = player.currentPosition
        val state = player.playbackState
        if (state == Player.STATE_IDLE || state == Player.STATE_ENDED) {
            abortAndRestore()
            return
        }
        val interrupted = player.currentMediaItemIndex != fadeIndex ||
            player.currentMediaItem?.mediaId != fadeMediaId ||
            pos + BACKWARD_TOLERANCE_MS < lastFadePos
        if (interrupted) {
            // El usuario saltó de canción o movió la barra hacia atrás: cierre suave, no corte.
            publishStatus("Último fundido: interrumpido (saltaste o moviste la barra)")
            beginSettle(t)
            return
        }
        lastFadePos = pos

        // Pausa / reanudación (o pérdida de foco) en conjunto.
        if (t.playWhenReady != player.playWhenReady) t.playWhenReady = player.playWhenReady

        val progress = (pos.toFloat() / fadeLenMs.toFloat()).coerceIn(0f, 1f)
        val angle = progress * (PI / 2.0).toFloat()
        player.volume = sin(angle)
        t.volume = cos(angle)

        if (progress >= 1f || t.playbackState == Player.STATE_ENDED) {
            publishStatus("Último fundido: completo")
            abortAndRestore()
        }
    }

    // ---------------------------------------------------------------------------------------
    // Cierre suave (el usuario interrumpió el fundido)
    // ---------------------------------------------------------------------------------------

    private fun beginSettle(t: ExoPlayer) {
        fading = false
        settling = true
        settleStartedAt = SystemClock.elapsedRealtime()
        settleMainFrom = player.volume
        settleTailFrom = t.volume
    }

    private fun onSettleTick() {
        val t = tail
        val elapsed = SystemClock.elapsedRealtime() - settleStartedAt
        if (t == null || elapsed >= SETTLE_MS || !player.playWhenReady) {
            abortAndRestore()
            return
        }
        val f = elapsed.toFloat() / SETTLE_MS.toFloat()
        player.volume = settleMainFrom + (1f - settleMainFrom) * f
        t.volume = settleTailFrom * (1f - f)
    }

    // ---------------------------------------------------------------------------------------
    // Limpieza
    // ---------------------------------------------------------------------------------------

    /** Termina (o cancela) todo: volumen principal al 100% y la cola se libera. */
    private fun abortAndRestore() {
        fading = false
        settling = false
        try {
            player.volume = 1f
        } catch (_: Exception) {
        }
        discardTail()
    }

    private fun discardTail() {
        tail?.let {
            try {
                it.release()
            } catch (_: Exception) {
            }
        }
        tail = null
        tailKey = null
        tailWarm = false
        tailSynced = false
    }
}
