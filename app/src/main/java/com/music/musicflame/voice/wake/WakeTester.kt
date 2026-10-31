package com.music.musicflame.voice.wake

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Corre el detector de verdad sobre el micrófono durante unos segundos y cuenta resultados (para "Probar nombre").
 *  - Paso 1: dices la frase varias veces → cada segmento de voz que activa cuenta como detección; el que no, como fallo.
 *  - Paso 2: hablas normal → cualquier activación es una falsa activación.
 */
class WakeTester(private val ctx: Context, private val detector: WakeDetector) {

    class Live(val level: Float, val hits: Int, val misses: Int, val score: Float, val threshold: Float, val msLeft: Long)
    class Result(val hits: Int, val misses: Int, val micError: Boolean)

    suspend fun run(durationMs: Long, stopAtHits: Int, onLive: (Live) -> Unit): Result = withContext(Dispatchers.IO) {
        val rec = WakeAudio.open()
        if (rec == null || !WakeAudio.startSafely(rec)) {
            WakeAudio.releaseSafely(rec)
            return@withContext Result(0, 0, true)
        }
        detector.reset()
        val buf = ShortArray(WakeAudio.CHUNK)
        val t0 = SystemClock.elapsedRealtime()
        var hits = 0
        var misses = 0
        var segActive = false
        var segFired = false
        var segLoud = 0
        var music = false
        var chunks = 0
        try {
            while (true) {
                currentCoroutineContext().ensureActive()
                val left = durationMs - (SystemClock.elapsedRealtime() - t0)
                if (left <= 0L) break
                if (stopAtHits in 1..hits) break
                if (!WakeAudio.readChunk(rec, buf)) return@withContext Result(hits, misses, true)
                if (chunks++ % 12 == 0) music = WakeWord.musicLeaking(ctx)
                val step = detector.process(buf, music)
                if (step.fired) { hits++; segFired = true }
                if (step.voiceActive) {
                    if (!segActive) { segActive = true; segFired = step.fired; segLoud = 0 }
                    if (step.loud) segLoud++
                } else if (segActive) {
                    segActive = false
                    if (!segFired && segLoud >= 3) misses++
                    segFired = false
                }
                onLive(Live((WakeDetector.rms(buf) / 6000f).coerceIn(0f, 1f), hits, misses, step.score, step.threshold, left))
            }
            if (segActive && !segFired && segLoud >= 3) misses++
        } finally { WakeAudio.releaseSafely(rec) }
        Result(hits, misses, false)
    }
}
