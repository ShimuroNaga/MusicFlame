package com.music.musicflame.ui.screens.onboarding

import com.music.musicflame.R
import androidx.compose.ui.res.stringResource
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
                stringResource(R.string.ob_sg_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = highEmphasis
            )
            Text(
                stringResource(R.string.ob_sg_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = mediumEmphasis,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        item { onboardingSectionHeader(stringResource(R.string.ob_sg_header)) }
        item {
            OutlinedTextField(
                value = durationMin,
                onValueChange = {
                    durationMin = it.filter { c -> c.isDigit() }
                    settingsRepo.saveDurationFilterMin(durationMin.toIntOrNull() ?: 0)
                },
                label = { Text(stringResource(R.string.ob_sg_min_dur)) },
                supportingText = { Text(stringResource(R.string.ob_sg_min_dur_hint)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            )
            HorizontalDivider(color = dividerColor)
        }

        item { onboardingSectionHeader(stringResource(R.string.ob_sg_playback)) }
        item {
            ListItem(
                headlineContent = { Text(stringResource(R.string.ob_sg_background)) },
                supportingContent = { Text(stringResource(R.string.ob_sg_background_desc)) },
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
                headlineContent = { Text(stringResource(R.string.ob_sg_pause_unplug)) },
                supportingContent = { Text(stringResource(R.string.ob_sg_pause_unplug_desc)) },
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

        item { onboardingSectionHeader(stringResource(R.string.ob_sg_eq)) }
        item {
            ListItem(
                headlineContent = { Text("Studio Pro EQ") },
                supportingContent = { Text(stringResource(R.string.ob_sg_preset_active, eqPreset)) },
                colors = listItemColors
            )
            HorizontalDivider(color = dividerColor)
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}
