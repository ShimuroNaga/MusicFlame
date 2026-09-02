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
 * [isFree] decide qué mostrar bloqueado en [AppFontPickerDialog] y a qué
 * fuente cae por defecto MusicFlameTheme si el usuario no está desbloqueado
 * (ver ProStatusHolder.isProUnlocked) y tenía guardada una de pago — mismo
 * patrón que appTextColorState/COLOR_MODE_RAINBOW en Theme.kt.
 *
 * Las 5 fuentes de pago están además en PaymentCatalog (sección "Tipo de
 * letra") solo para pintarse en la tabla informativa de Ajustes > Pagos; el
 * candado real que de verdad bloquea su uso vive acá, consultado desde
 * MusicFlameTheme y AppFontPickerDialog.
 */
enum class AppFont(
    val id: String,
    val displayName: String,
    val fontFamily: FontFamily,
    val isFree: Boolean
) {
    ROBOTO(
        id = "roboto",
        displayName = "Roboto",
        fontFamily = FontFamily(
            Font(R.font.roboto_regular, FontWeight.Normal),
            Font(R.font.roboto_medium, FontWeight.Medium)
        ),
        isFree = true
    ),
    LATO(
        id = "lato",
        displayName = "Lato",
        fontFamily = FontFamily(
            Font(R.font.lato_regular, FontWeight.Normal),
            Font(R.font.lato_italic, FontWeight.Normal, FontStyle.Italic)
        ),
        isFree = true
    ),
    OPEN_SANS(
        id = "open_sans",
        displayName = "Open Sans",
        fontFamily = FontFamily(
            Font(R.font.open_sans_regular, FontWeight.Normal),
            Font(R.font.open_sans_medium, FontWeight.Medium)
        ),
        isFree = true
    ),
    INTER(
        id = "inter",
        displayName = "Inter",
        fontFamily = FontFamily(
            Font(R.font.inter_regular, FontWeight.Normal),
            Font(R.font.inter_italic, FontWeight.Normal, FontStyle.Italic)
        ),
        isFree = true
    ),
    ASAP_SHARP(
        id = "asap_sharp",
        displayName = "Asap Sharp",
        fontFamily = FontFamily(
            Font(R.font.asap_sharp_regular, FontWeight.Normal),
            Font(R.font.asap_sharp_medium, FontWeight.Medium)
        ),
        isFree = true
    ),
    NUNITO(
        id = "nunito",
        displayName = "Nunito",
        fontFamily = FontFamily(
            Font(R.font.nunito_regular, FontWeight.Normal),
            Font(R.font.nunito_medium, FontWeight.Medium)
        ),
        isFree = true
    ),

    // --- Premium ($5 MXN c/u, ver PaymentCatalog) ---
    COMFORTAA(
        id = "comfortaa",
        displayName = "Comfortaa",
        fontFamily = FontFamily(
            Font(R.font.comfortaa_regular, FontWeight.Normal),
            Font(R.font.comfortaa_medium, FontWeight.Medium)
        ),
        isFree = false
    ),
    PLAYFAIR_DISPLAY(
        id = "playfair_display",
        displayName = "Playfair Display",
        fontFamily = FontFamily(
            Font(R.font.playfairdisplay_regular, FontWeight.Normal),
            Font(R.font.playfairdisplay_medium, FontWeight.Medium)
        ),
        isFree = false
    ),
    ORBITRON(
        id = "orbitron",
        displayName = "Orbitron",
        fontFamily = FontFamily(
            Font(R.font.orbitron_regular, FontWeight.Normal),
            Font(R.font.orbitron_medium, FontWeight.Medium)
        ),
        isFree = false
    ),
    PRESS_START_2P(
        id = "press_start_2p",
        displayName = "Press Start 2P",
        fontFamily = FontFamily(
            Font(R.font.pressstart_regular, FontWeight.Normal)
        ),
        isFree = false
    ),
    SPACE_MONO(
        id = "space_mono",
        displayName = "Space Mono",
        fontFamily = FontFamily(
            Font(R.font.spacemono_regular, FontWeight.Normal)
        ),
        isFree = false
    );

    companion object {
        val DEFAULT: AppFont = ROBOTO

        fun fromId(id: String): AppFont = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}
