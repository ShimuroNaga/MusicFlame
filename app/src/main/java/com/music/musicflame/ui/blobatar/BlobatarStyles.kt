/*
 * Port a Kotlin de Blobatar (https://github.com/Alain00/blobatar), motor "generation-2",
 * versión de referencia 2.4.0. Basado en packages/blobatar/src/styles/{shapes,compose,blob}.ts y en el
 * port a Dart. Licencia MIT, Copyright (c) 2026 Alain. Texto completo en LICENSE.blobatar (esta carpeta).
 */
package com.music.musicflame.ui.blobatar

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** El cuerpo central que dibuja y parcha cada silueta. Mutable a propósito: las siluetas lo ajustan. */
class BlobBody(
    var cx: Double,
    var cy: Double,
    var rx: Double,
    var ry: Double,
    var n: Double,
    var rot: Double,
    var radii: List<Double>,
    /** Solo polígonos. */
    var sides: Int? = null,
    var round: Double? = null
)

/** Una región elíptica: la cara, o la tapa de una decoración. */
class BlobEllipse(var cx: Double, var cy: Double, var rx: Double, var ry: Double)

/** Un círculo unido al cuerpo (pétalos, tapas, lóbulos, rayos del sol). */
data class Petal(val cx: Double, val cy: Double, val r: Double)

/** Lo que acumula el gancho `decorate` de una silueta. */
class Deco {
    val petals = ArrayList<Petal>()
    val extra = ArrayList<BlobPath>()
}

/**
 * Una silueta: todo lo que el layout necesita para dibujarla y nada sobre cuándo toca (eso es de la tabla
 * de bandas). [core] es cuánto del cuadro ocupa el cuerpo.
 */
abstract class BlobShape(val name: String, val core: Double) {
    /** Parcha el cuerpo antes de medir la cara. */
    open fun body(t: Traits, b: BlobBody) {}

    /** La región donde deben caber los ojos. Null = el cuerpo mismo. */
    open fun face(b: BlobBody): BlobEllipse? = null

    open fun decorate(t: Traits, b: BlobBody, out: Deco) {}

    /** El trazo del cuerpo. Null = superelipse. */
    open fun path(b: BlobBody): BlobPath? = null
}

private fun polyPath(b: BlobBody): BlobPath = BlobatarShapes.polygon(
    Polygon(b.cx, b.cy, b.rx, b.ry, sides = b.sides!!, round = b.round!!, rot = b.rot)
)

private fun splinePath(b: BlobBody): BlobPath = BlobatarShapes.blobPath(b.cx, b.cy, b.rx, b.ry, b.radii, b.rot)

private fun shrunk(b: BlobBody, k: Double) = BlobEllipse(b.cx, b.cy, b.rx * k, b.ry * k)

private fun splineFace(b: BlobBody): BlobEllipse {
    var m = b.radii[0]
    for (r in b.radii) if (r < m) m = r
    return shrunk(b, m * 0.95)
}

private fun cos(v: Double) = StrictMath.cos(v)
private fun sin(v: Double) = StrictMath.sin(v)
private const val PI = Math.PI

/** La silueta de todos los días. n~4 squircle, todo por semilla. */
object RoundShape : BlobShape("round", 1.0)

/** El guijarro dibujado a mano. */
object OrganicShape : BlobShape("organic", 0.98) {
    override fun path(b: BlobBody) = splinePath(b)
    override fun face(b: BlobBody) = splineFace(b)
}

/** `round`, cuadrado e inclinado. */
object BoxyShape : BlobShape("boxy", 0.86) {
    override fun body(t: Traits, b: BlobBody) {
        b.n = t.numIn("body.n", 3.4, 6.0)
        b.rot = t.numIn("body.rot", -20.0, 20.0)
    }
}

object CapsuleShape : BlobShape("capsule", 1.02) {
    override fun body(t: Traits, b: BlobBody) {
        b.ry *= t.numIn("capsule.squat", 0.55, 0.68)
    }

    override fun face(b: BlobBody) = shrunk(b, 0.94)

    override fun decorate(t: Traits, b: BlobBody, out: Deco) {
        for (s in intArrayOf(-1, 1)) out.petals.add(Petal(b.cx + s * (b.rx - b.ry), b.cy, b.ry))
    }

    override fun path(b: BlobBody) = BlobatarShapes.box(b.cx, b.cy, b.rx - b.ry, b.ry)
}

object NubShape : BlobShape("nub", 0.88) {
    override fun decorate(t: Traits, b: BlobBody, out: Deco) {
        val count = t.intIn("nub.n", 1, 2)
        for (i in 0 until count) {
            val a = t.numIn("nub.a$i", 0.0, 2 * PI)
            out.petals.add(
                Petal(
                    b.cx + cos(a) * b.rx * 0.88,
                    b.cy + sin(a) * b.rx * 0.88,
                    b.rx * t.numIn("nub.r$i", 0.24, 0.4)
                )
            )
        }
    }
}

/** `organic`, con lóbulos en la mitad superior. */
object CloudShape : BlobShape("cloud", 0.78) {
    override fun path(b: BlobBody) = splinePath(b)
    override fun face(b: BlobBody) = splineFace(b)

    override fun decorate(t: Traits, b: BlobBody, out: Deco) {
        val count = t.intIn("cloud.n", 4, 6)
        for (i in 0 until count) {
            val a = PI + (PI * (i + 0.5)) / count
            out.petals.add(
                Petal(
                    b.cx + cos(a) * b.rx * 0.8,
                    b.cy + sin(a) * b.rx * 0.5,
                    b.rx * t.numIn("cloud.r$i", 0.44, 0.62)
                )
            )
        }
    }
}

object DropletShape : BlobShape("droplet", 0.78) {
    override fun body(t: Traits, b: BlobBody) {
        // Bajado lo que añade la punta arriba, para que cabeza y punta queden centradas en el cuadro.
        b.cy += 0.22 * b.ry
        // n fijo en 2: una elipse verdadera, que es la curva a la que la punta es tangente.
        b.n = 2.0
    }

    override fun face(b: BlobBody) = BlobEllipse(b.cx, b.cy + b.ry * 0.05, b.rx * 0.88, b.ry * 0.88)

    override fun decorate(t: Traits, b: BlobBody, out: Deco) {
        out.extra.add(BlobatarShapes.taper(b.cx, b.cy, b.rx, b.ry, t.numIn("droplet.tip", 1.4, 1.65)))
    }
}

object HexagonShape : BlobShape("hexagon", 1.05) {
    override fun path(b: BlobBody) = polyPath(b)
    override fun face(b: BlobBody) = shrunk(b, 0.84)

    override fun body(t: Traits, b: BlobBody) {
        b.sides = 6
        b.rot = t.numIn("body.rot", -12.0, 12.0)
        b.round = t.numIn("poly.round", 0.24, 0.5)
    }
}

object SunShape : BlobShape("sun", 0.7) {
    override fun decorate(t: Traits, b: BlobBody, out: Deco) {
        val count = t.intIn("sun.n", 6, 9)
        val dist = b.rx * t.numIn("sun.dist", 1.0, 1.08)
        val pr = b.rx * t.numIn("sun.r", 0.2, 0.26)
        val off = t.numIn("sun.rot", 0.0, 2 * PI)
        for (i in 0 until count) {
            val a = off + (2 * PI * i) / count
            out.petals.add(Petal(b.cx + cos(a) * dist, b.cy + sin(a) * dist, pr))
        }
    }
}

/** `hexagon` con tres lados y una inclinación más corta para que descanse sobre su base. */
object TriangleShape : BlobShape("triangle", 1.15) {
    override fun path(b: BlobBody) = polyPath(b)

    override fun body(t: Traits, b: BlobBody) {
        b.sides = 3
        b.rot = t.numIn("body.rot", -5.0, 5.0)
        b.round = t.numIn("poly.round", 0.24, 0.5)
    }

    override fun face(b: BlobBody) = BlobEllipse(b.cx, b.cy + b.ry * 0.1, b.rx * 0.54, b.ry * 0.36)
}

/** Un ojo ya ajustado. */
class Eye(var cx: Double, var cy: Double, var rx: Double, var ry: Double, var n: Double, var rot: Double)

/**
 * El layout resuelto de una semilla: todo lo que un renderizador necesita, sin nada de cómo serializarlo.
 * [draw] se conserva como función (igual que el renderizador TS) para trazarse contra los valores vivos del
 * cuerpo al momento de dibujar.
 */
class BlobatarLayout(
    val shape: String,
    val body: BlobBody,
    val face: BlobEllipse,
    val eyes: List<Eye>,
    val petals: List<Petal>,
    val extra: List<BlobPath>,
    val draw: ((BlobBody) -> BlobPath?)?,
    val bodyOffsetY: Double = 0.0
) {
    /** El trazo del cuerpo: el gancho de la silueta o, si no tiene, una superelipse. */
    fun bodyPath(): BlobPath =
        draw?.invoke(body) ?: BlobatarShapes.superellipse(
            Superellipse(body.cx, body.cy, body.rx, body.ry, body.n, body.rot)
        )

    fun eyePaths(): List<BlobPath> = eyes.map { e ->
        BlobatarShapes.superellipse(Superellipse(e.cx, e.cy, e.rx, e.ry, e.n, e.rot))
    }
}

/** `[silueta, borde superior de su banda en [0, 1)]`, en orden. */
typealias Band = Pair<BlobShape, Double>

/** Un estilo compuesto: la tabla de bandas más el ajuste de los ojos. */
class BlobatarStyle(
    val bands: List<Band>,
    val fit: (Traits, BlobBody, BlobEllipse) -> List<Eye>,
    /** Si el estilo trae fondo por defecto. Gen-2 no: el cuerpo va sobre lo que ponga el anfitrión. */
    val background: Boolean = false
) {
    private fun pick(v: Double): BlobShape {
        for ((shape, upTo) in bands) if (v < upTo) return shape
        return bands.last().first
    }

    /** Resuelve los rasgos de una semilla en el layout completo. */
    fun layout(t: Traits): BlobatarLayout {
        val shape = pick(t("shape"))
        val r = t.numIn("body.r", 31.0, 38.0) * shape.core
        val len = t.intIn("body.pts", 6, 8)
        val body = BlobBody(
            cx = 50 + t.jitter("body.x", 1.5),
            cy = 50 + t.jitter("body.y", 1.5),
            rx = r,
            ry = r * t.numIn("body.ratio", 0.92, 1.08),
            n = t.numIn("body.n", 1.9, 2.5),
            rot = 0.0,
            radii = List(len) { i -> 1 + t.jitter("body.r$i", 0.16) }
        )
        shape.body(t, body)

        val face = shape.face(body) ?: BlobEllipse(body.cx, body.cy, body.rx, body.ry)
        val deco = Deco()
        shape.decorate(t, body, deco)

        return BlobatarLayout(
            shape = shape.name,
            body = body,
            face = face,
            eyes = fit(t, body, face),
            petals = deco.petals,
            extra = deco.extra,
            draw = { b -> shape.path(b) }
        )
    }
}

private fun hypot2(x: Double, y: Double): Double = Math.sqrt(x * x + y * y)

/** Ajusta el par de ojos contra la región de la cara, en ambos ejes. */
fun faceFit(t: Traits, b: BlobBody, face: BlobEllipse): List<Eye> {
    val rx = b.rx
    val er0 = t.numIn("eye.rx", 0.075, 0.105) * rx
    val ratio = t.numIn("eye.ratio", 1.9, 3.2)
    val scale = t.numIn("eye.scale", 0.78, 1.24)
    val stretch = t.numIn("eye.stretch", 0.85, 1.18)
    val clearance = t.numIn("eye.gap", 0.1, 0.24) * rx
    val wide = er0 * max(1.0, scale)
    val tall = er0 * ratio * max(1.0, scale * stretch)
    val gap0 = wide + rx * 0.03 + clearance

    val gx = t.jitter("gaze.x", 0.09) * face.rx
    val gy = t.numIn("gaze.y", -0.2, 0.08) * face.ry
    val dy = t.jitter("eye.dy", 0.04) * face.ry
    val reach = hypot2(wide, tall)
    val need = hypot2(
        (abs(gx) + gap0 + reach) / face.rx,
        (abs(gy) + abs(dy) + reach) / face.ry
    )
    val fit = if (need > 0.9) 0.9 / need else 1.0

    val er = er0 * fit
    val eyeRy = er * ratio
    val gap = gap0 * fit
    val room = max(0.0, min(1.0, clearance / tall))
    val bound = min(12.0, StrictMath.asin(room) * 180 / PI)
    val lean = t.numIn("eye.lean", -1.0, 1.0) * bound
    val lean2 = max(-12.0, min(12.0, lean + t.jitter("eye.lean2", 3.5)))

    val cx = face.cx + gx * fit
    val cy = face.cy + gy * fit
    return listOf(
        Eye(cx - gap, cy, er, eyeRy, t.numIn("eye.n", 3.5, 6.0), lean),
        Eye(cx + gap, cy + dy * fit, er * scale, eyeRy * scale * stretch, t.numIn("eye.n", 3.5, 6.0), lean2)
    )
}

object BlobatarStyles {
    /** La tabla de bandas. Los umbrales son parte del contrato congelado. */
    val bands: List<Band> = listOf(
        RoundShape to 0.22,
        OrganicShape to 0.48,
        BoxyShape to 0.6,
        CapsuleShape to 0.7,
        NubShape to 0.79,
        CloudShape to 0.86,
        DropletShape to 0.915,
        HexagonShape to 0.95,
        SunShape to 0.98,
        TriangleShape to 1.0
    )

    /** El estilo compuesto de Blobatar 2. */
    val style = BlobatarStyle(bands, ::faceFit)
}
