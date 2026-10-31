package com.music.musicflame.voice.wake

import android.content.Context
import kotlin.math.max
import kotlin.math.sqrt

/** DTW (Dynamic Time Warping) con distancia coseno entre secuencias de huellas ya normalizadas. */
object WakeDtw {

    private fun dot(a: FloatArray, b: FloatArray): Float {
        var s = 0f
        for (i in a.indices) s += a[i] * b[i]
        return s
    }

    /**
     * Qué tanto se parece el final de [live] a la plantilla [t] (0 = idéntico; más alto = más distinto).
     * Compara contra las últimas ~1.5 veces el largo de la plantilla; el inicio es libre y el final queda fijo en la
     * huella más reciente (así detecta justo cuando terminas de decir la frase).
     * Devuelve [Float.MAX_VALUE] si todavía no hay suficientes huellas.
     */
    fun score(t: Array<FloatArray>, live: List<FloatArray>): Float {
        val m = t.size
        if (m == 0 || live.size < m) return Float.MAX_VALUE
        val n = minOf(live.size, m * 3 / 2)
        val off = live.size - n
        var prev = FloatArray(n)
        var cur = FloatArray(n)
        for (j in 0 until n) prev[j] = 1f - dot(t[0], live[off + j])
        for (i in 1 until m) {
            cur[0] = prev[0] + (1f - dot(t[i], live[off]))
            for (j in 1 until n) {
                val best = minOf(prev[j], prev[j - 1], cur[j - 1])
                cur[j] = best + (1f - dot(t[i], live[off + j]))
            }
            val tmp = prev; prev = cur; cur = tmp
        }
        return prev[n - 1] / m
    }
}

/**
 * Detector de "oye <nombre>" en audio continuo. Se alimenta con bloques de 1280 muestras (80 ms).
 *
 *  - Solo corre los modelos cuando hay voz (compuerta de energía con memoria de ~1.6 s hacia atrás): en silencio casi
 *    no gasta batería.
 *  - Compara las huellas recientes contra tus plantillas con DTW; se activa cuando la mejor distancia baja del umbral
 *    en 2 pasos seguidos (evita disparos por un solo pico).
 *  - Si suena música por el altavoz, el umbral se vuelve más estricto.
 */
class WakeDetector(ctx: Context, private val data: WakeTemplates.Data, var sensitivity: WakeSensitivity) : AutoCloseable {

    class Step(
        /** Hay voz en curso (los modelos están corriendo). */
        val voiceActive: Boolean,
        /** Este bloque en particular tenía voz fuerte. */
        val loud: Boolean,
        /** Mejor distancia contra las plantillas (menor = más parecido); MAX_VALUE si aún no se mide. */
        val score: Float,
        val threshold: Float,
        val fired: Boolean
    )

    private val extractor = WakeFeatureExtractor(ctx)
    private val live = ArrayList<FloatArray>()
    private val maxLive = (data.templates.maxOf { it.size }) * 3 / 2 + 2

    private val ring = Array(BACKLOG) { ShortArray(WakeFeatureExtractor.CHUNK) }
    private var ringPos = 0
    private var ringCount = 0

    private var tick = 0L
    private var active = false
    private var activeUntil = 0L
    private var noise = 300f
    private var streak = 0
    private var refractoryUntil = 0L
    private var lastScore = Float.MAX_VALUE

    fun reset() {
        active = false; streak = 0; live.clear(); ringCount = 0; ringPos = 0
        extractor.reset()
    }

    fun process(chunk: ShortArray, musicPlaying: Boolean): Step {
        tick++
        System.arraycopy(chunk, 0, ring[ringPos], 0, chunk.size)
        ringPos = (ringPos + 1) % BACKLOG
        if (ringCount < BACKLOG) ringCount++

        val rms = rms(chunk)
        val gate = max(MIN_GATE, noise * 3f)
        val loud = rms > gate
        var fired = false
        val threshold = data.base * sensitivity.multiplier * (if (musicPlaying) 0.85f else 1f)

        if (loud) activeUntil = tick + HANGOVER
        if (!active) {
            if (!loud) {
                noise += (rms - noise) * 0.05f          // aprende el ruido de fondo solo cuando nadie habla
                return Step(false, false, Float.MAX_VALUE, threshold, false)
            }
            active = true; streak = 0; live.clear(); extractor.reset()
            // Se alimenta lo que pasó justo antes de la voz para no perder el inicio de la frase.
            var idx = (ringPos - ringCount + BACKLOG) % BACKLOG
            repeat(ringCount) {
                if (consume(ring[idx], threshold)) fired = true
                idx = (idx + 1) % BACKLOG
            }
        } else {
            if (consume(chunk, threshold)) fired = true
        }
        if (tick > activeUntil) { active = false; live.clear(); streak = 0 }
        return Step(active, loud, lastScore, threshold, fired)
    }

    /** Procesa un bloque; true si en este paso se confirmó la frase. */
    private fun consume(chunk: ShortArray, threshold: Float): Boolean {
        val e = extractor.feed(chunk) ?: return false
        live.add(e)
        while (live.size > maxLive) live.removeAt(0)
        var best = Float.MAX_VALUE
        for (t in data.templates) {
            val s = WakeDtw.score(t, live)
            if (s < best) best = s
        }
        lastScore = best
        streak = if (best < threshold) streak + 1 else 0
        if (streak >= 2 && tick >= refractoryUntil) {
            refractoryUntil = tick + REFRACTORY
            live.clear(); streak = 0
            return true
        }
        return false
    }

    override fun close() { extractor.close() }

    companion object {
        private const val BACKLOG = 20          // 1.6 s de audio previo a la voz
        private const val HANGOVER = 28         // ~2.2 s siguen corriendo los modelos tras la última voz fuerte
        private const val REFRACTORY = 25       // ~2 s sin volver a activarse
        private const val MIN_GATE = 450f

        fun rms(b: ShortArray): Float {
            var s = 0.0
            for (x in b) s += x.toDouble() * x
            return sqrt(s / b.size).toFloat()
        }
    }
}
