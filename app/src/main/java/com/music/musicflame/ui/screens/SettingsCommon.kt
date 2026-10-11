package com.music.musicflame.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Colores compartidos de Ajustes. SettingsScreen los calcula una vez y se los
 * pasa a cada sección que vive en su propio archivo (Settings*.kt).
 */
@Immutable
class SettingsStyle(
    val highEmphasis: Color,
    val mediumEmphasis: Color,
    val trailingColor: Color,
    val listItemColors: ListItemColors,
    val dividerColor: Color
)

/** Encabezado de sección (mismo diseño que el sectionHeader local de SettingsScreen). */
@Composable
internal fun SettingsSectionHeader(style: SettingsStyle, text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
        fontSize = 15.sp,
        fontWeight = FontWeight.Black,
        color = style.trailingColor
    )
}
