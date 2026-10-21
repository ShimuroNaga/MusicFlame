package com.music.musicflame.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * NORMALIZACIÓN DE VOLUMEN (gratis, independiente del EQ Pro).
 *
 * AudioProcessor de solo ganancia: multiplica el PCM por un gain lineal que calcula
 * VolumeNormalizationAnalyzer para cada canción, para que todas suenen a un volumen parecido.
 * Va DESPUÉS del EQ Pro en la cadena de ExoPlayer, igual que antes iba dentro de él.
 *
 * Con la opción apagada, o con gain neutro (0 dB), copia el PCM tal cual, bit a bit.
 * Solo soporta PCM 16-bit (el mismo formato que usa el resto del proyecto); con otro
 * encoding se declara no soportado y Media3 lo deja pasar de largo.
 */
class VolumeNormalizationAudioProcessor : BaseAudioProcessor() {

    companion object {
        private const val MAX_GAIN_DB = 12f
        private const val MIN_GAIN_DB = -12f
    }

    @Volatile private var enabled: Boolean = false
    @Volatile private var gainLinear: Float = 1f

    private var channelCount = 0

    /** Activa o apaga la normalización (switch de Ajustes). */
    fun setEnabled(value: Boolean) {
        enabled = value
    }

    /** Gain en dB de la canción actual, recortado a [-12, 12]. 0f = neutro. */
    fun setGainDb(gainDb: Float) {
        val clamped = gainDb.coerceIn(MIN_GAIN_DB, MAX_GAIN_DB)
        gainLinear = 10.0.pow(clamped / 20.0).toFloat()
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        channelCount = inputAudioFormat.channelCount
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0 || channelCount == 0) return

        val gain = gainLinear
        if (!enabled || gain == 1f) {
            val out = replaceOutputBuffer(remaining)
            out.put(inputBuffer)
            out.flip()
            return
        }

        val frameSizeBytes = channelCount * 2
        val usableBytes = (remaining / frameSizeBytes) * frameSizeBytes
        if (usableBytes == 0) return

        val out = replaceOutputBuffer(usableBytes)
        val input = inputBuffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        out.order(ByteOrder.LITTLE_ENDIAN)

        var i = 0
        while (i < usableBytes) {
            val sample = input.short.toFloat() * gain
            out.putShort(sample.roundToInt().coerceIn(-32768, 32767).toShort())
            i += 2
        }

        inputBuffer.position(inputBuffer.position() + usableBytes)
        out.flip()
    }
}
