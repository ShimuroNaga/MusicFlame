package com.music.musicflame.data

import android.content.Context
import org.json.JSONObject

/**
 * Qué fuente de carátula terminó funcionando (o si ninguna funcionó) la ÚLTIMA
 * vez que se intentó resolver la carátula "de fábrica" (no personalizada) de
 * una canción:
 * - EMBEDDED: la carátula embebida en el propio archivo de audio (tag ID3/APIC).
 * - ALBUM_URI: la Uri genérica de MediaStore por álbum (content://.../albumart/{id}).
 * - NONE: ni una ni la otra tienen carátula real; se debe mostrar el ícono
 *   genérico directamente, sin intentar ninguna carga.
 */
enum class ArtworkSource { EMBEDDED, ALBUM_URI, NONE }

/**
 * Guarda y lee, en SharedPreferences (como JSON), qué fuente de carátula
 * "de fábrica" funcionó por última vez para cada canción (indexada por su
 * ruta física de archivo, ya que es lo único que AlbumArt() siempre recibe).
 *
 * Por qué existe: antes, CADA VEZ que se mostraba la lista de canciones,
 * AlbumArt() repetía desde cero el mismo intento fallido (leer la carátula
 * embebida vía MediaMetadataRetriever, fallar, caer a la Uri de MediaStore,
 * fallar también) para canciones que sabemos de sobra que no tienen carátula
 * en ningún lado — desperdiciando tiempo e IO en cada apertura de la app y
 * haciendo perceptible el "parpadeo" antes de caer al ícono genérico.
 *
 * Ahora, una vez que se resuelve el resultado de una canción (con éxito o
 * agotando los dos intentos), se guarda acá. La próxima vez, AlbumArt() lee
 * este cache primero:
 * - Si dice NONE, muestra el ícono genérico de inmediato, sin tocar disco.
 * - Si dice EMBEDDED o ALBUM_URI, va directo a esa fuente, sin repetir el
 *   intento que ya sabemos que falla antes.
 *
 * Si el archivo cambia después (ej. se le agrega una carátula por fuera de la
 * app), el peor caso es que el cache quede desactualizado hasta que el
 * usuario lo fuerce a re-chequear (ver invalidate()); no rompe nada, solo
 * puede tardar en reflejar el cambio.
 */
class ArtworkCacheRepository(context: Context) {
    private val prefs = context.getSharedPreferences("artwork_cache", Context.MODE_PRIVATE)
    private val KEY_MAP = "artwork_source_map"

    private fun readAll(): MutableMap<String, String> {
        val json = prefs.getString(KEY_MAP, null) ?: return mutableMapOf()
        val result = mutableMapOf<String, String>()
        return try {
            val obj = JSONObject(json)
            obj.keys().forEach { key ->
                result[key] = obj.optString(key)
            }
            result
        } catch (e: Exception) {
            mutableMapOf()
        }
    }

    private fun writeAll(map: Map<String, String>) {
        val obj = JSONObject()
        map.forEach { (path, source) -> obj.put(path, source) }
        try {
            prefs.edit().putString(KEY_MAP, obj.toString()).apply()
        } catch (e: Exception) {
            // No pasa nada si por alguna razón no se pudo guardar: la próxima
            // vez simplemente se vuelve a intentar resolver desde cero.
        }
    }

    /** Devuelve la fuente que funcionó la última vez para este archivo, o null si nunca se resolvió. */
    fun get(filePath: String): ArtworkSource? {
        if (filePath.isEmpty()) return null
        val raw = readAll()[filePath] ?: return null
        return try {
            ArtworkSource.valueOf(raw)
        } catch (e: Exception) {
            null
        }
    }

    /** Guarda cuál fuente funcionó (o NONE si ninguna) para este archivo. */
    fun set(filePath: String, source: ArtworkSource) {
        if (filePath.isEmpty()) return
        val map = readAll()
        if (map[filePath] == source.name) return // ya estaba guardado igual, no reescribas por gusto
        map[filePath] = source.name
        writeAll(map)
    }

    /** Borra la entrada de un archivo puntual (ej. si el usuario le agregó/quitó una carátula por fuera de la app). */
    fun invalidate(filePath: String) {
        val map = readAll()
        if (map.remove(filePath) != null) writeAll(map)
    }
}
