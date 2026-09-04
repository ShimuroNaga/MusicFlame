package com.music.musicflame.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import android.util.Base64

/**
 * Sincroniza la "Meta de ahorro" ($0 de $14,000) contra un archivo meta.json
 * en la raíz del repo de GitHub (ShimuroNaga/MusicFlame), siguiendo el diseño
 * ya conversado: lectura pública vía raw.githubusercontent.com (sin límites
 * prácticos, sin cuenta nueva), escritura solo posible para el dueño
 * (isOwnerAccount() en LicenseRepository) usando la API de contenidos de
 * GitHub, que sí requiere un token con permiso de escritura sobre el repo.
 *
 * El token NUNCA se guarda en el código ni se sube al repo: el dueño lo pega
 * una sola vez (diálogo gateado por isOwnerAccount()) y queda cifrado en el
 * dispositivo vía EncryptedSharedPreferences (Android Keystore). Recomendado
 * crear un token "fine-grained" en GitHub, con acceso limitado SOLO a este
 * repo y permiso "Contents: Read and write" — nada más.
 */
class SavingsGoalRepository(context: Context) {
    private val appContext = context.applicationContext
    private val gson = Gson()

    // Cache local para poder mostrar el último valor conocido sin internet.
    private val cachePrefs = appContext.getSharedPreferences("savings_goal_cache", Context.MODE_PRIVATE)

    // Prefs cifradas solo para el token del dueño (nunca se sincronizan, viven solo en este dispositivo).
    private val securePrefs by lazy {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            appContext,
            "savings_goal_secure",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    data class MetaData(val actual: Int)

    companion object {
        const val META_MAX = 14000

        // Cambiá owner/repo/rama si hace falta.
        private const val GITHUB_OWNER = "ShimuroNaga"
        private const val GITHUB_REPO = "MusicFlame"
        private const val GITHUB_BRANCH = "master"
        private const val FILE_PATH = "meta.json"

        private const val RAW_URL =
            "https://raw.githubusercontent.com/$GITHUB_OWNER/$GITHUB_REPO/$GITHUB_BRANCH/$FILE_PATH"
        private const val CONTENTS_API_URL =
            "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/contents/$FILE_PATH"

        private const val KEY_TOKEN = "github_pat"
        private const val KEY_CACHED_ACTUAL = "cached_actual"
    }

    fun hasToken(): Boolean = !securePrefs.getString(KEY_TOKEN, null).isNullOrBlank()

    fun saveToken(token: String) {
        securePrefs.edit().putString(KEY_TOKEN, token.trim()).apply()
    }

    fun clearToken() {
        securePrefs.edit().remove(KEY_TOKEN).apply()
    }

    fun getCachedActual(): Int = cachePrefs.getInt(KEY_CACHED_ACTUAL, 0)

    /** Lectura pública, la usan TODOS los usuarios (dueño incluido). */
    suspend fun fetchActual(): Int? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = URL(RAW_URL).openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.requestMethod = "GET"
            val body = conn.inputStream.bufferedReader(StandardCharsets.UTF_8).use(BufferedReader::readText)
            conn.disconnect()
            val meta = gson.fromJson(body, MetaData::class.java)
            val value = meta.actual.coerceIn(0, META_MAX)
            cachePrefs.edit().putInt(KEY_CACHED_ACTUAL, value).apply()
            value
        }.getOrNull()
    }

    sealed class PushResult {
        object Success : PushResult()
        object NoToken : PushResult()
        data class Error(val message: String) : PushResult()
    }

    /**
     * Escritura real vía GitHub Contents API. Solo debe llamarse desde UI
     * gateada por LicenseRepository.isOwnerAccount() == true.
     * Requiere 2 pasos: 1) GET del sha actual del archivo, 2) PUT con el
     * contenido nuevo en base64 + ese sha (así GitHub sabe que es una
     * actualización y no un choque de commits).
     */
    suspend fun pushActual(newValue: Int): PushResult = withContext(Dispatchers.IO) {
        val token = securePrefs.getString(KEY_TOKEN, null)
        if (token.isNullOrBlank()) return@withContext PushResult.NoToken

        val clamped = newValue.coerceIn(0, META_MAX)

        runCatching {
            // 1) Obtener el sha actual del archivo.
            val getConn = URL(CONTENTS_API_URL).openConnection() as HttpURLConnection
            getConn.requestMethod = "GET"
            getConn.setRequestProperty("Authorization", "token $token")
            getConn.setRequestProperty("Accept", "application/vnd.github+json")
            if (getConn.responseCode !in 200..299) {
                val err = getConn.errorStream?.bufferedReader()?.use(BufferedReader::readText)
                getConn.disconnect()
                return@withContext PushResult.Error("No se pudo leer el archivo actual (HTTP ${getConn.responseCode}): ${err ?: ""}")
            }
            val getBody = getConn.inputStream.bufferedReader(StandardCharsets.UTF_8).use(BufferedReader::readText)
            getConn.disconnect()
            val sha = com.google.gson.JsonParser.parseString(getBody).asJsonObject.get("sha").asString

            // 2) Subir el nuevo contenido con ese sha.
            val newJson = gson.toJson(MetaData(clamped))
            val encodedContent = Base64.encodeToString(newJson.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
            val payload = com.google.gson.JsonObject().apply {
                addProperty("message", "Actualizar meta de ahorro a $clamped")
                addProperty("content", encodedContent)
                addProperty("sha", sha)
                addProperty("branch", GITHUB_BRANCH)
            }

            val putConn = URL(CONTENTS_API_URL).openConnection() as HttpURLConnection
            putConn.requestMethod = "PUT"
            putConn.doOutput = true
            putConn.setRequestProperty("Authorization", "token $token")
            putConn.setRequestProperty("Accept", "application/vnd.github+json")
            putConn.setRequestProperty("Content-Type", "application/json")
            putConn.outputStream.use { it.write(gson.toJson(payload).toByteArray(StandardCharsets.UTF_8)) }

            if (putConn.responseCode !in 200..299) {
                val err = putConn.errorStream?.bufferedReader()?.use(BufferedReader::readText)
                putConn.disconnect()
                return@withContext PushResult.Error("GitHub rechazó el cambio (HTTP ${putConn.responseCode}): ${err ?: ""}")
            }
            putConn.disconnect()
            cachePrefs.edit().putInt(KEY_CACHED_ACTUAL, clamped).apply()
            PushResult.Success
        }.getOrElse { PushResult.Error(it.message ?: "Error de red desconocido") }
    }
}
