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
 * SongCustomizationRepository. También resuelve búsquedas online contra lrclib.net,
 * y si ahí no aparece nada, prueba automáticamente en otras plataformas (lyrics.ovh,
 * que agrega Genius, AZLyrics, Paroles.net, LyricsMania, Letras.mus.br y Lyrics.com).
 *
 * Regla clave: TODAS las búsquedas están guiadas obligatoriamente por el TÍTULO de
 * la canción, tenga o no tenga artista. Si hay artista se usa primero para afinar
 * la búsqueda (más precisión), pero nunca es un requisito: en cuanto una búsqueda
 * "con artista" no da resultado, se reintenta guiándose solo por el título.
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

    /**
     * Igual que llamar [hasLyrics] por cada id de [songIds], pero parseando el JSON
     * guardado en SharedPreferences UNA sola vez en vez de una vez por canción.
     * Usar esto (y no un .filter { hasLyrics(it.id) }) para listas completas de la
     * librería: con librerías grandes, hasLyrics() por canción vuelve a leer y
     * parsear el mismo JSON cientos de veces y bloquea el hilo principal.
     */
    fun availableLyricsIds(songIds: Collection<Long>): Set<Long> {
        val all = readAll()
        return songIds.filter { all.containsKey(it.toString()) }.toSet()
    }

    fun saveLyrics(songId: Long, raw: String, source: LyricsSource) {
        val map = readAll()
        map[songId.toString()] = StoredLyrics(raw.trim(), source)
        writeAll(map)
        markChecked(songId)
    }

    /**
     * Borra DEFINITIVAMENTE la letra guardada de una sola canción (y solo esa),
     * pensado para cuando el usuario se equivocó de letra. También limpia la
     * marca de "revisada" para que, si se vuelve a abrir esa canción, la app
     * pueda intentar buscarla de nuevo desde cero en lugar de recordar el fallo.
     */
    fun clearLyrics(songId: Long) {
        val map = readAll()
        map.remove(songId.toString())
        writeAll(map)
        clearChecked(songId)
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

        /**
         * Limpia texto que suele venir pegado en títulos de video de YouTube
         * ("(Official Video)", "[Lyrics]", "HD", etc.) para que la búsqueda de
         * letra no falle por ruido que no es parte del título real.
         */
        fun cleanNoisyTitle(raw: String): String {
            var t = raw
            t = t.replace(Regex("\\(([^()]*)\\)"), " ")
            t = t.replace(Regex("\\[([^\\[\\]]*)]"), " ")
            t = t.replace(
                Regex(
                    "(?i)\\b(official\\s*(music\\s*)?video|official\\s*audio|lyric\\s*video|lyrics|letra|video\\s*oficial|audio\\s*oficial|hd|4k|remaster(ed)?)\\b"
                ),
                " "
            )
            t = t.replace(Regex("\\s+"), " ").trim()
            return t.ifBlank { raw.trim() }
        }
    }

    /** true si trae texto real de letra (sincronizada o plana). */
    private fun LrcLibResult.hasUsableLyrics(): Boolean =
        !syncedLyrics.isNullOrBlank() || !plainLyrics.isNullOrBlank()

    /**
     * Cascada de búsqueda en lrclib.net, guiada SIEMPRE por el título, con o sin
     * artista disponible:
     *  1) Si hay artista, intenta la coincidencia exacta (título + artista + duración).
     *  2) Búsqueda libre con título + artista (si hay).
     *  3) Búsqueda libre SOLO con título (sin artista), por si el artista guardado
     *     en el mp3 está mal y estaba tapando resultados válidos.
     * En cualquiera de las búsquedas libres, se prioriza el resultado cuyo título
     * coincide con el nuestro, sin importar si el artista coincide o no.
     */
    private suspend fun searchLrcLib(title: String, artist: String, durationSeconds: Int?): LrcLibResult? {
        if (artist.isNotBlank()) {
            try {
                val exact = LyricsApi.service.get(
                    trackName = title,
                    artistName = artist,
                    durationSeconds = durationSeconds
                )
                val body = exact.body()
                if (exact.isSuccessful && body != null && body.hasUsableLyrics()) return body
            } catch (e: Exception) { /* seguimos con el resto de intentos */ }
        }

        suspend fun freeSearch(withArtist: Boolean): LrcLibResult? {
            return try {
                val search = LyricsApi.service.search(
                    trackName = title,
                    artistName = if (withArtist) artist.takeIf { it.isNotBlank() } else null
                )
                val candidates = search.body().orEmpty().filter { it.hasUsableLyrics() }
                candidates.firstOrNull {
                    it.trackName?.trim()?.equals(title.trim(), ignoreCase = true) == true
                } ?: candidates.firstOrNull()
            } catch (e: Exception) {
                null
            }
        }

        // Con artista (si lo hay) primero, guiado por título.
        if (artist.isNotBlank()) {
            freeSearch(withArtist = true)?.let { return it }
        }
        // Guiado SOLO por título: obligatorio pase lo que pase con el artista.
        return freeSearch(withArtist = false)
    }

    /**
     * Respaldo en "otras plataformas" (lyrics.ovh, que agrega varias fuentes)
     * cuando lrclib.net no encontró nada. Se guía por título igual que arriba.
     * Devuelve solo texto plano (esta fuente no trae letras sincronizadas).
     */
    private suspend fun searchOtherPlatforms(title: String, artist: String): LrcLibResult? {
        // 1) Si hay artista, intento directo.
        if (artist.isNotBlank()) {
            try {
                val res = LyricsOvhApi.service.getLyrics(artist = artist, title = title)
                val lyrics = res.body()?.lyrics
                if (res.isSuccessful && !lyrics.isNullOrBlank()) {
                    return LrcLibResult(trackName = title, artistName = artist, plainLyrics = lyrics)
                }
            } catch (e: Exception) { /* continúa */ }
        }

        // 2) Guiado SOLO por título: busca candidatos en Deezer por texto libre
        // (esto no depende para nada de si el artista guardado es correcto) y
        // prueba con el/los mejores candidatos que traigan letra.
        return try {
            val suggestResp = LyricsOvhApi.service.suggest(title)
            val candidates = suggestResp.body()?.data.orEmpty()
                .mapNotNull { track ->
                    val candTitle = track.title ?: track.title_short
                    val candArtist = track.artist?.name
                    if (candTitle.isNullOrBlank() || candArtist.isNullOrBlank()) null
                    else candTitle to candArtist
                }
                .distinct()
                .let { list ->
                    // Prioriza candidatos cuyo título coincide con el nuestro.
                    val exact = list.filter { it.first.trim().equals(title.trim(), ignoreCase = true) }
                    (exact + list).distinct()
                }
                .take(3) // no saturar la API gratuita probando decenas de candidatos

            for ((candTitle, candArtist) in candidates) {
                try {
                    val res = LyricsOvhApi.service.getLyrics(artist = candArtist, title = candTitle)
                    val lyrics = res.body()?.lyrics
                    if (res.isSuccessful && !lyrics.isNullOrBlank()) {
                        return LrcLibResult(trackName = candTitle, artistName = candArtist, plainLyrics = lyrics)
                    }
                } catch (e: Exception) { /* prueba el siguiente candidato */ }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Busca la letra online. Primero lrclib.net (que sí trae letras sincronizadas)
     * y, solo si ahí no aparece nada, prueba automáticamente en otras plataformas
     * antes de rendirse. Guiada siempre por título, tenga o no tenga artista real.
     *
     * Debe llamarse desde una corrutina (IO).
     */
    suspend fun searchOnline(title: String, artist: String, durationSeconds: Int?): LrcLibResult? {
        val (guessedArtist, effectiveTitle) =
            if (isUnknownArtist(artist)) splitArtistFromTitle(title) else artist to title
        val effectiveArtist = guessedArtist ?: ""

        searchLrcLib(effectiveTitle, effectiveArtist, durationSeconds)?.let { return it }
        return searchOtherPlatforms(effectiveTitle, effectiveArtist)
    }

    /**
     * Igual que [searchOnline] pero partiendo de un texto libre (por ejemplo, el
     * título de un video de YouTube que el usuario acaba de confirmar como
     * correcto). Se limpia el ruido típico de títulos de video, se intenta
     * separar "Artista - Título" si aplica, y se guía la búsqueda por ese texto,
     * exactamente con la misma cascada (lrclib -> otras plataformas).
     */
    suspend fun searchByFreeText(rawText: String): LrcLibResult? {
        val cleaned = cleanNoisyTitle(rawText)
        val (guessedArtist, effectiveTitle) = splitArtistFromTitle(cleaned)
        val effectiveArtist = guessedArtist ?: ""

        searchLrcLib(effectiveTitle, effectiveArtist, durationSeconds = null)?.let { return it }
        searchOtherPlatforms(effectiveTitle, effectiveArtist)?.let { return it }

        // Último intento: si no se pudo separar artista, prueba el texto completo
        // tal cual como título (por si el título real no tenía guion separador).
        if (guessedArtist == null && cleaned != effectiveTitle) {
            searchLrcLib(cleaned, "", durationSeconds = null)?.let { return it }
            searchOtherPlatforms(cleaned, "")?.let { return it }
        }
        return null
    }

    /**
     * Recorre toda la biblioteca en segundo plano (sin bloquear la UI) y busca
     * automáticamente qué canciones tienen letra disponible, guiándose por el
     * nombre aunque el mp3 no tenga el artista en sus etiquetas, y probando en
     * otras plataformas si lrclib no la tiene. Las letras que encuentra quedan
     * guardadas de una vez, así que al abrir cualquier canción después aparecen
     * al instante. No repite canciones ya revisadas antes.
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
            // Pequeña pausa entre canción y canción para no saturar las APIs gratuitas de letras.
            delay(300)
        }
    }
}