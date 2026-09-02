package com.music.musicflame.data

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import java.nio.ByteOrder
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Analiza el audio REAL de un archivo (no sus tags) para estimar tempo (BPM)
 * y energía.
 *
 * Todo el código de este archivo es propio, sin librerías de terceros. Se
 * evaluó usar TarsosDSP para esto, pero su licencia es GPLv3: incluirla
 * obligaría a liberar el código fuente completo de MusicFlame bajo GPL,
 * incompatible con el desbloqueo pago vía Lemon Squeezy (y de paso, el repo
 * de TarsosDSP tampoco trae un build.gradle en la raíz, así que ni siquiera
 * se resuelve limpio vía JitPack). Por eso acá se usa únicamente
 * MediaExtractor + MediaCodec (nativos de Android, sin restricción de
 * licencia) para decodificar, y matemática de DSP básica para el análisis:
 * RMS para energía, y autocorrelación sobre la envolvente de energía para
 * estimar el tempo.
 */
object AudioFeatureExtractor {
    private const val TAG = "AudioFeatureExtractor"

    // Cuánto del archivo se analiza: evita decodificar la canción entera.
    // Bajado de 45s a 25s: para bibliotecas grandes (cientos de canciones) el
    // tiempo de decodificado importa mucho más que 20 segundos extra de
    // precisión en el BPM, que en la práctica no cambia casi nada el resultado.
    private const val ANALYSIS_DURATION_MS = 25_000L

    // A qué milisegundo empezar a analizar, para saltar intros silenciosas
    // o con solo voz hablada. Si la canción es más corta, se ajusta solo.
    private const val ANALYSIS_START_MS = 15_000L

    // Tasa de muestreo interna a la que se reduce el audio antes de
    // analizarlo: no hace falta la fidelidad de 44.1kHz para tempo/energía,
    // y bajarla acelera mucho el análisis.
    private const val TARGET_SAMPLE_RATE = 11_025

    // Tamaño de ventana para la envolvente de energía (~93ms a 11025Hz).
    private const val WINDOW_SIZE = 1024

    // Rango de tempo soportado (cubre prácticamente cualquier género).
    private const val MIN_BPM = 60f
    private const val MAX_BPM = 200f

    /**
     * Decodifica un fragmento del archivo y calcula sus features. Devuelve
     * null si el archivo no se pudo abrir/decodificar (formato no soportado,
     * archivo corrupto, permisos, canción demasiado corta, etc.) — el
     * llamador (AudioAnalysisScheduler) lo trata como "no se pudo analizar
     * esta canción" y sigue con la siguiente.
     */
    fun extract(path: String): AudioFeatures? {
        return try {
            val pcm = decodeToMonoPcm(path) ?: return null
            if (pcm.size < WINDOW_SIZE * 4) return null
            computeFeatures(pcm)
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo analizar $path: ${e.message}")
            null
        }
    }

    // --- Decodificación ---

    private fun decodeToMonoPcm(path: String): FloatArray? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(path)

            var trackIndex = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    trackIndex = i
                    format = f
                    break
                }
            }
            if (trackIndex < 0 || format == null) return null
            extractor.selectTrack(trackIndex)

            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION))
                format.getLong(MediaFormat.KEY_DURATION) else 0L
            val startUs = min(
                ANALYSIS_START_MS * 1000L,
                max(0L, durationUs - ANALYSIS_DURATION_MS * 1000L)
            )
            if (startUs > 0) extractor.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

            val mime = format.getString(MediaFormat.KEY_MIME)!!
            val inputChannels = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT))
                format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 2
            val inputSampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE))
                format.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 44100
            val downsampleStride = max(1, inputSampleRate / TARGET_SAMPLE_RATE)

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            // Buffer pre-asignado (con margen de 1s) en vez de una lista con
            // autoboxing: evita generar basura y reasignos para ~500k samples.
            val expectedSamples =
                (TARGET_SAMPLE_RATE.toLong() * ANALYSIS_DURATION_MS / 1000L).toInt() + TARGET_SAMPLE_RATE
            var pcmBuffer = FloatArray(expectedSamples)
            var writeIndex = 0

            val bufferInfo = MediaCodec.BufferInfo()
            var sawInputEof = false
            var sawOutputEof = false
            var sampleCounter = 0

            while (!sawOutputEof) {
                if (!sawInputEof) {
                    val inIndex = codec.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val inBuffer = codec.getInputBuffer(inIndex)!!
                        val sampleSize = extractor.readSampleData(inBuffer, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            sawInputEof = true
                        } else {
                            val sampleTimeUs = extractor.sampleTime
                            codec.queueInputBuffer(inIndex, 0, sampleSize, sampleTimeUs, 0)
                            extractor.advance()
                        }
                    }
                }

                val outIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)
                if (outIndex >= 0) {
                    if (bufferInfo.size > 0 && writeIndex < pcmBuffer.size) {
                        val outBuffer = codec.getOutputBuffer(outIndex)!!
                        outBuffer.position(bufferInfo.offset)
                        outBuffer.limit(bufferInfo.offset + bufferInfo.size)
                        val shortBuffer = outBuffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                        val frameCount = shortBuffer.remaining() / inputChannels

                        for (frame in 0 until frameCount) {
                            var sum = 0
                            for (ch in 0 until inputChannels) sum += shortBuffer.get()
                            val monoSample = sum / inputChannels

                            // Downsample simple por decimación: suficiente para
                            // estimar tempo/energía, y mucho más barato que un
                            // filtro anti-aliasing completo.
                            if (sampleCounter % downsampleStride == 0) {
                                if (writeIndex < pcmBuffer.size) {
                                    pcmBuffer[writeIndex++] = monoSample / 32768f
                                } else {
                                    break
                                }
                            }
                            sampleCounter++
                        }
                    }
                    val isEos = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                    codec.releaseOutputBuffer(outIndex, false)
                    if (isEos || writeIndex >= pcmBuffer.size) {
                        sawOutputEof = true
                    }
                }
            }

            return pcmBuffer.copyOf(writeIndex)
        } finally {
            codec?.let {
                try {
                    it.stop()
                } catch (e: Exception) {
                    // Puede fallar si ya se cortó antes por error; no es crítico.
                }
                it.release()
            }
            extractor.release()
        }
    }

    // --- Análisis DSP ---

    private fun computeFeatures(pcm: FloatArray): AudioFeatures {
        val windowCount = pcm.size / WINDOW_SIZE
        val energy = rmsEnergy(pcm)
        if (windowCount < 8) return AudioFeatures(bpm = 0f, energy = energy)

        // Envolvente de energía: RMS por ventana.
        val envelope = FloatArray(windowCount)
        for (w in 0 until windowCount) {
            var sumSquares = 0.0
            val base = w * WINDOW_SIZE
            for (i in 0 until WINDOW_SIZE) {
                val s = pcm[base + i]
                sumSquares += (s * s).toDouble()
            }
            envelope[w] = sqrt(sumSquares / WINDOW_SIZE).toFloat()
        }

        // Envolvente de "onsets": solo los incrementos de energía (media onda
        // rectificada). Los golpes de batería/bajo se ven como picos acá.
        val onset = FloatArray(windowCount)
        for (w in 1 until windowCount) {
            onset[w] = max(0f, envelope[w] - envelope[w - 1])
        }

        val bpm = estimateBpmByAutocorrelation(onset)
        return AudioFeatures(bpm = bpm, energy = energy)
    }

    private fun rmsEnergy(pcm: FloatArray): Float {
        if (pcm.isEmpty()) return 0f
        var sumSquares = 0.0
        for (s in pcm) sumSquares += (s * s).toDouble()
        val rms = sqrt(sumSquares / pcm.size).toFloat()

        // RMS lineal se "siente" muy distinto a cómo el oído percibe volumen.
        // Se pasa a escala logarítmica (dB) y se normaliza dentro de un rango
        // típico de música grabada (-40dB flojito .. 0dB a tope).
        val db = 20f * (ln(max(rms, 1e-6f)) / ln(10f))
        val normalized = (db + 40f) / 40f
        return normalized.coerceIn(0f, 1f)
    }

    private fun estimateBpmByAutocorrelation(onset: FloatArray): Float {
        val windowsPerSecond = TARGET_SAMPLE_RATE.toFloat() / WINDOW_SIZE
        val minLag = (60f / MAX_BPM * windowsPerSecond).toInt().coerceAtLeast(1)
        val maxLag = (60f / MIN_BPM * windowsPerSecond).toInt().coerceAtMost(onset.size - 1)
        if (maxLag <= minLag) return 0f

        var bestLag = -1
        var bestScore = 0f
        for (lag in minLag..maxLag) {
            var score = 0f
            for (i in 0 until onset.size - lag) {
                score += onset[i] * onset[i + lag]
            }
            if (score > bestScore) {
                bestScore = score
                bestLag = lag
            }
        }
        if (bestLag <= 0 || bestScore <= 0f) return 0f
        return (60f * windowsPerSecond / bestLag).coerceIn(MIN_BPM, MAX_BPM)
    }
}
