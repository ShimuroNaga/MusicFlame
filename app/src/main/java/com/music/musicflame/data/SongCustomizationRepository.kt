package com.music.musicflame.data

import android.content.Context
import org.json.JSONObject

/**
 * Personalización guardada para una canción: nombre, artista, álbum y/o carátula
 * (imagen o GIF) elegidos por el usuario, que sobreescriben los metadatos
 * originales del archivo.
 */
data class SongCustomization(
    val title: String? = null,
    val coverUri: String? = null,
    // --- NUEVO: editor de etiquetas/metadata ---
    val artist: String? = null,
    val album: String? = null
)

/**
 * Guarda y lee, en SharedPreferences (como JSON), las personalizaciones de
 * nombre/carátula por canción. Se indexa por el ID de MediaStore de cada canción.
 */
class SongCustomizationRepository(context: Context) {
    private val prefs = context.getSharedPreferences("song_customizations", Context.MODE_PRIVATE)
    private val KEY_MAP = "customizations_map"

    private fun readAll(): MutableMap<String, SongCustomization> {
        val json = prefs.getString(KEY_MAP, null) ?: return mutableMapOf()
        val result = mutableMapOf<String, SongCustomization>()
        return try {
            val obj = JSONObject(json)
            obj.keys().forEach { key ->
                val entry = obj.optJSONObject(key) ?: return@forEach
                val title = if (entry.has("title") && !entry.isNull("title")) entry.getString("title") else null
                val coverUri = if (entry.has("coverUri") && !entry.isNull("coverUri")) entry.getString("coverUri") else null
                val artist = if (entry.has("artist") && !entry.isNull("artist")) entry.getString("artist") else null
                val album = if (entry.has("album") && !entry.isNull("album")) entry.getString("album") else null
                if (title != null || coverUri != null || artist != null || album != null) {
                    result[key] = SongCustomization(title = title, coverUri = coverUri, artist = artist, album = album)
                }
            }
            result
        } catch (e: Exception) {
            mutableMapOf()
        }
    }

    private fun writeAll(map: Map<String, SongCustomization>) {
        val obj = JSONObject()
        map.forEach { (id, custom) ->
            if (custom.title != null || custom.coverUri != null || custom.artist != null || custom.album != null) {
                val entry = JSONObject()
                if (custom.title != null) entry.put("title", custom.title)
                if (custom.coverUri != null) entry.put("coverUri", custom.coverUri)
                if (custom.artist != null) entry.put("artist", custom.artist)
                if (custom.album != null) entry.put("album", custom.album)
                obj.put(id, entry)
            }
        }
        prefs.edit().putString(KEY_MAP, obj.toString()).apply()
    }

    /** Devuelve la personalización guardada para una canción, o null si no tiene ninguna. */
    fun getCustomization(songId: Long): SongCustomization? = readAll()[songId.toString()]

    /** Devuelve todas las personalizaciones guardadas, indexadas por ID de canción. */
    fun getAll(): Map<Long, SongCustomization> =
        readAll().mapNotNull { (key, value) -> key.toLongOrNull()?.let { it to value } }.toMap()

    /**
     * Guarda nombre, artista, álbum y/o carátula personalizados para una canción.
     * Pasar `null` en cualquiera deja ese campo sin cambios respecto a lo que ya
     * hubiera guardado (usar los flags clear* para borrarlo puntualmente).
     */
    fun setCustomization(
        songId: Long,
        title: String? = null,
        coverUri: String? = null,
        artist: String? = null,
        album: String? = null,
        clearTitle: Boolean = false,
        clearCover: Boolean = false,
        clearArtist: Boolean = false,
        clearAlbum: Boolean = false
    ) {
        val map = readAll()
        val key = songId.toString()
        val current = map[key] ?: SongCustomization()

        val newTitle = when {
            clearTitle -> null
            title != null -> title.trim().takeIf { it.isNotBlank() }
            else -> current.title
        }
        val newCover = when {
            clearCover -> null
            coverUri != null -> coverUri.takeIf { it.isNotBlank() }
            else -> current.coverUri
        }
        val newArtist = when {
            clearArtist -> null
            artist != null -> artist.trim().takeIf { it.isNotBlank() }
            else -> current.artist
        }
        val newAlbum = when {
            clearAlbum -> null
            album != null -> album.trim().takeIf { it.isNotBlank() }
            else -> current.album
        }

        if (newTitle == null && newCover == null && newArtist == null && newAlbum == null) {
            map.remove(key)
        } else {
            map[key] = SongCustomization(title = newTitle, coverUri = newCover, artist = newArtist, album = newAlbum)
        }
        writeAll(map)
    }

    /** Aplica la misma carátula personalizada a varias canciones a la vez. */
    fun setCoverForSongs(songIds: List<Long>, coverUri: String?, clearCover: Boolean = false) {
        val map = readAll()
        songIds.forEach { id ->
            val key = id.toString()
            val current = map[key] ?: SongCustomization()
            val newCover = if (clearCover) null else coverUri
            val updated = current.copy(coverUri = newCover)
            if (updated.title == null && updated.coverUri == null) map.remove(key) else map[key] = updated
        }
        writeAll(map)
    }

    /**
     * Aplica el mismo nombre de álbum a varias canciones a la vez (para juntarlas
     * en un solo álbum desde selección múltiple, sin tener que editarlas una por una).
     * No toca la carátula de cada canción; combínalo con [setCoverForSongs] si además
     * quieres que todas compartan una misma carátula para representar el álbum.
     */
    fun setAlbumForSongs(songIds: List<Long>, album: String?, clearAlbum: Boolean = false) {
        val map = readAll()
        val newAlbum = if (clearAlbum) null else album?.trim()?.takeIf { it.isNotBlank() }
        songIds.forEach { id ->
            val key = id.toString()
            val current = map[key] ?: SongCustomization()
            val updated = current.copy(album = newAlbum)
            if (updated.title == null && updated.coverUri == null && updated.artist == null && updated.album == null) {
                map.remove(key)
            } else {
                map[key] = updated
            }
        }
        writeAll(map)
    }

    /** Elimina toda personalización (nombre y carátula) de una canción. */
    fun clearCustomization(songId: Long) {
        val map = readAll()
        map.remove(songId.toString())
        writeAll(map)
    }
}