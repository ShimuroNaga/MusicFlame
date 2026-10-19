package com.music.musicflame.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.KeyStore
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Modelos de la IA online (Groq, plan gratis). Los límites son los que Groq publica para el plan gratis
 * (30 peticiones/min, 1,000/día y 200K tokens/día POR MODELO) y pueden cambiar sin aviso: la barra de uso
 * es una estimación; las "peticiones restantes" salen de los headers reales que devuelve Groq.
 */
enum class OnlineModel(
    val id: String,
    val displayName: String,
    val dailyTokenLimit: Int,
    val dailyRequestLimit: Int,
    /** gpt-oss "razona" antes de contestar: se le pide poco razonamiento para gastar menos tokens. */
    val usesReasoningEffort: Boolean
) {
    GPT_OSS_120B("openai/gpt-oss-120b", "GPT-OSS 120B", 200_000, 1_000, true),
    GPT_OSS_20B("openai/gpt-oss-20b", "GPT-OSS 20B", 200_000, 1_000, true);

    companion object {
        fun fromId(id: String?): OnlineModel = values().firstOrNull { it.id == id } ?: GPT_OSS_120B
    }
}

/** Foto del consumo de HOY de un modelo (para pintar la barra). */
data class OnlineUsage(
    val tokensUsed: Int,
    val tokenLimit: Int,
    val requestsUsed: Int,
    /** Peticiones restantes según el último header de Groq de HOY; null si aún no hay dato. */
    val requestsRemaining: Int?,
    val requestLimit: Int
) {
    val fraction: Float get() = (tokensUsed.toFloat() / tokenLimit).coerceIn(0f, 1f)
}

/**
 * Guarda la API key de Groq del usuario y el consumo del día.
 *
 *  - La key vive CIFRADA (EncryptedSharedPreferences) en un archivo propio que va excluido de las copias de
 *    seguridad de Android (la llave del Keystore no se restaura en otro teléfono) y NO está en el SharedPreferences
 *    "settings", así que tampoco entra en Exportar configuración.
 *  - El contador de uso es un SharedPreferences normal aparte; se reinicia solo al cambiar el día UTC.
 *  - Abrir el almacén cifrado puede tardar y puede fallar en algunos equipos: por eso solo se toca desde
 *    hilos de fondo y cualquier fallo se atrapa (mismo patrón que SavingsGoalRepository).
 */
class OnlineAiStore(context: Context) {
    private val appContext = context.applicationContext

    /** SharedPreferences del consumo: la UI se suscribe aquí para refrescar la barra en vivo. */
    val usagePrefs: SharedPreferences = appContext.getSharedPreferences(USAGE_FILE, Context.MODE_PRIVATE)

    companion object {
        const val SECURE_FILE = "ai_online_secure"
        const val USAGE_FILE = "ai_online_usage"
        private const val KEY_API = "groq_api_key"
        private const val K_DAY = "day"
    }

    // ---------- API key (cifrada) ----------
    private var secureCache: SharedPreferences? = null

    private fun createSecure(): SharedPreferences {
        val masterKey = MasterKey.Builder(appContext, "musicflame_ai_online_key")
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            appContext, SECURE_FILE, masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    @Synchronized
    private fun secure(): SharedPreferences? {
        secureCache?.let { return it }
        return try {
            createSecure().also { secureCache = it }
        } catch (first: Throwable) {
            // Archivo cifrado que ya no coincide con la llave (p. ej. restaurado): se borra y se intenta una vez más.
            try {
                appContext.deleteSharedPreferences(SECURE_FILE)
                runCatching { KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry("musicflame_ai_online_key") }
                createSecure().also { secureCache = it }
            } catch (second: Throwable) { null }
        }
    }

    /** La key guardada o null. Llamar desde un hilo de fondo. */
    fun getApiKey(): String? = secure()?.getString(KEY_API, null)?.takeIf { it.isNotBlank() }
    fun hasApiKey(): Boolean = getApiKey() != null

    /** true si quedó guardada. Llamar desde un hilo de fondo. */
    fun saveApiKey(key: String): Boolean {
        val p = secure() ?: return false
        return p.edit().putString(KEY_API, key.trim()).commit()
    }

    fun clearApiKey() { secure()?.edit()?.remove(KEY_API)?.commit() }

    // ---------- Consumo del día (día UTC, como el reinicio de Groq) ----------
    private fun today(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date())

    private fun tokKey(m: OnlineModel) = "tok_${m.name}"
    private fun reqKey(m: OnlineModel) = "req_${m.name}"
    private fun remKey(m: OnlineModel) = "rem_${m.name}"

    /** Si el día guardado no es hoy, borra los contadores. */
    @Synchronized
    private fun rollDay() {
        val t = today()
        if (usagePrefs.getString(K_DAY, null) != t) usagePrefs.edit().clear().putString(K_DAY, t).apply()
    }

    @Synchronized
    fun addTokens(m: OnlineModel, tokens: Int) {
        if (tokens <= 0) return
        rollDay()
        usagePrefs.edit().putInt(tokKey(m), usagePrefs.getInt(tokKey(m), 0) + tokens).apply()
    }

    @Synchronized
    fun addRequest(m: OnlineModel, remainingFromHeader: Int?) {
        rollDay()
        val e = usagePrefs.edit().putInt(reqKey(m), usagePrefs.getInt(reqKey(m), 0) + 1)
        if (remainingFromHeader != null) e.putInt(remKey(m), remainingFromHeader)
        e.apply()
    }

    /** Consumo de hoy. No escribe nada: si el día cambió, simplemente devuelve ceros. */
    fun usage(m: OnlineModel): OnlineUsage {
        val sameDay = usagePrefs.getString(K_DAY, null) == today()
        return OnlineUsage(
            tokensUsed = if (sameDay) usagePrefs.getInt(tokKey(m), 0) else 0,
            tokenLimit = m.dailyTokenLimit,
            requestsUsed = if (sameDay) usagePrefs.getInt(reqKey(m), 0) else 0,
            requestsRemaining = if (sameDay && usagePrefs.contains(remKey(m))) usagePrefs.getInt(remKey(m), 0) else null,
            requestLimit = m.dailyRequestLimit
        )
    }

    /** Hora local aproximada del próximo reinicio (00:00 UTC), ya formateada para mostrar. */
    fun nextResetLocalLabel(): String {
        val c = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        return DateFormat.getTimeInstance(DateFormat.SHORT).format(c.time)
    }
}
