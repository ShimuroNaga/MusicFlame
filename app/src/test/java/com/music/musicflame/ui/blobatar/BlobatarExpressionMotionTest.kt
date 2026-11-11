package com.music.musicflame.ui.blobatar

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Paridad de expresiones (poses, geometría y tinte de paleta) contra los vectores exportados del Blobatar
 * original (TypeScript 2.4.0), y del movimiento determinista contra los valores calculados con el original.
 */
class BlobatarExpressionMotionTest {

    private val expressionVectors: JSONObject by lazy {
        val stream = javaClass.getResourceAsStream("/blobatar/reference-vectors-expressions.json")
            ?: error("Falta src/test/resources/blobatar/reference-vectors-expressions.json")
        JSONObject(stream.bufferedReader(Charsets.UTF_8).use { it.readText() }).getJSONObject("expressions")
    }

    private fun close(actual: Double, expected: Double, tol: Double = 1e-9): Boolean {
        if (actual == expected) return true
        val scale = maxOf(1.0, maxOf(Math.abs(actual), Math.abs(expected)))
        return Math.abs(actual - expected) <= tol * scale
    }

    private fun optionsFrom(json: JSONObject, expression: Expression): BlobatarOptions {
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
            normalize = if (json.has("normalize")) json.getBoolean("normalize") else true,
            expression = expression
        )
    }

    private fun names(): List<String> = expressionVectors.keys().asSequence().toList()

    // ---- Expresiones ----

    @Test
    fun elRepertorioCompletoCoincide() {
        assertEquals(names().toSet(), BlobatarExpressions.all.map { it.name }.toSet())
        assertEquals(14, BlobatarExpressions.all.size)
        // Orden del original. No se compara con las claves del JSON: org.json no garantiza orden.
        assertEquals(
            listOf("idle", "happy", "sad", "mad", "surprised", "wink", "sleepy", "smug", "unsure", "scared", "love", "shy", "sick", "thinking"),
            BlobatarExpressions.all.map { it.name }
        )
    }

    @Test
    fun cadaPoseCoincideExacto() {
        for (name in names()) {
            val expected = expressionVectors.getJSONObject(name).getJSONObject("pose")
            val actual = BlobatarExpressions.byName.getValue(name).pose.toMap()
            assertEquals("canales de $name", expected.keys().asSequence().toSet(), actual.keys)
            for ((k, v) in actual) assertTrue("$name.$k: $v != ${expected.getDouble(k)}", v == expected.getDouble(k))
        }
    }

    @Test
    fun geometriaEstaticaYTinteCoincidenEnTodosLosCasos() {
        var checked = 0
        for (name in names()) {
            val expression = BlobatarExpressions.byName.getValue(name)
            val cases = expressionVectors.getJSONObject(name).getJSONArray("cases")
            for (c in 0 until cases.length()) {
                val e = cases.getJSONObject(c)
                val (layout, palette) = BlobatarRender.partsFor(e.getString("seed"), optionsFrom(e.getJSONObject("options"), expression))
                assertEquals(name, e.getString("shape"), layout.shape)
                assertTrue("$name bodyOffsetY", layout.bodyOffsetY == expression.pose.bdy)
                val eyes = e.getJSONArray("eyes")
                assertEquals(eyes.length(), layout.eyes.size)
                for (i in 0 until eyes.length()) {
                    val x = eyes.getJSONObject(i)
                    val a = layout.eyes[i]
                    assertTrue("$name.eyes[$i].cx", close(a.cx, x.getDouble("cx")))
                    assertTrue("$name.eyes[$i].cy", close(a.cy, x.getDouble("cy")))
                    assertTrue("$name.eyes[$i].rx", close(a.rx, x.getDouble("rx")))
                    assertTrue("$name.eyes[$i].ry", close(a.ry, x.getDouble("ry")))
                    assertTrue("$name.eyes[$i].n", close(a.n, x.getDouble("n")))
                    assertTrue("$name.eyes[$i].rot", close(a.rot, x.getDouble("rot")))
                }
                val pal = e.getJSONObject("palette")
                for (k in listOf("bg", "head", "eye")) assertEquals("$name.palette.$k", pal.getString(k), palette[k])
                checked++
            }
        }
        assertTrue("se esperaban casos", checked >= 14)
    }

    @Test
    fun idleEsIdenticoAOmitirLaExpresion() {
        for (i in 0 until 200) {
            val seed = "idle-$i"
            val (plainLayout, plainPalette) = BlobatarRender.partsFor(seed)
            val (idleLayout, idlePalette) = BlobatarRender.partsFor(seed, BlobatarOptions(expression = BlobatarExpressions.idle))
            assertEquals(plainLayout.bodyPath().toPathData(), idleLayout.bodyPath().toPathData())
            assertEquals(plainLayout.eyePaths().map { it.toPathData() }, idleLayout.eyePaths().map { it.toPathData() })
            assertTrue(idleLayout.bodyOffsetY == 0.0)
            assertEquals(plainPalette, idlePalette)
        }
    }

    @Test
    fun losCanalesDiferencialesSoloAfectanAlSegundoOjo() {
        val base = BlobatarRender.layoutFor("seed-7")
        val wink = BlobatarExpressions.wink
        val posed = BlobatarRender.layoutFor("seed-7", BlobatarOptions(expression = wink))
        assertEquals(wink.pose.esx, posed.eyes[0].rx / base.eyes[0].rx, 1e-12)
        assertEquals(wink.pose.esx + wink.pose.esx2, posed.eyes[1].rx / base.eyes[1].rx, 1e-12)
        assertEquals(wink.pose.esy, posed.eyes[0].ry / base.eyes[0].ry, 1e-12)
        assertEquals(wink.pose.esy + wink.pose.esy2, posed.eyes[1].ry / base.eyes[1].ry, 1e-12)
    }

    @Test
    fun losOjosPosadosNuncaSeFusionanEnUnBarridoDeSemillas() {
        fun reach(e: Eye): Double {
            val angle = e.rot * Math.PI / 180
            return Math.abs(e.rx * StrictMath.cos(angle)) + Math.abs(e.ry * StrictMath.sin(angle))
        }
        for (expression in BlobatarExpressions.all) {
            for (i in 0 until 800) {
                val layout = BlobatarRender.layoutFor("expression-$i", BlobatarOptions(expression = expression))
                val l = layout.eyes[0]
                val r = layout.eyes[1]
                assertTrue("${expression.name} seed=$i", Math.abs(r.cx - l.cx) > reach(l) + reach(r))
            }
        }
    }

    @Test
    fun lasExpresionesQueTinenConservanElContrasteOjoCabeza() {
        for (expression in BlobatarExpressions.all.filter { it.tint != null }) {
            for (i in 0 until 100) {
                val (_, colors) = BlobatarRender.partsFor("tint-$i", BlobatarOptions(expression = expression))
                val ratio = BlobatarColor.contrast(BlobatarColor.fromHex(colors.getValue("eye")), BlobatarColor.fromHex(colors.getValue("head")))
                assertTrue("${expression.name} seed=$i: $ratio", ratio >= 4.5)
            }
        }
    }

    // ---- Movimiento ----

    private val seeds = MotionSeeds(
        phase = 508, bob = 1170, blink = 3619, blinkPhase = 2957, saccade = 6179, saccadePhase = 1350,
        lookX = 1.95, lookY = -0.97, lookMagnitudeX = 1.95, lookMagnitudeY = 0.97
    )

    @Test
    fun lasSemillasDeMovimientoCoincidenConTypeScript() {
        assertEquals(seeds, BlobatarMotion.seedsFor("alain"))
        assertEquals(
            MotionSeeds(
                phase = 455, bob = 328, blink = 5328, blinkPhase = 58, saccade = 5844, saccadePhase = 561,
                lookX = -1.78, lookY = -1.36, lookMagnitudeX = 1.78, lookMagnitudeY = 1.36
            ),
            BlobatarMotion.seedsFor("ada")
        )
    }

    @Test
    fun nombresYCanalesIndependientesNoSeSincronizan() {
        val all = listOf("alain", "ada", "grace", "linus").map { BlobatarMotion.seedsFor(it) }
        assertEquals(4, all.map { it.phase }.toSet().size)
        for (s in all) {
            assertNotEquals(s.phase, s.bob)
            assertTrue(s.blink in 3500..6500)
            assertTrue(s.saccade in 4200..7600)
        }
    }

    private fun expectFrame(
        f: MotionFrame, shake: Pair<Double, Double>, breathe: Pair<Double, Double>, bob: Double,
        saccade: Pair<Double, Double>, thinking: Double, blink: Double, wrap: List<Double>
    ) {
        fun eq(what: String, a: Double, b: Double) = assertTrue("$what: $a != $b", Math.abs(a - b) <= 1e-12)
        eq("shake.x", f.shake.first, shake.first); eq("shake.y", f.shake.second, shake.second)
        eq("breathe.x", f.breathe.first, breathe.first); eq("breathe.y", f.breathe.second, breathe.second)
        eq("bob", f.bob, bob)
        eq("saccade.x", f.saccade.first, saccade.first); eq("saccade.y", f.saccade.second, saccade.second)
        eq("thinking", f.thinkingPhase, thinking)
        eq("blink", f.blink, blink)
        eq("wrap.mx", f.wrap.magnitudeX, wrap[0]); eq("wrap.side", f.wrap.side, wrap[1])
        eq("wrap.sy", f.wrap.scaleY, wrap[2]); eq("wrap.rot", f.wrap.rotation, wrap[3])
    }

    @Test
    fun loscuadrosCoincidenConTypeScriptEnTiemposElegidos() {
        expectFrame(
            BlobatarMotion.motionAt(seeds, 0.0, 1.0, shake = 0.55),
            shake = 0.341 to -0.18700000000000003,
            breathe = 1.001470229356656 to 0.998797085071827,
            bob = -0.27170814718541325, saccade = -1.56 to 0.873, thinking = 1.0, blink = 1.0,
            wrap = listOf(-0.03432, 0.0156, -0.026189999999999998, -1.225692)
        )
        expectFrame(
            BlobatarMotion.motionAt(seeds, 1234.0, 1.0, shake = 0.55),
            shake = 0.28914285714285864 to -0.16500000000000065,
            breathe = 1.015462618780544 to 0.9873487664522821,
            bob = -0.903594833714393, saccade = 1.95 to -0.097, thinking = -0.7249387100885838, blink = 1.0,
            wrap = listOf(-0.042899999999999994, -0.0195, -0.00291, -0.170235)
        )
        expectFrame(
            BlobatarMotion.motionAt(seeds, 5000.0, 1.0, shake = 0.55),
            shake = -0.04871428571429122 to -0.03300000000000846,
            breathe = 1.0000455442519844 to 0.9999627365211037,
            bob = -0.0767684801351858, saccade = 0.0 to 0.0, thinking = -0.9510907709234386, blink = 1.0,
            wrap = listOf(0.0, 0.0, 0.0, 0.0)
        )
    }

    @Test
    fun amplitudCeroQuitaTodoCanalAmbiente() {
        var time = 0
        while (time < 20000) {
            val f = BlobatarMotion.motionAt(seeds, time.toDouble(), 0.0)
            assertEquals(1.0 to 1.0, f.breathe)
            assertTrue(Math.abs(f.bob) < 1e-12)
            assertTrue(Math.abs(f.saccade.first) < 1e-12 && Math.abs(f.saccade.second) < 1e-12)
            assertTrue(f.blink == 1.0)
            assertTrue(Math.abs(f.wrap.magnitudeX) < 1e-12 && Math.abs(f.wrap.side) < 1e-12)
            assertTrue(Math.abs(f.wrap.scaleY) < 1e-12 && Math.abs(f.wrap.rotation) < 1e-12)
            time += 137
        }
    }

    @Test
    fun elParpadeoCierraUnaVezSinInversion() {
        val closed = 0.986 * seeds.blink - seeds.blinkPhase
        assertEquals(0.08, BlobatarMotion.motionAt(seeds, closed, 1.0).blink, 1e-9)
        for (index in 0 until 4000) {
            val time = index * seeds.blink / 4000.0
            assertTrue(BlobatarMotion.motionAt(seeds, time, 1.0).blink > 0)
        }
    }

    @Test
    fun lasSacadasSeSostienenYConservanElEscorzoDelOjoSecundario() {
        val values = List(600) { BlobatarMotion.motionAt(seeds, it * seeds.saccade / 600.0, 1.0).saccade.first }
        var held = 0
        for (i in 1 until values.size) if (Math.abs(values[i] - values[i - 1]) < 1e-9) held++
        assertTrue(held.toDouble() / values.size > 0.8)
        val f = BlobatarMotion.motionAt(seeds, 1234.0, 1.0)
        assertNotEquals(0.0, f.wrap.side)
        assertNotEquals(0.0, f.wrap.rotation)
    }

    @Test
    fun laInterpolacionLlegaAExtremosExactosYLasCurvasCoinciden() {
        val idle = BlobatarExpressions.idle.pose
        val happy = BlobatarExpressions.happy.pose
        assertEquals(idle, BlobatarMotion.lerpPose(idle, happy, 0.0))
        assertEquals(happy, BlobatarMotion.lerpPose(idle, happy, 1.0))
        val half = BlobatarMotion.lerpPose(idle, happy, 0.5)
        assertEquals((1 + happy.esx) / 2, half.esx, 1e-12)
        assertEquals(happy.bdy / 2, half.bdy, 1e-12)
        assertEquals(300, BlobatarMotion.EXPRESSION_ENTER_MS)
        assertEquals(400, BlobatarMotion.EXPRESSION_EXIT_MS)
        assertEquals(0.0, BlobatarMotion.expressionEnterEase(0.0), 1e-12)
        assertEquals(1.0, BlobatarMotion.expressionEnterEase(1.0), 1e-12)
        assertEquals(0.5, BlobatarMotion.easeInOut(0.5), 1e-9)
        assertEquals(0.0, BlobatarMotion.hoverEase(0.0), 1e-12)
        assertEquals(1.0, BlobatarMotion.hoverEase(1.0), 1e-12)
    }
}
