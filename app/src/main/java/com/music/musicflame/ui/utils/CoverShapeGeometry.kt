package com.music.musicflame.ui.utils

import com.music.musicflame.data.CoverDesign
import com.music.musicflame.data.CoverFigure
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Destino de dibujo mínimo, sin dependencias de Android ni de Compose.
 *
 * Existe para que la MISMA geometría del diseño de carátula la usen:
 *  - Compose (CustomCoverShape en AlbumArt.kt, androidx.compose.ui.graphics.Path)
 *  - los widgets del home screen (android.graphics.Path, que no puede usar Compose)
 * Así lo que se ve en la vista previa del editor es, punto por punto, lo que se
 * ve en las listas, el reproductor y los widgets.
 */
interface CoverPathSink {
    fun moveTo(x: Float, y: Float)
    fun lineTo(x: Float, y: Float)
    fun cubicTo(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float)
    fun close()
}

object CoverShapeGeometry {

    const val MIN_SIDES = 3
    const val MAX_SIDES = 12

    // Radio interior de la estrella, como fracción del radio exterior.
    private const val STAR_INNER_RATIO = 0.5f

    const val DEFAULT_DEPTH = 0.5f

    // Puntos con los que se muestrean las figuras curvas (flor, corazón).
    private const val SMOOTH_POINTS = 192

    /** Atajo: dibuja el diseño completo del usuario (figura, caras, redondeo, profundidad, giro). */
    fun build(sink: CoverPathSink, design: CoverDesign, width: Float, height: Float) {
        build(
            sink = sink,
            figure = design.figure,
            sides = design.sides,
            roundness = design.roundness,
            depth = design.depth,
            rotationDeg = design.rotation,
            width = width,
            height = height
        )
    }

    /** Versión original (solo polígono / estrella); se conserva para no romper pruebas ni llamadas viejas. */
    fun build(
        sink: CoverPathSink,
        isStar: Boolean,
        sides: Int,
        roundness: Float,
        width: Float,
        height: Float
    ) {
        build(
            sink = sink,
            figure = if (isStar) CoverFigure.STAR else CoverFigure.POLYGON,
            sides = sides,
            roundness = roundness,
            depth = DEFAULT_DEPTH,
            rotationDeg = 0f,
            width = width,
            height = height
        )
    }

    /**
     * Dibuja en [sink] la [figure] elegida con las esquinas redondeadas según [roundness]
     * (0 = esquinas vivas, 1 = redondeo máximo), ajustada y centrada dentro de un área de
     * [width] x [height] (el ajuste se calcula sobre el contorno ya redondeado y girado).
     *
     *  - [sides]: caras (polígono), puntas (estrella), pétalos (flor) o dientes (engrane).
     *  - [depth] 0..1: profundidad de las puntas/pétalos, grosor de la cruz, altura de los
     *    dientes o anchura del rombo (el corazón no la usa).
     *  - [rotationDeg]: giro de la figura en grados, sentido de las manecillas del reloj.
     *
     * El redondeo usa arcos de circunferencia tangentes a los dos lados de cada
     * esquina. Con roundness = 1 cada arco llega justo hasta la mitad del lado, así
     * que cualquier polígono regular se convierte en un círculo perfecto. En las figuras
     * curvas (flor, corazón) el redondeo las va mezclando con un círculo.
     */
    fun build(
        sink: CoverPathSink,
        figure: CoverFigure,
        sides: Int,
        roundness: Float,
        depth: Float,
        rotationDeg: Float,
        width: Float,
        height: Float
    ) {
        val n = sides.coerceIn(MIN_SIDES, MAX_SIDES)
        val r = (if (roundness.isNaN()) 0f else roundness).coerceIn(0f, 1f)
        val d = (if (depth.isNaN()) DEFAULT_DEPTH else depth).coerceIn(0f, 1f)
        val smooth = figure == CoverFigure.FLOWER || figure == CoverFigure.HEART
        val unit = rotate(unitVertices(figure, n, d, r), if (rotationDeg.isNaN()) 0f else rotationDeg)
        // En las figuras curvas el redondeo ya va dentro de los puntos (se mezclan con un círculo).
        val cornerR = if (smooth) 0f else r
        val count = unit.size / 2
        val ux = FloatArray(count) { unit[it * 2] }
        val uy = FloatArray(count) { unit[it * 2 + 1] }

        // Pasada 1: se mide el contorno YA redondeado (no los vértices). Así un
        // triángulo o un pentágono con mucho redondeo sigue llenando y centrándose
        // en el área, en vez de quedar como una figura chica y corrida.
        val bounds = BoundsSink()
        emit(bounds, ux, uy, cornerR)
        val extentW = bounds.maxX - bounds.minX
        val extentH = bounds.maxY - bounds.minY

        // Ajuste uniforme (sin deformar) al área, centrado.
        val scale = min(width / extentW, height / extentH)
        val offsetX = (width - extentW * scale) / 2f - bounds.minX * scale
        val offsetY = (height - extentH * scale) / 2f - bounds.minY * scale
        val vx = FloatArray(count) { ux[it] * scale + offsetX }
        val vy = FloatArray(count) { uy[it] * scale + offsetY }

        // Pasada 2: el trazado real, ya a escala.
        emit(sink, vx, vy, cornerR)
    }

    // Mide el rectángulo que ocupa un trazado (las curvas se muestrean).
    private class BoundsSink : CoverPathSink {
        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        private var curX = 0f
        private var curY = 0f

        private fun add(x: Float, y: Float) {
            minX = min(minX, x); maxX = max(maxX, x)
            minY = min(minY, y); maxY = max(maxY, y)
        }

        override fun moveTo(x: Float, y: Float) { add(x, y); curX = x; curY = y }
        override fun lineTo(x: Float, y: Float) { add(x, y); curX = x; curY = y }
        override fun cubicTo(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) {
            val steps = 24
            for (s in 1..steps) {
                val t = s / steps.toFloat()
                val u = 1f - t
                add(
                    u * u * u * curX + 3f * u * u * t * x1 + 3f * u * t * t * x2 + t * t * t * x3,
                    u * u * u * curY + 3f * u * u * t * y1 + 3f * u * t * t * y2 + t * t * t * y3
                )
            }
            curX = x3; curY = y3
        }
        override fun close() {}
    }

    // Recorre los vértices (vx, vy) y emite el contorno con las esquinas redondeadas.
    private fun emit(sink: CoverPathSink, vx: FloatArray, vy: FloatArray, r: Float) {
        val count = vx.size

        if (r <= 0.001f) {
            sink.moveTo(vx[0], vy[0])
            for (i in 1 until count) sink.lineTo(vx[i], vy[i])
            sink.close()
            return
        }

        // Para cada vértice: punto de entrada, punto de salida y puntos de control
        // del arco (aproximado con una curva cúbica) que redondea la esquina.
        val inX = FloatArray(count); val inY = FloatArray(count)
        val outX = FloatArray(count); val outY = FloatArray(count)
        val c1X = FloatArray(count); val c1Y = FloatArray(count)
        val c2X = FloatArray(count); val c2Y = FloatArray(count)
        val sharp = BooleanArray(count)

        for (i in 0 until count) {
            val p = (i + count - 1) % count
            val q = (i + 1) % count
            val toPrevX = vx[p] - vx[i]; val toPrevY = vy[p] - vy[i]
            val toNextX = vx[q] - vx[i]; val toNextY = vy[q] - vy[i]
            val lenPrev = sqrt(toPrevX * toPrevX + toPrevY * toPrevY)
            val lenNext = sqrt(toNextX * toNextX + toNextY * toNextY)
            val u1X = toPrevX / lenPrev; val u1Y = toPrevY / lenPrev
            val u2X = toNextX / lenNext; val u2Y = toNextY / lenNext

            // Ángulo entre las dos aristas (0..π) y giro del trazado (π - ángulo).
            val dot = (u1X * u2X + u1Y * u2Y).coerceIn(-1f, 1f)
            val phi = acos(dot)
            val turn = PI.toFloat() - phi
            if (turn < 1e-3f || phi < 1e-3f) {
                sharp[i] = true
                continue
            }

            // Distancia de tangencia: como mucho media arista, para que los arcos
            // de vértices vecinos nunca se pisen.
            val d = r * min(lenPrev, lenNext) / 2f
            val radius = d * tan(phi / 2f)
            val handle = (4f / 3f) * tan(turn / 4f) * radius

            inX[i] = vx[i] + u1X * d; inY[i] = vy[i] + u1Y * d
            outX[i] = vx[i] + u2X * d; outY[i] = vy[i] + u2Y * d
            c1X[i] = inX[i] - u1X * handle; c1Y[i] = inY[i] - u1Y * handle
            c2X[i] = outX[i] - u2X * handle; c2Y[i] = outY[i] - u2Y * handle
        }

        // Arranca al final del arco del vértice 0 y recorre los demás.
        if (sharp[0]) sink.moveTo(vx[0], vy[0]) else sink.moveTo(outX[0], outY[0])
        for (i in 1 until count) {
            if (sharp[i]) {
                sink.lineTo(vx[i], vy[i])
            } else {
                sink.lineTo(inX[i], inY[i])
                sink.cubicTo(c1X[i], c1Y[i], c2X[i], c2Y[i], outX[i], outY[i])
            }
        }
        if (!sharp[0]) {
            sink.lineTo(inX[0], inY[0])
            sink.cubicTo(c1X[0], c1Y[0], c2X[0], c2Y[0], outX[0], outY[0])
        }
        sink.close()
    }

    // Gira los vértices (x0, y0, x1, y1, ...) alrededor del origen; con y hacia abajo, un
    // ángulo positivo gira en sentido de las manecillas del reloj.
    private fun rotate(v: FloatArray, deg: Float): FloatArray {
        if (abs(deg % 360f) < 1e-3f) return v
        val a = Math.toRadians(deg.toDouble())
        val c = cos(a).toFloat()
        val s = sin(a).toFloat()
        return FloatArray(v.size) { idx ->
            val x = v[idx - idx % 2]
            val y = v[idx - idx % 2 + 1]
            if (idx % 2 == 0) x * c - y * s else x * s + y * c
        }
    }

    // Vértices (x0, y0, x1, y1, ...) de la figura sobre (aprox.) la circunferencia unitaria.
    private fun unitVertices(figure: CoverFigure, n: Int, depth: Float, roundness: Float): FloatArray =
        when (figure) {
            CoverFigure.POLYGON -> polygonVertices(n)
            CoverFigure.STAR -> starVertices(n, (1f - depth).coerceIn(0.12f, 0.92f))
            CoverFigure.FLOWER -> flowerPoints(n, depth * 0.6f * (1f - roundness))
            CoverFigure.HEART -> heartPoints(roundness)
            CoverFigure.CROSS -> crossVertices(0.2f + depth * 0.55f)
            CoverFigure.GEAR -> gearVertices(n, (1f - depth * 0.5f).coerceIn(0.5f, 0.95f))
            CoverFigure.DIAMOND -> diamondVertices(0.35f + depth * 0.65f)
        }

    // Polígono: con n par queda una arista plana arriba (el cuadrado se ve como
    // cuadrado); con n impar, un vértice apunta hacia arriba (triángulo, pentágono).
    private fun polygonVertices(n: Int): FloatArray {
        val start = if (n % 2 == 0) -PI / 2.0 + PI / n else -PI / 2.0
        return FloatArray(n * 2) { idx ->
            val k = idx / 2
            val angle = start + 2.0 * PI * k / n
            (if (idx % 2 == 0) cos(angle) else sin(angle)).toFloat()
        }
    }

    // Estrella: 2n vértices alternando radio exterior e interior, punta arriba.
    private fun starVertices(n: Int, innerRatio: Float): FloatArray =
        FloatArray(n * 4) { idx ->
            val k = idx / 2
            val isX = idx % 2 == 0
            val outer = k % 2 == 0
            val radius = if (outer) 1f else innerRatio
            val angle = -PI / 2.0 + k * PI / n
            (if (isX) radius * cos(angle) else radius * sin(angle)).toFloat()
        }

    // Flor: r(t) = 1 + amplitud * cos(n t), un pétalo apunta hacia arriba.
    private fun flowerPoints(n: Int, amplitude: Float): FloatArray =
        FloatArray(SMOOTH_POINTS * 2) { idx ->
            val k = idx / 2
            val t = 2.0 * PI * k / SMOOTH_POINTS
            val radius = 1.0 + amplitude * cos(n * t)
            val angle = -PI / 2.0 + t
            (if (idx % 2 == 0) radius * cos(angle) else radius * sin(angle)).toFloat()
        }

    // Corazón (curva clásica), mezclado con un círculo según el redondeo.
    private fun heartPoints(roundness: Float): FloatArray =
        FloatArray(SMOOTH_POINTS * 2) { idx ->
            val k = idx / 2
            val t = 2.0 * PI * k / SMOOTH_POINTS
            val heart = if (idx % 2 == 0) {
                16.0 * sin(t).pow(3) / 17.0
            } else {
                -(13.0 * cos(t) - 5.0 * cos(2 * t) - 2.0 * cos(3 * t) - cos(4 * t)) / 17.0
            }
            val circle = if (idx % 2 == 0) sin(t) else -cos(t)
            ((1f - roundness) * heart + roundness * circle).toFloat()
        }

    // Cruz (signo +) de 12 vértices; [arm] = mitad del grosor de los brazos (0..1).
    private fun crossVertices(arm: Float): FloatArray {
        val w = arm.coerceIn(0.1f, 0.9f)
        return floatArrayOf(
            -w, -1f, w, -1f, w, -w, 1f, -w, 1f, w, w, w,
            w, 1f, -w, 1f, -w, w, -1f, w, -1f, -w, -w, -w
        )
    }

    // Engrane: [teeth] dientes trapezoidales sobre un cuerpo de radio [innerRatio].
    private fun gearVertices(teeth: Int, innerRatio: Float): FloatArray {
        val out = FloatArray(teeth * 8)
        val slot = 2.0 * PI / teeth
        for (i in 0 until teeth) {
            val center = -PI / 2.0 + slot * i
            val baseHalf = slot * 0.28
            val tipHalf = slot * 0.17
            val radii = floatArrayOf(innerRatio, 1f, 1f, innerRatio)
            val offsets = doubleArrayOf(-baseHalf, -tipHalf, tipHalf, baseHalf)
            for (j in 0 until 4) {
                out[i * 8 + j * 2] = (radii[j] * cos(center + offsets[j])).toFloat()
                out[i * 8 + j * 2 + 1] = (radii[j] * sin(center + offsets[j])).toFloat()
            }
        }
        return out
    }

    // Rombo con la punta arriba; [halfWidth] = anchura relativa a la altura (1 = cuadrado girado).
    private fun diamondVertices(halfWidth: Float): FloatArray =
        floatArrayOf(0f, -1f, halfWidth, 0f, 0f, 1f, -halfWidth, 0f)
}
