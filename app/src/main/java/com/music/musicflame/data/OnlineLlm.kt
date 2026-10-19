package com.music.musicflame.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import android.content.Context

enum class OnlineErrorKind { NO_KEY, INVALID_KEY, RATE_LIMIT, NETWORK, SERVER, OTHER }

/** Error de la IA online con un [kind] para que la UI muestre un texto claro (y decida si cae a la IA offline). */
class OnlineAiException(val kind: OnlineErrorKind, message: String, val retryAfterSec: Int? = null) : Exception(message)

/**
 * IA online: Groq (API compatible con OpenAI). Se conecta DIRECTO desde el teléfono con la key del propio
 * usuario; MusicFlame no tiene servidor en medio ni recibe las conversaciones. Sin librerías nuevas
 * (HttpURLConnection + org.json) y con respuesta en streaming, igual que la IA offline.
 *
 * Reutiliza el prompt de sistema y el recorte de historial de [LocalLlm] para que las dos IAs se comporten igual
 * (mismas reglas, mismos datos reales de la biblioteca).
 */
object OnlineLlm {
    private const val BASE = "https://api.groq.com/openai/v1"
    private const val MAX_COMPLETION_TOKENS = 900            // incluye el "razonamiento" de gpt-oss
    private const val HISTORY_CHAR_BUDGET = 1200             // más generoso que offline: aquí no hay RAM que cuidar...
    private const val HISTORY_CHAR_BUDGET_WITH_DATA = 700    // ...pero los tokens del día sí son limitados
    private val RE_THINK = Regex("(?s)<think>.*?(</think>|$)")

    /**
     * Comprueba la key sin gastar tokens (GET /models). null = la key sirve; si no, el motivo.
     * Llamar desde cualquier corrutina (cambia a IO sola).
     */
    suspend fun verifyKey(key: String): OnlineErrorKind? = withContext(Dispatchers.IO) {
        val conn = URL("$BASE/models").openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000; conn.readTimeout = 15_000
            conn.setRequestProperty("Authorization", "Bearer ${key.trim()}")
            when (val code = conn.responseCode) {
                200 -> null
                401, 403 -> OnlineErrorKind.INVALID_KEY
                429 -> OnlineErrorKind.RATE_LIMIT
                in 500..599 -> OnlineErrorKind.SERVER
                else -> { if (code > 0) OnlineErrorKind.OTHER else OnlineErrorKind.NETWORK }
            }
        } catch (_: IOException) { OnlineErrorKind.NETWORK }
        finally { conn.disconnect() }
    }

    /**
     * Responde en streaming. [onPartial] recibe el texto acumulado (desde un hilo de fondo, como [LocalLlm.reply]).
     * Lanza [OnlineAiException] con el motivo si algo falla.
     */
    suspend fun reply(
        ctx: Context, aiName: String, nowPlaying: String?, history: List<AiMessage>,
        libraryBlock: String? = null, lang: String = "es",
        librarySummary: String? = null,
        onPartial: (String) -> Unit = {}
    ): String = withContext(Dispatchers.IO) {
        val store = OnlineAiStore(ctx)
        val key = store.getApiKey() ?: throw OnlineAiException(OnlineErrorKind.NO_KEY, "sin API key")
        val model = AiChatSettings(ctx).getOnlineModel()

        val system = LocalLlm.buildSystem(aiName, lang)
        // Datos del momento (qué suena, resumen de biblioteca, selección calculada): pegados al ÚLTIMO mensaje, igual que en offline.
        val context = LocalLlm.buildUserContext(nowPlaying, librarySummary, libraryBlock, lang)
        val turns = LocalLlm.buildTurns(history, if (libraryBlock.isNullOrBlank()) HISTORY_CHAR_BUDGET else HISTORY_CHAR_BUDGET_WITH_DATA)
        val messages = JSONArray().put(JSONObject().put("role", "system").put("content", system))
        turns.forEachIndexed { i, t ->
            val text = if (i == turns.lastIndex && context.isNotBlank())
                context + "\n\n" + (if (lang == "en") "User message:\n" else "Mensaje del usuario:\n") + t.text
            else t.text
            messages.put(JSONObject().put("role", if (t.user) "user" else "assistant").put("content", text))
        }
        val promptChars = system.length + turns.sumOf { it.text.length } + context.length

        // Primer intento con parámetros opcionales (poco razonamiento + conteo de uso); si Groq los rechaza (400)
        // se repite UNA vez con la petición mínima.
        try {
            stream(store, key, model, messages, promptChars, optionalParams = true, onPartial)
        } catch (e: BadRequest) {
            try {
                stream(store, key, model, messages, promptChars, optionalParams = false, onPartial)
            } catch (e2: BadRequest) {
                throw OnlineAiException(OnlineErrorKind.OTHER, e2.detail)
            }
        }
    }

    private class BadRequest(val detail: String) : Exception(detail)

    private suspend fun stream(
        store: OnlineAiStore, key: String, model: OnlineModel, messages: JSONArray, promptChars: Int,
        optionalParams: Boolean, onPartial: (String) -> Unit
    ): String {
        val body = JSONObject()
            .put("model", model.id)
            .put("messages", messages)
            .put("stream", true)
            .put("temperature", 0.6)
            .put("max_completion_tokens", MAX_COMPLETION_TOKENS)
        if (optionalParams) {
            body.put("stream_options", JSONObject().put("include_usage", true))
            if (model.usesReasoningEffort) body.put("reasoning_effort", "low")
        }

        val conn = URL("$BASE/chat/completions").openConnection() as HttpURLConnection
        val acc = StringBuilder()
        var tokensCounted = false
        var reportedTotal = 0
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15_000; conn.readTimeout = 60_000
            conn.doOutput = true
            conn.setRequestProperty("Authorization", "Bearer $key")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept", "text/event-stream")
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            if (code != 200) {
                val detail = readError(conn)
                when {
                    code == 400 -> throw BadRequest(detail)
                    code == 401 || code == 403 -> throw OnlineAiException(OnlineErrorKind.INVALID_KEY, detail)
                    code == 429 -> throw OnlineAiException(OnlineErrorKind.RATE_LIMIT, detail, conn.getHeaderField("retry-after")?.trim()?.toDoubleOrNull()?.toInt())
                    code in 500..599 -> throw OnlineAiException(OnlineErrorKind.SERVER, detail)
                    else -> throw OnlineAiException(OnlineErrorKind.OTHER, "HTTP $code $detail")
                }
            }

            // La petición se aceptó: cuenta como 1 del día y se guarda lo que Groq dice que queda (si lo dice).
            store.addRequest(model, conn.getHeaderField("x-ratelimit-remaining-requests")?.trim()?.toIntOrNull())

            var lastEmit = 0L
            BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { reader ->
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val line = reader.readLine() ?: break
                    if (!line.startsWith("data:")) continue
                    val payload = line.removePrefix("data:").trim()
                    if (payload.isEmpty()) continue
                    if (payload == "[DONE]") break
                    val o = try { JSONObject(payload) } catch (_: Exception) { continue }
                    o.optJSONObject("error")?.let { throw OnlineAiException(OnlineErrorKind.OTHER, it.optString("message", "error")) }

                    // Tokens reales: vienen en el último trozo (campo usage, o x_groq.usage en versiones anteriores de la API).
                    (o.optJSONObject("usage") ?: o.optJSONObject("x_groq")?.optJSONObject("usage"))
                        ?.optInt("total_tokens", 0)?.takeIf { it > 0 }?.let { reportedTotal = it }

                    val choices = o.optJSONArray("choices")
                    if (choices != null && choices.length() > 0) {
                        val delta = choices.optJSONObject(0)?.optJSONObject("delta")
                        // Solo "content": el "reasoning" de gpt-oss se ignora (no se muestra).
                        if (delta != null && !delta.isNull("content")) {
                            val piece = delta.optString("content", "")
                            if (piece.isNotEmpty()) acc.append(piece)
                        }
                    }
                    val now = android.os.SystemClock.uptimeMillis()
                    if (now - lastEmit >= 50L && acc.isNotEmpty()) {
                        lastEmit = now
                        val shown = clean(acc.toString())
                        if (shown.isNotEmpty()) onPartial(shown)
                    }
                }
            }

            val total = if (reportedTotal > 0) reportedTotal else ((promptChars + acc.length) / 3).coerceAtLeast(1)   // sin dato real: estimación
            store.addTokens(model, total); tokensCounted = true
            val answer = clean(acc.toString())
            if (answer.isEmpty()) throw OnlineAiException(OnlineErrorKind.OTHER, "respuesta vacía")
            return answer
        } catch (e: OnlineAiException) { throw e }
        catch (e: BadRequest) { throw e }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: IOException) { throw OnlineAiException(OnlineErrorKind.NETWORK, e.message ?: "sin conexión") }
        finally {
            // Si se cortó a medias (cancelado o error de red) ya se gastaron tokens: se cuentan por estimación.
            if (!tokensCounted && acc.isNotEmpty()) store.addTokens(model, ((promptChars + acc.length) / 3).coerceAtLeast(1))
            conn.disconnect()
        }
    }

    private fun clean(raw: String): String = RE_THINK.replace(raw, "").trim()

    private fun readError(conn: HttpURLConnection): String = try {
        val txt = (conn.errorStream ?: return "").bufferedReader(Charsets.UTF_8).use { it.readText() }.take(2000)
        JSONObject(txt).optJSONObject("error")?.optString("message", txt) ?: txt
    } catch (_: Exception) { "" }
}
