package com.music.musicflame.ui.blobatar

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Paridad del núcleo (hash, rasgos, paleta) contra los vectores de referencia exportados del Blobatar
 * original (TypeScript 2.4.0). Lo marcado "exact" en el fixture debe coincidir EXACTO.
 */
class BlobatarCoreParityTest {

    private val vectors: JSONObject by lazy {
        val stream = javaClass.getResourceAsStream("/blobatar/reference-vectors-core.json")
            ?: error("Falta src/test/resources/blobatar/reference-vectors-core.json")
        JSONObject(stream.bufferedReader(Charsets.UTF_8).use { it.readText() })
    }

    private fun closeEnough(actual: Double, expected: Double, tol: Double = 1e-9): Boolean {
        if (actual == expected) return true
        if (actual.isNaN() && expected.isNaN()) return true
        val scale = maxOf(1.0, maxOf(Math.abs(actual), Math.abs(expected)))
        return Math.abs(actual - expected) <= tol * scale
    }

    @Test
    fun normalizacionCoincideByteAByte() {
        val hashes = vectors.getJSONArray("hash")
        for (i in 0 until hashes.length()) {
            val m = hashes.getJSONObject(i)
            assertEquals("normalizeSeed(${m.getString("seed")})", m.getString("normalized"), BlobatarHash.normalizeSeed(m.getString("seed")))
        }
    }

    @Test
    fun estadosDeSemillaCoincidenExacto() {
        val hashes = vectors.getJSONArray("hash")
        for (i in 0 until hashes.length()) {
            val m = hashes.getJSONObject(i)
            // El fixture guarda el estado como int32 con signo (el `| 0` de JS).
            assertEquals("seedState(${m.getString("seed")})", m.getLong("state").toInt(), BlobatarHash.seedState(m.getString("seed")))
        }
    }

    @Test
    fun streamsCoincidenExacto() {
        val hashes = vectors.getJSONArray("hash")
        var checked = 0
        for (i in 0 until hashes.length()) {
            val m = hashes.getJSONObject(i)
            val state = BlobatarHash.seedState(m.getString("seed"))
            val streams = m.getJSONObject("streams")
            for (key in streams.keys()) {
                val expected = streams.getDouble(key)
                val actual = BlobatarHash.stream(state, key)
                assertTrue("stream(${m.getString("seed")}, $key): $actual != $expected", actual == expected)
                checked++
            }
        }
        assertTrue("se esperaban muchos streams", checked > 1000)
    }

    private fun overridesFrom(json: JSONObject): Map<String, Any> {
        val out = LinkedHashMap<String, Any>()
        for (k in json.keys()) {
            val v = json.get(k)
            out[k] = if (v is JSONArray) List(v.length()) { v.getDouble(it) } else json.getDouble(k)
        }
        return out
    }

    @Test
    fun overridesCoincidenExacto() {
        val list = vectors.getJSONArray("overrides")
        for (i in 0 until list.length()) {
            val m = list.getJSONObject(i)
            val ov = overridesFrom(m.getJSONObject("overrides"))
            val t = Traits.forSeed(m.getString("seed"), overrides = ov.ifEmpty { null })
            val values = m.getJSONObject("values")
            for (key in values.keys()) {
                val expected = values.getDouble(key)
                val actual = t(key)
                assertTrue("${m.getString("seed")} $key: $actual != $expected", actual == expected)
            }
        }
    }

    @Test
    fun paletaHexExactaYRampaConTolerancia() {
        val list = vectors.getJSONArray("palette")
        for (i in 0 until list.length()) {
            val m = list.getJSONObject(i)
            val hue = m.getDouble("hue")
            val tone = m.getDouble("tone")
            val hex = m.getJSONObject("hex")

            val enforced = BlobatarColor.palette(hue, true, tone)
            for (key in listOf("bg", "head", "eye")) {
                assertEquals("$key hex h=$hue t=$tone", hex.getString(key), enforced[key])
            }

            val unenforced = BlobatarColor.ramp(hue, false, tone)
            val raw = m.getJSONObject("rampUnenforced")
            for (key in listOf("bg", "head", "eye")) {
                val e = raw.getJSONObject(key)
                val a = unenforced.getValue(key)
                assertTrue("$key.l h=$hue t=$tone", closeEnough(a.l, e.getDouble("l")))
                assertTrue("$key.c h=$hue t=$tone", closeEnough(a.c, e.getDouble("c")))
                assertTrue("$key.h h=$hue t=$tone", closeEnough(a.h, e.getDouble("h")))
            }
        }
    }

    // ---- Propiedades propias del port ----

    @Test
    fun mismoNombreMismoVectorYNombresDistintosDifieren() {
        val keys = listOf("shape", "hue", "tone", "body.r", "eye.gap")
        fun vec(seed: String) = keys.map { Traits.forSeed(seed)(it) }
        assertEquals(vec("alain"), vec("alain"))
        assertNotEquals(vec("alain"), vec("bob"))
        assertEquals(vec("Alain@Example.com"), vec("  ALAIN@EXAMPLE.COM  "))
    }

    @Test
    fun sustitutoSueltoSeCodificaComoFFFD() {
        // Igual que TextEncoder de JS: un UTF-16 inválido no debe convertirse en '?'.
        val bytes = BlobatarHash.utf8("a\uD800b")
        assertEquals(5, bytes.size)
        assertEquals(0xEF, bytes[1].toInt() and 0xff)
        assertEquals(0xBF, bytes[2].toInt() and 0xff)
        assertEquals(0xBD, bytes[3].toInt() and 0xff)
    }

    @Test
    fun valoresFueraDeRangoDeOverrideSeRecortan() {
        val t = Traits.forSeed("alain", overrides = mapOf("a" to 5.0, "b" to -3.0, "c" to Double.NaN))
        assertEquals(0.999999, t("a"), 0.0)
        assertEquals(0.0, t("b"), 0.0)
        assertEquals(0.0, t("c"), 0.0)
    }
}
