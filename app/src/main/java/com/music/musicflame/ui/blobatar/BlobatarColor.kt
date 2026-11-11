/*
 * Port a Kotlin de Blobatar (https://github.com/Alain00/blobatar), motor "generation-2",
 * versión de referencia 2.4.0. Basado en packages/blobatar/src/color.ts y en el port a Dart.
 * Licencia MIT, Copyright (c) 2026 Alain. Texto completo en LICENSE.blobatar (esta carpeta).
 */
package com.music.musicflame.ui.blobatar

/** Un color en OKLCh. */
data class Oklch(val l: Double, val c: Double, val h: Double) {
    fun withL(l: Double) = Oklch(l, c, h)
}

/** Hacia dónde va una pose que tiñe el cuerpo (matiz, luminosidad, cuánto del camino recorre, croma mínimo). */
data class Tint(val h: Double, val l: Double, val pull: Double, val c: Double)

/**
 * Construcción de la paleta. El matiz es lo único que controla la semilla; luminosidad y croma son
 * constantes de autor (por eso todos los blobatars parecen del mismo diseñador). Los colores se resuelven
 * a hex y el contraste se exige contra la luminancia sRGB real.
 *
 * Se usa StrictMath (fdlibm) para sin/cos/pow/cbrt/atan2: da el mismo resultado en todos los teléfonos
 * y es el más cercano al motor de referencia, así un mismo nombre produce el mismo color en cualquier equipo.
 */
object BlobatarColor {
    const val BG = "bg"
    const val HEAD = "head"
    const val EYE = "eye"

    /** `Math.round` de JS: mitad hacia +infinito. */
    fun jsRound(v: Double): Double = Math.floor(v + 0.5)

    private fun cbrt(x: Double): Double = StrictMath.cbrt(x)
    private fun hypot2(x: Double, y: Double): Double = Math.sqrt(x * x + y * y)

    private class Rgb(val r: Double, val g: Double, val b: Double)

    /** OKLCh -> sRGB lineal. Los componentes pueden salir de [0,1] (fuera de gama). */
    private fun toLinear(color: Oklch): Rgb {
        val rad = color.h * Math.PI / 180
        val a = color.c * StrictMath.cos(rad)
        val b = color.c * StrictMath.sin(rad)

        val l_ = color.l + 0.3963377774 * a + 0.2158037573 * b
        val m_ = color.l - 0.1055613458 * a - 0.0638541728 * b
        val s_ = color.l - 0.0894841775 * a - 1.291485548 * b

        val lL = l_ * l_ * l_
        val mM = m_ * m_ * m_
        val sS = s_ * s_ * s_

        return Rgb(
            4.0767416621 * lL - 3.3077115913 * mM + 0.2309699292 * sS,
            -1.2684380046 * lL + 2.6097574011 * mM - 0.3413193965 * sS,
            -0.0041960863 * lL - 0.7034186147 * mM + 1.707614701 * sS
        )
    }

    private fun inGamut(c: Rgb): Boolean =
        c.r >= -1e-4 && c.r <= 1 + 1e-4 &&
            c.g >= -1e-4 && c.g <= 1 + 1e-4 &&
            c.b >= -1e-4 && c.b <= 1 + 1e-4

    /** Resuelve a sRGB lineal dentro de gama, bajando el croma si hace falta (recortar canales cambiaría el matiz). */
    private fun resolve(color: Oklch): Rgb {
        var rgb = toLinear(color)
        if (!inGamut(rgb)) {
            var lo = 0.0
            var hi = color.c
            for (i in 0 until 12) {
                val mid = (lo + hi) / 2
                if (inGamut(toLinear(Oklch(color.l, mid, color.h)))) lo = mid else hi = mid
            }
            rgb = toLinear(Oklch(color.l, lo, color.h))
        }
        return Rgb(rgb.r.coerceIn(0.0, 1.0), rgb.g.coerceIn(0.0, 1.0), rgb.b.coerceIn(0.0, 1.0))
    }

    /** Luminancia relativa WCAG (los valores de [resolve] ya son sRGB lineal). */
    private fun luminance(color: Oklch): Double {
        val rgb = resolve(color)
        return 0.2126 * rgb.r + 0.7152 * rgb.g + 0.0722 * rgb.b
    }

    fun contrast(a: Oklch, b: Oklch): Double {
        val x = luminance(a)
        val y = luminance(b)
        return (Math.max(x, y) + 0.05) / (Math.min(x, y) + 0.05)
    }

    /**
     * Aleja la luminosidad de [fg] de [bg] hasta superar [min]. Primero camina en la dirección en la que ya
     * se inclina; si se acaba el rango, prueba la otra; al final cae a negro o blanco puro.
     */
    fun ensureContrast(fg: Oklch, bg: Oklch, min: Double): Oklch {
        if (contrast(fg, bg) >= min) return fg

        val lean = if (fg.l >= bg.l) 1.0 else -1.0
        for (dir in doubleArrayOf(lean, -lean)) {
            var l = fg.l
            for (i in 0 until 60) {
                l = Math.min(1.0, Math.max(0.0, l + dir * 0.02))
                val probe = Oklch(l, fg.c, fg.h)
                if (contrast(probe, bg) >= min) return probe
                if (l == 0.0 || l == 1.0) break
            }
        }

        val black = Oklch(0.0, 0.0, fg.h)
        val white = Oklch(1.0, 0.0, fg.h)
        return if (contrast(black, bg) >= contrast(white, bg)) black else white
    }

    private fun hex2(v: Int): String = if (v < 16) "0" + Integer.toHexString(v) else Integer.toHexString(v)

    fun toHex(color: Oklch): String {
        val rgb = resolve(color)
        val out = StringBuilder("#")
        for (v in doubleArrayOf(rgb.r, rgb.g, rgb.b)) {
            val s = if (v <= 0.0031308) 12.92 * v else 1.055 * StrictMath.pow(v, 1 / 2.4) - 0.055
            out.append(hex2(jsRound(s * 255).toInt()))
        }
        return out.toString()
    }

    /** sRGB hex -> OKLCh. La única vuelta al espacio de color desde una paleta ya serializada. */
    fun fromHex(hex: String): Oklch {
        val n = hex.substring(1).toInt(16)
        fun decode(v: Int): Double {
            val s = v / 255.0
            return if (s <= 0.04045) s / 12.92 else StrictMath.pow((s + 0.055) / 1.055, 2.4)
        }
        val r = decode((n shr 16) and 255)
        val g = decode((n shr 8) and 255)
        val b = decode(n and 255)

        val l = cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
        val m = cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
        val s = cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)

        val a = 1.9779984951 * l - 2.428592205 * m + 0.4505937099 * s
        val bb = 0.0259040371 * l + 0.7827717662 * m - 0.808675766 * s

        return Oklch(
            0.2104542553 * l + 0.793617785 * m - 0.0040720468 * s,
            hypot2(a, bb),
            StrictMath.atan2(bb, a) * 180 / Math.PI
        )
    }

    /** Mezcla dos colores en OKLab (`color-mix(in oklab, a, b t)`): se interpola a/b cartesiano, no c/h polar. */
    fun mix(a: Oklch, b: Oklch, t: Double): Oklch {
        fun rad(v: Double) = v * Math.PI / 180
        val ax = a.c * StrictMath.cos(rad(a.h))
        val ay = a.c * StrictMath.sin(rad(a.h))
        val bx = b.c * StrictMath.cos(rad(b.h))
        val by = b.c * StrictMath.sin(rad(b.h))
        val x = ax + (bx - ax) * t
        val y = ay + (by - ay) * t
        return Oklch(
            a.l + (b.l - a.l) * t,
            hypot2(x, y),
            StrictMath.atan2(y, x) * 180 / Math.PI
        )
    }

    /** [mix] entre dos colores serializados, serializado. */
    fun mixHex(a: String, b: String, t: Double): String = toHex(mix(fromHex(a), fromHex(b), t))

    /** El otro tipo de mezcla (en sRGB, como `transition: fill` de CSS): cómo viaja un relleno mientras la pose cambia. */
    fun fadeHex(a: String, b: String, t: Double): String {
        val out = StringBuilder("#")
        var i = 1
        while (i < 7) {
            val from = a.substring(i, i + 2).toInt(16)
            val to = b.substring(i, i + 2).toInt(16)
            val v = jsRound(from + (to - from) * t).toInt()
            out.append(hex2(v))
            i += 2
        }
        return out.toString()
    }

    /** Rojo (enojo): solo el 60% del camino en luminosidad para que sobreviva el tono. */
    val hot = Tint(27.0, 0.58, 0.6, 0.18)
    val rose = Tint(358.0, 0.72, 0.55, 0.16)
    val blush = Tint(12.0, 0.84, 0.4, 0.1)
    val bile = Tint(142.0, 0.66, 0.6, 0.13)

    /** Todos los destinos de tinte sobre los que se sostiene la garantía de contraste. */
    val tints: List<Pair<String, Tint>> = listOf("hot" to hot, "rose" to rose, "blush" to blush, "bile" to bile)

    /** Un poco más de los 4.5:1 exigidos; el margen es solo por la cuantización a 8 bits. */
    private const val TINT_FLOOR = 4.55

    /**
     * La paleta hacia la que va una pose que tiñe, dada la paleta desde la que parte. El cuerpo llega a
     * medio camino del destino y el ojo se empuja hasta que TODO punto de la mezcla supera el piso.
     */
    fun tinted(head: String, eye: String, t: Tint): Pair<String, String> {
        val base = fromHex(head)
        val baseEye = fromHex(eye)

        var hotHead = Oklch(base.l + (t.l - base.l) * t.pull, Math.max(base.c, t.c), t.h)
        hotHead = ensureContrast(hotHead, DARK_SURFACE, SURFACE_FLOOR)

        var hotEye = ensureContrast(baseEye, hotHead, TINT_FLOOR)

        val dir = if (hotEye.l >= hotHead.l) 1.0 else -1.0
        val headHex = toHex(hotHead)
        for (pass in 0 until 40) {
            val eyeHex = toHex(hotEye)
            var worst = Double.POSITIVE_INFINITY
            for (i in 0..10) {
                val tt = i / 10.0
                worst = Math.min(
                    worst,
                    contrast(fromHex(mixHex(eye, eyeHex, tt)), fromHex(mixHex(head, headHex, tt)))
                )
            }
            if (worst >= TINT_FLOOR) return headHex to eyeHex
            val l = Math.min(1.0, Math.max(0.0, hotEye.l + dir * 0.02))
            if (l == hotEye.l) return headHex to eyeHex
            hotEye = hotEye.withL(l)
        }
        return headHex to toHex(hotEye)
    }

    private class Tone(val edge: Double, val l: Double, val c: Double)

    // Los umbrales son acumulativos: dominan los tonos pálidos y medios; el casi negro es raro.
    private val tones = listOf(
        Tone(0.2, 0.86, 0.085), // pastel
        Tone(0.36, 0.9, 0.028), // neutro pálido
        Tone(0.62, 0.73, 0.135), // medio
        Tone(0.8, 0.62, 0.165), // intenso
        Tone(0.93, 0.87, 0.16), // brillante
        Tone(1.0, 0.34, 0.035) // tinta (no más oscuro que una superficie oscura)
    )

    private fun toneAt(v: Double): Tone = tones.firstOrNull { v < it.edge } ?: tones.first()

    /** La superficie más oscura sobre la que se espera un blob sin fondo (~#0a0a0b) y la razón mínima contra ella. */
    val DARK_SURFACE = Oklch(0.145, 0.0, 0.0)
    const val SURFACE_FLOOR = 1.5

    private fun rampRaw(hue: Double, tone: Double): LinkedHashMap<String, Oklch> {
        val t = toneAt(tone)
        val head = ensureContrast(Oklch(t.l, t.c, hue), DARK_SURFACE, SURFACE_FLOOR)
        return linkedMapOf(
            BG to Oklch(0.965, 0.01, hue),
            HEAD to head,
            // La polaridad sigue al cuerpo: ojos oscuros sobre cuerpo claro y al revés.
            EYE to if (head.l >= 0.5) Oklch(0.17, 0.02, hue) else Oklch(0.97, 0.012, hue)
        )
    }

    /** Razones mínimas de contraste (primer plano, fondo, razón), aplicadas en orden. */
    val floors: List<Triple<String, String, Double>> = listOf(
        Triple(HEAD, BG, 1.25),
        Triple(EYE, HEAD, 4.5)
    )

    private val SHORT_HEX = Regex("^#([0-9a-f])([0-9a-f])([0-9a-f])$", RegexOption.IGNORE_CASE)
    private val LONG_HEX = Regex("^#[0-9a-f]{6}$", RegexOption.IGNORE_CASE)

    /** Un color de placa `#rgb`/`#rrggbb` expandido a `#rrggbb`, o null si no es válido. */
    fun plateHex(color: String): String? {
        val m = SHORT_HEX.matchEntire(color)
        val hex = if (m == null) color else {
            val g = m.groupValues
            "#${g[1]}${g[1]}${g[2]}${g[2]}${g[3]}${g[3]}"
        }
        return if (LONG_HEX.matches(hex)) hex else null
    }

    /** La placa del usuario como el gris neutro de la misma luminancia WCAG (un gris tiene luminancia l³). */
    fun plateGrey(hex: String): Oklch {
        val n = hex.substring(1).toInt(16)
        var y = 0.0
        for ((shift, w) in listOf(16 to 0.2126, 8 to 0.7152, 0 to 0.0722)) {
            val s = ((n shr shift) and 255) / 255.0
            y += w * (if (s <= 0.04045) s / 12.92 else StrictMath.pow((s + 0.055) / 1.055, 2.4))
        }
        return Oklch(StrictMath.pow(y, 1 / 3.0), 0.0, 0.0)
    }

    /**
     * La paleta en OKLCh antes de pasar a hex. [bg] es una placa del usuario (`#rgb`/`#rrggbb`) que
     * sustituye al fondo de autor ANTES de aplicar los pisos de contraste.
     */
    fun ramp(hue: Double, enforce: Boolean = true, tone: Double = 0.0, bg: String? = null): LinkedHashMap<String, Oklch> {
        val r = rampRaw(hue, tone)
        val hex = if (bg == null) null else plateHex(bg)
        if (hex != null) r[BG] = plateGrey(hex)
        if (enforce) {
            for ((fg, back, min) in floors) {
                r[fg] = ensureContrast(r.getValue(fg), r.getValue(back), min)
            }
        }
        return r
    }

    /** La paleta de autor en hex: la semilla elige matiz y tono y todo lo demás sale de ahí. */
    fun palette(hue: Double, enforce: Boolean = true, tone: Double = 0.0, bg: String? = null): LinkedHashMap<String, String> {
        val r = ramp(hue, enforce, tone, bg)
        val hex = if (bg == null) null else plateHex(bg)
        val out = LinkedHashMap<String, String>()
        for ((k, v) in r) out[k] = toHex(v)
        if (hex != null) out[BG] = hex
        return out
    }
}
