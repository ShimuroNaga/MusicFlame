package com.music.musicflame.voice.wake

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Utilidades de micrófono para la escucha siempre (16 kHz, mono, PCM 16 bits). */
object WakeAudio {
    const val RATE = 16_000
    const val CHUNK = WakeFeatureExtractor.CHUNK

    /** Abre (sin iniciar) el micrófono. null si no se pudo (sin permiso o en uso). El llamador debe liberarlo. */
    @SuppressLint("MissingPermission")
    fun open(): AudioRecord? {
        val min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val bytes = maxOf(if (min > 0) min else 0, CHUNK * 2 * 4)
        for (src in intArrayOf(MediaRecorder.AudioSource.VOICE_RECOGNITION, MediaRecorder.AudioSource.MIC)) {
            try {
                val r = AudioRecord(src, RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bytes)
                if (r.state == AudioRecord.STATE_INITIALIZED) return r
                r.release()
            } catch (_: Exception) { /* se prueba la siguiente fuente */ }
        }
        return null
    }

    /** Inicia la captura; false si el sistema no la dejó empezar. */
    fun startSafely(r: AudioRecord): Boolean = try {
        r.startRecording()
        r.recordingState == AudioRecord.RECORDSTATE_RECORDING
    } catch (_: Exception) { false }

    fun releaseSafely(r: AudioRecord?) {
        if (r == null) return
        try { r.stop() } catch (_: Exception) {}
        try { r.release() } catch (_: Exception) {}
    }

    /** Lee exactamente [buf].size muestras (bloquea ~80 ms). false si hubo error de lectura. */
    fun readChunk(r: AudioRecord, buf: ShortArray): Boolean {
        var off = 0
        while (off < buf.size) {
            val n = r.read(buf, off, buf.size - off)
            if (n <= 0) return false
            off += n
        }
        return true
    }

    sealed class Capture {
        class Clip(val samples: ShortArray) : Capture()
        object NoSpeech : Capture()
        object MicError : Capture()
    }

    /**
     * Graba UNA frase para el registro guiado: espera a que hables, y corta ~0.6 s después de que termines.
     * Devuelve 0.6 s de audio antes de tu voz + la frase + un pequeño final. El audio solo vive en memoria.
     */
    suspend fun captureClip(onLevel: (Float) -> Unit, onSpeaking: () -> Unit, maxWaitMs: Long = 9_000): Capture =
        withContext(Dispatchers.IO) {
            val rec = open() ?: return@withContext Capture.MicError
            if (!startSafely(rec)) { releaseSafely(rec); return@withContext Capture.MicError }
            try {
                val chunks = ArrayList<ShortArray>()
                var noiseSum = 0f
                var noiseN = 0
                var onset = -1
                var lastLoud = -1
                var consecutiveLoud = 0
                var i = 0
                val limit = (maxWaitMs / 80L).toInt() + MAX_SPEECH_CHUNKS
                while (i < limit) {
                    currentCoroutineContext().ensureActive()
                    val buf = ShortArray(CHUNK)
                    if (!readChunk(rec, buf)) return@withContext Capture.MicError
                    chunks += buf
                    val rms = WakeDetector.rms(buf)
                    onLevel((rms / 6000f).coerceIn(0f, 1f))
                    if (i < 4) { noiseSum += rms; noiseN++ }
                    val gate = maxOf(450f, (noiseSum / maxOf(1, noiseN)) * 3.2f)
                    val loud = i >= 2 && rms > gate

                    if (loud) { lastLoud = i; consecutiveLoud++ } else consecutiveLoud = 0
                    if (onset < 0 && consecutiveLoud >= 2) { onset = i - 1; onSpeaking() }

                    if (onset >= 0) {
                        val speechChunks = lastLoud - onset + 1
                        val silentFor = i - lastLoud
                        if ((silentFor >= 8 && speechChunks >= MIN_SPEECH_CHUNKS) || speechChunks >= MAX_SPEECH_CHUNKS) {
                            val from = maxOf(0, onset - PRE_ROLL)
                            val to = minOf(chunks.size - 1, lastLoud + 2)
                            val out = ShortArray((to - from + 1) * CHUNK)
                            for (k in from..to) System.arraycopy(chunks[k], 0, out, (k - from) * CHUNK, CHUNK)
                            return@withContext Capture.Clip(out)
                        }
                        if (silentFor >= 8 && speechChunks < MIN_SPEECH_CHUNKS) { onset = -1; consecutiveLoud = 0 }  // ruido corto
                    } else if (i * 80L > maxWaitMs) break
                    i++
                }
                Capture.NoSpeech
            } finally { releaseSafely(rec) }
        }

    private const val PRE_ROLL = 8              // 0.64 s antes de la voz
    private const val MIN_SPEECH_CHUNKS = 5     // 0.4 s mínimo de voz
    private const val MAX_SPEECH_CHUNKS = 45    // 3.6 s máximo de voz
}
