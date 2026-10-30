package com.music.musicflame.voice

import android.content.Context
import com.music.musicflame.data.OnlineLlm
import com.music.musicflame.data.PlaylistKind
import org.json.JSONObject
import java.text.Normalizer

enum class VoiceControl { PAUSE, RESUME, NEXT, PREVIOUS }

/** Playlist que el asistente puede abrir: tus playlists, Favoritas y las inteligentes (Más/Nunca escuchadas). */
data class VoicePlaylist(val id: String, val name: String, val kind: PlaylistKind)

/** Lo que el asistente decidió hacer. [listenAgain] = seguir escuchando después de hablar (conversación). */
sealed class VoiceAction {
    abstract val say: String
    abstract val listenAgain: Boolean

    data class PlayPlaylist(val playlist: VoicePlaylist, val shuffle: Boolean, override val say: String) : VoiceAction() { override val listenAgain = false }
    /** [query] = lo que dijo el usuario ("título" o "título artista"); la app lo busca en la biblioteca REAL. */
    data class PlaySong(val query: String, override val say: String) : VoiceAction() { override val listenAgain = false }
    data class Control(val kind: VoiceControl, override val say: String) : VoiceAction() { override val listenAgain = false }
    data class Chat(override val say: String, override val listenAgain: Boolean = true) : VoiceAction()
    data class End(override val say: String) : VoiceAction() { override val listenAgain = false }
}

/** Datos que el asistente necesita para hablar "con contexto". */
class VoiceContext(
    val aiName: String,
    val userName: String,
    val lang: String,
    val hour: Int,
    val nowPlaying: String?,
    val playlists: List<VoicePlaylist>
)

/**
 * "Cerebro" del asistente. Personalidad cercana y con chispa (estilo mayordomo digital), habla con tu nombre,
 * conversa y obedece órdenes. Los nombres de playlists y canciones los valida SIEMPRE la app contra tu
 * biblioteca: la IA solo elige, nunca inventa. Sin key de Groq o sin internet, [local] cubre lo básico.
 */
object VoiceBrain {
    private val RE_MARKS = Regex("\\p{M}+")
    fun fold(s: String): String =
        RE_MARKS.replace(Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD), "").filter { it.isLetterOrDigit() || it == ' ' }.trim()

    suspend fun decide(ctx: Context, vc: VoiceContext, dialog: List<Pair<Boolean, String>>): VoiceAction {
        val en = vc.lang == "en"
        val names = vc.playlists.take(80).joinToString("\n") { "- " + it.name.take(60) }
        val who = if (vc.userName.isNotBlank()) vc.userName else if (en) "the user" else "el usuario"
        val moment = when (vc.hour) { in 5..11 -> if (en) "morning" else "mañana"; in 12..18 -> if (en) "afternoon" else "tarde"; else -> if (en) "night" else "noche" }
        val np = vc.nowPlaying ?: (if (en) "nothing" else "nada")
        val system = if (en)
            "You are ${vc.aiName}, the living voice assistant of the MusicFlame music player, like a witty, warm digital butler. Your user is $who; it's $moment. " +
            "Talk like a person: natural, a bit playful, curious, never robotic. Use their name now and then (not every sentence). You can chat about music, answer questions and keep the conversation going, AND you obey music commands. " +
            "The text comes from speech recognition and may have mistakes. Currently playing: $np.\n" +
            "Reply ONLY with one JSON object (no Markdown): {\"action\":\"chat|play_playlist|play_song|pause|resume|next|previous|end\",\"playlist\":\"\",\"song\":\"\",\"shuffle\":false,\"say\":\"\",\"listen\":true}\n" +
            "- say: what you speak aloud, in English, 1-3 short sentences (max ~35 words). For direct commands keep it very short (max 8 words).\n" +
            "- play_playlist: 'playlist' must be EXACTLY one of the names below. play_song: 'song' = the title as the user said it, plus the artist if they said it. Never invent playlists.\n" +
            "- If the user wants music but is vague, don't just ask 'which playlist?': suggest 2-3 options with personality (from their playlists below) and ask, with action chat and listen true. If they ask what playlists they have, name them (the first 4-5) in 'say'.\n" +
            "- listen: true if you asked something or the chat should continue; false after executing a command. Use end (listen false) when they say thanks/bye.\n" +
            "User's playlists (includes defaults):\n$names"
        else
            "Eres ${vc.aiName}, el asistente de voz con vida del reproductor MusicFlame: como un mayordomo digital cálido, ingenioso y cercano. Tu usuario es $who; es de $moment. " +
            "Habla como una persona: natural, algo juguetón, curioso, nunca robótico. Usa su nombre de vez en cuando (no en cada frase). Puedes platicar de música, responder preguntas y mantener la conversación, Y obedeces órdenes de música. " +
            "El texto viene de reconocimiento de voz y puede tener errores. Sonando ahora: $np.\n" +
            "Responde SOLO con un objeto JSON (sin Markdown): {\"action\":\"chat|play_playlist|play_song|pause|resume|next|previous|end\",\"playlist\":\"\",\"song\":\"\",\"shuffle\":false,\"say\":\"\",\"listen\":true}\n" +
            "- say: lo que dices en voz alta, en español, 1-3 frases cortas (máx. ~35 palabras). En órdenes directas sé muy breve (máx. 8 palabras).\n" +
            "- play_playlist: 'playlist' debe ser EXACTAMENTE uno de los nombres de abajo. play_song: 'song' = el título como lo dijo el usuario, más el artista si lo dijo. Nunca inventes playlists.\n" +
            "- Si quiere música pero es vago, no te limites a preguntar '¿qué playlist?': propón 2 o 3 opciones con personalidad (de sus playlists de abajo) y pregunta, con action chat y listen true. Si pregunta qué playlists tiene, nómbralas (las primeras 4 o 5) en 'say'.\n" +
            "- listen: true si preguntaste algo o la charla sigue; false después de ejecutar una orden. Usa end (listen false) cuando se despida o agradezca.\n" +
            "Playlists del usuario (incluye las predeterminadas):\n$names"
        val user = dialog.takeLast(14).joinToString("\n") { (fromUser, t) ->
            (if (fromUser) (if (en) "User: " else "Usuario: ") else (if (en) "Assistant: " else "Asistente: ")) + t.take(400)
        }
        val raw = OnlineLlm.command(ctx, system, user, maxTokens = 800, temperature = 0.7)
        return parse(raw, vc) ?: throw IllegalStateException("respuesta no válida")
    }

    private fun parse(raw: String, vc: VoiceContext): VoiceAction? {
        val en = vc.lang == "en"
        val a = raw.indexOf('{'); val b = raw.lastIndexOf('}')
        val o = if (a >= 0 && b > a) try { JSONObject(raw.substring(a, b + 1)) } catch (_: Exception) { null } else null
        if (o == null) {
            // El modelo contestó en texto plano: sigue siendo una respuesta válida de charla.
            val t = raw.trim().take(300)
            return if (t.isNotEmpty()) VoiceAction.Chat(t) else null
        }
        val say = o.optString("say", "").trim()
        val listen = o.optBoolean("listen", false)
        return when (o.optString("action", "chat").lowercase()) {
            "play_playlist", "play" -> {
                val pl = findPlaylist(o.optString("playlist", ""), vc.playlists)
                if (pl != null) VoiceAction.PlayPlaylist(pl, o.optBoolean("shuffle", false), say.ifBlank { if (en) "On it." else "Enseguida." })
                else VoiceAction.Chat(if (en) "I couldn't find that playlist. Which one did you mean?" else "No encontré esa playlist. ¿Cuál era?")
            }
            "play_song" -> {
                val q = o.optString("song", "").trim()
                if (q.isNotEmpty()) VoiceAction.PlaySong(q, say.ifBlank { if (en) "Coming right up." else "Va." })
                else VoiceAction.Chat(if (en) "Which song?" else "¿Cuál canción?")
            }
            "pause" -> VoiceAction.Control(VoiceControl.PAUSE, say)
            "resume" -> VoiceAction.Control(VoiceControl.RESUME, say)
            "next" -> VoiceAction.Control(VoiceControl.NEXT, say)
            "previous" -> VoiceAction.Control(VoiceControl.PREVIOUS, say)
            "end" -> VoiceAction.End(say)
            else -> VoiceAction.Chat(say.ifBlank { if (en) "Go on, I'm listening." else "Dime, te escucho." }, listen || say.endsWith("?"))
        }
    }

    /** Empareja un nombre dicho/escrito con una playlist real: exacto, luego contenido, luego palabras en común. */
    fun findPlaylist(query: String, playlists: List<VoicePlaylist>): VoicePlaylist? {
        val q = fold(query)
        if (q.isEmpty()) return null
        playlists.firstOrNull { fold(it.name) == q }?.let { return it }
        playlists.filter { fold(it.name).let { n -> n.isNotEmpty() && (q.contains(n) || n.contains(q)) } }
            .maxByOrNull { fold(it.name).length }?.let { return it }
        val qWords = q.split(' ').filter { it.length > 2 }.toSet()
        if (qWords.isEmpty()) return null
        return playlists
            .map { p -> p to fold(p.name).split(' ').count { it in qWords } }
            .filter { it.second > 0 }.maxByOrNull { it.second }?.first
    }

    private val VERBS = Regex("^(pon(me|er)?|reproduce|reproducir|play|quiero escuchar|quiero oir|escuchar|toca|ponla|ponlo)\\s+(la cancion|la rola|el tema|the song)?\\s*")

    /** Respaldo sin IA: palabras clave + nombre de playlist o canción. */
    fun local(text: String, vc: VoiceContext): VoiceAction {
        val en = vc.lang == "en"
        val t = fold(text)
        fun has(vararg w: String) = w.any { t.contains(it) }
        val name = if (vc.userName.isNotBlank()) ", ${vc.userName}" else ""
        return when {
            has("gracias", "adios", "hasta luego", "thanks", "bye", "that's all") -> VoiceAction.End(if (en) "Anytime$name." else "Para eso estoy$name.")
            has("pausa", "pausar", "detente", "para la musica", "pause", "stop") -> VoiceAction.Control(VoiceControl.PAUSE, if (en) "Paused." else "Pausado.")
            has("siguiente", "next", "salta", "skip") -> VoiceAction.Control(VoiceControl.NEXT, if (en) "Next one." else "Siguiente.")
            has("anterior", "previous", "atras", "go back") -> VoiceAction.Control(VoiceControl.PREVIOUS, if (en) "Going back." else "Anterior.")
            has("continua", "reanuda", "resume", "continue") -> VoiceAction.Control(VoiceControl.RESUME, if (en) "Resuming." else "Continuando.")
            has("que playlists", "mis playlists", "what playlists", "my playlists") -> {
                val list = vc.playlists.take(5).joinToString(", ") { it.name }
                VoiceAction.Chat(if (en) "You have: $list. Which one?" else "Tienes: $list. ¿Cuál te pongo?")
            }
            else -> {
                val pl = findPlaylist(text, vc.playlists)
                val shuffle = has("aleatori", "mezcla", "shuffle", "random")
                val rest = VERBS.replace(t, "").trim()
                when {
                    pl != null -> VoiceAction.PlayPlaylist(pl, shuffle, if (en) "Playing ${pl.name}." else "Reproduciendo ${pl.name}.")
                    VERBS.containsMatchIn(t) && rest.length >= 2 && !rest.matches(Regex("(musica|something|algo|una playlist|playlist|lista)")) ->
                        VoiceAction.PlaySong(rest, if (en) "Looking for it." else "Buscándola.")
                    has("pon", "reproduce", "musica", "play", "playlist", "lista", "escuchar", "listen") -> {
                        val opts = vc.playlists.take(3).joinToString(", ") { it.name }
                        VoiceAction.Chat(if (en) "Sure$name. Want $opts, or a specific song?" else "Claro$name. ¿Te pongo $opts, o alguna canción en especial?")
                    }
                    else -> VoiceAction.Chat(if (en) "I didn't catch that$name." else "No te entendí bien$name.")
                }
            }
        }
    }
}
