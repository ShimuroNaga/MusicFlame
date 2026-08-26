package com.music.musicflame.ui.components

import android.media.audiofx.Visualizer
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

// --- MOTOR COMPARTIDO DE ESPECTRO DE AUDIO ---
// Este archivo es el resultado de sacar TODA la lógica de captura/FFT que antes
// vivía adentro de AudioVisualizerBars.kt y dejarla en un solo lugar reusable.
// Antes, si querías un estilo de ecualizador distinto (espejado, ondas, círculo,
// partículas, etc.) tenías que copiar/pegar 200 líneas de Visualizer + threading
// + auto-gain + detección de golpe por banda. Ahora esa lógica vive UNA sola vez
// acá, y cada estilo (ver EqualizerStyleRenderers.kt) solo se encarga de DIBUJAR
// a partir de los mismos niveles ya calculados — el color y el estilo son
// ortogonales al análisis de audio en sí.
//
// El comportamiento/tuning (auto-gain global, golpe por banda, suavizado a 60fps,
// rango de frecuencia recortado a 14kHz, etc.) es EXACTAMENTE el mismo que tenía
// AudioVisualizerBars antes de este refactor — no se tocó ningún número.

private const val CAPTURE_SIZE = 1024
private const val TARGET_MAX_FREQUENCY_HZ = 14000f

private const val ATTACK_PER_SEC = 26f
private const val RELEASE_PER_SEC = 9f

private const val GAIN_DECAY = 0.82f

private const val BEAT_BAND_COUNT = 4
private const val BEAT_AVG_DECAY = 0.88f
private const val BEAT_TRIGGER_RATIO = 1.22f
private const val BEAT_BOOST = 1.7f

/**
 * Estado compartido por cualquier estilo de ecualizador: un FloatArray de niveles
 * ya suavizados (0f..1f, uno por barra/banda) y un [tick] que los estilos leen
 * DENTRO de su Canvas (DrawScope) para que Compose redibuje en cada frame sin
 * necesitar recomposición completa del árbol — el mismo truco que ya usaba
 * AudioVisualizerBars original con su `frameTick`.
 *
 * Se usa tanto para el ecualizador REAL (enganchado al audioSessionId, ver
 * [rememberAudioSpectrum]) como para la vista previa animada y falsa que se
 * usa en el selector de estilo de Ajustes (ver [rememberFakeEqualizerLevels]
 * en EqualizerStyle.kt) — así ambos casos dibujan con el mismo código.
 */
class EqualizerLevelsState(val barCount: Int) {
    val displayLevels = FloatArray(barCount)
    var tick by mutableIntStateOf(0)
        internal set
}

/**
 * Engancha un [EqualizerLevelsState] al audioSessionId real de ExoPlayer vía
 * android.media.audiofx.Visualizer. Ver AudioVisualizerBars.kt (ahora un simple
 * wrapper de esto) para el detalle de cómo se usaba antes de este refactor.
 */
@Composable
fun rememberAudioSpectrum(
    audioSessionId: Int,
    isPlaying: Boolean,
    hasRecordAudioPermission: Boolean,
    barCount: Int
): EqualizerLevelsState {
    val state = remember(barCount) { EqualizerLevelsState(barCount) }
    val targetLevels = remember(barCount) { FloatArray(barCount) }

    DisposableEffect(audioSessionId, isPlaying, hasRecordAudioPermission, barCount) {
        var visualizer: Visualizer? = null
        var handlerThread: HandlerThread? = null
        val mainHandler = Handler(Looper.getMainLooper())

        if (hasRecordAudioPermission && isPlaying && audioSessionId != 0) {
            try {
                handlerThread = HandlerThread("AudioVisualizerThread").apply { start() }
                val bgHandler = Handler(handlerThread.looper)

                var bins = 0
                var magnitudes: FloatArray? = null
                var edges: IntArray? = null
                var barWeight: FloatArray? = null
                val rawLevels = FloatArray(barCount)

                var globalGain = 1f
                val bandAvg = FloatArray(BEAT_BAND_COUNT)
                var sampleRateHz = 0

                fun buildEdgesIfNeeded() {
                    if (edges != null || bins < 2 || sampleRateHz <= 0) return
                    val minBin = 1
                    val nyquistBin = bins - 1
                    val binsPerHz = bins.toFloat() / (sampleRateHz / 2f)
                    val maxBin = (TARGET_MAX_FREQUENCY_HZ * binsPerHz).toInt().coerceIn(minBin + 1, nyquistBin)
                    val ratio = (maxBin.toFloat() / minBin.toFloat()).pow(1f / barCount)
                    val e = IntArray(barCount + 1)
                    for (i in 0..barCount) {
                        val v = (minBin * ratio.pow(i.toFloat())).toInt()
                        e[i] = v.coerceIn(minBin, maxBin)
                    }
                    for (i in 1..barCount) {
                        if (e[i] <= e[i - 1]) e[i] = min(e[i - 1] + 1, maxBin)
                    }
                    edges = e

                    val w = FloatArray(barCount) { i ->
                        1f + (i.toFloat() / (barCount - 1).coerceAtLeast(1)) * 2.6f
                    }
                    barWeight = w
                }

                visualizer = Visualizer(audioSessionId).apply {
                    val maxCapture = Visualizer.getCaptureSizeRange()[1]
                    captureSize = minOf(CAPTURE_SIZE, maxCapture)

                    setDataCaptureListener(
                        object : Visualizer.OnDataCaptureListener {
                            override fun onWaveFormDataCapture(
                                v: Visualizer?,
                                waveform: ByteArray?,
                                samplingRate: Int
                            ) {
                                // No usamos la onda, solo FFT.
                            }

                            override fun onFftDataCapture(
                                v: Visualizer?,
                                fft: ByteArray?,
                                samplingRate: Int
                            ) {
                                if (fft == null || fft.size < 4) return

                                val newSampleRateHz = samplingRate / 1000
                                if (bins != fft.size / 2 || magnitudes == null || sampleRateHz != newSampleRateHz) {
                                    bins = fft.size / 2
                                    magnitudes = FloatArray(bins)
                                    sampleRateHz = newSampleRateHz
                                    edges = null
                                    buildEdgesIfNeeded()
                                }
                                val mags = magnitudes ?: return
                                val binEdges = edges ?: return
                                val weights = barWeight ?: return

                                mags[0] = kotlin.math.abs(fft[0].toFloat())
                                for (k in 1 until bins) {
                                    val re = fft[2 * k].toFloat()
                                    val im = if (2 * k + 1 < fft.size) fft[2 * k + 1].toFloat() else 0f
                                    mags[k] = sqrt(re * re + im * im)
                                }

                                var frameMax = 1f
                                for (bar in 0 until barCount) {
                                    val start = binEdges[bar]
                                    val end = binEdges[bar + 1]
                                    if (start >= end) {
                                        rawLevels[bar] = 0f
                                        continue
                                    }
                                    var sum = 0f
                                    for (i in start until end) sum += mags[i]
                                    val avg = (sum / (end - start)) * weights[bar]
                                    rawLevels[bar] = avg
                                    if (avg > frameMax) frameMax = avg
                                }

                                globalGain = max(globalGain * GAIN_DECAY, frameMax).coerceAtLeast(1f)

                                val barsPerBand = max(1, barCount / BEAT_BAND_COUNT)
                                val beatMultiplier = FloatArray(barCount) { 1f }
                                for (band in 0 until BEAT_BAND_COUNT) {
                                    val bandStart = band * barsPerBand
                                    val bandEnd = if (band == BEAT_BAND_COUNT - 1) barCount else min(bandStart + barsPerBand, barCount)
                                    if (bandStart >= bandEnd) continue
                                    var bandSum = 0f
                                    for (i in bandStart until bandEnd) bandSum += rawLevels[i]
                                    val bandNow = bandSum / (bandEnd - bandStart)
                                    val avg = bandAvg[band]
                                    val isBandBeat = avg > 0f && bandNow > avg * BEAT_TRIGGER_RATIO
                                    bandAvg[band] = if (avg == 0f) bandNow else avg + (bandNow - avg) * (1f - BEAT_AVG_DECAY)
                                    if (isBandBeat) {
                                        for (i in bandStart until bandEnd) beatMultiplier[i] = BEAT_BOOST
                                    }
                                }

                                for (bar in 0 until barCount) {
                                    val normalized = (rawLevels[bar] / globalGain * beatMultiplier[bar]).coerceIn(0f, 1f)
                                    rawLevels[bar] = normalized.pow(0.42f)
                                }

                                val target = rawLevels.copyOf()
                                mainHandler.post {
                                    for (i in 0 until barCount) targetLevels[i] = target[i]
                                }
                            }
                        },
                        Visualizer.getMaxCaptureRate() / 2,
                        /* waveform = */ false,
                        /* fft = */ true
                    )
                    enabled = true
                }
            } catch (e: Exception) {
                e.printStackTrace()
                visualizer = null
            }
        }

        onDispose {
            visualizer?.let {
                try {
                    it.enabled = false
                    it.release()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            handlerThread?.quitSafely()
            for (i in targetLevels.indices) targetLevels[i] = 0f
            for (i in state.displayLevels.indices) state.displayLevels[i] = 0f
            state.tick++
        }
    }

    LaunchedEffect(barCount) {
        var lastFrameNanos = 0L
        while (true) {
            withFrameNanos { frameNanos ->
                val dt = if (lastFrameNanos == 0L) 0f else (frameNanos - lastFrameNanos) / 1_000_000_000f
                lastFrameNanos = frameNanos
                for (i in 0 until barCount) {
                    val current = state.displayLevels[i]
                    val goal = targetLevels[i]
                    val rate = if (goal > current) ATTACK_PER_SEC else RELEASE_PER_SEC
                    val alpha = 1f - exp(-rate * dt)
                    state.displayLevels[i] = current + (goal - current) * alpha
                }
            }
            state.tick++
        }
    }

    return state
}
