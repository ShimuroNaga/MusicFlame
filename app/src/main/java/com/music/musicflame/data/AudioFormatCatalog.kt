package com.music.musicflame.data

import androidx.annotation.StringRes
import com.music.musicflame.R

data class AudioFormatInfo(
    val extension: String,       // "mp3", "flac", ... siempre en minúsculas, es la "clave" del formato
    val displayName: String,     // ".mp3", ".flac", ... para mostrar en la UI
    val usable: Boolean,         // true = se reproduce bien dentro de la app
    val isPlaylistFormat: Boolean = false, // true SOLO para .m3u
    @StringRes val noteRes: Int? = null // aclaración opcional (recurso) mostrada bajo el nombre del formato
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
            noteRes = R.string.fmt_note_mp4
        ),
        AudioFormatInfo(
            extension = "3gp", displayName = ".3gp", usable = true,
            noteRes = R.string.fmt_note_3gp
        ),
        AudioFormatInfo("amr", ".amr", usable = true),
        AudioFormatInfo(
            extension = "webm", displayName = ".webm", usable = true,
            noteRes = R.string.fmt_note_webm
        ),
        AudioFormatInfo(
            extension = "mka", displayName = ".mka", usable = true,
            noteRes = R.string.fmt_note_mka
        ),
        AudioFormatInfo("ac3", ".ac3", usable = true),
        AudioFormatInfo("eac3", ".eac3", usable = true),

        // --- No usables: formatos de audio real, pero sin decodificador nativo en la app ---
        AudioFormatInfo(
            extension = "aac", displayName = ".aac", usable = false,
            noteRes = R.string.fmt_note_aac
        ),
        AudioFormatInfo(
            extension = "ape", displayName = ".ape", usable = false,
            noteRes = R.string.fmt_note_ape
        ),
        AudioFormatInfo(
            extension = "wv", displayName = ".wv", usable = false,
            noteRes = R.string.fmt_note_wv
        ),
        AudioFormatInfo(
            extension = "tta", displayName = ".tta", usable = false,
            noteRes = R.string.fmt_note_tta
        ),
        AudioFormatInfo(
            extension = "mpc", displayName = ".mpc", usable = false,
            noteRes = R.string.fmt_note_mpc
        ),
        AudioFormatInfo(
            extension = "ra", displayName = ".ra", usable = false,
            noteRes = R.string.fmt_note_ra
        ),
        AudioFormatInfo(
            extension = "caf", displayName = ".caf", usable = false,
            noteRes = R.string.fmt_note_caf
        ),
        AudioFormatInfo(
            extension = "au", displayName = ".au", usable = false,
            noteRes = R.string.fmt_note_au
        ),
        AudioFormatInfo(
            extension = "voc", displayName = ".voc", usable = false,
            noteRes = R.string.fmt_note_voc
        ),

        // --- No usables: no son audio grabado, son instrucciones para generar sonido ---
        AudioFormatInfo(
            extension = "midi", displayName = ".midi", usable = false,
            noteRes = R.string.fmt_note_midi
        ),
        AudioFormatInfo(
            extension = "mod", displayName = ".mod", usable = false,
            noteRes = R.string.fmt_note_mod
        ),

        // --- Formato de Playlist, no de canción ---
        AudioFormatInfo(
            extension = "m3u",
            displayName = ".m3u",
            usable = true,
            isPlaylistFormat = true,
            noteRes = R.string.fmt_note_m3u
        )
    )

    private val FORMATS_BY_EXTENSION: Map<String, AudioFormatInfo> = ALL_FORMATS.associateBy { it.extension }

    fun infoFor(extension: String): AudioFormatInfo? = FORMATS_BY_EXTENSION[canonicalExtension(extension)]

    /** Formatos no usables (ej. .aac crudo, .midi) que se ocultan de la librería por defecto la primera vez, hasta que el usuario decida lo contrario. */
    val DEFAULT_HIDDEN_EXTENSIONS: Set<String> = ALL_FORMATS.filter { !it.usable }.map { it.extension }.toSet()
}