package com.music.musicflame.ui.screens.onboarding

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.musicflame.ui.theme.LocalAppTextColor

/**
 * Mismo look & feel que sectionHeader() en SettingsScreen.kt: título de
 * bloque en color primario, para que el onboarding se sienta igual que Ajustes.
 */
@Composable
fun onboardingSectionHeader(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 20.dp, bottom = 8.dp),
        fontSize = 15.sp,
        fontWeight = FontWeight.Black,
        color = MaterialTheme.colorScheme.primary
    )
}

/**
 * Mismos colores de ListItem que usa SettingsScreen.kt (listItemColors):
 * texto principal = LocalAppTextColor, texto secundario = LocalAppTextColor al 70%,
 * iconos/trailing = color primario. Así los ListItem del onboarding se ven
 * idénticos a los de Ajustes.
 */
@Composable
fun onboardingListItemColors(): ListItemColors {
    val highEmphasis = LocalAppTextColor.current
    val mediumEmphasis = LocalAppTextColor.current.copy(alpha = 0.7f)
    val trailingColor = MaterialTheme.colorScheme.primary
    return ListItemDefaults.colors(
        containerColor = Color.Transparent,
        headlineColor = highEmphasis,
        supportingColor = mediumEmphasis,
        trailingIconColor = trailingColor
    )
}
