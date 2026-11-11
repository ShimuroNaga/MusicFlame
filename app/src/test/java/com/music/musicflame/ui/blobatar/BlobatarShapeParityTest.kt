package com.music.musicflame.ui.blobatar

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Paridad de forma y estilos (siluetas, capas, ojos, paleta) contra los 1570 casos exportados del Blobatar
 * original (TypeScript 2.4.0). Los trazos (path strings) y los hex de la paleta deben coincidir EXACTO; la
 * geometría numérica, con tolerancia relativa 1e-9 (trigonometría de librerías distintas, ver meta.comparisonRules).
 */
class BlobatarShapeParityTest {

    private val vectors: JSONObject by lazy {
        val stream = javaClass.getResourceAsStream("/blobatar/reference-vectors-cases.json")
            ?: error("Falta src/test/resources/blobatar/reference-vectors-cases.json")
        JSONObject(stream.bufferedReader(Charsets.UTF_8).use { it.readText() })
    }

    private fun closeEnough(actual: Double, expected: Double, tol: Double = 1e-9): Boolean {
        if (actual == expected) return true
        if (actual.isNaN() && expected.isNaN()) return true
        val scale = maxOf(1.0, maxOf(Math.abs(actual), Math.abs(expected)))
        return Math.abs(actual - expected) <= tol * scale
    }

    private fun optionsFrom(json: JSONObject): BlobatarOptions {
        var traits: Map<String, Any>? = null
        if (json.has("traits") && !json.isNull("traits")) {
            val src = json.getJSONObject("traits")
            val out = LinkedHashMap<String, Any>()
            for (k in src.keys()) {
                val v = src.get(k)
                out[k] = if (v is JSONArray) List(v.length()) { v.getDouble(it) } else src.getDouble(k)
            }
            traits = out
        }
        return BlobatarOptions(
            traits = traits,
            hue = if (json.has("hue") && !json.isNull("hue")) json.getDouble("hue") else null,
            tone = if (json.has("tone") && !json.isNull("tone")) json.getDouble("tone") else null,
            contrast = if (json.has("contrast")) json.getBoolean("contrast") else true,
            normalize = if (json.has("normalize")) json.getBoolean("normalize") else true
        )
    }

    private fun num(o: JSONObject, k: String) = o.getDouble(k)

    private fun checkEllipse(what: String, cx: Double, cy: Double, rx: Double, ry: Double, e: JSONObject): String? {
        if (!closeEnough(cx, num(e, "cx"))) return "$what.cx"
        if (!closeEnough(cy, num(e, "cy"))) return "$what.cy"
        if (!closeEnough(rx, num(e, "rx"))) return "$what.rx"
        if (!closeEnough(ry, num(e, "ry"))) return "$what.ry"
        return null
    }

    private fun checkCase(m: JSONObject): String? {
        val seed = m.getString("seed")
        val opts = optionsFrom(m.getJSONObject("options"))
        val l = BlobatarRender.layoutFor(seed, opts)
        if (l.shape != m.getString("shape")) return "${l.shape} != ${m.getString("shape")}"

        val body = m.getJSONObject("body")
        for (k in listOf("cx", "cy", "rx", "ry", "n", "rot")) {
            val actual = when (k) {
                "cx" -> l.body.cx; "cy" -> l.body.cy; "rx" -> l.body.rx
                "ry" -> l.body.ry; "n" -> l.body.n; else -> l.body.rot
            }
            if (!closeEnough(actual, num(body, k))) return "body.$k"
        }
        val radii = body.getJSONArray("radii")
        if (l.body.radii.size != radii.length()) return "radii.length"
        for (i in 0 until radii.length()) if (!closeEnough(l.body.radii[i], radii.getDouble(i))) return "radii[$i]"
        if (body.has("sides") && !body.isNull("sides") && l.body.sides != body.getInt("sides")) return "sides"
        if (body.has("round") && !body.isNull("round") && !closeEnough(l.body.round!!, body.getDouble("round"))) return "round"

        checkEllipse("face", l.face.cx, l.face.cy, l.face.rx, l.face.ry, m.getJSONObject("face"))?.let { return it }

        val eyes = m.getJSONArray("eyes")
        if (l.eyes.size != eyes.length()) return "eyes.length"
        for (i in 0 until eyes.length()) {
            val e = eyes.getJSONObject(i)
            val a = l.eyes[i]
            checkEllipse("eye$i", a.cx, a.cy, a.rx, a.ry, e)?.let { return it }
            if (!closeEnough(a.n, num(e, "n"))) return "eye$i.n"
            if (!closeEnough(a.rot, num(e, "rot"))) return "eye$i.rot"
        }

        val petals = m.getJSONArray("petals")
        if (l.petals.size != petals.length()) return "petals.length"
        for (i in 0 until petals.length()) {
            val p = petals.getJSONObject(i)
            if (!closeEnough(l.petals[i].cx, num(p, "cx"))) return "petals[$i].cx"
            if (!closeEnough(l.petals[i].cy, num(p, "cy"))) return "petals[$i].cy"
            if (!closeEnough(l.petals[i].r, num(p, "r"))) return "petals[$i].r"
        }

        // Datos de trazo: redondeados por r2 en ambos lados, así que EXACTOS.
        val extra = m.getJSONArray("extra")
        if (l.extra.size != extra.length()) return "extra.length"
        for (i in 0 until extra.length()) if (l.extra[i].toPathData() != extra.getString(i)) return "extra[$i]"
        if (l.bodyPath().toPathData() != m.getString("bodyPath")) return "bodyPath"
        val eyePaths = m.getJSONArray("eyePaths")
        val actualEyes = l.eyePaths()
        for (i in 0 until eyePaths.length()) if (actualEyes[i].toPathData() != eyePaths.getString(i)) return "eyePaths[$i]"

        val pal = m.getJSONObject("palette")
        val resolved = BlobatarRender.partsFor(seed, opts).second
        for (k in listOf("bg", "head", "eye")) if (resolved[k] != pal.getString(k)) return "palette.$k"
        return null
    }

    @Test
    fun elFixtureCubreCadaSiluetaConSuCuota() {
        val counts = vectors.getJSONObject("meta").getJSONObject("shapeCounts")
        var n = 0
        for (k in counts.keys()) {
            assertTrue("banda $k", counts.getInt(k) >= 25)
            n++
        }
        assertEquals(10, n)
    }

    @Test
    fun todosLosCasosCoincidenConElNucleoTypeScript() {
        val cases = vectors.getJSONArray("cases")
        assertEquals(1570, cases.length())
        var failures = 0
        var first: String? = null
        for (i in 0 until cases.length()) {
            val m = cases.getJSONObject(i)
            val reason = checkCase(m)
            if (reason != null) {
                failures++
                if (first == null) first = "seed=${m.getString("seed")} opts=${m.getJSONObject("options")}: $reason"
            }
        }
        assertEquals("fallos: $failures; primero: $first", 0, failures)
    }

    // ---- Propiedades propias del port ----

    @Test
    fun r2NoDependeDeDoubleToString() {
        assertEquals("0", r2(0.0))
        assertEquals("0", r2(-0.001))
        assertEquals("0", r2(-0.0))
        assertEquals("1", r2(1.0))
        assertEquals("1.5", r2(1.504))
        assertEquals("0.05", r2(0.05))
        assertEquals("-0.5", r2(-0.5))
        assertEquals("82.21", r2(82.2149))
        assertEquals("100", r2(99.999))
        assertEquals("-12.3", r2(-12.3))
        assertEquals("0", r2(-0.004))
        assertEquals("-3.46", r2(-3.456))
    }

    @Test
    fun colorDeFondoInvalidoLanza() {
        var thrown = false
        try {
            BlobatarRender.resolve("alain", BlobatarOptions(backgroundColor = "rojo"))
        } catch (e: IllegalArgumentException) {
            thrown = true
        }
        assertTrue(thrown)
    }

    @Test
    fun elFondoSoloEntraAlContrasteSiSeDibuja() {
        val sin = BlobatarRender.resolve("alain", BlobatarOptions(backgroundColor = "#222222")).palette
        val base = BlobatarRender.resolve("alain").palette
        assertEquals(base, sin)
        val con = BlobatarRender.resolve("alain", BlobatarOptions(background = Backdrop.CIRCLE, backgroundColor = "#222222")).palette
        assertEquals("#222222", con["bg"])
    }

    @Test
    fun fondosGeometricosCoincidenConElSvg() {
        val p = BlobatarRender.resolve("alain").palette
        assertEquals("M0 0H100V100H0Z", BlobatarRender.backdropFor(Backdrop.SQUARE, p)!!.path.toPathData())
        assertTrue(BlobatarRender.backdropFor(null, p) == null)
    }
}
