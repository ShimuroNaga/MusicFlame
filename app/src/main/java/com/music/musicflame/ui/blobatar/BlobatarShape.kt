/*
 * Port a Kotlin de Blobatar (https://github.com/Alain00/blobatar), motor "generation-2",
 * versión de referencia 2.4.0. Basado en packages/blobatar/src/shape.ts y en el port a Dart.
 * Licencia MIT, Copyright (c) 2026 Alain. Texto completo en LICENSE.blobatar (esta carpeta).
 */
package com.music.musicflame.ui.blobatar

import kotlin.math.max
import kotlin.math.min

/** Un comando de trazo. Solo absolutos: es lo único que emite esta librería. */
sealed class PathSegment {
    data class MoveTo(val x: Double, val y: Double) : PathSegment()
    data class LineTo(val x: Double, val y: Double) : PathSegment()
    data class CubicTo(val c1x: Double, val c1y: Double, val c2x: Double, val c2y: Double, val x: Double, val y: Double) : PathSegment()
    data class QuadTo(val cx: Double, val cy: Double, val x: Double, val y: Double) : PathSegment()
    data class HorizontalLineTo(val x: Double) : PathSegment()
    data class VerticalLineTo(val y: Double) : PathSegment()
    object ClosePath : PathSegment()
}

/**
 * El formateador `r2`: dos decimales, `-0` colapsado a `0` y sin `.0` en los enteros, idéntico a
 * `String(Number)` de JS. Se arma con aritmética entera (no con Double.toString) para que el texto no
 * dependa de la versión de Java/Android.
 */
fun r2(v: Double): String {
    val scaled = BlobatarColor.jsRound(v * 100)
    if (scaled == 0.0 || scaled.isNaN()) return "0"
    val k = scaled.toLong()
    val abs = if (k < 0) -k else k
    val whole = abs / 100
    val frac = (abs % 100).toInt()
    val sb = StringBuilder()
    if (k < 0) sb.append('-')
    sb.append(whole)
    if (frac != 0) {
        sb.append('.')
        if (frac % 10 == 0) sb.append(frac / 10) else {
            sb.append(frac / 10)
            sb.append(frac % 10)
        }
    }
    return sb.toString()
}

/** Un trazo estructurado (cerrado o abierto) con una serialización fiel. */
class BlobPath(val segments: List<PathSegment>) {
    /** Los datos SVG exactamente como los emite el núcleo TypeScript. */
    fun toPathData(): String {
        val d = StringBuilder()
        for (seg in segments) {
            when (seg) {
                is PathSegment.MoveTo -> d.append("M${r2(seg.x)} ${r2(seg.y)}")
                is PathSegment.LineTo -> d.append("L${r2(seg.x)} ${r2(seg.y)}")
                is PathSegment.CubicTo -> d.append(
                    "C${r2(seg.c1x)} ${r2(seg.c1y)} ${r2(seg.c2x)} ${r2(seg.c2y)} ${r2(seg.x)} ${r2(seg.y)}"
                )
                is PathSegment.QuadTo -> d.append("Q${r2(seg.cx)} ${r2(seg.cy)} ${r2(seg.x)} ${r2(seg.y)}")
                is PathSegment.HorizontalLineTo -> d.append("H${r2(seg.x)}")
                is PathSegment.VerticalLineTo -> d.append("V${r2(seg.y)}")
                PathSegment.ClosePath -> d.append('Z')
            }
        }
        return d.toString()
    }
}

/** Una superelipse: `|x/a|^n + |y/b|^n = 1`. n=2 elipse, n~4 squircle, n grande rectángulo. */
data class Superellipse(
    val cx: Double,
    val cy: Double,
    val rx: Double,
    val ry: Double,
    val n: Double = 4.0,
    val rot: Double = 0.0
)

/** Un polígono regular con esquinas redondeadas. [round] va de 0 (filoso) a 1 (todo curva). */
data class Polygon(
    val cx: Double,
    val cy: Double,
    val rx: Double,
    val ry: Double,
    val sides: Int,
    val round: Double = 0.3,
    val rot: Double = 0.0
)

object BlobatarShapes {
    private const val PI = Math.PI

    private fun cos(v: Double) = StrictMath.cos(v)
    private fun sin(v: Double) = StrictMath.sin(v)

    /** Aproxima cada cuadrante de la superelipse con una sola cúbica que pasa por el punto de 45°. */
    fun superellipse(s: Superellipse): BlobPath {
        val a = s.rx
        val b = s.ry
        // Por encima de n~5.55 el control se sale del radio; se recorta para no inflar la esquina.
        val k = min(1.0, (8 * StrictMath.pow(2.0, -1 / s.n) - 4) / 3)
        val ak = a * k
        val bk = b * k

        val pts = arrayOf(
            a to 0.0, a to bk, ak to b, 0.0 to b,
            -ak to b, -a to bk, -a to 0.0, -a to -bk,
            -ak to -b, 0.0 to -b, ak to -b, a to -bk,
            a to 0.0
        )

        val t = s.rot * PI / 180
        val c = cos(t)
        val sn = sin(t)
        fun at(i: Int): Pair<Double, Double> {
            val (x, y) = pts[i]
            return (s.cx + x * c - y * sn) to (s.cy + x * sn + y * c)
        }

        val segments = ArrayList<PathSegment>()
        val (x0, y0) = at(0)
        segments.add(PathSegment.MoveTo(x0, y0))
        var i = 1
        while (i < 13) {
            val (c1x, c1y) = at(i)
            val (c2x, c2y) = at(i + 1)
            val (px, py) = at(i + 2)
            segments.add(PathSegment.CubicTo(c1x, c1y, c2x, c2y, px, py))
            i += 3
        }
        segments.add(PathSegment.ClosePath)
        return BlobPath(segments)
    }

    /** Arco cuadrático, solo trazado: sonrisas y ceños. */
    fun arc(cx: Double, cy: Double, w: Double, depth: Double): BlobPath = BlobPath(
        listOf(PathSegment.MoveTo(cx - w, cy), PathSegment.QuadTo(cx, cy + depth, cx + w, cy))
    )

    /** Curva cerrada orgánica: radios muestreados en un círculo unidos por un Catmull-Rom cerrado. */
    fun blobPath(cx: Double, cy: Double, rx: Double, ry: Double, radii: List<Double>, rot: Double = 0.0): BlobPath {
        val n = radii.size
        val t0 = rot * PI / 180
        val p = List(n) { i ->
            (cx + rx * radii[i] * cos(t0 + 2 * PI * i / n)) to (cy + ry * radii[i] * sin(t0 + 2 * PI * i / n))
        }
        fun at(i: Int) = p[((i % n) + n) % n]

        val segments = ArrayList<PathSegment>()
        val (sx, sy) = at(0)
        segments.add(PathSegment.MoveTo(sx, sy))
        for (i in 0 until n) {
            val (x0, y0) = at(i - 1)
            val (x1, y1) = at(i)
            val (x2, y2) = at(i + 1)
            val (x3, y3) = at(i + 2)
            segments.add(
                PathSegment.CubicTo(
                    x1 + (x2 - x0) / 6,
                    y1 + (y2 - y0) / 6,
                    x2 - (x3 - x1) / 6,
                    y2 - (y3 - y1) / 6,
                    x2,
                    y2
                )
            )
        }
        segments.add(PathSegment.ClosePath)
        return BlobPath(segments)
    }

    fun polygon(p: Polygon): BlobPath {
        val sides = p.sides
        // La mitad, porque el corte se toma de ambos extremos de cada arista.
        val k = if (p.round > 0) (if (p.round < 1) p.round / 2 else 0.5) else 0.0
        // -90° para que un vértice quede arriba: el triángulo apunta hacia arriba.
        val t0 = p.rot * PI / 180 - PI / 2
        val v = List(sides) { i ->
            (p.cx + p.rx * cos(t0 + 2 * PI * i / sides)) to (p.cy + p.ry * sin(t0 + 2 * PI * i / sides))
        }
        fun at(i: Int) = v[((i % sides) + sides) % sides]

        /** El punto de corte en la arista que sale del vértice [i] hacia el [j]. */
        fun cut(i: Int, j: Int): Pair<Double, Double> {
            val (x0, y0) = at(i)
            val (x1, y1) = at(j)
            return (x0 + (x1 - x0) * k) to (y0 + (y1 - y0) * k)
        }

        val segments = ArrayList<PathSegment>()
        val (m0x, m0y) = cut(0, -1)
        segments.add(PathSegment.MoveTo(m0x, m0y))
        for (i in 0 until sides) {
            val (vx, vy) = at(i)
            val (ex, ey) = cut(i, i + 1)
            segments.add(PathSegment.QuadTo(vx, vy, ex, ey))
            // El tramo recto se omite cuando los cortes se juntan.
            if (k < 0.5) {
                val (lx, ly) = cut(i + 1, i)
                segments.add(PathSegment.LineTo(lx, ly))
            }
        }
        segments.add(PathSegment.ClosePath)
        return BlobPath(segments)
    }

    /** El tramo recto de una cápsula, como caja simple. */
    fun box(cx: Double, cy: Double, rx: Double, ry: Double): BlobPath = BlobPath(
        listOf(
            PathSegment.MoveTo(cx - rx, cy - ry),
            PathSegment.HorizontalLineTo(cx + rx),
            PathSegment.VerticalLineTo(cy + ry),
            PathSegment.HorizontalLineTo(cx - rx),
            PathSegment.ClosePath
        )
    )

    /** La punta de una gota: las dos tangentes desde un ápice hasta la elipse del cuerpo. */
    fun taper(cx: Double, cy: Double, rx: Double, ry: Double, tip: Double): BlobPath {
        val t = max(1.05, tip)
        val tx = rx * Math.sqrt(1 - 1 / (t * t))
        val ty = cy - ry / t
        val apex = cy - t * ry
        val px = tx * 0.14
        val py = ty + 0.86 * (apex - ty)
        return BlobPath(
            listOf(
                PathSegment.MoveTo(cx - tx, ty),
                PathSegment.LineTo(cx - px, py),
                PathSegment.QuadTo(cx, apex, cx + px, py),
                PathSegment.LineTo(cx + tx, ty),
                PathSegment.ClosePath
            )
        )
    }
}
