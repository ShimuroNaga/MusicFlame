package com.music.musicflame.data

import android.content.Context
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Archivo de audio en la carpeta MusicFlame de Drive (solo lo que la app usa). */
data class DriveFile(val id: String, val name: String)

/**
 * Respaldo en Google Drive por REST directo (HttpURLConnection + token de play-services-auth).
 * Antes usaba google-api-client + google-api-services-drive, que pesaban mucho y solo se usaban aquí.
 * Alcance: drive.file (solo archivos que crea la propia app).
 */
class DriveRepository(private val context: Context) {

    private companion object {
        const val SCOPE = "oauth2:https://www.googleapis.com/auth/drive.file"
        const val API = "https://www.googleapis.com/drive/v3/files"
        const val UPLOAD = "https://www.googleapis.com/upload/drive/v3/files?uploadType=resumable&fields=id"
        const val FOLDER_MIME = "application/vnd.google-apps.folder"
        const val FOLDER_NAME = "MusicFlame"
    }

    private class AuthExpired : IOException("401")

    // Token OAuth de la cuenta activa. Bloqueante: llamar siempre dentro de Dispatchers.IO.
    private fun token(account: GoogleSignInAccount): String {
        val acc = account.account ?: throw IOException("Cuenta sin Account")
        return GoogleAuthUtil.getToken(context.applicationContext, acc, SCOPE)
    }

    // Ejecuta [block] con el token; si Drive responde 401, invalida el token y reintenta una vez.
    private fun <T> withAuth(account: GoogleSignInAccount, block: (String) -> T): T {
        val first = token(account)
        return try {
            block(first)
        } catch (_: AuthExpired) {
            GoogleAuthUtil.clearToken(context.applicationContext, first)
            block(token(account))
        }
    }

    private fun open(url: String, method: String, token: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/json")
        }

    private fun readJson(conn: HttpURLConnection): JSONObject {
        val code = conn.responseCode
        if (code == 401) throw AuthExpired()
        if (code !in 200..299) {
            val err = try { conn.errorStream?.bufferedReader()?.use { it.readText() } } catch (_: Exception) { null }
            throw IOException("Drive HTTP $code ${err ?: ""}")
        }
        return JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun listFiles(token: String, query: String, fields: String): List<JSONObject> {
        val out = ArrayList<JSONObject>()
        var pageToken: String? = null
        do {
            val url = buildString {
                append(API).append("?q=").append(enc(query))
                append("&spaces=drive&pageSize=1000&fields=").append(enc("nextPageToken,files($fields)"))
                if (pageToken != null) append("&pageToken=").append(enc(pageToken!!))
            }
            val conn = open(url, "GET", token)
            try {
                val json = readJson(conn)
                val arr: JSONArray = json.optJSONArray("files") ?: JSONArray()
                for (i in 0 until arr.length()) out += arr.getJSONObject(i)
                pageToken = json.optString("nextPageToken").takeIf { it.isNotEmpty() }
            } finally { conn.disconnect() }
        } while (pageToken != null)
        return out
    }

    // 2. Buscar nuestra base de operaciones (La carpeta)
    suspend fun getOrCreateAppFolder(account: GoogleSignInAccount): String? = withContext(Dispatchers.IO) {
        try {
            withAuth(account) { token ->
                val query = "mimeType='$FOLDER_MIME' and name='$FOLDER_NAME' and trashed=false"
                val existing = listFiles(token, query, "id,name").firstOrNull()
                if (existing != null) return@withAuth existing.getString("id")

                val body = JSONObject().put("name", FOLDER_NAME).put("mimeType", FOLDER_MIME).toString()
                val conn = open("$API?fields=id", "POST", token)
                try {
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                    readJson(conn).getString("id")
                } finally { conn.disconnect() }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // 3. El escáner de música
    suspend fun getSongsFromFolder(account: GoogleSignInAccount, folderId: String): List<DriveFile> = withContext(Dispatchers.IO) {
        try {
            withAuth(account) { token ->
                val query = "'$folderId' in parents and mimeType contains 'audio/' and trashed=false"
                listFiles(token, query, "id,name").map { DriveFile(it.getString("id"), it.optString("name")) }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    // 4. Función para subir una canción local a nuestra carpeta de Drive (subida reanudable, en streaming)
    suspend fun uploadSong(account: GoogleSignInAccount, folderId: String, localFilePath: String, mediaStoreId: Long = -1L): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(localFilePath)
            // Con scoped storage (Android 10+) File(path) puede no existir/no ser legible aunque la canción
            // sí esté en MediaStore; por eso, si no se puede leer por ruta, se abre por su Uri de MediaStore.
            val uri = if (mediaStoreId >= 0) {
                android.content.ContentUris.withAppendedId(android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, mediaStoreId)
            } else null
            val readableByPath = file.exists() && file.canRead()
            if (!readableByPath && uri == null) return@withContext false
            val length: Long = if (readableByPath) file.length() else {
                context.contentResolver.openAssetFileDescriptor(uri!!, "r")?.use { it.length } ?: -1L
            }
            if (length <= 0L) return@withContext false
            val mime = when (file.extension.lowercase()) {
                "mp3" -> "audio/mpeg"
                "m4a", "aac" -> "audio/mp4"
                "flac" -> "audio/flac"
                "ogg", "opus" -> "audio/ogg"
                "wav" -> "audio/wav"
                else -> "audio/mpeg"
            }

            withAuth(account) { token ->
                // Paso 1: abrir la sesión con los metadatos (nombre y carpeta destino)
                val meta = JSONObject().put("name", file.name).put("parents", JSONArray().put(folderId)).toString()
                val init = open(UPLOAD, "POST", token)
                val sessionUrl = try {
                    init.doOutput = true
                    init.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    init.setRequestProperty("X-Upload-Content-Type", mime)
                    init.setRequestProperty("X-Upload-Content-Length", length.toString())
                    init.outputStream.use { it.write(meta.toByteArray(Charsets.UTF_8)) }
                    val code = init.responseCode
                    if (code == 401) throw AuthExpired()
                    if (code !in 200..299) throw IOException("Drive upload init HTTP $code")
                    init.getHeaderField("Location") ?: throw IOException("Drive no devolvió la URL de subida")
                } finally { init.disconnect() }

                // Paso 2: mandar los bytes del audio sin cargarlos enteros en memoria
                val put = open(sessionUrl, "PUT", token)
                try {
                    put.readTimeout = 120_000
                    put.doOutput = true
                    put.setFixedLengthStreamingMode(length)
                    put.setRequestProperty("Content-Type", mime)
                    val input = if (readableByPath) FileInputStream(file) else context.contentResolver.openInputStream(uri!!)
                        ?: throw IOException("No se pudo abrir la canción")
                    input.use { src -> put.outputStream.use { out -> src.copyTo(out, 64 * 1024) } }
                    val code = put.responseCode
                    if (code == 401) throw AuthExpired()
                    code in 200..299
                } finally { put.disconnect() }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
