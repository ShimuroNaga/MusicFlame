package com.music.musicflame.voice

import android.content.Context
import com.music.musicflame.data.SongLibraryHolder
import com.music.musicflame.data.StatsRepository
import org.json.JSONArray
import java.io.File

/**
 * Memoria compacta del asistente de voz. Dos partes, ambas pequeñas a propósito (la cuota gratuita de Groq
 * es corta):
 *  - Estadísticas CALCULADAS EN EL TELÉFONO (más escuchadas, minutos). A Groq solo salen NOMBRES, nunca rutas.
 *  - Las últimas frases de conversaciones anteriores, guardadas en un archivo local que no entra en la
 *    copia de seguridad de la configuración.
 */
object VoiceMemory {
    private const val FILE = "voice_memory.json"
    private const val MAX_LINES = 6
    private const val MAX_LINE_CHARS = 90

    private fun file(ctx: Context) = File(ctx.applicationContext.filesDir, FILE)

    fun loadLines(ctx: Context): List<String> = try {
        val f = file(ctx)
        if (!f.isFile) emptyList() else JSONArray(f.readText()).let { a -> (0 until a.length()).map { a.getString(it) } }
    } catch (_: Exception) { emptyList() }

    /** Guarda un intercambio como una línea corta. Llamar desde un hilo de fondo. */
    fun remember(ctx: Context, userSaid: String, assistantSaid: String) {
        if (userSaid.isBlank()) return
        try {
            val line = ("${userSaid.trim()} → ${assistantSaid.trim()}").replace('\n', ' ').take(MAX_LINE_CHARS)
            val all = (loadLines(ctx) + line).takeLast(MAX_LINES)
            file(ctx).writeText(JSONArray(all).toString())
        } catch (_: Exception) { /* la memoria es best-effort */ }
    }

    fun clear(ctx: Context) { try { file(ctx).delete() } catch (_: Exception) {} }

    /** Resumen de estadísticas (solo nombres y números). Requiere la biblioteca cargada; hilo de fondo. */
    private fun statsSummary(ctx: Context, en: Boolean): String {
        val stats = StatsRepository(ctx).getAllStats().filter { it.value.playCount > 0 }
        if (stats.isEmpty()) return ""
        val byId = SongLibraryHolder.songs.associateBy { it.id }
        val topSongs = stats.entries.sortedByDescending { it.value.playCount }
            .mapNotNull { e -> byId[e.key]?.let { "«${it.title.take(40)}» (${it.artist.take(30)})" } }.take(3)
        val unknown = setOf("", "desconocido", "unknown", "<unknown>")
        val topArtists = stats.entries.mapNotNull { e -> byId[e.key]?.artist?.let { it to e.value.playCount } }
            .filter { it.first.trim().lowercase() !in unknown }
            .groupBy({ it.first }, { it.second }).mapValues { it.value.sum() }
            .entries.sortedByDescending { it.value }.take(3).map { it.key.take(30) }
        val minutes = stats.values.sumOf { it.totalListenedMs } / 60_000L
        return buildString {
            if (topArtists.isNotEmpty()) append(if (en) "Top artists: " else "Artistas más escuchados: ").append(topArtists.joinToString(", ")).append(". ")
            if (topSongs.isNotEmpty()) append(if (en) "Top songs: " else "Canciones más escuchadas: ").append(topSongs.joinToString(", ")).append(". ")
            append(if (en) "Total listening: ~$minutes min." else "Tiempo total escuchado: ~$minutes min.")
        }
    }

    /** Todo lo que el modelo "recuerda" del usuario, listo para el prompt ("" si no hay nada). */
    fun build(ctx: Context, lang: String): String {
        val en = lang == "en"
        val parts = ArrayList<String>()
        try { statsSummary(ctx, en).takeIf { it.isNotBlank() }?.let { parts += it } } catch (_: Exception) {}
        val lines = loadLines(ctx)
        if (lines.isNotEmpty())
            parts += (if (en) "Recent past exchanges (user → you): " else "Intercambios recientes anteriores (usuario → tú): ") + lines.joinToString(" | ")
        return parts.joinToString("\n")
    }
}
