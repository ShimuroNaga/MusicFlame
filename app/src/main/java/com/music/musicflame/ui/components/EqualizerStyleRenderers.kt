package com.music.musicflame.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Dispatcher central: dado un [EqualizerStyle] y un [EqualizerLevelsState] ya
 * calculado (real o de vista previa), dibuja el estilo correspondiente. Todos
 * los estilos reciben el mismo [color] — el color es una preferencia
 * ORTOGONAL al estilo, tal cual lo pidió el punto 4 del catálogo.
 */
@Composable
fun EqualizerCanvas(
    style: EqualizerStyle,
    spectrum: EqualizerLevelsState,
    color: Color,
    modifier: Modifier = Modifier
) {
    when (style) {
        EqualizerStyle.BARS -> BarsEqualizerCanvas(spectrum, color, modifier)
        EqualizerStyle.MIRRORED_BARS -> MirroredBarsEqualizerCanvas(spectrum, color, modifier)
        EqualizerStyle.WATER_WAVE -> WaterWaveEqualizerCanvas(spectrum, color, modifier)
        EqualizerStyle.PULSE_CIRCLE -> PulseCircleEqualizerCanvas(spectrum, color, modifier)
        EqualizerStyle.PARTICLES -> ParticlesEqualizerCanvas(spectrum, color, modifier)
        EqualizerStyle.THIN_BARS -> ThinBarsEqualizerCanvas(spectrum, color, modifier)
        EqualizerStyle.VU_METER_RETRO -> VuMeterRetroEqualizerCanvas(spectrum, color, modifier)
        EqualizerStyle.OSCILLOSCOPE -> OscilloscopeEqualizerCanvas(spectrum, color, modifier)
        EqualizerStyle.CONCENTRIC_RIPPLES -> ConcentricRipplesEqualizerCanvas(spectrum, color, modifier)
        EqualizerStyle.CONSTELLATION -> ConstellationEqualizerCanvas(spectrum, color, modifier)
    }
}

// --- 4a) DOBLE ECUALIZADOR ESPEJADO ---
// A diferencia de la primera versión (que partía el canvas en dos mitades
// pegadas al centro), esto imita al estilo BARS "de siempre": una fila de
// barras pegada al borde inferior, creciendo hacia arriba EXACTAMENTE igual
// que BarsEqualizerCanvas (mismo alto máximo relativo por fila). La fila de
// arriba es el espejo: nace del borde superior y crece hacia abajo. Entre
// ambas queda un hueco/margen fijo (GAP_FRACTION) en vez de tocarse en el
// medio, que es justo lo que se pidió: "una barra abajo como el original y
// la barra arriba mirando hacia abajo", con aire entre las dos.
private const val MIRROR_GAP_FRACTION = 0.16f

@Composable
fun MirroredBarsEqualizerCanvas(
    spectrum: EqualizerLevelsState,
    color: Color,
    modifier: Modifier = Modifier
) {
    val barCount = spectrum.barCount
    Canvas(modifier = modifier) {
        @Suppress("UNUSED_EXPRESSION")
        spectrum.tick

        val barWidth = size.width / (barCount * 1.5f)
        val spacing = barWidth * 0.5f
        val minHeightFraction = 0.02f

        // Cada fila (abajo y arriba) tiene su propio alto máximo, dejando un
        // margen fijo entre ambas en el medio del canvas.
        val gap = size.height * MIRROR_GAP_FRACTION
        val rowMaxHeight = (size.height - gap) / 2f
        val bottomEdge = size.height
        val topEdge = 0f

        for (index in 0 until barCount) {
            val level = spectrum.displayLevels[index].coerceIn(0f, 1f)
            val barHeight = rowMaxHeight * max(level, minHeightFraction)
            val x = index * (barWidth + spacing)

            // Fila de ABAJO: igual que el estilo clásico, nace del borde
            // inferior real del canvas y crece hacia arriba.
            drawRoundRect(
                color = color,
                topLeft = Offset(x, bottomEdge - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
            // Fila de ARRIBA: el espejo, nace del borde superior real y
            // "mira hacia abajo" (crece hacia abajo en vez de hacia arriba).
            drawRoundRect(
                color = color.copy(alpha = color.alpha * 0.7f),
                topLeft = Offset(x, topEdge),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}

// --- 4a-bis) FILA DE ARRIBA DEL ESPEJADO, para pantalla completa ---
// En el selector de Ajustes (previsualización chica) el espejado sigue
// dibujándose con [MirroredBarsEqualizerCanvas] de arriba, un solo Canvas
// autocontenido con las dos filas adentro — eso no cambió.
// PERO en FullScreenPlayer las dos filas ahora viven en dos cajas
// independientes ancladas cada una a su borde real de pantalla (abajo /
// arriba de verdad, no solo "arriba de una caja que empieza abajo"). La fila
// de abajo es sencillamente [BarsEqualizerCanvas] tal cual (mismo dibujo que
// el estilo clásico). Esta es la de ARRIBA: el mismo dibujo, pero el origen
// de crecimiento es el borde SUPERIOR del canvas en vez del inferior, así
// que las barras "miran hacia abajo".
@Composable
fun MirroredBarsTopRowCanvas(
    spectrum: EqualizerLevelsState,
    color: Color,
    modifier: Modifier = Modifier
) {
    val barCount = spectrum.barCount
    Canvas(modifier = modifier) {
        @Suppress("UNUSED_EXPRESSION")
        spectrum.tick

        val barWidth = size.width / (barCount * 1.5f)
        val spacing = barWidth * 0.5f
        val maxBarHeight = size.height
        val minHeightFraction = 0.015f

        for (index in 0 until barCount) {
            val level = spectrum.displayLevels[index].coerceIn(0f, 1f)
            val barHeight = maxBarHeight * max(level, minHeightFraction)
            val x = index * (barWidth + spacing)
            drawRoundRect(
                color = color,
                topLeft = Offset(x, 0f),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}

// --- 4b) ONDAS TIPO AGUA ---
// En vez de barras discretas, una curva continua (spline por Bezier cuadrática
// entre puntos medios) que sube y baja con el audio, con relleno degradado
// hacia abajo para que se sienta "líquida".
// FIX posición: antes la línea base (baseline) estaba en 0.92 * h, dejando un
// hueco visible entre la ola y el borde inferior REAL de la caja (a
// diferencia de BARS, que nace justo en el borde de abajo). Ahora la base
// está pegada al borde real (igual que el resto de los estilos) y la
// amplitud se reparte sobre esa misma altura total, así el estilo queda
// anclado en la misma posición que "estándar" en vez de flotar más arriba.
@Composable
fun WaterWaveEqualizerCanvas(
    spectrum: EqualizerLevelsState,
    color: Color,
    modifier: Modifier = Modifier
) {
    val barCount = spectrum.barCount
    Canvas(modifier = modifier) {
        @Suppress("UNUSED_EXPRESSION")
        spectrum.tick

        val w = size.width
        val h = size.height
        val baseline = h
        val amplitude = h * 0.9f

        val points = FloatArray(barCount + 1)
        for (i in 0 until barCount) {
            points[i] = spectrum.displayLevels[i].coerceIn(0f, 1f)
        }
        points[barCount] = points[barCount - 1]

        fun xAt(i: Int) = (i.toFloat() / barCount) * w
        fun yAt(i: Int) = baseline - points[i.coerceIn(0, barCount - 1)] * amplitude

        val path = Path().apply {
            moveTo(0f, baseline)
            lineTo(xAt(0), yAt(0))
            for (i in 0 until barCount - 1) {
                val midX = (xAt(i) + xAt(i + 1)) / 2f
                val midY = (yAt(i) + yAt(i + 1)) / 2f
                quadraticTo(xAt(i), yAt(i), midX, midY)
            }
            lineTo(xAt(barCount - 1), yAt(barCount - 1))
            lineTo(w, baseline)
            close()
        }

        // Relleno principal (más transparente, "cuerpo" de la ola).
        drawPath(path, color = color.copy(alpha = color.alpha * 0.35f))

        // Contorno de la ola: la línea que marca la "cresta" del agua.
        val outline = Path().apply {
            moveTo(xAt(0), yAt(0))
            for (i in 0 until barCount - 1) {
                val midX = (xAt(i) + xAt(i + 1)) / 2f
                val midY = (yAt(i) + yAt(i + 1)) / 2f
                quadraticTo(xAt(i), yAt(i), midX, midY)
            }
            lineTo(xAt(barCount - 1), yAt(barCount - 1))
        }
        drawPath(
            outline,
            color = color,
            style = Stroke(width = 6f, cap = StrokeCap.Round)
        )
    }
}

// --- 4c) CÍRCULO PULSANTE ---
// Pensado para rodear la carátula: un aro cuyo radio y grosor laten según la
// energía promedio general, más 2-3 anillos secundarios más tenues para dar
// sensación de "onda expansiva".
@Composable
fun PulseCircleEqualizerCanvas(
    spectrum: EqualizerLevelsState,
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        @Suppress("UNUSED_EXPRESSION")
        spectrum.tick

        var sum = 0f
        for (i in 0 until spectrum.barCount) sum += spectrum.displayLevels[i]
        val avgLevel = (sum / spectrum.barCount).coerceIn(0f, 1f)

        val cx = size.width / 2f
        val cy = size.height / 2f
        val maxRadius = min(size.width, size.height) / 2f
        val baseRadius = maxRadius * 0.55f

        // Anillos secundarios: fase corrida, se expanden y se desvanecen —
        // dan la sensación de "pulso" saliendo hacia afuera del anillo principal.
        for (ring in 1..2) {
            val ringLevel = avgLevel
            val ringRadius = baseRadius + (maxRadius - baseRadius) * ringLevel * (ring / 2f)
            drawCircle(
                color = color.copy(alpha = color.alpha * (0.18f / ring)),
                radius = ringRadius,
                center = Offset(cx, cy),
                style = Stroke(width = 4f)
            )
        }

        // Anillo principal: crece con la energía y también engrosa el trazo.
        val radius = baseRadius + (maxRadius - baseRadius) * avgLevel
        val strokeWidth = 6f + avgLevel * 14f
        drawCircle(
            color = color,
            radius = radius,
            center = Offset(cx, cy),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
    }
}

// --- 4d) PARTÍCULAS ---
// Un punto por barra/frecuencia, saltando en altura según su nivel, con un
// desplazamiento lateral fijo (pero aleatorio por punto) para que se vea
// orgánico y no una fila perfectamente alineada como las barras.
@Composable
fun ParticlesEqualizerCanvas(
    spectrum: EqualizerLevelsState,
    color: Color,
    modifier: Modifier = Modifier
) {
    val barCount = spectrum.barCount
    // Jitter fijo por partícula (semilla estable): se calcula una sola vez,
    // no en cada frame, para que cada punto "viva" siempre en su mismo carril
    // en vez de teletransportarse de lugar entre frames.
    val jitterX = remember(barCount) { FloatArray(barCount) { Random(it * 7919).nextFloat() * 0.6f - 0.3f } }
    val baseRadius = remember(barCount) { FloatArray(barCount) { Random(it * 104729).nextFloat() * 4f + 4f } }

    Canvas(modifier = modifier) {
        @Suppress("UNUSED_EXPRESSION")
        spectrum.tick

        val w = size.width
        val h = size.height
        val slot = w / barCount

        for (i in 0 until barCount) {
            val level = spectrum.displayLevels[i].coerceIn(0f, 1f)
            val x = (i + 0.5f + jitterX[i]) * slot
            val y = h * (1f - level * 0.9f) - baseRadius[i]
            val radius = baseRadius[i] + level * 10f
            drawCircle(
                color = color.copy(alpha = (color.alpha * (0.35f + level * 0.65f)).coerceIn(0f, 1f)),
                radius = radius,
                center = Offset(x.coerceIn(0f, w), y.coerceIn(radius, h))
            )
        }
    }
}

// --- 4e) BARRAS MINIMALISTAS FINAS (estilo Spotify Canvas) ---
// Igual concepto que las barras clásicas pero mucho más angostas, sin bordes
// redondeados grandes, y más juntas — una "línea de audio" en vez de barras.
@Composable
fun ThinBarsEqualizerCanvas(
    spectrum: EqualizerLevelsState,
    color: Color,
    modifier: Modifier = Modifier
) {
    val barCount = spectrum.barCount
    Canvas(modifier = modifier) {
        @Suppress("UNUSED_EXPRESSION")
        spectrum.tick

        val totalSlot = size.width / barCount
        val barWidth = (totalSlot * 0.35f).coerceAtLeast(1.5f)
        val maxBarHeight = size.height
        val minHeightFraction = 0.04f

        for (index in 0 until barCount) {
            val level = spectrum.displayLevels[index].coerceIn(0f, 1f)
            val barHeight = maxBarHeight * max(level, minHeightFraction)
            val x = index * totalSlot + (totalSlot - barWidth) / 2f
            drawRoundRect(
                color = color,
                topLeft = Offset(x, maxBarHeight - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}

// --- 4f) VU METER RETRO ---
// Dos agujas analógicas (izquierda/derecha, simuladas ambas a partir del mismo
// espectro con una leve diferencia de fase) que oscilan sobre un arco fijo,
// como los VU meter de equipos de audio vintage.
@Composable
fun VuMeterRetroEqualizerCanvas(
    spectrum: EqualizerLevelsState,
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        @Suppress("UNUSED_EXPRESSION")
        spectrum.tick

        val half = spectrum.barCount / 2
        var sumLeft = 0f
        var sumRight = 0f
        for (i in 0 until spectrum.barCount) {
            if (i < half) sumLeft += spectrum.displayLevels[i] else sumRight += spectrum.displayLevels[i]
        }
        val levelLeft = (sumLeft / max(1, half)).coerceIn(0f, 1f)
        val levelRight = (sumRight / max(1, spectrum.barCount - half)).coerceIn(0f, 1f)

        val gaugeCount = 2
        val gaugeWidth = size.width / gaugeCount
        val arcRadius = min(gaugeWidth, size.height) * 0.42f
        val needleColor = color

        // Ángulo del arco fijo: de 200° a 340° (un abanico hacia abajo, como un
        // VU meter de verdad), con la aguja moviéndose dentro de ese rango.
        val startAngleDeg = 200f
        val sweepAngleDeg = 140f

        fun drawGauge(cx: Float, cy: Float, level: Float) {
            // Arco de fondo (la "escala").
            drawArc(
                color = needleColor.copy(alpha = needleColor.alpha * 0.25f),
                startAngle = startAngleDeg,
                sweepAngle = sweepAngleDeg,
                useCenter = false,
                topLeft = Offset(cx - arcRadius, cy - arcRadius),
                size = Size(arcRadius * 2f, arcRadius * 2f),
                style = Stroke(width = 5f, cap = StrokeCap.Round)
            )

            // Marcas fijas cada 20% de la escala, como las rayitas de un VU real.
            for (mark in 0..5) {
                val angle = Math.toRadians((startAngleDeg + sweepAngleDeg * (mark / 5f)).toDouble())
                val inner = arcRadius * 0.82f
                val outer = arcRadius
                val x1 = cx + (inner * cos(angle)).toFloat()
                val y1 = cy + (inner * sin(angle)).toFloat()
                val x2 = cx + (outer * cos(angle)).toFloat()
                val y2 = cy + (outer * sin(angle)).toFloat()
                drawLine(
                    color = needleColor.copy(alpha = needleColor.alpha * 0.4f),
                    start = Offset(x1, y1),
                    end = Offset(x2, y2),
                    strokeWidth = 3f
                )
            }

            // Aguja: pivota desde el centro del arco, largo = radio, ángulo según nivel.
            val needleAngleDeg = startAngleDeg + sweepAngleDeg * level
            val needleAngle = Math.toRadians(needleAngleDeg.toDouble())
            val needleLength = arcRadius * 0.9f
            val tipX = cx + (needleLength * cos(needleAngle)).toFloat()
            val tipY = cy + (needleLength * sin(needleAngle)).toFloat()
            drawLine(
                color = needleColor,
                start = Offset(cx, cy),
                end = Offset(tipX, tipY),
                strokeWidth = 5f,
                cap = StrokeCap.Round
            )
            drawCircle(color = needleColor, radius = 8f, center = Offset(cx, cy))
        }

        val gaugeCy = size.height * 0.78f
        drawGauge(gaugeWidth * 0.5f, gaugeCy, levelLeft)
        drawGauge(gaugeWidth * 1.5f, gaugeCy, levelRight)
    }
}

// --- 4g) OSCILOSCOPIO (waveform en vivo) ---
// A diferencia de "Ondas de agua" (WATER_WAVE, que tiene relleno y nace del
// borde inferior como una ola), acá es solo un TRAZO (sin relleno) centrado
// verticalmente que sube y baja por encima y por debajo de una línea media,
// como un osciloscopio/monitor de audio real. El signo (arriba/abajo) de
// cada punto es fijo por índice (elegido una sola vez), para que el trazo se
// vea como una forma de onda real en vez de "todo para arriba".
@Composable
fun OscilloscopeEqualizerCanvas(
    spectrum: EqualizerLevelsState,
    color: Color,
    modifier: Modifier = Modifier
) {
    val barCount = spectrum.barCount
    val sign = remember(barCount) { FloatArray(barCount) { if (Random(it * 92821).nextBoolean()) 1f else -1f } }

    Canvas(modifier = modifier) {
        @Suppress("UNUSED_EXPRESSION")
        spectrum.tick

        val w = size.width
        val h = size.height
        val midY = h / 2f
        val amplitude = h * 0.48f

        fun xAt(i: Int) = (i.toFloat() / (barCount - 1)) * w
        fun yAt(i: Int): Float {
            val level = spectrum.displayLevels[i].coerceIn(0f, 1f)
            return midY - sign[i] * level * amplitude
        }

        // Línea media de referencia, tenue (marca el "cero" del osciloscopio).
        drawLine(
            color = color.copy(alpha = color.alpha * 0.15f),
            start = Offset(0f, midY),
            end = Offset(w, midY),
            strokeWidth = 1.5f
        )

        val path = Path().apply {
            moveTo(xAt(0), yAt(0))
            for (i in 0 until barCount - 1) {
                val midX = (xAt(i) + xAt(i + 1)) / 2f
                val midYPoint = (yAt(i) + yAt(i + 1)) / 2f
                quadraticTo(xAt(i), yAt(i), midX, midYPoint)
            }
            lineTo(xAt(barCount - 1), yAt(barCount - 1))
        }

        drawPath(
            path,
            color = color,
            style = Stroke(width = 4f, cap = StrokeCap.Round)
        )
    }
}

// --- 4h) ONDAS CONCÉNTRICAS ---
// A diferencia de "Círculo pulsante" (PULSE_CIRCLE, un solo aro que late al
// ritmo INSTANTÁNEO del nivel promedio), acá hay varios anillos que nacen en
// el centro y se EXPANDEN hacia afuera de forma continua en el tiempo, cada
// uno con su propia fase/delay (progress[i] arranca repartido entre 0 y 1),
// desvaneciéndose a medida que crecen. El resultado es una cascada de ondas
// escalonadas, como el eco visual de una piedra cayendo repetidas veces en
// el agua — nunca se detiene del todo, pero el nivel de audio acelera la
// velocidad y agranda el alcance de cada anillo.
private const val RIPPLE_RING_COUNT = 4

@Composable
fun ConcentricRipplesEqualizerCanvas(
    spectrum: EqualizerLevelsState,
    color: Color,
    modifier: Modifier = Modifier
) {
    val progress = remember { FloatArray(RIPPLE_RING_COUNT) { it / RIPPLE_RING_COUNT.toFloat() } }
    var localTick by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        var lastNanos = 0L
        while (true) {
            withFrameNanos { frameNanos ->
                val dt = if (lastNanos == 0L) 0f else (frameNanos - lastNanos) / 1_000_000_000f
                lastNanos = frameNanos

                var sum = 0f
                for (i in 0 until spectrum.barCount) sum += spectrum.displayLevels[i]
                val avgLevel = (sum / spectrum.barCount).coerceIn(0f, 1f)

                // Más nivel de audio = ondas más rápidas ("eco" más
                // urgente), pero jamás se detienen del todo: siempre hay
                // algo de vida, incluso en silencio.
                val speed = 0.22f + avgLevel * 0.55f
                for (i in 0 until RIPPLE_RING_COUNT) {
                    progress[i] = (progress[i] + dt * speed) % 1f
                }
            }
            localTick++
        }
    }

    Canvas(modifier = modifier) {
        @Suppress("UNUSED_EXPRESSION")
        localTick

        var sum = 0f
        for (i in 0 until spectrum.barCount) sum += spectrum.displayLevels[i]
        val avgLevel = (sum / spectrum.barCount).coerceIn(0f, 1f)

        val cx = size.width / 2f
        val cy = size.height / 2f
        val maxRadius = min(size.width, size.height) / 2f
        val minRadius = maxRadius * 0.12f
        // Con más nivel, cada anillo también "nace" con más fuerza y llega
        // más lejos antes de apagarse.
        val expandRange = maxRadius * (0.75f + avgLevel * 0.25f)

        for (i in 0 until RIPPLE_RING_COUNT) {
            val p = progress[i]
            val radius = minRadius + expandRange * p
            // Se desvanece a medida que se expande: se siente como una onda
            // perdiendo energía, no un aro fijo que solo cambia de tamaño.
            val alpha = (1f - p) * (1f - p) * (0.5f + avgLevel * 0.5f)
            val strokeWidth = (10f * (1f - p) + 2f).coerceAtLeast(1.5f)
            drawCircle(
                color = color.copy(alpha = (color.alpha * alpha).coerceIn(0f, 1f)),
                radius = radius,
                center = Offset(cx, cy),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }

        // Punto central fijo que "respira" con el nivel promedio: el origen
        // visual del que salen todas las ondas.
        drawCircle(
            color = color.copy(alpha = (color.alpha * (0.5f + avgLevel * 0.5f)).coerceIn(0f, 1f)),
            radius = minRadius * (0.5f + avgLevel * 0.3f),
            center = Offset(cx, cy)
        )
    }
}

// --- 4i) CONSTELACIÓN ---
// 4 constelaciones "reales" (formas fijas tipo Casiopea/Osa Mayor/Cruz del
// Sur/Orión, no un grafo de vecinos recalculado cada frame): cada una vive
// en una posición/escala/rotación semi-fija sorteada UNA SOLA VEZ (como pide
// un cielo real y no partículas caóticas), y cada estrella representa una
// banda de frecuencia del espectro. La altura de cada constelación respeta
// su banda: la de graves tiende a aparecer abajo, la de agudos arriba.
//
// Brillo con decay: cada estrella tiene su propio nivel suavizado (ataque
// rápido, caída lenta) para que no titile feo. Las líneas entre dos
// estrellas de una misma constelación SOLO se dibujan cuando AMBAS superan
// un umbral de brillo al mismo tiempo — así la forma se arma y se deshace en
// vivo según lo que suena, en vez de ser un dibujo fijo que solo cambia de
// opacidad. Un micro-drift lento (seno en el tiempo) hace que las estrellas
// "respiren" en vez de quedar clavadas cuando no hay mucho audio.
private data class ConstellationShape(val points: List<Offset>, val edges: List<Pair<Int, Int>>)

private val CONSTELLATION_SHAPES = listOf(
    // Zigzag tipo Casiopea.
    ConstellationShape(
        points = listOf(
            Offset(-1f, 0.1f), Offset(-0.55f, -0.7f), Offset(0f, 0.15f),
            Offset(0.55f, -0.75f), Offset(1f, 0f)
        ),
        edges = listOf(0 to 1, 1 to 2, 2 to 3, 3 to 4)
    ),
    // Cucharón tipo Osa Mayor.
    ConstellationShape(
        points = listOf(
            Offset(-1f, -0.2f), Offset(-0.55f, -0.55f), Offset(-0.1f, -0.35f),
            Offset(0.35f, -0.5f), Offset(0.55f, 0.05f), Offset(0.95f, 0.5f)
        ),
        edges = listOf(0 to 1, 1 to 2, 2 to 3, 3 to 4, 4 to 5)
    ),
    // Cruz tipo Cruz del Sur.
    ConstellationShape(
        points = listOf(
            Offset(0f, -1f), Offset(0f, 0.7f), Offset(-0.85f, 0.05f),
            Offset(0.85f, -0.05f), Offset(0.05f, 0.05f)
        ),
        edges = listOf(0 to 4, 4 to 1, 2 to 4, 4 to 3)
    ),
    // Cinturón + hombros tipo Orión.
    ConstellationShape(
        points = listOf(
            Offset(-0.6f, -0.05f), Offset(0f, 0.05f), Offset(0.6f, -0.05f),
            Offset(-0.9f, -1f), Offset(0.9f, -0.9f), Offset(0f, 1f)
        ),
        edges = listOf(0 to 1, 1 to 2, 3 to 0, 4 to 2, 1 to 5)
    )
)

private class ConstellationLayout(
    val centerXFraction: Float,
    val centerYFraction: Float,
    val scale: Float,
    val rotationDeg: Float,
    val bandStart: Int,
    val bandEnd: Int
)

@Composable
fun ConstellationEqualizerCanvas(
    spectrum: EqualizerLevelsState,
    color: Color,
    modifier: Modifier = Modifier
) {
    val barCount = spectrum.barCount
    val shapes = CONSTELLATION_SHAPES
    val totalStars = remember { shapes.sumOf { it.points.size } }

    // Layout aleatorio (posición, escala, rotación y banda de frecuencia)
    // de cada una de las 4 constelaciones — sorteado una sola vez por
    // barCount, no en cada frame.
    val layouts = remember(barCount) {
        val bandSize = (barCount / shapes.size).coerceAtLeast(1)
        shapes.indices.map { g ->
            val yBase = 0.82f - g * (0.62f / (shapes.size - 1))
            ConstellationLayout(
                centerXFraction = 0.18f + Random.nextFloat() * 0.64f,
                centerYFraction = (yBase + (Random.nextFloat() - 0.5f) * 0.12f).coerceIn(0.14f, 0.86f),
                // Escala más grande (antes 0.65-1.15): constelaciones bien visibles,
                // no puntitos chicos perdidos en la franja del ecualizador.
                scale = 0.95f + Random.nextFloat() * 0.65f,
                rotationDeg = Random.nextFloat() * 50f - 25f,
                bandStart = g * bandSize,
                bandEnd = if (g == shapes.size - 1) barCount - 1 else ((g + 1) * bandSize - 1).coerceAtMost(barCount - 1)
            )
        }
    }

    // A qué índice del espectro escucha cada estrella global (repartido
    // dentro de la banda de su propia constelación).
    val starSpectrumIndex = remember(barCount) {
        val list = IntArray(totalStars)
        var s = 0
        shapes.forEachIndexed { g, shape ->
            val layout = layouts[g]
            val span = (layout.bandEnd - layout.bandStart + 1).coerceAtLeast(1)
            for (local in shape.points.indices) {
                list[s] = layout.bandStart + (local % span)
                s++
            }
        }
        list
    }

    val brightness = remember(totalStars) { FloatArray(totalStars) }
    val driftPhase = remember(totalStars) { FloatArray(totalStars) { Random.nextFloat() * 6.2832f } }
    val timeState = remember { mutableStateOf(0f) }
    var localTick by remember { mutableStateOf(0) }

    LaunchedEffect(totalStars) {
        var lastNanos = 0L
        var t = 0f
        while (true) {
            withFrameNanos { frameNanos ->
                val dt = if (lastNanos == 0L) 0f else (frameNanos - lastNanos) / 1_000_000_000f
                lastNanos = frameNanos
                t += dt
                for (i in 0 until totalStars) {
                    val target = spectrum.displayLevels[starSpectrumIndex[i]].coerceIn(0f, 1f)
                    val current = brightness[i]
                    // Ataque rápido (se enciende ya) pero caída lenta
                    // (decay), para que no parpadee feo — se apaga
                    // gradual, no de golpe.
                    brightness[i] = if (target > current) {
                        current + (target - current) * (dt * 14f).coerceIn(0f, 1f)
                    } else {
                        current + (target - current) * (dt * 2.4f).coerceIn(0f, 1f)
                    }
                }
                timeState.value = t
            }
            localTick++
        }
    }

    Canvas(modifier = modifier) {
        @Suppress("UNUSED_EXPRESSION")
        localTick
        val t = timeState.value

        val w = size.width
        val h = size.height
        val threshold = 0.3f
        var starOffset = 0

        for (g in shapes.indices) {
            val shape = shapes[g]
            val layout = layouts[g]
            val cx = layout.centerXFraction * w
            val cy = layout.centerYFraction * h
            // Spread más grande (antes 0.17f): la forma de cada constelación ocupa
            // bastante más espacio, para que se lea clara en vez de diminuta.
            val radiusBase = min(w, h) * 0.32f * layout.scale
            val rot = Math.toRadians(layout.rotationDeg.toDouble())
            val cosR = cos(rot).toFloat()
            val sinR = sin(rot).toFloat()

            fun starPos(localIndex: Int): Offset {
                val s = starOffset + localIndex
                val drift = 0.03f * sin(t * 0.6f + driftPhase[s])
                val p = shape.points[localIndex]
                val lx = (p.x + drift) * radiusBase
                val ly = p.y * radiusBase
                val rx = lx * cosR - ly * sinR
                val ry = lx * sinR + ly * cosR
                return Offset(cx + rx, cy + ry)
            }

            // Líneas: solo aparecen cuando AMBAS puntas superan el umbral de
            // brillo al mismo tiempo — la constelación se arma y se deshace
            // en vivo según lo que suena en ese momento.
            for ((a, b) in shape.edges) {
                val brightA = brightness[starOffset + a]
                val brightB = brightness[starOffset + b]
                if (brightA > threshold && brightB > threshold) {
                    val lineAlpha = (min(brightA, brightB) - threshold) / (1f - threshold)
                    drawLine(
                        color = color.copy(alpha = (color.alpha * lineAlpha * 0.8f).coerceIn(0f, 1f)),
                        start = starPos(a),
                        end = starPos(b),
                        strokeWidth = 3f
                    )
                }
            }

            // Estrellas: halo tenue + núcleo brillante, tamaño y opacidad
            // según el brillo (ya suavizado con decay arriba).
            for (localIndex in shape.points.indices) {
                val bright = brightness[starOffset + localIndex]
                val pos = starPos(localIndex)
                // Estrellas más grandes (antes 2f + bright*5f, halo x2.2): núcleo y
                // halo bien visibles, no puntitos perdidos entre las líneas.
                val radius = 3.5f + bright * 8f
                drawCircle(
                    color = color.copy(alpha = (color.alpha * bright * 0.35f).coerceIn(0f, 1f)),
                    radius = radius * 2.4f,
                    center = pos
                )
                drawCircle(
                    color = color.copy(alpha = (color.alpha * (0.25f + bright * 0.75f)).coerceIn(0f, 1f)),
                    radius = radius,
                    center = pos
                )
            }

            starOffset += shape.points.size
        }
    }
}
