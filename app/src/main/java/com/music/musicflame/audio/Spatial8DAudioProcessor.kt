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
 * AUDIO 8D + BALANCE IZQ/DER (gratis).
 *
 * AudioProcessor de Media3 que va AL FINAL de la cadena (después del EQ Pro y de la
 * normalización de volumen), así el nivel que calculó la normalización no se altera.
 *
 * 8D = paneo automático: una fuente virtual da vueltas alrededor de la cabeza. Se construye
 * con tres pistas que usa el oído para ubicar un sonido (todo en Kotlin puro, sin librerías):
 *  1) Diferencia de volumen: el oído lejano baja de nivel (ley de balance, el cercano queda
 *     siempre en 1.0, así que el paneo nunca sube el volumen ni puede recortar).
 *  2) Diferencia de tiempo (ITD): el oído lejano recibe el sonido hasta ~0.6 ms después
 *     (retardo fraccionario con interpolación lineal, sin clicks).
 *  3) Sombra de cabeza: el oído lejano pierde agudos (filtro pasa-bajos de un polo).
 *
 * Solo procesa PCM 16-bit ESTÉREO. Con mono u otro número de canales deja pasar el audio sin
 * tocarlo. Apagado y con balance en 0, copia el PCM bit a bit (cero costo de cómputo).
 * Prender/apagar y mover el balance se suaviza con rampas (~60 ms) para que no haya "pops".
 *
 * Todos los setters son seguros desde otro hilo (@Volatile): el servicio los llama desde el
 * hilo principal y queueInput() corre en el hilo de audio.
 */
class Spatial8DAudioProcessor : BaseAudioProcessor() {

    companion object {
        const val MIN_PERIOD_SECONDS = 3f
        const val MAX_PERIOD_SECONDS = 20f
        const val DEFAULT_PERIOD_SECONDS = 8f
        const val DEFAULT_DEPTH = 0.85f

        private const val MAX_ITD_SECONDS = 0.0006f      // ~0.6 ms, retardo interaural realista
        private const val MAX_DELAY_SAMPLES = 64          // buffer circular (cubre hasta 96 kHz)
        private const val RAMP_SECONDS = 0.06f            // suavizado de on/off y balance
        private const val FAR_EAR_CUTOFF_HZ = 5500f       // pasa-bajos del oído lejano a tope
        private const val TWO_PI = (2.0 * PI).toFloat()
    }

    @Volatile private var enabled: Boolean = false
    @Volatile private var periodSeconds: Float = DEFAULT_PERIOD_SECONDS
    @Volatile private var depth: Float = DEFAULT_DEPTH
    @Volatile private var balanceTarget: Float = 0f

    private var channelCount = 0
    private var sampleRateHz = 0

    // Estado interno (solo lo toca el hilo de audio).
    private var phase = 0f                 // 0..2π, avanza con las muestras
    private var wet = 0f                   // 0 = audio original, 1 = 8D completo (rampa)
    private var balanceCurrent = 0f        // balance suavizado
    private var delayL = FloatArray(MAX_DELAY_SAMPLES)
    private var delayR = FloatArray(MAX_DELAY_SAMPLES)
    private var writeIndex = 0
    private var lpL = 0f
    private var lpR = 0f

    /** Activa o apaga el efecto 8D (botón del reproductor / diálogo). */
    fun setEnabled(value: Boolean) {
        enabled = value
    }

    /** Segundos que tarda en dar una vuelta completa. Se recorta a [3, 20]. */
    fun setPeriodSeconds(seconds: Float) {
        periodSeconds = seconds.coerceIn(MIN_PERIOD_SECONDS, MAX_PERIOD_SECONDS)
    }

    /** Intensidad del paneo, 0..1 (1 = el oído lejano casi se apaga en los extremos). */
    fun setDepth(value: Float) {
        depth = value.coerceIn(0f, 1f)
    }

    /** Balance fijo -1 (todo izquierda) .. +1 (todo derecha). Funciona con o sin 8D. */
    fun setBalance(value: Float) {
        balanceTarget = value.coerceIn(-1f, 1f)
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        channelCount = inputAudioFormat.channelCount
        sampleRateHz = inputAudioFormat.sampleRate
        clearState()
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0 || channelCount == 0) return

        val on = enabled
        val targetBalance = balanceTarget
        val neutral = !on && wet == 0f && targetBalance == 0f && balanceCurrent == 0f

        // Mono / multicanal, o todo en neutro: copia bit a bit, sin tocar nada.
        if (channelCount != 2 || sampleRateHz <= 0 || neutral) {
            val out = replaceOutputBuffer(remaining)
            out.put(inputBuffer)
            out.flip()
            return
        }

        val frameCount = remaining / 4 // 2 canales x 2 bytes
        if (frameCount == 0) return
        val usableBytes = frameCount * 4

        val out = replaceOutputBuffer(usableBytes)
        val input = inputBuffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        out.order(ByteOrder.LITTLE_ENDIAN)

        val sr = sampleRateHz.toFloat()
        val phaseStep = TWO_PI / (periodSeconds * sr)
        val rampStep = 1f / (RAMP_SECONDS * sr)
        val wetTarget = if (on) 1f else 0f
        val maxDelay = (MAX_ITD_SECONDS * sr).coerceAtMost((MAX_DELAY_SAMPLES - 2).toFloat())
        // Coeficiente del pasa-bajos de un polo para el oído lejano a tope.
        val lpFar = 1f - kotlin.math.exp((-TWO_PI * FAR_EAR_CUTOFF_HZ / sr).toDouble()).toFloat()
        val d = depth

        for (frame in 0 until frameCount) {
            // Rampas suaves hacia el estado pedido.
            wet = approach(wet, wetTarget, rampStep)
            balanceCurrent = approach(balanceCurrent, targetBalance, rampStep)

            val l = input.short.toFloat()
            val r = input.short.toFloat()

            // Guarda la muestra actual en el buffer circular (para el retardo ITD).
            delayL[writeIndex] = l
            delayR[writeIndex] = r

            // Posición de la fuente virtual: -1 (izquierda) .. +1 (derecha).
            val pan = sin(phase) * d * wet

            // Oído lejano = el opuesto al lado donde está la fuente.
            val farRight = if (pan > 0f) pan else 0f   // fuente a la derecha -> L es el lejano
            val farLeft = if (pan < 0f) -pan else 0f   // fuente a la izquierda -> R es el lejano

            // 1) Nivel: el lejano baja con curva de coseno, el cercano queda en 1.
            val gainL = cos(farRight * (PI.toFloat() / 2f))
            val gainR = cos(farLeft * (PI.toFloat() / 2f))

            // 2) ITD: el lejano llega un poco después (lectura fraccionaria).
            val dl = readDelayed(delayL, farRight * maxDelay)
            val dr = readDelayed(delayR, farLeft * maxDelay)

            // 3) Sombra de cabeza: el lejano pierde agudos (mezcla pasa-bajos según lejanía).
            lpL += lpFar * (dl - lpL)
            lpR += lpFar * (dr - lpR)
            val shadowL = farRight
            val shadowR = farLeft
            var outL = (dl * (1f - shadowL) + lpL * shadowL) * gainL
            var outR = (dr * (1f - shadowR) + lpR * shadowR) * gainR

            // Balance fijo (independiente del 8D): baja solo el lado contrario.
            val b = balanceCurrent
            if (b > 0f) outL *= cos(b * (PI.toFloat() / 2f)) else if (b < 0f) outR *= cos(-b * (PI.toFloat() / 2f))

            out.putShort(outL.roundToInt().coerceIn(-32768, 32767).toShort())
            out.putShort(outR.roundToInt().coerceIn(-32768, 32767).toShort())

            writeIndex = (writeIndex + 1) % MAX_DELAY_SAMPLES
            phase += phaseStep
            if (phase >= TWO_PI) phase -= TWO_PI
        }

        inputBuffer.position(inputBuffer.position() + usableBytes)
        out.flip()
    }

    /** Lee del buffer circular [delaySamples] muestras atrás, con interpolación lineal. */
    private fun readDelayed(buffer: FloatArray, delaySamples: Float): Float {
        val whole = delaySamples.toInt()
        val frac = delaySamples - whole
        val i0 = Math.floorMod(writeIndex - whole, MAX_DELAY_SAMPLES)
        val i1 = Math.floorMod(writeIndex - whole - 1, MAX_DELAY_SAMPLES)
        return buffer[i0] * (1f - frac) + buffer[i1] * frac
    }

    private fun approach(current: Float, target: Float, step: Float): Float = when {
        current < target -> minOf(current + step, target)
        current > target -> maxOf(current - step, target)
        else -> current
    }

    private fun clearState() {
        phase = 0f
        wet = 0f
        balanceCurrent = 0f
        writeIndex = 0
        lpL = 0f
        lpR = 0f
        delayL = FloatArray(MAX_DELAY_SAMPLES)
        delayR = FloatArray(MAX_DELAY_SAMPLES)
    }

    override fun onFlush() {
        // Seek o cambio de canción: limpia retardos y filtros para no arrastrar un eco del audio
        // anterior. NO toca los ajustes del usuario (enabled, periodo, intensidad, balance) ni
        // la fase, así el giro continúa sin saltos.
        writeIndex = 0
        lpL = 0f
        lpR = 0f
        delayL = FloatArray(MAX_DELAY_SAMPLES)
        delayR = FloatArray(MAX_DELAY_SAMPLES)
    }

    override fun onReset() {
        clearState()
        channelCount = 0
        sampleRateHz = 0
    }
}
