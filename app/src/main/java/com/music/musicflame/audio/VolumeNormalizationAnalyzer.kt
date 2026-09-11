package com.music.musicflame.audio

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.nio.ByteOrder
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * EQ PRO — ETAPA 2: calcula, para una canción dada (por su ruta física de archivo), cuánto
 * gain (en dB) hay que sumarle o restarle para que suene "parejo" respecto a un nivel de
 * referencia — la típica normalización de volumen entre canciones (que una no reviente el
 * oído y otra suene bajita).
 *
 * CÓMO SE CALCULA (sin librerías externas, restricción de licencia del proyecto):
 * Decodifica el archivo a PCM con MediaExtractor + MediaCodec (las mismas clases nativas de
 * Android que ya usa el resto del proyecto para leer audio), acumula la energía RMS de las
 * muestras decodificadas, y la convierte a dBFS. El gain resultante es la diferencia entre
 * un nivel de referencia fijo (TARGET_RMS_DBFS) y lo que se midió, recortado a un rango
 * seguro para que ninguna canción reciba un boost/corte extremo.
 *
 * Esto es una normalización basada en RMS simple, NO es LUFS (el estándar real de streaming
 * como Spotify/YouTube usa un modelo psicoacústico bastante más complejo, con ponderación de
 * frecuencias tipo "K-weighting" y ventanas de gating). RMS es más simple, pero cumple el
 * objetivo pedido ("basado en RMS/energía") sin meter una dependencia de licencia.
 *
 * NO reutiliza ni recrea la clase AudioFeatureExtractor que existió antes en el proyecto (esa
 * usaba TarsosDSP, GPLv3, y fue eliminada por completo por conflicto de licencia con el
 * modelo de pago). Esta clase es independiente, no toca autocorrelación/BPM, solo RMS, y no
 * depende de ninguna librería de terceros.
 *
 * Para no tardar minutos en canciones largas, solo analiza hasta MAX_ANALYSIS_DURATION_MS de
 * audio (desde el inicio del archivo) — de sobra para estimar el nivel general de energía de
 * una canción típica.
 */
object VolumeNormalizationAnalyzer {

    /** Nivel de referencia (RMS en dBFS) al que se intenta llevar cada canción. */
    private const val TARGET_RMS_DBFS = -18f

    /** Nunca se aplica más boost/corte que esto, para no distorsionar canciones muy
     *  bajitas ni silenciar de más canciones ya muy fuertes. */
    private const val MAX_GAIN_DB = 12f
    private const val MIN_GAIN_DB = -12f

    /** Tope de cuánto audio (desde el inicio) se decodifica para el análisis. */
    private const val MAX_ANALYSIS_DURATION_US = 30_000_000L // 30s

    private const val DEQUEUE_TIMEOUT_US = 10_000L

    /**
     * Analiza el archivo en [filePath] y devuelve el gain de normalización en dB, o null si
     * el archivo no se pudo leer/decodificar (DRM, formato no soportado, ruta inválida, etc.)
     * — en ese caso el llamador simplemente no aplica normalización a esa canción.
     *
     * Bloqueante: debe llamarse desde un hilo de fondo (Dispatchers.IO), nunca desde el hilo
     * principal ni desde el hilo de audio de ExoPlayer.
     */
    fun analyze(filePath: String): Float? {
        if (filePath.isEmpty()) return null

        var extractor: MediaExtractor? = null
        var codec: MediaCodec? = null
        try {
            extractor = MediaExtractor()
            extractor.setDataSource(filePath)

            val trackIndex = selectAudioTrack(extractor) ?: return null
            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            extractor.selectTrack(trackIndex)

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            var sumSquares = 0.0
            var sampleCount = 0L
            var sawInputEos = false
            var sawOutputEos = false
            val bufferInfo = MediaCodec.BufferInfo()

            while (!sawOutputEos) {
                if (!sawInputEos) {
                    val inputIndex = codec.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputIndex)
                        val sampleSize = inputBuffer?.let { extractor.readSampleData(it, 0) } ?: -1
                        val sampleTimeUs = extractor.sampleTime

                        if (sampleSize < 0 || sampleTimeUs > MAX_ANALYSIS_DURATION_US) {
                            codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            sawInputEos = true
                        } else {
                            codec.queueInputBuffer(inputIndex, 0, sampleSize, sampleTimeUs, 0)
                            extractor.advance()
                        }
                    }
                }

                val outputIndex = codec.dequeueOutputBuffer(bufferInfo, DEQUEUE_TIMEOUT_US)
                if (outputIndex >= 0) {
                    if (bufferInfo.size > 0) {
                        codec.getOutputBuffer(outputIndex)?.let { outBuffer ->
                            outBuffer.order(ByteOrder.LITTLE_ENDIAN)
                            outBuffer.position(bufferInfo.offset)
                            outBuffer.limit(bufferInfo.offset + bufferInfo.size)
                            while (outBuffer.remaining() >= 2) {
                                val sample = outBuffer.short.toFloat() / 32768f
                                sumSquares += (sample * sample).toDouble()
                                sampleCount++
                            }
                        }
                    }
                    codec.releaseOutputBuffer(outputIndex, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        sawOutputEos = true
                    }
                }
            }

            if (sampleCount == 0L) return null

            val rms = sqrt(sumSquares / sampleCount)
            if (rms <= 0.0) return null

            val measuredDbfs = 20.0 * log10(rms)
            val gainDb = (TARGET_RMS_DBFS - measuredDbfs).toFloat()
            return gainDb.coerceIn(MIN_GAIN_DB, MAX_GAIN_DB)
        } catch (e: Exception) {
            return null
        } finally {
            try {
                codec?.stop()
            } catch (e: Exception) {
                // Puede tronar si el codec nunca llegó a arrancar bien; no pasa nada, igual
                // se libera abajo.
            }
            try {
                codec?.release()
            } catch (e: Exception) {
            }
            try {
                extractor?.release()
            } catch (e: Exception) {
            }
        }
    }

    private fun selectAudioTrack(extractor: MediaExtractor): Int? {
        for (i in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)
            if (mime != null && mime.startsWith("audio/")) return i
        }
        return null
    }
}
