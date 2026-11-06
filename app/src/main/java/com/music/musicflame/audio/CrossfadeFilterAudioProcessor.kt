package com.music.musicflame.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * FILTRO DEL CROSSFADE (Kotlin puro, sin librerias).
 *
 * Mientras dos canciones suenan a la vez, sus graves (y el resto del espectro) se pisan: suena
 * "embarrado", se pierde volumen percibido y la suma de ambas puede saturar. Es la misma idea que
 * usan otras apps (p. ej. SimpMusic): a la cancion que SALE se le cierra poco a poco un pasa-bajos
 * (LOW_PASS) y a la que ENTRA se le abre un pasa-altos (HIGH_PASS), asi cada una ocupa su zona.
 *
 * Es un biquad Butterworth de 2.o orden (Q = 0.7071) con las formulas publicas del "RBJ Audio EQ
 * Cookbook". Fuera del fundido esta apagado y copia el PCM bit a bit (no gasta nada). Solo soporta
 * PCM 16-bit, igual que el resto de procesadores del proyecto.
 */
class CrossfadeFilterAudioProcessor(private val mode: Mode) : BaseAudioProcessor() {

    enum class Mode { LOW_PASS, HIGH_PASS }

    private companion object {
        const val Q = 0.7071
        const val MAX_CHANNELS = 8
        const val RECALC_RATIO = 0.005
    }

    @Volatile private var active = false
    @Volatile private var cutoffHz = 20f

    private var channelCount = 0
    private var sampleRate = 44100

    // Coeficientes normalizados y estado por canal (forma directa I).
    private var b0 = 1.0
    private var b1 = 0.0
    private var b2 = 0.0
    private var a1 = 0.0
    private var a2 = 0.0
    private var coefCutoff = -1.0
    private var wasActive = false
    private val x1 = DoubleArray(MAX_CHANNELS)
    private val x2 = DoubleArray(MAX_CHANNELS)
    private val y1 = DoubleArray(MAX_CHANNELS)
    private val y2 = DoubleArray(MAX_CHANNELS)

    /** Enciende/apaga el filtro y fija la frecuencia de corte (Hz). Seguro desde cualquier hilo. */
    fun setFilter(enabled: Boolean, cutoff: Float) {
        cutoffHz = cutoff
        active = enabled
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount > MAX_CHANNELS) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        channelCount = inputAudioFormat.channelCount
        sampleRate = inputAudioFormat.sampleRate
        coefCutoff = -1.0
        resetState()
        return inputAudioFormat
    }

    override fun onFlush() {
        resetState()
        coefCutoff = -1.0
        wasActive = false
    }

    private fun resetState() {
        x1.fill(0.0); x2.fill(0.0); y1.fill(0.0); y2.fill(0.0)
    }

    private fun updateCoefficients(fc: Double) {
        val f = fc.coerceIn(10.0, sampleRate * 0.45)
        val w0 = 2.0 * PI * f / sampleRate
        val cosW = cos(w0)
        val alpha = sin(w0) / (2.0 * Q)
        val a0 = 1.0 + alpha
        if (mode == Mode.LOW_PASS) {
            b0 = ((1.0 - cosW) / 2.0) / a0
            b1 = (1.0 - cosW) / a0
            b2 = b0
        } else {
            b0 = ((1.0 + cosW) / 2.0) / a0
            b1 = -(1.0 + cosW) / a0
            b2 = b0
        }
        a1 = (-2.0 * cosW) / a0
        a2 = (1.0 - alpha) / a0
        coefCutoff = fc
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0 || channelCount == 0) return

        if (!active) {
            wasActive = false
            val out = replaceOutputBuffer(remaining)
            out.put(inputBuffer)
            out.flip()
            return
        }

        val frameBytes = channelCount * 2
        val usableBytes = (remaining / frameBytes) * frameBytes
        if (usableBytes == 0) return

        if (!wasActive) {
            resetState()
            coefCutoff = -1.0
            wasActive = true
        }
        val fc = cutoffHz.toDouble()
        if (coefCutoff < 0.0 || kotlin.math.abs(fc - coefCutoff) > coefCutoff * RECALC_RATIO) {
            updateCoefficients(fc)
        }

        val out = replaceOutputBuffer(usableBytes)
        val input = inputBuffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        out.order(ByteOrder.LITTLE_ENDIAN)

        val frames = usableBytes / frameBytes
        for (n in 0 until frames) {
            for (ch in 0 until channelCount) {
                val x = input.short.toDouble()
                val y = b0 * x + b1 * x1[ch] + b2 * x2[ch] - a1 * y1[ch] - a2 * y2[ch]
                x2[ch] = x1[ch]; x1[ch] = x
                y2[ch] = y1[ch]; y1[ch] = y
                out.putShort(y.roundToInt().coerceIn(-32768, 32767).toShort())
            }
        }

        inputBuffer.position(inputBuffer.position() + usableBytes)
        out.flip()
    }
}
