package com.music.musicflame.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

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
 * Guarda los Momentos de cada canción en SharedPreferences (JSON), mismo patrón que
 * StatsRepository / SongCustomizationRepository.
 *
 * Se indexa por la RUTA del archivo (no por el id de MediaStore): los ids pueden cambiar
 * cuando Android re-escanea el archivo, la ruta no.
 */
class MomentsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("song_moments", Context.MODE_PRIVATE)
    private val KEY_MAP = "moments_map"

    companion object {
        /** Largo mínimo de un momento. */
        const val MIN_DURATION_MS = 3_000L
        /** Largo del momento "rápido" (mantener presionado el botón). */
        const val QUICK_DURATION_MS = 15_000L
    }

    private fun readAll(): MutableMap<String, MutableList<Moment>> {
        val json = prefs.getString(KEY_MAP, null) ?: return mutableMapOf()
        val result = mutableMapOf<String, MutableList<Moment>>()
        return try {
            val obj = JSONObject(json)
            obj.keys().forEach { path ->
                val arr = obj.optJSONArray(path) ?: return@forEach
                val list = mutableListOf<Moment>()
                for (i in 0 until arr.length()) {
                    val m = arr.optJSONObject(i) ?: continue
                    list.add(
                        Moment(
                            id = m.optString("id", UUID.randomUUID().toString()),
                            startMs = m.optLong("startMs", 0L),
                            endMs = m.optLong("endMs", 0L),
                            createdAt = m.optLong("createdAt", 0L)
                        )
                    )
                }
                if (list.isNotEmpty()) result[path] = list
            }
            result
        } catch (e: Exception) {
            mutableMapOf()
        }
    }

    private fun writeAll(map: Map<String, List<Moment>>) {
        val obj = JSONObject()
        map.forEach { (path, list) ->
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
        prefs.edit().putString(KEY_MAP, obj.toString()).apply()
    }

    /** Momentos de una canción, ordenados por inicio. */
    fun getMoments(path: String): List<Moment> =
        readAll()[path].orEmpty().sortedBy { it.startMs }

    /** Todos los momentos de la biblioteca, por ruta de archivo. */
    fun getAll(): Map<String, List<Moment>> = readAll()

    /**
     * Guarda un momento nuevo. Si se solapa con alguno existente se FUSIONAN en uno solo
     * (así nunca hay dos franjas encimadas). Devuelve null si es más corto que [MIN_DURATION_MS].
     */
    fun addMoment(path: String, startMs: Long, endMs: Long): Moment? {
        val start = minOf(startMs, endMs).coerceAtLeast(0L)
        val end = maxOf(startMs, endMs)
        if (end - start < MIN_DURATION_MS) return null

        val all = readAll()
        val list = all.getOrPut(path) { mutableListOf() }
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
        writeAll(all)
        return moment
    }

    /** Ajusta inicio/fin de un momento. Devuelve false si queda más corto que el mínimo. */
    fun updateMoment(path: String, id: String, startMs: Long, endMs: Long): Boolean {
        if (endMs - startMs < MIN_DURATION_MS) return false
        val all = readAll()
        val list = all[path] ?: return false
        val idx = list.indexOfFirst { it.id == id }
        if (idx < 0) return false
        list[idx] = list[idx].copy(startMs = startMs.coerceAtLeast(0L), endMs = endMs)
        writeAll(all)
        return true
    }

    fun deleteMoment(path: String, id: String) {
        val all = readAll()
        val list = all[path] ?: return
        list.removeAll { it.id == id }
        if (list.isEmpty()) all.remove(path)
        writeAll(all)
    }
}
