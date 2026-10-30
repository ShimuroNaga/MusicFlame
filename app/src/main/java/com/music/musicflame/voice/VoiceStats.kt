package com.music.musicflame.voice

import android.content.Context
import com.music.musicflame.data.Song
import com.music.musicflame.data.SongLibraryHolder
import com.music.musicflame.data.StatsRepository

/**
 * Estadísticas habladas. Las calcula la app con tus datos reales; el modelo solo elige qué dato quieres
 * ([KINDS]) y nunca ve ni inventa los números. Llamar desde un hilo de fondo con la biblioteca cargada.
 */
object VoiceStats {
    val KINDS = setOf("now_playing", "top_song", "top_artist", "minutes", "wrapped")

    private val UNKNOWN = setOf("", "desconocido", "unknown", "<unknown>")

    fun answer(ctx: Context, kind: String, lang: String, nowPlaying: Song?): String {
        val en = lang == "en"
        if (kind == "now_playing") {
            return if (nowPlaying == null) (if (en) "Nothing is playing right now." else "Ahora mismo no suena nada.")
            else if (en) "Now playing «${nowPlaying.title}» by ${nowPlaying.artist}." else "Suena «${nowPlaying.title}» de ${nowPlaying.artist}."
        }
        val stats = StatsRepository(ctx).getAllStats().filter { it.value.playCount > 0 }
        if (stats.isEmpty()) return if (en) "I don't have enough listening history yet." else "Aún no tengo suficiente historial de escucha."
        val byId = SongLibraryHolder.songs.associateBy { it.id }
        val topSong = stats.entries.sortedByDescending { it.value.playCount }
            .firstNotNullOfOrNull { e -> byId[e.key]?.let { it to e.value.playCount } }
        val topArtist = stats.entries.mapNotNull { e -> byId[e.key]?.artist?.let { it to e.value.playCount } }
            .filter { it.first.trim().lowercase() !in UNKNOWN }
            .groupBy({ it.first }, { it.second }).mapValues { it.value.sum() }
            .maxByOrNull { it.value }
        val minutes = stats.values.sumOf { it.totalListenedMs } / 60_000L
        val time = if (minutes >= 120) (if (en) "${minutes / 60} hours" else "${minutes / 60} horas") else (if (en) "$minutes minutes" else "$minutes minutos")

        val song = topSong?.let { if (en) "your most played song is «${it.first.title}» with ${it.second} plays" else "tu canción más escuchada es «${it.first.title}» con ${it.second} reproducciones" }
        val artist = topArtist?.let { if (en) "your top artist is ${it.key}" else "tu artista más escuchado es ${it.key}" }
        val total = if (en) "you've listened for about $time" else "llevas unos $time escuchando"
        return when (kind) {
            "top_song" -> song?.replaceFirstChar { it.uppercase() }?.plus(".") ?: (if (en) "I couldn't find a top song." else "No encontré una canción top.")
            "top_artist" -> artist?.replaceFirstChar { it.uppercase() }?.plus(".") ?: (if (en) "I couldn't find a top artist." else "No encontré un artista top.")
            "minutes" -> total.replaceFirstChar { it.uppercase() } + "."
            else -> listOfNotNull(song, artist, total).joinToString(if (en) "; " else "; ").replaceFirstChar { it.uppercase() } + "."
        }
    }
}
