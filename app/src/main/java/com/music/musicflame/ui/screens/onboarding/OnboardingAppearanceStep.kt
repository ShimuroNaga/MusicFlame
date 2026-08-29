package com.music.musicflame.ui.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.music.musicflame.AlbumArtShapeType
import com.music.musicflame.R
import com.music.musicflame.data.AppIconManager
import com.music.musicflame.data.SettingsRepository
import com.music.musicflame.ui.components.AlbumArtShapePreview
import com.music.musicflame.ui.theme.LocalAppTextColor
import com.music.musicflame.ui.theme.parseCustomTextColor

// Mismos iconos, mismas claves y mismos labels que appIconOptions en SettingsScreen.kt
private val appIconOptions = listOf(
    Triple("default", "Original", R.mipmap.ic_launcher),
    Triple("brilliant", "Brillante", R.mipmap.ic_launcher_brilliant),
    Triple("pixel", "Pixelart", R.mipmap.ic_launcher_pixel),
    Triple("cookies", "Cookies N Cream", R.mipmap.ic_launcher_cookies),
    Triple("gray", "Escala de grises", R.mipmap.ic_launcher_gray),
    Triple("remix", "RemixFlame", R.mipmap.ic_launcher_remixflame)
)

// Mismos labels que muestra el diálogo de forma de carátula en SettingsScreen.kt
private fun shapeLabel(shape: AlbumArtShapeType) = when (shape) {
    AlbumArtShapeType.SQUARE -> "Cuadrado"
    AlbumArtShapeType.CIRCLE -> "Círculo"
    AlbumArtShapeType.HEXAGON -> "Hexágono"
    AlbumArtShapeType.VINYL -> "Vinilo"
    AlbumArtShapeType.SQUIRCLE -> "Squircle"
}

// Mismos textos exactos que usa MusicFlameTheme para leer/guardar "app_theme"
private val themeOptions = listOf("Siguiendo al sistema", "Fondo blanco", "Fondo oscuro")

// Misma paleta exacta que el diálogo "Color de texto" de SettingsScreen.kt
private val presetTextColors = listOf(
    "#FFFFFF", "#000000", "#F44336", "#E91E63", "#9C27B0",
    "#673AB7", "#3F51B5", "#2196F3", "#03A9F4", "#00BCD4",
    "#009688", "#4CAF50", "#8BC34A", "#CDDC39", "#FFEB3B",
    "#FFC107", "#FF9800", "#FF5722", "#795548", "#9E9E9E"
)

@Composable
fun OnboardingAppearanceStep(settingsRepo: SettingsRepository) {
    val context = LocalContext.current
    val listItemColors = onboardingListItemColors()
    val dividerColor = MaterialTheme.colorScheme.outlineVariant
    val highEmphasis = LocalAppTextColor.current
    val mediumEmphasis = LocalAppTextColor.current.copy(alpha = 0.7f)
    val trailingColor = MaterialTheme.colorScheme.primary

    var selectedIcon by remember { mutableStateOf(settingsRepo.getSelectedAppIcon()) }
    var selectedTheme by remember { mutableStateOf(settingsRepo.getAppTheme()) }
    var selectedShape by remember { mutableStateOf(settingsRepo.getAlbumArtShape()) }
    // "Negro" y "Blanco" se fusionaron en una sola opción "Adaptativo": MusicFlameTheme
    // siempre auto-corrige el color de texto contra la luminancia real del fondo (ver
    // Theme.kt), así que mantenerlos como dos presets separados era engañoso — daba a
    // entender que el usuario fijaba un color fijo cuando en realidad ambos terminaban
    // resolviéndose al mismo blanco/negro automático. Si lo guardado es un preset legado
    // ("Negro"/"Blanco" de instalaciones previas), se muestra como "Adaptativo"; solo
    // "Personalizado" se respeta tal cual.
    var selectedTextColor by remember {
        mutableStateOf(
            settingsRepo.getAppTextColor().let { stored ->
                if (stored == "Personalizado") stored else "Adaptativo"
            }
        )
    }
    var customTextColorHex by remember { mutableStateOf(settingsRepo.getCustomTextColorHex()) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)
    ) {
        item {
            Text(
                "Personaliza tu app",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = highEmphasis
            )
            Text(
                "Podrás cambiar todo esto luego desde Ajustes.",
                style = MaterialTheme.typography.bodyMedium,
                color = mediumEmphasis,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        item { onboardingSectionHeader("Icono de la app") }
        item {
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(appIconOptions) { (key, label, previewRes) ->
                    val isSelected = selectedIcon == key
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable {
                            selectedIcon = key
                            settingsRepo.saveSelectedAppIcon(key)
                            AppIconManager.setIcon(context, key)
                        }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    if (isSelected) trailingColor.copy(alpha = 0.15f)
                                    else Color.Transparent
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = previewRes,
                                contentDescription = label,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            label,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) trailingColor else mediumEmphasis
                        )
                    }
                }
            }
            HorizontalDivider(color = dividerColor, modifier = Modifier.padding(top = 12.dp))
        }

        item { onboardingSectionHeader("Tema") }
        item {
            Column {
                themeOptions.forEach { theme ->
                    ListItem(
                        headlineContent = { Text(theme) },
                        leadingContent = {
                            RadioButton(
                                selected = selectedTheme == theme,
                                onClick = {
                                    selectedTheme = theme
                                    settingsRepo.saveAppTheme(theme)
                                }
                            )
                        },
                        colors = listItemColors,
                        modifier = Modifier.clickable {
                            selectedTheme = theme
                            settingsRepo.saveAppTheme(theme)
                        }
                    )
                }
            }
            HorizontalDivider(color = dividerColor)
        }

        item { onboardingSectionHeader("Color de texto") }
        item {
            Column {
                listOf("Adaptativo", "Personalizado").forEach { colorOption ->
                    ListItem(
                        headlineContent = { Text(colorOption) },
                        leadingContent = {
                            RadioButton(
                                selected = selectedTextColor == colorOption,
                                onClick = {
                                    selectedTextColor = colorOption
                                    settingsRepo.saveAppTextColor(colorOption)
                                }
                            )
                        },
                        colors = listItemColors,
                        modifier = Modifier.clickable {
                            selectedTextColor = colorOption
                            settingsRepo.saveAppTextColor(colorOption)
                        }
                    )
                }

                if (selectedTextColor == "Personalizado") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        presetTextColors.forEach { hex ->
                            val isSelected = customTextColorHex.equals(hex, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(parseCustomTextColor(hex))
                                    .border(
                                        width = if (isSelected) 3.dp else 1.dp,
                                        color = if (isSelected) trailingColor else Color.Gray,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        customTextColorHex = hex
                                        settingsRepo.saveCustomTextColorHex(hex)
                                    }
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = customTextColorHex,
                        onValueChange = {
                            customTextColorHex = it
                            settingsRepo.saveCustomTextColorHex(it)
                        },
                        label = { Text("Hex (#RRGGBB) o RGBA (r,g,b,a)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                }

                if (selectedTextColor == "Adaptativo") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Info,
                            contentDescription = null,
                            tint = trailingColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "MusicFlame adaptará el color del texto (blanco o negro) según el color de fondo que elijas, para que siempre se pueda leer bien.",
                            color = mediumEmphasis,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            HorizontalDivider(color = dividerColor)
        }

        item { onboardingSectionHeader("Forma de la carátula") }
        item {
            // Reusa la Shape real (HexagonShape, SquircleShape, VinylOverlay, etc.)
            // de AlbumArt.kt — la misma que usa el reproductor de verdad, no una
            // aproximación visual hecha aparte para el onboarding.
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(AlbumArtShapeType.entries.toList()) { shape ->
                    val isSelected = selectedShape == shape
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable {
                            selectedShape = shape
                            settingsRepo.saveAlbumArtShape(shape)
                        }
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(4.dp)
                                .background(
                                    if (isSelected) trailingColor.copy(alpha = 0.15f) else Color.Transparent,
                                    RoundedCornerShape(16.dp)
                                )
                                .padding(8.dp)
                        ) {
                            AlbumArtShapePreview(shape = shape, size = 44.dp, color = trailingColor)
                        }
                        Text(
                            shapeLabel(shape),
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) trailingColor else mediumEmphasis
                        )
                    }
                }
            }
            HorizontalDivider(color = dividerColor)
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}