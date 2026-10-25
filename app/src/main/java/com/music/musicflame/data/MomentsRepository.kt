package com.music.musicflame.data

import android.content.Context
import com.music.musicflame.data.db.AppDatabase
import com.music.musicflame.data.db.MomentEntity
import com.music.musicflame.data.db.RoomStoreEngine
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Un "Momento": fragmento favorito de una canción (ej. el coro), marcado por el usuario.
 * [startMs]/[endMs] son posiciones ABSOLUTAS dentro del archivo de audio.
 */
data class Moment(
    val id: String,
    val startMs: Long,
    val endMs: Long,
    val createdAt: Long
) {
    val durationMs: Long get() = endMs - startMs
}

/**
 * Estado compartido (memoria + Room) de MomentsRepository. Ver RoomStoreEngine para el patrón de
 * arranque/migración/escritura. Antes: SharedPreferences "song_moments", clave "moments_map" (JSON).
 * La operación en cola es la lista COMPLETA de momentos de esa ruta (vacía = borrar todos).
 */
private class MomentsOp(val moments: List<Moment>)

private object MomentsStore : RoomStoreEngine<String, MomentsOp>(
    tag = "MomentsStore",
    legacyPrefsName = "song_moments",
    legacyKey = "moments_map"
) {
    /** Listas inmutables (copy-on-write): cada cambio reemplaza la lista de esa ruta. */
    val memory = ConcurrentHashMap<String, List<Moment>>()
    val lock = Any()

    private fun dao() = AppDatabase.getInstance(appContext).momentDao()

    /** Guarda la lista completa de una ruta (vacía = quitar la ruta). Llamar con [lock] tomado. */
    fun commit(path: String, moments: List<Moment>) {
        if (moments.isEmpty()) memory.remove(path) else memory[path] = moments.toList()
        enqueue(path, MomentsOp(moments.toList()))
    }

    private fun parseLegacy(json: String): Map<String, List<Moment>> {
        val obj = JSONObject(json) // JSONException si está corrupto
        val result = LinkedHashMap<String, List<Moment>>()
        val usedIds = HashSet<String>()
        obj.keys().forEach { path ->
            val arr = obj.optJSONArray(path) ?: return@forEach
            val list = ArrayList<Moment>()
            for (i in 0 until arr.length()) {
                val m = arr.optJSONObject(i) ?: continue
                var id = m.optString("id", UUID.randomUUID().toString())
                // La clave primaria es el id: si se repite, el momento recibe un id nuevo en vez de perderse.
                while (!usedIds.add(id)) id = UUID.randomUUID().toString()
                list.add(
                    Moment(
                        id = id,
                        startMs = m.optLong("startMs", 0L),
                        endMs = m.optLong("endMs", 0L),
                        createdAt = m.optLong("createdAt", 0L)
                    )
                )
            }
            if (list.isNotEmpty()) result[path] = list
        }
        return result
    }

    private fun toEntities(path: String, moments: List<Moment>) =
        moments.map { MomentEntity(it.id, path, it.startMs, it.endMs, it.createdAt) }

    override suspend fun migrateLegacy(json: String): Int {
        val parsed = parseLegacy(json)
        val entities = parsed.flatMap { (path, list) -> toEntities(path, list) }
        if (entities.isNotEmpty()) dao().insertAllIgnoringExisting(entities)
        val inRoom = dao().count()
        check(inRoom >= entities.size) { "verificación falló: Room tiene $inRoom filas y el JSON tenía ${entities.size}" }
        return entities.size
    }

    override suspend fun loadFromRoom() {
        dao().getAll().groupBy { it.path }.forEach { (path, rows) ->
            memory[path] = rows.map { Moment(it.id, it.startMs, it.endMs, it.createdAt) }
        }
    }

    override fun loadFromLegacyJson(json: String) {
        memory.putAll(parseLegacy(json))
    }

    override suspend fun flush(batch: Map<String, MomentsOp>) {
        val entities = batch.flatMap { (path, op) -> toEntities(path, op.moments) }
        dao().replaceForPaths(batch.keys.toList(), entities)
    }

    override fun serializeLegacy(): String {
        val obj = JSONObject()
        memory.forEach { (path, list) ->
            if (list.isEmpty()) return@forEach
            val arr = JSONArray()
            list.forEach { m ->
                arr.put(
                    JSONObject()
                        .put("id", m.id)
                        .put("startMs", m.startMs)
                        .put("endMs", m.endMs)
                        .put("createdAt", m.createdAt)
                )
            }
            obj.put(path, arr)
        }
        return obj.toString()
    }

    override fun describeMemory(): String = "${memory.size} canciones con momentos"
}

/**
 * Guarda los Momentos de cada canción. Almacenamiento: Room (tabla moments). Interfaz pública
 * idéntica a la versión con SharedPreferences; las lecturas son de memoria (sin disco).
 *
 * Se indexa por la RUTA del archivo (no por el id de MediaStore): los ids pueden cambiar
 * cuando Android re-escanea el archivo, la ruta no.
 */
class MomentsRepository(context: Context) {

    init {
        MomentsStore.start(context)
    }

    companion object {
        /** Largo mínimo de un momento. */
        const val MIN_DURATION_MS = 3_000L
        /** Largo del momento "rápido" (mantener presionado el botón). */
        const val QUICK_DURATION_MS = 15_000L

        /** Arranca la carga/migración en segundo plano (llamar desde MainActivity.onCreate). */
        fun preload(context: Context) = MomentsStore.start(context)
    }

    /** Momentos de una canción, ordenados por inicio. */
    fun getMoments(path: String): List<Moment> {
        MomentsStore.awaitLoaded()
        return MomentsStore.memory[path].orEmpty().sortedBy { it.startMs }
    }

    /** Todos los momentos de la biblioteca, por ruta de archivo. */
    fun getAll(): Map<String, List<Moment>> {
        MomentsStore.awaitLoaded()
        return MomentsStore.memory.toMap()
    }

    /**
     * Guarda un momento nuevo. Si se solapa con alguno existente se FUSIONAN en uno solo
     * (así nunca hay dos franjas encimadas). Devuelve null si es más corto que [MIN_DURATION_MS].
     */
    fun addMoment(path: String, startMs: Long, endMs: Long): Moment? {
        val start = minOf(startMs, endMs).coerceAtLeast(0L)
        val end = maxOf(startMs, endMs)
        if (end - start < MIN_DURATION_MS) return null

        MomentsStore.awaitLoaded()
        synchronized(MomentsStore.lock) {
            val list = (MomentsStore.memory[path] ?: emptyList()).toMutableList()
            var mergedStart = start
            var mergedEnd = end
            val overlapping = list.filter { it.startMs <= end && it.endMs >= start }
            overlapping.forEach {
                mergedStart = minOf(mergedStart, it.startMs)
                mergedEnd = maxOf(mergedEnd, it.endMs)
            }
            list.removeAll(overlapping.toSet())
            val moment = Moment(UUID.randomUUID().toString(), mergedStart, mergedEnd, System.currentTimeMillis())
            list.add(moment)
            MomentsStore.commit(path, list)
            return moment
        }
    }

    /** Ajusta inicio/fin de un momento. Devuelve false si queda más corto que el mínimo. */
    fun updateMoment(path: String, id: String, startMs: Long, endMs: Long): Boolean {
        if (endMs - startMs < MIN_DURATION_MS) return false
        MomentsStore.awaitLoaded()
        synchronized(MomentsStore.lock) {
            val list = MomentsStore.memory[path]?.toMutableList() ?: return false
            val idx = list.indexOfFirst { it.id == id }
            if (idx < 0) return false
            list[idx] = list[idx].copy(startMs = startMs.coerceAtLeast(0L), endMs = endMs)
            MomentsStore.commit(path, list)
            return true
        }
    }

    fun deleteMoment(path: String, id: String) {
        MomentsStore.awaitLoaded()
        synchronized(MomentsStore.lock) {
            val list = MomentsStore.memory[path]?.toMutableList() ?: return
            list.removeAll { it.id == id }
            MomentsStore.commit(path, list)
        }
    }
}
