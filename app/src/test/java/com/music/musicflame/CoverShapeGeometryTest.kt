package com.music.musicflame

import com.music.musicflame.data.CoverDesign
import com.music.musicflame.data.CoverFigure
import com.music.musicflame.ui.utils.CoverPathSink
import com.music.musicflame.ui.utils.CoverShapeGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

/** Pruebas de la geometría del editor de carátulas (pura, corre en JVM sin Android). */
class CoverShapeGeometryTest {

    // Graba los puntos del trazado (las curvas se muestrean).
    private class Recorder : CoverPathSink {
        val xs = ArrayList<Double>()
        val ys = ArrayList<Double>()
        var closed = false
        var invalid = false
        private var curX = 0f
        private var curY = 0f

        private fun add(x: Float, y: Float) {
            if (x.isNaN() || y.isNaN() || x.isInfinite() || y.isInfinite()) invalid = true
            xs.add(x.toDouble()); ys.add(y.toDouble())
            curX = x; curY = y
        }

        override fun moveTo(x: Float, y: Float) = add(x, y)
        override fun lineTo(x: Float, y: Float) = add(x, y)
        override fun cubicTo(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) {
            val x0 = curX; val y0 = curY
            for (s in 1..16) {
                val t = s / 16f; val u = 1f - t
                add(
                    u * u * u * x0 + 3 * u * u * t * x1 + 3 * u * t * t * x2 + t * t * t * x3,
                    u * u * u * y0 + 3 * u * u * t * y1 + 3 * u * t * t * y2 + t * t * t * y3
                )
            }
        }
        override fun close() { closed = true }
    }

    private fun record(star: Boolean, sides: Int, roundness: Float, w: Float = 200f, h: Float = 200f) =
        Recorder().also { CoverShapeGeometry.build(it, star, sides, roundness, w, h) }

    @Test
    fun everyCombinationIsClosedFiniteAndInsideTheArea() {
        for (star in listOf(false, true)) for (n in 3..12) for (r in listOf(0f, 0.2f, 0.5f, 0.8f, 1f)) {
            val rec = record(star, n, r)
            val msg = "star=$star n=$n r=$r"
            assertTrue(msg, rec.closed)
            assertFalse(msg, rec.invalid)
            assertTrue(msg, rec.xs.min() >= -0.5 && rec.ys.min() >= -0.5)
            assertTrue(msg, rec.xs.max() <= 200.5 && rec.ys.max() <= 200.5)
        }
    }

    @Test
    fun maxRoundnessMakesAPerfectCenteredCircleForAnySideCount() {
        for (n in CoverShapeGeometry.MIN_SIDES..CoverShapeGeometry.MAX_SIDES) {
            val rec = record(false, n, 1f)
            // Centro y radio esperados: el cuadro completo (200x200 -> centro 100, radio 100).
            var maxDeviation = 0.0
            // Ajuste simple: distancia de cada punto al centro del cuadro.
            val rs = rec.xs.indices.map { sqrt((rec.xs[it] - 100).pow(2) + (rec.ys[it] - 100).pow(2)) }
            maxDeviation = rs.maxOf { abs(it - 100.0) }
            assertTrue("n=$n desviación=$maxDeviation", maxDeviation < 0.6)
        }
    }

    @Test
    fun squareWithoutRoundnessFillsTheArea() {
        val rec = record(false, 4, 0f, 100f, 100f)
        assertEquals(0.0, rec.xs.min(), 0.01); assertEquals(100.0, rec.xs.max(), 0.01)
        assertEquals(0.0, rec.ys.min(), 0.01); assertEquals(100.0, rec.ys.max(), 0.01)
    }

    @Test
    fun shapeIsNotStretchedInANonSquareArea() {
        val rec = record(false, 6, 0f, 300f, 100f)
        val width = rec.xs.max() - rec.xs.min()
        val height = rec.ys.max() - rec.ys.min()
        // Hexágono regular de arista plana arriba: ancho/alto = 2 / sqrt(3).
        assertEquals(2.0 / sqrt(3.0), width / height, 0.01)
        assertEquals(150.0, (rec.xs.min() + rec.xs.max()) / 2, 0.5) // centrado
    }

    @Test
    fun sanitizedClampsOutOfRangeValues() {
        val dirty = CoverDesign(id = "x", name = "   ", sides = 99, roundness = 5f, transparency = 3f).sanitized()
        assertEquals(CoverDesign.DEFAULT_NAME, dirty.name)
        assertEquals(CoverShapeGeometry.MAX_SIDES, dirty.sides)
        assertEquals(1f, dirty.roundness, 0f)
        assertEquals(CoverDesign.MAX_TRANSPARENCY, dirty.transparency, 0f)

        val nan = CoverDesign(id = "y", sides = 1, roundness = Float.NaN, transparency = Float.NaN).sanitized()
        assertEquals(CoverShapeGeometry.MIN_SIDES, nan.sides)
        assertEquals(0f, nan.roundness, 0f)
        assertEquals(0f, nan.transparency, 0f)
    }

    @Test
    fun everyFigureIsClosedFiniteAndInsideTheAreaForAnyDepthAndRotation() {
        for (figure in CoverFigure.entries) for (n in listOf(3, 6, 12)) for (depth in listOf(0f, 0.5f, 1f))
            for (r in listOf(0f, 0.5f, 1f)) for (deg in listOf(0f, 45f, 200f)) {
                val rec = Recorder().also {
                    CoverShapeGeometry.build(it, figure, n, r, depth, deg, 200f, 200f)
                }
                val msg = "figure=$figure n=$n depth=$depth r=$r deg=$deg"
                assertTrue(msg, rec.closed)
                assertFalse(msg, rec.invalid)
                assertTrue(msg, rec.xs.min() >= -0.5 && rec.ys.min() >= -0.5)
                assertTrue(msg, rec.xs.max() <= 200.5 && rec.ys.max() <= 200.5)
            }
    }

    @Test
    fun rotatingASquare45DegreesGivesADiamondThatStillFillsTheArea() {
        val rec = Recorder().also {
            CoverShapeGeometry.build(it, CoverFigure.POLYGON, 4, 0f, 0.5f, 45f, 100f, 100f)
        }
        assertEquals(0.0, rec.xs.min(), 0.01); assertEquals(100.0, rec.xs.max(), 0.01)
        assertEquals(0.0, rec.ys.min(), 0.01); assertEquals(100.0, rec.ys.max(), 0.01)
        // Las puntas quedan en el centro de cada borde (no en las esquinas).
        assertTrue(rec.xs.indices.any { abs(rec.xs[it] - 50) < 0.01 && abs(rec.ys[it]) < 0.01 })
    }

    @Test
    fun oldDesignsKeepTheirLookThroughTheLegacyOverload() {
        val legacy = record(true, 5, 0.3f)
        val modern = Recorder().also {
            CoverShapeGeometry.build(it, CoverFigure.STAR, 5, 0.3f, CoverShapeGeometry.DEFAULT_DEPTH, 0f, 200f, 200f)
        }
        assertEquals(legacy.xs, modern.xs)
        assertEquals(legacy.ys, modern.ys)
    }

    @Test
    fun sanitizedClampsDepthAndRotation() {
        val dirty = CoverDesign(id = "x", depth = 7f, rotation = -90f).sanitized()
        assertEquals(1f, dirty.depth, 0f)
        assertEquals(270f, dirty.rotation, 0.001f)

        val nan = CoverDesign(id = "y", depth = Float.NaN, rotation = Float.NaN).sanitized()
        assertEquals(CoverDesign.DEFAULT_DEPTH, nan.depth, 0f)
        assertEquals(0f, nan.rotation, 0f)
    }
}
