package com.music.musicflame.voice.wake

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import java.nio.FloatBuffer
import kotlin.math.sqrt

/**
 * Convierte audio (16 kHz, mono, PCM de 16 bits, bloques de 1280 muestras = 80 ms) en "huellas" de voz de 96 números.
 *
 * Usa los dos modelos pequeños de openWakeWord (assets/wake): un espectrograma mel y el modelo de embeddings de voz de
 * Google. NO reconocen palabras: solo describen cómo suena el audio, por eso sirven con cualquier idioma y cualquier
 * nombre. Todo corre dentro del teléfono con ONNX Runtime.
 *
 * Es de uso en flujo continuo: se llama [feed] con cada bloque y, cuando ya hay audio suficiente (~0.8 s), devuelve una
 * huella normalizada (largo 1) cada 80 ms.
 */
class WakeFeatureExtractor(context: Context) : AutoCloseable {

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val melSession: OrtSession
    private val embSession: OrtSession
    private val melInput: String
    private val embInput: String

    private val raw = FloatArray(RAW_KEEP)
    private val melFrames = ArrayList<FloatArray>()

    init {
        val opts = OrtSession.SessionOptions()
        opts.setIntraOpNumThreads(1)
        opts.setInterOpNumThreads(1)
        melSession = env.createSession(context.assets.open(MEL_ASSET).use { it.readBytes() }, opts)
        embSession = env.createSession(context.assets.open(EMB_ASSET).use { it.readBytes() }, opts)
        melInput = melSession.inputNames.first()
        embInput = embSession.inputNames.first()
    }

    /** Olvida todo el audio anterior (se usa al empezar una frase nueva). */
    fun reset() {
        java.util.Arrays.fill(raw, 0f)
        melFrames.clear()
    }

    /** [chunk] debe tener exactamente [CHUNK] muestras. Devuelve la huella (96 floats, norma 1) o null si aún no hay. */
    fun feed(chunk: ShortArray): FloatArray? {
        System.arraycopy(raw, CHUNK, raw, 0, RAW_KEEP - CHUNK)
        for (i in 0 until CHUNK) raw[RAW_KEEP - CHUNK + i] = chunk[i].toFloat()

        melFrames.addAll(runMel())
        while (melFrames.size > MEL_KEEP) melFrames.removeAt(0)
        if (melFrames.size < EMB_FRAMES) return null
        return runEmbedding()
    }

    private fun runMel(): List<FloatArray> {
        OnnxTensor.createTensor(env, FloatBuffer.wrap(raw), longArrayOf(1L, RAW_KEEP.toLong())).use { input ->
            melSession.run(mapOf(melInput to input)).use { result ->
                val out = result[0] as OnnxTensor
                val fb = out.floatBuffer
                val arr = FloatArray(fb.remaining())
                fb.get(arr)
                val n = arr.size / MEL_BINS
                // Misma transformación que openWakeWord: x / 10 + 2.
                return List(n) { f -> FloatArray(MEL_BINS) { k -> arr[f * MEL_BINS + k] / 10f + 2f } }
            }
        }
    }

    private fun runEmbedding(): FloatArray {
        val buf = FloatArray(EMB_FRAMES * MEL_BINS)
        val start = melFrames.size - EMB_FRAMES
        for (f in 0 until EMB_FRAMES) System.arraycopy(melFrames[start + f], 0, buf, f * MEL_BINS, MEL_BINS)
        OnnxTensor.createTensor(env, FloatBuffer.wrap(buf), longArrayOf(1L, EMB_FRAMES.toLong(), MEL_BINS.toLong(), 1L)).use { input ->
            embSession.run(mapOf(embInput to input)).use { result ->
                val out = result[0] as OnnxTensor
                val fb = out.floatBuffer
                val v = FloatArray(fb.remaining())
                fb.get(v)
                var s = 0f
                for (x in v) s += x * x
                val inv = 1f / (sqrt(s) + 1e-9f)
                for (i in v.indices) v[i] *= inv
                return v
            }
        }
    }

    override fun close() {
        try { melSession.close() } catch (_: Exception) {}
        try { embSession.close() } catch (_: Exception) {}
    }

    companion object {
        const val CHUNK = 1280                 // 80 ms a 16 kHz
        private const val RAW_KEEP = CHUNK + 480
        private const val MEL_BINS = 32
        private const val EMB_FRAMES = 76
        private const val MEL_KEEP = 120
        private const val MEL_ASSET = "wake/melspectrogram.onnx"
        private const val EMB_ASSET = "wake/embedding_model.onnx"
    }
}
