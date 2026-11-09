package com.music.musicflame.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.music.musicflame.R

/**
 * Catálogo de tipos de letra para toda la app (título de canción, letras,
 * menús, todo). Cada fuente viene empaquetada como recurso en res/font/
 * (ver res/font/, archivos .ttf de Google Fonts, licencia OFL).
 *
 * Todas son gratis: ya no se venden en Ko-fi ni están en PaymentCatalog.
 */
enum class AppFont(
    val id: String,
    val displayName: String,
    val fontFamily: FontFamily
) {
    ROBOTO(
        id = "roboto",
        displayName = "Roboto",
        fontFamily = FontFamily(
            Font(R.font.roboto_regular, FontWeight.Normal),
            Font(R.font.roboto_medium, FontWeight.Medium)
        )
    ),
    LATO(
        id = "lato",
        displayName = "Lato",
        fontFamily = FontFamily(
            Font(R.font.lato_regular, FontWeight.Normal),
            Font(R.font.lato_italic, FontWeight.Normal, FontStyle.Italic)
        )
    ),
    OPEN_SANS(
        id = "open_sans",
        displayName = "Open Sans",
        fontFamily = FontFamily(
            Font(R.font.open_sans_regular, FontWeight.Normal),
            Font(R.font.open_sans_medium, FontWeight.Medium)
        )
    ),
    INTER(
        id = "inter",
        displayName = "Inter",
        fontFamily = FontFamily(
            Font(R.font.inter_regular, FontWeight.Normal),
            Font(R.font.inter_italic, FontWeight.Normal, FontStyle.Italic)
        )
    ),
    ASAP_SHARP(
        id = "asap_sharp",
        displayName = "Asap Sharp",
        fontFamily = FontFamily(
            Font(R.font.asap_sharp_regular, FontWeight.Normal),
            Font(R.font.asap_sharp_medium, FontWeight.Medium)
        )
    ),
    NUNITO(
        id = "nunito",
        displayName = "Nunito",
        fontFamily = FontFamily(
            Font(R.font.nunito_regular, FontWeight.Normal),
            Font(R.font.nunito_medium, FontWeight.Medium)
        )
    ),
    BLACK_OPS_ONE(
        id = "black_ops_one",
        displayName = "Black Ops One",
        fontFamily = FontFamily(
            Font(R.font.black_regular, FontWeight.Normal)
        )
    ),
    ISOMETRA(
        id = "isometra",
        displayName = "Isometra",
        fontFamily = FontFamily(
            Font(R.font.iso_regular, FontWeight.Normal)
        )
    ),

    // --- Antes de pago, ahora gratis ---
    COMFORTAA(
        id = "comfortaa",
        displayName = "Comfortaa",
        fontFamily = FontFamily(
            Font(R.font.comfortaa_regular, FontWeight.Normal),
            Font(R.font.comfortaa_medium, FontWeight.Medium)
        )
    ),
    PLAYFAIR_DISPLAY(
        id = "playfair_display",
        displayName = "Playfair Display",
        fontFamily = FontFamily(
            Font(R.font.playfairdisplay_regular, FontWeight.Normal),
            Font(R.font.playfairdisplay_medium, FontWeight.Medium)
        )
    ),
    ORBITRON(
        id = "orbitron",
        displayName = "Orbitron",
        fontFamily = FontFamily(
            Font(R.font.orbitron_regular, FontWeight.Normal),
            Font(R.font.orbitron_medium, FontWeight.Medium)
        )
    ),
    PRESS_START_2P(
        id = "press_start_2p",
        displayName = "Press Start 2P",
        fontFamily = FontFamily(
            Font(R.font.pressstart_regular, FontWeight.Normal)
        )
    ),
    SPACE_MONO(
        id = "space_mono",
        displayName = "Space Mono",
        fontFamily = FontFamily(
            Font(R.font.spacemono_regular, FontWeight.Normal)
        )
    ),
    GOOGLE_SANS(
        id = "google_sans",
        displayName = "Google Sans",
        fontFamily = FontFamily(
            Font(R.font.googlesans_regular, FontWeight.Normal)
        )
    ),
    RUBIK(
        id = "rubik",
        displayName = "Rubik",
        fontFamily = FontFamily(
            Font(R.font.rubik_regular, FontWeight.Normal)
        )
    );

    companion object {
        val DEFAULT: AppFont = ROBOTO

        fun fromId(id: String): AppFont = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}
