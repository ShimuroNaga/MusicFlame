package com.music.musicflame.ui.screens.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.music.musicflame.data.SettingsRepository
import com.music.musicflame.ui.theme.LocalAppTextColor

/**
 * Paso 3. El ecualizador real ("Studio Pro EQ") es un diálogo completo con
 * sliders de bandas, bass boost y virtualizer — no se replica aquí para no
 * duplicar esa lógica; solo se avisa que existe y dónde encontrarlo.
 */
@Composable
fun OnboardingSongsStep(settingsRepo: SettingsRepository) {
    val context = LocalContext.current
    val listItemColors = onboardingListItemColors()
    val dividerColor = MaterialTheme.colorScheme.outlineVariant
    val highEmphasis = LocalAppTextColor.current
    val mediumEmphasis = LocalAppTextColor.current.copy(alpha = 0.7f)

    var durationMin by remember { mutableStateOf(settingsRepo.getDurationFilterMin().toString()) }
    var playInBackground by remember { mutableStateOf(settingsRepo.getPlayInBackground()) }
    var pauseOnDisconnect by remember { mutableStateOf(settingsRepo.getPauseOnDisconnect()) }
    val eqPreset = remember { settingsRepo.getEqPresetSelected() }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)
    ) {
        item {
            Text(
                "Manejo de canciones",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = highEmphasis
            )
            Text(
                "Ajusta cómo se comporta la reproducción.",
                style = MaterialTheme.typography.bodyMedium,
                color = mediumEmphasis,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        item { onboardingSectionHeader("Manejo de Canciones") }
        item {
            OutlinedTextField(
                value = durationMin,
                onValueChange = {
                    durationMin = it.filter { c -> c.isDigit() }
                    settingsRepo.saveDurationFilterMin(durationMin.toIntOrNull() ?: 0)
                },
                label = { Text("Duración mínima (segundos)") },
                supportingText = { Text("Excluye canciones más cortas que esto, ej. tonos o clips") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            )
            HorizontalDivider(color = dividerColor)
        }

        item { onboardingSectionHeader("Reproducción") }
        item {
            ListItem(
                headlineContent = { Text("Reproducir en segundo plano") },
                supportingContent = { Text("Mantiene el reproductor activo fuera de la app") },
                trailingContent = {
                    Switch(
                        checked = playInBackground,
                        onCheckedChange = {
                            playInBackground = it
                            settingsRepo.savePlayInBackground(it)
                        }
                    )
                },
                colors = listItemColors
            )
            HorizontalDivider(color = dividerColor)
        }
        item {
            ListItem(
                headlineContent = { Text("Pausar al desconectar audífonos") },
                supportingContent = { Text("Detiene la canción si te quitas los audífonos o se desconecta el Bluetooth") },
                trailingContent = {
                    Switch(
                        checked = pauseOnDisconnect,
                        onCheckedChange = {
                            pauseOnDisconnect = it
                            settingsRepo.savePauseOnDisconnect(it)
                        }
                    )
                },
                colors = listItemColors
            )
            HorizontalDivider(color = dividerColor)
        }

        item { onboardingSectionHeader("Ecualizador") }
        item {
            ListItem(
                headlineContent = { Text("Studio Pro EQ") },
                supportingContent = { Text("Preset activo: $eqPreset — personalízalo desde Ajustes cuando quieras") },
                colors = listItemColors
            )
            HorizontalDivider(color = dividerColor)
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}
