package com.music.musicflame.audio

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.util.Log
import com.music.musicflame.data.AutomixCacheRepository
import com.music.musicflame.data.AutomixData
import com.music.musicflame.data.Song
import com.music.musicflame.data.SettingsStore
import java.io.File
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.PI
import kotlin.math.round
import kotlin.math.sin
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.metrolist.beatweave.learned.LocalSongAnalysis
import org.metrolist.beatweave.learned.LocalSongAnalyzer
import org.metrolist.beatweave.learned.OrtBeatThisBackend

/**
 * AUTOMIX: analiza una canción local y devuelve su BPM, tonalidad y rejilla de beats.
 *
 * Usa SOLO BeatWeave (módulos analysis + learned-beats, ambos MIT) para el análisis: el audio se
 * decodifica con MediaCodec (como VolumeNormalizationAnalyzer), se pasa a mono a 22050 Hz con un
 * remuestreo de banda limitada (BeatWeave lo exige así) y se entrega a LocalSongAnalyzer.
 *
 * Reglas:
 *  - Solo archivos locales (nunca http/rtsp/content remotos).
 *  - Corre en Dispatchers.IO con THREAD_PRIORITY_BACKGROUND.
 *  - Nunca lanza: cualquier fallo de BeatWeave (incluido OutOfMemory) es Log.w + null, y el fundido
 *    de esa canción queda como el normal de siempre. Solo propaga la cancelación de la coroutine.
 *  - De a una canción a la vez (Mutex): el modelo ONNX y la memoria no se comparten bien en paralelo.
 *  - Para no tener ~30 MB de ONNX Runtime residentes, la sesión se cierra tras 45 s sin usarse.
 */
object AutomixAnalyzer {
    private const val TAG = "AutomixAnalyzer"

    private const val TARGET_RATE = 22050

    /**
     * Presupuesto de audio que se le entrega al analizador por canción (lo elige el slider de Ajustes).
     * Si la canción dura menos se analiza entera; si dura más se analiza la mitad del presupuesto del
     * INICIO (tempo, fase, tonalidad) y la otra mitad del FINAL (mide el último beat en vez de
     * extrapolarlo, y detecta cambios de tempo a mitad de canción).
     */
    const val PREF_ANALYSIS_SECONDS = "automix_analysis_seconds"
    const val DEFAULT_ANALYSIS_SECONDS = 60
    const val MIN_ANALYSIS_BUDGET_SECONDS = 40
    const val MAX_ANALYSIS_BUDGET_SECONDS = 240

    /** Archivos más largos (mixes, podcasts) solo se analizan por el inicio: no se decodifican hasta el final. */
    private const val MAX_TAIL_FILE_SECONDS = 20 * 60

    /** Con menos beats que esto en la cola, esa ventana no sirve de evidencia y se ignora. */
    private const val MIN_TAIL_BEATS = 8

    /** Menos que esto no alcanza para estimar un pulso. */
    private const val MIN_ANALYSIS_SECONDS = 8

    /** El prompt original: si la confianza no llega a 0.5 se descarta. */
    private const val MIN_CONFIDENCE = 0.5

    private const val MIN_BPM = 40.0
    private const val MAX_BPM = 240.0

    /** Si algún beat se aleja más de esto de la rejilla de tempo constante, la canción no es "de reloj". */
    private const val MAX_GRID_RESIDUAL_SEC = 0.06
    private const val IRREGULAR_GRID_CONFIDENCE_FACTOR = 0.6

    private const val DEQUEUE_TIMEOUT_US = 10_000L

    // Ahorro de CPU al decodificar (ver decodeHeadTail). Ambos mecanismos solo actúan si la duración
    // declarada del archivo es conocida y el hueco entre la cabeza y la cola vale la pena.
    /** Mínimo de hueco (s) entre el final de la cabeza y el inicio de la cola para saltarlo o no procesarlo. */
    private const val MIN_SKIP_GAP_SEC = 10.0
    /** Salto con seekTo: se aterriza esto (s) ANTES de la ventana de cola; el anillo descarta ese precalentamiento. */
    private const val SEEK_SAFETY_SEC = 4.0
    /** Si el seek aterriza más lejos que esto antes de lo pedido, el índice del contenedor no es fiable. */
    private const val SEEK_MAX_UNDERSHOOT_US = 5_000_000L
    /** Sin seek: se vuelve a convertir/remuestrear esto antes de la ventana de cola (la duración declarada puede errar). */
    private const val SKIP_MARGIN_SEC = 15.0
    private const val SKIP_MARGIN_FRACTION = 0.05
    private const val IDLE_CLOSE_MS = 45_000L

    private val KEY_NAMES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    private val mutex = Mutex()

    /** Motivo por el que el último análisis no dio datos (para mostrarlo en Ajustes). */
    @Volatile private var failReason = ""
    private var backend: OrtBeatThisBackend? = null // protegido por [mutex]
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private val idleClose = Runnable { closeBackendIfIdle() }

    /**
     * Analiza [filePath]. null si no es un archivo local, el modelo no está disponible, el archivo no
     * se pudo decodificar, BeatWeave falló o la confianza es menor a 0.5.
     */
    suspend fun analyze(filePath: String, context: Context): AutomixData? {
        if (!isLocalFile(filePath)) return null
        val modelFile = AutomixModelManager.ensureModel(context.applicationContext)
        if (modelFile == null) {
            Log.w(TAG, "Sin modelo de AutoMix (¿sin red?): no se analiza")
            AutomixStatus.set("Sin modelo de AutoMix: ${AutomixModelManager.lastError.ifEmpty { "sin descargar" }}")
            return null
        }
        return withContext(Dispatchers.IO) {
            val tid = Process.myTid()
            val oldPriority = Process.getThreadPriority(tid)
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            try {
                val job = currentCoroutineContext()[Job]
                val cancel: () -> Unit = { job?.ensureActive() }
                val name = File(filePath).name
                val budgetSec = SettingsStore.get(context.applicationContext)
                    .getInt(PREF_ANALYSIS_SECONDS, DEFAULT_ANALYSIS_SECONDS)
                    .coerceIn(MIN_ANALYSIS_BUDGET_SECONDS, MAX_ANALYSIS_BUDGET_SECONDS)
                val startedAt = SystemClock.elapsedRealtime()
                val result = mutex.withLock {
                    failReason = ""
                    AutomixStatus.set("Analizando: $name…")
                    analyzeBlocking(filePath, modelFile, budgetSec, cancel)
                }
                val elapsedSec = (SystemClock.elapsedRealtime() - startedAt) / 1000.0
                AutomixStatus.set(
                    if (result != null) {
                        "Listo: $name · ${"%.1f".format(result.bpm)} BPM (confianza ${(result.confidence * 100).toInt()} %) · ${"%.1f".format(elapsedSec)} s"
                    } else {
                        "Sin datos: $name — ${failReason.ifEmpty { "motivo desconocido" }}"
                    }
                )
                result
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // BeatWeave puede envolver la excepción de cancelación en otra: si la coroutine se
                // canceló, se propaga la cancelación y NO se cuenta como un fallo del archivo.
                currentCoroutineContext().ensureActive()
                // Incluye OutOfMemoryError y UnsatisfiedLinkError: AutoMix nunca debe tumbar la app.
                Log.w(TAG, "BeatWeave falló con $filePath; esta canción usará fundido normal", e)
                AutomixStatus.set("Error: ${File(filePath).name} — ${e.javaClass.simpleName}: ${e.message}")
                null
            } finally {
                Process.setThreadPriority(tid, oldPriority)
                scheduleIdleClose()
            }
        }
    }

    /** Libera la sesión de ONNX Runtime (el servicio lo llama en onDestroy). */
    fun release() {
        mainHandler.removeCallbacks(idleClose)
        closeBackendIfIdle()
    }

    private fun scheduleIdleClose() {
        mainHandler.removeCallbacks(idleClose)
        mainHandler.postDelayed(idleClose, IDLE_CLOSE_MS)
    }

    private fun closeBackendIfIdle() {
        if (!mutex.tryLock()) return // hay un análisis en curso: se reprograma al terminar
        try {
            try {
                backend?.close()
            } catch (e: Throwable) {
                Log.w(TAG, "No se pudo cerrar la sesión de ONNX", e)
            }
            backend = null
        } finally {
            mutex.unlock()
        }
    }

    private fun isLocalFile(path: String): Boolean {
        if (path.isEmpty()) return false
        val lower = path.lowercase()
        if (lower.startsWith("http:") || lower.startsWith("https:") || lower.startsWith("rtsp:")) return false
        if (path.contains("://") && !lower.startsWith("file://")) return false
        return File(path.removePrefix("file://")).isFile
    }

    // ---------------------------------------------------------------------------------------
    // Análisis (bloqueante; con el mutex tomado)
    // ---------------------------------------------------------------------------------------

    private fun analyzeBlocking(filePath: String, modelFile: File, budgetSec: Int, cancel: () -> Unit): AutomixData? {
        val headSec = budgetSec / 2
        val tailSec = budgetSec - headSec
        val decoded = decodeHeadTail(filePath.removePrefix("file://"), headSec, tailSec, cancel) ?: run {
            failReason = "no se pudo decodificar el audio (o dura menos de 8 s)"
            return null
        }
        cancel()

        val be = backend ?: try {
            OrtBeatThisBackend(modelFile, threads = 2)
        } catch (e: IllegalArgumentException) {
            // El archivo no coincide con el SHA-256 que BeatWeave exige: está corrupto; se vuelve a bajar.
            modelFile.delete()
            throw e
        }.also { backend = it }

        val analyzer = LocalSongAnalyzer(be)
        val headAnalysis = analyzer.analyze(decoded.head, cancel)
        cancel()
        // La cola es evidencia extra: si falla por lo que sea, se sigue solo con el inicio.
        val tailAnalysis = decoded.tail?.let { tailPcm ->
            try {
                analyzer.analyze(tailPcm, cancel)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                cancel() // si en realidad fue una cancelación envuelta, se propaga
                Log.w(TAG, "No se pudo analizar el final de la canción; se usa solo el inicio", e)
                null
            }
        }
        return toAutomixData(headAnalysis, tailAnalysis, decoded)
    }

    private fun toAutomixData(head: LocalSongAnalysis, tail: LocalSongAnalysis?, decoded: Decoded): AutomixData? {
        if (!head.pulse.quality.safeForAutomaticMix) {
            Log.w(TAG, "Rejilla de beats no apta para mezclar automáticamente")
            failReason = "rejilla de beats no apta para mezclar"
            return null
        }
        var confidence = head.audio.tempoConfidence.coerceIn(0.0, 1.0)
        val headTimes = DoubleArray(head.pulse.beats.size) { head.pulse.beats[it].seconds }

        // La cola solo cuenta si su propio pulso es apto y tiene beats suficientes; sus tiempos vienen
        // relativos al inicio de la ventana, así que se desplazan al reloj de la canción.
        val tailTimes: DoubleArray? = if (
            tail != null && decoded.tail != null && tail.pulse.quality.safeForAutomaticMix
        ) {
            DoubleArray(tail.pulse.beats.size) { tail.pulse.beats[it].seconds + decoded.tailStartSec }
                .takeIf { it.size >= MIN_TAIL_BEATS }
        } else {
            null
        }

        val fit = fitGrid(headTimes, tailTimes) ?: run {
            failReason = "se detectaron muy pocos beats"
            return null
        }
        val bpm = 60.0 / fit.periodSec
        if (bpm !in MIN_BPM..MAX_BPM) {
            Log.w(TAG, "BPM fuera de rango (${"%.1f".format(bpm)})")
            failReason = "BPM fuera de rango (${"%.1f".format(bpm)})"
            return null
        }
        // Con cola, el residuo también mide si el tempo se mantuvo entre el inicio y el final.
        val regular = fit.maxResidualSec <= MAX_GRID_RESIDUAL_SEC
        if (!regular) confidence *= IRREGULAR_GRID_CONFIDENCE_FACTOR
        if (confidence < MIN_CONFIDENCE) {
            failReason = "confianza baja (${"%.2f".format(confidence)})"
            return null
        }

        // Fase: un beat de la rejilla normalizado a [0, periodo).
        val phaseSec = ((fit.anchorSec % fit.periodSec) + fit.periodSec) % fit.periodSec

        // Último beat de la canción: si el tempo es regular, la rejilla ajustada extendida hasta el final
        // del archivo; si no lo es (y se observó el final), el último beat realmente observado.
        val totalSec = decoded.durationMs / 1000.0
        val gridLast = fit.anchorSec + fit.periodSec * floor((totalSec - fit.anchorSec) / fit.periodSec)
        val observedLast = tailTimes?.last() ?: headTimes.last()
        val lastBeatSec = if ((decoded.coversWholeFile || tailTimes != null) && !regular) observedLast else gridLast

        val key = KEY_NAMES.indexOf(head.audio.keyEstimate.trim().substringBefore(' '))
        return AutomixData(
            bpm = bpm.toFloat(),
            key = key,
            beatPhaseMs = round(phaseSec * 1000.0).toLong(),
            confidence = confidence.toFloat(),
            lastBeatMs = round(lastBeatSec * 1000.0).toLong()
        )
    }

    private class GridFit(val periodSec: Double, val anchorSec: Double, val maxResidualSec: Double)

    /**
     * Ajusta t = ancla + periodo * k por mínimos cuadrados.
     *
     * Los beats de Beat This! salen cuantizados a 20 ms (frame / 50), así que la mediana de intervalos
     * puede errar por varios ms. Si el índice k se calculara como round((t - t0) / mediana), ese error
     * se acumularía beat a beat y el ajuste daría BPM desviados 1-3 y residuos enormes. Por eso k se
     * obtiene sumando el entero más cercano de cada intervalo consecutivo (tolera beats faltantes y no
     * acumula el error del periodo).
     *
     * [tailTimes] (ventana del final, ya en el reloj de la canción) se engancha a la rejilla del inicio:
     * su primer k sale del periodo ajustado en la cabeza, que es mucho más preciso que la mediana.
     */
    private fun fitGrid(headTimes: DoubleArray, tailTimes: DoubleArray?): GridFit? {
        if (headTimes.size < 8) return null
        val diffs = DoubleArray(headTimes.size - 1) { headTimes[it + 1] - headTimes[it] }.also { it.sort() }
        val p0 = diffs[diffs.size / 2]
        if (p0 <= 0.0) return null

        val cap = headTimes.size + (tailTimes?.size ?: 0)
        val ts = DoubleArray(cap)
        val ks = DoubleArray(cap)
        ts[0] = headTimes[0]
        ks[0] = 0.0
        var n = 1
        for (i in 1 until headTimes.size) {
            val step = round((headTimes[i] - headTimes[i - 1]) / p0)
            if (step < 1.0) continue // beat espurio pegado al anterior: no entra al ajuste
            ts[n] = headTimes[i]
            ks[n] = ks[n - 1] + step
            n++
        }
        val headFit = leastSquares(ts, ks, n) ?: return null
        var period = headFit.first
        var anchor = headFit.second

        if (tailTimes != null && tailTimes.isNotEmpty()) {
            var prevT = Double.NaN
            var prevK = 0.0
            for (i in tailTimes.indices) {
                val t = tailTimes[i]
                val k = if (i == 0) {
                    round((t - anchor) / period)
                } else {
                    val step = round((t - prevT) / period)
                    if (step < 1.0) continue
                    prevK + step
                }
                ts[n] = t
                ks[n] = k
                n++
                prevT = t
                prevK = k
            }
            val all = leastSquares(ts, ks, n) ?: return null
            period = all.first
            anchor = all.second
        }
        if (period <= 0.0) return null

        var maxRes = 0.0
        for (i in 0 until n) maxRes = max(maxRes, abs(ts[i] - (anchor + period * ks[i])))
        return GridFit(period, anchor, maxRes)
    }

    /** Regresión t = ancla + periodo * k. Devuelve (periodo, ancla) o null si degenera. */
    private fun leastSquares(ts: DoubleArray, ks: DoubleArray, n: Int): Pair<Double, Double>? {
        if (n < 2) return null
        var sumK = 0.0
        var sumT = 0.0
        var sumKK = 0.0
        var sumKT = 0.0
        for (i in 0 until n) {
            sumK += ks[i]
            sumT += ts[i]
            sumKK += ks[i] * ks[i]
            sumKT += ks[i] * ts[i]
        }
        val nd = n.toDouble()
        val denom = nd * sumKK - sumK * sumK
        if (denom <= 0.0) return null
        val period = (nd * sumKT - sumK * sumT) / denom
        if (period <= 0.0) return null
        return Pair(period, (sumT - period * sumK) / nd)
    }

    // ---------------------------------------------------------------------------------------
    // Decodificación: MediaCodec -> mono -> 22050 Hz
    // ---------------------------------------------------------------------------------------

    /**
     * [head]: PCM mono 22050 Hz del inicio. [tail]: PCM de la ventana final (null si la canción cabe
     * entera en [head] o el archivo es demasiado largo), que empieza en [tailStartSec] del reloj de la
     * canción. [coversWholeFile]: [head] ES la canción completa.
     */
    private class Decoded(
        val head: FloatArray,
        val tail: FloatArray?,
        val tailStartSec: Double,
        val coversWholeFile: Boolean,
        val durationMs: Long
    )

    /** Fallo recuperable del salto al final: el llamador repite la decodificación en orden, sin seek. */
    private class SeekFailed(message: String) : RuntimeException(message)

    /**
     * Decodifica la cabeza y la cola del archivo a PCM mono 22050 Hz, conservando solo los primeros
     * [headSec] s y los últimos [tailSec] s (el analizador, lo caro, nunca ve el medio).
     *
     * El reloj de la cabeza y el de la cola DEBEN ser el mismo: fitGrid() engancha la cola a la rejilla de
     * la cabeza redondeando (t - ancla) / periodo, y exige un residuo < 60 ms. Por eso hay dos caminos:
     *
     *  - Contenedores con índice exacto (m4a, flac, ogg, opus, wav...): tras llenar la cabeza se hace
     *    extractor.seekTo() unos segundos antes de la ventana final y solo se decodifica el último tramo.
     *    El reloj de la cola sale del presentationTimeUs real de la primera salida tras el salto.
     *  - mp3 (y cualquier caso donde el seek no sea fiable): se decodifica en orden y el reloj sale de
     *    contar muestras, así no hereda el error de búsqueda de los mp3 VBR (con o sin tabla TOC el seek
     *    mp3 es aproximado, y sampleTime repite esa misma estimación). Para no gastar CPU en el medio,
     *    ese tramo se decodifica pero NO se convierte a mono ni se remuestrea; el reloj se mantiene
     *    contando los frames de entrada.
     *
     * Si el seek no aterriza donde se pidió (SeekFailed) se repite todo en orden.
     */
    private fun decodeHeadTail(filePath: String, headSec: Int, tailSec: Int, cancel: () -> Unit): Decoded? =
        try {
            decodeHeadTailImpl(filePath, headSec, tailSec, cancel, allowSeek = true)
        } catch (e: SeekFailed) {
            Log.w(TAG, "Salto al final no fiable (${e.message}); se decodifica en orden: $filePath")
            decodeHeadTailImpl(filePath, headSec, tailSec, cancel, allowSeek = false)
        }

    private fun decodeHeadTailImpl(
        filePath: String,
        headSec: Int,
        tailSec: Int,
        cancel: () -> Unit,
        allowSeek: Boolean
    ): Decoded? {
        var extractor: MediaExtractor? = null
        var codec: MediaCodec? = null
        try {
            extractor = MediaExtractor()
            extractor.setDataSource(filePath)
            val track = selectAudioTrack(extractor) ?: return null
            val format = extractor.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            val declaredDurationUs =
                if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else -1L
            extractor.selectTrack(track)

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var pcmFloat = false
            var resampler: StreamingResampler? = null

            val headCap = TARGET_RATE * headSec
            val tailEnabled = tailSec > 0 &&
                (declaredDurationUs <= 0L || declaredDurationUs <= MAX_TAIL_FILE_SECONDS * 1_000_000L)
            val tailCap = if (tailEnabled) TARGET_RATE * tailSec else 0
            val expectedHead = if (declaredDurationUs > 0) {
                min(declaredDurationUs / 1_000_000.0 * TARGET_RATE, headCap.toDouble()).toInt()
            } else {
                TARGET_RATE * 60
            }
            val sink = HeadTailSink(headCap, tailCap, expectedHead + 4096)
            var mono = FloatArray(8192)

            // Con cola se decodifica hasta el final (con tope); sin ella, solo hasta llenar la cabeza.
            val maxInputUs = (if (tailEnabled) MAX_TAIL_FILE_SECONDS else headSec) * 1_000_000L
            var sawInputEos = false
            var sawOutputEos = false
            var coversWhole = false
            var truncated = false
            val info = MediaCodec.BufferInfo()

            // --- Cómo se llega a la cola ---------------------------------------------------------
            val declaredSec = if (declaredDurationUs > 0L) declaredDurationUs / 1_000_000.0 else 0.0
            val canJump = tailEnabled && declaredSec > 0.0
            val isMpeg = mime.startsWith("audio/mpeg") // mp3 / mpeg-L1 / mpeg-L2: seek aproximado
            val seekTargetSec = declaredSec - tailSec - SEEK_SAFETY_SEC
            val resumeSec = declaredSec - tailSec - SKIP_MARGIN_SEC - SKIP_MARGIN_FRACTION * declaredSec
            val seekWanted = allowSeek && canJump && !isMpeg && seekTargetSec > headSec + MIN_SKIP_GAP_SEC
            val skipMiddle = !seekWanted && canJump && resumeSec > headSec + MIN_SKIP_GAP_SEC

            var modeDecided = false  // ya se eligió salto / hueco / nada al llenarse la cabeza
            var seeked = false
            var skipping = false     // hueco: se decodifica pero no se procesa
            var skippedAny = false
            var framesSeen = 0L      // frames de entrada decodificados (reloj exacto en el camino sin seek)
            var resumeFrame = 0L
            var segStartUs = -1L     // presentationTimeUs de la primera salida tras el seek
            var seekSinkTotal = 0L

            while (!sawOutputEos) {
                cancel()
                if (!sawInputEos) {
                    val inIdx = codec.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
                    if (inIdx >= 0) {
                        val inBuf = codec.getInputBuffer(inIdx)
                        val size = inBuf?.let { extractor.readSampleData(it, 0) } ?: -1
                        val timeUs = extractor.sampleTime
                        if (size < 0) {
                            coversWhole = true
                            codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            sawInputEos = true
                        } else if (timeUs > maxInputUs) {
                            if (tailEnabled) truncated = true // no se llegó al final real: no hay cola válida
                            codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            sawInputEos = true
                        } else {
                            codec.queueInputBuffer(inIdx, 0, size, timeUs, 0)
                            extractor.advance()
                        }
                    }
                }

                val outIdx = codec.dequeueOutputBuffer(info, DEQUEUE_TIMEOUT_US)
                when {
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val f = codec.outputFormat
                        if (f.containsKey(MediaFormat.KEY_SAMPLE_RATE)) sampleRate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        if (f.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        pcmFloat = f.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                            f.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                        resampler = null
                    }
                    outIdx >= 0 -> {
                        if (info.size > 0 && channels > 0) {
                            codec.getOutputBuffer(outIdx)?.let { bb ->
                                bb.order(ByteOrder.LITTLE_ENDIAN)
                                bb.position(info.offset)
                                bb.limit(info.offset + info.size)
                                val bytesPerSample = if (pcmFloat) 4 else 2
                                val frames = info.size / (bytesPerSample * channels)
                                if (frames > 0) {
                                    if (seeked && segStartUs < 0L) segStartUs = info.presentationTimeUs

                                    var process = true
                                    if (skipping) {
                                        if (framesSeen >= resumeFrame) {
                                            // Fin del hueco: se vuelve a procesar. El reloj sale de los frames contados.
                                            skipping = false
                                            resampler = null
                                            sink.jumpTo(Math.round(framesSeen.toDouble() * TARGET_RATE / sampleRate))
                                        } else {
                                            process = false
                                        }
                                    }
                                    framesSeen += frames

                                    if (process) {
                                        if (mono.size < frames) mono = FloatArray(frames)
                                        if (pcmFloat) {
                                            val fb = bb.asFloatBuffer()
                                            for (i in 0 until frames) {
                                                var s = 0f
                                                for (c in 0 until channels) s += fb.get()
                                                mono[i] = s / channels
                                            }
                                        } else {
                                            val sb = bb.asShortBuffer()
                                            for (i in 0 until frames) {
                                                var s = 0f
                                                for (c in 0 until channels) s += sb.get() / 32768f
                                                mono[i] = s / channels
                                            }
                                        }
                                        if (sampleRate == TARGET_RATE) {
                                            sink.addAll(mono, frames)
                                        } else {
                                            val r = resampler ?: StreamingResampler(sampleRate, TARGET_RATE).also { resampler = it }
                                            r.push(mono, frames, sink)
                                        }
                                    }
                                }
                            }
                        }
                        codec.releaseOutputBuffer(outIdx, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) sawOutputEos = true
                        if (!tailEnabled && sink.headFull) break // sin cola: con la cabeza llena ya no hace falta más

                        // La cabeza acaba de llenarse: se decide cómo llegar a la cola.
                        if (tailEnabled && sink.headFull && !modeDecided && !sawOutputEos && !sawInputEos) {
                            modeDecided = true
                            if (seekWanted) {
                                val targetUs = (seekTargetSec * 1_000_000.0).toLong()
                                extractor.seekTo(targetUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                                val landedUs = extractor.sampleTime
                                if (landedUs < 0L || landedUs > targetUs + 1_000_000L ||
                                    landedUs < targetUs - SEEK_MAX_UNDERSHOOT_US
                                ) {
                                    throw SeekFailed("pedido ${targetUs / 1000} ms, aterrizó en ${landedUs / 1000} ms")
                                }
                                codec.flush() // modo síncrono: tras flush() se sigue sin llamar a start()
                                sink.resetTail() // lo que cayó en el anillo al llenarse la cabeza no es de la cola
                                resampler = null
                                seeked = true
                                seekSinkTotal = sink.total
                                segStartUs = -1L
                            } else if (skipMiddle) {
                                skipping = true
                                skippedAny = true
                                sink.resetTail()
                                resumeFrame = (resumeSec * sampleRate).toLong()
                            }
                        }
                    }
                }
            }
            resampler?.finish(sink)

            val total = sink.total
            if (total < TARGET_RATE * MIN_ANALYSIS_SECONDS) return null
            val pcmDurationMs = total * 1000L / TARGET_RATE
            val durationMs = if (declaredDurationUs > 0) declaredDurationUs / 1000L else pcmDurationMs
            val headArr = sink.headArray()
            val rest = total - headArr.size

            return when {
                // Salto con seek: la cola es el anillo, fechada con el tiempo real de la primera salida.
                seeked -> {
                    val tail = sink.tailArray()
                    if (segStartUs < 0L || tail.size < TARGET_RATE * MIN_ANALYSIS_SECONDS) {
                        Decoded(headArr, null, 0.0, false, durationMs)
                    } else {
                        val segSamples = sink.total - seekSinkTotal
                        val tailStartSec = segStartUs / 1_000_000.0 + (segSamples - tail.size).toDouble() / TARGET_RATE
                        Decoded(headArr, tail, tailStartSec, false, durationMs)
                    }
                }
                // Cabió entera en la cabeza.
                rest <= 0L -> Decoded(headArr, null, 0.0, coversWhole && !truncated, durationMs)
                // Sin cola (archivo larguísimo o presupuesto sin cola): solo el inicio, el final se extrapola.
                !tailEnabled || truncated -> Decoded(headArr, null, 0.0, false, durationMs)
                // Cabeza + final se tocan: es la canción completa y continua (sin costura entre ventanas).
                // Con hueco saltado nunca es continua, aunque el conteo parezca caber.
                rest <= tailCap && !skippedAny -> Decoded(headArr + sink.tailArray(), null, 0.0, coversWhole, durationMs)
                // Canción larga: inicio + final, con un hueco en medio que nunca se analiza.
                else -> {
                    val tail = sink.tailArray()
                    if (tail.size < TARGET_RATE * MIN_ANALYSIS_SECONDS) {
                        Decoded(headArr, null, 0.0, false, durationMs)
                    } else {
                        Decoded(headArr, tail, (total - tail.size).toDouble() / TARGET_RATE, false, durationMs)
                    }
                }
            }
        } finally {
            try { codec?.stop() } catch (e: Exception) { }
            try { codec?.release() } catch (e: Exception) { }
            try { extractor?.release() } catch (e: Exception) { }
        }
    }

    private fun selectAudioTrack(extractor: MediaExtractor): Int? {
        for (i in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)
            if (mime != null && mime.startsWith("audio/")) return i
        }
        return null
    }

    private class FloatSink(initialCapacity: Int) {
        private var data = FloatArray(max(initialCapacity, 1024))
        var size = 0
            private set

        fun add(v: Float) {
            if (size == data.size) data = data.copyOf(size + size / 2 + 1024)
            data[size++] = v
        }

        fun addAll(src: FloatArray, n: Int) {
            if (size + n > data.size) data = data.copyOf(max(size + n, size + size / 2) + 1024)
            System.arraycopy(src, 0, data, size, n)
            size += n
        }

        fun toArray(): FloatArray = if (size == data.size) data else data.copyOf(size)
    }

    /**
     * Receptor del PCM remuestreado: guarda los primeros [headCap] samples y, en un anillo, los últimos
     * [tailCap]. Memoria acotada (presupuesto de audio), sin importar cuánto dure el archivo.
     */
    private class HeadTailSink(private val headCap: Int, private val tailCap: Int, headExpected: Int) {
        private val head = FloatSink(max(min(headExpected, headCap), 1024))
        private val ring = FloatArray(tailCap)
        private var ringPos = 0
        private var ringCount = 0

        /** Samples recibidos en total (cabeza + medio + cola). */
        var total = 0L
            private set

        val headFull: Boolean get() = head.size >= headCap

        fun add(v: Float) {
            total++
            if (head.size < headCap) {
                head.add(v)
            } else if (tailCap > 0) {
                ring[ringPos] = v
                ringPos = if (ringPos + 1 == tailCap) 0 else ringPos + 1
                if (ringCount < tailCap) ringCount++
            }
        }

        fun addAll(src: FloatArray, n: Int) {
            for (i in 0 until n) add(src[i])
        }

        fun headArray(): FloatArray = head.toArray()

        /** Descarta lo acumulado en el anillo (al saltar o abrir un hueco, esas muestras no son de la cola). */
        fun resetTail() {
            ringPos = 0
            ringCount = 0
        }

        /** Avanza el reloj (en samples de salida) sin guardar nada: el hueco que no se procesó. */
        fun jumpTo(totalSamples: Long) {
            if (totalSamples > total) total = totalSamples
        }

        /** Los últimos samples guardados, en orden temporal. */
        fun tailArray(): FloatArray {
            val out = FloatArray(ringCount)
            val start = if (ringCount < tailCap) 0 else ringPos
            for (i in 0 until ringCount) out[i] = ring[(start + i) % tailCap]
            return out
        }
    }

    /**
     * Remuestreador en streaming de banda limitada (sinc con ventana Hann; el corte baja al de Nyquist
     * de la salida cuando se reduce la frecuencia, para que no entre aliasing al análisis).
     */
    private class StreamingResampler(inRate: Int, outRate: Int) {
        private companion object {
            const val HALF_WIDTH = 12          // cruces por cero a cada lado del kernel
            const val TABLE_RES = 64           // muestras de tabla por cruce por cero
            val KERNEL = FloatArray(HALF_WIDTH * TABLE_RES + 2).also { t ->
                for (i in t.indices) {
                    val u = i.toDouble() / TABLE_RES
                    val sinc = if (u == 0.0) 1.0 else sin(PI * u) / (PI * u)
                    val window = if (u >= HALF_WIDTH) 0.0 else 0.5 * (1.0 + cos(PI * u / HALF_WIDTH))
                    t[i] = (sinc * window).toFloat()
                }
            }

            fun kernel(u: Double): Double {
                val a = abs(u) * TABLE_RES
                val i = a.toInt()
                if (i >= HALF_WIDTH * TABLE_RES) return 0.0
                val frac = a - i
                return (KERNEL[i] + (KERNEL[i + 1] - KERNEL[i]) * frac).toDouble()
            }
        }

        private val step = inRate.toDouble() / outRate            // muestras de entrada por muestra de salida
        private val scale = min(1.0, outRate.toDouble() / inRate) // <1 al reducir la frecuencia
        private val radius = HALF_WIDTH / scale                   // radio del kernel en muestras de entrada

        private var buf = FloatArray(1 shl 16)
        private var bufStart = 0L // índice absoluto de buf[0]
        private var bufLen = 0
        private var total = 0L    // muestras de entrada recibidas
        private var nextOut = 0L

        fun push(src: FloatArray, n: Int, sink: HeadTailSink) {
            if (bufLen + n > buf.size) buf = buf.copyOf(max(bufLen + n, buf.size * 2))
            System.arraycopy(src, 0, buf, bufLen, n)
            bufLen += n
            total += n
            produce(sink, isFinal = false)
        }

        fun finish(sink: HeadTailSink) = produce(sink, isFinal = true)

        private fun produce(sink: HeadTailSink, isFinal: Boolean) {
            while (true) {
                val x = nextOut * step
                val hi = floor(x + radius).toLong()
                if (isFinal) {
                    if (x > total - 1) break
                } else if (hi >= total) {
                    break
                }
                var k = max(ceil(x - radius).toLong(), 0L)
                val kMax = min(hi, total - 1)
                var acc = 0.0
                while (k <= kMax) {
                    acc += buf[(k - bufStart).toInt()] * kernel((k - x) * scale)
                    k++
                }
                sink.add((acc * scale).toFloat())
                nextOut++
            }
            // Descarta lo que ya no necesita ninguna salida futura.
            val keepFrom = max(0L, ceil(nextOut * step - radius).toLong())
            var drop = (keepFrom - bufStart).toInt()
            if (drop > 0) {
                drop = min(drop, bufLen)
                System.arraycopy(buf, drop, buf, 0, bufLen - drop)
                bufLen -= drop
                bufStart += drop
            }
        }
    }
}

/**
 * AUTOMIX: botón "Analizar biblioteca completa". Vive a nivel de proceso (no de pantalla), así
 * que sigue si el usuario sale de Ajustes y la pantalla puede volver a leer su progreso.
 * No bloquea la reproducción: corre en IO con prioridad de fondo y el análisis es de a una canción.
 */
object AutomixLibraryAnalysis {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress

    fun start(context: Context, songs: List<Song>, repo: AutomixCacheRepository) {
        if (_running.value) return
        val appContext = context.applicationContext
        _running.value = true
        _progress.value = 0f
        job = scope.launch {
            try {
                val todo = songs.map { it.path }.filter { it.isNotEmpty() && !repo.has(it) }
                var okCount = 0
                var failCount = 0
                AutomixStatus.set("Biblioteca: ${todo.size} canciones por analizar")
                todo.forEachIndexed { i, path ->
                    val data = AutomixAnalyzer.analyze(path, appContext)
                    ensureActive() // si se canceló, no marcar la canción como fallida
                    if (data != null) {
                        okCount++
                        repo.set(path, data)
                    } else if (AutomixModelManager.isReady(appContext)) {
                        failCount++
                        repo.set(path, AutomixData.UNUSABLE)
                    } else {
                        return@launch // sin modelo (sin red): no tiene sentido seguir
                    }
                    _progress.value = (i + 1).toFloat() / todo.size
                }
                AutomixStatus.set("Biblioteca terminada: $okCount analizadas, $failCount sin datos útiles")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.w("AutomixLibrary", "El análisis de la biblioteca se detuvo", e)
            } finally {
                _running.value = false
            }
        }
    }

    fun cancel() {
        job?.cancel()
    }
}
