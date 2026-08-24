package com.music.musicflame.ui.components

import android.media.audiofx.Visualizer
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

// Cuántas barras se dibujan.
private const val BAR_COUNT = 32

// Tamaño de captura del Visualizer. Más grande = más resolución en graves
// (que es donde vive el ritmo), a costa de un poquito más de trabajo por frame.
private const val CAPTURE_SIZE = 1024

// Techo real de frecuencia que se reparte entre las barras. Antes se usaba el
// Nyquist completo (~20-22kHz con audio a 44.1/48kHz), pero casi ninguna canción
// tiene energía real por encima de ~14kHz (el "brillo"/aire de platillos vive
// como mucho ahí) — por eso las últimas 3-5 barras de la derecha casi siempre
// quedaban vacías, aunque el resto sí reaccionara: estaban mirando una zona sin
// señal real, no que no funcionaran. Al recortar el rango hasta acá, TODAS las
// barras -incluidas las últimas- caen en zonas con contenido musical real.
private const val TARGET_MAX_FREQUENCY_HZ = 14000f

// --- ANIMACIÓN A 60FPS, DESACOPLADA DEL MUESTREO DE AUDIO ---
// El Visualizer entrega FFT a una tasa baja (Visualizer.getMaxCaptureRate()/2,
// típicamente ~10-20 veces por segundo). Antes las barras SOLO se movían cuando
// llegaba un dato nuevo, así que aunque el audio fuera real, el movimiento se veía
// "a saltos" (rígido). Ahora el FFT solo marca la META (targetLevels) y un loop de
// animación aparte (withFrameNanos, a la velocidad real de la pantalla) interpola
// hacia esa meta todo el tiempo, con una tasa "por segundo" (no por callback), así
// la velocidad de la animación no depende de cuántos fps tenga el celular.
// ATTACK/RELEASE_PER_SEC son tasas de suavizado exponencial: más alto = llega más
// rápido a la meta. El ataque es MUY rápido (pegado al golpe real); la caída es
// bastante más lenta pero ya no arrastra tanto como para verse plana.
private const val ATTACK_PER_SEC = 26f
private const val RELEASE_PER_SEC = 9f

// Qué tan rápido se ajusta el auto-gain GLOBAL (una sola escala para todas las
// barras) a la energía reciente de la canción. Antes era por barra, y eso
// "aplanaba" todo: un hi-hat bajito se veía tan alto como un golpe de bombo.
// Con un gain global, la altura relativa entre barras SÍ refleja la mezcla real.
// Bajado más (0.88 -> 0.82): el gain "olvida" un pico viejo todavía más rápido, así
// las barras usan más rango dinámico en las partes tranquilas de la canción en vez
// de quedar aplastadas cerca del techo por un golpe fuerte que ya pasó, y en temas
// con audio sostenido y fuerte no se quedan "pegadas" arriba sin margen para picar.
private const val GAIN_DECAY = 0.82f

// Detección de "golpe" POR BANDA (graves, medios, agudos) en vez de una sola
// global: antes solo los graves disparaban, y cuando disparaban empujaban TODAS
// las barras por igual (un flash plano parejo). Así no se sentía "como en los
// videos de música", donde el bombo pega en los graves, el redoblante/hi-hat
// pega en los agudos, y cada uno en SU propio momento, no todos juntos.
// Ahora cada banda tiene su propio promedio y dispara su propio golpe, que solo
// empuja las barras DE ESA banda — así se ven golpes independientes recorriendo
// el espectro en vez de un pulso uniforme.
private const val BEAT_BAND_COUNT = 4          // graves / medio-graves / medio-agudos / agudos
private const val BEAT_AVG_DECAY = 0.88f       // qué tan rápido se actualiza el promedio de cada banda
private const val BEAT_TRIGGER_RATIO = 1.22f   // cuánto debe superar su propio promedio para contar como golpe
private const val BEAT_BOOST = 1.7f            // empuje extra que se aplica SOLO a las barras de esa banda

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
 * - Detección de golpe POR BANDA (graves/medios/agudos): cada banda dispara su
 *   propio golpe contra su propio promedio reciente y empuja SOLO sus barras,
 *   no todas — así el bombo pega en los graves y el hi-hat pega en los agudos,
 *   cada uno en su momento, como en un analizador de espectro real.
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
    // Meta real, escrita por el análisis de audio (FFT) cada vez que llega un dato nuevo.
    val targetLevels = remember { FloatArray(barCount) }
    // Buffer que YA se dibuja (leído por Canvas). Se muta en el sitio, nunca se re-crea,
    // para no generar basura (garbage) en cada frame. Un loop aparte lo va acercando a
    // targetLevels a 60fps, así el movimiento se ve fluido aunque el FFT llegue más lento.
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
                // Promedio reciente de energía de CADA banda (para detectar su propio golpe).
                val bandAvg = FloatArray(BEAT_BAND_COUNT)
                // Frecuencia de muestreo real (Hz), la manda el propio Visualizer en cada
                // callback (en milliHertz). La necesitamos para saber a qué bin corresponde
                // TARGET_MAX_FREQUENCY_HZ, y recalcular los bordes si cambia (ej. cambia de
                // canción y el audio tiene otro sample rate).
                var sampleRateHz = 0

                fun buildEdgesIfNeeded() {
                    if (edges != null || bins < 2 || sampleRateHz <= 0) return
                    val minBin = 1
                    val nyquistBin = bins - 1
                    // Bin correspondiente a TARGET_MAX_FREQUENCY_HZ (en vez de ir hasta el
                    // Nyquist absoluto): así ninguna barra queda mirando una zona sin señal.
                    val binsPerHz = bins.toFloat() / (sampleRateHz / 2f)
                    val maxBin = (TARGET_MAX_FREQUENCY_HZ * binsPerHz).toInt().coerceIn(minBin + 1, nyquistBin)
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

                                // samplingRate llega en milliHertz (Android). Si cambia (o es la
                                // primera vez), recalculamos los bordes de las barras porque el
                                // bin que corresponde a TARGET_MAX_FREQUENCY_HZ depende de esto.
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

                                // Detección de golpe POR BANDA: dividimos las barras en
                                // BEAT_BAND_COUNT tramos (graves -> agudos) y cada uno detecta
                                // su propio golpe contra su propio promedio reciente. El boost
                                // de ese golpe solo se aplica a las barras DE ESA banda, no a
                                // todas — así el bombo pega en los graves y el hi-hat pega en
                                // los agudos, cada uno en su momento, como en un video real.
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
                                    // Curva perceptual: empuja los valores medios hacia arriba
                                    // para que se vea más "vivo" y menos plano. Bajada de 0.5 a
                                    // 0.42: silencio/audio bajo cae más cerca de 0 y lo fuerte
                                    // pega más cerca del techo, más contraste entre ambos.
                                    rawLevels[bar] = normalized.pow(0.42f)
                                }

                                // Solo actualizamos la META acá (barato, 32 floats). El
                                // MOVIMIENTO hacia esa meta lo hace el loop de animación de
                                // abajo, a 60fps, para que no se vea a saltos entre capturas.
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
            // Reseteamos meta Y pantalla a 0 para que la próxima vez (nueva canción, se
            // reanuda, etc.) no arranque "congelado" en el último valor.
            for (i in targetLevels.indices) targetLevels[i] = 0f
            for (i in displayLevels.indices) displayLevels[i] = 0f
            frameTick++
        }
    }

    // Loop de animación a la velocidad real de la pantalla (~60fps o más), totalmente
    // aparte de la tasa de captura del Visualizer. Cada frame acerca displayLevels a
    // targetLevels (la meta real, marcada por el audio) usando un suavizado exponencial
    // basado en TIEMPO transcurrido (dt), no en "pasos": así la velocidad percibida es
    // igual sin importar el refresh rate del celular, y el movimiento se ve continuo y
    // vivo en vez de a saltos cada vez que llega un dato de FFT nuevo.
    LaunchedEffect(Unit) {
        var lastFrameNanos = 0L
        while (true) {
            withFrameNanos { frameNanos ->
                val dt = if (lastFrameNanos == 0L) 0f else (frameNanos - lastFrameNanos) / 1_000_000_000f
                lastFrameNanos = frameNanos
                for (i in 0 until barCount) {
                    val current = displayLevels[i]
                    val goal = targetLevels[i]
                    val rate = if (goal > current) ATTACK_PER_SEC else RELEASE_PER_SEC
                    val alpha = 1f - exp(-rate * dt)
                    displayLevels[i] = current + (goal - current) * alpha
                }
            }
            frameTick++
        }
    }

    Canvas(modifier = modifier) {
        @Suppress("UNUSED_EXPRESSION")
        frameTick

        val barWidth = size.width / (barCount * 1.5f)
        val spacing = barWidth * 0.5f
        val maxBarHeight = size.height
        // Bajado de 0.03 a 0.015: en silencio/audio muy bajo las barras casi
        // desaparecen (en vez de quedar en un piso visible todo el tiempo), para
        // que el contraste contra los picos fuertes se note más.
        val minHeightFraction = 0.015f

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