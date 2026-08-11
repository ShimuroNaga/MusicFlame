package com.music.musicflame.data

import android.content.Context
import org.json.JSONObject

enum class LyricsSource { MANUAL, ONLINE }

data class StoredLyrics(
    val raw: String,
    val source: LyricsSource
)

/**
 * Guarda la letra (formato LRC si está sincronizada, o texto plano) por canción,
 * indexada por el ID de MediaStore, siguiendo el mismo patrón que
 * SongCustomizationRepository. También resuelve búsquedas online contra lrclib.net.
 */
class LyricsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("song_lyrics", Context.MODE_PRIVATE)
    private val KEY_MAP = "lyrics_map"

    private fun readAll(): MutableMap<String, StoredLyrics> {
        val json = prefs.getString(KEY_MAP, null) ?: return mutableMapOf()
        val result = mutableMapOf<String, StoredLyrics>()
        return try {
            val obj = JSONObject(json)
            obj.keys().forEach { key ->
                val entry = obj.optJSONObject(key) ?: return@forEach
                val raw = entry.optString("raw", "")
                val source = try {
                    LyricsSource.valueOf(entry.optString("source", "MANUAL"))
                } catch (e: Exception) {
                    LyricsSource.MANUAL
                }
                if (raw.isNotBlank()) result[key] = StoredLyrics(raw, source)
            }
            result
        } catch (e: Exception) {
            mutableMapOf()
        }
    }

    private fun writeAll(map: Map<String, StoredLyrics>) {
        val obj = JSONObject()
        map.forEach { (id, stored) ->
            val entry = JSONObject()
            entry.put("raw", stored.raw)
            entry.put("source", stored.source.name)
            obj.put(id, entry)
        }
        prefs.edit().putString(KEY_MAP, obj.toString()).apply()
    }

    fun getLyrics(songId: Long): StoredLyrics? = readAll()[songId.toString()]

    fun saveLyrics(songId: Long, raw: String, source: LyricsSource) {
        val map = readAll()
        map[songId.toString()] = StoredLyrics(raw.trim(), source)
        writeAll(map)
    }

    fun clearLyrics(songId: Long) {
        val map = readAll()
        map.remove(songId.toString())
        writeAll(map)
    }

    /**
     * Busca la letra online en lrclib.net. Intenta primero una coincidencia
     * exacta (título + artista + duración) y, si no hay resultado, cae a
     * búsqueda libre devolviendo la mejor coincidencia disponible.
     * Debe llamarse desde una corrutina (IO).
     */
    suspend fun searchOnline(title: String, artist: String, durationSeconds: Int?): LrcLibResult? {
        return try {
            val exact = LyricsApi.service.get(
                trackName = title,
                artistName = artist,
                durationSeconds = durationSeconds
            )
            val exactBody = exact.body()
            if (exact.isSuccessful && exactBody != null &&
                (!exactBody.syncedLyrics.isNullOrBlank() || !exactBody.plainLyrics.isNullOrBlank())
            ) {
                return exactBody
            }

            val search = LyricsApi.service.search(trackName = title, artistName = artist)
            search.body()?.firstOrNull {
                !it.syncedLyrics.isNullOrBlank() || !it.plainLyrics.isNullOrBlank()
            }
        } catch (e: Exception) {
            null
        }
    }
}
