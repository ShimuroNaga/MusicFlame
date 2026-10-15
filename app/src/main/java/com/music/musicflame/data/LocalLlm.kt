package com.music.musicflame.data

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL

/**
 * IA local: Qwen2.5-1.5B-Instruct (Alibaba Cloud / Qwen team, licencia Apache-2.0) convertido por
 * litert-community para MediaPipe (también Apache-2.0). Corre 100% en el teléfono, sin internet.
 * Se usa el 1.5B y NO el 3B porque el Qwen2.5-3B tiene otra licencia (Qwen Research License, no comercial).
 */
object LocalLlm {
    const val MODEL_NAME = "Qwen2.5-1.5B-Instruct"
    const val MODEL_URL =
        "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/main/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv1280.task"
    const val APPROX_SIZE_LABEL = "≈1.6 GB"
    private const val MODEL_FILE = "Qwen2.5-1.5B-Instruct_q8_ekv1280.task"
    private const val MIN_VALID_BYTES = 500L * 1024 * 1024   // un archivo menor a esto está incompleto/corrupto
    private const val MAX_TOKENS = 1024                     // prompt + respuesta (el modelo trae ventana de 1280)
    private const val HISTORY_CHAR_BUDGET = 1500

    private fun dir(ctx: Context) = File(ctx.applicationContext.filesDir, "ai_models")
    fun modelFile(ctx: Context) = File(dir(ctx), MODEL_FILE)
    private fun partFile(ctx: Context) = File(dir(ctx), "$MODEL_FILE.part")

    fun isReady(ctx: Context): Boolean = modelFile(ctx).let { it.isFile && it.length() > MIN_VALID_BYTES }
    fun sizeOnDiskMb(ctx: Context): Long = modelFile(ctx).length() / (1024 * 1024)

    private val mutex = Mutex()
    @Volatile private var engine: LlmInference? = null

    /** Libera la RAM del modelo (se vuelve a cargar solo en el siguiente mensaje). */
    fun unload() { try { engine?.close() } catch (_: Throwable) {}; engine = null }

    fun deleteModel(ctx: Context) {
        unload()
        modelFile(ctx).delete(); partFile(ctx).delete()
    }

    /**
     * Descarga con reanudación (si se corta, continúa donde quedó). [onProgress] recibe 0f..1f.
     * Se cancela cancelando la corrutina. Lanza excepción si falla.
     */
    suspend fun download(ctx: Context, onProgress: (Float) -> Unit) = withContext(Dispatchers.IO) {
        val d = dir(ctx); if (!d.exists()) d.mkdirs()
        val part = partFile(ctx)
        val existing = if (part.isFile) part.length() else 0L
        val conn = URL(MODEL_URL).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 20_000; conn.readTimeout = 30_000
            if (existing > 0) conn.setRequestProperty("Range", "bytes=$existing-")
            val code = conn.responseCode
            if (code != 200 && code != 206) throw java.io.IOException("HTTP $code")
            val resumed = code == 206
            val start = if (resumed) existing else 0L
            val total = start + conn.contentLengthLong.coerceAtLeast(0L)
            RandomAccessFile(part, "rw").use { raf ->
                if (resumed) raf.seek(existing) else raf.setLength(0)
                conn.inputStream.use { input ->
                    val buf = ByteArray(256 * 1024)
                    var done = start
                    var lastReport = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buf); if (n < 0) break
                        raf.write(buf, 0, n); done += n
                        val now = System.currentTimeMillis()
                        if (total > 0 && now - lastReport > 300) { lastReport = now; onProgress((done.toFloat() / total).coerceIn(0f, 1f)) }
                    }
                    if (total > 0 && done < total) throw java.io.IOException("Descarga incompleta")
                }
            }
            if (part.length() < MIN_VALID_BYTES) { part.delete(); throw java.io.IOException("Archivo inválido") }
            val dest = modelFile(ctx)
            dest.delete()
            if (!part.renameTo(dest)) throw java.io.IOException("No se pudo guardar el modelo")
            onProgress(1f)
        } finally { conn.disconnect() }
    }

    suspend fun reply(ctx: Context, aiName: String, nowPlaying: String?, history: List<AiMessage>): String =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val llm = engine ?: LlmInference.createFromOptions(
                    ctx.applicationContext,
                    LlmInference.LlmInferenceOptions.builder()
                        .setModelPath(modelFile(ctx).absolutePath)
                        .setMaxTokens(MAX_TOKENS)
                        .build()
                ).also { engine = it }
                val raw = llm.generateResponse(buildPrompt(aiName, nowPlaying, history))
                val clean = raw.substringBefore("<|im_end|>").replace("<|im_start|>", "").trim()
                if (clean.isEmpty()) throw IllegalStateException("respuesta vacía")
                clean
            }
        }

    /** Formato ChatML de Qwen. El modelo es chico: system corto y solo los últimos turnos que quepan. */
    private fun buildPrompt(aiName: String, nowPlaying: String?, history: List<AiMessage>): String {
        val system = buildString {
            append("Eres $aiName, asistente de música dentro de la app MusicFlame. ")
            append("Responde en el idioma del usuario, en 2 a 3 oraciones, sin Markdown ni listas. ")
            append("Si no estás seguro de un dato (año, BPM, álbum), dilo en vez de inventarlo.")
            if (!nowPlaying.isNullOrBlank()) append(" Ahora suena: \"$nowPlaying\".")
        }
        val turns = ArrayDeque<AiMessage>()
        var used = 0
        for (m in history.filter { !it.isError }.asReversed()) {
            val len = m.text.length.coerceAtMost(600)
            if (used + len > HISTORY_CHAR_BUDGET && turns.isNotEmpty()) break
            turns.addFirst(m); used += len
        }
        while (turns.isNotEmpty() && !turns.first().fromUser) turns.removeFirst()
        return buildString {
            append("<|im_start|>system\n").append(system).append("<|im_end|>\n")
            turns.forEach {
                append("<|im_start|>").append(if (it.fromUser) "user" else "assistant").append('\n')
                append(it.text.take(600)).append("<|im_end|>\n")
            }
            append("<|im_start|>assistant\n")
        }
    }
}
