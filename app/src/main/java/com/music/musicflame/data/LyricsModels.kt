package com.music.musicflame.data

/**
 * Una línea de letra con su marca de tiempo (en milisegundos) si se conoce.
 * timeMs == null significa que la letra no está sincronizada (texto plano),
 * y por lo tanto se muestra completa, sin resaltado por línea.
 */
data class LyricLine(
    val timeMs: Long?,
    val text: String
)

data class ParsedLyrics(
    val lines: List<LyricLine>,
    val isSynced: Boolean
) {
    companion object {
        val EMPTY = ParsedLyrics(emptyList(), isSynced = false)
    }

    /** Índice de la línea activa para una posición de reproducción dada. */
    fun activeIndex(positionMs: Long): Int {
        if (!isSynced || lines.isEmpty()) return -1
        var result = -1
        for (i in lines.indices) {
            val t = lines[i].timeMs ?: continue
            if (t <= positionMs) result = i else break
        }
        return result
    }
}

/**
 * Parser de letras: soporta formato LRC estándar ("[mm:ss.xx] texto", con
 * múltiples marcas de tiempo por línea permitidas) y cae de forma segura a
 * texto plano si no detecta marcas de tiempo válidas.
 */
object LyricsParser {

    private val LRC_TAG_REGEX = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")

    fun parse(raw: String): ParsedLyrics {
        if (raw.isBlank()) return ParsedLyrics.EMPTY

        val rawLines = raw.lines()
        val timed = mutableListOf<LyricLine>()
        var foundAnyTimestamp = false

        for (rawLine in rawLines) {
            val matches = LRC_TAG_REGEX.findAll(rawLine).toList()
            if (matches.isEmpty()) {
                val text = rawLine.trim()
                if (text.isNotEmpty() && !text.startsWith("[")) {
                    // Línea sin marca de tiempo dentro de un LRC: se conserva sin tiempo.
                    timed.add(LyricLine(timeMs = null, text = text))
                }
                continue
            }
            foundAnyTimestamp = true
            val text = rawLine.substring(matches.last().range.last + 1).trim()
            matches.forEach { m ->
                val min = m.groupValues[1].toLongOrNull() ?: 0L
                val sec = m.groupValues[2].toLongOrNull() ?: 0L
                val fracRaw = m.groupValues[3]
                val fracMs = when (fracRaw.length) {
                    0 -> 0L
                    1 -> fracRaw.toLong() * 100L
                    2 -> fracRaw.toLong() * 10L
                    else -> fracRaw.take(3).toLong()
                }
                val timeMs = (min * 60_000L) + (sec * 1000L) + fracMs
                if (text.isNotEmpty()) {
                    timed.add(LyricLine(timeMs = timeMs, text = text))
                }
            }
        }

        if (!foundAnyTimestamp) {
            // Texto plano: cada línea no vacía es una línea de letra sin tiempo.
            val plain = rawLines.map { it.trim() }.filter { it.isNotEmpty() }
                .map { LyricLine(timeMs = null, text = it) }
            return ParsedLyrics(lines = plain, isSynced = false)
        }

        val sorted = timed.filter { it.timeMs != null }.sortedBy { it.timeMs }
        return ParsedLyrics(lines = sorted, isSynced = true)
    }
}
