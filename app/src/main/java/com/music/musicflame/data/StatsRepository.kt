package com.music.musicflame.data

import android.content.Context
import org.json.JSONObject

/**
 * Estadísticas acumuladas de una canción: cuántas veces se reprodujo, cuánto
 * tiempo total se escuchó y cuándo fue la última vez que sonó.
 */
data class SongStat(
    val playCount: Int = 0,
    val totalListenedMs: Long = 0L,
    val lastPlayedAt: Long = 0L
)

/**
 * Guarda y lee, en SharedPreferences (como JSON), las estadísticas de reproducción
 * por canción. Se indexa por el ID de MediaStore de cada canción, igual que
 * SongCustomizationRepository.
 */
class StatsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("song_stats", Context.MODE_PRIVATE)
    private val KEY_MAP = "stats_map"

    private fun readAll(): MutableMap<String, SongStat> {
        val json = prefs.getString(KEY_MAP, null) ?: return mutableMapOf()
        val result = mutableMapOf<String, SongStat>()
        return try {
            val obj = JSONObject(json)
            obj.keys().forEach { key ->
                val entry = obj.optJSONObject(key) ?: return@forEach
                result[key] = SongStat(
                    playCount = entry.optInt("playCount", 0),
                    totalListenedMs = entry.optLong("totalListenedMs", 0L),
                    lastPlayedAt = entry.optLong("lastPlayedAt", 0L)
                )
            }
            result
        } catch (e: Exception) {
            mutableMapOf()
        }
    }

    private fun writeAll(map: Map<String, SongStat>) {
        val obj = JSONObject()
        map.forEach { (id, stat) ->
            val entry = JSONObject()
            entry.put("playCount", stat.playCount)
            entry.put("totalListenedMs", stat.totalListenedMs)
            entry.put("lastPlayedAt", stat.lastPlayedAt)
            obj.put(id, entry)
        }
        prefs.edit().putString(KEY_MAP, obj.toString()).apply()
    }

    /** Suma una reproducción a la canción (se llama cada vez que empieza a sonar). */
    fun incrementPlayCount(songId: Long) {
        val map = readAll()
        val key = songId.toString()
        val current = map[key] ?: SongStat()
        map[key] = current.copy(
            playCount = current.playCount + 1,
            lastPlayedAt = System.currentTimeMillis()
        )
        writeAll(map)
    }

    /** Suma tiempo escuchado (en ms) a la canción que está sonando ahora mismo. */
    fun addListenedTime(songId: Long, deltaMs: Long) {
        if (deltaMs <= 0L) return
        val map = readAll()
        val key = songId.toString()
        val current = map[key] ?: SongStat()
        map[key] = current.copy(totalListenedMs = current.totalListenedMs + deltaMs)
        writeAll(map)
    }

    fun getStat(songId: Long): SongStat = readAll()[songId.toString()] ?: SongStat()

    fun getAllStats(): Map<Long, SongStat> =
        readAll().mapNotNull { (key, value) -> key.toLongOrNull()?.let { it to value } }.toMap()

    /** Top N canciones ordenadas por número de reproducciones (de más a menos). */
    fun getTopPlayed(limit: Int = 20): List<Pair<Long, SongStat>> =
        getAllStats().entries
            .sortedWith(compareByDescending<Map.Entry<Long, SongStat>> { it.value.playCount }
                .thenByDescending { it.value.totalListenedMs })
            .take(limit)
            .map { it.key to it.value }
}
