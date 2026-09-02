package com.music.musicflame.data

import android.content.Context
import org.json.JSONObject

/**
 * Guarda y lee, en SharedPreferences (como JSON), el resultado del análisis
 * real de audio (bpm + energía) de cada canción, indexado por su ruta física
 * de archivo — mismo patrón y misma clave que ArtworkCacheRepository (ver ese
 * archivo para más contexto de por qué se cachea así en todo el proyecto).
 *
 * Por qué existe: decodificar y analizar el audio (AudioFeatureExtractor) es
 * relativamente costoso en CPU. Se calcula UNA sola vez por canción y se
 * cachea acá para siempre; AudioAnalysisScheduler lee este caché antes de
 * decidir qué canciones todavía le faltan analizar.
 */
class AudioFeaturesCacheRepository(context: Context) {
    private val prefs = context.getSharedPreferences("audio_features_cache", Context.MODE_PRIVATE)
    private val KEY_MAP = "audio_features_map"

    private fun readAll(): MutableMap<String, AudioFeatures> {
        val json = prefs.getString(KEY_MAP, null) ?: return mutableMapOf()
        val result = mutableMapOf<String, AudioFeatures>()
        return try {
            val obj = JSONObject(json)
            obj.keys().forEach { path ->
                val entry = obj.optJSONObject(path) ?: return@forEach
                result[path] = AudioFeatures(
                    bpm = entry.optDouble("bpm", 0.0).toFloat(),
                    energy = entry.optDouble("energy", 0.0).toFloat(),
                    analyzedAt = entry.optLong("analyzedAt", 0L)
                )
            }
            result
        } catch (e: Exception) {
            mutableMapOf()
        }
    }

    private fun writeAll(map: Map<String, AudioFeatures>) {
        val obj = JSONObject()
        map.forEach { (path, features) ->
            val entry = JSONObject()
            entry.put("bpm", features.bpm.toDouble())
            entry.put("energy", features.energy.toDouble())
            entry.put("analyzedAt", features.analyzedAt)
            obj.put(path, entry)
        }
        try {
            prefs.edit().putString(KEY_MAP, obj.toString()).apply()
        } catch (e: Exception) {
            // Si no se pudo guardar, la próxima corrida del scheduler simplemente
            // vuelve a analizar esta canción. No rompe nada.
        }
    }

    /** Features guardados para este archivo, o null si nunca se analizó (o falló). */
    fun get(filePath: String): AudioFeatures? {
        if (filePath.isEmpty()) return null
        return readAll()[filePath]
    }

    /** Guarda el resultado del análisis para este archivo (sobreescribe si ya existía). */
    fun set(filePath: String, features: AudioFeatures) {
        if (filePath.isEmpty()) return
        val map = readAll()
        map[filePath] = features
        writeAll(map)
    }

    /** true si ya hay un análisis guardado para este archivo (exitoso o no). */
    fun has(filePath: String): Boolean = get(filePath) != null

    /** Borra la entrada de un archivo puntual (ej. si se reemplaza el archivo físico). */
    fun invalidate(filePath: String) {
        val map = readAll()
        if (map.remove(filePath) != null) writeAll(map)
    }
}
