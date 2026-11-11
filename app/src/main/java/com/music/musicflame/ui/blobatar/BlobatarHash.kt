/*
 * Port a Kotlin de Blobatar (https://github.com/Alain00/blobatar), motor "generation-2",
 * versión de referencia 2.4.0. Basado en packages/blobatar/src/hash.ts y en el port a Dart.
 * Licencia MIT, Copyright (c) 2026 Alain. Texto completo en LICENSE.blobatar (esta carpeta).
 *
 * Este archivo es Kotlin puro: no importa nada de Android ni de Compose.
 */
package com.music.musicflame.ui.blobatar

import java.text.Normalizer

/**
 * Hash de la semilla.
 *
 * Dos garantías: (1) avalancha — "alain" y "alaim" dan blobatars sin relación visual (finalizador de
 * murmur3); (2) streaming — la semilla se hashea una vez y cada clave de rasgo continúa desde ese
 * estado, así los rasgos son independientes entre sí.
 *
 * Se reproduce la semántica uint32 de JavaScript. En Kotlin, la multiplicación de Int ya es
 * Math.imul (32 bits con desborde), así que no hace falta descomponer en 16 bits como en Dart.
 */
object BlobatarHash {
    private const val SEP = 0xff
    private val MUL_FEED = 3432918353u.toInt()
    private val MUL_A = 2246822507u.toInt()
    private val MUL_B = 3266489909u.toInt()

    /** `Math.imul` de JavaScript. */
    fun imul(a: Int, b: Int): Int = a * b

    private fun feedByte(h0: Int, byte: Int): Int {
        var h = imul(h0 xor byte, MUL_FEED)
        h = (h shl 13) or (h ushr 19)
        return h
    }

    private fun feed(h0: Int, bytes: ByteArray): Int {
        var h = h0
        for (b in bytes) h = feedByte(h, b.toInt() and 0xff)
        return h
    }

    /** fmix32 de murmur3 — biyección sobre uint32. Devuelve el valor sin signo en un Long. */
    private fun finalize(h0: Int): Long {
        var h = imul(h0 xor (h0 ushr 16), MUL_A)
        h = imul(h xor (h ushr 13), MUL_B)
        return (h xor (h ushr 16)).toLong() and 0xFFFFFFFFL
    }

    /**
     * Normaliza la semilla para que entradas que una persona considera iguales den el mismo hash:
     * NFC, recortar y pasar a minúsculas (con la semántica de JavaScript).
     */
    fun normalizeSeed(seed: String): String = jsToLower(jsTrim(Normalizer.normalize(seed, Normalizer.Form.NFC)))

    /** `String.prototype.trim` de JS: su conjunto de espacios no es el de Kotlin ni el de Java. */
    private fun isJsSpace(c: Char): Boolean {
        val u = c.code
        return (u in 0x09..0x0D) || u == 0x20 || u == 0xA0 || u == 0x1680 ||
            (u in 0x2000..0x200A) || u == 0x2028 || u == 0x2029 || u == 0x202F ||
            u == 0x205F || u == 0x3000 || u == 0xFEFF
    }

    private fun jsTrim(s: String): String {
        var start = 0
        var end = s.length
        while (start < end && isJsSpace(s[start])) start++
        while (end > start && isJsSpace(s[end - 1])) end--
        return s.substring(start, end)
    }

    /**
     * `toLowerCase` de JS (mapeo completo) sobre el mapeo simple de Unicode: las únicas diferencias
     * incondicionales son U+0130 ("i" + punto combinante) y la sigma final (Σ -> ς cuando la precede una
     * letra con mayúsculas/minúsculas y no la sigue ninguna). Mismo criterio que el port a Dart.
     */
    private fun jsToLower(s: String): String {
        var needsWork = false
        for (ch in s) {
            if (ch.code == 0x0130 || ch.code == 0x03A3) { needsWork = true; break }
        }
        val sb = StringBuilder(s.length + 2)
        if (needsWork) {
            for (i in s.indices) {
                val u = s[i].code
                when {
                    u == 0x0130 -> sb.append("i\u0307")
                    u == 0x03A3 && precededByCased(s, i) && !followedByCased(s, i) -> sb.append('\u03C2')
                    else -> sb.append(s[i])
                }
            }
        } else {
            sb.append(s)
        }
        // Mapeo simple por punto de código (no el completo/contextual de String.lowercase()).
        val src = sb.toString()
        val out = StringBuilder(src.length)
        var i = 0
        while (i < src.length) {
            val cp = src.codePointAt(i)
            out.appendCodePoint(Character.toLowerCase(cp))
            i += Character.charCount(cp)
        }
        return out.toString()
    }

    private fun precededByCased(s: String, i: Int): Boolean {
        var j = i - 1
        while (j >= 0) {
            val u = s[j].code
            if (isCased(u)) return true
            if (!isCaseIgnorable(u)) return false
            j--
        }
        return false
    }

    private fun followedByCased(s: String, i: Int): Boolean {
        var j = i + 1
        while (j < s.length) {
            val u = s[j].code
            if (isCased(u)) return true
            if (!isCaseIgnorable(u)) return false
            j++
        }
        return false
    }

    private fun isCaseIgnorable(u: Int): Boolean = (u in 0x0300..0x036F) || u == 0x00AD || u == 0x200B

    private fun isCased(u: Int): Boolean =
        (u in 0x41..0x5A) || (u in 0x61..0x7A) ||
            (u in 0xC0..0x24F && u != 0xD7 && u != 0xF7) ||
            (u in 0x370..0x3FF) || (u in 0x400..0x4FF) || (u in 0x1E00..0x1FFF)

    /**
     * UTF-8 como lo hace TextEncoder de JS: un sustituto suelto (UTF-16 inválido) se codifica como
     * U+FFFD (EF BF BD). `String.toByteArray` de Java lo cambiaría por '?', y el hash divergiría.
     */
    internal fun utf8(s: String): ByteArray {
        val out = java.io.ByteArrayOutputStream(s.length + 8)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            var cp = c.code
            if (Character.isHighSurrogate(c) && i + 1 < s.length && Character.isLowSurrogate(s[i + 1])) {
                cp = Character.toCodePoint(c, s[i + 1])
                i += 2
            } else {
                if (Character.isSurrogate(c)) cp = 0xFFFD
                i += 1
            }
            when {
                cp < 0x80 -> out.write(cp)
                cp < 0x800 -> { out.write(0xC0 or (cp shr 6)); out.write(0x80 or (cp and 0x3F)) }
                cp < 0x10000 -> {
                    out.write(0xE0 or (cp shr 12)); out.write(0x80 or ((cp shr 6) and 0x3F)); out.write(0x80 or (cp and 0x3F))
                }
                else -> {
                    out.write(0xF0 or (cp shr 18)); out.write(0x80 or ((cp shr 12) and 0x3F))
                    out.write(0x80 or ((cp shr 6) and 0x3F)); out.write(0x80 or (cp and 0x3F))
                }
            }
        }
        return out.toByteArray()
    }

    /** Hashea la semilla una vez en un estado reutilizable (int32 con signo; para compararlo, `toLong() and 0xFFFFFFFF`). */
    fun seedState(seed: String, normalize: Boolean = true): Int {
        val s = if (normalize) normalizeSeed(seed) else seed
        return feed(1779033703 xor s.length, utf8(s))
    }

    /** Un float uniforme en [0, 1) para [key], independiente de cualquier otra clave. */
    fun stream(state: Int, key: String): Double =
        finalize(feed(feedByte(state, SEP), utf8(key))).toDouble() / 4294967296.0
}
