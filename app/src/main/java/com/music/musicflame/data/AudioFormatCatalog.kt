package com.music.musicflame.data

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
        "aif" to "aiff",
        "mid" to "midi",
        "weba" to "webm",
        "3ga" to "3gp",
        "ec3" to "eac3",
        "m3u8" to "m3u"
    )

    /** Normaliza una extensión de archivo (se recomienda ya en minúsculas) a su formato "canónico" del catálogo. */
    fun canonicalExtension(rawExtension: String): String {
        val ext = rawExtension.lowercase()
        return EXTENSION_ALIASES[ext] ?: ext
    }

    val ALL_FORMATS: List<AudioFormatInfo> = listOf(
        // --- Usables: los 9 ya documentados en RealTagWriter ---
        AudioFormatInfo("mp3", ".mp3", usable = true),
        AudioFormatInfo("flac", ".flac", usable = true),
        AudioFormatInfo("ogg", ".ogg", usable = true),
        AudioFormatInfo("wav", ".wav", usable = true),
        AudioFormatInfo("m4a", ".m4a", usable = true),
        AudioFormatInfo("wma", ".wma", usable = true),
        AudioFormatInfo("aiff", ".aiff", usable = true),
        AudioFormatInfo("dsf", ".dsf", usable = true),
        AudioFormatInfo("opus", ".opus", usable = true),

        // --- Usables adicionales: otros contenedores que ExoPlayer sí decodifica ---
        AudioFormatInfo(
            extension = "mp4", displayName = ".mp4", usable = true,
            note = "Cuando el archivo solo contiene audio (sin video)"
        ),
        AudioFormatInfo(
            extension = "3gp", displayName = ".3gp", usable = true,
            note = "Contenedor 3GPP con audio AMR o AAC"
        ),
        AudioFormatInfo("amr", ".amr", usable = true),
        AudioFormatInfo(
            extension = "webm", displayName = ".webm", usable = true,
            note = "Cuando lleva audio Vorbis u Opus"
        ),
        AudioFormatInfo(
            extension = "mka", displayName = ".mka", usable = true,
            note = "Matroska de solo audio"
        ),
        AudioFormatInfo("ac3", ".ac3", usable = true),
        AudioFormatInfo("eac3", ".eac3", usable = true),

        // --- No usables: formatos de audio real, pero sin decodificador nativo en la app ---
        AudioFormatInfo(
            extension = "aac", displayName = ".aac", usable = false,
            note = "Crudo (sin contenedor): no soportado dentro de la app, misma limitación ya documentada para el guardado de etiquetas reales"
        ),
        AudioFormatInfo(
            extension = "ape", displayName = ".ape", usable = false,
            note = "Monkey's Audio: compresión sin pérdida propietaria, sin decodificador nativo en el reproductor de la app"
        ),
        AudioFormatInfo(
            extension = "wv", displayName = ".wv", usable = false,
            note = "WavPack: sin decodificador nativo en el reproductor de la app"
        ),
        AudioFormatInfo(
            extension = "tta", displayName = ".tta", usable = false,
            note = "True Audio: sin decodificador nativo en el reproductor de la app"
        ),
        AudioFormatInfo(
            extension = "mpc", displayName = ".mpc", usable = false,
            note = "Musepack: formato poco común, sin decodificador nativo en el reproductor de la app"
        ),
        AudioFormatInfo(
            extension = "ra", displayName = ".ra", usable = false,
            note = "RealAudio: formato obsoleto/propietario, sin soporte en el reproductor de la app"
        ),
        AudioFormatInfo(
            extension = "caf", displayName = ".caf", usable = false,
            note = "Apple Core Audio Format: sin soporte nativo en el reproductor de la app"
        ),
        AudioFormatInfo(
            extension = "au", displayName = ".au", usable = false,
            note = "Formato antiguo (Sun/NeXT): sin soporte nativo en el reproductor de la app"
        ),
        AudioFormatInfo(
            extension = "voc", displayName = ".voc", usable = false,
            note = "Formato antiguo de Creative Labs: sin soporte en el reproductor de la app"
        ),

        // --- No usables: no son audio grabado, son instrucciones para generar sonido ---
        AudioFormatInfo(
            extension = "midi", displayName = ".midi", usable = false,
            note = "No es audio grabado, es una secuencia de notas que necesita un sintetizador: el reproductor de la app no lo soporta"
        ),
        AudioFormatInfo(
            extension = "mod", displayName = ".mod", usable = false,
            note = "Formato de tracker (patrones/instrumentos, no audio grabado): mismo problema de fondo que .midi, necesita un motor de tracker que la app no trae"
        ),

        // --- Formato de Playlist, no de canción ---
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

    /** Formatos no usables (ej. .aac crudo, .midi) que se ocultan de la librería por defecto la primera vez, hasta que el usuario decida lo contrario. */
    val DEFAULT_HIDDEN_EXTENSIONS: Set<String> = ALL_FORMATS.filter { !it.usable }.map { it.extension }.toSet()
}