package com.music.musicflame.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

/**
 * AUTOMIX: resultado del análisis de ritmo de una canción (lo calcula AutomixAnalyzer).
 *
 * @param bpm tempo en pulsos por minuto (0 = análisis fallido, ver [UNUSABLE]).
 * @param key tonalidad como semitono 0-11 (0 = Do), o -1 si no se pudo estimar.
 * @param beatPhaseMs instante (ms desde el inicio) de un beat de la rejilla, normalizado a [0, periodo).
 * @param confidence 0..1. Ojo: NO es una probabilidad calibrada de que el ritmo sea correcto.
 * @param lastBeatMs instante (ms) del último beat de la rejilla dentro de la canción. La canción que
 *        SALE se alinea contra este (cerca del final), no contra el primero: así un BPM con un
 *        error de 0,1 no acumula cientos de ms de desfase a lo largo de 4 minutos.
 */
data class AutomixData(
    val bpm: Float,
    val key: Int,
    val beatPhaseMs: Long,
    val confidence: Float,
    val lastBeatMs: Long = 0L
) {
    companion object {
        /** Marca "ya se intentó y no hay datos útiles": evita re-analizar (30 s de CPU) en cada reproducción. */
        val UNUSABLE = AutomixData(0f, -1, 0L, 0f, 0L)
    }
}

/**
 * AUTOMIX: cachea, por canción (clave = ruta física del archivo), el BPM / tonalidad / fase de beat.
 *
 * Misma estructura que VolumeNormalizationCacheRepository: SharedPreferences con un solo JSON, mapa
 * en memoria compartido (companion) y escritura diferida con DebouncedTask para no reserializar todo
 * el JSON en cada canción nueva.
 */
class AutomixCacheRepository(context: Context) {
    private val prefs = context.getSharedPreferences("automix_cache", Context.MODE_PRIVATE)

    init {
        prefsRef = prefs
    }

    private companion object {
        const val KEY_MAP = "bpm_key_map_v1"

        /** Por debajo de esto, get() ignora el dato: el beat matching con un ritmo dudoso suena peor que un fundido normal. */
        const val MIN_USABLE_CONFIDENCE = 0.7f

        @Volatile private var prefsRef: SharedPreferences? = null
        @Volatile private var cache: MutableMap<String, AutomixData>? = null
        private val lock = Any()

        private val writer = DebouncedTask(2000L) { persistNow() }

        private fun persistNow() {
            val p = prefsRef ?: return
            val snapshot = synchronized(lock) { cache?.toMap() } ?: return
            val obj = JSONObject()
            try {
                snapshot.forEach { (path, d) ->
                    obj.put(
                        path,
                        JSONObject()
                            .put("bpm", d.bpm.toDouble())
                            .put("key", d.key)
                            .put("beatPhaseMs", d.beatPhaseMs)
                            .put("lastBeatMs", d.lastBeatMs)
                            .put("confidence", d.confidence.toDouble())
                    )
                }
                p.edit().putString(KEY_MAP, obj.toString()).apply()
            } catch (e: Exception) {
                // Si no se pudo guardar, la canción se vuelve a analizar la próxima vez que suene.
            }
        }
    }

    // Debe llamarse con [lock] tomado.
    private fun memory(): MutableMap<String, AutomixData> {
        cache?.let { return it }
        val result = HashMap<String, AutomixData>()
        val json = prefs.getString(KEY_MAP, null)
        if (json != null) {
            try {
                val obj = JSONObject(json)
                obj.keys().forEach { path ->
                    val o = obj.optJSONObject(path) ?: return@forEach
                    result[path] = AutomixData(
                        bpm = o.optDouble("bpm", 0.0).toFloat(),
                        key = o.optInt("key", -1),
                        beatPhaseMs = o.optLong("beatPhaseMs", 0L),
                        confidence = o.optDouble("confidence", 0.0).toFloat(),
                        lastBeatMs = o.optLong("lastBeatMs", 0L)
                    )
                }
            } catch (e: Exception) {
                // JSON corrupto: se arranca en blanco; peor caso se re-analizan las canciones.
                result.clear()
            }
        }
        cache = result
        return result
    }

    /** Datos de la canción, o null si no hay, el análisis falló o la confianza es menor a 0.7. */
    fun get(filePath: String): AutomixData? {
        if (filePath.isEmpty()) return null
        val d = synchronized(lock) { memory()[filePath] } ?: return null
        return if (d.bpm > 0f && d.confidence >= MIN_USABLE_CONFIDENCE) d else null
    }

    /** true si ya se intentó analizar (aunque el resultado no sirva): para no repetir el análisis. */
    fun has(filePath: String): Boolean {
        if (filePath.isEmpty()) return false
        return synchronized(lock) { memory().containsKey(filePath) }
    }

    fun set(filePath: String, data: AutomixData) {
        if (filePath.isEmpty()) return
        val changed = synchronized(lock) {
            val map = memory()
            if (map[filePath] == data) {
                false
            } else {
                map[filePath] = data
                true
            }
        }
        if (changed) writer.request()
    }

    /** Borra una canción para forzar que se re-analice la próxima vez. */
    fun invalidate(filePath: String) {
        val changed = synchronized(lock) { memory().remove(filePath) != null }
        if (changed) writer.request()
    }

    /** Borra todo el caché (y lo guarda ya: es una acción del usuario). */
    fun clearAll() {
        synchronized(lock) { memory().clear() }
        writer.flushNow()
    }

    /** Cuántas canciones tienen datos utilizables (no cuenta los intentos fallidos). */
    fun countAnalyzed(): Int = synchronized(lock) { memory().values.count { it.bpm > 0f } }
}
