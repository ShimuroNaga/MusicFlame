package com.music.musicflame.data

import java.text.Normalizer
import kotlin.math.abs

/**
 * Le da a la IA ojos sobre la biblioteca REAL del usuario.
 *
 * Problema que resuelve: un modelo de 1.5B parámetros no conoce las canciones del usuario, así que al
 * preguntarle por una canción "inventaba" otra. Ahora, ANTES de llamar al modelo, la app busca en la
 * biblioteca (sin IA, instantáneo) y:
 *   1) encuentra la canción/artista que el usuario mencionó,
 *   2) elige canciones parecidas por criterios objetivos (artista, género, álbum, año, duración, escuchas),
 *   3) entrega esas canciones como tarjetas (la UI las muestra al instante) y como datos al prompt,
 *      de modo que el modelo solo tiene que comentarlas con gracia en vez de adivinar.
 */
object AiLibraryContext {

    class LibraryHelp(
        /** Canciones que se muestran como tarjetas bajo la respuesta (máx. [MAX_PICKS]). */
        val picks: List<Song>,
        /** Bloque de texto compacto con datos reales para el prompt; null si no hay nada que aportar. */
        val promptBlock: String?,
        /** Texto de respaldo (sin IA) por si el modelo falla o no está listo. */
        val fallbackText: String?
    ) {
        companion object { val EMPTY = LibraryHelp(emptyList(), null, null) }
    }

    const val MAX_PICKS = 5

    // ---------- Índice normalizado (se reconstruye solo cuando cambia la lista de canciones) ----------
    private class Entry(
        val song: Song, val title: String, val artist: String, val album: String,
        val genre: String?, val titleTokens: Set<String>
    )

    @Volatile private var indexedFor: List<Song>? = null
    @Volatile private var index: List<Entry> = emptyList()

    private fun entries(songs: List<Song>): List<Entry> {
        if (indexedFor === songs) return index
        val built = songs.map { s ->
            val t = norm(s.title)
            Entry(s, t, norm(s.artist), norm(s.album), s.genre?.let { norm(it) }, tokens(t))
        }
        index = built; indexedFor = songs
        return built
    }

    // Regex compiladas UNA vez (compilarlas por canción hacía lenta la búsqueda con bibliotecas grandes).
    private val RE_MARKS = Regex("\\p{M}+")
    private val RE_NON_ALNUM = Regex("[^a-z0-9 ]+")
    private val RE_SPACES = Regex("\\s+")

    private fun norm(s: String): String =
        RE_SPACES.replace(RE_NON_ALNUM.replace(RE_MARKS.replace(Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD), ""), " "), " ").trim()

    private fun tokens(normalized: String): Set<String> =
        normalized.split(' ').filter { it.length >= 2 }.toSet()

    // Palabras vacías para no confundir "de", "la", "algo"... con parte de un título.
    private val STOP = setOf(
        "de", "la", "el", "los", "las", "un", "una", "y", "o", "en", "que", "me", "mi", "tu", "te", "se", "es", "lo",
        "por", "para", "con", "del", "al", "the", "a", "an", "of", "to", "in", "and", "my", "you", "is", "it",
        "cancion", "canciones", "musica", "algo", "parecido", "parecida", "similar", "como", "recomienda", "recomiendame"
    )

    // ---------- Intención ----------
    private val RECO_WORDS = listOf(
        "recomiend", "sugier", "sugiere", "parecid", "similar", "como esa", "como esta", "algo asi", "otra como",
        "mas como", "dame cancion", "ponme", "pon algo", "que escucho", "que escuchar", "playlist", "mezcla", "recommend", "similar to"
    )
    private val LIBRARY_WORDS = listOf("tengo", "mis canciones", "mi biblioteca", "mi musica", "canciones de", "musica de", "rolas de", "my songs", "my library")
    private val NOW_WORDS = listOf("esta cancion", "esta rola", "esta musica", "la que suena", "la que esta sonando", "lo que suena", "this song", "currently")

    /** Palabras de ánimo/actividad → fragmentos de género (en MediaStore los géneros vienen en texto libre). */
    private val MOODS: List<Pair<List<String>, List<String>>> = listOf(
        listOf("correr", "gym", "entrenar", "ejercicio", "workout", "energia", "animad", "fiesta", "bailar") to
            listOf("rock", "metal", "punk", "dance", "electro", "edm", "house", "techno", "reggaeton", "hip", "rap", "pop"),
        listOf("dormir", "relaj", "tranquil", "calma", "sleep", "chill", "suave") to
            listOf("ambient", "classical", "clasic", "acoustic", "acust", "jazz", "lofi", "lo fi", "chill", "folk", "new age"),
        listOf("estudiar", "concentr", "trabajar", "focus", "study") to
            listOf("ambient", "classical", "clasic", "instrumental", "lofi", "lo fi", "jazz", "electro", "soundtrack"),
        listOf("triste", "llorar", "melanc", "sad") to
            listOf("balada", "ballad", "blues", "acoustic", "acust", "soul", "indie", "folk", "pop")
    )

    /**
     * Analiza el mensaje y devuelve tarjetas + datos. Seguro de llamar en un hilo de fondo (no toca la UI).
     * [stats] = reproducciones por id de canción (StatsRepository.getAllStats()).
     */
    fun build(
        userText: String,
        songs: List<Song>,
        stats: Map<Long, SongStat>,
        nowPlayingId: Long?
    ): LibraryHelp {
        if (songs.isEmpty()) return LibraryHelp.EMPTY
        val text = norm(userText)
        if (text.isEmpty()) return LibraryHelp.EMPTY
        val ents = entries(songs)

        val wantsReco = RECO_WORDS.any { text.contains(it) }
        val mentionsNow = NOW_WORDS.any { text.contains(it) }

        // 1) ¿Mencionó una canción de su biblioteca?
        val ref: Song? = (if (mentionsNow && nowPlayingId != null) songs.firstOrNull { it.id == nowPlayingId } else null)
            ?: findMentionedSong(text, ents)

        // 2) ¿Mencionó un artista?
        val artistEntry = if (ref == null) findMentionedArtist(text, ents) else null

        // 3) ¿Pidió por ánimo/género?
        val moodFragments = MOODS.firstOrNull { (kw, _) -> kw.any { text.contains(it) } }?.second
        // Un género suelto ("¿qué es el jazz?") es pregunta de cultura general, no de biblioteca: solo cuenta si pide música.
        val libraryIntent = wantsReco || LIBRARY_WORDS.any { text.contains(it) }
        val directGenre = if (ref == null && artistEntry == null && libraryIntent) findMentionedGenre(text, ents) else null

        val picks: List<Song>
        val block = StringBuilder()
        val fallback: String

        when {
            ref != null -> {
                val found = similarTo(ref, ents, stats)
                picks = found.map { it.song }
                block.append("Canción de su biblioteca que mencionó: ").append(describe(ref, stats)).append('\n')
                if (picks.isEmpty()) {
                    block.append("No hay otras canciones parecidas en su biblioteca; dilo con honestidad.")
                    fallback = "Encontré «${ref.title}» de ${ref.artist} en tu biblioteca, pero no tengo otras canciones parecidas para sugerirte."
                } else {
                    block.append("Canciones PARECIDAS de su biblioteca (se muestran aparte como tarjetas):\n")
                    found.forEachIndexed { i, p -> block.append(i + 1).append(". ").append(describe(p.song, stats, p.reason)).append('\n') }
                    fallback = "Encontré «${ref.title}» de ${ref.artist}. Estas son las que más se le parecen en tu biblioteca:"
                }
            }
            artistEntry != null -> {
                val name = artistEntry.song.artist
                val theirs = ents.filter { it.artist == artistEntry.artist }.map { it.song }
                picks = theirs.sortedByDescending { stats[it.id]?.playCount ?: 0 }.take(MAX_PICKS)
                val total = theirs.size
                block.append("Artista de su biblioteca que mencionó: $name ($total canciones). Sus canciones, las más escuchadas primero:\n")
                picks.forEachIndexed { i, s -> block.append(i + 1).append(". ").append(describe(s, stats, playsReason(s, stats) ?: "aún sin escuchar")).append('\n') }
                fallback = "De $name tienes $total canciones. Estas son las que más escuchas:"
            }
            moodFragments != null || directGenre != null || wantsReco -> {
                val frags = moodFragments ?: directGenre?.let { listOf(it) }
                val pool = if (frags != null) ents.filter { e -> e.genre?.let { g -> frags.any { g.contains(it) } } == true }.map { it.song } else emptyList()
                val base = if (pool.isNotEmpty()) pool else songs
                picks = mixOfFavoritesAndDiscovery(base, stats)
                val label = when {
                    pool.isNotEmpty() -> "Canciones de su biblioteca que encajan con lo que pidió (por género)"
                    frags != null -> "No hay canciones con ese género en su biblioteca; estas son favoritas y descubrimientos de su biblioteca en general"
                    else -> "Selección de su biblioteca: favoritas y algunas poco escuchadas"
                }
                block.append(label).append(" (se muestran aparte como tarjetas):\n")
                picks.forEachIndexed { i, s ->
                    val why = listOfNotNull(s.genre?.takeIf { it.isNotBlank() && !it.equals("unknown", true) }?.let { "género $it" }, playsReason(s, stats) ?: "casi no la has escuchado")
                    block.append(i + 1).append(". ").append(describe(s, stats, why.joinToString(", "))).append('\n')
                }
                fallback = if (pool.isNotEmpty()) "Esto es lo que encontré en tu biblioteca para eso:" else "Te armé una mezcla con tus favoritas y algunas que casi no escuchas:"
            }
            else -> return LibraryHelp.EMPTY
        }
        return LibraryHelp(picks, block.toString().trim(), fallback)
    }

    // ---------- Búsquedas ----------
    private fun findMentionedSong(text: String, ents: List<Entry>): Song? {
        val textTokens = tokens(text)
        var best: Entry? = null
        var bestScore = 0.0
        for (e in ents) {
            if (e.title.length < 3) continue
            val score: Double = when {
                // El título completo aparece como frase dentro del mensaje (el caso más fiable).
                " ${e.title} " in " $text " && e.title.length >= 4 -> 10.0 + e.title.length
                else -> {
                    val sig = e.titleTokens.filter { it !in STOP && it.length >= 3 }
                    if (sig.size < 2) 0.0 else {
                        val hit = sig.count { it in textTokens }
                        if (hit.toDouble() / sig.size >= 0.8) 5.0 + hit else 0.0
                    }
                }
            }
            if (score > bestScore) { bestScore = score; best = e }
        }
        return best?.song
    }

    private fun findMentionedArtist(text: String, ents: List<Entry>): Entry? {
        var best: Entry? = null
        for (e in ents) {
            if (e.artist.length < 3 || e.artist == "unknown" || e.artist == "desconocido") continue
            if (" ${e.artist} " in " $text " && (best == null || e.artist.length > best.artist.length)) best = e
        }
        return best
    }

    private fun findMentionedGenre(text: String, ents: List<Entry>): String? {
        val genres = ents.mapNotNull { it.genre }.filter { it.length >= 3 }.distinct()
        return genres.filter { " $it " in " $text " }.maxByOrNull { it.length }
    }

    // ---------- Parecidas por criterios objetivos ----------
    private class Pick(val song: Song, val score: Double, val reason: String)

    private fun playsReason(song: Song, stats: Map<Long, SongStat>): String? {
        val n = stats[song.id]?.playCount ?: 0
        return if (n > 0) "la has escuchado $n ${if (n == 1) "vez" else "veces"}" else null
    }

    private fun similarTo(ref: Song, ents: List<Entry>, stats: Map<Long, SongStat>): List<Pick> {
        val refArtist = norm(ref.artist)
        val refAlbum = norm(ref.album)
        val refGenre = ref.genre?.let { norm(it) }
        val scored = ArrayList<Pick>(64)
        for (e in ents) {
            val s = e.song
            if (s.id == ref.id) continue
            var sc = 0.0
            val why = ArrayList<String>(4)
            if (refArtist.isNotEmpty() && e.artist == refArtist) { sc += 5.0; why += "mismo artista (${s.artist})" }
            if (refGenre != null && e.genre == refGenre) { sc += 3.0; why += "mismo género (${s.genre})" }
            if (refAlbum.isNotEmpty() && refAlbum != "desconocido" && e.album == refAlbum) { sc += 2.0; why += "mismo álbum" }
            val ry = ref.year; val sy = s.year
            if (ry != null && sy != null) {
                val d = abs(ry - sy)
                if (d <= 3) { sc += 2.0; why += "de la misma época ($sy)" } else if (d <= 10) { sc += 1.0; why += "de una época cercana ($sy)" }
            }
            if (ref.duration > 0 && s.duration > 0 && abs(ref.duration - s.duration) <= ref.duration / 5) sc += 0.5
            if (sc <= 0.0) continue
            playsReason(s, stats)?.let { why += it }
            sc += minOf(stats[s.id]?.playCount ?: 0, 10) * 0.05   // desempate: las que sí escucha
            scored.add(Pick(s, sc, why.joinToString(", ")))
        }
        return scored.sortedByDescending { it.score }.take(MAX_PICKS)
    }

    /** 3 favoritas + 2 poco escuchadas (si hay), sin repetir. */
    private fun mixOfFavoritesAndDiscovery(base: List<Song>, stats: Map<Long, SongStat>): List<Song> {
        val favs = base.filter { (stats[it.id]?.playCount ?: 0) > 0 }
            .sortedByDescending { stats[it.id]?.playCount ?: 0 }.take(3)
        val fresh = base.filter { (stats[it.id]?.playCount ?: 0) == 0 && it !in favs }
            .shuffled().take(MAX_PICKS - favs.size)
        val out = (favs + fresh).distinctBy { it.id }.toMutableList()
        if (out.size < MAX_PICKS) out += base.filter { it !in out }.shuffled().take(MAX_PICKS - out.size)
        return out.take(MAX_PICKS)
    }

    // ---------- Descripción corta con características ----------
    /** Rasgos que no dependen del idioma (género, año, duración). La UI les añade el conteo de escuchas. */
    fun baseTraits(song: Song): List<String> {
        val out = ArrayList<String>(3)
        song.genre?.takeIf { it.isNotBlank() && !it.equals("unknown", true) }?.let { out += it }
        song.year?.takeIf { it > 0 }?.let { out += it.toString() }
        if (song.duration > 0) { val m = song.duration / 60000; val s = (song.duration / 1000) % 60; out += "%d:%02d".format(m, s) }
        return out
    }

    private fun describe(song: Song, stats: Map<Long, SongStat>, reason: String? = null): String {
        val album = song.album.takeIf { it.isNotBlank() && !it.equals("Desconocido", true) }
        val plays = stats[song.id]?.playCount ?: 0
        val t = baseTraits(song) + (if (plays > 0) "$plays escuchas" else "sin escuchar")
        return buildString {
            append('«').append(song.title).append("» de ").append(song.artist)
            if (album != null) append(", álbum ").append(album)
            append(" (").append(t.joinToString(", ")).append(')')
            if (!reason.isNullOrBlank()) append(" — motivo: ").append(reason)
        }
    }
}
