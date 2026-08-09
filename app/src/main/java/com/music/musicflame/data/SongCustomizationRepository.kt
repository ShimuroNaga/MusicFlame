package com.music.musicflame.data

import android.content.Context
import org.json.JSONObject

/**
 * Personalización guardada para una canción: nombre y/o carátula (imagen o GIF)
 * elegidos por el usuario, que sobreescriben los metadatos originales del archivo.
 */
data class SongCustomization(
    val title: String? = null,
    val coverUri: String? = null
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
                if (title != null || coverUri != null) {
                    result[key] = SongCustomization(title = title, coverUri = coverUri)
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
            if (custom.title != null || custom.coverUri != null) {
                val entry = JSONObject()
                if (custom.title != null) entry.put("title", custom.title)
                if (custom.coverUri != null) entry.put("coverUri", custom.coverUri)
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
     * Guarda nombre y/o carátula personalizados para una canción.
     * Pasar `null` en cualquiera de los dos deja ese campo sin cambios respecto
     * a lo que ya hubiera guardado (usar clearTitle/clearCover para borrarlo).
     */
    fun setCustomization(
        songId: Long,
        title: String? = null,
        coverUri: String? = null,
        clearTitle: Boolean = false,
        clearCover: Boolean = false
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

        if (newTitle == null && newCover == null) {
            map.remove(key)
        } else {
            map[key] = SongCustomization(title = newTitle, coverUri = newCover)
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

    /** Elimina toda personalización (nombre y carátula) de una canción. */
    fun clearCustomization(songId: Long) {
        val map = readAll()
        map.remove(songId.toString())
        writeAll(map)
    }
}