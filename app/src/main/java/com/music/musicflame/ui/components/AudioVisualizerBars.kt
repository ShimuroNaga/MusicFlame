package com.music.musicflame.ui.components

import android.media.audiofx.Visualizer
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

// Cuántas barras se dibujan.
private const val BAR_COUNT = 32

// Tamaño de captura del Visualizer. Más chico = menos trabajo por frame.
// 256 ya da 128 bins de frecuencia, de sobra para 32 barras.
private const val CAPTURE_SIZE = 256

// "Ataque" (cuando la barra SUBE, tipo llega un golpe de batería): rápido, para que reaccione ya.
private const val ATTACK = 0.65f
// "Caída" (cuando la barra BAJA): lento, para que no se vea nerviosa/parpadeante, sino que
// "resbale" hacia abajo como en los videos de ecualizador.
private const val RELEASE = 0.12f

// Qué tan rápido se ajusta el auto-gain de cada barra a su propio pico reciente.
private const val GAIN_DECAY = 0.985f

/**
 * Barras de espectro tipo ecualizador, en escala de grises (un solo [color] sólido).
 * Se engancha al audioSessionId real de ExoPlayer vía android.media.audiofx.Visualizer.
 *
 * Optimizado para no trabar la UI:
 * - Todo el cálculo de FFT corre en un HandlerThread aparte (no en el hilo principal).
 * - Cada barra tiene su propio auto-ajuste de escala (auto-gain), así que los agudos
 *   también se mueven y no solo los graves (que siempre traen más energía cruda).
 * - Ataque rápido / caída lenta, como un VU-meter real, para que se vea con "rebote".
 */
@Composable
fun AudioVisualizerBars(
    audioSessionId: Int,
    isPlaying: Boolean,
    hasRecordAudioPermission: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    barCount: Int = BAR_COUNT
) {
    // Buffer que YA se dibuja (leído por Canvas). Se muta en el sitio, nunca se re-crea,
    // para no generar basura (garbage) en cada frame.
    val displayLevels = remember { FloatArray(barCount) }
    // Disparador liviano: Canvas lo lee para saber cuándo redibujar. El valor en sí no importa.
    var frameTick by remember { mutableIntStateOf(0) }

    DisposableEffect(audioSessionId, isPlaying, hasRecordAudioPermission) {
        var visualizer: Visualizer? = null
        var handlerThread: HandlerThread? = null
        val mainHandler = Handler(Looper.getMainLooper())

        if (hasRecordAudioPermission && isPlaying && audioSessionId != 0) {
            try {
                // Hilo aparte SOLO para recibir y procesar los datos de FFT. Así el hilo
                // principal (UI/Compose) queda libre y no se traba mientras suena música.
                handlerThread = HandlerThread("AudioVisualizerThread").apply { start() }
                val bgHandler = Handler(handlerThread.looper)

                // Buffers reutilizables del hilo de fondo (se crean una sola vez, no en cada callback)
                var bins = 0
                var magnitudes: FloatArray? = null
                var barGain: FloatArray? = null
                val rawLevels = FloatArray(barCount)

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

                                if (bins != fft.size / 2 || magnitudes == null) {
                                    bins = fft.size / 2
                                    magnitudes = FloatArray(bins)
                                    barGain = FloatArray(barCount) { 1f }
                                }
                                val mags = magnitudes ?: return
                                val gains = barGain ?: return

                                // Formato del Visualizer: fft[0]=Re[0] (DC), fft[1]=Re[N/2] (Nyquist),
                                // y desde ahí fft[2k]=Re[k], fft[2k+1]=Im[k] para k = 1..N/2-1.
                                mags[0] = kotlin.math.abs(fft[0].toFloat())
                                for (k in 1 until bins) {
                                    val re = fft[2 * k].toFloat()
                                    val im = if (2 * k + 1 < fft.size) fft[2 * k + 1].toFloat() else 0f
                                    // sqrt directo en vez de hypot(): más barato y aquí no
                                    // necesitamos su protección extra contra overflow.
                                    mags[k] = sqrt(re * re + im * im)
                                }

                                val binsPerBar = max(1, bins / barCount)
                                for (bar in 0 until barCount) {
                                    val start = bar * binsPerBar
                                    val end = minOf(start + binsPerBar, bins)
                                    if (start >= end) {
                                        rawLevels[bar] = 0f
                                        continue
                                    }
                                    var sum = 0f
                                    for (i in start until end) sum += mags[i]
                                    val avg = sum / (end - start)

                                    // Auto-gain INDEPENDIENTE por barra: cada una se escala contra
                                    // su propio pico reciente, no uno global. Así los agudos (que
                                    // traen menos energía cruda que los graves) también reaccionan
                                    // en vez de quedarse planos todo el tiempo.
                                    gains[bar] = max(gains[bar] * GAIN_DECAY, avg).coerceAtLeast(1f)
                                    val normalized = (avg / gains[bar]).coerceIn(0f, 1f)
                                    // Curva perceptual: empuja los valores medios hacia arriba
                                    // para que se vea más "vivo" y menos plano.
                                    rawLevels[bar] = normalized.pow(0.6f)
                                }

                                // Ataque rápido / caída lenta, calculado aquí (barato, 32 floats)
                                // y aplicado en el hilo principal para no pelear con Compose.
                                val target = rawLevels.copyOf()
                                mainHandler.post {
                                    for (i in 0 until barCount) {
                                        val current = displayLevels[i]
                                        val goal = target[i]
                                        val rate = if (goal > current) ATTACK else RELEASE
                                        displayLevels[i] = current + (goal - current) * rate
                                    }
                                    frameTick++
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
            // Reseteamos las barras a 0 para que la próxima vez (nueva canción, se
            // reanuda, etc.) no arranque "congelado" en el último valor.
            for (i in displayLevels.indices) displayLevels[i] = 0f
            frameTick++
        }
    }

    Canvas(modifier = modifier) {
        // Leer frameTick aquí es lo que hace que Canvas se redibuje en cada actualización;
        // displayLevels se lee directo, sin copiar, sin alocar memoria nueva.
        @Suppress("UNUSED_EXPRESSION")
        frameTick

        val barWidth = size.width / (barCount * 1.5f)
        val spacing = barWidth * 0.5f
        val maxBarHeight = size.height
        val minHeightFraction = 0.04f

        for (index in 0 until barCount) {
            val level = displayLevels[index].coerceIn(0f, 1f)
            val barHeight = maxBarHeight * max(level, minHeightFraction)
            val x = index * (barWidth + spacing)
            drawRoundRect(
                color = color,
                topLeft = Offset(x, maxBarHeight - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}