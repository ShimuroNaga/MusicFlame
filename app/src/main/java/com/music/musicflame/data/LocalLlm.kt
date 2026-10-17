package com.music.musicflame.data

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.music.musicflame.R
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

/** Motor que ejecuta cada modelo. MediaPipe lee .task; LiteRT-LM lee .litertlm. */
enum class AiEngineKind { MEDIAPIPE, LITERT_LM }

/**
 * Modelos de IA local que el usuario puede elegir. SOLO UNO descargado a la vez: para cambiar de modelo
 * hay que borrar el actual (ver [LocalLlm.download]).
 *
 * Licencias: Qwen2.5-1.5B, Qwen3-0.6B y Gemma 4 son Apache-2.0. Gemma 3 va con los Términos de Uso de Gemma
 * (https://ai.google.dev/gemma/terms) + su política de uso prohibido; su repositorio de Hugging Face exige sesión,
 * por eso se descarga de un archivo que TÚ hospedas (ver [GEMMA3_URL]).
 */
enum class AiModel(
    val id: String,
    val displayName: String,
    val url: String,
    val fileName: String,
    val sizeLabel: String,
    val minBytes: Long,
    val engine: AiEngineKind,
    val modelPageUrl: String,
    val licenseName: String,
    val licenseUrl: String,
    val descRes: Int,
    /** El tokenizer de Qwen2.5 en MediaPipe a veces entrega bytes UTF-8 sueltos ("Â¡"); solo ese necesita reparación. */
    val needsByteRepair: Boolean = false,
    /** Qwen3 "sin pensar" no define rol system en su plantilla: las reglas se mandan dentro del mensaje del usuario. */
    val systemInUser: Boolean = false
) {
    QWEN25_15B(
        "qwen25_15b", "Qwen2.5-1.5B-Instruct",
        "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/main/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
        "Qwen2.5-1.5B-Instruct_q8_ekv1280.task",   // mismo nombre de siempre: quien ya lo tiene descargado no pierde nada
        "≈1.6 GB", 500L * 1024 * 1024, AiEngineKind.MEDIAPIPE,
        "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct", "Apache-2.0", "https://www.apache.org/licenses/LICENSE-2.0",
        R.string.ai_model_desc_qwen25, needsByteRepair = true
    ),
    GEMMA3_1B(
        "gemma3_1b", "Gemma 3 1B IT",
        LocalLlm.GEMMA3_URL,
        "gemma3-1b-it-int4.task",
        "≈555 MB", 300L * 1024 * 1024, AiEngineKind.MEDIAPIPE,
        "https://huggingface.co/google/gemma-3-1b-it", "Gemma Terms of Use", "https://ai.google.dev/gemma/terms",
        R.string.ai_model_desc_gemma3
    ),
    GEMMA4_E2B(
        "gemma4_e2b", "Gemma 4 E2B",
        "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm",
        "gemma-4-E2B-it.litertlm",
        "≈2.6 GB", 1500L * 1024 * 1024, AiEngineKind.LITERT_LM,
        "https://huggingface.co/google/gemma-4-E2B-it", "Apache-2.0", "https://www.apache.org/licenses/LICENSE-2.0",
        R.string.ai_model_desc_gemma4
    ),
    QWEN3_06B(
        "qwen3_06b", "Qwen3 0.6B",
        // Versión "sin pensar" (nothink): responde directo, sin <think>, y es ~2x más rápida en CPU.
        "https://huggingface.co/litert-community/Qwen3-0.6B-int4/resolve/main/qwen3_0.6b_nothink_q4_block32_ekv1280.litertlm",
        "qwen3_0.6b_nothink_q4_block32_ekv1280.litertlm",
        "≈330 MB", 200L * 1024 * 1024, AiEngineKind.LITERT_LM,
        "https://huggingface.co/Qwen/Qwen3-0.6B", "Apache-2.0", "https://www.apache.org/licenses/LICENSE-2.0",
        R.string.ai_model_desc_qwen3, systemInUser = true
    )
}

/**
 * IA local. Corre 100% en el teléfono, sin internet. Los modelos NO van dentro del APK: se descargan una vez
 * a files/ai_models y se borran desde Ajustes › IA.
 */
object LocalLlm {
    /**
     * Gemma 3 1B: el repositorio oficial está "gated" (exige aceptar la licencia con sesión de Hugging Face),
     * así que una URL directa no funciona. Descarga gemma3-1b-it-int4.task desde
     * https://huggingface.co/litert-community/Gemma3-1B-IT y súbelo como asset de un Release de GitHub
     * (tag "ai-models"); la URL queda así. Cambia el usuario/tag si usas otro.
     */
    const val GEMMA3_URL =
        "https://github.com/ShimuroNaga/MusicFlame/releases/download/ai-models/gemma3-1b-it-int4.task"

    private const val MAX_TOKENS = 1024                     // prompt (con datos de la biblioteca) + respuesta
    private const val HISTORY_CHAR_BUDGET = 600             // historial corto = prefill corto = primer trozo de respuesta antes
    private const val HISTORY_CHAR_BUDGET_WITH_DATA = 280   // con datos de biblioteca en el prompt el historial se acorta más

    /** Intenta GPU primero; si el equipo no la soporta se desactiva sola y se usa CPU. false = forzar CPU. */
    private const val USE_GPU = true

    private fun dir(ctx: Context) = File(ctx.applicationContext.filesDir, "ai_models")
    fun modelFile(ctx: Context, m: AiModel) = File(dir(ctx), m.fileName)
    private fun partFile(ctx: Context, m: AiModel) = File(dir(ctx), "${m.fileName}.part")

    fun isReady(ctx: Context, m: AiModel): Boolean = modelFile(ctx, m).let { it.isFile && it.length() > m.minBytes }

    /** El modelo descargado (solo puede haber uno) o null si no hay ninguno. */
    fun installed(ctx: Context): AiModel? = AiModel.values().firstOrNull { isReady(ctx, it) }
    fun isReady(ctx: Context): Boolean = installed(ctx) != null
    fun sizeOnDiskMb(ctx: Context): Long = installed(ctx)?.let { modelFile(ctx, it).length() / (1024 * 1024) } ?: 0L

    private val mutex = Mutex()
    @Volatile private var mpEngine: LlmInference? = null
    @Volatile private var ltEngine: Engine? = null
    @Volatile private var engineIsGpu = false

    /** Libera la RAM del modelo (se vuelve a cargar solo en el siguiente mensaje). */
    fun unload() {
        try { mpEngine?.close() } catch (_: Throwable) {}
        try { ltEngine?.close() } catch (_: Throwable) {}
        mpEngine = null; ltEngine = null
    }

    /** Libera el modelo cuando ya no se usa; espera a que termine cualquier respuesta en curso (mismo mutex). */
    suspend fun unloadWhenIdle() = withContext(Dispatchers.IO) { mutex.withLock { unload() } }

    /** Borra el modelo descargado (y cualquier descarga a medias) para poder elegir otro. */
    fun deleteModel(ctx: Context) {
        unload()
        AiModel.values().forEach { modelFile(ctx, it).delete(); partFile(ctx, it).delete() }
    }

    /**
     * Descarga [m] con reanudación (si se corta, continúa donde quedó). [onProgress] recibe 0f..1f.
     * Se cancela cancelando la corrutina. Solo se permite si no hay OTRO modelo descargado.
     */
    suspend fun download(ctx: Context, m: AiModel, onProgress: (Float) -> Unit) = withContext(Dispatchers.IO) {
        val other = installed(ctx)
        if (other != null && other != m) throw IllegalStateException("Primero borra ${other.displayName}")
        val d = dir(ctx); if (!d.exists()) d.mkdirs()
        val part = partFile(ctx, m)
        val existing = if (part.isFile) part.length() else 0L
        val conn = URL(m.url).openConnection() as HttpURLConnection
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
            if (part.length() < m.minBytes) { part.delete(); throw java.io.IOException("Archivo inválido") }
            val dest = modelFile(ctx, m)
            dest.delete()
            if (!part.renameTo(dest)) throw java.io.IOException("No se pudo guardar el modelo")
            onProgress(1f)
        } finally { conn.disconnect() }
    }

    // ---------- Motores ----------
    private fun enginePrefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences("ai_engine", Context.MODE_PRIVATE)

    // Claves de "GPU rota" por motor (las de MediaPipe conservan el nombre antiguo).
    private fun loadingKey(m: AiModel) = if (m.engine == AiEngineKind.MEDIAPIPE) "gpu_loading" else "lt_gpu_loading"
    private fun brokenKey(m: AiModel) = if (m.engine == AiEngineKind.MEDIAPIPE) "gpu_broken" else "lt_gpu_broken"

    private fun buildMp(ctx: Context, m: AiModel, gpu: Boolean): LlmInference = LlmInference.createFromOptions(
        ctx.applicationContext,
        LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelFile(ctx, m).absolutePath)
            .setPreferredBackend(if (gpu) LlmInference.Backend.GPU else LlmInference.Backend.CPU)
            .setMaxTokens(MAX_TOKENS)
            .build()
    )

    private suspend fun buildLt(ctx: Context, m: AiModel, gpu: Boolean): Engine {
        val e = Engine(
            EngineConfig(
                modelPath = modelFile(ctx, m).absolutePath,
                backend = if (gpu) Backend.GPU() else Backend.CPU(),
                cacheDir = ctx.applicationContext.cacheDir.path   // acelera la 2.ª carga
            )
        )
        try { e.initialize() } catch (t: Throwable) { try { e.close() } catch (_: Throwable) {}; throw t }
        return e
    }

    /**
     * Intenta GPU primero. Protección contra bucles de cierre: antes de cargar se deja una marca ("loading");
     * si la app muere cargando la GPU (fallo nativo, no capturable), la marca queda puesta y en el siguiente
     * arranque la GPU se descarta para siempre en este equipo (por motor).
     */
    private suspend fun <T> loadWithGpuFallback(ctx: Context, m: AiModel, build: suspend (Boolean) -> T): T {
        val prefs = enginePrefs(ctx)
        val lk = loadingKey(m); val bk = brokenKey(m)
        if (prefs.getBoolean(lk, false)) prefs.edit().putBoolean(bk, true).putBoolean(lk, false).apply()
        if (USE_GPU && !prefs.getBoolean(bk, false)) {
            prefs.edit().putBoolean(lk, true).commit()   // commit (síncrono): debe quedar escrito antes de un posible cierre
            try {
                val e = build(true)
                prefs.edit().putBoolean(lk, false).apply()
                engineIsGpu = true
                return e
            } catch (_: Throwable) {
                prefs.edit().putBoolean(lk, false).putBoolean(bk, true).apply()
            }
        }
        engineIsGpu = false
        return build(false)
    }

    private suspend fun mpEngineFor(ctx: Context, m: AiModel): LlmInference =
        mpEngine ?: loadWithGpuFallback(ctx, m) { gpu -> buildMp(ctx, m, gpu) }.also { mpEngine = it }

    private suspend fun ltEngineFor(ctx: Context, m: AiModel): Engine =
        ltEngine ?: loadWithGpuFallback(ctx, m) { gpu -> buildLt(ctx, m, gpu) }.also { ltEngine = it }

    private fun markGpuBroken(ctx: Context, m: AiModel) {
        enginePrefs(ctx).edit().putBoolean(brokenKey(m), true).putBoolean(loadingKey(m), false).apply()
        unload()
    }

    /** Carga el modelo en RAM antes de que el usuario escriba (así el primer mensaje no espera la carga). */
    suspend fun preload(ctx: Context) = withContext(Dispatchers.IO) {
        val m = installed(ctx) ?: return@withContext
        try {
            mutex.withLock { if (m.engine == AiEngineKind.MEDIAPIPE) mpEngineFor(ctx, m) else ltEngineFor(ctx, m) }
        } catch (_: Throwable) {}
    }

    /**
     * Responde en streaming: [onPartial] recibe el texto acumulado cada vez que el modelo suelta un trozo
     * (se llama desde un hilo de fondo). Devuelve la respuesta final ya limpia.
     */
    suspend fun reply(
        ctx: Context, aiName: String, nowPlaying: String?, history: List<AiMessage>,
        libraryBlock: String? = null,
        lang: String = "es",
        onPartial: (String) -> Unit = {}
    ): String = withContext(Dispatchers.IO) {
        val m = installed(ctx) ?: throw IllegalStateException("modelo no descargado")
        mutex.withLock {
            val system = buildSystem(aiName, nowPlaying, libraryBlock, lang)
            val turns = buildTurns(history, if (libraryBlock.isNullOrBlank()) HISTORY_CHAR_BUDGET else HISTORY_CHAR_BUDGET_WITH_DATA)
            suspend fun run(): String = when (m.engine) {
                AiEngineKind.MEDIAPIPE -> generateMp(mpEngineFor(ctx, m), m, promptFor(m, system, turns), onPartial)
                AiEngineKind.LITERT_LM -> generateLt(ltEngineFor(ctx, m), system, turns, m.systemInUser, lang, onPartial)
            }
            try {
                run()
            } catch (t: Throwable) {
                // La GPU cargó pero falló al generar (modelo/driver incompatible): reintento una vez en CPU.
                if (t is kotlinx.coroutines.CancellationException || !engineIsGpu) throw t
                markGpuBroken(ctx, m)
                run()
            }
        }
    }

    // ---------- MediaPipe (.task) ----------
    private fun cleanMp(m: AiModel, raw: String): String {
        val t = raw.substringBefore("<|im_end|>").substringBefore("<end_of_turn>")
            .replace("<|im_start|>", "").replace("<start_of_turn>", "")
        return (if (m.needsByteRepair) repairText(t) else t).trim()
    }

    private fun generateMp(llm: LlmInference, m: AiModel, prompt: String, onPartial: (String) -> Unit): String {
        val acc = StringBuilder()
        var lastEmit = 0L
        val future = llm.generateResponseAsync(prompt) { partial, _ ->
            acc.append(partial)
            // Se arma y limpia el texto SOLO ~20 veces por segundo, no en cada token.
            val now = android.os.SystemClock.uptimeMillis()
            if (now - lastEmit >= 50L) {
                lastEmit = now
                val shown = cleanMp(m, acc.toString())
                if (shown.isNotEmpty()) onPartial(shown)
            }
        }
        val raw = future.get()
        val clean = cleanMp(m, if (raw.isNullOrBlank()) acc.toString() else raw)
        if (clean.isEmpty()) throw IllegalStateException("respuesta vacía")
        return clean
    }

    // ---------- LiteRT-LM (.litertlm): la plantilla de chat la aplica el propio motor ----------
    private val RE_THINK = Regex("(?s)<think>.*?(</think>|$)")

    private fun cleanLt(raw: String): String =
        RE_THINK.replace(raw, "").replace("<end_of_turn>", "").replace("<|im_end|>", "").trim()

    private suspend fun generateLt(
        engine: Engine, system: String, turns: List<Turn>, systemInUser: Boolean, lang: String, onPartial: (String) -> Unit
    ): String {
        val last = turns.last().text   // buildTurns garantiza que el último turno es del usuario
        val prior = turns.dropLast(1)
        val uLabel = if (lang == "en") "User" else "Usuario"
        val aLabel = if (lang == "en") "Assistant" else "Asistente"
        // Sin initialMessages (la API cambia entre versiones): el historial corto va como texto dentro del mensaje.
        val userText = buildString {
            if (systemInUser) append(system).append("\n\n")
            if (prior.isNotEmpty()) {
                append(if (lang == "en") "Conversation so far:\n" else "Conversación hasta ahora:\n")
                prior.forEach { append(if (it.user) uLabel else aLabel).append(": ").append(it.text).append('\n') }
                append(if (lang == "en") "\nCurrent user message:\n" else "\nMensaje actual del usuario:\n")
            }
            append(last)
        }
        val sampler = SamplerConfig(topK = 40, topP = 0.9, temperature = 0.6)   // baja temperatura = inventa menos
        val config = if (systemInUser) ConversationConfig(samplerConfig = sampler)
                     else ConversationConfig(systemInstruction = Contents.of(system), samplerConfig = sampler)
        val acc = StringBuilder()
        var lastEmit = 0L
        engine.createConversation(config).use { conversation ->
            conversation.sendMessageAsync(userText).collect { msg ->
                acc.append(msg.toString())
                val now = android.os.SystemClock.uptimeMillis()
                if (now - lastEmit >= 50L) {
                    lastEmit = now
                    val shown = cleanLt(acc.toString())
                    if (shown.isNotEmpty()) onPartial(shown)
                }
            }
        }
        val clean = cleanLt(acc.toString())
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

    // ---------- Prompt ----------
    private class Turn(val user: Boolean, val text: String)

    /**
     * Prompt de sistema. Un modelo chico se confunde con instrucciones ambiguas: antes respondía cosas raras
     * con un saludo porque nada le decía que contestara directo a lo último que escribió el usuario.
     * Ahora las reglas van numeradas, cortas, y la 1 prohíbe saludar de nuevo.
     */
    private fun buildSystem(aiName: String, nowPlaying: String?, libraryBlock: String?, lang: String): String = buildString {
        if (lang == "en") {
            append("You are $aiName, the music assistant inside the MusicFlame app. You talk to someone listening to music on their phone.\n")
            append("Rules:\n")
            append("1. Answer directly what the user just wrote. Do not greet or introduce yourself unless the user greets you first; then greet in ONE short sentence.\n")
            append("2. Reply ONLY in English and never mix languages. Keep song titles and genres exactly as they are.\n")
            append("3. Plain text, no Markdown (no asterisks, no #). At most 3 sentences, except when listing songs.\n")
            append("4. Only mention songs, artists, albums or years that appear in the DATA below; NEVER invent any. If you don't have the data, say so in one sentence.\n")
            append("5. If the question is not about music, answer briefly (1-2 sentences) and offer help with music. Never answer with a greeting instead.")
        } else {
            append("Eres $aiName, el asistente musical de la app MusicFlame. Hablas con una persona que escucha música en su teléfono.\n")
            append("Reglas:\n")
            append("1. Responde directamente a lo último que escribió el usuario. No saludes ni te presentes, salvo que el usuario te salude primero; entonces saluda en UNA sola frase corta.\n")
            append("2. Responde SIEMPRE y únicamente en español, sin mezclar idiomas ni usar inglés ni chino. Los títulos de canciones y los géneros déjalos tal cual.\n")
            append("3. Texto plano, sin Markdown (nada de asteriscos ni #). Máximo 3 oraciones, salvo cuando listes canciones.\n")
            append("4. Solo menciona canciones, artistas, álbumes o años que aparezcan en los DATOS de abajo; NUNCA inventes ninguno. Si no tienes el dato, dilo en una frase.\n")
            append("5. Si la pregunta no trata de música, responde breve (1 o 2 oraciones) y ofrece ayuda con música. Nunca respondas con un saludo en lugar de contestar.")
        }
        if (!nowPlaying.isNullOrBlank()) append(if (lang == "en") "\nNow playing: \"$nowPlaying\"." else "\nAhora suena: \"$nowPlaying\".")
        if (!libraryBlock.isNullOrBlank()) {
            if (lang == "en") {
                append("\n\nDATA from the user's library:\n").append(libraryBlock)
                append("\nMessage structure: 1) one introductory sentence; 2) one line per song in the list, numbered (1., 2., ...), ")
                append("with title, artist and a short phrase on why it fits (use the reason); 3) one closing sentence inviting the user to tap a card or ask for something different. Include ALL the songs in the list.")
            } else {
                append("\n\nDATOS de la biblioteca del usuario:\n").append(libraryBlock)
                append("\nEstructura del mensaje: 1) una oración de introducción; 2) una línea por cada canción de la lista, ")
                append("numerada (1., 2., ...), con el título, el artista y en una frase corta por qué encaja (usa el motivo); ")
                append("3) una oración final invitando a tocar una tarjeta o a pedir algo distinto. Incluye TODAS las canciones de la lista.")
            }
        }
    }

    /** Historial recortado por presupuesto, que empieza y termina en turno del usuario y alterna roles. */
    private fun buildTurns(history: List<AiMessage>, budget: Int): List<Turn> {
        val picked = ArrayDeque<AiMessage>()
        var used = 0
        for (m in history.filter { !it.isError && it.text.isNotBlank() }.asReversed()) {
            val len = m.text.length.coerceAtMost(400)
            if (used + len > budget && picked.isNotEmpty()) break
            picked.addFirst(m); used += len
        }
        while (picked.isNotEmpty() && !picked.first().fromUser) picked.removeFirst()
        while (picked.isNotEmpty() && !picked.last().fromUser) picked.removeLast()
        // Si dos mensajes seguidos son del mismo rol (p. ej. tras un error), se unen: las plantillas exigen alternar.
        val out = ArrayList<Turn>()
        for (m in picked) {
            val text = m.text.take(400)
            val prev = out.lastOrNull()
            if (prev != null && prev.user == m.fromUser) { out[out.size - 1] = Turn(prev.user, prev.text + "\n" + text) }
            else out.add(Turn(m.fromUser, text))
        }
        if (out.isEmpty()) throw IllegalStateException("sin mensaje del usuario")
        return out
    }

    /** Solo MediaPipe: el prompt se arma a mano con la plantilla de cada familia de modelos. */
    private fun promptFor(m: AiModel, system: String, turns: List<Turn>): String = when (m) {
        AiModel.GEMMA3_1B -> buildString {
            // Gemma no tiene rol "system": las reglas van al inicio del primer turno del usuario. Sin <bos>: MediaPipe lo agrega.
            turns.forEachIndexed { i, t ->
                append("<start_of_turn>").append(if (t.user) "user" else "model").append('\n')
                if (i == 0) append(system).append("\n\n")
                append(t.text).append("<end_of_turn>\n")
            }
            append("<start_of_turn>model\n")
        }
        else -> buildString {   // ChatML (Qwen)
            append("<|im_start|>system\n").append(system).append("<|im_end|>\n")
            turns.forEach {
                append("<|im_start|>").append(if (it.user) "user" else "assistant").append('\n')
                append(it.text).append("<|im_end|>\n")
            }
            append("<|im_start|>assistant\n")
        }
    }
}
