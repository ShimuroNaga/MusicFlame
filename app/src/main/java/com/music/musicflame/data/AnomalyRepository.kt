package com.music.musicflame.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Tipo de problema que "Búsqueda de anomalías" (Ajustes > Canciones) puede
 * detectar para una canción. El `name` de cada entrada es lo que se persiste
 * tal cual en el JSON guardado (ver AnomalyRepository), así que no se debe
 * renombrar un valor existente sin migrar los datos ya guardados en
 * SharedPreferences de usuarios que ya hayan corrido un análisis.
 */
enum class AnomalyType(val label: String) {
    CORRUPT_ARTWORK("Carátula corrupta o ilegible"),
    SUSPICIOUS_METADATA("Metadata vacía o sospechosa"),
    UNSUPPORTED_FORMAT("Formato no soportado por el tagger"),
    ZERO_OR_TRUNCATED_DURATION("Duración cero o archivo truncado"),
    POSSIBLE_DUPLICATE("Posible duplicado")
}

/**
 * Resultado guardado de la última vez que se analizó una canción puntual:
 * qué problemas se encontraron (`types`), cuáles decidió ignorar el usuario
 * (`ignoredTypes`), y la "huella" del archivo físico (tamaño + fecha de
 * modificación) en el momento del análisis — es lo que AnomalyScanner usa
 * para decidir si hace falta volver a tocar el disco la próxima vez.
 */
data class AnomalyRecord(
    val songId: Long,
    val path: String,
    val title: String,
    val artist: String,
    val types: Set<AnomalyType>,
    val ignoredTypes: Set<AnomalyType> = emptySet(),
    val fileSize: Long,
    val fileLastModified: Long,
    val lastScannedAt: Long
) {
    /** Problemas realmente visibles en la UI: los detectados menos los que el usuario ya ignoró. */
    val visibleTypes: Set<AnomalyType> get() = types - ignoredTypes
}

/**
 * Guarda y lee, en SharedPreferences (como JSON), el resultado del último
 * análisis de "Búsqueda de anomalías", indexado por la ruta física de cada
 * canción — mismo patrón que ArtworkCacheRepository/SongCustomizationRepository.
 *
 * Por qué existe: escanear TODA la biblioteca (decodificar carátulas
 * embebidas, leer tags con jaudiotagger, etc.) es trabajo de IO pesado.
 * Guardando acá el tamaño+fecha de modificación con el que se analizó cada
 * archivo (además del título/artista efectivos que se usaron), la próxima
 * vez que el usuario presione "Buscar anomalías" AnomalyScanner puede
 * saltarse por completo cualquier canción que no haya cambiado desde el
 * último análisis y reutilizar directamente el resultado guardado, en vez
 * de re-escanear toda la biblioteca desde cero cada vez.
 */
class AnomalyRepository(context: Context) {
    private val prefs = context.getSharedPreferences("anomaly_scan_results", Context.MODE_PRIVATE)
    private val KEY_MAP = "records_map"

    /** Todos los resultados guardados, indexados por ruta física del archivo. */
    fun getAll(): Map<String, AnomalyRecord> {
        val json = prefs.getString(KEY_MAP, null) ?: return emptyMap()
        return try {
            val obj = JSONObject(json)
            val result = mutableMapOf<String, AnomalyRecord>()
            obj.keys().forEach { path ->
                val entry = obj.optJSONObject(path) ?: return@forEach
                entry.toRecord(path)?.let { result[path] = it }
            }
            result
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun get(path: String): AnomalyRecord? = getAll()[path]

    /**
     * Guarda/actualiza el resultado de un análisis reciente para varias
     * canciones a la vez (una sola escritura a SharedPreferences en vez de
     * una por canción, igual que ArtworkCacheRepository.writeAll).
     */
    fun saveAll(records: List<AnomalyRecord>) {
        if (records.isEmpty()) return
        val current = getAll().toMutableMap()
        records.forEach { record -> current[record.path] = record }
        writeAll(current)
    }

    /** Marca un tipo de problema puntual como ignorado para una canción (no borra el registro, solo lo oculta). */
    fun ignore(path: String, type: AnomalyType) {
        val current = getAll().toMutableMap()
        val existing = current[path] ?: return
        if (type in existing.ignoredTypes) return // ya estaba ignorado, no reescribas por gusto
        current[path] = existing.copy(ignoredTypes = existing.ignoredTypes + type)
        writeAll(current)
    }

    /**
     * Quita del cache las canciones que ya no existen en la biblioteca actual
     * (se borraron o se movieron), para que los resultados guardados no
     * sigan arrastrando para siempre entradas de archivos fantasma.
     */
    fun pruneMissing(currentPaths: Set<String>) {
        val current = getAll().toMutableMap()
        val before = current.size
        current.keys.retainAll(currentPaths)
        if (current.size != before) writeAll(current)
    }

    private fun writeAll(map: Map<String, AnomalyRecord>) {
        val obj = JSONObject()
        map.forEach { (path, record) -> obj.put(path, record.toJson()) }
        try {
            prefs.edit().putString(KEY_MAP, obj.toString()).apply()
        } catch (e: Exception) {
            // Si por alguna razón no se pudo guardar, la próxima vez simplemente
            // se vuelve a analizar desde cero; no rompe nada más.
        }
    }

    private fun AnomalyRecord.toJson(): JSONObject {
        val obj = JSONObject()
        obj.put("songId", songId)
        obj.put("title", title)
        obj.put("artist", artist)
        obj.put("types", JSONArray(types.map { it.name }))
        obj.put("ignoredTypes", JSONArray(ignoredTypes.map { it.name }))
        obj.put("fileSize", fileSize)
        obj.put("fileLastModified", fileLastModified)
        obj.put("lastScannedAt", lastScannedAt)
        return obj
    }

    private fun JSONObject.toRecord(path: String): AnomalyRecord? {
        return try {
            AnomalyRecord(
                songId = optLong("songId"),
                path = path,
                title = optString("title"),
                artist = optString("artist"),
                types = optJSONArray("types").toTypeSet(),
                ignoredTypes = optJSONArray("ignoredTypes").toTypeSet(),
                fileSize = optLong("fileSize"),
                fileLastModified = optLong("fileLastModified"),
                lastScannedAt = optLong("lastScannedAt")
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun JSONArray?.toTypeSet(): Set<AnomalyType> {
        if (this == null) return emptySet()
        val result = mutableSetOf<AnomalyType>()
        for (i in 0 until length()) {
            try {
                result.add(AnomalyType.valueOf(getString(i)))
            } catch (e: Exception) {
                // Tipo desconocido (ej. guardado por una versión futura de la app); se ignora sin romper el resto.
            }
        }
        return result
    }
}
