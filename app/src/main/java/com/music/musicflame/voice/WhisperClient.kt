package com.music.musicflame.voice

import com.music.musicflame.data.OnlineAiException
import com.music.musicflame.data.OnlineErrorKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * Voz → texto con Whisper en Groq (endpoint de transcripción, compatible con OpenAI).
 * Usa la MISMA API key de Groq que ya se guarda en Ajustes > IA; no hay que configurar nada más.
 * Sin librerías nuevas: multipart a mano con HttpURLConnection.
 */
object WhisperClient {
    private const val URL_TRANSCRIBE = "https://api.groq.com/openai/v1/audio/transcriptions"
    private const val MODEL = "whisper-large-v3-turbo"

    /** [lang] = código ISO-639-1 ("es", "en"). Lanza [OnlineAiException] con el motivo si falla. */
    suspend fun transcribe(key: String, audio: File, lang: String): String = withContext(Dispatchers.IO) {
        val boundary = "----MusicFlame" + UUID.randomUUID().toString().replace("-", "")
        val conn = URL(URL_TRANSCRIBE).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15_000; conn.readTimeout = 30_000
            conn.doOutput = true
            conn.setRequestProperty("Authorization", "Bearer ${key.trim()}")
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            conn.setChunkedStreamingMode(0)

            conn.outputStream.use { out ->
                fun field(name: String, value: String) {
                    out.write(("--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n").toByteArray())
                }
                field("model", MODEL)
                field("language", lang)
                field("response_format", "json")
                field("temperature", "0")
                out.write(("--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"${audio.name}\"\r\nContent-Type: audio/mp4\r\n\r\n").toByteArray())
                audio.inputStream().use { it.copyTo(out) }
                out.write("\r\n--$boundary--\r\n".toByteArray())
            }

            val code = conn.responseCode
            if (code != 200) {
                val detail = readError(conn)
                throw when (code) {
                    401, 403 -> OnlineAiException(OnlineErrorKind.INVALID_KEY, detail)
                    429 -> OnlineAiException(OnlineErrorKind.RATE_LIMIT, detail, conn.getHeaderField("retry-after")?.trim()?.toDoubleOrNull()?.toInt())
                    in 500..599 -> OnlineAiException(OnlineErrorKind.SERVER, detail)
                    else -> OnlineAiException(OnlineErrorKind.OTHER, "HTTP $code $detail")
                }
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            JSONObject(body).optString("text", "").trim()
        } catch (e: OnlineAiException) { throw e }
        catch (e: IOException) { throw OnlineAiException(OnlineErrorKind.NETWORK, e.message ?: "sin conexión") }
        finally { conn.disconnect() }
    }

    private fun readError(conn: HttpURLConnection): String = try {
        val txt = (conn.errorStream ?: return "").bufferedReader().use { it.readText() }.take(1000)
        JSONObject(txt).optJSONObject("error")?.optString("message", txt) ?: txt
    } catch (_: Exception) { "" }
}
