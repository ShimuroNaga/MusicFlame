package com.music.musicflame.ui.screens.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.musicflame.data.SettingsRepository
import com.music.musicflame.ui.theme.LocalAppTextColor

/**
 * Paso del onboarding para el feature de Lyrics: mismos ajustes exactos
 * que la card "Lyrics" de SettingsScreen.kt (velocidad, animación, color de
 * texto), para que el usuario configure su preferencia desde el primer uso.
 */
@Composable
fun OnboardingLyricsStep(settingsRepo: SettingsRepository) {
    val highEmphasis = LocalAppTextColor.current
    val mediumEmphasis = LocalAppTextColor.current.copy(alpha = 0.7f)

    var speed by remember { mutableStateOf(settingsRepo.getLyricsSpeed()) }
    var animType by remember { mutableStateOf(settingsRepo.getLyricsAnimationType()) }
    // Igual que en la card "Lyrics" de SettingsScreen.kt: SOLO Blanco/Negro.
    // Nunca "Adaptativo" (seguir el color de Apariencia) ni "Personalizado" (hex libre).
    var colorMode by remember { mutableStateOf(settingsRepo.getLyricsTextColorMode()) }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        item {
            onboardingSectionHeader("Lyrics")
            Text(
                "MusicFlame busca la letra automáticamente cuando abres el reproductor a pantalla completa. Para verla, desliza la pantalla (no la carátula) hacia la derecha con una canción sonando. Configúralo a tu gusto:",
                fontSize = 13.sp,
                color = mediumEmphasis,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Velocidad de animación: ${"%.1f".format(speed)}x",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Slider(
                        value = speed,
                        onValueChange = {
                            speed = it
                            settingsRepo.saveLyricsSpeed(it)
                        },
                        valueRange = 0.5f..2f,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            activeTrackColor = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Lenta", fontSize = 11.sp, color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f))
                        Text("Rápida", fontSize = 11.sp, color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f))
                    }

                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Tipo de animación",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Deslizar", "Desvanecer", "Rebote").forEach { opt ->
                            FilterChip(
                                selected = animType == opt,
                                onClick = {
                                    animType = opt
                                    settingsRepo.saveLyricsAnimationType(opt)
                                },
                                label = { Text(opt, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.tertiaryContainer
                                )
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Color del texto",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Blanco", "Negro").forEach { opt ->
                            FilterChip(
                                selected = colorMode == opt,
                                onClick = {
                                    colorMode = opt
                                    settingsRepo.saveLyricsTextColorMode(opt)
                                },
                                label = { Text(opt, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.tertiaryContainer
                                )
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
