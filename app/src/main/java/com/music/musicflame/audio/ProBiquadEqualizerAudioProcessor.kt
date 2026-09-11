package com.music.musicflame.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * EQ PRO — motor completo (etapas 1, 2 y 3 del roadmap; la UI/licencia quedan para la etapa
 * final, ver notas de esa etapa más abajo).
 *
 * Ecualizador propio de 10 bandas implementado como AudioProcessor de Media3/ExoPlayer, con
 * filtros biquad tipo "peaking EQ" (fórmulas del RBJ Audio EQ Cookbook, de dominio público)
 * procesando el PCM directo en software. Reemplaza la dependencia del android.media.audiofx.
 * Equalizer nativo (que varía en calidad/número de bandas real por fabricante) por algo que
 * suena EXACTAMENTE igual en cualquier celular.
 *
 * Además de las 10 bandas, esta clase también hace:
 * - ETAPA 2: normalización de volumen entre canciones — un gain lineal adicional (calculado
 *   fuera de esta clase, por VolumeNormalizationAnalyzer) que se aplica DESPUÉS del EQ, sobre
 *   la señal ya filtrada.
 * - ETAPA 3: bypass A/B — cuando está activo, el PCM pasa de largo sin tocar (ni EQ ni
 *   normalización), para poder comparar "con EQ" vs "sin EQ" con un solo toque.
 *
 * IMPORTANTE - alcance de esta etapa:
 * - Esto NO está conectado a LicenseRepository.isProUnlocked() todavía. El gate de abajo
 *   (FORCE_ENABLED_FOR_TESTING) es un candado hardcodeado SOLO para que puedas probar en tu
 *   celular que el filtro realmente cambia el sonido. En la etapa final esto se reemplaza
 *   por la lectura real de la licencia y un array de ganancias en 0 dB (flat) hasta que el
 *   usuario mueva los sliders de la UI Pro.
 * - Las ganancias de banda (bandGainsDb) por ahora tienen un perfil de prueba fijo en forma
 *   de "V" (graves y agudos subidos, medios en 0 dB) para que el efecto sea imposible de no
 *   escuchar al probar. Esto se quita/reemplaza en la etapa de la UI.
 * - setBypassed()/isBypassed() ya están listos para que la UI de la etapa final los conecte
 *   a un botón; por ahora no hay ningún botón que los llame (nada cambia de comportamiento
 *   por default: arranca en false, igual que antes).
 * - Solo soporta PCM 16-bit (C.ENCODING_PCM_16BIT), que es el formato que usa ExoPlayer por
 *   defecto en este proyecto (no se activó enableFloatOutput). Si algún día se activa salida
 *   float, este processor hay que extenderlo aparte — por ahora, si le llega un encoding
 *   distinto, se declara a sí mismo "no soportado" y Media3 lo deja pasar de largo (pass-through),
 *   nunca truena la reproducción.
 * - NO usa ninguna librería externa. Todo el cálculo de coeficientes y el filtrado biquad
 *   está escrito a mano en Kotlin puro sobre el PCM, sin dependencias de licencia restrictiva
 *   (cumpliendo la restricción de licencia del proyecto: nada de TarsosDSP ni GPL/LGPL).
 */
class ProBiquadEqualizerAudioProcessor : BaseAudioProcessor() {

    companion object {
        /** Frecuencias centrales fijas, estándar ISO de 10 bandas (Hz). Fijas en esta etapa. */
        val BAND_CENTER_FREQUENCIES_HZ = floatArrayOf(
            31f, 62f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f
        )
        const val BAND_COUNT = 10

        /** Q fijo por banda (ancho de campana del peaking EQ). Un valor moderado, tipo
         *  ecualizador gráfico de 10 bandas comercial. Se puede exponer como parámetro
         *  más adelante si hace falta afinar el "sonido" del EQ. */
        private const val DEFAULT_Q = 1.4f

        private const val MAX_BAND_GAIN_DB = 15f
        private const val MIN_BAND_GAIN_DB = -15f

        // Rango de seguridad para el gain de normalización (ver setNormalizationGainDb).
        // VolumeNormalizationAnalyzer ya recorta a este mismo rango, esto es un segundo
        // cinturón de seguridad por si algún día se llama desde otro lado.
        private const val MAX_NORMALIZATION_GAIN_DB = 12f
        private const val MIN_NORMALIZATION_GAIN_DB = -12f

        // --- GATE DE PRUEBA HARDCODEADO — SOLO ETAPAS 1-3, QUITAR/REEMPLAZAR EN LA ETAPA FINAL ---
        // true = el processor SIEMPRE filtra el audio, sin importar licencia.
        // Cuando se conecte a LicenseRepository, esta constante desaparece y el gate real
        // se decide fuera de esta clase (ver nota en MusicPlaybackService.kt).
        const val FORCE_ENABLED_FOR_TESTING = true

        // Perfil de prueba en "V" para que el cambio se escuche fuerte y claro al probar.
        // 31/62 Hz y 8k/16k Hz subidos, resto en 0 dB. Se reemplaza por la UI real después.
        private val TEST_PROFILE_DB = floatArrayOf(
            8f, 6f, 0f, 0f, 0f, 0f, 0f, 0f, 5f, 7f
        )
    }

    private val bandGainsDb = FloatArray(BAND_COUNT) { TEST_PROFILE_DB[it] }

    // ETAPA 2 — gain de normalización de volumen, en lineal (no en dB) para no recalcular
    // el pow() en caliente por cada muestra. @Volatile porque se escribe desde el hilo
    // principal/una coroutine (MusicPlaybackService, al cambiar de canción) y se lee desde
    // el hilo de audio interno de ExoPlayer.
    @Volatile private var normalizationGainLinear: Float = 1f

    // ETAPA 3 — bypass A/B. @Volatile por la misma razón de arriba: lo toca la UI (hilo
    // principal) y lo lee el hilo de audio.
    @Volatile private var bypassed: Boolean = false

    private var channelCount = 0
    private var sampleRateHz = 0

    // Un juego de coeficientes por banda (compartido entre canales) + un estado
    // (memoria x1,x2,y1,y2) por banda POR CANAL, porque cada canal tiene su propia
    // señal y no se pueden mezclar los estados entre canal izquierdo/derecho.
    private var bandCoeffs: Array<BiquadCoeffs> = emptyArray()
    private var channelStates: Array<Array<BiquadState>> = emptyArray() // [canal][banda]

    /**
     * Cambia la ganancia (en dB, se recorta a [-15, 15]) de una banda en caliente, sin cortar
     * el audio. Pensado para conectarse a los sliders de la UI Pro en una etapa futura.
     */
    @Synchronized
    fun setBandGainDb(bandIndex: Int, gainDb: Float) {
        if (bandIndex !in 0 until BAND_COUNT) return
        bandGainsDb[bandIndex] = gainDb.coerceIn(MIN_BAND_GAIN_DB, MAX_BAND_GAIN_DB)
        rebuildCoefficients()
    }

    @Synchronized
    fun setAllBandGainsDb(gainsDb: FloatArray) {
        for (i in 0 until minOf(BAND_COUNT, gainsDb.size)) {
            bandGainsDb[i] = gainsDb[i].coerceIn(MIN_BAND_GAIN_DB, MAX_BAND_GAIN_DB)
        }
        rebuildCoefficients()
    }

    fun getBandGainDb(bandIndex: Int): Float =
        if (bandIndex in 0 until BAND_COUNT) bandGainsDb[bandIndex] else 0f

    /**
     * ETAPA 2 — fija el gain de normalización de volumen (en dB) de la canción actual. Se
     * recorta a [-12, 12] dB. Pasar 0f equivale a "sin normalización" (gain neutro).
     * Pensado para que MusicPlaybackService lo llame cada vez que cambia de canción, con el
     * valor que venga de VolumeNormalizationCacheRepository/VolumeNormalizationAnalyzer.
     */
    fun setNormalizationGainDb(gainDb: Float) {
        val clamped = gainDb.coerceIn(MIN_NORMALIZATION_GAIN_DB, MAX_NORMALIZATION_GAIN_DB)
        normalizationGainLinear = 10.0.pow(clamped / 20.0).toFloat()
    }

    /**
     * ETAPA 3 — activa/desactiva el bypass A/B. Con bypass activo, el audio sale exactamente
     * igual que entra (ni EQ ni normalización); pensado para un botón "con EQ / sin EQ" en la
     * UI de una etapa futura.
     */
    fun setBypassed(enabled: Boolean) {
        bypassed = enabled
    }

    fun isBypassed(): Boolean = bypassed

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (!FORCE_ENABLED_FOR_TESTING) {
            // Camino de la etapa final para usuarios sin Pro: no tocar el formato, Media3
            // trata este processor como inactivo y el audio pasa de largo sin overhead.
            return AudioProcessor.AudioFormat.NOT_SET
        }
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            // No soportamos este encoding todavía (ver nota de clase). Declinar limpio.
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }

        channelCount = inputAudioFormat.channelCount
        sampleRateHz = inputAudioFormat.sampleRate
        channelStates = Array(channelCount) { Array(BAND_COUNT) { BiquadState() } }
        rebuildCoefficients()

        // Mismo sampleRate/encoding/canales de entrada: solo transformamos las muestras,
        // no cambiamos la forma del audio.
        return inputAudioFormat
    }

    private fun rebuildCoefficients() {
        if (sampleRateHz <= 0) return
        bandCoeffs = Array(BAND_COUNT) { band ->
            computePeakingCoefficients(
                f0 = BAND_CENTER_FREQUENCIES_HZ[band],
                fs = sampleRateHz.toFloat(),
                q = DEFAULT_Q,
                gainDb = bandGainsDb[band]
            )
        }
    }

    /**
     * Coeficientes de un filtro "peaking EQ" (campana), fórmulas del RBJ Audio EQ Cookbook
     * (Robert Bristow-Johnson) — de dominio público, es el estándar de facto para este tipo
     * de filtro y no depende de ninguna librería de terceros.
     */
    private fun computePeakingCoefficients(f0: Float, fs: Float, q: Float, gainDb: Float): BiquadCoeffs {
        if (gainDb == 0f) return BiquadCoeffs.IDENTITY

        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * PI * (f0 / fs)
        val cosW0 = cos(w0)
        val sinW0 = sin(w0)
        val alpha = sinW0 / (2.0 * q)

        val b0 = 1.0 + alpha * a
        val b1 = -2.0 * cosW0
        val b2 = 1.0 - alpha * a
        val a0 = 1.0 + alpha / a
        val a1 = -2.0 * cosW0
        val a2 = 1.0 - alpha / a

        return BiquadCoeffs(
            b0 = (b0 / a0).toFloat(),
            b1 = (b1 / a0).toFloat(),
            b2 = (b2 / a0).toFloat(),
            a1 = (a1 / a0).toFloat(),
            a2 = (a2 / a0).toFloat()
        )
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0 || channelCount == 0) return

        if (bypassed) {
            // ETAPA 3 — A/B bypass: copiamos el PCM tal cual, sin pasar por el EQ ni la
            // normalización, para poder comparar "con EQ" vs "sin EQ" al toque. Copia
            // directa de bytes, sin decodificar muestra por muestra: la señal que sale es
            // BIT A BIT la misma que entra.
            val outputBuffer = replaceOutputBuffer(remaining)
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            return
        }

        val frameSizeBytes = channelCount * 2 // PCM 16-bit = 2 bytes por muestra
        val frameCount = remaining / frameSizeBytes
        val usableBytes = frameCount * frameSizeBytes
        if (frameCount == 0) return

        val outputBuffer = replaceOutputBuffer(usableBytes)

        val inDup = inputBuffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        outputBuffer.order(ByteOrder.LITTLE_ENDIAN)

        val coeffs = bandCoeffs
        val normGain = normalizationGainLinear
        for (frame in 0 until frameCount) {
            for (ch in 0 until channelCount) {
                var sample = inDup.short.toFloat() / 32768f
                val states = channelStates[ch]
                for (band in 0 until BAND_COUNT) {
                    sample = states[band].process(sample, coeffs[band])
                }
                // ETAPA 2 — normalización de volumen: se aplica DESPUÉS del EQ, sobre la
                // señal ya filtrada, así el gain de normalización compensa el volumen real
                // que va a sonar (con el EQ puesto), no el volumen "crudo" del archivo.
                sample *= normGain
                val clamped = (sample * 32768f).roundToInt().coerceIn(-32768, 32767)
                outputBuffer.putShort(clamped.toShort())
            }
        }

        // Avanzamos el buffer de entrada exactamente lo que consumimos (por si sobraban
        // bytes sueltos de un frame incompleto, quedan para la siguiente llamada).
        inputBuffer.position(inputBuffer.position() + usableBytes)
        outputBuffer.flip()
    }

    override fun onFlush() {
        // Limpia la memoria de los filtros (x1,x2,y1,y2) para no arrastrar un "eco" del
        // audio anterior al hacer seek o cambiar de canción. NO toca bandGainsDb,
        // normalizationGainLinear ni bypassed: esos son ajustes del usuario/de la canción
        // actual, no memoria interna del filtro, y deben sobrevivir a un flush.
        for (states in channelStates) {
            for (state in states) {
                state.reset()
            }
        }
    }

    override fun onReset() {
        channelStates = emptyArray()
        bandCoeffs = emptyArray()
        channelCount = 0
        sampleRateHz = 0
    }

    /** Estado (memoria) de UN filtro biquad para UNA banda en UN canal. */
    private class BiquadState {
        private var x1 = 0f
        private var x2 = 0f
        private var y1 = 0f
        private var y2 = 0f

        fun process(x0: Float, c: BiquadCoeffs): Float {
            val y0 = c.b0 * x0 + c.b1 * x1 + c.b2 * x2 - c.a1 * y1 - c.a2 * y2
            x2 = x1
            x1 = x0
            y2 = y1
            y1 = y0
            return y0
        }

        fun reset() {
            x1 = 0f
            x2 = 0f
            y1 = 0f
            y2 = 0f
        }
    }

    private data class BiquadCoeffs(
        val b0: Float,
        val b1: Float,
        val b2: Float,
        val a1: Float,
        val a2: Float
    ) {
        companion object {
            // Filtro identidad: y[n] = x[n], para banda en 0 dB sin gastar cálculo de más.
            val IDENTITY = BiquadCoeffs(1f, 0f, 0f, 0f, 0f)
        }
    }
}
