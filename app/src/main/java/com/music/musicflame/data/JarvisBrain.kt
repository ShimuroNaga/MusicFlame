package com.music.musicflame.data

import android.content.Context
import android.media.AudioManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.Normalizer

/**
 * "Cerebro" de acciones de la IA (Jarvis).
 *
 * Reglas de seguridad (a propósito):
 *  1) LISTA BLANCA: la IA solo puede pedir las herramientas de [JarvisTool]. Cualquier otra cosa que
 *     invente se descarta. No existe ninguna herramienta para borrar archivos ni tocar la papelera.
 *  2) La IA NUNCA elige IDs ni rutas: describe lo que quiere (título, artista, "las más escuchadas"...) y
 *     la app lo resuelve contra la biblioteca real. Si no existe, se dice tal cual (nada de inventar).
 *  3) Lo que cambia muchas cosas (quitar favoritos en masa, vaciar una playlist...) pide confirmación Sí / No.
 *  4) Privacidad con Groq: solo salen el texto del usuario, la canción que suena, NOMBRES de playlists y un
 *     resumen de nombres (artistas/géneros). Jamás rutas de archivo.
 */
enum class JarvisTool(val id: String) {
    PLAY_SONG("play_song"), PLAY_ARTIST("play_artist"), PLAY_ALBUM("play_album"),
    PLAY_GENRE("play_genre"), PLAY_PLAYLIST("play_playlist"), PLAY_SELECTION("play_selection"),
    QUEUE_NEXT("queue_next"), QUEUE_ADD("queue_add"),
    FAVORITE("favorite"), UNFAVORITE("unfavorite"),
    CREATE_PLAYLIST("create_playlist"), ADD_TO_PLAYLIST("add_to_playlist"), REMOVE_FROM_PLAYLIST("remove_from_playlist"),
    PAUSE("pause"), RESUME("resume"), NEXT("next"), PREVIOUS("previous"),
    VOLUME("volume"), SLEEP_TIMER("sleep_timer"), CANCEL_SLEEP_TIMER("cancel_sleep_timer"),
    REPEAT("repeat"), SHUFFLE_MODE("shuffle_mode"), RENAME_PLAYLIST("rename_playlist"),
    EQ_PRESET("eq_preset"), EQ_BAND("eq_band"), SONG_TITLE("song_title"), SONG_COVER("song_cover"),
    /** Sala de Juntos: las ejecuta la pantalla de voz (necesitan la Activity para abrir el menú de invitar). */
    TOGETHER_CREATE("together_create"), TOGETHER_INVITE("together_invite");

    companion object {
        fun fromId(id: String?): JarvisTool? {
            val k = id?.trim()?.lowercase() ?: return null
            return values().firstOrNull { it.id == k }
        }
    }
}

/** Descripción (no IDs) de un conjunto de canciones. La app lo traduce a canciones reales. */
class JarvisSelector(
    val query: String? = null,      // título(s) concreto(s)
    val source: String? = null,     // most_played | never_played | forgotten | favorites | now_playing | random | playlist
    val theme: String? = null,      // "rock de los 2000", "para estudiar"...
    val genre: String? = null,
    val artist: String? = null,
    val yearFrom: Int? = null,
    val yearTo: Int? = null,
    val minMinutes: Double? = null,
    val maxMinutes: Double? = null,
    val count: Int? = null,
    val shuffle: Boolean = false
)

class JarvisCall(
    val tool: JarvisTool,
    val songs: JarvisSelector? = null,
    val name: String? = null,       // artista / álbum / género / nombre de la playlist nueva
    val playlist: String? = null,   // playlist objetivo (agregar/quitar)
    val value: Int? = null,         // minutos (temporizador) o % (volumen)
    val direction: String? = null,  // up | down (volumen)
    val shuffle: Boolean = false
)

class JarvisPlan(val say: String?, val calls: List<JarvisCall>)

/** Una acción ya resuelta contra la biblioteca real (lista para ejecutarse). */
class JarvisStep(
    val call: JarvisCall,
    val songs: List<Song> = emptyList(),
    val candidates: List<Song> = emptyList(),
    /** Resto de la biblioteca (sin marcar) que la tabla del borrador muestra debajo de las canciones elegidas. */
    val extra: List<Song> = emptyList(),
    val label: String? = null,
    val playlistId: String? = null,
    /** Si no se pudo resolver, el texto ya listo para decírselo al usuario. */
    val problem: String? = null
)

class JarvisOutcome(val lines: List<String>, val songIds: List<Long>, val playlistsChanged: Boolean)

object JarvisBrain {
    private const val MAX_CALLS = 6
    // Sin tope fijo: el maximo es la cantidad de canciones que tenga el usuario en su biblioteca.
    private const val MAX_SONGS_PER_CALL = Int.MAX_VALUE
    private const val FORGOTTEN_DAYS = 90L

    // ---------- Texto ----------
    private val RE_MARKS = Regex("\\p{M}+")
    private val RE_SEP = Regex("[^a-z0-9]+")

    fun fold(s: String): String =
        RE_SEP.replace(RE_MARKS.replace(Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD), ""), " ").trim()

    // ---------- Atajos sin IA (instantáneos, 0 tokens, funcionan sin internet) ----------
    private val RE_PAUSE = Regex("^(pon )?(pausa|pausar|pause|detente|deten la musica|para la musica|stop|stop the music|pare|parar|pare a musica|parar a musica|pausa a musica|pausar a musica)$")
    private val RE_RESUME = Regex("^(continua|continuar|reanuda|reanudar|resume|play|reproduce|dale play|sigue|continue|retoma|retomar|toca|tocar|toque|da play|de play|segue)$")
    private val RE_NEXT = Regex("^(siguiente|sig|next|skip|salta|saltar|siguiente cancion|next song|salta la cancion|skip song|proxima|proximo|proxima musica|proxima faixa|pula|pule|pular|pula a musica|pular musica|avanca|avancar)$")
    private val RE_PREV = Regex("^(anterior|previous|atras|cancion anterior|previous song|regresa|voltar|volta|volte|musica anterior|faixa anterior|volta a musica)$")
    private val RE_SLEEP = Regex("(apagate|apaga|duermete|temporizador|sleep timer|sleep|apagar|desligar|desliga|desligue|dormir|durma|timer)( en| de| a los| in| for| em| daqui a| daqui| por)? (\\d{1,3}) ?(min|minuto|minutos|m|minutes)")
    private val RE_SLEEP_OFF = Regex("(cancela|quita|apaga|cancel|stop|cancelar|cancele|remove|remova|desativa|desative|desativar|para|pare) (el |o |a )?(temporizador|sleep timer|timer)")
    private val RE_VOL_SET = Regex("(volumen|volume) (al |a |to |at |ao |em |para |no |na )?(\\d{1,3})")
    private val RE_VOL_UP = Regex("^(sube|subele|sube el volumen|subele al volumen|aumenta el volumen|volume up|turn it up|louder|aumenta|aumentar|aumente|aumenta o volume|aumente o volume|sobe|suba|sobe o volume|suba o volume|mais alto|mais volume)$")
    private val RE_VOL_DOWN = Regex("^(baja|bajale|baja el volumen|bajale al volumen|reduce el volumen|volume down|turn it down|quieter|abaixa|abaixar|abaixe|abaixa o volume|abaixe o volume|baixa|baixe|baixa o volume|baixe o volume|diminui|diminua|diminui o volume|diminua o volume|mais baixo|menos volume)$")

    // ---------- Crear playlists aleatorias sin depender del modelo ----------
    private val RE_MK_VERB = Regex("\\b(crea|creame|crear|haz|hazme|hacer|arma|armame|armar|genera|generame|generar|arroja|arrojame|muestrame|ensename|dame|quiero|necesito|create|make|build|generate|show|want|need|give|cria|crie|criar|faz|faca|fazer|monta|monte|montar|gera|gere|gerar|mostra|mostre|me de|me da|quero|preciso)\\b")
    private val RE_MK_PLAYLIST = Regex("play ?lists?|listas? de reproduccion|listas? de reproducao")
    private val RE_MK_RANDOM = Regex("aleatori|al azar|random|sorpresa|variad|revuelt|surpresa|misturad|embaralhad|ao acaso")
    // "una tabla de mis canciones para elegirlas": sin tema ni artista, la tabla se arma con una muestra de la biblioteca.
    private val RE_MK_PICK = Regex("\\btabla\\b|elegir|escoger|seleccionar|mis canciones|mi musica|table|pick|choose|\\btabela\\b|escolher|selecionar|minhas musicas|minha musica")
    private val RE_MK_COUNT = Regex("(\\d{1,3}|[a-z]+) (canciones|cancion|temas|songs|tracks|musicas|musica|faixas|faixa)")
    private val RE_MK_THEMED = Regex("\\b(playlists?|listas?) (de|del|con|of|with|by|do|da|dos|das|com) (?!mis\\b|mi\\b|todas\\b|toda\\b|minhas\\b|minha\\b|\\d)")
    private val RE_MK_SONGS = Regex("(\\d{1,3}|[a-z]+) (canciones|cancion|temas|musicas|musica|songs|tracks|piezas|faixas|faixa)")
    private val RE_MK_LISTS = Regex("(\\d{1,2}|[a-z]+) (playlists?|listas)")
    private val NUM_WORDS = mapOf(
        "un" to 1, "una" to 1, "dos" to 2, "tres" to 3, "cuatro" to 4, "cinco" to 5, "seis" to 6, "diez" to 10, "quince" to 15,
        "veinte" to 20, "veinticinco" to 25, "treinta" to 30, "cuarenta" to 40, "cincuenta" to 50, "sesenta" to 60,
        "setenta" to 70, "ochenta" to 80, "noventa" to 90, "cien" to 100, "quinientas" to 500, "quinientos" to 500, "ciento" to 100, "doscientas" to 200, "doscientos" to 200,
        "ten" to 10, "fifteen" to 15, "twenty" to 20, "thirty" to 30, "forty" to 40, "fifty" to 50, "hundred" to 100, "five hundred" to 500, "um" to 1, "dois" to 2, "duas" to 2, "dez" to 10, "quinze" to 15, "vinte" to 20, "trinta" to 30, "quarenta" to 40, "cinquenta" to 50, "sessenta" to 60, "setenta" to 70, "oitenta" to 80, "cem" to 100, "duzentas" to 200, "duzentos" to 200, "quinhentas" to 500, "quinhentos" to 500
    )
    private fun numOf(tok: String): Int? = tok.toIntOrNull() ?: NUM_WORDS[tok]

    /**
     * "Crea una playlist de 50 canciones aleatorias" (también "2 playlists aleatorias de 30 canciones").
     * Se resuelve aquí, sin modelo: así siempre sale la tabla de borrador y nunca se queda en un "listo" inventado.
     * Pedidos con artista, género o tema siguen yendo al modelo.
     */
    fun quickCreatePlaylists(text: String, lang: String): JarvisPlan? {
        val f = fold(text)
        if (f.isEmpty() || f.length > 160) return null
        // "una playlist de 50 canciones" (con cantidad) tambien cuenta como pedir la tabla para elegir.
        val hasCount = RE_MK_COUNT.findAll(f).any { numOf(it.groupValues[1]) != null }
        val pick = RE_MK_PICK.containsMatchIn(f) || hasCount
        // "playlist de rock / de Queen para elegir": trae tema o artista -> que lo resuelva el modelo.
        // "playlist de cincuenta canciones" NO es un tema: despues del "de" hay un numero escrito con letras.
        val themed = RE_MK_THEMED.find(f)?.let { m -> numOf(f.substring(m.range.last + 1).trim().substringBefore(' ')) == null } ?: false
        android.util.Log.w("JarvisDraft", "quick f='$f' verb=${RE_MK_VERB.containsMatchIn(f)} playlist=${RE_MK_PLAYLIST.containsMatchIn(f)} random=${RE_MK_RANDOM.containsMatchIn(f)} pick=$pick themed=$themed")
        if (pick && !RE_MK_RANDOM.containsMatchIn(f) && themed) return null
        if (!RE_MK_VERB.containsMatchIn(f) || !RE_MK_PLAYLIST.containsMatchIn(f) || !(RE_MK_RANDOM.containsMatchIn(f) || pick)) return null
        val given = RE_MK_SONGS.findAll(f).mapNotNull { numOf(it.groupValues[1]) }.firstOrNull()
        // "una tabla de mis canciones para elegirlas" (sin cantidad ni "aleatoria"): tabla con TODA la biblioteca y 0 marcadas.
        val browse = pick && given == null && !RE_MK_RANDOM.containsMatchIn(f)
        val perList = given ?: (if (pick) 50 else 20)
        val lists = RE_MK_LISTS.findAll(f).mapNotNull { numOf(it.groupValues[1]) }.firstOrNull() ?: 1
        val count = perList.coerceIn(1, MAX_SONGS_PER_CALL)
        val n = if (browse) 1 else lists.coerceIn(1, 4)
        val base = if (browse) (if (lang == "en") "My picks" else if (lang == "pt") "Minha seleção" else "Mi selección") else if (lang == "en") "Random mix" else if (lang == "pt") "Mix aleatório" else "Mezcla aleatoria"
        val calls = (1..n).map { i ->
            JarvisCall(
                JarvisTool.CREATE_PLAYLIST,
                songs = if (browse) JarvisSelector(source = "browse") else JarvisSelector(source = "random", count = count, shuffle = true),
                name = if (n == 1) base else "$base $i"
            )
        }
        return JarvisPlan(null, calls)
    }

    private val RE_MK_TOPIC = Regex("\\b(playlists?|listas?) (de|del|con|of|with|by|do|da|dos|das|com) (.+)")
    private val RE_MK_LEAD = Regex("^(canciones|cancion|temas|musica|songs|music|musicas|faixas|faixa)\\s+(de|del|of|by|do|da)\\s+")
    private val RE_MK_EDGE = Regex("^(de|del|con|of|with|by|para|for|do|da|com)\\s+|\\s+(de|del|con|of|with|by|para|for|do|da|com)$")
    private val RE_MK_TAIL = Regex("\\s+(songs|tracks|music|canciones|cancion|temas|musica|musicas|faixas|faixa)$")
    private val RE_MK_PICKTAIL = Regex("\\s+((para|pa|to)\\s+)?(elegir|escoger|seleccionar|pick|choose|escolher|selecionar)\\w*$")

    /**
     * Respaldo para "hazme una playlist de Ado / de rock": si el modelo contesta con charla en vez de create_playlist,
     * se arma aqui el pedido con el tema o artista dicho, para que SIEMPRE salga la tabla.
     */
    fun themedCreatePlaylist(text: String, lang: String): JarvisPlan? {
        val f = fold(text)
        if (f.isEmpty() || f.length > 160) return null
        if (!RE_MK_VERB.containsMatchIn(f) || !RE_MK_PLAYLIST.containsMatchIn(f)) return null
        val given = RE_MK_SONGS.findAll(f).mapNotNull { numOf(it.groupValues[1]) }.firstOrNull() ?: 30
        var topic = RE_MK_TOPIC.find(f)?.groupValues?.get(3) ?: return null
        // Solo se quita "30 canciones" / "treinta canciones" (numero + palabra); "Ado songs" conserva "Ado".
        topic = RE_MK_SONGS.replace(topic) { m -> if (numOf(m.groupValues[1]) != null) " " else m.value }.trim()
        topic = RE_MK_TAIL.replace(RE_MK_PICKTAIL.replace(topic, ""), "").trim()
        topic = RE_MK_EDGE.replace(RE_MK_LEAD.replace(topic, ""), "").trim().take(40)
        if (topic.isEmpty()) return null
        val name = topic.replaceFirstChar { it.uppercase() }
        android.util.Log.w("JarvisDraft", "themedCreatePlaylist topic='$topic' count=$given")
        val call = JarvisCall(
            JarvisTool.CREATE_PLAYLIST,
            songs = JarvisSelector(theme = topic, count = given.coerceIn(1, MAX_SONGS_PER_CALL), shuffle = true),
            name = name
        )
        return JarvisPlan(null, listOf(call))
    }

    fun quickPlan(text: String): JarvisPlan? {
        val f = fold(text)
        if (f.isEmpty() || f.length > 60) return null
        val call: JarvisCall? = when {
            RE_PAUSE.matches(f) -> JarvisCall(JarvisTool.PAUSE)
            RE_RESUME.matches(f) -> JarvisCall(JarvisTool.RESUME)
            RE_NEXT.matches(f) -> JarvisCall(JarvisTool.NEXT)
            RE_PREV.matches(f) -> JarvisCall(JarvisTool.PREVIOUS)
            RE_SLEEP_OFF.containsMatchIn(f) -> JarvisCall(JarvisTool.CANCEL_SLEEP_TIMER)
            RE_SLEEP.containsMatchIn(f) -> RE_SLEEP.find(f)?.groupValues?.getOrNull(3)?.toIntOrNull()
                ?.let { JarvisCall(JarvisTool.SLEEP_TIMER, value = it) }
            RE_VOL_SET.containsMatchIn(f) -> RE_VOL_SET.find(f)?.groupValues?.getOrNull(3)?.toIntOrNull()
                ?.let { JarvisCall(JarvisTool.VOLUME, value = it) }
            RE_VOL_UP.matches(f) -> JarvisCall(JarvisTool.VOLUME, direction = "up")
            RE_VOL_DOWN.matches(f) -> JarvisCall(JarvisTool.VOLUME, direction = "down")
            else -> null
        }
        return call?.let { JarvisPlan(null, listOf(it)) }
    }

    // ---------- Parseo del plan que devuelve el modelo ----------
    private fun JSONObject.str(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key, "").trim().ifEmpty { null }

    private fun JSONObject.intOrNull(key: String): Int? {
        if (!has(key) || isNull(key)) return null
        return when (val v = opt(key)) { is Number -> v.toInt(); is String -> v.trim().toIntOrNull(); else -> null }
    }

    private fun JSONObject.dblOrNull(key: String): Double? {
        if (!has(key) || isNull(key)) return null
        return when (val v = opt(key)) { is Number -> v.toDouble(); is String -> v.trim().toDoubleOrNull(); else -> null }
    }

    private fun JSONObject.flag(key: String): Boolean = when (val v = opt(key)) {
        is Boolean -> v
        is String -> v.equals("true", true)
        else -> false
    }

    private fun parseSelector(o: JSONObject) = JarvisSelector(
        query = o.str("query"), source = o.str("source")?.lowercase(), theme = o.str("theme"),
        genre = o.str("genre"), artist = o.str("artist"),
        yearFrom = o.intOrNull("year_from"), yearTo = o.intOrNull("year_to"),
        minMinutes = o.dblOrNull("min_minutes"), maxMinutes = o.dblOrNull("max_minutes"),
        count = o.intOrNull("count"), shuffle = o.flag("shuffle")
    )

    /** Devuelve null si la respuesta no es un JSON utilizable (entonces el chat sigue como siempre). */
    fun parsePlan(raw: String): JarvisPlan? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val root = try { JSONObject(raw.substring(start, end + 1)) } catch (e: Exception) { return null }
        val say = root.str("say")
        val arr = root.optJSONArray("actions")
        val calls = ArrayList<JarvisCall>()
        if (arr != null) {
            for (i in 0 until minOf(arr.length(), MAX_CALLS)) {
                val o = arr.optJSONObject(i) ?: continue
                val tool = JarvisTool.fromId(o.str("tool")) ?: continue   // fuera de la lista blanca → se ignora
                val selObj = o.optJSONObject("songs")
                    ?: if (listOf("query", "source", "theme", "genre", "artist").any { o.has(it) }) o else null
                calls += JarvisCall(
                    tool = tool, songs = selObj?.let { parseSelector(it) }, name = o.str("name"), playlist = o.str("playlist"),
                    value = o.intOrNull("value"), direction = o.str("direction")?.lowercase(), shuffle = o.flag("shuffle")
                )
            }
        }
        return JarvisPlan(say, calls)
    }

    // ---------- Confirmaciones ----------
    fun confirmQuestion(steps: List<JarvisStep>, lang: String): String? {
        val en = lang == "en"
        val pt = lang == "pt"
        val parts = ArrayList<String>()
        for (s in steps) {
            if (s.problem != null) continue
            val n = s.songs.size
            when (s.call.tool) {
                JarvisTool.UNFAVORITE -> if (n >= 3) parts += if (en) "remove $n songs from favorites" else if (pt) "remover $n músicas dos favoritos" else "quitar $n canciones de favoritos"
                JarvisTool.REMOVE_FROM_PLAYLIST -> if (n >= 3)
                    parts += if (en) "remove $n songs from «${s.label}»" else if (pt) "remover $n músicas de «${s.label}»" else "quitar $n canciones de «${s.label}»"
                JarvisTool.FAVORITE -> if (n >= 15) parts += if (en) "mark $n songs as favorites" else if (pt) "marcar $n músicas como favoritas" else "marcar $n canciones como favoritas"
                else -> {}
            }
        }
        if (parts.isEmpty()) return null
        val list = parts.joinToString(", ")
        return if (en) "That changes a lot at once: $list. Shall I go ahead?" else if (pt) "Isso muda várias coisas de uma vez: $list. Posso fazer?" else "Esto cambia varias cosas a la vez: $list. ¿Lo hago?"
    }

    // ---------- Resolver contra la biblioteca real ----------
    private class NamedList(val name: String, val ids: List<Long>, val editableId: String?)

    private fun findList(ctx: Context, query: String?): NamedList? {
        val q = fold(query ?: return null)
        if (q.isEmpty()) return null
        val regular = PlaylistRepository(ctx).getAllPlaylists()
        regular.firstOrNull { fold(it.name) == q }?.let { return NamedList(it.name, it.songIds, if (it.isDefault) null else it.id) }
        if (q.contains("favorit")) return NamedList("Favoritos", FavoritesRepository(ctx).getAllFavoriteIds().toList(), null)
        val most = buildMostPlayedPlaylist(ctx)
        if (fold(most.name) == q || q.contains("sonado") || q.contains("most played") || q.contains("mas escuchad") || q.contains("mais tocad") || q.contains("mais ouvid"))
            return NamedList(most.name, most.songIds, null)
        val never = buildNeverPlayedPlaylist(ctx)
        if (fold(never.name) == q || q.contains("descubrir") || q.contains("discover") || q.contains("never played") || q.contains("descobrir") || q.contains("nunca ouvid"))
            return NamedList(never.name, never.songIds, null)
        regular.firstOrNull { val n = fold(it.name); n.isNotEmpty() && (n.contains(q) || q.contains(n)) }
            ?.let { return NamedList(it.name, it.songIds, if (it.isDefault) null else it.id) }
        return null
    }

    private class Resolution(val songs: List<Song>, val candidates: List<Song> = emptyList(), val notFound: List<String> = emptyList())

    private fun resolveSongs(
        sel: JarvisSelector?, library: List<Song>, stats: Map<Long, SongStat>, favIds: Set<Long>,
        nowId: Long?, context: NamedList?, defaultCount: Int
    ): Resolution {
        if (sel == null) return Resolution(emptyList())
        val cap = (sel.count ?: defaultCount).coerceIn(1, MAX_SONGS_PER_CALL)
        val listMode = sel.source != null || sel.theme != null || sel.genre != null || sel.artist != null ||
            sel.yearFrom != null || sel.yearTo != null || sel.minMinutes != null || sel.maxMinutes != null

        // Título(s) concreto(s)
        if (!listMode && !sel.query.isNullOrBlank()) {
            val q: String = sel.query!!
            AiLibraryContext.findSong(q, library)?.let { return Resolution(listOf(it)) }
            val r = AiCommands.resolveSongs(q, library)
            if (r.songs.isNotEmpty()) return Resolution(r.songs.take(cap), notFound = r.notFound)
            return Resolution(emptyList(), candidates = AiLibraryContext.matchTheme(q, library).take(5), notFound = listOf(q))
        }

        val now = System.currentTimeMillis()
        var base: List<Song> = when (sel.source) {
            "most_played" -> stats.entries.filter { it.value.playCount > 0 }
                .sortedByDescending { it.value.playCount }.mapNotNull { e -> library.firstOrNull { it.id == e.key } }
            "never_played" -> library.filter { (stats[it.id]?.playCount ?: 0) == 0 }.sortedByDescending { it.dateAdded }
            "forgotten" -> library.filter {
                val st = stats[it.id]
                st != null && st.playCount > 0 && st.lastPlayedAt > 0 && now - st.lastPlayedAt > FORGOTTEN_DAYS * 86_400_000L
            }.sortedBy { stats[it.id]?.lastPlayedAt ?: 0L }
            "favorites" -> library.filter { it.id in favIds }
            "now_playing" -> library.filter { it.id == nowId }
            "playlist" -> context?.let { c -> library.filter { it.id in c.ids } } ?: emptyList()
            "random", "all" -> library
            else -> if (!sel.theme.isNullOrBlank()) AiLibraryContext.matchTheme(sel.theme, library) else library
        }
        // Un tema además de otra fuente: se usa para afinar
        if (sel.source != null && !sel.theme.isNullOrBlank()) {
            val themed = AiLibraryContext.matchTheme(sel.theme, base).toSet()
            if (themed.isNotEmpty()) base = base.filter { it in themed }
        }
        sel.genre?.let { g -> val n = fold(g); base = base.filter { fold(it.genre ?: "").contains(n) } }
        sel.artist?.let { a -> val n = fold(a); base = base.filter { fold(it.artist).contains(n) } }
        sel.yearFrom?.let { y -> base = base.filter { (it.year ?: 0) >= y } }
        sel.yearTo?.let { y -> base = base.filter { val v = it.year ?: 0; v in 1..y } }
        sel.minMinutes?.let { m -> base = base.filter { it.duration >= (m * 60_000).toLong() } }
        sel.maxMinutes?.let { m -> base = base.filter { it.duration in 1..(m * 60_000).toLong() } }
        if (sel.shuffle || sel.source == "random") base = base.shuffled()
        return Resolution(base.take(cap))
    }

    // ---------- Preparar y ejecutar ----------
    suspend fun prepare(ctx: Context, lang: String, calls: List<JarvisCall>, nowId: Long?): List<JarvisStep> =
        withContext(Dispatchers.Default) {
            val en = lang == "en"
            val pt = lang == "pt"
            val library = SongLibraryHolder.songs
            val stats = StatsRepository(ctx).getAllStats()
            val favIds = FavoritesRepository(ctx).getAllFavoriteIds()
            val steps = ArrayList<JarvisStep>()

            fun missing(what: String?) = if (en) "I couldn't find «${what ?: "?"}» in your library." else if (pt) "Não encontrei «${what ?: "?"}» na sua biblioteca." else "No encontré «${what ?: "?"}» en tu biblioteca."

            for (c in calls) {
                when (c.tool) {
                    JarvisTool.PLAY_SONG, JarvisTool.QUEUE_NEXT, JarvisTool.QUEUE_ADD, JarvisTool.FAVORITE, JarvisTool.UNFAVORITE,
                    JarvisTool.PLAY_SELECTION -> {
                        val sel = c.songs ?: c.name?.let { JarvisSelector(query = it) }
                        val r = resolveSongs(sel, library, stats, favIds, nowId, null, if (c.tool == JarvisTool.PLAY_SELECTION) 30 else 10)
                        if (r.songs.isEmpty()) {
                            val q = sel?.query ?: sel?.theme ?: c.name
                            val msg = if (r.candidates.isNotEmpty())
                                (if (en) "I couldn't find exactly «$q». Maybe one of these?" else if (pt) "Não encontrei exatamente «$q». Será que é alguma destas?" else "No encontré exactamente «$q». ¿Será alguna de estas?")
                            else if (sel?.source != null) (if (en) "I found no songs for that in your library." else if (pt) "Não encontrei músicas para isso na sua biblioteca." else "No encontré canciones para eso en tu biblioteca.")
                            else missing(q)
                            steps += JarvisStep(c, candidates = r.candidates, problem = msg)
                        } else {
                            val songs = if (c.tool == JarvisTool.PLAY_SONG && r.songs.size == 1) {
                                val s = r.songs[0]   // suena la canción y luego otras del mismo artista
                                listOf(s) + library.filter { it.id != s.id && it.artist == s.artist }.shuffled().take(25)
                            } else r.songs
                            steps += JarvisStep(c, songs = songs)
                        }
                    }
                    JarvisTool.PLAY_ARTIST -> {
                        val q = c.name ?: c.songs?.artist ?: c.songs?.query
                        val artist = q?.let { AiLibraryContext.findArtist(it, library) }
                        val songs = if (artist == null) emptyList() else library.filter { it.artist == artist }
                            .let { if (c.shuffle || c.songs?.shuffle == true) it.shuffled() else it }
                        steps += if (songs.isEmpty()) JarvisStep(c, problem = missing(q)) else JarvisStep(c, songs = songs, label = artist)
                    }
                    JarvisTool.PLAY_ALBUM -> {
                        val q = c.name ?: c.songs?.query
                        val n = fold(q ?: "")
                        val hits = if (n.isEmpty()) emptyList() else library.filter { fold(it.album).let { a -> a.isNotEmpty() && (a == n || a.contains(n)) } }
                        val best = hits.groupingBy { it.album }.eachCount().maxByOrNull { it.value }?.key
                        val songs = hits.filter { it.album == best }
                        steps += if (songs.isEmpty()) JarvisStep(c, problem = missing(q)) else JarvisStep(c, songs = songs, label = best)
                    }
                    JarvisTool.PLAY_GENRE -> {
                        val q = c.name ?: c.songs?.genre
                        val n = fold(q ?: "")
                        val songs = if (n.isEmpty()) emptyList() else library.filter { fold(it.genre ?: "").contains(n) }
                            .let { if (c.shuffle || c.songs?.shuffle == true) it.shuffled() else it }
                        steps += if (songs.isEmpty()) JarvisStep(c, problem = missing(q)) else JarvisStep(c, songs = songs, label = q)
                    }
                    JarvisTool.PLAY_PLAYLIST -> {
                        val q = c.name ?: c.playlist
                        val list = findList(ctx, q)
                        val songs = list?.ids?.mapNotNull { id -> library.firstOrNull { it.id == id } } ?: emptyList()
                        val shuffled = if (c.shuffle || c.songs?.shuffle == true) songs.shuffled() else songs
                        steps += if (list == null) JarvisStep(c, problem = if (en) "I don't see a playlist called «$q»." else if (pt) "Não vejo uma playlist chamada «$q»." else "No veo una playlist llamada «$q».")
                        else if (shuffled.isEmpty()) JarvisStep(c, problem = if (en) "«${list.name}» has no playable songs yet." else if (pt) "«${list.name}» ainda não tem músicas para tocar." else "«${list.name}» aún no tiene canciones para reproducir.")
                        else JarvisStep(c, songs = shuffled, label = list.name)
                    }
                    JarvisTool.CREATE_PLAYLIST -> {
                        val browse = c.songs?.source == "browse"
                        val r = if (browse) Resolution(emptyList()) else resolveSongs(c.songs, library, stats, favIds, nowId, null, 20)
                        android.util.Log.w("JarvisDraft", "create_playlist browse=$browse theme=${c.songs?.theme} artist=${c.songs?.artist} genre=${c.songs?.genre} source=${c.songs?.source} -> ${r.songs.size} canciones de ${library.size} en la biblioteca")
                        val name = c.name ?: c.songs?.theme ?: "MusicFlame"
                        // La tabla siempre muestra TODA la biblioteca: lo pedido va marcado y el resto sin marcar.
                        val pickedIds = r.songs.map { it.id }.toHashSet()
                        val rest = library.filter { it.id !in pickedIds }.sortedBy { it.title.lowercase() }
                        steps += if (browse && rest.isNotEmpty()) {
                            JarvisStep(c, songs = emptyList(), extra = rest, label = name)
                        } else if (r.songs.isEmpty()) {
                            val candIds = r.candidates.map { it.id }.toHashSet()
                            val all = r.candidates + rest.filter { it.id !in candIds }
                            JarvisStep(c, candidates = all, label = name, problem = if (en) "I found no songs to build «$name»." else if (pt) "Não encontrei músicas para montar «$name»." else "No encontré canciones para armar «$name».")
                        } else JarvisStep(c, songs = r.songs, extra = rest, label = name)
                    }
                    JarvisTool.ADD_TO_PLAYLIST, JarvisTool.REMOVE_FROM_PLAYLIST -> {
                        val list = findList(ctx, c.playlist ?: c.name)
                        if (list == null || list.editableId == null) {
                            steps += JarvisStep(c, problem = if (list == null)
                                (if (en) "I don't see a playlist called «${c.playlist ?: c.name}»." else if (pt) "Não vejo uma playlist chamada «${c.playlist ?: c.name}»." else "No veo una playlist llamada «${c.playlist ?: c.name}».")
                            else (if (en) "«${list.name}» can't be edited by hand." else if (pt) "«${list.name}» não pode ser editada manualmente." else "«${list.name}» no se puede editar a mano."))
                        } else {
                            val r = resolveSongs(c.songs, library, stats, favIds, nowId, list, 10)
                            val songs = if (c.tool == JarvisTool.REMOVE_FROM_PLAYLIST) r.songs.filter { it.id in list.ids } else r.songs
                            steps += if (songs.isEmpty()) JarvisStep(c, candidates = r.candidates, problem = missing(c.songs?.query ?: c.songs?.theme))
                            else JarvisStep(c, songs = songs, label = list.name, playlistId = list.editableId)
                        }
                    }
                    JarvisTool.RENAME_PLAYLIST -> {
                        val list = findList(ctx, c.playlist)
                        val newName = c.name?.trim()?.take(40)
                        steps += if (list == null) JarvisStep(c, problem = if (en) "I don't see a playlist called «${c.playlist}»." else if (pt) "Não vejo uma playlist chamada «${c.playlist}»." else "No veo una playlist llamada «${c.playlist}».")
                        else if (list.editableId == null) JarvisStep(c, problem = if (en) "«${list.name}» can't be renamed." else if (pt) "«${list.name}» não pode ser renomeada." else "«${list.name}» no se puede renombrar.")
                        else if (newName.isNullOrBlank()) JarvisStep(c, problem = if (en) "What should the new name be?" else if (pt) "Como você quer que se chame?" else "¿Cómo quieres que se llame?")
                        else JarvisStep(c, label = newName, playlistId = list.editableId)
                    }
                    JarvisTool.EQ_PRESET -> {
                        val name = com.music.musicflame.audio.EqVoice.findPreset(ctx, c.name)
                        steps += if (name == null) JarvisStep(c, problem = if (en) "I don't have an equalizer preset called «${c.name}»." else if (pt) "Não tenho um preset de equalizador chamado «${c.name}»." else "No tengo un preset de ecualizador llamado «${c.name}».")
                        else JarvisStep(c, label = name)
                    }
                    JarvisTool.EQ_BAND -> {
                        val ok = com.music.musicflame.audio.EqVoice.BAND_IDS.contains(c.name?.trim()?.lowercase())
                        steps += if (ok) JarvisStep(c) else JarvisStep(c, problem = if (en) "Which band: bass, low mids, mids, high mids or treble?" else if (pt) "Qual banda: graves, médios graves, médios, médios agudos ou agudos?" else "¿Qué banda: graves, medios graves, medios, medios agudos o agudos?")
                    }
                    JarvisTool.SONG_TITLE, JarvisTool.SONG_COVER -> {
                        // La canción objetivo es la que dijo el usuario o, si no dijo ninguna, la que suena.
                        val sel = c.songs ?: JarvisSelector(source = "now_playing")
                        val r = resolveSongs(sel, library, stats, favIds, nowId, null, 1)
                        val target = r.songs.firstOrNull()
                        if (target == null) {
                            steps += JarvisStep(c, problem = if (sel.source == "now_playing") (if (en) "Nothing is playing right now." else if (pt) "Agora não está tocando nada." else "Ahora mismo no suena nada.") else missing(sel.query))
                        } else if (c.tool == JarvisTool.SONG_COVER && c.direction != "reset") {
                            // songs = [canción a cambiar, canción de la que se copia la carátula]
                            val src = c.name?.let { AiLibraryContext.findSong(it, library) }
                            steps += if (src == null) JarvisStep(c, problem = missing(c.name))
                            else JarvisStep(c, songs = listOf(target, src))
                        } else if (c.tool == JarvisTool.SONG_TITLE && c.direction != "reset" && c.name.isNullOrBlank()) {
                            steps += JarvisStep(c, problem = if (en) "What should the new title be?" else if (pt) "Como você quer que se chame?" else "¿Cómo quieres que se llame?")
                        } else steps += JarvisStep(c, songs = listOf(target), label = c.name?.trim()?.take(80))
                    }
                    else -> steps += JarvisStep(c)   // controles del reproductor: no necesitan canciones
                }
            }
            steps
        }

    suspend fun execute(
        ctx: Context, lang: String, steps: List<JarvisStep>,
        player: MusicPlayerManager?, play: (Song, List<Song>) -> Unit
    ): JarvisOutcome {
        val en = lang == "en"
        val pt = lang == "pt"
        val lines = ArrayList<String>()
        val ids = LinkedHashSet<Long>()
        var plChanged = false

        for (s in steps) {
            if (s.problem != null) {
                lines += s.problem; ids += s.candidates.map { it.id }; continue
            }
            try {
                when (s.call.tool) {
                    JarvisTool.PLAY_SONG, JarvisTool.PLAY_ARTIST, JarvisTool.PLAY_ALBUM, JarvisTool.PLAY_GENRE,
                    JarvisTool.PLAY_PLAYLIST, JarvisTool.PLAY_SELECTION -> {
                        val first = s.songs.firstOrNull()
                        if (first == null) { lines += if (en) "Nothing to play." else if (pt) "Não há nada para tocar." else "No hay nada que reproducir."; continue }
                        withContext(Dispatchers.Main) { play(first, s.songs) }
                        lines += when (s.call.tool) {
                            JarvisTool.PLAY_SONG -> if (en) "Playing «${first.title}» by ${first.artist}" else if (pt) "Tocando «${first.title}» de ${first.artist}" else "Reproduciendo «${first.title}» de ${first.artist}"
                            else -> if (en) "Playing ${s.label ?: "your selection"} (${s.songs.size} songs)" else if (pt) "Tocando ${s.label ?: "sua seleção"} (${s.songs.size} músicas)" else "Reproduciendo ${s.label ?: "tu selección"} (${s.songs.size} canciones)"
                        }
                    }
                    JarvisTool.QUEUE_NEXT, JarvisTool.QUEUE_ADD -> {
                        val p = player
                        if (p == null) { lines += if (en) "I couldn't reach the player." else if (pt) "Não consegui acessar o player." else "No pude acceder al reproductor."; continue }
                        val idle = p.currentSong.value == null
                        withContext(Dispatchers.Main) {
                            if (idle) play(s.songs.first(), s.songs)
                            else if (s.call.tool == JarvisTool.QUEUE_NEXT) p.playNext(s.songs) else p.addToQueue(s.songs)
                        }
                        val what = if (s.songs.size == 1) "«${s.songs[0].title}»" else (if (en) "${s.songs.size} songs" else if (pt) "${s.songs.size} músicas" else "${s.songs.size} canciones")
                        lines += if (idle) (if (en) "Nothing was playing, so I started $what" else if (pt) "Não estava tocando nada, então coloquei $what" else "No sonaba nada, así que puse $what")
                        else if (s.call.tool == JarvisTool.QUEUE_NEXT) (if (en) "$what will play next" else if (pt) "$what toca em seguida" else "$what sonará después")
                        else (if (en) "Added $what to the queue" else if (pt) "Adicionei $what à fila" else "Agregué $what a la cola")
                    }
                    JarvisTool.FAVORITE, JarvisTool.UNFAVORITE -> withContext(Dispatchers.IO) {
                        val repo = FavoritesRepository(ctx)
                        val add = s.call.tool == JarvisTool.FAVORITE
                        var changed = 0
                        for (song in s.songs) {
                            if (repo.isFavorite(song.id) != add) { if (add) repo.addFavorite(song.id) else repo.removeFavorite(song.id); changed++ }
                        }
                        val same = s.songs.size - changed
                        val tail = if (same > 0) (if (en) " ($same already were)" else if (pt) " ($same já estavam assim)" else " ($same ya estaban así)") else ""
                        lines += if (add) (if (en) "Marked $changed song(s) as favorites$tail" else if (pt) "Marquei $changed música(s) como favorita(s)$tail" else "Marqué $changed canción(es) como favoritas$tail")
                        else (if (en) "Removed $changed song(s) from favorites$tail" else if (pt) "Removi $changed música(s) dos favoritos$tail" else "Quité $changed canción(es) de favoritos$tail")
                        if (s.songs.size <= 5) ids += s.songs.map { it.id }
                        Unit
                    }
                    JarvisTool.CREATE_PLAYLIST -> withContext(Dispatchers.IO) {
                        val created = PlaylistRepository(ctx).createPlaylistWithSongs(s.label ?: "MusicFlame", s.songs.map { it.id })
                        plChanged = true
                        lines += if (en) "Created «${created.name}» with ${created.songIds.size} songs" else if (pt) "Criei «${created.name}» com ${created.songIds.size} músicas" else "Creé «${created.name}» con ${created.songIds.size} canciones"
                        ids += s.songs.take(8).map { it.id }
                        Unit
                    }
                    JarvisTool.ADD_TO_PLAYLIST -> withContext(Dispatchers.IO) {
                        val repo = PlaylistRepository(ctx)
                        s.songs.forEach { repo.addSongToPlaylist(s.playlistId!!, it.id) }
                        plChanged = true
                        lines += if (en) "Added ${s.songs.size} song(s) to «${s.label}»" else if (pt) "Adicionei ${s.songs.size} música(s) a «${s.label}»" else "Agregué ${s.songs.size} canción(es) a «${s.label}»"
                        Unit
                    }
                    JarvisTool.REMOVE_FROM_PLAYLIST -> withContext(Dispatchers.IO) {
                        PlaylistRepository(ctx).removeSongsFromPlaylist(s.playlistId!!, s.songs.map { it.id }.toSet())
                        plChanged = true
                        lines += if (en) "Removed ${s.songs.size} song(s) from «${s.label}» (the files stay untouched)" else if (pt) "Removi ${s.songs.size} música(s) de «${s.label}» (os arquivos não são mexidos)" else "Quité ${s.songs.size} canción(es) de «${s.label}» (los archivos no se tocan)"
                        Unit
                    }
                    JarvisTool.PAUSE, JarvisTool.RESUME, JarvisTool.NEXT, JarvisTool.PREVIOUS,
                    JarvisTool.SLEEP_TIMER, JarvisTool.CANCEL_SLEEP_TIMER -> {
                        val p = player
                        if (p == null) { lines += if (en) "I couldn't reach the player." else if (pt) "Não consegui acessar o player." else "No pude acceder al reproductor."; continue }
                        withContext(Dispatchers.Main) {
                            when (s.call.tool) {
                                JarvisTool.PAUSE -> { p.pause(); lines += if (en) "Paused" else if (pt) "Em pausa" else "En pausa" }
                                JarvisTool.RESUME -> {
                                    if (p.currentSong.value == null) lines += if (en) "Nothing is loaded yet. Tell me what to play." else if (pt) "Ainda não há nada carregado. Diga o que tocar." else "Aún no hay nada cargado. Dime qué reproducir."
                                    else { if (!p.isPlayingState.value) p.togglePlayPause(); lines += if (en) "Playing" else if (pt) "Tocando" else "Reproduciendo" }
                                }
                                JarvisTool.NEXT -> { p.skipNext(); lines += if (en) "Next song" else if (pt) "Próxima música" else "Siguiente canción" }
                                JarvisTool.PREVIOUS -> { p.skipPrevious(); lines += if (en) "Previous song" else if (pt) "Música anterior" else "Canción anterior" }
                                JarvisTool.SLEEP_TIMER -> {
                                    val m = (s.call.value ?: 0).coerceIn(1, 600)
                                    p.startSleepTimer(m)
                                    lines += if (en) "I'll stop the music in $m min" else if (pt) "Vou desligar em $m min" else "Me apago en $m min"
                                }
                                else -> { p.cancelSleepTimer(); lines += if (en) "Sleep timer cancelled" else if (pt) "Timer cancelado" else "Temporizador cancelado" }
                            }
                            Unit
                        }
                    }
                    JarvisTool.RENAME_PLAYLIST -> withContext(Dispatchers.IO) {
                        PlaylistRepository(ctx).renamePlaylist(s.playlistId!!, s.label!!)
                        plChanged = true
                        lines += if (en) "Renamed it to «${s.label}»" else if (pt) "Renomeei para «${s.label}»" else "La renombré a «${s.label}»"
                        Unit
                    }
                    JarvisTool.REPEAT, JarvisTool.SHUFFLE_MODE -> {
                        val p = player
                        if (p == null) { lines += if (en) "I couldn't reach the player." else if (pt) "Não consegui acessar o player." else "No pude acceder al reproductor."; continue }
                        withContext(Dispatchers.Main) {
                            val cur = p.cycleMode.value
                            var shuffle = cur == PlaybackCycle.SHUFFLE || cur == PlaybackCycle.SHUFFLE_REPEAT_ALL
                            var rep = when (cur) {
                                PlaybackCycle.REPEAT_ALL, PlaybackCycle.SHUFFLE_REPEAT_ALL -> "all"
                                PlaybackCycle.REPEAT_ONE -> "one"
                                else -> "off"
                            }
                            if (s.call.tool == JarvisTool.SHUFFLE_MODE) shuffle = s.call.direction != "off"
                            else rep = when (s.call.direction) { "one" -> "one"; "off" -> "off"; else -> "all" }
                            val target = when {
                                rep == "one" -> PlaybackCycle.REPEAT_ONE
                                rep == "all" && shuffle -> PlaybackCycle.SHUFFLE_REPEAT_ALL
                                rep == "all" -> PlaybackCycle.REPEAT_ALL
                                shuffle -> PlaybackCycle.SHUFFLE
                                else -> PlaybackCycle.NORMAL
                            }
                            p.setCycleMode(target)
                            lines += if (s.call.tool == JarvisTool.SHUFFLE_MODE) (if (shuffle) (if (en) "Shuffle on" else if (pt) "Aleatório ativado" else "Mezclar activado") else (if (en) "Shuffle off" else if (pt) "Aleatório desativado" else "Mezclar desactivado"))
                            else when (rep) {
                                "one" -> if (en) "Repeating this song" else if (pt) "Repetindo esta música" else "Repitiendo esta canción"
                                "all" -> if (en) "Repeating the list" else if (pt) "Repetindo a lista" else "Repitiendo la lista"
                                else -> if (en) "Repeat off" else if (pt) "Repetição desativada" else "Repetir desactivado"
                            }
                            Unit
                        }
                    }
                    JarvisTool.EQ_PRESET -> {
                        com.music.musicflame.audio.EqVoice.applyPreset(ctx, s.label)
                        lines += if (en) "Equalizer set to ${s.label}" else if (pt) "Equalizador em ${s.label}" else "Ecualizador en ${s.label}"
                    }
                    JarvisTool.EQ_BAND -> {
                        val up = s.call.direction != "down"
                        com.music.musicflame.audio.EqVoice.nudgeBand(ctx, s.call.name, up)
                        lines += if (en) "${if (up) "Raised" else "Lowered"} the ${s.call.name?.replace('_', ' ')} band" else if (pt) "${if (up) "Aumentei" else "Diminuí"} a banda ${s.call.name?.replace('_', ' ')}" else "${if (up) "Subí" else "Bajé"} la banda ${s.call.name?.replace('_', ' ')}"
                    }
                    JarvisTool.SONG_TITLE -> withContext(Dispatchers.IO) {
                        val song = s.songs[0]
                        val repo = SongCustomizationRepository(ctx)
                        if (s.call.direction == "reset") {
                            repo.setCustomization(song.id, clearTitle = true)
                            lines += if (en) "Restored the original title of «${song.title}»" else if (pt) "Restaurei o título original de «${song.title}»" else "Restauré el título original de «${song.title}»"
                        } else {
                            repo.setCustomization(song.id, title = s.label)
                            lines += if (en) "Renamed «${song.title}» to «${s.label}»" else if (pt) "Renomeei «${song.title}» para «${s.label}»" else "Renombré «${song.title}» a «${s.label}»"
                        }
                        Unit
                    }
                    JarvisTool.SONG_COVER -> withContext(Dispatchers.IO) {
                        val song = s.songs[0]
                        val repo = SongCustomizationRepository(ctx)
                        val srcCover = s.songs.getOrNull(1)?.albumArtUri
                        if (s.call.direction == "reset") {
                            repo.setCustomization(song.id, clearCover = true)
                            lines += if (en) "Restored the original cover of «${song.title}»" else if (pt) "Restaurei a capa original de «${song.title}»" else "Restauré la carátula original de «${song.title}»"
                        } else if (srcCover.isNullOrBlank()) {
                            lines += if (en) "«${s.songs[1].title}» has no cover to copy." else if (pt) "«${s.songs[1].title}» não tem capa para copiar." else "«${s.songs[1].title}» no tiene carátula para copiar."
                        } else {
                            repo.setCustomization(song.id, coverUri = srcCover)
                            lines += if (en) "Gave «${song.title}» the cover of «${s.songs[1].title}»" else if (pt) "Coloquei em «${song.title}» a capa de «${s.songs[1].title}»" else "Le puse a «${song.title}» la carátula de «${s.songs[1].title}»"
                        }
                        Unit
                    }
                    JarvisTool.TOGETHER_CREATE, JarvisTool.TOGETHER_INVITE -> { /* los resuelve la pantalla de voz */ }
                    JarvisTool.VOLUME -> {
                        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                        val cur = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                        val target = when {
                            s.call.value != null -> (max * s.call.value.coerceIn(0, 100) / 100f).toInt()
                            s.call.direction == "down" -> cur - (max / 8).coerceAtLeast(1)
                            else -> cur + (max / 8).coerceAtLeast(1)
                        }.coerceIn(0, max)
                        am.setStreamVolume(AudioManager.STREAM_MUSIC, target, AudioManager.FLAG_SHOW_UI)
                        lines += if (en) "Volume at ${target * 100 / max}%" else if (pt) "Volume em ${target * 100 / max}%" else "Volumen al ${target * 100 / max}%"
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                lines += if (en) "Something failed with that action (${e.javaClass.simpleName})." else if (pt) "Algo deu errado com essa ação (${e.javaClass.simpleName})." else "Algo falló con esa acción (${e.javaClass.simpleName})."
            }
        }
        return JarvisOutcome(lines, ids.toList(), plChanged)
    }
}
