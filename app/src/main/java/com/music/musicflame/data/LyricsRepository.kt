package com.music.musicflame.data

import android.content.Context
import kotlinx.coroutines.delay
import org.json.JSONObject

enum class LyricsSource { MANUAL, ONLINE }

data class StoredLyrics(
    val raw: String,
    val source: LyricsSource
)

/**
 * Guarda la letra (formato LRC si está sincronizada, o texto plano) por canción,
 * indexada por el ID de MediaStore, siguiendo el mismo patrón que
 * SongCustomizationRepository. También resuelve búsquedas online contra lrclib.net.
 */
class LyricsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("song_lyrics", Context.MODE_PRIVATE)
    private val KEY_MAP = "lyrics_map"
    // IDs de canciones que ya se buscaron online al menos una vez (se encontrara
    // letra o no), para que el escaneo de toda la biblioteca no repita trabajo
    // cada vez que se abre la app.
    private val KEY_CHECKED = "lyrics_checked_set"

    private fun readAll(): MutableMap<String, StoredLyrics> {
        val json = prefs.getString(KEY_MAP, null) ?: return mutableMapOf()
        val result = mutableMapOf<String, StoredLyrics>()
        return try {
            val obj = JSONObject(json)
            obj.keys().forEach { key ->
                val entry = obj.optJSONObject(key) ?: return@forEach
                val raw = entry.optString("raw", "")
                val source = try {
                    LyricsSource.valueOf(entry.optString("source", "MANUAL"))
                } catch (e: Exception) {
                    LyricsSource.MANUAL
                }
                if (raw.isNotBlank()) result[key] = StoredLyrics(raw, source)
            }
            result
        } catch (e: Exception) {
            mutableMapOf()
        }
    }

    private fun writeAll(map: Map<String, StoredLyrics>) {
        val obj = JSONObject()
        map.forEach { (id, stored) ->
            val entry = JSONObject()
            entry.put("raw", stored.raw)
            entry.put("source", stored.source.name)
            obj.put(id, entry)
        }
        prefs.edit().putString(KEY_MAP, obj.toString()).apply()
    }

    fun getLyrics(songId: Long): StoredLyrics? = readAll()[songId.toString()]

    fun hasLyrics(songId: Long): Boolean = getLyrics(songId) != null

    fun saveLyrics(songId: Long, raw: String, source: LyricsSource) {
        val map = readAll()
        map[songId.toString()] = StoredLyrics(raw.trim(), source)
        writeAll(map)
        markChecked(songId)
    }

    fun clearLyrics(songId: Long) {
        val map = readAll()
        map.remove(songId.toString())
        writeAll(map)
    }

    /** Marca una canción como "ya revisada" (se encontrara letra o no), para no repetir la búsqueda en el escaneo automático. */
    fun markChecked(songId: Long) {
        val set = prefs.getStringSet(KEY_CHECKED, emptySet())?.toMutableSet() ?: mutableSetOf()
        set.add(songId.toString())
        prefs.edit().putStringSet(KEY_CHECKED, set).apply()
    }

    /** Quita la marca de "revisada" para forzar que se vuelva a intentar (ej. al pulsar "Reintentar"). */
    fun clearChecked(songId: Long) {
        val set = prefs.getStringSet(KEY_CHECKED, emptySet())?.toMutableSet() ?: mutableSetOf()
        set.remove(songId.toString())
        prefs.edit().putStringSet(KEY_CHECKED, set).apply()
    }

    fun isChecked(songId: Long): Boolean =
        (prefs.getStringSet(KEY_CHECKED, emptySet()) ?: emptySet()).contains(songId.toString())

    companion object {
        // Valores típicos que trae MediaStore (o que pone la propia app) cuando
        // el mp3 no tiene el artista en sus etiquetas ID3. Mandar esto tal cual
        // a la API de letras rompe la búsqueda exacta, porque no hay artista
        // real con el que comparar.
        private val UNKNOWN_ARTIST_VALUES = setOf(
            "<unknown>", "unknown", "unknown artist", "artista desconocido",
            "desconocido", "varios artistas", "various artists", ""
        )

        fun isUnknownArtist(artist: String): Boolean =
            artist.trim().lowercase() in UNKNOWN_ARTIST_VALUES

        /**
         * Muchos archivos sin etiquetas traen el nombre del archivo como título,
         * y ese nombre a veces ya incluye "Artista - Título". Si detectamos ese
         * patrón lo separamos para ayudar a la búsqueda; si no, seguimos solo
         * con el nombre completo como título.
         */
        fun splitArtistFromTitle(title: String): Pair<String?, String> {
            val parts = title.split(Regex("\\s*-\\s*"), limit = 2)
            return if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                parts[0].trim() to parts[1].trim()
            } else {
                null to title
            }
        }
    }

    /**
     * Busca la letra online en lrclib.net. Intenta primero una coincidencia
     * exacta (título + artista + duración) y, si no hay resultado, cae a
     * búsqueda libre devolviendo la mejor coincidencia disponible.
     *
     * Si el artista que trae el mp3 es un placeholder de "desconocido" (o
     * viene vacío), la búsqueda se guía solo por el nombre de la canción —
     * intentando primero separar "Artista - Título" si el nombre lo trae así.
     *
     * Debe llamarse desde una corrutina (IO).
     */
    suspend fun searchOnline(title: String, artist: String, durationSeconds: Int?): LrcLibResult? {
        val (guessedArtist, effectiveTitle) =
            if (isUnknownArtist(artist)) splitArtistFromTitle(title) else artist to title
        val effectiveArtist = guessedArtist ?: ""

        return try {
            if (effectiveArtist.isNotBlank()) {
                val exact = LyricsApi.service.get(
                    trackName = effectiveTitle,
                    artistName = effectiveArtist,
                    durationSeconds = durationSeconds
                )
                val exactBody = exact.body()
                if (exact.isSuccessful && exactBody != null &&
                    (!exactBody.syncedLyrics.isNullOrBlank() || !exactBody.plainLyrics.isNullOrBlank())
                ) {
                    return exactBody
                }
            }

            // Búsqueda libre guiada por el nombre: si no hay artista real, se
            // manda null para que la API no descarte resultados por un artista
            // inventado ("Artista Desconocido", "<unknown>", etc).
            val search = LyricsApi.service.search(
                trackName = effectiveTitle,
                artistName = effectiveArtist.takeIf { it.isNotBlank() }
            )
            val candidates = search.body().orEmpty().filter {
                !it.syncedLyrics.isNullOrBlank() || !it.plainLyrics.isNullOrBlank()
            }
            // Preferimos la coincidencia cuyo título es igual al nuestro; si no
            // hay ninguna así, nos quedamos con la primera con letra disponible.
            candidates.firstOrNull { it.trackName?.trim()?.equals(effectiveTitle.trim(), ignoreCase = true) == true }
                ?: candidates.firstOrNull()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Recorre toda la biblioteca en segundo plano (sin bloquear la UI) y busca
     * automáticamente qué canciones tienen letra disponible, guiándose por el
     * nombre aunque el mp3 no tenga el artista en sus etiquetas. Las letras que
     * encuentra quedan guardadas de una vez, así que al abrir cualquier canción
     * después aparecen al instante. No repite canciones ya revisadas antes.
     */
    suspend fun scanLibrary(
        songs: List<Song>,
        onFound: (Song) -> Unit = {},
        onProgress: (checked: Int, total: Int) -> Unit = { _, _ -> }
    ) {
        val pending = songs.filter { !isChecked(it.id) }
        pending.forEachIndexed { index, song ->
            if (!hasLyrics(song.id)) {
                val result = searchOnline(
                    title = song.title,
                    artist = song.artist,
                    durationSeconds = (song.duration / 1000).toInt().takeIf { it > 0 }
                )
                val raw = result?.syncedLyrics?.takeIf { it.isNotBlank() } ?: result?.plainLyrics
                if (raw != null) {
                    saveLyrics(song.id, raw, LyricsSource.ONLINE)
                    onFound(song)
                }
            }
            markChecked(song.id)
            onProgress(index + 1, pending.size)
            // Pequeña pausa entre canción y canción para no saturar la API gratuita de letras.
            delay(300)
        }
    }
}
