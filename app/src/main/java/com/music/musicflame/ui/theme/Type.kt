package com.music.musicflame.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Set of Material typography styles to start with
val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    )
    /* Other default text styles to override
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
    */
)

/**
 * Reconstruye TODA la escala tipográfica de Material 3 (display/headline/
 * title/body/label, los 15 estilos) con [fontFamily] aplicado en cada una,
 * partiendo de los tamaños/pesos/interlineados por defecto de M3 — solo se
 * reemplaza la fuente, no el resto del estilo.
 *
 * [fontScale] multiplica el fontSize y el lineHeight de los 15 estilos por
 * igual (manteniendo la jerarquía entre ellos: si display sigue siendo más
 * grande que body, etc.), calculado como
 * (tamaño elegido por el usuario en sp) / 16f — 16sp es el tamaño por
 * defecto de bodyLarge en M3, que es el que se usa como referencia en el
 * selector de Ajustes > Apariencia. fontScale = 1f no cambia nada.
 *
 * Usado por MusicFlameTheme para aplicar el tipo de letra Y el tamaño de
 * letra elegidos por el usuario (ver AppFont, SettingsRepository.getAppFont
 * / getAppFontSizeSp) a TODA la app de una sola vez.
 */
fun appTypographyFor(fontFamily: FontFamily, fontScale: Float = 1f): Typography {
    val base = Typography()
    fun androidx.compose.ui.text.TextStyle.scaled() = this.copy(
        fontFamily = fontFamily,
        fontSize = fontSize * fontScale,
        lineHeight = lineHeight * fontScale
    )
    return base.copy(
        displayLarge = base.displayLarge.scaled(),
        displayMedium = base.displayMedium.scaled(),
        displaySmall = base.displaySmall.scaled(),
        headlineLarge = base.headlineLarge.scaled(),
        headlineMedium = base.headlineMedium.scaled(),
        headlineSmall = base.headlineSmall.scaled(),
        titleLarge = base.titleLarge.scaled(),
        titleMedium = base.titleMedium.scaled(),
        titleSmall = base.titleSmall.scaled(),
        bodyLarge = base.bodyLarge.scaled(),
        bodyMedium = base.bodyMedium.scaled(),
        bodySmall = base.bodySmall.scaled(),
        labelLarge = base.labelLarge.scaled(),
        labelMedium = base.labelMedium.scaled(),
        labelSmall = base.labelSmall.scaled()
    )
}