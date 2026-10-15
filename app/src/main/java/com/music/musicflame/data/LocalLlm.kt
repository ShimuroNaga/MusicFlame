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
    private const val MAX_TOKENS = 512                      // prompt + respuesta: menos tokens = KV cache más chico y respuesta más rápida
    private const val HISTORY_CHAR_BUDGET = 600             // historial corto = prefill corto = primer trozo de respuesta antes

    /**
     * Acelera la inferencia con la GPU del teléfono (varias veces más rápido que CPU en la mayoría de equipos).
     * Si la GPU no soporta el modelo, o el equipo se cierra al cargarla, se desactiva sola y se usa CPU
     * (ver [engineFor]). Ponlo en false para forzar CPU siempre.
     */
    private const val USE_GPU = true

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

    @Volatile private var engineIsGpu = false

    private fun enginePrefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences("ai_engine", Context.MODE_PRIVATE)

    private fun buildEngine(ctx: Context, gpu: Boolean): LlmInference = LlmInference.createFromOptions(
        ctx.applicationContext,
        LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelFile(ctx).absolutePath)
            .setPreferredBackend(if (gpu) LlmInference.Backend.GPU else LlmInference.Backend.CPU)
            .setMaxTokens(MAX_TOKENS)
            .build()
    )

    /**
     * Intenta GPU primero. Protección contra bucles de cierre: antes de cargar se deja una marca
     * ("gpu_loading"); si la app muere cargando la GPU (fallo nativo, no capturable), la marca queda
     * puesta y en el siguiente arranque la GPU se descarta para siempre en este equipo.
     */
    private fun engineFor(ctx: Context): LlmInference {
        engine?.let { return it }
        val prefs = enginePrefs(ctx)
        if (prefs.getBoolean("gpu_loading", false))
            prefs.edit().putBoolean("gpu_broken", true).putBoolean("gpu_loading", false).apply()
        if (USE_GPU && !prefs.getBoolean("gpu_broken", false)) {
            prefs.edit().putBoolean("gpu_loading", true).commit()   // commit (síncrono): debe quedar escrito antes de un posible cierre
            try {
                val e = buildEngine(ctx, gpu = true)
                prefs.edit().putBoolean("gpu_loading", false).apply()
                engineIsGpu = true
                engine = e
                return e
            } catch (_: Throwable) {
                prefs.edit().putBoolean("gpu_loading", false).putBoolean("gpu_broken", true).apply()
            }
        }
        engineIsGpu = false
        return buildEngine(ctx, gpu = false).also { engine = it }
    }

    private fun markGpuBroken(ctx: Context) {
        enginePrefs(ctx).edit().putBoolean("gpu_broken", true).putBoolean("gpu_loading", false).apply()
        unload()
    }

    /** Carga el modelo en RAM antes de que el usuario escriba (así el primer mensaje no espera la carga). */
    suspend fun preload(ctx: Context) = withContext(Dispatchers.IO) {
        if (!isReady(ctx)) return@withContext
        try { mutex.withLock { engineFor(ctx) } } catch (_: Throwable) {}
    }

    /**
     * Responde en streaming: [onPartial] recibe el texto acumulado cada vez que el modelo suelta un trozo
     * (se llama desde un hilo de fondo). Devuelve la respuesta final ya limpia.
     */
    suspend fun reply(
        ctx: Context, aiName: String, nowPlaying: String?, history: List<AiMessage>,
        onPartial: (String) -> Unit = {}
    ): String = withContext(Dispatchers.IO) {
        mutex.withLock {
            val prompt = buildPrompt(aiName, nowPlaying, history)
            try {
                generate(engineFor(ctx), prompt, onPartial)
            } catch (t: Throwable) {
                // La GPU cargó pero falló al generar (modelo/driver incompatible): reintento una vez en CPU.
                if (t is kotlinx.coroutines.CancellationException || !engineIsGpu) throw t
                markGpuBroken(ctx)
                generate(engineFor(ctx), prompt, onPartial)
            }
        }
    }

    private fun generate(llm: LlmInference, prompt: String, onPartial: (String) -> Unit): String {
        val acc = StringBuilder()
        val future = llm.generateResponseAsync(prompt) { partial, _ ->
            acc.append(partial)
            // repairText sale al instante (O(n) sin asignar) cuando no hay bytes sueltos que reparar.
            val shown = repairText(acc.toString().substringBefore("<|im_end|>").replace("<|im_start|>", "")).trim()
            if (shown.isNotEmpty()) onPartial(shown)
        }
        val raw = future.get()
        val text = if (raw.isNullOrBlank()) acc.toString() else raw
        val clean = repairText(text.substringBefore("<|im_end|>").replace("<|im_start|>", "")).trim()
        if (clean.isEmpty()) throw IllegalStateException("respuesta vacía")
        return clean
    }

    // ---------- Reparación de texto: el tokenizer a veces entrega bytes UTF-8 sueltos ("Â¡" en vez de "¡") ----------
    /** Inversa de bytes_to_unicode de GPT-2/Qwen: carácter visible -> byte original. */
    private val byteLevelMap: Map<Char, Int> by lazy {
        val keep = ArrayList<Int>()
        keep.addAll(33..126); keep.addAll(161..172); keep.addAll(174..255)
        val map = HashMap<Char, Int>()
        var extra = 0
        for (b in 0..255) {
            if (b in keep) map[b.toChar()] = b else { map[(256 + extra).toChar()] = b; extra++ }
        }
        map
    }

    /**
     * Si el texto trae secuencias UTF-8 "partidas" (p. ej. "Â¡Hola" o "Ã©"), las convierte al carácter real.
     * Solo reemplaza secuencias completas y válidas; el resto del texto no se toca.
     */
    fun repairText(text: String): String {
        if (text.none { it.code in 0x80..0x143 }) return text
        val out = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val b = byteLevelMap[text[i]]
            val need = when (b) {
                null -> 0
                in 0xC2..0xDF -> 1
                in 0xE0..0xEF -> 2
                in 0xF0..0xF4 -> 3
                else -> 0
            }
            if (need > 0 && i + need <= text.length - 1) {
                val bytes = ByteArray(need + 1)
                bytes[0] = b!!.toByte()
                var ok = true
                for (k in 1..need) {
                    val c = byteLevelMap[text[i + k]]
                    if (c == null || c !in 0x80..0xBF) { ok = false; break }
                    bytes[k] = c.toByte()
                }
                if (ok) {
                    val decoded = try {
                        Charsets.UTF_8.newDecoder()
                            .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                            .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                            .decode(java.nio.ByteBuffer.wrap(bytes)).toString()
                    } catch (_: Exception) { null }
                    if (decoded != null) { out.append(decoded); i += need + 1; continue }
                }
            }
            out.append(text[i]); i++
        }
        return out.toString()
    }

    /** Formato ChatML de Qwen. El modelo es chico: system corto y solo los últimos turnos que quepan. */
    private fun buildPrompt(aiName: String, nowPlaying: String?, history: List<AiMessage>): String {
        val system = buildString {
            append("Eres $aiName, asistente de música dentro de la app MusicFlame. ")
            append("Responde en el idioma del usuario, en máximo 2 oraciones cortas, sin Markdown ni listas. ")
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
