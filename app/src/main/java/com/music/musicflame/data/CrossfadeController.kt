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
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.analytics.AnalyticsListener
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round
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

    /**
     * true desde que arranca un fundido hasta que termina (o se interrumpe). El servicio la usa
     * para no lanzar trabajo pesado (p. ej. el análisis de normalización de volumen) en pleno fundido.
     */
    @Volatile
    var fadeActive: Boolean = false

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
 *
 * AUTOMIX (opcional, dentro de este mismo controlador): si está activado y hay datos de BPM/beat de
 * AMBAS canciones (AutomixCacheRepository, los llena el servicio en segundo plano con BeatWeave):
 *  1. Si los tempos difieren menos de MAX_TEMPO_DIFF_PCT, la COLA (la que sale) se estira al tempo de
 *     la que entra con la velocidad de ExoPlayer (Sonic, pitch intacto). El principal NO se toca.
 *  2. El inicio del fundido se retrasa, como mucho un beat, hasta que un beat de la cola caiga sobre
 *     el primer beat de la que entra.
 *  3. Ya sonando las dos, un lazo de fase (PLL) corrige el desfase restante con ajustes de velocidad
 *     de como mucho +-4% (el desfase se mide con las posiciones reales de ambos reproductores).
 * Si falta cualquier dato, o BeatWeave falló, el fundido es EXACTAMENTE el de siempre.
 */
@UnstableApi
class CrossfadeController(
    private val context: Context,
    private val player: ExoPlayer,
    private val settings: SettingsStore,
    /** Fábrica con la misma cadena de audio que el principal, con instancias nuevas. */
    private val tailRenderersFactory: () -> RenderersFactory,
    /** Crea los efectos nativos (EQ, graves, etc.) de la cola en SU sesión de audio. */
    private val tailEffectsFactory: (Int) -> AutoCloseable? = { null },
    /** Filtro pasa-altos del principal (la que entra): se abre durante el fundido. */
    private val mainFilter: com.music.musicflame.audio.CrossfadeFilterAudioProcessor? = null,
    /** Proveedor del filtro pasa-bajos de la cola (la que sale): se cierra durante el fundido. */
    private val tailFilterProvider: () -> com.music.musicflame.audio.CrossfadeFilterAudioProcessor? = { null },
    /** AUTOMIX: ¿está activado en Ajustes? */
    private val automixEnabled: () -> Boolean = { false },
    /** AUTOMIX: caché de BPM/beat por canción (la llena el servicio en segundo plano). */
    private val automixCacheRepo: AutomixCacheRepository? = null
) {
    companion object {
        private const val TAG = "Crossfade"
        private const val KEY_ENABLED = "crossfade_enabled"
        private const val KEY_SECONDS = "crossfade_seconds"
        private const val KEY_STATUS = "crossfade_status"
        private const val DEFAULT_SECONDS = 5
        private const val MIN_SECONDS = 5
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
        private const val SYNC_TOLERANCE_MS = 40L

        /** Máximo de correcciones por seek durante el calentamiento. */
        private const val MAX_SYNC_CORRECTIONS = 6

        /** Tope del adelanto aprendido (lo que tarda la cola en empezar a sonar tras un seek). */
        private const val MAX_SYNC_LEAD_MS = 1000L

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

        // Frecuencias de corte del filtro espectral del crossfade.
        // HP (pasa-altos, canción que entra): empieza en 800 Hz y abre hasta 20 Hz.
        // LP (pasa-bajos, canción que sale): empieza en 20 kHz y cierra hasta 300 Hz.
        private const val HP_START_HZ = 800f
        private const val HP_END_HZ   = 20f
        private const val LP_START_HZ = 20000f
        private const val LP_END_HZ   = 300f

        /**
         * Refuerzo del cruce contra el bajón de volumen. La curva seno/coseno deja a cada canción en
         * 0.71 (-3 dB) en el medio, y con música real (outro suave + intro baja) se oye un hueco.
         * Se multiplica ambas por 1 + BOOST * sin(PI * progreso): 0 en los extremos y máximo en el
         * medio. Ojo: NO usar exponentes (x^0.4): tienen pendiente casi vertical al inicio y al
         * final y el volumen, que se actualiza a saltos cada ~40 ms, hace un "pth" audible.
         * Esta forma es suave en los extremos. Subir BOOST si aún hay bajón; bajarlo si se oye más
         * fuerte en el medio.
         */
        private const val MID_BOOST = 0.25f

        /**
         * Traspaso corto al empezar el fundido. Antes la cola pasaba de 0 a 1 y el principal de 1 a 0
         * de golpe; si la cola iba desfasada unos milisegundos, ese corte seco se oía como un "pth"
         * justo cuando entra la siguiente canción. Ahora ambos se cruzan en línea recta durante este
         * tiempo (es el MISMO audio en los dos, así que la suma lineal no cambia el volumen).
         */
        private const val HANDOFF_MS = 80L

        // --- AUTOMIX ---
        /** Si los BPM difieren más que esto (8 %), no hay beat matching: fundido normal. */
        private const val MAX_TEMPO_DIFF_PCT = 0.08f

        /** Confianza mínima del análisis de AMBAS canciones. */
        private const val MIN_AUTOMIX_CONFIDENCE = 0.70f

        /** Retraso inicial estimado entre disparar el fundido y que el principal suene (traspaso + arranque). Se aprende. */
        private const val AUTOMIX_DEFAULT_DELAY_MS = 230L
        private const val AUTOMIX_MIN_DELAY_MS = 80L
        private const val AUTOMIX_MAX_DELAY_MS = 1500L

        /** Cuánto se puede retrasar el fundido esperando el beat (como mucho ~1 beat). */
        private const val AUTOMIX_MAX_ALIGN_WAIT_MS = 1200L

        /** Si tras esperar el beat quedaría menos que esto de canción, no se alinea. */
        private const val AUTOMIX_MIN_REMAINING_AFTER_WAIT_MS = 1500L

        /** Margen para no apuntar a un beat que ya pasó mientras corre el tick. */
        private const val AUTOMIX_TRIGGER_GUARD_MS = 30L

        // Lazo de fase: cada PLL_INTERVAL_MS mide el desfase de beat y ajusta la velocidad de la cola.
        private const val PLL_START_DELAY_MS = 500L
        private const val PLL_INTERVAL_MS = 500L
        private const val PLL_WINDOW_MS = 1500f
        private const val PLL_MAX_TRIM = 0.04f
        private const val PLL_DEADBAND_MS = 8f
        private const val PLL_STOP_PROGRESS = 0.85f

        private const val KEY_AUTOMIX_DELAY = "automix_delay_ms"
        private const val KEY_AUTOMIX_PHASE_OFFSET = "automix_phase_offset_ms"
        private const val KEY_AUTOMIX_PLAN_DIAG = "automix_diag_plan"
    }

    private val handler = Handler(Looper.getMainLooper())

    private var tail: ExoPlayer? = null
    private var tailEffects: AutoCloseable? = null
    private var tailKey: String? = null
    private var giveUpKey: String? = null

    // Calentamiento
    private var tailWarm = false
    private var tailSynced = false
    private var syncCorrections = 0
    private var lastCorrectionAt = 0L

    /**
     * Cuánto hay que ADELANTAR cada seek de la cola para compensar lo que tarda en empezar a
     * sonar (en este dispositivo). Se aprende midiendo la diferencia tras cada corrección y se
     * conserva entre canciones: así la cola queda alineada desde el primer intento.
     */
    private var syncLeadMs = 0L

    // Fundido
    private var fading = false
    private var fadeLenMs = 0L
    private var fadeIndex = C.INDEX_UNSET
    private var fadeMediaId: String? = null
    private var lastFadePos = 0L

    /** Duración de la canción que sale (la cola termina ahí): ancla el volumen del fundido. */
    private var fadeSongDurMs = 0L

    /** Posición de la cola cuando el principal empezó a sonar de verdad (-1 = aún no suena). */
    private var fadeTailStartPos = -1L

    /** Instante en que empezó el traspaso corto principal -> cola (0 = no hay traspaso en curso). */
    private var handoffStartedAt = 0L

    // Cierre suave
    private var settling = false
    private var settleStartedAt = 0L
    private var settleMainFrom = 1f
    private var settleTailFrom = 0f

    private var ticking = false
    private var released = false

    // Diagnóstico: último texto publicado (solo se escribe cuando cambia).
    private var lastStatus: String? = null

    // Diagnóstico de trabas durante el fundido: eventos del fundido en curso y reporte del último.
    private var fadeStartedAt = 0L
    private var lastFadeTickAt = 0L
    private val fadeEvents = StringBuilder()
    private var lastFadeReport: String? = null

    // AutoMix: plan de la canción que sale (null = fundido normal) y estado del fundido en curso.
    private class AutomixPlan(
        val ratio: Double,           // velocidad de la cola = BPM entrante / BPM saliente
        val tailBpm: Float,
        val mainBpm: Float,
        val tailPeriodMs: Double,
        val tailAnchorMs: Double,    // un beat REAL de la que sale, cerca de su final
        val mainPeriodMs: Double,
        val mainFirstBeatMs: Double, // primer beat de la rejilla de la que entra
        val confidence: Float
    )

    private var plannedKey: String? = null
    private var autoPlan: AutomixPlan? = null
    private var triggerTailPos = -1L        // posición de la cola en la que se dispara el fundido (-1 = sin esperar)
    private var automixActive = false       // la velocidad de la cola ya se cambió
    private var tailSpeed = 1f
    private var mainStartedAt = 0L
    private var lastPllAt = 0L
    private var pllLearned = false

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

    private val mainAnalytics = analyticsFor("principal")

    init {
        player.addListener(playerListener)
        player.addAnalyticsListener(mainAnalytics)
        scheduleTick()
    }

    /** Libera todo. Llamar ANTES de player.release(). */
    fun release() {
        released = true
        handler.removeCallbacksAndMessages(null)
        try {
            player.removeListener(playerListener)
            player.removeAnalyticsListener(mainAnalytics)
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
            fading || settling || handoffStartedAt != 0L -> FADE_TICK_MS
            // AutoMix esperando el beat: ticks cortos para disparar con pocos ms de error.
            triggerTailPos >= 0L && tail != null ->
                (triggerTailPos - (tail?.currentPosition ?: 0L)).coerceIn(2L, FADE_TICK_MS)
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
        // Fuera de un fundido, el reporte del último fundido se queda pegado al estado para que no
        // lo borre el siguiente mensaje de "Listo: mezclará…".
        val report = lastFadeReport
        val full = if (report != null && !fading && !settling && !text.startsWith("Último fundido")) {
            "$text · Último fundido: $report"
        } else {
            text
        }
        if (full == lastStatus) return
        lastStatus = full
        try {
            settings.edit().putString(KEY_STATUS, full).apply()
        } catch (_: Exception) {
        }
    }

    /** Anota un evento raro (traba, salto, buffering) del fundido en curso, con su tiempo. */
    private fun noteEvent(text: String) {
        if (!fading && !settling) return
        val t = SystemClock.elapsedRealtime() - fadeStartedAt
        Log.w(TAG, "Evento a los $t ms del fundido: $text")
        if (fadeEvents.length < 1500) {
            fadeEvents.append("[").append(t / 100 / 10f).append(" s] ").append(text).append("; ")
        }
    }

    /** Cierra el fundido: guarda y publica el reporte completo (resultado + eventos raros). */
    private fun finishFadeReport(result: String) {
        val total = if (fadeStartedAt != 0L) SystemClock.elapsedRealtime() - fadeStartedAt else 0L
        val ev = if (fadeEvents.isEmpty()) "sin eventos raros" else fadeEvents.toString().trim()
        val report = "$result (${total / 1000f} s) · $ev"
        lastFadeReport = report
        Log.w(TAG, "Fundido: $report")
        publishStatus("Último fundido: $report")
    }

    /** Escucha de bajo nivel de un reproductor: detecta cortes de audio y saltos durante el fundido. */
    private fun analyticsFor(who: String): AnalyticsListener = object : AnalyticsListener {
        private var bufferingSince = 0L

        override fun onAudioUnderrun(
            eventTime: AnalyticsListener.EventTime,
            bufferSize: Int,
            bufferSizeMs: Long,
            elapsedSinceLastFeedMs: Long
        ) {
            noteEvent("$who: el audio se quedó sin datos ($elapsedSinceLastFeedMs ms)")
        }

        override fun onPlaybackStateChanged(eventTime: AnalyticsListener.EventTime, state: Int) {
            if (state == Player.STATE_BUFFERING) {
                bufferingSince = SystemClock.elapsedRealtime()
                noteEvent("$who: cargando (buffering)")
            }
            if (state == Player.STATE_READY && bufferingSince != 0L) {
                noteEvent("$who: listo tras ${SystemClock.elapsedRealtime() - bufferingSince} ms")
                bufferingSince = 0L
            }
            if (state == Player.STATE_ENDED) noteEvent("$who: llegó al final")
        }

        override fun onPositionDiscontinuity(
            eventTime: AnalyticsListener.EventTime,
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            if (reason == Player.DISCONTINUITY_REASON_SEEK) return
            noteEvent("$who: salto de posición ${newPosition.positionMs - oldPosition.positionMs} ms (motivo $reason)")
        }

        override fun onAudioSinkError(eventTime: AnalyticsListener.EventTime, audioSinkError: Exception) {
            noteEvent("$who: error en la salida de audio")
        }

        // DIAGNOSTICO (nov 2026): el logcat de este celular no deja pasar los logs de la app, asi que
        // estos eventos se anotan en el reporte del fundido (Ajustes > Canciones > Diagnostico).
        // Sirven para saber QUE reinicia la salida de audio del reproductor a mitad del fundido.
        override fun onAudioEnabled(
            eventTime: AnalyticsListener.EventTime,
            counters: androidx.media3.exoplayer.DecoderCounters
        ) {
            noteEvent("$who: audio ACTIVADO (se reinicia el renderer)")
        }

        override fun onAudioDisabled(
            eventTime: AnalyticsListener.EventTime,
            counters: androidx.media3.exoplayer.DecoderCounters
        ) {
            noteEvent("$who: audio DESACTIVADO")
        }

        override fun onAudioDecoderInitialized(
            eventTime: AnalyticsListener.EventTime,
            decoderName: String,
            initializedTimestampMs: Long,
            initializationDurationMs: Long
        ) {
            noteEvent("$who: decodificador nuevo ($decoderName, $initializationDurationMs ms)")
        }

        override fun onAudioDecoderReleased(eventTime: AnalyticsListener.EventTime, decoderName: String) {
            noteEvent("$who: decodificador liberado")
        }

        override fun onAudioSessionIdChanged(eventTime: AnalyticsListener.EventTime, audioSessionId: Int) {
            noteEvent("$who: cambio de sesion de audio ($audioSessionId)")
        }

        override fun onTimelineChanged(eventTime: AnalyticsListener.EventTime, reason: Int) {
            // Se modifico la cola de reproduccion (agregar, quitar, mover, mezclar).
            noteEvent("$who: cambio la cola (motivo $reason)")
        }

        override fun onPlayWhenReadyChanged(
            eventTime: AnalyticsListener.EventTime,
            playWhenReady: Boolean,
            reason: Int
        ) {
            noteEvent("$who: playWhenReady=$playWhenReady (motivo $reason)")
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
            t.seekTo((pos + syncLeadMs).coerceAtLeast(0L))
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
            // SESIÓN PROPIA (distinta a la del principal) con su copia de los efectos nativos.
            // Antes compartían sesión: al saltar de canción el principal apaga y recrea su salida
            // de audio, y eso reconfiguraba la cadena de efectos que la cola también usaba (se
            // oía una traba justo al empezar el fundido, coincidiendo con el "buffering" del principal).
            val session = t.audioSessionId
            if (session != C.AUDIO_SESSION_ID_UNSET) {
                tailEffects = try {
                    tailEffectsFactory(session)
                } catch (e: Exception) {
                    Log.w(TAG, "No se pudieron crear los efectos de la cola", e)
                    null
                }
            }
            t.addAnalyticsListener(analyticsFor("cola"))
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
     *
     * OJO: un seek tarda en empezar a sonar, y mientras tanto el principal sigue avanzando. Si el
     * seek apuntara a "donde está el principal ahora", la cola quedaría SIEMPRE atrasada esa
     * misma cantidad y nunca convergería. Por eso cada corrección apunta a la posición del
     * principal + syncLeadMs, y syncLeadMs se ajusta con la diferencia medida (drift < 0: la cola
     * va atrasada, hay que adelantar más; drift > 0: al revés).
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
                publishStatus("Cola sincronizada (diferencia ${drift} ms, $syncCorrections ajustes, adelanto $syncLeadMs ms)")
            }
            syncCorrections < MAX_SYNC_CORRECTIONS -> {
                tailSynced = false
                syncCorrections++
                lastCorrectionAt = now
                syncLeadMs = (syncLeadMs - drift).coerceIn(0L, MAX_SYNC_LEAD_MS)
                t.seekTo((player.currentPosition + syncLeadMs).coerceAtLeast(0L))
            }
            else -> {
                tailSynced = false // no logró alinearse: se usará la ruta de respaldo
                publishStatus("Cola sin alinear (diferencia ${drift} ms, adelanto $syncLeadMs ms): ruta de respaldo")
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

        if (hot) {
            if (handoffStartedAt == 0L) {
                // AUTOMIX: se espera (como mucho ~1 beat) a que un beat de la cola caiga sobre el
                // primer beat de la canción que entra. Sin plan, no espera nada.
                val ap = planAutomix(key)
                if (ap != null && !automixBeatReached(ap, t, dur)) return
                handoffStartedAt = SystemClock.elapsedRealtime()
                // La velocidad se cambia AQUÍ, durante el traspaso de 80 ms: ExoPlayer la aplica tras
                // drenar el audio ya enviado (sin saltos) y el cambio se oye un poco después.
                autoPlan?.let { applyAutomixTempo(t, it) }
            }
            val now = SystemClock.elapsedRealtime()
            val k = ((now - handoffStartedAt).toFloat() / HANDOFF_MS.toFloat()).coerceIn(0f, 1f)
            if (k < 1f) {
                // Traspaso en curso: la cola sube y el principal baja, todavía sin saltar de canción.
                t.volume = k
                player.volume = 1f - k
                return
            }
        }
        handoffStartedAt = 0L

        val oldIndex = player.currentMediaItemIndex
        fadeLenMs = min(remaining, fadeMs.coerceAtLeast(MIN_FADE_MS))

        if (!hot) {
            // Respaldo: la cola arranca en frío desde la posición oída (+ compensación).
            t.seekTo(pos + maxOf(COLD_START_LEAD_MS, syncLeadMs))
            t.playWhenReady = true
        }
        t.volume = 1f

        // El principal salta a la siguiente, en silencio. El manager la tratará como
        // "terminó sola" gracias a CrossfadeSignal.
        // `fading` se marca ANTES del salto: ExoPlayer llama a los listeners DENTRO de
        // seekToNextMediaItem() (de forma síncrona), y a esa altura ya debe constar que hay un fundido.
        fadeSongDurMs = dur
        fadeTailStartPos = -1L
        fadeStartedAt = SystemClock.elapsedRealtime()
        fadeEvents.setLength(0)
        lastFadeTickAt = 0L
        fading = true
        CrossfadeSignal.fadeActive = true
        player.volume = 0f
        CrossfadeSignal.markAdvance()
        player.seekToNextMediaItem()

        if (player.currentMediaItemIndex == oldIndex) {
            // El salto no se aplicó: restaurar todo y no reintentar en esta canción.
            giveUpKey = key
            abortAndRestore()
            return
        }

        val automixNote = autoPlan?.takeIf { automixActive }
            ?.let { " · AutoMix ${String.format(java.util.Locale.US, "%.3f", it.ratio)}x" } ?: ""
        publishStatus("Mezclando: fundido de ${fadeLenMs / 1000f} s (${if (hot) "cola caliente" else "ruta de respaldo"})$automixNote")
        fadeIndex = player.currentMediaItemIndex
        fadeMediaId = player.currentMediaItem?.mediaId
        lastFadePos = 0L
    }

    private fun onFadeTick() {
        // Si el hilo principal se atasca (trabajo pesado de la UI/servicio), los ticks se retrasan.
        val nowTick = SystemClock.elapsedRealtime()
        if (lastFadeTickAt != 0L && nowTick - lastFadeTickAt > 300L) {
            noteEvent("hilo principal atascado ${nowTick - lastFadeTickAt} ms")
        }
        lastFadeTickAt = nowTick

        val t = tail
        if (t == null) {
            finishFadeReport("se perdió la cola de la canción que sale")
            abortAndRestore()
            return
        }
        val pos = player.currentPosition
        val state = player.playbackState
        if (state == Player.STATE_IDLE || state == Player.STATE_ENDED) {
            finishFadeReport("el principal quedó en estado $state")
            abortAndRestore()
            return
        }
        val interrupted = player.currentMediaItemIndex != fadeIndex ||
                player.currentMediaItem?.mediaId != fadeMediaId ||
                pos + BACKWARD_TOLERANCE_MS < lastFadePos
        if (interrupted) {
            // El usuario saltó de canción o movió la barra hacia atrás: cierre suave, no corte.
            val why = when {
                player.currentMediaItemIndex != fadeIndex -> "cambió el índice de la cola"
                player.currentMediaItem?.mediaId != fadeMediaId -> "cambió la canción"
                else -> "la posición del principal retrocedió ${lastFadePos - pos} ms"
            }
            finishFadeReport("interrumpido: $why")
            beginSettle(t)
            return
        }
        lastFadePos = pos

        // Pausa / reanudación (o pérdida de foco) en conjunto.
        if (t.playWhenReady != player.playWhenReady) t.playWhenReady = player.playWhenReady

        // El volumen NO empieza a moverse hasta que la siguiente canción suena de verdad: mientras el
        // principal carga (buffering) la cola sigue al 100% y el principal en 0, así no hay bajón.
        // Desde que arranca, el fundido se reparte en lo que le queda a la cola, medido con la
        // posición de la propia cola: llega a 0 justo cuando termina su audio.
        if (fadeTailStartPos < 0L && state == Player.STATE_READY && player.isPlaying) {
            fadeTailStartPos = t.currentPosition
        }
        val progress = if (fadeTailStartPos < 0L) {
            0f
        } else {
            val span = (fadeSongDurMs - fadeTailStartPos).coerceAtLeast(MIN_FADE_MS)
            ((t.currentPosition - fadeTailStartPos).toFloat() / span.toFloat()).coerceIn(0f, 1f)
        }
        applyFilters(progress)
        automixPll(t, nowTick, progress)
        val angle = progress * (PI / 2.0).toFloat()
        val volStart = SystemClock.elapsedRealtime()
        val boost = 1f + MID_BOOST * sin(PI.toFloat() * progress)
        player.volume = (sin(angle) * boost).coerceIn(0f, 1f)
        t.volume = (cos(angle) * boost).coerceIn(0f, 1f)
        val volMs = SystemClock.elapsedRealtime() - volStart
        if (volMs > 50L) noteEvent("cambiar el volumen tardó $volMs ms")

        if (progress >= 1f || t.playbackState == Player.STATE_ENDED) {
            finishFadeReport("completo")
            abortAndRestore()
        }
    }

    // ---------------------------------------------------------------------------------------
    // AUTOMIX: igualar el tempo y alinear los beats durante el fundido
    // ---------------------------------------------------------------------------------------

    private fun songPath(item: MediaItem?): String? =
        item?.mediaId?.toLongOrNull()
            ?.let { SongLibraryHolder.findById(it)?.path }
            ?.takeIf { it.isNotEmpty() }

    /** Publica (y loguea) por qué este fundido no usa AutoMix. */
    private fun planDiag(text: String) {
        Log.i(TAG, text)
        try {
            settings.edit().putString(KEY_AUTOMIX_PLAN_DIAG, "NOTE|$text").apply()
        } catch (_: Exception) {
        }
    }

    private fun automixDelayMs(): Float =
        settings.getFloat(KEY_AUTOMIX_DELAY, AUTOMIX_DEFAULT_DELAY_MS.toFloat())

    /** Plan de AutoMix para este fundido, calculado UNA vez por canción. null = fundido normal. */
    private fun planAutomix(key: String): AutomixPlan? {
        if (plannedKey == key) return autoPlan
        plannedKey = key
        autoPlan = null
        triggerTailPos = -1L
        if (!automixEnabled()) return null
        val repo = automixCacheRepo ?: return null
        val plan = try {
            computePlan(repo)
        } catch (e: Exception) {
            Log.w(TAG, "AutoMix no pudo planear este fundido; se hace el normal", e)
            planDiag("AutoMix: error al planear (${e.javaClass.simpleName}), fundido normal")
            null
        }
        autoPlan = plan
        return plan
    }

    private fun computePlan(repo: AutomixCacheRepository): AutomixPlan? {
        val tailPath = songPath(player.currentMediaItem)
        val nextIndex = player.nextMediaItemIndex
        val mainPath = if (nextIndex != C.INDEX_UNSET) songPath(player.getMediaItemAt(nextIndex)) else null
        if (tailPath == null || mainPath == null) {
            planDiag("AutoMix: falta la ruta de una de las canciones, fundido normal")
            return null
        }
        val tailData = repo.get(tailPath)
        val mainData = repo.get(mainPath)
        if (tailData == null || mainData == null) {
            val who = if (tailData == null) "la que sale" else "la que entra"
            planDiag("AutoMix: sin análisis fiable de $who, fundido normal")
            return null
        }
        if (min(tailData.confidence, mainData.confidence) < MIN_AUTOMIX_CONFIDENCE) {
            planDiag("AutoMix: confianza baja, fundido normal")
            return null
        }
        val ratio = mainData.bpm.toDouble() / tailData.bpm.toDouble()
        if (abs(ratio - 1.0) > MAX_TEMPO_DIFF_PCT) {
            planDiag(
                "AutoMix: BPM muy distintos (${String.format(java.util.Locale.US, "%.1f", tailData.bpm)} → " +
                    "${String.format(java.util.Locale.US, "%.1f", mainData.bpm)}, " +
                    "${String.format(java.util.Locale.US, "%.0f", abs(ratio - 1.0) * 100)} %), fundido normal"
            )
            return null
        }
        val tailPeriod = 60000.0 / tailData.bpm
        val mainPeriod = 60000.0 / mainData.bpm
        val anchor = if (tailData.lastBeatMs > 0L) {
            tailData.lastBeatMs.toDouble()
        } else {
            val dur = player.duration.toDouble()
            tailData.beatPhaseMs + tailPeriod * floor((dur - tailData.beatPhaseMs) / tailPeriod)
        }
        return AutomixPlan(
            ratio = ratio,
            tailBpm = tailData.bpm,
            mainBpm = mainData.bpm,
            tailPeriodMs = tailPeriod,
            tailAnchorMs = anchor,
            mainPeriodMs = mainPeriod,
            mainFirstBeatMs = mainData.beatPhaseMs.toDouble(),
            confidence = min(tailData.confidence, mainData.confidence)
        )
    }

    /**
     * true = ya se puede disparar el fundido; false = seguir esperando el beat.
     *
     * Se busca la posición p de la cola tal que, cuando el principal empiece a sonar (p + retraso),
     * un beat de la cola caiga sobre el primer beat de la entrante:
     *     p ≡ ancla − retraso − ratio * primerBeatEntrante   (mod periodo de la cola)
     * y se toma la primera p posterior al instante actual (espera < 1 beat). Si esperar dejaría muy
     * poca canción, o más de AUTOMIX_MAX_ALIGN_WAIT_MS, no se alinea: fundido normal.
     */
    private fun automixBeatReached(ap: AutomixPlan, t: ExoPlayer, dur: Long): Boolean {
        val tailPos = t.currentPosition
        if (triggerTailPos < 0L) {
            val raw = ap.tailAnchorMs - automixDelayMs() - ap.ratio * ap.mainFirstBeatMs
            val guard = tailPos + AUTOMIX_TRIGGER_GUARD_MS
            val n = ceil((guard - raw) / ap.tailPeriodMs)
            val target = (raw + n * ap.tailPeriodMs).toLong()
            if (target - tailPos > AUTOMIX_MAX_ALIGN_WAIT_MS || dur - target < AUTOMIX_MIN_REMAINING_AFTER_WAIT_MS) {
                planDiag("AutoMix: no hay tiempo para alinear el beat, fundido normal")
                autoPlan = null
                return true
            }
            triggerTailPos = target
        }
        return tailPos >= triggerTailPos
    }

    /** Cambia la velocidad de la cola (pitch intacto) al tempo de la canción que entra. */
    private fun applyAutomixTempo(t: ExoPlayer, ap: AutomixPlan) {
        try {
            t.playbackParameters = PlaybackParameters(ap.ratio.toFloat(), 1f)
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo cambiar el tempo de la cola; fundido normal", e)
            autoPlan = null
            return
        }
        automixActive = true
        tailSpeed = ap.ratio.toFloat()
        mainStartedAt = 0L
        lastPllAt = 0L
        pllLearned = false
        publishPlanDiag(ap, t.currentPosition, null)
    }

    private fun publishPlanDiag(ap: AutomixPlan, cueMs: Long, errMs: Float?) {
        try {
            val err = if (errMs == null) "-" else String.format(java.util.Locale.US, "%.0f", errMs)
            settings.edit().putString(
                KEY_AUTOMIX_PLAN_DIAG,
                String.format(
                    java.util.Locale.US, "%.3f|%d|%.1f|%.1f|%d|%s",
                    ap.ratio, cueMs, ap.tailBpm, ap.mainBpm, (ap.confidence * 100).toInt(), err
                )
            ).apply()
        } catch (_: Exception) {
        }
    }

    private fun posMod(x: Double, m: Double): Double {
        val r = x % m
        return if (r < 0.0) r + m else r
    }

    /**
     * Lazo de fase: con las dos canciones sonando, mide cuánto se adelanta (o atrasa) el beat de la
     * cola respecto al del principal y ajusta la velocidad de la cola (máx. +-4 %) para cerrar el
     * desfase. Usa FRACCIONES de periodo, así no depende de la velocidad vigente.
     * La primera medida también corrige el retraso estimado (automix_delay_ms) para el siguiente fundido.
     */
    private fun automixPll(t: ExoPlayer, now: Long, progress: Float) {
        val ap = autoPlan ?: return
        if (!automixActive || fadeTailStartPos < 0L) return
        if (mainStartedAt == 0L) {
            mainStartedAt = now
            return
        }
        if (now - mainStartedAt < PLL_START_DELAY_MS || now - lastPllAt < PLL_INTERVAL_MS) return
        lastPllAt = now
        if (progress > PLL_STOP_PROGRESS) return

        val fracTail = posMod(t.currentPosition - ap.tailAnchorMs, ap.tailPeriodMs) / ap.tailPeriodMs
        val fracMain = posMod(player.currentPosition - ap.mainFirstBeatMs, ap.mainPeriodMs) / ap.mainPeriodMs
        var dFrac = fracTail - fracMain
        dFrac -= round(dFrac) // a (-0.5, 0.5]: >0 = la cola va adelantada
        val errMs = (dFrac * ap.mainPeriodMs).toFloat() - settings.getFloat(KEY_AUTOMIX_PHASE_OFFSET, 0f)

        if (!pllLearned) {
            pllLearned = true
            val learned = (automixDelayMs() + 0.5f * errMs)
                .coerceIn(AUTOMIX_MIN_DELAY_MS.toFloat(), AUTOMIX_MAX_DELAY_MS.toFloat())
            settings.edit().putFloat(KEY_AUTOMIX_DELAY, learned).apply()
            publishPlanDiag(ap, fadeTailStartPos, errMs)
        }
        val trim = if (abs(errMs) <= PLL_DEADBAND_MS) 0f else (errMs / PLL_WINDOW_MS).coerceIn(-PLL_MAX_TRIM, PLL_MAX_TRIM)
        val target = (ap.ratio * (1.0 - trim)).toFloat()
        if (abs(target - tailSpeed) > 0.002f) {
            try {
                t.playbackParameters = PlaybackParameters(target, 1f)
                tailSpeed = target
            } catch (e: Exception) {
                Log.w(TAG, "AutoMix: no se pudo ajustar la fase", e)
            }
        }
    }

    /** Vuelve la cola a velocidad 1.0 y olvida el plan (se llama al descartar la cola). */
    private fun resetAutomix() {
        if (automixActive || tailSpeed != 1f) {
            try {
                tail?.playbackParameters = PlaybackParameters.DEFAULT
            } catch (_: Exception) {
            }
        }
        automixActive = false
        tailSpeed = 1f
        plannedKey = null
        autoPlan = null
        triggerTailPos = -1L
        mainStartedAt = 0L
        lastPllAt = 0L
        pllLearned = false
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

    /** Mueve los filtros segun el progreso del fundido (0 = recien empieza, 1 = termina). */
    private fun applyFilters(progress: Float) {
        val p = progress.coerceIn(0f, 1f)
        mainFilter?.setFilter(true, HP_START_HZ * (HP_END_HZ / HP_START_HZ).pow(p))
        tailFilterProvider()?.setFilter(true, LP_START_HZ * (LP_END_HZ / LP_START_HZ).pow(p))
    }

    private fun releaseFilters() {
        mainFilter?.setFilter(false, HP_END_HZ)
        tailFilterProvider()?.setFilter(false, LP_START_HZ)
    }

    /** Termina (o cancela) todo: volumen principal al 100% y la cola se libera. */
    private fun abortAndRestore() {
        releaseFilters()
        handoffStartedAt = 0L
        fading = false
        settling = false
        CrossfadeSignal.fadeActive = false
        try {
            player.volume = 1f
        } catch (_: Exception) {
        }
        discardTail()
    }

    private fun discardTail() {
        resetAutomix()
        if (handoffStartedAt != 0L) {
            // Se canceló en pleno traspaso: devolver el volumen del principal.
            handoffStartedAt = 0L
            try {
                player.volume = 1f
            } catch (_: Exception) {
            }
        }
        tail?.let {
            try {
                it.release()
            } catch (_: Exception) {
            }
        }
        try {
            tailEffects?.close()
        } catch (_: Exception) {
        }
        tailEffects = null
        tail = null
        tailKey = null
        tailWarm = false
        tailSynced = false
    }
}