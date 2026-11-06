package com.music.musicflame.audio

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.Log
import com.music.musicflame.data.AutomixCacheRepository
import com.music.musicflame.data.AutomixData
import com.music.musicflame.data.Song
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

    /** Tope de audio que se decodifica. Más allá, el final se extrapola con el tempo medido. */
    private const val MAX_ANALYSIS_SECONDS = 240

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
                val result = mutex.withLock {
                    failReason = ""
                    AutomixStatus.set("Analizando: $name…")
                    analyzeBlocking(filePath, modelFile, cancel)
                }
                AutomixStatus.set(
                    if (result != null) {
                        "Listo: $name · ${"%.1f".format(result.bpm)} BPM (confianza ${(result.confidence * 100).toInt()} %)"
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

    private fun analyzeBlocking(filePath: String, modelFile: File, cancel: () -> Unit): AutomixData? {
        val decoded = decodeMono22050(filePath.removePrefix("file://"), cancel) ?: run {
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

        val analysis = LocalSongAnalyzer(be).analyze(decoded.pcm, cancel)
        return toAutomixData(analysis, decoded)
    }

    private fun toAutomixData(analysis: LocalSongAnalysis, decoded: Decoded): AutomixData? {
        if (!analysis.pulse.quality.safeForAutomaticMix) {
            Log.w(TAG, "Rejilla de beats no apta para mezclar automáticamente")
            failReason = "rejilla de beats no apta para mezclar"
            return null
        }
        var confidence = analysis.audio.tempoConfidence.coerceIn(0.0, 1.0)
        val beatTimes = DoubleArray(analysis.pulse.beats.size) { analysis.pulse.beats[it].seconds }
        val fit = fitGrid(beatTimes) ?: run {
            failReason = "se detectaron muy pocos beats"
            return null
        }
        val bpm = 60.0 / fit.periodSec
        if (bpm !in MIN_BPM..MAX_BPM) {
            Log.w(TAG, "BPM fuera de rango (${"%.1f".format(bpm)})")
            failReason = "BPM fuera de rango (${"%.1f".format(bpm)})"
            return null
        }
        val regular = fit.maxResidualSec <= MAX_GRID_RESIDUAL_SEC
        if (!regular) confidence *= IRREGULAR_GRID_CONFIDENCE_FACTOR
        if (confidence < MIN_CONFIDENCE) {
            failReason = "confianza baja (${"%.2f".format(confidence)})"
            return null
        }

        // Fase: un beat de la rejilla normalizado a [0, periodo).
        val phaseSec = ((fit.anchorSec % fit.periodSec) + fit.periodSec) % fit.periodSec

        // Último beat de la canción: el observado si se analizó entera y el tempo es regular o no es
        // extrapolable; si no, la rejilla ajustada extendida hasta el final del archivo.
        val totalSec = decoded.durationMs / 1000.0
        val gridLast = fit.anchorSec + fit.periodSec * floor((totalSec - fit.anchorSec) / fit.periodSec)
        val lastBeatSec = if (decoded.coversWholeFile && !regular) beatTimes.last() else gridLast

        val key = KEY_NAMES.indexOf(analysis.audio.keyEstimate.trim().substringBefore(' '))
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
     * Ajusta t = ancla + periodo * k por mínimos cuadrados, asignando a cada beat su índice k según
     * el intervalo mediano (así tolera beats faltantes). Da un BPM mucho más fino que 60 / mediana.
     */
    private fun fitGrid(times: DoubleArray): GridFit? {
        if (times.size < 8) return null
        val diffs = DoubleArray(times.size - 1) { times[it + 1] - times[it] }.also { it.sort() }
        val p0 = diffs[diffs.size / 2]
        if (p0 <= 0.0) return null
        val n = times.size.toDouble()
        var sumK = 0.0
        var sumT = 0.0
        var sumKK = 0.0
        var sumKT = 0.0
        val ks = DoubleArray(times.size)
        for (i in times.indices) {
            val k = round((times[i] - times[0]) / p0)
            ks[i] = k
            sumK += k
            sumT += times[i]
            sumKK += k * k
            sumKT += k * times[i]
        }
        val denom = n * sumKK - sumK * sumK
        if (denom <= 0.0) return null
        val period = (n * sumKT - sumK * sumT) / denom
        if (period <= 0.0) return null
        val anchor = (sumT - period * sumK) / n
        var maxRes = 0.0
        for (i in times.indices) maxRes = max(maxRes, abs(times[i] - (anchor + period * ks[i])))
        return GridFit(period, anchor, maxRes)
    }

    // ---------------------------------------------------------------------------------------
    // Decodificación: MediaCodec -> mono -> 22050 Hz
    // ---------------------------------------------------------------------------------------

    private class Decoded(val pcm: FloatArray, val coversWholeFile: Boolean, val durationMs: Long)

    private fun decodeMono22050(filePath: String, cancel: () -> Unit): Decoded? {
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

            val maxOutSamples = TARGET_RATE * MAX_ANALYSIS_SECONDS
            val expected = if (declaredDurationUs > 0) {
                min(declaredDurationUs / 1_000_000.0 * TARGET_RATE, maxOutSamples.toDouble()).toInt()
            } else {
                TARGET_RATE * 60
            }
            val sink = FloatSink(expected + 4096)
            var mono = FloatArray(8192)

            val maxInputUs = MAX_ANALYSIS_SECONDS * 1_000_000L
            var sawInputEos = false
            var sawOutputEos = false
            var coversWhole = false
            val info = MediaCodec.BufferInfo()

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
                        codec.releaseOutputBuffer(outIdx, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) sawOutputEos = true
                        if (sink.size >= maxOutSamples) break // tope de memoria alcanzado
                    }
                }
            }
            resampler?.finish(sink)

            if (sink.size < TARGET_RATE * MIN_ANALYSIS_SECONDS) return null
            val pcmDurationMs = sink.size * 1000L / TARGET_RATE
            val durationMs = if (declaredDurationUs > 0) declaredDurationUs / 1000L else pcmDurationMs
            return Decoded(sink.toArray(), coversWhole && sink.size < maxOutSamples, durationMs)
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

        fun push(src: FloatArray, n: Int, sink: FloatSink) {
            if (bufLen + n > buf.size) buf = buf.copyOf(max(bufLen + n, buf.size * 2))
            System.arraycopy(src, 0, buf, bufLen, n)
            bufLen += n
            total += n
            produce(sink, isFinal = false)
        }

        fun finish(sink: FloatSink) = produce(sink, isFinal = true)

        private fun produce(sink: FloatSink, isFinal: Boolean) {
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
