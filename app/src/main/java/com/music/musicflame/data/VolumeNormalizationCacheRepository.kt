package com.music.musicflame.data

import android.content.Context
import android.content.SharedPreferences
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

    init {
        prefsRef = prefs
    }

    private companion object {
        // v2: los valores viejos ("gain_db_map") no tenían protección anticlipping; se ignoran y se
        // re-analizan solos la próxima vez que suene cada canción.
        const val KEY_MAP = "gain_db_map_v2"

        @Volatile private var prefsRef: SharedPreferences? = null
        @Volatile private var cache: MutableMap<String, Float>? = null
        private val lock = Any()

        // ANTES: cada set() reserializaba y guardaba TODO el JSON (cientos de KB con miles de
        // canciones) cada vez que sonaba una canción nueva. AHORA: la memoria se actualiza al
        // instante y a disco se guarda una sola vez cuando pasan 2 s sin cambios.
        private val writer = DebouncedTask(2000L) { persistNow() }

        private fun persistNow() {
            val p = prefsRef ?: return
            val snapshot = synchronized(lock) { cache?.toMap() } ?: return
            val obj = JSONObject()
            snapshot.forEach { (path, gainDb) -> obj.put(path, gainDb.toDouble()) }
            try {
                p.edit().putString(KEY_MAP, obj.toString()).apply()
            } catch (e: Exception) {
                // No pasa nada si no se pudo guardar: se vuelve a analizar la próxima vez que
                // suene esta canción.
            }
        }
    }

    // Debe llamarse con [lock] tomado.
    private fun memory(): MutableMap<String, Float> {
        cache?.let { return it }
        val result = HashMap<String, Float>()
        val json = prefs.getString(KEY_MAP, null)
        if (json != null) {
            try {
                val obj = JSONObject(json)
                obj.keys().forEach { key -> result[key] = obj.optDouble(key, 0.0).toFloat() }
            } catch (e: Exception) {
                // JSON guardado corrupto: se arranca en blanco, peor caso se re-analizan
                // todas las canciones (no rompe nada, solo tarda un poco la próxima vez
                // que suene cada una).
                result.clear()
            }
        }
        cache = result
        return result
    }

    /** Devuelve el gain (en dB) calculado la última vez para este archivo, o null si nunca se analizó. */
    fun get(filePath: String): Float? {
        if (filePath.isEmpty()) return null
        return synchronized(lock) { memory()[filePath] }
    }

    /** Guarda el gain (en dB) calculado para este archivo. */
    fun set(filePath: String, gainDb: Float) {
        if (filePath.isEmpty()) return
        val changed = synchronized(lock) {
            val map = memory()
            if (map[filePath] == gainDb) {
                false // ya estaba guardado igual, no reescribas por gusto
            } else {
                map[filePath] = gainDb
                true
            }
        }
        if (changed) writer.request()
    }

    /** Borra la entrada de un archivo puntual, para forzar que se re-analice la próxima vez que suene. */
    fun invalidate(filePath: String) {
        val changed = synchronized(lock) { memory().remove(filePath) != null }
        if (changed) writer.request()
    }
}
