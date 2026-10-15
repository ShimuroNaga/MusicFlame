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
        val OBSERVED_KEYS = setOf("ai_terms_accepted", "ai_name", "ai_bubble_mode", "ai_bubble_hex")
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

    // ---------- Limpieza del fondo de chat (opción eliminada) ----------
    /** Borra la imagen y las claves del fondo que existían en versiones anteriores. */
    fun purgeLegacyBackground() {
        try {
            File(appContext.filesDir, "ai_chat_bg").deleteRecursively()
            if (prefs.contains("ai_bg_uri") || prefs.contains("ai_bg_dim"))
                prefs.edit().remove("ai_bg_uri").remove("ai_bg_dim").apply()
        } catch (_: Exception) {}
    }

    // ---------- Historial ----------
    private fun historyFile() = File(appContext.filesDir, "ai_chat_history.json")

    fun loadHistory(): List<AiMessage> = try {
        val f = historyFile()
        if (!f.isFile) emptyList() else {
            val arr = JSONArray(f.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                AiMessage(o.getLong("id"), o.getBoolean("user"), LocalLlm.repairText(o.getString("text")), o.optLong("t", 0L), o.optBoolean("err", false))
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