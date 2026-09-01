package com.music.musicflame.data

/**
 * Catálogo de formatos de audio para el botón "Formatos de audio a escuchar"
 * de Ajustes > Canciones.
 *
 * "usable" indica si el formato es un audio reproducible de forma confiable
 * dentro de la app. El único caso marcado como NO usable es .aac "crudo"
 * (stream elemental, sin contenedor MP4): es el mismo caso ya documentado
 * como limitación en RealTagWriter (ver su comentario de cabecera) — no hay
 * un contenedor estándar donde ubicar los datos, así que por la misma razón
 * de fondo tampoco se puede tratar como un formato de audio normal dentro de
 * la librería.
 *
 * .m3u es un caso aparte: no es un formato de AUDIO individual, es un
 * formato de LISTA DE REPRODUCCIÓN (ver PlaylistRepository.importFromM3U /
 * exportToM3U). Se muestra en el selector solo a modo informativo — no tiene
 * checkbox propio porque no participa del filtro de la librería de
 * canciones (SongRepository.loadSongsFromDevice), solo de Playlists.
 */
data class AudioFormatInfo(
    val extension: String,       // "mp3", "flac", ... siempre en minúsculas, es la "clave" del formato
    val displayName: String,     // ".mp3", ".flac", ... para mostrar en la UI
    val usable: Boolean,         // true = se reproduce bien dentro de la app
    val isPlaylistFormat: Boolean = false, // true SOLO para .m3u
    val note: String? = null     // aclaración opcional mostrada bajo el nombre del formato
)

object AudioFormatCatalog {

    // Extensiones "hermanas" que MediaStore puede reportar para el mismo
    // formato base y que tratamos como un solo renglón en el selector (ej.
    // un .m4b se agrupa con .m4a, un .oga con .ogg, un .aif con .aiff).
    private val EXTENSION_ALIASES: Map<String, String> = mapOf(
        "oga" to "ogg",
        "m4b" to "m4a",
        "aif" to "aiff"
    )

    /** Normaliza una extensión de archivo (se recomienda ya en minúsculas) a su formato "canónico" del catálogo. */
    fun canonicalExtension(rawExtension: String): String {
        val ext = rawExtension.lowercase()
        return EXTENSION_ALIASES[ext] ?: ext
    }

    // Mismos 9 formatos "usables" ya documentados en RealTagWriter (los que
    // la librería de re-etiquetado sabe manejar), más .aac crudo (no usable)
    // y .m3u (formato de Playlist, no de canción).
    val ALL_FORMATS: List<AudioFormatInfo> = listOf(
        AudioFormatInfo("mp3", ".mp3", usable = true),
        AudioFormatInfo("flac", ".flac", usable = true),
        AudioFormatInfo("ogg", ".ogg", usable = true),
        AudioFormatInfo("wav", ".wav", usable = true),
        AudioFormatInfo("m4a", ".m4a", usable = true),
        AudioFormatInfo("wma", ".wma", usable = true),
        AudioFormatInfo("aiff", ".aiff", usable = true),
        AudioFormatInfo("dsf", ".dsf", usable = true),
        AudioFormatInfo("opus", ".opus", usable = true),
        AudioFormatInfo(
            extension = "aac",
            displayName = ".aac",
            usable = false,
            note = "Crudo (sin contenedor): no soportado dentro de la app, misma limitación ya documentada para el guardado de etiquetas reales"
        ),
        AudioFormatInfo(
            extension = "m3u",
            displayName = ".m3u",
            usable = true,
            isPlaylistFormat = true,
            note = "Formato de Playlist, no de canción individual — no forma parte de tu biblioteca de canciones"
        )
    )

    private val FORMATS_BY_EXTENSION: Map<String, AudioFormatInfo> = ALL_FORMATS.associateBy { it.extension }

    fun infoFor(extension: String): AudioFormatInfo? = FORMATS_BY_EXTENSION[canonicalExtension(extension)]

    /** Formatos no usables (ej. .aac crudo) que se ocultan de la librería por defecto la primera vez, hasta que el usuario decida lo contrario. */
    val DEFAULT_HIDDEN_EXTENSIONS: Set<String> = ALL_FORMATS.filter { !it.usable }.map { it.extension }.toSet()
}
