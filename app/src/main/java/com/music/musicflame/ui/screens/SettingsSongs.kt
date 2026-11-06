package com.music.musicflame.ui.screens

import android.content.Context
import android.content.Intent
import android.media.audiofx.Equalizer
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.music.musicflame.R
import com.music.musicflame.audio.EqPresets
import com.music.musicflame.data.AppIconManager
import com.music.musicflame.data.DeviceInfoProvider
import com.music.musicflame.data.LicenseRepository
import com.music.musicflame.data.SavingsGoalRepository
import com.music.musicflame.data.SettingsRepository
import com.music.musicflame.together.SupabaseSalas
import com.music.musicflame.ui.theme.LocalAppTextColor
import com.music.musicflame.widget.MusicFlameWidgetProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Sección "Canciones" de Ajustes. Código movido tal cual desde SettingsScreen.kt (sin cambios de comportamiento). */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
internal fun LazyListScope.settingsSongsSection(
    style: SettingsStyle,
    playerManager: com.music.musicflame.data.MusicPlayerManager,
    context: Context,
    settingsRepo: SettingsRepository,
    showDurationFilterDialog: MutableState<Boolean>,
    showAudioFormatsDialog: MutableState<Boolean>,
    showSleepTimerDialog: MutableState<Boolean>,
    showEqualizerDialog: MutableState<Boolean>,
    showProEqualizerDialog: MutableState<Boolean>,
    showAnomalyScanDialog: MutableState<Boolean>,
    playInBackground: MutableState<Boolean>,
    pauseOnDisconnect: MutableState<Boolean>,
    volumeNormalizationEnabled: MutableState<Boolean>,
    eqPresetSelected: MutableState<String>,
    isIgnoringBatteryState: MutableState<Boolean>,
    hasFileAccessPermissionState: MutableState<Boolean>,
    realTagWritingEnabledState: MutableState<Boolean>
) {
    var isIgnoringBattery by isIgnoringBatteryState
    var hasFileAccessPermission by hasFileAccessPermissionState
    var realTagWritingEnabled by realTagWritingEnabledState

    item { SettingsSectionHeader(style, stringResource(R.string.settings_manejo_de_canciones)) }

    item {
        SettingsRow(style, SettingsRowPos.First) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_filtrar_por_duracion)) },
                supportingContent = { Text(stringResource(R.string.settings_excluir_o_mostrar_solo_canciones_d)) },
                trailingContent = { TextButton(onClick = { showDurationFilterDialog.value = true }) { Text(stringResource(R.string.settings_configurar), fontWeight = FontWeight.ExtraBold, color = style.trailingColor) } },
                colors = style.listItemColors
            )
            SettingsDivider(style)
        }
    }

    item {
        SettingsRow(style, SettingsRowPos.Middle) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_formatos_de_audio_a_escuchar)) },
                supportingContent = { Text(stringResource(R.string.settings_elige_que_formatos_de_tu_bibliotec)) },
                trailingContent = { TextButton(onClick = { showAudioFormatsDialog.value = true }) { Text(stringResource(R.string.settings_ver_formatos), fontWeight = FontWeight.ExtraBold, color = style.trailingColor) } },
                colors = style.listItemColors
            )
            SettingsDivider(style)
        }
    }

    item {
        SettingsRow(style, SettingsRowPos.Middle) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_busqueda_de_anomalias)) },
                supportingContent = { Text(stringResource(R.string.settings_detecta_caratulas_corruptas_metada)) },
                trailingContent = {
                    TextButton(onClick = { showAnomalyScanDialog.value = true }) {
                        Text(stringResource(R.string.settings_analizar), fontWeight = FontWeight.ExtraBold, color = style.trailingColor)
                    }
                },
                colors = style.listItemColors
            )
            SettingsDivider(style)
        }
    }

    item {
        SettingsRow(style, SettingsRowPos.Middle) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_guardar_etiquetas_reales_en_el_arc)) },
                supportingContent = {
                    Text(
                        text = when {
                            !hasFileAccessPermission -> stringResource(R.string.settings_requiere_el_permiso_acceso_a_todos)
                            realTagWritingEnabled -> stringResource(R.string.settings_activado_caratula_titulo_artista_y)
                            else -> stringResource(R.string.settings_desactivado_los_cambios_solo_se_ve)
                        },
                        color = if (!hasFileAccessPermission) MaterialTheme.colorScheme.error else style.trailingColor
                    )
                },
                trailingContent = {
                    Switch(
                        checked = realTagWritingEnabled,
                        onCheckedChange = { checked ->
                            if (checked) {
                                if (hasFileAccessPermission) {
                                    realTagWritingEnabled = true
                                    settingsRepo.saveRealTagWritingEnabled(true)
                                } else {
                                    com.music.musicflame.data.RealTagWriter.requestFileAccessPermission(context)
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.settings_concede_acceso_a_todos_los_archivo),
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            } else {
                                realTagWritingEnabled = false
                                settingsRepo.saveRealTagWritingEnabled(false)
                            }
                        }
                    )
                },
                colors = style.listItemColors
            )
            SettingsDivider(style)
        }
    }

    item {
        SettingsRow(style, SettingsRowPos.Last) {
            val sleepActive by playerManager.sleepTimerActive
            val sleepEndOfSong by playerManager.sleepTimerEndOfSongActive
            val sleepRemainingMs by playerManager.sleepTimerRemainingMs

            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_temporizador_de_apagado)) },
                supportingContent = {
                    Text(
                        when {
                            sleepEndOfSong -> stringResource(R.string.settings_se_pausara_al_terminar_la_cancion)
                            sleepActive -> {
                                val totalSeconds = (sleepRemainingMs / 1000L).coerceAtLeast(0L)
                                val mm = totalSeconds / 60
                                val ss = totalSeconds % 60
                                stringResource(R.string.settings_pausando_en_02d_02d).format(mm, ss)
                            }
                            else -> stringResource(R.string.settings_pausa_la_reproduccion_automaticame)
                        }
                    )
                },
                trailingContent = {
                    if (sleepActive) {
                        TextButton(onClick = { playerManager.cancelSleepTimer() }) {
                            Text(stringResource(R.string.action_cancel), fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.error)
                        }
                    } else {
                        TextButton(onClick = { showSleepTimerDialog.value = true }) {
                            Text(stringResource(R.string.settings_configurar), fontWeight = FontWeight.ExtraBold, color = style.trailingColor)
                        }
                    }
                },
                colors = style.listItemColors
            )
        }
    }

    item { SettingsSectionHeader(style, stringResource(R.string.settings_reproduccion)) }

    item {
        SettingsRow(style, SettingsRowPos.First) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_reproducir_en_segundo_plano)) },
                supportingContent = { Text(stringResource(R.string.settings_mantiene_el_reproductor_activo_fue)) },
                trailingContent = {
                    Switch(
                        checked = playInBackground.value,
                        onCheckedChange = {
                            playInBackground.value = it
                            settingsRepo.savePlayInBackground(it)
                            Toast.makeText(context, if (it) context.getString(R.string.settings_segundo_plano_activado) else context.getString(R.string.settings_segundo_plano_desactivado), Toast.LENGTH_SHORT).show()
                        }
                    )
                },
                colors = style.listItemColors
            )
            SettingsDivider(style)
        }
    }
    item {
        SettingsRow(style, SettingsRowPos.Middle) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_pausar_al_desconectar_audifonos)) },
                supportingContent = { Text(stringResource(R.string.settings_detiene_la_cancion_si_te_quitas_lo)) },
                trailingContent = {
                    Switch(
                        checked = pauseOnDisconnect.value,
                        onCheckedChange = {
                            pauseOnDisconnect.value = it
                            settingsRepo.savePauseOnDisconnect(it)
                            Toast.makeText(context, if (it) context.getString(R.string.settings_pausa_automatica_activada) else context.getString(R.string.settings_pausa_automatica_desactivada), Toast.LENGTH_SHORT).show()
                        }
                    )
                },
                colors = style.listItemColors
            )
            SettingsDivider(style)
        }
    }

    item {
        SettingsRow(style, SettingsRowPos.Middle) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_normalizar_volumen)) },
                supportingContent = { Text(stringResource(R.string.settings_normalizar_volumen_desc)) },
                trailingContent = {
                    Switch(
                        checked = volumeNormalizationEnabled.value,
                        onCheckedChange = {
                            volumeNormalizationEnabled.value = it
                            settingsRepo.saveVolumeNormalizationEnabled(it)
                            // Aplica ya a la canción que suena (el servicio también lo relee solo).
                            context.sendBroadcast(android.content.Intent("com.music.musicflame.UPDATE_EQ").setPackage(context.packageName))
                        }
                    )
                },
                colors = style.listItemColors
            )
            SettingsDivider(style)
        }
    }

    // CROSSFADE: mezcla el final de una canción con el inicio de la siguiente.
    // Apagado por defecto; la duración (1-12 s) solo aparece cuando está activado.
    item {
        SettingsRow(style, SettingsRowPos.Middle) {
            val crossfadeOn = remember { mutableStateOf(settingsRepo.getCrossfadeEnabled()) }
            var crossfadeSeconds by remember { mutableStateOf(settingsRepo.getCrossfadeSeconds().toFloat()) }
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_crossfade_title)) },
                supportingContent = { Text(stringResource(R.string.settings_crossfade_desc)) },
                trailingContent = {
                    Switch(
                        checked = crossfadeOn.value,
                        onCheckedChange = { on ->
                            crossfadeOn.value = on
                            settingsRepo.saveCrossfadeEnabled(on)
                        }
                    )
                },
                colors = style.listItemColors
            )
            AnimatedVisibility(visible = crossfadeOn.value) {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        stringResource(R.string.settings_crossfade_duration, crossfadeSeconds.toInt()),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    androidx.compose.material3.Slider(
                        value = crossfadeSeconds,
                        onValueChange = { crossfadeSeconds = it.toInt().toFloat() },
                        onValueChangeFinished = { settingsRepo.saveCrossfadeSeconds(crossfadeSeconds.toInt()) },
                        valueRange = 5f..12f,
                        steps = 6
                    )
                    Text(
                        stringResource(R.string.settings_crossfade_note),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // AUTOMIX: iguala tempo y beat al hacer el fundido (BeatWeave + velocidad de ExoPlayer).
                    var automixOn by remember { mutableStateOf(settingsRepo.getAutomixEnabled()) }
                    ListItem(
                        headlineContent = { Text("AutoMix") },
                        supportingContent = { Text("Sincroniza el tempo y el beat al hacer el fundido") },
                        trailingContent = {
                            Switch(
                                checked = automixOn,
                                onCheckedChange = { on ->
                                    automixOn = on
                                    settingsRepo.saveAutomixEnabled(on)
                                    // Avisa al servicio (descarga el modelo y analiza la canción actual).
                                    context.sendBroadcast(
                                        android.content.Intent("com.music.musicflame.UPDATE_EQ")
                                            .setPackage(context.packageName)
                                            .putExtra("automix_changed", true)
                                    )
                                }
                            )
                        },
                        colors = style.listItemColors
                    )
                    AnimatedVisibility(visible = automixOn) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                            val automixCache = remember { com.music.musicflame.data.AutomixCacheRepository(context) }
                            var analyzedCount by remember { mutableIntStateOf(0) }
                            val libraryTotal = com.music.musicflame.data.SongLibraryHolder.songs.size
                            val libraryRunning by com.music.musicflame.audio.AutomixLibraryAnalysis.running.collectAsState()
                            val libraryProgress by com.music.musicflame.audio.AutomixLibraryAnalysis.progress.collectAsState()

                            // Contador: se refresca cada 3 s (el análisis ocurre en segundo plano).
                            LaunchedEffect(Unit) {
                                while (true) {
                                    analyzedCount = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                        automixCache.countAnalyzed()
                                    }
                                    kotlinx.coroutines.delay(3000)
                                }
                            }
                            Text(
                                "Analizadas: $analyzedCount / $libraryTotal canciones (el resto se analiza al reproducirse)",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val automixStatus by com.music.musicflame.audio.AutomixStatus.text.collectAsState()
                            if (automixStatus.isNotEmpty()) {
                                Text(
                                    automixStatus,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            // Cuánto audio se analiza por canción: velocidad vs. precisión del final.
                            val analysisSteps = remember { listOf(40, 60, 90, 120, 180, 240) }
                            var analysisSeconds by remember { mutableIntStateOf(settingsRepo.getAutomixAnalysisSeconds()) }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Audio a analizar por canción: ${analysisSeconds / 60}:${"%02d".format(analysisSeconds % 60)} min",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Slider(
                                value = analysisSteps.indexOf(analysisSeconds).coerceAtLeast(0).toFloat(),
                                onValueChange = {
                                    analysisSeconds = analysisSteps[Math.round(it).coerceIn(0, analysisSteps.lastIndex)]
                                },
                                onValueChangeFinished = { settingsRepo.saveAutomixAnalysisSeconds(analysisSeconds) },
                                valueRange = 0f..analysisSteps.lastIndex.toFloat(),
                                steps = analysisSteps.size - 2
                            )
                            val half = analysisSeconds / 2
                            Text(
                                "Analiza $half s del inicio y $half s del final (si la canción dura menos, la analiza entera). " +
                                    "Tiempo de análisis ≈ ${analysisSeconds * 100 / 240} % del máximo (estimado, depende del celular). " +
                                    when {
                                        analysisSeconds <= 60 ->
                                            "Muy rápido: el tempo suele salir bien, pero un cambio de ritmo a mitad de canción o un final muy distinto pueden pasar desapercibidos."
                                        analysisSeconds <= 120 ->
                                            "Equilibrado (recomendado): mide el tempo al inicio y confirma el último beat en el final, que es donde se hace el fundido."
                                        else ->
                                            "Más lento: casi toda canción se analiza completa. Ayuda poco más al fundido; úsalo si tu música cambia mucho de tempo."
                                    } +
                                    " Aplica a canciones nuevas; para re-analizar las ya guardadas usa \"Borrar caché de BPM\".",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(4.dp))
                            OutlinedButton(
                                onClick = {
                                    com.music.musicflame.audio.AutomixLibraryAnalysis.start(
                                        context,
                                        com.music.musicflame.data.SongLibraryHolder.songs,
                                        automixCache
                                    )
                                },
                                enabled = !libraryRunning
                            ) {
                                Text("Analizar biblioteca completa")
                            }
                            if (libraryRunning) {
                                Spacer(Modifier.height(4.dp))
                                androidx.compose.material3.LinearProgressIndicator(
                                    progress = { libraryProgress },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                TextButton(onClick = { com.music.musicflame.audio.AutomixLibraryAnalysis.cancel() }) {
                                    Text("Cancelar análisis", fontSize = 12.sp)
                                }
                            }
                            TextButton(
                                onClick = {
                                    automixCache.clearAll()
                                    analyzedCount = 0
                                }
                            ) {
                                Text("Borrar caché de BPM", fontSize = 12.sp)
                            }

                            // Diagnóstico de AutoMix: solo en builds de depuración o para la cuenta dueña.
                            val showAutomixDiag = remember {
                                com.music.musicflame.BuildConfig.DEBUG ||
                                        com.music.musicflame.data.LicenseRepository(context).isOwnerAccount()
                            }
                            if (showAutomixDiag) {
                                val diagStore = remember { com.music.musicflame.data.SettingsStore.get(context) }
                                var diagCurrent by remember { mutableStateOf("") }
                                var diagNext by remember { mutableStateOf("") }
                                var diagPlan by remember { mutableStateOf("") }
                                var phaseOffsetMs by remember { mutableStateOf(diagStore.getFloat("automix_phase_offset_ms", 0f)) }
                                LaunchedEffect(Unit) {
                                    while (true) {
                                        diagCurrent = diagStore.getString("automix_diag_current", null) ?: ""
                                        diagNext = diagStore.getString("automix_diag_next", null) ?: ""
                                        diagPlan = diagStore.getString("automix_diag_plan", null) ?: ""
                                        kotlinx.coroutines.delay(1000)
                                    }
                                }
                                val cur = diagCurrent.split('|')
                                val nxt = diagNext.split('|')
                                val curBpm = cur.getOrNull(0)?.toFloatOrNull()
                                val nxtBpm = nxt.getOrNull(0)?.toFloatOrNull()
                                val ratioTxt = if (curBpm != null && nxtBpm != null && curBpm > 0f) {
                                    String.format(java.util.Locale.US, "%.2fx", nxtBpm / curBpm)
                                } else "—"
                                Text(
                                    "BPM actual: ${curBpm?.let { String.format(java.util.Locale.US, "%.1f", it) } ?: "—"}" +
                                            " · siguiente: ${nxtBpm?.let { String.format(java.util.Locale.US, "%.1f", it) } ?: "—"}" +
                                            " · ratio: $ratioTxt",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                val plan = diagPlan.split('|')
                                val planText = when {
                                    diagPlan.isEmpty() -> "Cue point: — (aún sin fundido)"
                                    plan[0] == "NOTE" -> plan.drop(1).joinToString("|")
                                    else -> {
                                        val cueS = (plan.getOrNull(1)?.toLongOrNull() ?: 0L) / 1000L
                                        val err = plan.getOrNull(5)?.takeIf { it != "-" }?.let { " · desfase de beat: $it ms" } ?: ""
                                        String.format(
                                            java.util.Locale.US, "Cue point: %02d:%02d:%02d · confianza: %s%%%s",
                                            cueS / 3600, (cueS % 3600) / 60, cueS % 60, plan.getOrNull(4) ?: "?", err
                                        )
                                    }
                                }
                                Text(planText, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                Text(
                                    "Ajuste de fase del beat: " + String.format(java.util.Locale.US, "%+.0f", phaseOffsetMs) +
                                            " ms (si el beat de la cola suena siempre adelantado/atrasado, corrígelo aquí; aplica en el siguiente fundido)",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                androidx.compose.material3.Slider(
                                    value = phaseOffsetMs,
                                    onValueChange = { phaseOffsetMs = kotlin.math.round(it / 5f) * 5f },
                                    onValueChangeFinished = {
                                        diagStore.edit().putFloat("automix_phase_offset_ms", phaseOffsetMs).apply()
                                    },
                                    valueRange = -150f..150f,
                                    steps = 59
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                        }
                    }
                    // Diagnóstico: solo en builds de depuración o para la cuenta dueña.
                    val showCrossfadeDiag = remember {
                        com.music.musicflame.BuildConfig.DEBUG ||
                                com.music.musicflame.data.LicenseRepository(context).isOwnerAccount()
                    }
                    if (showCrossfadeDiag) {
                        var crossfadeStatus by remember { mutableStateOf("") }
                        var tailFxStatus by remember { mutableStateOf("") }
                        LaunchedEffect(Unit) {
                            val sp = com.music.musicflame.data.SettingsStore.get(context)
                            while (true) {
                                tailFxStatus = sp.getString("crossfade_tail_fx", null) ?: ""
                                crossfadeStatus = sp.getString("crossfade_status", null)
                                    ?: "Sin datos todavía (pon una canción)"
                                kotlinx.coroutines.delay(1000)
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Diagnóstico: $crossfadeStatus",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (tailFxStatus.isNotEmpty()) {
                            Text(
                                tailFxStatus,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        val diagPrefs = remember { com.music.musicflame.data.SettingsStore.get(context) }
                        var tailTrimDb by remember { mutableStateOf(diagPrefs.getFloat("crossfade_tail_gain_db", 0f)) }
                        Text(
                            "Volumen de la cola: " + String.format("%+.1f", tailTrimDb) +
                                    " dB (sube si al empezar el fundido baja el volumen; aplica desde la siguiente canción)",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        androidx.compose.material3.Slider(
                            value = tailTrimDb,
                            onValueChange = { tailTrimDb = kotlin.math.round(it * 2f) / 2f },
                            onValueChangeFinished = {
                                diagPrefs.edit().putFloat("crossfade_tail_gain_db", tailTrimDb).apply()
                            },
                            valueRange = -6f..9f,
                            steps = 29
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                }
            }
            SettingsDivider(style)
        }
    }

    // AUDIO 8D + BALANCE: el switch lo activa al instante; tocar la fila abre sus ajustes.
    item {
        SettingsRow(style, SettingsRowPos.Middle) {
            val audio8dPrefs = remember { com.music.musicflame.data.SettingsStore.get(context) }
            val audio8dOn = remember { mutableStateOf(audio8dPrefs.getBoolean("audio8d_enabled", false)) }
            val showAudio8dDialog = remember { mutableStateOf(false) }
            ListItem(
                headlineContent = { Text(stringResource(R.string.audio8d_title)) },
                supportingContent = { Text(stringResource(R.string.audio8d_desc)) },
                trailingContent = {
                    Switch(
                        checked = audio8dOn.value,
                        onCheckedChange = { on ->
                            audio8dOn.value = on
                            audio8dPrefs.edit().putBoolean("audio8d_enabled", on).apply()
                            context.sendBroadcast(android.content.Intent("com.music.musicflame.UPDATE_EQ").setPackage(context.packageName))
                        }
                    )
                },
                modifier = Modifier.clickable { showAudio8dDialog.value = true },
                colors = style.listItemColors
            )
            SettingsDivider(style)
            if (showAudio8dDialog.value) {
                Audio8DDialog(onDismiss = {
                    showAudio8dDialog.value = false
                    // Por si lo prendió/apagó dentro del diálogo, se refleja en el switch de la fila.
                    audio8dOn.value = audio8dPrefs.getBoolean("audio8d_enabled", false)
                })
            }
        }
    }

    // SOLO DUEÑO: muestra el ajuste en dB que el servicio le puso a la canción actual.
    item {
        SettingsRow(style, SettingsRowPos.Middle) {
            val isOwner = remember { com.music.musicflame.data.LicenseRepository(context).isOwnerAccount() }
            if (isOwner) {
                var normStatus by remember { mutableStateOf("") }
                LaunchedEffect(Unit) {
                    val sp = com.music.musicflame.data.SettingsStore.get(context)
                    while (true) {
                        normStatus = sp.getString("volume_normalization_status", null)
                            ?: "Sin datos todavía (pon una canción)"
                        kotlinx.coroutines.delay(1000)
                    }
                }
                ListItem(
                    headlineContent = { Text("Diagnóstico de volumen (solo dueño)") },
                    supportingContent = { Text(normStatus) },
                    colors = style.listItemColors
                )
                SettingsDivider(style)
            }
        }
    }

    item {
        SettingsRow(style, SettingsRowPos.Last) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_optimizacion_de_bateria)) },
                supportingContent = {
                    Text(
                        text = if (isIgnoringBattery)
                            stringResource(R.string.settings_optimizado_para_musica_continua_re)
                        else
                            stringResource(R.string.settings_restringido_android_podria_pausar),
                        color = if (isIgnoringBattery) style.trailingColor else MaterialTheme.colorScheme.error
                    )
                },
                trailingContent = {
                    Switch(
                        checked = isIgnoringBattery,
                        onCheckedChange = { checked ->
                            if (checked) {
                                try {
                                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, context.getString(R.string.settings_no_se_pudo_abrir_la_configuracion), Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                context.startActivity(intent)
                            }
                        }
                    )
                },
                colors = style.listItemColors
            )
        }
    }

    item { SettingsSectionHeader(style, stringResource(R.string.settings_ecualizador)) }

    item {
        SettingsRow(style, SettingsRowPos.First) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_studio_pro_eq)) },
                supportingContent = { Text(stringResource(R.string.settings_preset_activo_1_s, eqPresetSelected.value)) },
                trailingContent = {
                    Button(
                        onClick = { showEqualizerDialog.value = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) { Text(stringResource(R.string.settings_abrir_consola), fontWeight = FontWeight.ExtraBold) }
                },
                colors = style.listItemColors
            )
            SettingsDivider(style)
        }
    }

    item {
        SettingsRow(style, SettingsRowPos.Last) {
            val isProEqUnlocked = com.music.musicflame.data.ProStatusHolder.isItemUnlocked("pro_eq_10band")
            ListItem(
                headlineContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.settings_eq_pro_10_bandas))
                        if (!isProEqUnlocked) {
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.Filled.Lock, contentDescription = stringResource(R.string.settings_bloqueado), modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(2.dp))
                            Text("\$${com.music.musicflame.data.PaymentCatalog.ITEMS.find { it.id == "pro_eq_10band" }?.priceMxn ?: 20} MXN", fontSize = 10.sp, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                        }
                    }
                },
                supportingContent = {
                    Text(
                        if (isProEqUnlocked) stringResource(R.string.settings_motor_propio_pre_amp_presets_y_nor)
                        else stringResource(R.string.settings_funcion_de_pago_toca_para_ver_mas)
                    )
                },
                trailingContent = {
                    Button(
                        onClick = { showProEqualizerDialog.value = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) { Text(if (isProEqUnlocked) stringResource(R.string.settings_abrir) else stringResource(R.string.settings_ver), fontWeight = FontWeight.ExtraBold) }
                },
                colors = style.listItemColors
            )
        }
    }

}