package com.music.musicflame.data

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Un mensaje del chat de IA. [fromUser] = true → burbuja derecha (tú), false → burbuja izquierda (la IA). */
data class AiMessage(
    val id: Long,
    val fromUser: Boolean,
    val text: String,
    val timeMs: Long = System.currentTimeMillis(),
    val isError: Boolean = false
)

/**
 * Ajustes + historial del chat de IA (6ª pantalla).
 *
 * - Las preferencias visuales viven en el mismo SharedPreferences "settings" que usa el resto de la app
 *   (así entran solas en Copia de seguridad), salvo `ai_bg_uri` que apunta a un archivo de ESTE
 *   dispositivo y está en EXCLUDED_KEYS de ConfigExportRepository.
 * - El historial se guarda como JSON en files/ai_chat_history.json (últimos [MAX_HISTORY] mensajes).
 */
class AiChatSettings(context: Context) {
    private val appContext = context.applicationContext
    val prefs: SharedPreferences = appContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    init {
        // Limpieza: versiones de prueba guardaban aquí una API key de Gemini; ya no se usa.
        try { appContext.deleteSharedPreferences("ai_secure") } catch (_: Exception) {}
    }

    companion object {
        const val MODE_ADAPTIVE = "Adaptativo"
        const val MODE_CUSTOM = "Personalizado"
        const val MODE_RAINBOW = "Arcoiris"   // mismo texto crudo que COLOR_MODE_RAINBOW del resto de la app
        const val DEFAULT_NAME = "Flame"
        const val MAX_HISTORY = 80

        /** Vuelta completa del arcoíris de las burbujas: lento a propósito para no marear (el global va a 6s). */
        const val BUBBLE_RAINBOW_CYCLE_SECONDS = 30f

        // Claves que, al cambiar, deben refrescar el chat en vivo.
        val OBSERVED_KEYS = setOf("ai_terms_accepted", "ai_name", "ai_bubble_mode", "ai_bubble_hex", "ai_bg_uri", "ai_bg_dim")
    }

    // ---------- Términos ----------
    fun isTermsAccepted(): Boolean = prefs.getBoolean("ai_terms_accepted", false)
    fun saveTermsAccepted(v: Boolean) = prefs.edit().putBoolean("ai_terms_accepted", v).apply()

    // ---------- Nombre ----------
    fun getName(): String = (prefs.getString("ai_name", DEFAULT_NAME) ?: DEFAULT_NAME).ifBlank { DEFAULT_NAME }
    fun saveName(name: String) = prefs.edit().putString("ai_name", name.trim().take(24)).apply()

    // ---------- Burbujas ----------
    fun getBubbleMode(): String = prefs.getString("ai_bubble_mode", MODE_ADAPTIVE) ?: MODE_ADAPTIVE
    fun saveBubbleMode(mode: String) = prefs.edit().putString("ai_bubble_mode", mode).apply()
    fun getBubbleHex(): String = prefs.getString("ai_bubble_hex", "#2196F3") ?: "#2196F3"
    fun saveBubbleHex(hex: String) = prefs.edit().putString("ai_bubble_hex", hex).apply()

    // ---------- Fondo del chat ----------
    fun getBackgroundUri(): String? {
        val uri = prefs.getString("ai_bg_uri", null) ?: return null
        val f = fileFromUri(uri) ?: return null
        return if (f.isFile && f.length() > 0L) uri else null
    }
    /** Oscurecido del fondo, 0f..0.8f, para que las burbujas se lean sobre cualquier foto. */
    fun getBackgroundDim(): Float = prefs.getFloat("ai_bg_dim", 0.25f).coerceIn(0f, 0.8f)
    fun saveBackgroundDim(v: Float) = prefs.edit().putFloat("ai_bg_dim", v.coerceIn(0f, 0.8f)).apply()

    private fun bgDir() = File(appContext.filesDir, "ai_chat_bg")

    private fun fileFromUri(uriString: String): File? {
        return try {
            val path = Uri.parse(uriString).path ?: return null
            val f = File(path).canonicalFile
            if (f.parentFile?.canonicalPath == bgDir().canonicalPath) f else null
        } catch (e: Exception) {
            null
        }
    }

    /** Copia la imagen elegida a files/ai_chat_bg/ (el Uri del selector no es permanente) y la guarda como fondo. */
    fun importBackground(source: Uri): Boolean {
        return try {
            val dir = bgDir()
            if (!dir.exists() && !dir.mkdirs()) return false
            val mime = appContext.contentResolver.getType(source)?.lowercase()
            val ext = when (mime) { "image/png" -> "png"; "image/webp" -> "webp"; else -> "jpg" }
            val dest = File(dir, "ai_bg_${System.currentTimeMillis()}.$ext")
            val input = appContext.contentResolver.openInputStream(source) ?: return false
            input.use { inp ->
                dest.outputStream().use { out -> inp.copyTo(out) }
            }
            val old = prefs.getString("ai_bg_uri", null)?.let { fileFromUri(it) }
            prefs.edit().putString("ai_bg_uri", Uri.fromFile(dest).toString()).apply()
            old?.delete()
            true
        } catch (e: Exception) {
            false
        }
    }

    fun removeBackground() {
        prefs.getString("ai_bg_uri", null)?.let { fileFromUri(it) }?.delete()
        prefs.edit().remove("ai_bg_uri").apply()
    }

    // ---------- Historial ----------
    private fun historyFile() = File(appContext.filesDir, "ai_chat_history.json")

    fun loadHistory(): List<AiMessage> = try {
        val f = historyFile()
        if (!f.isFile) emptyList() else {
            val arr = JSONArray(f.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                AiMessage(o.getLong("id"), o.getBoolean("user"), o.getString("text"), o.optLong("t", 0L), o.optBoolean("err", false))
            }
        }
    } catch (e: Exception) { emptyList() }

    fun saveHistory(messages: List<AiMessage>) {
        try {
            val arr = JSONArray()
            messages.takeLast(MAX_HISTORY).forEach {
                arr.put(JSONObject().put("id", it.id).put("user", it.fromUser).put("text", it.text).put("t", it.timeMs).put("err", it.isError))
            }
            historyFile().writeText(arr.toString())
        } catch (e: Exception) { /* historial es best-effort */ }
    }

    fun clearHistory() { historyFile().delete() }
}