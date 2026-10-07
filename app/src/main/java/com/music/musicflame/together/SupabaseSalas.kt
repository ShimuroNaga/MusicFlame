package com.music.musicflame.together

import com.music.musicflame.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Cliente mínimo de Supabase Storage para el bucket "salas" (modo "En compañía").
 *
 * Usa HttpURLConnection para no agregar dependencias nuevas.
 * La URL y la clave publishable NO están en el código: se leen de local.properties
 * (SUPABASE_URL y SUPABASE_KEY) vía BuildConfig, igual que YOUTUBE_API_KEY, para que no
 * queden en el repo público. La clave sigue yendo dentro del APK (es pública por diseño);
 * lo que realmente protege el bucket son las políticas (RLS) del SQL Editor de Supabase.
 *
 * Rutas de objeto: "{CODIGO_SALA}/{uuid-aleatorio}.{ext}" (el nombre aleatorio hace que nadie
 * pueda adivinar la URL de una canción).
 */
object SupabaseSalas {
    private val BASE_URL: String get() = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val API_KEY: String get() = BuildConfig.SUPABASE_KEY
    private const val BUCKET = "salas"

    /** Tope por archivo (el mismo que configuraste en el bucket). */
    const val MAX_BYTES = 25L * 1024L * 1024L

    private fun open(path: String, method: String): HttpURLConnection {
        if (BASE_URL.isBlank() || API_KEY.isBlank()) {
            throw IOException("Faltan SUPABASE_URL y SUPABASE_KEY en local.properties")
        }
        val conn = URL("$BASE_URL/storage/v1/object/$BUCKET/$path").openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 15_000
        conn.readTimeout = 60_000
        // Las claves nuevas (sb_publishable_...) NO son JWT: van solo en "apikey".
        // Si algún día usas la anon key vieja (eyJ...), también va como Bearer.
        conn.setRequestProperty("apikey", API_KEY)
        if (API_KEY.startsWith("eyJ")) conn.setRequestProperty("Authorization", "Bearer $API_KEY")
        return conn
    }

    private fun errorText(conn: HttpURLConnection): String =
        try { conn.errorStream?.bufferedReader()?.use { it.readText() }?.take(300).orEmpty() } catch (_: Throwable) { "" }

    fun mimeFor(ext: String): String = when (ext.lowercase()) {
        "mp3" -> "audio/mpeg"
        "m4a", "aac" -> "audio/mp4"
        "flac" -> "audio/flac"
        "ogg", "opus" -> "audio/ogg"
        "wav" -> "audio/wav"
        else -> "application/octet-stream"
    }

    /** Sube [file] a "{objectPath}". Lanza IOException si Supabase lo rechaza. */
    suspend fun upload(file: File, objectPath: String, mime: String) = withContext(Dispatchers.IO) {
        if (file.length() > MAX_BYTES) throw IOException("El archivo pasa el límite de 25 MB")
        val conn = open(objectPath, "POST")
        try {
            conn.doOutput = true
            conn.setFixedLengthStreamingMode(file.length())
            conn.setRequestProperty("Content-Type", mime)
            conn.setRequestProperty("x-upsert", "false")
            file.inputStream().use { input -> conn.outputStream.use { out -> input.copyTo(out) } }
            val code = conn.responseCode
            if (code !in 200..299) throw IOException("Supabase respondió $code ${errorText(conn)}")
        } finally {
            conn.disconnect()
        }
    }

    /** Descarga "{objectPath}" en [dest] (escribe a .part y renombra al terminar). */
    suspend fun download(objectPath: String, dest: File) = withContext(Dispatchers.IO) {
        val conn = open(objectPath, "GET")
        val part = File(dest.parentFile, dest.name + ".part")
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw IOException("Supabase respondió $code ${errorText(conn)}")
            conn.inputStream.use { input -> part.outputStream().use { out -> input.copyTo(out) } }
            if (dest.exists()) dest.delete()
            if (!part.renameTo(dest)) throw IOException("No se pudo guardar la canción descargada")
        } catch (t: Throwable) {
            part.delete()
            throw t
        } finally {
            conn.disconnect()
        }
    }

    /** Espacio ocupado en el bucket "salas" (solo archivos que hay AHORA en el bucket). */
    data class Usage(val usedBytes: Long, val files: Int) {
        val remainingBytes: Long get() = (PLAN_BYTES - usedBytes).coerceAtLeast(0L)
        val fraction: Float get() = (usedBytes.toFloat() / PLAN_BYTES).coerceIn(0f, 1f)
    }

    /** Almacenamiento del plan gratis de Supabase (1 GB para todo el proyecto). */
    const val PLAN_BYTES = 1024L * 1024L * 1024L

    fun formatSize(bytes: Long): String = when {
        bytes >= 1024L * 1024L * 1024L -> "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
        bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
        else -> "${bytes / 1024L} KB"
    }

    /**
     * Suma el tamaño de todos los archivos del bucket (recorre las carpetas de cada sala).
     * Usa la política de lectura (select) que ya pegaste en Supabase.
     */
    suspend fun usage(): Usage = withContext(Dispatchers.IO) {
        var bytes = 0L
        var files = 0
        val folders = ArrayDeque<String>()
        folders.add("")
        var visited = 0
        while (folders.isNotEmpty() && visited < 200) {
            val prefix = folders.removeFirst()
            visited++
            var offset = 0
            while (true) {
                val page = list(prefix, offset)
                for (i in 0 until page.length()) {
                    val o = page.getJSONObject(i)
                    val name = o.optString("name")
                    if (o.isNull("id")) {
                        // carpeta (una sala): se entra a contar lo de adentro
                        folders.add(if (prefix.isEmpty()) name else "$prefix/$name")
                    } else {
                        files++
                        bytes += o.optJSONObject("metadata")?.optLong("size", 0L) ?: 0L
                    }
                }
                if (page.length() < 100) break
                offset += 100
            }
        }
        Usage(bytes, files)
    }

    private fun list(prefix: String, offset: Int): JSONArray {
        val conn = URL("$BASE_URL/storage/v1/object/list/$BUCKET").let {
            if (BASE_URL.isBlank() || API_KEY.isBlank()) throw IOException("Faltan SUPABASE_URL y SUPABASE_KEY en local.properties")
            it.openConnection() as HttpURLConnection
        }
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.doOutput = true
            conn.setRequestProperty("apikey", API_KEY)
            if (API_KEY.startsWith("eyJ")) conn.setRequestProperty("Authorization", "Bearer $API_KEY")
            conn.setRequestProperty("Content-Type", "application/json")
            val body = JSONObject()
                .put("prefix", prefix)
                .put("limit", 100)
                .put("offset", offset)
                .toString()
                .toByteArray(Charsets.UTF_8)
            conn.outputStream.use { it.write(body) }
            val code = conn.responseCode
            if (code !in 200..299) throw IOException("Supabase respondió $code ${errorText(conn)}")
            return JSONArray(conn.inputStream.bufferedReader().use { it.readText() })
        } finally {
            conn.disconnect()
        }
    }

    /** Borra objetos del bucket. Los errores se ignoran (best-effort). */
    suspend fun delete(objectPaths: Collection<String>) = withContext(Dispatchers.IO) {
        for (p in objectPaths) {
            val conn = try { open(p, "DELETE") } catch (_: Throwable) { continue }
            try { conn.responseCode } catch (_: Throwable) {} finally { conn.disconnect() }
        }
    }
}
