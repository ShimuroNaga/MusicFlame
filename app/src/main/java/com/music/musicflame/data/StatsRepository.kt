package com.music.musicflame.data

import android.content.Context
import com.music.musicflame.data.db.AppDatabase
import com.music.musicflame.data.db.RoomStoreEngine
import com.music.musicflame.data.db.SongStatEntity
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

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
 * Estado compartido (memoria + Room) de StatsRepository. Ver RoomStoreEngine para el patrón de
 * arranque/migración/escritura. Antes: SharedPreferences "song_stats", clave "stats_map" (JSON).
 */
private object StatsStore : RoomStoreEngine<Long, SongStat>(
    tag = "StatsStore",
    legacyPrefsName = "song_stats",
    legacyKey = "stats_map"
) {
    val memory = ConcurrentHashMap<Long, SongStat>()
    val lock = Any()

    private fun dao() = AppDatabase.getInstance(appContext).songStatDao()

    private fun parseLegacy(json: String): Map<Long, SongStat> {
        val obj = JSONObject(json) // JSONException si está corrupto
        val result = LinkedHashMap<Long, SongStat>()
        obj.keys().forEach { key ->
            val id = key.toLongOrNull() ?: return@forEach
            val entry = obj.optJSONObject(key) ?: return@forEach
            result[id] = SongStat(
                playCount = entry.optInt("playCount", 0),
                totalListenedMs = entry.optLong("totalListenedMs", 0L),
                lastPlayedAt = entry.optLong("lastPlayedAt", 0L)
            )
        }
        return result
    }

    override suspend fun migrateLegacy(json: String): Int {
        val parsed = parseLegacy(json)
        if (parsed.isNotEmpty()) {
            dao().insertAllIgnoringExisting(parsed.map { (id, s) -> SongStatEntity(id, s.playCount, s.totalListenedMs, s.lastPlayedAt) })
        }
        val inRoom = dao().count()
        check(inRoom >= parsed.size) { "verificación falló: Room tiene $inRoom filas y el JSON tenía ${parsed.size}" }
        return parsed.size
    }

    override suspend fun loadFromRoom() {
        dao().getAll().forEach { memory[it.songId] = SongStat(it.playCount, it.totalListenedMs, it.lastPlayedAt) }
    }

    override fun loadFromLegacyJson(json: String) {
        memory.putAll(parseLegacy(json))
    }

    override suspend fun flush(batch: Map<Long, SongStat>) {
        dao().upsertAll(batch.map { (id, s) -> SongStatEntity(id, s.playCount, s.totalListenedMs, s.lastPlayedAt) })
    }

    override fun serializeLegacy(): String {
        val obj = JSONObject()
        memory.forEach { (id, stat) ->
            obj.put(
                id.toString(),
                JSONObject()
                    .put("playCount", stat.playCount)
                    .put("totalListenedMs", stat.totalListenedMs)
                    .put("lastPlayedAt", stat.lastPlayedAt)
            )
        }
        return obj.toString()
    }

    override fun describeMemory(): String = "${memory.size} canciones con estadísticas"

    fun put(songId: Long, stat: SongStat) {
        memory[songId] = stat
        enqueue(songId, stat)
    }
}

/**
 * Guarda y lee las estadísticas de reproducción por canción, indexadas por el ID de MediaStore
 * (igual que SongCustomizationRepository). Almacenamiento: Room (tabla song_stats). Interfaz pública
 * idéntica a la versión con SharedPreferences; las lecturas son de memoria (sin disco).
 */
class StatsRepository(context: Context) {

    init {
        StatsStore.start(context)
    }

    companion object {
        /** Arranca la carga/migración en segundo plano (llamar desde MainActivity.onCreate). */
        fun preload(context: Context) = StatsStore.start(context)
    }

    /** Suma una reproducción a la canción (se llama cada vez que empieza a sonar). */
    fun incrementPlayCount(songId: Long) {
        StatsStore.awaitLoaded()
        synchronized(StatsStore.lock) {
            val current = StatsStore.memory[songId] ?: SongStat()
            StatsStore.put(
                songId,
                current.copy(playCount = current.playCount + 1, lastPlayedAt = System.currentTimeMillis())
            )
        }
    }

    /** Suma tiempo escuchado (en ms) a la canción que está sonando ahora mismo. */
    fun addListenedTime(songId: Long, deltaMs: Long) {
        if (deltaMs <= 0L) return
        StatsStore.awaitLoaded()
        synchronized(StatsStore.lock) {
            val current = StatsStore.memory[songId] ?: SongStat()
            StatsStore.put(songId, current.copy(totalListenedMs = current.totalListenedMs + deltaMs))
        }
    }

    fun getStat(songId: Long): SongStat {
        StatsStore.awaitLoaded()
        return StatsStore.memory[songId] ?: SongStat()
    }

    fun getAllStats(): Map<Long, SongStat> {
        StatsStore.awaitLoaded()
        return StatsStore.memory.toMap()
    }

    /**
     * Top N canciones ordenadas por número de reproducciones (de más a menos).
     *
     * Solo se consideran canciones que llegaron a un mínimo de reproducciones
     * acumuladas (minPlayCount, no tienen que ser seguidas/consecutivas). Esto
     * evita que una canción escuchada una sola vez aparezca de entrada en el
     * Top 20 de "Tu Mix" solo porque hay pocas canciones con estadísticas todavía.
     */
    fun getTopPlayed(limit: Int = 20, minPlayCount: Int = 3): List<Pair<Long, SongStat>> =
        getAllStats().entries
            .filter { it.value.playCount >= minPlayCount }
            .sortedWith(compareByDescending<Map.Entry<Long, SongStat>> { it.value.playCount }
                .thenByDescending { it.value.totalListenedMs })
            .take(limit)
            .map { it.key to it.value }
}
