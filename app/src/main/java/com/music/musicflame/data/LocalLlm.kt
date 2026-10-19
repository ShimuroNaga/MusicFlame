package com.music.musicflame.data

import android.content.Context
import android.content.SharedPreferences
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.ExperimentalFlags
import com.google.ai.edge.litertlm.SamplerConfig
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
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Modelos de IA local que el usuario puede elegir. SOLO UNO descargado a la vez: para cambiar de modelo
 * hay que borrar el actual (ver [LocalLlm.download]).
 *
 * Se quitaron Qwen2.5-1.5B, Qwen3-0.6B y Gemma 3 1B (555 MB): eran lentos, demasiado tontos o añadían un
 * segundo motor (MediaPipe) al APK. Solo queda Gemma 4 E2B (con MTP corre mucho más rápido), Apache-2.0.
 * Para una IA ligera existe la IA online (Groq) con la API key del usuario.
 */
enum class AiModel(
    val id: String,
    val displayName: String,
    val url: String,
    val fileName: String,
    val sizeLabel: String,
    val minBytes: Long,
    val modelPageUrl: String,
    val licenseName: String,
    val licenseUrl: String,
    val descRes: Int
) {
    GEMMA4_E2B(
        "gemma4_e2b", "Gemma 4 E2B",
        "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm",
        "gemma-4-E2B-it.litertlm",
        "≈2.6 GB", 1500L * 1024 * 1024,
        "https://huggingface.co/google/gemma-4-E2B-it", "Apache-2.0", "https://www.apache.org/licenses/LICENSE-2.0",
        R.string.ai_model_desc_gemma4
    )
}

/**
 * IA local. Corre 100% en el teléfono, sin internet. Los modelos NO van dentro del APK: se descargan una vez
 * a files/ai_models y se borran desde Ajustes › IA.
 */
object LocalLlm {
    private const val HISTORY_CHAR_BUDGET = 800             // historial corto = prefill corto = primer trozo de respuesta antes
    private const val HISTORY_CHAR_BUDGET_WITH_DATA = 400   // con datos de biblioteca en el prompt el historial se acorta más

    /** LiteRT-LM: se reutiliza la misma conversación (el motor conserva el contexto) y se reinicia al pasar estos límites. */
    private const val CONV_MAX_TURNS = 6
    private const val CONV_MAX_TOKENS = 1500

    /** MTP (decodificación especulativa de Gemma 4): hasta ~2x más rápido. Si el modelo no lo soporta, se reintenta sin MTP. */
    private const val USE_MTP = true

    /** Si la GPU falla, se vuelve a intentar pasado este tiempo (antes se descartaba para siempre y la IA se quedaba en CPU). */
    private const val GPU_RETRY_MS = 3L * 24 * 60 * 60 * 1000

    /** Intenta GPU primero; si el equipo no la soporta se desactiva sola y se usa CPU. false = forzar CPU. */
    private const val USE_GPU = true

    private fun dir(ctx: Context) = File(ctx.applicationContext.filesDir, "ai_models")
    fun modelFile(ctx: Context, m: AiModel) = File(dir(ctx), m.fileName)
    private fun partFile(ctx: Context, m: AiModel) = File(dir(ctx), "${m.fileName}.part")

    fun isReady(ctx: Context, m: AiModel): Boolean = modelFile(ctx, m).let { it.isFile && it.length() > m.minBytes }

    /** El modelo descargado (solo puede haber uno) o null si no hay ninguno. */
    fun installed(ctx: Context): AiModel? {
        purgeLegacy(ctx)
        return AiModel.values().firstOrNull { isReady(ctx, it) }
    }

    /** Modelos que ya no existen en la app (Qwen, Gemma 3): se borran una vez por sesión para liberar 0.3–1.6 GB. */
    private val legacyPurged = AtomicBoolean(false)
    private val LEGACY_FILES = listOf(
        "Qwen2.5-1.5B-Instruct_q8_ekv1280.task",
        "qwen3_0.6b_nothink_q4_block32_ekv1280.litertlm",
        "gemma3-1b-it-int4.task"
    )
    private fun purgeLegacy(ctx: Context) {
        if (!legacyPurged.compareAndSet(false, true)) return
        try { LEGACY_FILES.forEach { File(dir(ctx), it).delete(); File(dir(ctx), "$it.part").delete() } } catch (_: Throwable) {}
    }
    fun isReady(ctx: Context): Boolean = installed(ctx) != null
    fun sizeOnDiskMb(ctx: Context): Long = installed(ctx)?.let { modelFile(ctx, it).length() / (1024 * 1024) } ?: 0L

    private val mutex = Mutex()
    @Volatile private var ltEngine: Engine? = null
    @Volatile private var engineIsGpu = false

    // Conversación reutilizada de LiteRT-LM (solo se toca con el mutex tomado).
    private var ltConv: Conversation? = null
    private var ltConvLang: String? = null
    private var ltConvTurns = 0

    private fun closeConv() {
        try { ltConv?.close() } catch (_: Throwable) {}
        ltConv = null; ltConvLang = null; ltConvTurns = 0
    }

    /** Libera la RAM del modelo (se vuelve a cargar solo en el siguiente mensaje). */
    fun unload() {
        closeConv()
        try { ltEngine?.close() } catch (_: Throwable) {}
        ltEngine = null
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

    // Claves de "GPU rota" (el parámetro se conserva por compatibilidad con las llamadas existentes).
    private fun loadingKey(@Suppress("UNUSED_PARAMETER") m: AiModel) = "lt_gpu_loading"
    private fun brokenKey(@Suppress("UNUSED_PARAMETER") m: AiModel) = "lt_gpu_broken"

    @OptIn(ExperimentalApi::class)
    private suspend fun buildLt(ctx: Context, m: AiModel, gpu: Boolean): Engine {
        suspend fun create(mtp: Boolean?): Engine {
            ExperimentalFlags.enableSpeculativeDecoding = mtp   // se lee al crear el Engine
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
        val prefs = enginePrefs(ctx)
        val stamp = modelFile(ctx, m).lastModified()
        // MTP necesita un modelo descargado después de mayo de 2026. Si falla, se carga normal y se recuerda
        // para ESTE archivo (si el usuario lo vuelve a descargar, se reintenta MTP).
        if (USE_MTP && prefs.getLong("lt_mtp_broken_stamp", -1L) != stamp) {
            try { return create(true) } catch (_: Throwable) {}
            val e = create(null)
            prefs.edit().putLong("lt_mtp_broken_stamp", stamp).apply()
            return e
        }
        return create(null)
    }

    private fun gpuBroken(prefs: SharedPreferences, bk: String): Boolean =
        prefs.getBoolean(bk, false) && System.currentTimeMillis() - prefs.getLong(bk + "_at", 0L) < GPU_RETRY_MS

    private fun setGpuBroken(prefs: SharedPreferences, bk: String, lk: String) {
        prefs.edit().putBoolean(bk, true).putLong(bk + "_at", System.currentTimeMillis()).putBoolean(lk, false).apply()
    }

    /**
     * Intenta GPU primero. Protección contra bucles de cierre: antes de cargar se deja una marca ("loading");
     * si la app muere cargando la GPU (fallo nativo, no capturable), la marca queda puesta y en el siguiente
     * arranque la GPU se descarta (por motor) durante [GPU_RETRY_MS]; después se vuelve a intentar.
     */
    private suspend fun <T> loadWithGpuFallback(ctx: Context, m: AiModel, build: suspend (Boolean) -> T): T {
        val prefs = enginePrefs(ctx)
        val lk = loadingKey(m); val bk = brokenKey(m)
        if (prefs.getBoolean(lk, false)) setGpuBroken(prefs, bk, lk)
        if (USE_GPU && !gpuBroken(prefs, bk)) {
            prefs.edit().putBoolean(lk, true).commit()   // commit (síncrono): debe quedar escrito antes de un posible cierre
            try {
                val e = build(true)
                prefs.edit().putBoolean(lk, false).putBoolean(bk, false).apply()
                engineIsGpu = true
                return e
            } catch (_: Throwable) {
                setGpuBroken(prefs, bk, lk)
            }
        }
        engineIsGpu = false
        return build(false)
    }

    private suspend fun ltEngineFor(ctx: Context, m: AiModel): Engine =
        ltEngine ?: loadWithGpuFallback(ctx, m) { gpu -> buildLt(ctx, m, gpu) }.also { ltEngine = it }

    private fun markGpuBroken(ctx: Context, m: AiModel) {
        setGpuBroken(enginePrefs(ctx), brokenKey(m), loadingKey(m))
        unload()
    }

    /** Carga el modelo en RAM antes de que el usuario escriba (así el primer mensaje no espera la carga). */
    suspend fun preload(ctx: Context) = withContext(Dispatchers.IO) {
        val m = installed(ctx) ?: return@withContext
        try {
            mutex.withLock { ltEngineFor(ctx, m) }
        } catch (_: Throwable) {}
    }

    /**
     * Responde en streaming: [onPartial] recibe el texto acumulado cada vez que el modelo suelta un trozo
     * (se llama desde un hilo de fondo). Devuelve la respuesta final ya limpia.
     * [librarySummary] = resumen corto y fijo de la biblioteca (siempre va en el prompt, así el modelo nunca
     * se queda "sin datos" ante un simple saludo).
     */
    suspend fun reply(
        ctx: Context, aiName: String, nowPlaying: String?, history: List<AiMessage>,
        libraryBlock: String? = null,
        lang: String = "es",
        librarySummary: String? = null,
        onPartial: (String) -> Unit = {}
    ): String = withContext(Dispatchers.IO) {
        val m = installed(ctx) ?: throw IllegalStateException("modelo no descargado")
        mutex.withLock {
            val system = buildSystem(aiName, lang)
            val context = buildUserContext(nowPlaying, librarySummary, libraryBlock, lang)
            val turns = buildTurns(history, if (libraryBlock.isNullOrBlank()) HISTORY_CHAR_BUDGET else HISTORY_CHAR_BUDGET_WITH_DATA)
            suspend fun run(): String = generateLt(ltEngineFor(ctx, m), system, context, turns, lang, onPartial)
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

    // ---------- LiteRT-LM (.litertlm): la plantilla de chat la aplica el propio motor ----------
    private val RE_THINK = Regex("(?s)<think>.*?(</think>|$)")

    private fun cleanLt(raw: String): String =
        RE_THINK.replace(raw, "").replace("<end_of_turn>", "").replace("<|im_end|>", "").trim()

    /**
     * Se REUTILIZA la conversación entre mensajes: el motor conserva el contexto (no hay que reenviar el
     * historial ni volver a procesar el prompt de sistema, que es fijo). Los datos que cambian en cada mensaje
     * (qué suena, biblioteca) van dentro del mensaje del usuario. La conversación se reinicia al empezar un
     * chat nuevo, al cambiar de idioma, tras [CONV_MAX_TURNS] turnos o si el contexto pasa de [CONV_MAX_TOKENS].
     */
    private suspend fun generateLt(
        engine: Engine, system: String, context: String, turns: List<Turn>, lang: String, onPartial: (String) -> Unit
    ): String {
        val last = turns.last().text   // buildTurns garantiza que el último turno es del usuario
        val prior = turns.dropLast(1)
        val uLabel = if (lang == "en") "User" else "Usuario"
        val aLabel = if (lang == "en") "Assistant" else "Asistente"
        val sampler = SamplerConfig(topK = 40, topP = 0.9, temperature = 0.6)   // baja temperatura = inventa menos

        fun needsReset(c: Conversation): Boolean =
            ltConvLang != lang || prior.isEmpty() || ltConvTurns >= CONV_MAX_TURNS ||
                try { c.getTokenCount() > CONV_MAX_TOKENS } catch (_: Throwable) { true }

        suspend fun attempt(): String {
            ltConv?.let { if (needsReset(it)) closeConv() }
            var fresh = false
            val conversation = ltConv ?: run {
                fresh = true
                engine.createConversation(ConversationConfig(systemInstruction = Contents.of(system), samplerConfig = sampler))
                    .also { ltConv = it; ltConvLang = lang; ltConvTurns = 0 }
            }
            val withHistory = fresh && prior.isNotEmpty()
            val userText = buildString {
                if (withHistory) {
                    // Conversación nueva con chat previo: el historial corto va como texto.
                    append(if (lang == "en") "Conversation so far:\n" else "Conversación hasta ahora:\n")
                    prior.forEach { append(if (it.user) uLabel else aLabel).append(": ").append(it.text).append('\n') }
                    append('\n')
                }
                if (context.isNotBlank()) append(context).append("\n\n")
                if (withHistory || context.isNotBlank()) append(if (lang == "en") "User message:\n" else "Mensaje del usuario:\n")
                append(last)
            }
            val acc = StringBuilder()
            var lastEmit = 0L
            conversation.sendMessageAsync(userText).collect { msg ->
                acc.append(msg.toString())
                val now = android.os.SystemClock.uptimeMillis()
                if (now - lastEmit >= 50L) {
                    lastEmit = now
                    val shown = cleanLt(acc.toString())
                    if (shown.isNotEmpty()) onPartial(shown)
                }
            }
            ltConvTurns++
            val clean = cleanLt(acc.toString())
            if (clean.isEmpty()) throw IllegalStateException("respuesta vacía")
            return clean
        }

        val reused = ltConv != null
        return try {
            attempt()
        } catch (t: Throwable) {
            closeConv()   // tras cualquier fallo o cancelación el contexto queda a medias: se descarta
            if (t is kotlinx.coroutines.CancellationException || !reused) throw t
            attempt()     // contexto lleno o conversación dañada: una vez más con conversación nueva
        }
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
    internal class Turn(val user: Boolean, val text: String)

    /**
     * Prompt de sistema FIJO y corto (un modelo chico se confunde con muchas reglas). Como no cambia entre
     * mensajes, LiteRT-LM lo procesa una sola vez por conversación. Lo que cambia va en [buildUserContext].
     */
    internal fun buildSystem(aiName: String, lang: String): String =
        if (lang == "en") {
            "You are $aiName, the music assistant inside the MusicFlame app, talking to someone listening to music on their phone.\n" +
            "Rules:\n" +
            "1. Answer directly what the user just wrote, in English, in plain text (no Markdown), in at most 3 sentences. Do not greet unless the user greets you first (then one short sentence).\n" +
            "2. Only mention songs, artists or albums that appear in the DATA; never invent any.\n" +
            "3. If the question is not about music, answer in 1-2 sentences and offer help with music."
        } else {
            "Eres $aiName, el asistente musical de la app MusicFlame, y hablas con alguien que escucha música en su teléfono.\n" +
            "Reglas:\n" +
            "1. Contesta directo a lo último que escribió el usuario, en español, en texto plano (sin Markdown) y en máximo 3 oraciones. No saludes salvo que te saluden primero (entonces, una frase corta).\n" +
            "2. Solo menciona canciones, artistas o álbumes que aparezcan en los DATOS; nunca inventes ninguno.\n" +
            "3. Si la pregunta no es de música, responde en 1 o 2 oraciones y ofrece ayuda con música."
        }

    /**
     * Datos que cambian en cada mensaje: qué suena, resumen fijo de la biblioteca (para que el modelo nunca
     * se quede "sin datos") y, si el usuario pidió algo concreto, la selección ya calculada por la app.
     * Las tarjetas ya muestran las canciones, así que se le pide una respuesta CORTA (generar es lo más lento).
     */
    internal fun buildUserContext(nowPlaying: String?, summary: String?, block: String?, lang: String): String {
        val en = lang == "en"
        val parts = ArrayList<String>(4)
        if (!nowPlaying.isNullOrBlank()) parts += if (en) "Now playing: \"$nowPlaying\"." else "Ahora suena: \"$nowPlaying\"."
        if (!summary.isNullOrBlank()) parts += (if (en) "DATA - user's library: " else "DATOS - biblioteca del usuario: ") + summary
        if (!block.isNullOrBlank()) {
            parts += (if (en) "DATA for this request:\n" else "DATOS para esta petición:\n") + block
            parts += if (en) "The chosen songs are shown separately as cards: do NOT list them. Comment on the selection in one or two short sentences and invite the user to tap a card."
                     else "Las canciones elegidas se muestran aparte como tarjetas: NO las enumeres. Comenta la selección en una o dos frases cortas e invita a tocar una tarjeta."
        }
        return parts.joinToString("\n")
    }

    /** Historial recortado por presupuesto, que empieza y termina en turno del usuario y alterna roles. */
    internal fun buildTurns(history: List<AiMessage>, budget: Int): List<Turn> {
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
}
