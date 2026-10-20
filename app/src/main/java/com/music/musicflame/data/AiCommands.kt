package com.music.musicflame.data

import com.music.musicflame.R
import org.json.JSONObject
import java.text.Normalizer

/**
 * Comandos del chat de IA (se escriben con "/"). Solo funcionan con la IA online (Groq): piden respuestas
 * más largas y estructuradas de lo que un modelo local pequeño hace bien.
 *
 * Regla de oro: los DATOS de las canciones (título, artista, álbum, género, año, duración, escuchas) los pone
 * la app desde la biblioteca real; el modelo solo los comenta. Nunca "extrae" títulos y nunca oye el audio.
 */
enum class AiCommand(
    val es: String,
    val en: String,
    val aliases: List<String>,
    val descRes: Int,
    val argHintRes: Int?,
    /** Sin argumentos abre el selector de canciones (hasta [AiCommands.MAX_SONGS]). */
    val usesPicker: Boolean = false,
    /** Se ejecuta de inmediato al tocarlo en el menú (no necesita argumentos). */
    val runsAlone: Boolean = false
) {
    ANALYZE("analiza", "analyze", listOf("analize", "analizar", "analyse"), R.string.ai_cmd_analyze_desc, R.string.ai_cmd_arg_songs, usesPicker = true),
    TELL("tell", "tell", listOf("cuenta", "dato", "curiosidad"), R.string.ai_cmd_tell_desc, null, runsAlone = true),
    OPINION("opinion", "opinion", listOf("opina", "rate"), R.string.ai_cmd_opinion_desc, R.string.ai_cmd_arg_songs, usesPicker = true),
    PLAYLIST("playlist", "playlist", listOf("lista"), R.string.ai_cmd_playlist_desc, R.string.ai_cmd_arg_theme),
    WRAPPED("wrapped", "wrapped", listOf("resumen"), R.string.ai_cmd_wrapped_desc, null, runsAlone = true),
    ROAST("roast", "roast", listOf("burla"), R.string.ai_cmd_roast_desc, null, runsAlone = true),
    QUIZ("quiz", "quiz", listOf("trivia"), R.string.ai_cmd_quiz_desc, null, runsAlone = true),
    DUEL("duelo", "duel", listOf("versus"), R.string.ai_cmd_duel_desc, R.string.ai_cmd_arg_ab);

    /** Nombre visible según el idioma de la app. */
    fun label(lang: String): String = if (lang == "en") en else es
}

object AiCommands {
    const val MAX_SONGS = 10
    private const val PLAYLIST_CANDIDATES = 120

    class Parsed(val cmd: AiCommand, val args: String)

    /** Petición lista para [OnlineLlm.command]. */
    class Request(val system: String, val user: String, val maxTokens: Int, val temperature: Double = 0.6)

    private val RE_MARKS = Regex("\\p{M}+")
    private fun fold(s: String): String =
        RE_MARKS.replace(Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD), "").filter { it.isLetterOrDigit() }

    /** "/analiza a; b" → (ANALYZE, "a; b"). null si no empieza con "/" o el comando no existe. */
    fun parse(text: String): Parsed? {
        val t = text.trim()
        if (!t.startsWith("/")) return null
        val body = t.drop(1)
        val word = body.takeWhile { !it.isWhitespace() }
        val key = fold(word)
        if (key.isEmpty()) return null
        val cmd = AiCommand.values().firstOrNull { c ->
            key == fold(c.es) || key == fold(c.en) || c.aliases.any { key == fold(it) }
        } ?: return null
        return Parsed(cmd, body.drop(word.length).trim())
    }

    fun labels(lang: String): String = AiCommand.values().joinToString(", ") { "/" + it.label(lang) }

    // ---------- Datos reales de la biblioteca ----------
    private fun known(v: String?) =
        !v.isNullOrBlank() && !v.equals("unknown", true) && !v.equals("desconocido", true) && !v.equals("<unknown>", true)

    fun songLine(s: Song, stats: Map<Long, SongStat>, lang: String): String {
        val en = lang == "en"
        val parts = ArrayList<String>(6)
        parts += "«${s.title}» — ${s.artist}"
        if (known(s.album)) parts += (if (en) "album: " else "álbum: ") + s.album
        if (known(s.genre)) parts += (if (en) "genre: " else "género: ") + s.genre
        s.year?.takeIf { it > 0 }?.let { parts += (if (en) "year: " else "año: ") + it }
        if (s.duration > 0) parts += "%d:%02d".format(s.duration / 60000, (s.duration / 1000) % 60)
        val plays = stats[s.id]?.playCount ?: 0
        parts += if (plays > 0) (if (en) "$plays plays" else "$plays escuchas") else (if (en) "never played" else "sin escuchar")
        return parts.joinToString(" | ")
    }

    class Resolved(val songs: List<Song>, val notFound: List<String>, val truncated: Boolean)

    /** Convierte "tema a; tema b, tema c" en canciones REALES de la biblioteca (máx. [MAX_SONGS]). */
    fun resolveSongs(args: String, library: List<Song>): Resolved {
        val pieces = args.split('\n', ';', '|', ',').map { it.trim().trim('"', '«', '»') }.filter { it.isNotEmpty() }
        val found = LinkedHashMap<Long, Song>()
        val missing = ArrayList<String>()
        for (p in pieces) {
            val s = AiLibraryContext.findSong(p, library)
            if (s == null) missing += p else found.putIfAbsent(s.id, s)
        }
        val all = found.values.toList()
        return Resolved(all.take(MAX_SONGS), missing, all.size > MAX_SONGS)
    }

    // ---------- Prompts ----------
    private fun base(aiName: String, lang: String): String =
        if (lang == "en")
            "You are $aiName, the music assistant inside the MusicFlame app. Reply in English, in plain text (no Markdown, no asterisks, no headings). " +
            "Be warm and concise. Never invent facts: if you are not sure about a song, artist or fact, say so instead of guessing. " +
            "The song DATA you receive comes from the user's own library and is reliable; anything you know beyond it may be wrong. You cannot hear audio."
        else
            "Eres $aiName, el asistente musical de la app MusicFlame. Responde en español, en texto plano (sin Markdown, sin asteriscos, sin títulos). " +
            "Sé cálido y conciso. Nunca inventes datos: si no estás seguro de una canción, artista o dato, dilo en vez de adivinar. " +
            "Los DATOS de canciones que recibes vienen de la biblioteca del propio usuario y son fiables; lo que sepas más allá puede estar equivocado. No puedes oír el audio."

    private fun numbered(songs: List<Song>, stats: Map<Long, SongStat>, lang: String) =
        songs.mapIndexed { i, s -> "${i + 1}) ${songLine(s, stats, lang)}" }.joinToString("\n")

    fun analyze(aiName: String, lang: String, songs: List<Song>, stats: Map<Long, SongStat>): Request {
        val en = lang == "en"
        val task = if (en)
            "Task /analyze: for EACH song, in the given order, write one numbered line with the title and 1-2 sentences: style or genre, what stands out, and how it fits the user's listening (use the data). " +
            "If you don't know the song well, write 'I don't know it well' and comment only on the data. Then one closing sentence about the whole set."
        else
            "Tarea /analiza: para CADA canción, en el orden dado, escribe una línea numerada con el título y 1-2 frases: estilo o género, qué destaca y cómo encaja con las escuchas del usuario (usa los datos). " +
            "Si no conoces bien la canción, escribe 'no la conozco bien' y comenta solo con los datos. Al final, una frase de conclusión sobre el conjunto."
        return Request(base(aiName, lang) + "\n" + task, (if (en) "Songs:\n" else "Canciones:\n") + numbered(songs, stats, lang), 1500)
    }

    fun opinion(aiName: String, lang: String, songs: List<Song>, stats: Map<Long, SongStat>): Request {
        val en = lang == "en"
        val task = if (en)
            "Task /opinion: give your honest, short opinion (1-2 sentences) of EACH song and a score from 1 to 10 written like '7/10'. One line per song: '1. Title — 8/10: opinion'. " +
            "At the end write 'Average: X/10' (compute it carefully with one decimal) and one closing sentence. If you don't know a song, score it only from the data and say so."
        else
            "Tarea /opinion: da tu opinión honesta y breve (1-2 frases) de CADA canción y una calificación de 1 a 10 escrita como '7/10'. Una línea por canción: '1. Título — 8/10: opinión'. " +
            "Al final escribe 'Promedio: X/10' (calcúlalo con cuidado, con un decimal) y una frase de cierre. Si no conoces una canción, califícala solo por los datos y dilo."
        return Request(base(aiName, lang) + "\n" + task, (if (en) "Songs:\n" else "Canciones:\n") + numbered(songs, stats, lang), 1500)
    }

    class TellSubject(val desc: String, val song: Song?)

    fun tellSubject(args: String, library: List<Song>, stats: Map<Long, SongStat>, nowId: Long?, lang: String): TellSubject {
        val en = lang == "en"
        if (args.isNotBlank()) {
            AiLibraryContext.findSong(args, library)?.let { return TellSubject(songLine(it, stats, lang), it) }
            AiLibraryContext.findArtist(args, library)?.let { return TellSubject((if (en) "Artist: " else "Artista: ") + it, null) }
            return TellSubject((if (en) "Topic: " else "Tema: ") + args.take(120), null)
        }
        val song = library.firstOrNull { it.id == nowId }
            ?: library.filter { (stats[it.id]?.playCount ?: 0) > 0 }.maxByOrNull { stats[it.id]?.playCount ?: 0 }
            ?: library.randomOrNull()
        return if (song != null) TellSubject(songLine(song, stats, lang), song)
        else TellSubject(if (en) "Music in general" else "La música en general", null)
    }

    fun tell(aiName: String, lang: String, subject: TellSubject): Request {
        val en = lang == "en"
        val task = if (en)
            "Task /tell: share ONE real curious fact about the given music (song, artist, genre or topic) in at most 3 sentences. Only if you are sure it is true; " +
            "if you are not sure about this particular song or artist, say so and give a well-known verified fact about its genre instead. Do not invent dates, figures or anecdotes."
        else
            "Tarea /tell: comparte UN dato curioso real sobre la música indicada (canción, artista, género o tema) en máximo 3 oraciones. Solo si estás seguro de que es verdad; " +
            "si no estás seguro de esa canción o artista, dilo y da un dato conocido y verificado de su género. No inventes fechas, cifras ni anécdotas."
        return Request(base(aiName, lang) + "\n" + task, (if (en) "Subject: " else "Sobre: ") + subject.desc, 500)
    }

    // ---------- Wrapped / Roast ----------
    class WrappedData(val text: String, val topSongs: List<Song>, val hasPlays: Boolean)

    fun wrappedData(library: List<Song>, stats: Map<Long, SongStat>, lang: String): WrappedData {
        val en = lang == "en"
        val byId = library.associateBy { it.id }
        val played = stats.entries.filter { it.value.playCount > 0 && byId.containsKey(it.key) }
        if (played.isEmpty()) return WrappedData("", emptyList(), false)
        val totalPlays = played.sumOf { it.value.playCount }
        val totalMin = played.sumOf { it.value.totalListenedMs } / 60_000L
        val topSongs = played.sortedByDescending { it.value.playCount }.take(5).map { byId.getValue(it.key) }
        class Agg(var plays: Int = 0, var ms: Long = 0L)
        val artists = HashMap<String, Agg>()
        val genres = HashMap<String, Agg>()
        for (e in played) {
            val s = byId.getValue(e.key)
            if (known(s.artist)) artists.getOrPut(s.artist) { Agg() }.also { it.plays += e.value.playCount; it.ms += e.value.totalListenedMs }
            if (known(s.genre)) genres.getOrPut(s.genre!!) { Agg() }.also { it.plays += e.value.playCount }
        }
        val topArtists = artists.entries.sortedByDescending { it.value.plays }.take(5)
        val topGenres = genres.entries.sortedByDescending { it.value.plays }.take(3)
        val text = buildString {
            append(if (en) "Library: ${library.size} songs, ${library.size - played.size} never played.\n" else "Biblioteca: ${library.size} canciones, ${library.size - played.size} sin escuchar nunca.\n")
            append(if (en) "Total plays: $totalPlays. Minutes listened: $totalMin.\n" else "Reproducciones totales: $totalPlays. Minutos escuchados: $totalMin.\n")
            append(if (en) "Top artists (plays, minutes): " else "Artistas más escuchados (veces, minutos): ")
            append(topArtists.joinToString("; ") { "${it.key} (${it.value.plays}, ${it.value.ms / 60_000L})" }).append('\n')
            append(if (en) "Top songs: " else "Canciones más escuchadas: ")
            append(topSongs.joinToString("; ") { "«${it.title}» — ${it.artist} (${stats[it.id]?.playCount ?: 0})" }).append('\n')
            if (topGenres.isNotEmpty()) {
                append(if (en) "Top genres: " else "Géneros más escuchados: ")
                append(topGenres.joinToString(", ") { it.key }).append('\n')
            }
        }.trim()
        return WrappedData(text, topSongs, true)
    }

    fun wrapped(aiName: String, lang: String, data: WrappedData): Request {
        val en = lang == "en"
        val task = if (en)
            "Task /wrapped: write a fun 'Wrapped'-style summary (4-6 sentences) of the user's listening. Mention the top artist, the most played song, total minutes and one curious thing you notice. Use ONLY the figures given."
        else
            "Tarea /wrapped: redacta un resumen divertido estilo 'Wrapped' (4-6 frases) de lo que escucha el usuario. Menciona el artista top, la canción más escuchada, los minutos totales y algo curioso que notes. Usa SOLO las cifras dadas."
        return Request(base(aiName, lang) + "\n" + task, (if (en) "DATA:\n" else "DATOS:\n") + data.text, 700)
    }

    fun roast(aiName: String, lang: String, summary: String?, data: WrappedData): Request {
        val en = lang == "en"
        val task = if (en)
            "Task /roast: tease the user's music taste in a friendly, creative way (3-5 sentences) using the data. No hurtful insults and no sensitive topics; the goal is that they laugh. Use only the data given."
        else
            "Tarea /roast: haz una burla amistosa y creativa (3-5 frases) de los gustos del usuario usando los datos. Sin insultos hirientes ni temas sensibles; la idea es que se ría. Usa solo los datos dados."
        val body = listOfNotNull(summary, data.text.takeIf { it.isNotBlank() }).joinToString("\n")
        return Request(base(aiName, lang) + "\n" + task, (if (en) "DATA:\n" else "DATOS:\n") + body, 500, 0.8)
    }

    // ---------- Duelo ----------
    class Side(val label: String, val desc: String, val songs: List<Song>)

    fun splitDuel(args: String): Pair<String, String>? {
        val parts = Regex("(?i)\\s+(?:vs\\.?|versus|contra)\\s+").split(args.trim(), 2)
        if (parts.size != 2) return null
        val a = parts[0].trim(); val b = parts[1].trim()
        return if (a.isNotEmpty() && b.isNotEmpty()) a to b else null
    }

    fun duelSide(query: String, library: List<Song>, stats: Map<Long, SongStat>, lang: String): Side {
        val en = lang == "en"
        AiLibraryContext.findSong(query, library)?.let { return Side(it.title, songLine(it, stats, lang), listOf(it)) }
        AiLibraryContext.findArtist(query, library)?.let { artist ->
            val ss = library.filter { it.artist.equals(artist, true) }
            val plays = ss.sumOf { stats[it.id]?.playCount ?: 0 }
            val top = ss.maxByOrNull { stats[it.id]?.playCount ?: 0 }
            val desc = if (en) "Artist «$artist»: ${ss.size} songs in the library, $plays plays" + (top?.let { "; most played: «${it.title}»" } ?: "")
            else "Artista «$artist»: ${ss.size} canciones en la biblioteca, $plays escuchas" + (top?.let { "; la más escuchada: «${it.title}»" } ?: "")
            return Side(artist, desc, listOfNotNull(top))
        }
        return Side(query, if (en) "\"$query\" (not in the user's library)" else "\"$query\" (no está en su biblioteca)", emptyList())
    }

    fun duel(aiName: String, lang: String, a: Side, b: Side): Request {
        val en = lang == "en"
        val task = if (en)
            "Task /duel: compare side A and side B (songs or artists) in 4-6 sentences: style, strengths and what the user's listening data says. End with a clear verdict ('A wins' / 'B wins') and say it is just an opinion. " +
            "If a side is not in the library or you don't know it well, say so."
        else
            "Tarea /duelo: compara el lado A y el lado B (canciones o artistas) en 4-6 frases: estilo, fortalezas y qué dicen los datos de escucha del usuario. Termina con un veredicto claro ('Gana A' / 'Gana B') aclarando que es solo una opinión. " +
            "Si un lado no está en la biblioteca o no lo conoces bien, dilo."
        return Request(base(aiName, lang) + "\n" + task, "A: ${a.desc}\nB: ${b.desc}", 800)
    }

    // ---------- Playlist (el modelo elige; la app valida y pregunta antes de guardar) ----------
    /** Hasta [PLAYLIST_CANDIDATES] canciones de la biblioteca para que el modelo elija: primero las que encajan con el tema. */
    fun playlistCandidates(theme: String, library: List<Song>, stats: Map<Long, SongStat>): List<Song> {
        if (library.size <= PLAYLIST_CANDIDATES) return library
        val matched = AiLibraryContext.matchTheme(theme, library).take(80)
        val top = library.filter { (stats[it.id]?.playCount ?: 0) > 0 }.sortedByDescending { stats[it.id]?.playCount ?: 0 }.take(30)
        return (matched + top + library.shuffled()).distinctBy { it.id }.take(PLAYLIST_CANDIDATES)
    }

    fun playlist(aiName: String, lang: String, theme: String, candidates: List<Song>): Request {
        val en = lang == "en"
        val task = if (en)
            "Task /playlist: from the LIST, pick between 5 and 10 songs that best fit the theme, in a pleasant listening order. Reply ONLY with valid JSON, no extra text and no code fences: " +
            "{\"name\":\"short playlist name\",\"why\":\"one sentence\",\"picks\":[list numbers]}. Use only numbers that exist in the list, no repeats."
        else
            "Tarea /playlist: de la LISTA, elige entre 5 y 10 canciones que mejor encajen con el tema, en un orden agradable de escuchar. Responde SOLO con JSON válido, sin texto extra ni bloques de código: " +
            "{\"name\":\"nombre corto de la playlist\",\"why\":\"una frase\",\"picks\":[números de la lista]}. Usa solo números que existan en la lista, sin repetir."
        val themeLine = theme.ifBlank { if (en) "what the user listens to most / a varied mix" else "lo que más escucha el usuario / una mezcla variada" }
        val list = candidates.mapIndexed { i, s ->
            "${i + 1}|${s.title}|${s.artist}" + (if (known(s.genre)) "|${s.genre}" else "")
        }.joinToString("\n")
        return Request(base(aiName, lang) + "\n" + task, (if (en) "Theme: " else "Tema: ") + themeLine + "\n" + (if (en) "LIST (n|title|artist|genre):\n" else "LISTA (n|título|artista|género):\n") + list, 900, 0.5)
    }

    private fun extractJson(raw: String): JSONObject? {
        val s = raw.indexOf('{'); val e = raw.lastIndexOf('}')
        if (s < 0 || e <= s) return null
        return try { JSONObject(raw.substring(s, e + 1)) } catch (_: Exception) { null }
    }

    class PlaylistResult(val name: String, val why: String, val songs: List<Song>)

    fun parsePlaylist(raw: String, candidates: List<Song>): PlaylistResult? {
        val o = extractJson(raw) ?: return null
        val arr = o.optJSONArray("picks") ?: return null
        val seen = LinkedHashSet<Int>()
        for (i in 0 until arr.length()) {
            val n = arr.optInt(i, -1)
            if (n in 1..candidates.size) seen += n
        }
        val songs = seen.take(MAX_SONGS).map { candidates[it - 1] }
        if (songs.isEmpty()) return null
        return PlaylistResult(o.optString("name").trim().take(40).ifBlank { "MusicFlame IA" }, o.optString("why").trim().take(200), songs)
    }

    // ---------- Quiz ----------
    fun quiz(aiName: String, lang: String, library: List<Song>, stats: Map<Long, SongStat>): Request {
        val en = lang == "en"
        val task = if (en)
            "Task /quiz: create ONE trivia question with 4 options about the songs in the list. The answer must be checkable with the data (artist, album, year, genre, plays) " +
            "or be a famous music fact you are completely sure about. All 4 options must be plausible, only one correct. Reply ONLY with JSON, no extra text: " +
            "{\"q\":\"question\",\"options\":[\"a\",\"b\",\"c\",\"d\"],\"answer\":0,\"why\":\"one sentence\"} (answer = index 0-3)."
        else
            "Tarea /quiz: crea UNA pregunta de trivia con 4 opciones sobre las canciones de la lista. La respuesta debe poder comprobarse con los datos (artista, álbum, año, género, escuchas) " +
            "o ser un dato musical famoso del que estés totalmente seguro. Las 4 opciones deben ser plausibles y solo una correcta. Responde SOLO con JSON, sin texto extra: " +
            "{\"q\":\"pregunta\",\"options\":[\"a\",\"b\",\"c\",\"d\"],\"answer\":0,\"why\":\"una frase\"} (answer = índice 0-3)."
        val sample = (library.filter { (stats[it.id]?.playCount ?: 0) > 0 }.sortedByDescending { stats[it.id]?.playCount ?: 0 }.take(6) + library.shuffled())
            .distinctBy { it.id }.take(12)
        val user = (if (en) "Songs:\n" else "Canciones:\n") + numbered(sample, stats, lang) +
            "\n" + (if (en) "Variation seed: " else "Semilla de variación: ") + (1000..9999).random()
        return Request(base(aiName, lang) + "\n" + task, user, 800, 0.9)
    }

    class QuizResult(val question: String, val options: List<String>, val correct: Int, val why: String)

    fun parseQuiz(raw: String): QuizResult? {
        val o = extractJson(raw) ?: return null
        val q = o.optString("q").trim()
        val arr = o.optJSONArray("options") ?: return null
        val opts = (0 until arr.length()).map { arr.optString(it).trim() }
        val ans = o.optInt("answer", -1)
        if (q.isEmpty() || opts.size != 4 || opts.any { it.isEmpty() } || opts.toSet().size != 4 || ans !in 0..3) return null
        val order = opts.indices.shuffled()   // el modelo suele poner la correcta en la misma posición: se baraja aquí
        return QuizResult(q, order.map { opts[it] }, order.indexOf(ans), o.optString("why").trim().take(200))
    }
}
