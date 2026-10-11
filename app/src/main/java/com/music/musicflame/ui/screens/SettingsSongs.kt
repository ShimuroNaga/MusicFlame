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
import com.music.musicflame.data.LicenseStatus
import com.music.musicflame.data.LicenseValidationResult
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
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_filtrar_por_duracion)) },
            supportingContent = { Text(stringResource(R.string.settings_excluir_o_mostrar_solo_canciones_d)) },
            trailingContent = { TextButton(onClick = { showDurationFilterDialog.value = true }) { Text(stringResource(R.string.settings_configurar), fontWeight = FontWeight.ExtraBold, color = style.trailingColor) } },
            colors = style.listItemColors
        )
        HorizontalDivider(color = style.dividerColor)
    }

    item {
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_formatos_de_audio_a_escuchar)) },
            supportingContent = { Text(stringResource(R.string.settings_elige_que_formatos_de_tu_bibliotec)) },
            trailingContent = { TextButton(onClick = { showAudioFormatsDialog.value = true }) { Text(stringResource(R.string.settings_ver_formatos), fontWeight = FontWeight.ExtraBold, color = style.trailingColor) } },
            colors = style.listItemColors
        )
        HorizontalDivider(color = style.dividerColor)
    }

    item {
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
        HorizontalDivider(color = style.dividerColor)
    }

    item {
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
        HorizontalDivider(color = style.dividerColor)
    }

    item {
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
        HorizontalDivider(color = style.dividerColor)
    }

    item { SettingsSectionHeader(style, stringResource(R.string.settings_reproduccion)) }

    item {
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
        HorizontalDivider(color = style.dividerColor)
    }
    item {
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
        HorizontalDivider(color = style.dividerColor)
    }

    item {
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
        HorizontalDivider(color = style.dividerColor)
    }

    item { SettingsSectionHeader(style, stringResource(R.string.settings_ecualizador)) }

    item {
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
        HorizontalDivider(color = style.dividerColor)
    }

    item {
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
        HorizontalDivider(color = style.dividerColor)
    }

}
