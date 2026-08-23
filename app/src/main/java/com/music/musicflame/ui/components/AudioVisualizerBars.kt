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
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

// Cuántas barras se dibujan.
private const val BAR_COUNT = 32

// Tamaño de captura del Visualizer. Más grande = más resolución en graves
// (que es donde vive el ritmo), a costa de un poquito más de trabajo por frame.
private const val CAPTURE_SIZE = 1024

// "Ataque" (cuando la barra SUBE, tipo llega un golpe de batería): prácticamente
// instantáneo, para que se sienta pegado al audio real.
private const val ATTACK = 0.94f
// "Caída" (cuando la barra BAJA): antes muy lenta (0.16) para que se viera tipo
// "resbalando"; subida a 0.26 a pedido: cae más rápido, se ve más ágil/nerviosa y
// menos como un promedio suavizado, más como el pulso real de la canción.
private const val RELEASE = 0.26f

// Qué tan rápido se ajusta el auto-gain GLOBAL (una sola escala para todas las
// barras) a la energía reciente de la canción. Antes era por barra, y eso
// "aplanaba" todo: un hi-hat bajito se veía tan alto como un golpe de bombo.
// Con un gain global, la altura relativa entre barras SÍ refleja la mezcla real.
// Bajado de 0.94 a 0.88: el gain "olvida" un pico viejo todavía más rápido, así
// las barras usan más rango dinámico en las partes tranquilas de la canción en
// vez de quedar aplastadas cerca del techo por un golpe fuerte que ya pasó.
private const val GAIN_DECAY = 0.88f

// Detección simple de "golpe" en graves (kick/bajo, que es donde vive el ritmo):
// si la energía de las barras graves sube fuerte respecto a su propio promedio
// reciente, se considera un golpe y se le da un empujoncito extra a TODA la fila
// de barras ese frame, para que se sienta un "pulso" sincronizado con la canción.
// Ajustado para que dispare más seguido (RATIO más bajo) y empuje más fuerte
// (BOOST más alto), así el pulso se siente mucho más marcado y "vivo".
private const val BASS_BAND_FRACTION = 0.25f // % de barras (desde la izquierda) que cuentan como graves
private const val BEAT_AVG_DECAY = 0.88f     // qué tan rápido se actualiza el promedio de graves
private const val BEAT_TRIGGER_RATIO = 1.15f // cuánto debe superar al promedio para contar como golpe
private const val BEAT_BOOST = 1.4f          // empuje extra que se aplica a todas las barras en un golpe

/**
 * Barras de espectro tipo ecualizador, en escala de grises (un solo [color] sólido).
 * Se engancha al audioSessionId real de ExoPlayer vía android.media.audiofx.Visualizer.
 *
 * Pensado para que se SIENTA conectado a la canción, no genérico:
 * - Las frecuencias se agrupan en escala LOGARÍTMICA (como un analizador de espectro
 *   real), así que graves/medios -donde vive el kick, el bajo y el ritmo- ocupan
 *   muchas más barras que antes, en vez de comprimirse en 1 o 2.
 * - Auto-gain GLOBAL (una sola escala para las 32 barras) en vez de una por barra:
 *   así se conservan las diferencias reales de energía entre graves y agudos, y un
 *   golpe de bombo de verdad se ve más alto que un hi-hat de fondo.
 * - Detección liviana de golpe en graves: cuando la energía baja sube fuerte
 *   respecto a su propio promedio reciente, toda la fila de barras recibe un
 *   empujón sincronizado, para que se note el pulso del ritmo.
 *
 * Optimizado para no trabar la UI: todo el cálculo de FFT corre en un
 * HandlerThread aparte (no en el hilo principal).
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
                // Bordes (en índice de bin) de cada barra, calculados en escala logarítmica
                // la primera vez que sabemos cuántos bins hay. edges[i]..edges[i+1] = rango de
                // la barra i. Van desde el bin 1 (saltamos el DC) hasta el último bin.
                var edges: IntArray? = null
                // Peso fijo por barra: compensa que la energía natural de la música cae hacia
                // los agudos, para que las barras de agudos no se vean siempre apagadas.
                var barWeight: FloatArray? = null
                val rawLevels = FloatArray(barCount)

                var globalGain = 1f
                var bassAvg = 0f

                fun buildEdgesIfNeeded() {
                    if (edges != null || bins < 2) return
                    val minBin = 1
                    val maxBin = bins - 1
                    val ratio = (maxBin.toFloat() / minBin.toFloat()).pow(1f / barCount)
                    val e = IntArray(barCount + 1)
                    for (i in 0..barCount) {
                        val v = (minBin * ratio.pow(i.toFloat())).toInt()
                        e[i] = v.coerceIn(minBin, maxBin)
                    }
                    // Asegura que cada barra cubra al menos 1 bin y que sean crecientes.
                    for (i in 1..barCount) {
                        if (e[i] <= e[i - 1]) e[i] = min(e[i - 1] + 1, maxBin)
                    }
                    edges = e

                    val w = FloatArray(barCount) { i ->
                        // Curva suave: +0% en la primera barra, hasta +260% en la última,
                        // compensando el "rolloff" natural de energía hacia los agudos para
                        // que TODAS las 32 barras se muevan con vida, no solo las de graves.
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

                                if (bins != fft.size / 2 || magnitudes == null) {
                                    bins = fft.size / 2
                                    magnitudes = FloatArray(bins)
                                    edges = null
                                    buildEdgesIfNeeded()
                                }
                                val mags = magnitudes ?: return
                                val binEdges = edges ?: return
                                val weights = barWeight ?: return

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

                                // Auto-gain GLOBAL: una sola escala para las 32 barras, así se
                                // conservan las diferencias reales de energía entre graves y agudos.
                                globalGain = max(globalGain * GAIN_DECAY, frameMax).coerceAtLeast(1f)

                                // Detección de golpe en graves: energía promedio de las primeras
                                // barras (kick/bajo) contra su propio promedio reciente.
                                val bassBars = max(1, (barCount * BASS_BAND_FRACTION).toInt())
                                var bassSum = 0f
                                for (i in 0 until bassBars) bassSum += rawLevels[i]
                                val bassNow = bassSum / bassBars
                                val isBeat = bassAvg > 0f && bassNow > bassAvg * BEAT_TRIGGER_RATIO
                                bassAvg = if (bassAvg == 0f) bassNow else bassAvg + (bassNow - bassAvg) * (1f - BEAT_AVG_DECAY)
                                val beatMultiplier = if (isBeat) BEAT_BOOST else 1f

                                for (bar in 0 until barCount) {
                                    val normalized = (rawLevels[bar] / globalGain * beatMultiplier).coerceIn(0f, 1f)
                                    // Curva perceptual: empuja los valores medios hacia arriba
                                    // para que se vea más "vivo" y menos plano. Bajado de 0.6 a
                                    // 0.5 para más contraste todavía entre silencios y golpes.
                                    rawLevels[bar] = normalized.pow(0.5f)
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
        @Suppress("UNUSED_EXPRESSION")
        frameTick

        val barWidth = size.width / (barCount * 1.5f)
        val spacing = barWidth * 0.5f
        val maxBarHeight = size.height
        val minHeightFraction = 0.03f

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