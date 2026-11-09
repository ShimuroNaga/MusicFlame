package com.music.musicflame.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.music.musicflame.data.db.AppDatabase
import com.music.musicflame.data.db.OrbitaMetaEntity
import com.music.musicflame.data.db.OrbitaMetaWithCount
import com.music.musicflame.data.db.OrbitaSongEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Buzón de importación: MainActivity deja aquí la Uri de un archivo .orbita que abrió otra app,
 * y OrbitaPage la consume (muestra el preview y pregunta "¿Guardar esta Órbita?").
 * Se lee dentro de composables, por eso es un estado de Compose.
 */
object OrbitaInbox {
    var pendingUri: Uri? by mutableStateOf(null)

    /** true si el intent VIEW apunta a un archivo .orbita (por extensión del path o por tipo MIME). */
    fun looksLikeOrbita(uri: Uri?, mimeType: String?): Boolean {
        if (uri == null) return false
        if (mimeType == OrbitaRepository.MIME_ORBITA) return true
        val name = (uri.lastPathSegment ?: uri.path ?: "").lowercase()
        return name.endsWith(".orbita")
    }
}

/** Una canción dentro de un archivo .orbita ya leído (todavía sin guardar). */
data class OrbitaImportSong(
    val titulo: String,
    val artista: String,
    val album: String,
    val nota: String,
    val fechaAgregada: Long,
    val posX: Float,
    val posY: Float,
    val caratulaBase64: String?
)

data class OrbitaImportPreview(
    val autorNombre: String,
    val fechaExport: Long,
    val canciones: List<OrbitaImportSong>
)

/**
 * Órbitas del usuario (la propia + las importadas), en Room (tablas orbita_meta/orbita_song).
 * Todo corre en Dispatchers.IO.
 */
class OrbitaRepository(context: Context) {

    private val appContext = context.applicationContext
    private fun dao() = AppDatabase.getInstance(appContext).orbitaDao()

    companion object {
        const val MAX_SONGS = 20
        const val MIME_ORBITA = "application/x-orbita"
        const val EXPORT_DIR = "orbita_exports"
        private const val FORMAT_VERSION = 1
        private const val MAX_IMPORT_BYTES = 8L * 1024 * 1024
        private const val COVER_SIZE = 100
        private const val COVER_QUALITY = 60

        /** Nombre del autor a mostrar/guardar: el de la cuenta de Google activa, o vacío. */
        fun currentAuthorName(context: Context): String {
            val acc = GoogleSignIn.getLastSignedInAccount(context.applicationContext) ?: return ""
            return (acc.givenName ?: acc.displayName ?: "").trim()
        }

        fun exportFileName(autor: String): String {
            val clean = autor.trim().lowercase()
                .replace(Regex("[^a-z0-9áéíóúñü]+"), "_").trim('_')
            return "orbita_de_${clean.ifEmpty { "musicflame" }}.orbita"
        }
    }

    // ------------------------------------------------------------------ lectura

    /** Id de la órbita propia; la crea la primera vez ("Mi Órbita"). */
    suspend fun ensureOwn(ownName: String): Long = withContext(Dispatchers.IO) {
        val d = dao()
        d.getOwn()?.id ?: d.insertMeta(
            OrbitaMetaEntity(
                nombre = ownName,
                autorNombre = currentAuthorName(appContext),
                esPropia = true,
                fechaImport = 0L
            )
        )
    }

    suspend fun list(): List<OrbitaMetaWithCount> = withContext(Dispatchers.IO) { dao().listWithCount() }

    suspend fun songs(orbitaId: Long): List<OrbitaSongEntity> = withContext(Dispatchers.IO) { dao().songsOf(orbitaId) }

    // ------------------------------------------------------------------ edición (solo la propia)

    enum class AddResult { ADDED, FULL, DUPLICATE }

    suspend fun addSong(orbitaId: Long, song: Song, posX: Float, posY: Float): AddResult = withContext(Dispatchers.IO) {
        val d = dao()
        if (d.countOf(orbitaId) >= MAX_SONGS) return@withContext AddResult.FULL
        if (d.songsOf(orbitaId).any { it.songId == song.id }) return@withContext AddResult.DUPLICATE
        d.insertSong(
            OrbitaSongEntity(
                orbitaId = orbitaId,
                songId = song.id,
                path = song.path,
                titulo = song.title,
                artista = song.artist,
                album = song.album,
                nota = "",
                fechaAgregada = System.currentTimeMillis(),
                posX = posX.coerceIn(0f, 1f),
                posY = posY.coerceIn(0f, 1f),
                caratula = null
            )
        )
        AddResult.ADDED
    }

    suspend fun movePos(id: Long, x: Float, y: Float) = withContext(Dispatchers.IO) {
        dao().updatePos(id, x.coerceIn(0f, 1f), y.coerceIn(0f, 1f))
    }

    suspend fun setNota(id: Long, nota: String) = withContext(Dispatchers.IO) { dao().updateNota(id, nota.trim()) }

    suspend fun removeSong(id: Long) = withContext(Dispatchers.IO) { dao().deleteSong(id) }

    /** Borra una órbita IMPORTADA completa. La propia nunca se borra. */
    suspend fun deleteImported(orbitaId: Long) = withContext(Dispatchers.IO) {
        val meta = dao().getMeta(orbitaId) ?: return@withContext
        if (!meta.esPropia) dao().deleteOrbita(orbitaId)
    }

    // ------------------------------------------------------------------ exportar

    /** Escribe cache/orbita_exports/<nombre>.orbita y devuelve el archivo. */
    suspend fun exportToCacheFile(orbitaId: Long, autorNombre: String): File = withContext(Dispatchers.IO) {
        val songs = dao().songsOf(orbitaId)
        val arr = JSONArray()
        for (s in songs) {
            val cover = s.caratula ?: coverBase64FromLocalSong(s)
            arr.put(
                JSONObject()
                    .put("titulo", s.titulo)
                    .put("artista", s.artista)
                    .put("album", s.album)
                    .put("nota", s.nota)
                    .put("fechaAgregada", s.fechaAgregada)
                    .put("posX", s.posX.toDouble())
                    .put("posY", s.posY.toDouble())
                    .put("caratula", cover ?: JSONObject.NULL)
            )
        }
        val root = JSONObject()
            .put("version", FORMAT_VERSION)
            .put("autorNombre", autorNombre)
            .put("fechaExport", System.currentTimeMillis())
            .put("canciones", arr)
        val dir = File(appContext.cacheDir, EXPORT_DIR).apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() } // solo queda el último export
        File(dir, exportFileName(autorNombre)).also { it.writeText(root.toString(), Charsets.UTF_8) }
    }

    /** Carátula de la canción local -> JPEG 100x100 q60 -> base64. null si no tiene. */
    private fun coverBase64FromLocalSong(s: OrbitaSongEntity): String? {
        val song = findLocalSong(s)
        var bmp: Bitmap? = null
        try {
            val path = song?.path ?: s.path
            if (!path.isNullOrBlank() && !(song?.hasCustomCover ?: false)) {
                val r = MediaMetadataRetriever()
                try {
                    r.setDataSource(path)
                    r.embeddedPicture?.let { bmp = BitmapFactory.decodeByteArray(it, 0, it.size) }
                } catch (_: Exception) {
                } finally {
                    try { r.release() } catch (_: Exception) {}
                }
            }
            if (bmp == null && song?.albumArtUri != null) {
                try {
                    appContext.contentResolver.openInputStream(Uri.parse(song.albumArtUri))?.use {
                        bmp = BitmapFactory.decodeStream(it)
                    }
                } catch (_: Exception) {
                }
            }
            val src = bmp ?: return null
            val side = minOf(src.width, src.height)
            val square = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
            val scaled = Bitmap.createScaledBitmap(square, COVER_SIZE, COVER_SIZE, true)
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, COVER_QUALITY, out)
            return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        } catch (_: Exception) {
            return null
        }
    }

    // ------------------------------------------------------------------ importar

    /** Lee y valida un .orbita. Devuelve null si el archivo no es válido. */
    suspend fun readPreview(uri: Uri): OrbitaImportPreview? = withContext(Dispatchers.IO) {
        try {
            val text = appContext.contentResolver.openInputStream(uri)?.use { input ->
                val buf = ByteArrayOutputStream()
                val chunk = ByteArray(16 * 1024)
                var total = 0L
                while (true) {
                    val n = input.read(chunk)
                    if (n < 0) break
                    total += n
                    if (total > MAX_IMPORT_BYTES) return@withContext null
                    buf.write(chunk, 0, n)
                }
                buf.toString("UTF-8")
            } ?: return@withContext null
            val root = JSONObject(text)
            if (root.optInt("version", 0) != FORMAT_VERSION) return@withContext null
            val arr = root.optJSONArray("canciones") ?: return@withContext null
            val songs = ArrayList<OrbitaImportSong>()
            for (i in 0 until minOf(arr.length(), MAX_SONGS)) {
                val o = arr.optJSONObject(i) ?: continue
                val titulo = o.optString("titulo", "").trim()
                if (titulo.isEmpty()) continue
                songs.add(
                    OrbitaImportSong(
                        titulo = titulo,
                        artista = o.optString("artista", ""),
                        album = o.optString("album", ""),
                        nota = o.optString("nota", ""),
                        fechaAgregada = o.optLong("fechaAgregada", 0L),
                        posX = o.optDouble("posX", 0.5).toFloat().coerceIn(0f, 1f),
                        posY = o.optDouble("posY", 0.5).toFloat().coerceIn(0f, 1f),
                        caratulaBase64 = if (o.isNull("caratula")) null else o.optString("caratula").ifEmpty { null }
                    )
                )
            }
            OrbitaImportPreview(
                autorNombre = root.optString("autorNombre", "").trim(),
                fechaExport = root.optLong("fechaExport", 0L),
                canciones = songs
            )
        } catch (_: Exception) {
            null
        }
    }

    /** Ya hay una órbita importada del mismo autor? (para preguntar reemplazar o guardar como nueva) */
    suspend fun findImportedByAuthor(autor: String): OrbitaMetaEntity? = withContext(Dispatchers.IO) {
        if (autor.isBlank()) null else dao().findImportedByAuthor(autor)
    }

    /**
     * Guarda el preview como órbita importada (solo lectura). Si [replaceId] != null reemplaza
     * esa órbita en vez de crear otra. Devuelve el id resultante.
     */
    suspend fun saveImported(preview: OrbitaImportPreview, nombre: String, replaceId: Long? = null): Long =
        withContext(Dispatchers.IO) {
            val d = dao()
            val now = System.currentTimeMillis()
            val songs = preview.canciones.map {
                OrbitaSongEntity(
                    orbitaId = 0,
                    songId = null,
                    path = null,
                    titulo = it.titulo,
                    artista = it.artista,
                    album = it.album,
                    nota = it.nota,
                    fechaAgregada = it.fechaAgregada,
                    posX = it.posX,
                    posY = it.posY,
                    caratula = it.caratulaBase64
                )
            }
            val existing = replaceId?.let { d.getMeta(it) }
            if (existing != null && !existing.esPropia) {
                d.replaceImported(
                    existing.copy(nombre = nombre, autorNombre = preview.autorNombre, fechaImport = now),
                    songs
                )
                existing.id
            } else {
                d.createImported(
                    OrbitaMetaEntity(nombre = nombre, autorNombre = preview.autorNombre, esPropia = false, fechaImport = now),
                    songs
                )
            }
        }

    // ------------------------------------------------------------------ copia de seguridad

    /**
     * La órbita PROPIA como JSON para el respaldo de configuración (sin carátulas: al restaurar
     * se resuelven por ruta o título+artista contra la biblioteca). null si no hay nada que guardar.
     */
    suspend fun exportOwnForBackup(): JSONArray? = withContext(Dispatchers.IO) {
        val d = dao()
        val own = d.getOwn() ?: return@withContext null
        val songs = d.songsOf(own.id)
        if (songs.isEmpty()) return@withContext null
        val arr = JSONArray()
        for (s in songs) {
            arr.put(
                JSONObject()
                    .put("path", s.path ?: JSONObject.NULL)
                    .put("titulo", s.titulo).put("artista", s.artista).put("album", s.album)
                    .put("nota", s.nota).put("fechaAgregada", s.fechaAgregada)
                    .put("posX", s.posX.toDouble()).put("posY", s.posY.toDouble())
            )
        }
        arr
    }

    /** Restaura la órbita propia desde el respaldo: reemplaza sus nodos por los del archivo. */
    suspend fun restoreOwnFromBackup(arr: JSONArray) = withContext(Dispatchers.IO) {
        val d = dao()
        val ownId = d.getOwn()?.id ?: d.insertMeta(
            OrbitaMetaEntity(
                nombre = appContext.getString(com.music.musicflame.R.string.orbita_own_name),
                autorNombre = currentAuthorName(appContext), esPropia = true, fechaImport = 0L
            )
        )
        val rows = ArrayList<OrbitaSongEntity>()
        for (i in 0 until minOf(arr.length(), MAX_SONGS)) {
            val o = arr.optJSONObject(i) ?: continue
            val titulo = o.optString("titulo", "").trim()
            if (titulo.isEmpty()) continue
            val base = OrbitaSongEntity(
                orbitaId = ownId, songId = null,
                path = if (o.isNull("path")) null else o.optString("path").ifEmpty { null },
                titulo = titulo, artista = o.optString("artista", ""), album = o.optString("album", ""),
                nota = o.optString("nota", ""), fechaAgregada = o.optLong("fechaAgregada", 0L),
                posX = o.optDouble("posX", 0.5).toFloat().coerceIn(0f, 1f),
                posY = o.optDouble("posY", 0.5).toFloat().coerceIn(0f, 1f), caratula = null
            )
            rows.add(base.copy(songId = findLocalSong(base)?.id))
        }
        if (rows.isNotEmpty()) {
            d.deleteSongsOf(ownId)
            d.insertSongs(rows)
        }
    }

    // ------------------------------------------------------------------ resolver canciones locales

    /** Canción local de la biblioteca que corresponde a un nodo (por id, ruta o título+artista). */
    fun findLocalSong(s: OrbitaSongEntity): Song? {
        val lib = SongLibraryHolder.songs
        s.songId?.let { id -> lib.firstOrNull { it.id == id }?.let { return it } }
        s.path?.takeIf { it.isNotBlank() }?.let { p -> lib.firstOrNull { it.path == p }?.let { return it } }
        return lib.firstOrNull {
            it.title.equals(s.titulo, ignoreCase = true) && it.artist.equals(s.artista, ignoreCase = true)
        }
    }
}