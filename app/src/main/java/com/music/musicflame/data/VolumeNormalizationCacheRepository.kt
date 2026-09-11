package com.music.musicflame.data

import android.content.Context
import org.json.JSONObject

/**
 * EQ PRO — ETAPA 2: cachea, por canción (indexada por su ruta física de archivo, mismo patrón
 * que ArtworkCacheRepository), el gain de normalización de volumen (en dB) que calculó
 * VolumeNormalizationAnalyzer la última vez.
 *
 * Por qué existe: analizar un archivo completo (decodificarlo con MediaCodec y calcular RMS)
 * no es gratis — sin este caché, cada vez que una canción vuelve a sonar (aunque sea la misma
 * sesión, aunque sea en shuffle) habría que re-decodificarla entera solo para el análisis de
 * volumen. Con el caché, una vez calculado, el gain de esa canción queda fijo hasta que se
 * invalide a mano (ver invalidate()) — no cambia aunque el archivo se re-analice, salvo que se
 * borre la entrada primero.
 *
 * Mismo patrón que ArtworkCacheRepository: SharedPreferences con un solo JSON adentro, más un
 * mapa en memoria (companion object) para no re-parsear ese JSON en cada lectura.
 */
class VolumeNormalizationCacheRepository(context: Context) {
    private val prefs = context.getSharedPreferences("volume_normalization_cache", Context.MODE_PRIVATE)
    private val KEY_MAP = "gain_db_map"

    companion object {
        @Volatile private var cache: MutableMap<String, Float>? = null
        private val lock = Any()
    }

    private fun readAll(): MutableMap<String, Float> {
        cache?.let { return it }
        synchronized(lock) {
            cache?.let { return it }
            val json = prefs.getString(KEY_MAP, null)
            val result = mutableMapOf<String, Float>()
            if (json != null) {
                try {
                    val obj = JSONObject(json)
                    obj.keys().forEach { key -> result[key] = obj.optDouble(key, 0.0).toFloat() }
                } catch (e: Exception) {
                    // JSON guardado corrupto: se arranca en blanco, peor caso se re-analizan
                    // todas las canciones (no rompe nada, solo tarda un poco la próxima vez
                    // que suene cada una).
                }
            }
            cache = result
            return result
        }
    }

    private fun writeAll(map: Map<String, Float>) {
        cache = map.toMutableMap()
        val obj = JSONObject()
        map.forEach { (path, gainDb) -> obj.put(path, gainDb.toDouble()) }
        try {
            prefs.edit().putString(KEY_MAP, obj.toString()).apply()
        } catch (e: Exception) {
            // No pasa nada si no se pudo guardar: se vuelve a analizar la próxima vez que
            // suene esta canción.
        }
    }

    /** Devuelve el gain (en dB) calculado la última vez para este archivo, o null si nunca se analizó. */
    fun get(filePath: String): Float? {
        if (filePath.isEmpty()) return null
        return readAll()[filePath]
    }

    /** Guarda el gain (en dB) calculado para este archivo. */
    fun set(filePath: String, gainDb: Float) {
        if (filePath.isEmpty()) return
        val map = readAll()
        if (map[filePath] == gainDb) return // ya estaba guardado igual, no reescribas por gusto
        map[filePath] = gainDb
        writeAll(map)
    }

    /** Borra la entrada de un archivo puntual, para forzar que se re-analice la próxima vez que suene. */
    fun invalidate(filePath: String) {
        val map = readAll()
        if (map.remove(filePath) != null) writeAll(map)
    }
}
