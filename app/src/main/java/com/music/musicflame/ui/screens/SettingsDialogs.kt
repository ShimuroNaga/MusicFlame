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
import com.music.musicflame.data.SavingsGoalRepository
import com.music.musicflame.data.SettingsRepository
import com.music.musicflame.together.SupabaseSalas
import com.music.musicflame.ui.theme.LocalAppTextColor
import com.music.musicflame.widget.MusicFlameWidgetProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Diálogos de Ajustes. Código movido tal cual desde SettingsScreen.kt (sin cambios de comportamiento). */

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsDurationFilterDialog(
    context: Context,
    settingsRepo: SettingsRepository,
    showDurationFilterDialog: MutableState<Boolean>,
    durationMin: MutableState<String>,
    durationMax: MutableState<String>,
    filterMode: MutableState<String>,
    refreshScope: kotlinx.coroutines.CoroutineScope
) {
    if (showDurationFilterDialog.value) {
        val tempMin = remember { mutableStateOf(durationMin.value) }
        val tempMax = remember { mutableStateOf(durationMax.value) }
        val tempMode = remember { mutableStateOf(filterMode.value) }
        AlertDialog(
            onDismissRequest = { showDurationFilterDialog.value = false },
            title = { Text(stringResource(R.string.settings_filtrar_por_duracion), fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(value = tempMin.value, onValueChange = { tempMin.value = it }, label = { Text(stringResource(R.string.settings_duracion_minima_segundos)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = tempMax.value, onValueChange = { tempMax.value = it }, label = { Text(stringResource(R.string.settings_duracion_maxima_segundos)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.settings_modo), fontWeight = FontWeight.Bold)
                    TextButton(onClick = { tempMode.value = "exclude" }) { Text(stringResource(R.string.settings_excluir), fontWeight = FontWeight.Bold, color = if (tempMode.value == "exclude") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) }
                    TextButton(onClick = { tempMode.value = "only" }) { Text(stringResource(R.string.settings_solo_mostrar), fontWeight = FontWeight.Bold, color = if (tempMode.value == "only") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val minVal = tempMin.value.toIntOrNull() ?: 0
                    val maxVal = tempMax.value.toIntOrNull() ?: Int.MAX_VALUE
                    settingsRepo.saveDurationFilterMin(minVal)
                    settingsRepo.saveDurationFilterMax(maxVal)
                    settingsRepo.saveDurationFilterMode(tempMode.value)
                    // El filtro cambia qué canciones cuentan como parte de la
                    // librería, así que el cache compartido necesita re-escanear.
                    refreshScope.launch { com.music.musicflame.data.SongLibraryHolder.refresh(context) }
                    durationMin.value = tempMin.value
                    durationMax.value = tempMax.value
                    filterMode.value = tempMode.value
                    showDurationFilterDialog.value = false
                }) { Text(stringResource(R.string.action_save), fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showDurationFilterDialog.value = false }) { Text(stringResource(R.string.action_cancel), fontWeight = FontWeight.Bold) } }
        )
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsAudioFormatsDialog(
    style: SettingsStyle,
    context: Context,
    settingsRepo: SettingsRepository,
    showAudioFormatsDialog: MutableState<Boolean>,
    refreshScope: kotlinx.coroutines.CoroutineScope
) {
    if (showAudioFormatsDialog.value) {
        // Se muestra SIEMPRE el catálogo completo del repo (los 9 formatos
        // usables ya documentados en RealTagWriter, más .aac no usable y
        // .m3u informativo), no solo lo detectado en este dispositivo en
        // particular — así el usuario puede pre-activar/ocultar formatos
        // aunque su biblioteca actual solo tenga, por ejemplo, mp3.
        // detectPresentAudioExtensions() se usa aparte solo para marcar
        // cuáles SÍ están presentes ahora mismo en su biblioteca.
        val detectedExtensions = remember { com.music.musicflame.data.detectPresentAudioExtensions(context) }
        val displayFormats = remember { com.music.musicflame.data.AudioFormatCatalog.ALL_FORMATS }
        // Copia editable de los formatos ocultos: se guarda solo al presionar "Guardar".
        val tempHiddenFormats = remember { mutableStateOf(settingsRepo.getHiddenAudioFormats()) }

        AlertDialog(
            onDismissRequest = { showAudioFormatsDialog.value = false },
            title = { Text(stringResource(R.string.settings_formatos_de_audio_a_escuchar), fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        stringResource(R.string.settings_los_que_ya_tienes_en_tu_biblioteca),
                        color = style.trailingColor,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Column(
                        modifier = Modifier
                            .heightIn(max = 420.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        displayFormats.forEach { format ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (format.isPlaylistFormat) {
                                    // .m3u es informativo: no participa del filtro de la
                                    // librería de canciones, así que no lleva checkbox.
                                    Spacer(Modifier.width(48.dp))
                                } else {
                                    Checkbox(
                                        checked = !tempHiddenFormats.value.contains(format.extension),
                                        onCheckedChange = { checked ->
                                            tempHiddenFormats.value = if (checked) {
                                                tempHiddenFormats.value - format.extension
                                            } else {
                                                tempHiddenFormats.value + format.extension
                                            }
                                        }
                                    )
                                }
                                Column(modifier = Modifier.padding(start = 8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(format.displayName, fontWeight = FontWeight.Bold)
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = when {
                                                format.isPlaylistFormat -> stringResource(R.string.settings_solo_playlists)
                                                format.usable -> stringResource(R.string.settings_usable)
                                                else -> stringResource(R.string.settings_no_usable)
                                            },
                                            fontWeight = FontWeight.Bold,
                                            color = when {
                                                format.isPlaylistFormat -> MaterialTheme.colorScheme.secondary
                                                format.usable -> MaterialTheme.colorScheme.primary
                                                else -> MaterialTheme.colorScheme.error
                                            }
                                        )
                                        if (!format.isPlaylistFormat && detectedExtensions.contains(format.extension)) {
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                text = stringResource(R.string.settings_detectado),
                                                color = style.trailingColor
                                            )
                                        }
                                    }
                                    format.noteRes?.let { note ->
                                        Text(text = stringResource(note), color = style.trailingColor)
                                    }
                                }
                            }
                            HorizontalDivider(color = style.dividerColor)
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    settingsRepo.saveHiddenAudioFormats(tempHiddenFormats.value)
                    // Los formatos ocultos cambian qué canciones cuentan como parte
                    // de la librería, así que el cache compartido necesita re-escanear
                    // (mismo patrón que el filtro de duración de arriba).
                    refreshScope.launch { com.music.musicflame.data.SongLibraryHolder.refresh(context) }
                    showAudioFormatsDialog.value = false
                }) { Text(stringResource(R.string.action_save), fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showAudioFormatsDialog.value = false }) { Text(stringResource(R.string.action_cancel), fontWeight = FontWeight.Bold) } }
        )
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsSleepTimerDialog(
    playerManager: com.music.musicflame.data.MusicPlayerManager,
    showSleepTimerDialog: MutableState<Boolean>
) {
    if (showSleepTimerDialog.value) {
        val customMinutes = remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showSleepTimerDialog.value = false },
            title = { Text(stringResource(R.string.settings_temporizador_de_apagado), fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    listOf(15, 30, 45, 60, 90).forEach { minutes ->
                        TextButton(
                            onClick = {
                                playerManager.startSleepTimer(minutes)
                                showSleepTimerDialog.value = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.settings_1_s_minutos, minutes), modifier = Modifier.fillMaxWidth(), fontWeight = FontWeight.Bold)
                        }
                    }
                    TextButton(
                        onClick = {
                            playerManager.startSleepTimerEndOfSong()
                            showSleepTimerDialog.value = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.settings_al_terminar_la_cancion_actual), modifier = Modifier.fillMaxWidth(), fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = customMinutes.value,
                        onValueChange = { customMinutes.value = it.filter { c -> c.isDigit() } },
                        label = { Text(stringResource(R.string.settings_minutos_personalizados)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val minutes = customMinutes.value.toIntOrNull()
                    if (minutes != null && minutes > 0) {
                        playerManager.startSleepTimer(minutes)
                    }
                    showSleepTimerDialog.value = false
                }) { Text(stringResource(R.string.settings_iniciar), fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showSleepTimerDialog.value = false }) { Text(stringResource(R.string.action_cancel), fontWeight = FontWeight.Bold) } }
        )
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsThemeDialog(
    settingsRepo: SettingsRepository,
    showThemeDialog: MutableState<Boolean>,
    appTheme: MutableState<String>
) {
    if (showThemeDialog.value) {
        val tempTheme = remember { mutableStateOf(appTheme.value) }
        AlertDialog(
            onDismissRequest = { showThemeDialog.value = false },
            title = { Text(stringResource(R.string.settings_elegir_tema), fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    listOf("Siguiendo al sistema", "Fondo blanco", "Fondo oscuro").forEach { theme ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { tempTheme.value = theme }.padding(vertical = 8.dp)) {
                            RadioButton(selected = tempTheme.value == theme, onClick = { tempTheme.value = theme })
                            Spacer(Modifier.width(8.dp))
                            Text(settingsOptionLabel(theme), fontSize = 14.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    settingsRepo.saveAppTheme(tempTheme.value)
                    appTheme.value = tempTheme.value
                    showThemeDialog.value = false
                }) { Text(stringResource(R.string.action_save), fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showThemeDialog.value = false }) { Text(stringResource(R.string.action_cancel), fontWeight = FontWeight.Bold) } }
        )
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsEqualizerStyleDialog(
    settingsRepo: SettingsRepository,
    showEqualizerStyleDialog: MutableState<Boolean>,
    equalizerStyle: MutableState<com.music.musicflame.ui.components.EqualizerStyle>,
    unlockedIds: Set<String>,
    showLockedFeatureToast: (String) -> Unit
) {
    if (showEqualizerStyleDialog.value) {
        com.music.musicflame.ui.components.EqualizerStylePickerDialog(
            currentStyle = equalizerStyle.value,
            isStyleUnlocked = { style -> style.catalogId == null || unlockedIds.contains(style.catalogId) },
            onDismiss = { showEqualizerStyleDialog.value = false },
            onConfirm = { newStyle ->
                equalizerStyle.value = newStyle
                settingsRepo.saveEqualizerStyle(newStyle)
                showEqualizerStyleDialog.value = false
            },
            onLockedStyleClick = { style -> showLockedFeatureToast(style.catalogId.orEmpty()) }
        )
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsFontDialog(
    settingsRepo: SettingsRepository,
    showFontDialog: MutableState<Boolean>,
    appFontPref: MutableState<com.music.musicflame.ui.theme.AppFont>
) {
    if (showFontDialog.value) {
        com.music.musicflame.ui.components.AppFontPickerDialog(
            currentFont = appFontPref.value,
            onDismiss = { showFontDialog.value = false },
            onConfirm = { newFont ->
                appFontPref.value = newFont
                settingsRepo.saveAppFont(newFont.id)
                showFontDialog.value = false
            }
        )
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsFontSizeDialog(
    settingsRepo: SettingsRepository,
    showFontSizeDialog: MutableState<Boolean>,
    appFontPref: MutableState<com.music.musicflame.ui.theme.AppFont>,
    appFontSizePref: MutableState<Float>
) {
    if (showFontSizeDialog.value) {
        com.music.musicflame.ui.components.AppFontSizeDialog(
            currentSizeSp = appFontSizePref.value,
            previewFontFamily = appFontPref.value.fontFamily,
            onDismiss = { showFontSizeDialog.value = false },
            onConfirm = { newSizeSp ->
                appFontSizePref.value = newSizeSp
                settingsRepo.saveAppFontSizeSp(newSizeSp)
                showFontSizeDialog.value = false
            }
        )
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsLanguageDialog(
    context: Context,
    showLanguageDialog: MutableState<Boolean>,
    appLanguagePref: MutableState<com.music.musicflame.data.AppLanguage>
) {
    if (showLanguageDialog.value) {
        com.music.musicflame.ui.components.LanguagePickerDialog(
            currentLanguage = appLanguagePref.value,
            onDismiss = { showLanguageDialog.value = false },
            onConfirm = { newLanguage ->
                showLanguageDialog.value = false
                if (newLanguage != appLanguagePref.value) {
                    appLanguagePref.value = newLanguage
                    // Android 13+: el sistema recrea la Activity solo. Android 12/12L: recreate().
                    with(com.music.musicflame.data.LanguageManager) {
                        context.findActivity()?.let { apply(it, newLanguage) }
                    }
                }
            }
        )
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsAlbumArtShapeDialog(
    onAlbumArtShapeChanged: (com.music.musicflame.AlbumArtShapeType) -> Unit,
    onCoverDesignChanged: (com.music.musicflame.data.CoverDesign?) -> Unit,
    context: Context,
    settingsRepo: SettingsRepository,
    albumArtShapePref: MutableState<com.music.musicflame.AlbumArtShapeType>,
    showAlbumArtShapeDialog: MutableState<Boolean>,
    coverDesigns: MutableState<List<com.music.musicflame.data.CoverDesign>>,
    activeCoverDesignId: MutableState<String?>,
    shapeDialogShape: MutableState<com.music.musicflame.AlbumArtShapeType>,
    shapeDialogDesignId: MutableState<String?>,
    showCoverEditor: MutableState<Boolean>,
    editingCoverDesign: MutableState<com.music.musicflame.data.CoverDesign?>,
    coverDesignToDelete: MutableState<com.music.musicflame.data.CoverDesign?>
) {
    if (showAlbumArtShapeDialog.value) {
        AlertDialog(
            onDismissRequest = { showAlbumArtShapeDialog.value = false },
            title = { Text(stringResource(R.string.settings_forma_de_la_caratula), fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    listOf(
                        com.music.musicflame.AlbumArtShapeType.SQUARE to stringResource(R.string.settings_cuadrado),
                        com.music.musicflame.AlbumArtShapeType.CIRCLE to stringResource(R.string.settings_circulo),
                        com.music.musicflame.AlbumArtShapeType.HEXAGON to stringResource(R.string.settings_hexagono),
                        com.music.musicflame.AlbumArtShapeType.VINYL to stringResource(R.string.settings_vinilo),
                        com.music.musicflame.AlbumArtShapeType.SQUIRCLE to stringResource(R.string.settings_squircle)
                    ).forEach { (shape, label) ->
                        val isSelected = shapeDialogShape.value == shape
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { shapeDialogShape.value = shape }
                                .padding(vertical = 10.dp)
                        ) {
                            com.music.musicflame.ui.components.AlbumArtShapePreview(
                                shape = shape,
                                size = 40.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                            )
                            Spacer(Modifier.width(14.dp))
                            Text(
                                label,
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.weight(1f)
                            )
                            RadioButton(selected = isSelected, onClick = { shapeDialogShape.value = shape })
                        }
                    }

                    // --- Diseños propios ---
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text(
                        stringResource(R.string.settings_mis_disenos),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                    coverDesigns.value.forEach { design ->
                        val isSelected = shapeDialogShape.value == com.music.musicflame.AlbumArtShapeType.CUSTOM && shapeDialogDesignId.value == design.id
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    shapeDialogShape.value = com.music.musicflame.AlbumArtShapeType.CUSTOM
                                    shapeDialogDesignId.value = design.id
                                }
                                .padding(vertical = 6.dp)
                        ) {
                            com.music.musicflame.ui.components.AlbumArtShapePreview(
                                shape = com.music.musicflame.AlbumArtShapeType.CUSTOM,
                                size = 40.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                customDesign = design
                            )
                            Spacer(Modifier.width(14.dp))
                            Text(
                                design.name,
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = {
                                editingCoverDesign.value = design
                                showCoverEditor.value = true
                            }) { Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.settings_editar_diseno)) }
                            IconButton(onClick = { coverDesignToDelete.value = design }) {
                                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.settings_borrar_diseno))
                            }
                            RadioButton(selected = isSelected, onClick = {
                                shapeDialogShape.value = com.music.musicflame.AlbumArtShapeType.CUSTOM
                                shapeDialogDesignId.value = design.id
                            })
                        }
                    }
                    TextButton(
                        onClick = {
                            editingCoverDesign.value = null
                            showCoverEditor.value = true
                        },
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.settings_crear_diseno), fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    // Un diseño propio solo es válido si todavía existe en la lista.
                    val chosenDesign = if (shapeDialogShape.value == com.music.musicflame.AlbumArtShapeType.CUSTOM)
                        coverDesigns.value.firstOrNull { it.id == shapeDialogDesignId.value }
                    else null
                    val finalShape = if (shapeDialogShape.value == com.music.musicflame.AlbumArtShapeType.CUSTOM && chosenDesign == null)
                        com.music.musicflame.AlbumArtShapeType.SQUARE
                    else shapeDialogShape.value
                    settingsRepo.saveAlbumArtShape(finalShape)
                    if (chosenDesign != null) {
                        settingsRepo.saveActiveCoverDesignId(chosenDesign.id)
                        activeCoverDesignId.value = chosenDesign.id
                    }
                    albumArtShapePref.value = finalShape
                    onAlbumArtShapeChanged(finalShape)
                    onCoverDesignChanged(settingsRepo.getActiveCoverDesign())
                    // Los widgets leen la forma al dibujarse: refrescarlos para que la tomen ya.
                    MusicFlameWidgetProvider.refreshAllWidgets(context)
                    showAlbumArtShapeDialog.value = false
                }) { Text(stringResource(R.string.action_save), fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showAlbumArtShapeDialog.value = false }) { Text(stringResource(R.string.action_cancel), fontWeight = FontWeight.Bold) } }
        )
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsCoverEditorDialog(
    onCoverDesignChanged: (com.music.musicflame.data.CoverDesign?) -> Unit,
    playerManager: com.music.musicflame.data.MusicPlayerManager,
    context: Context,
    settingsRepo: SettingsRepository,
    albumArtShapePref: MutableState<com.music.musicflame.AlbumArtShapeType>,
    coverDesigns: MutableState<List<com.music.musicflame.data.CoverDesign>>,
    activeCoverDesignId: MutableState<String?>,
    shapeDialogShape: MutableState<com.music.musicflame.AlbumArtShapeType>,
    shapeDialogDesignId: MutableState<String?>,
    showCoverEditor: MutableState<Boolean>,
    editingCoverDesign: MutableState<com.music.musicflame.data.CoverDesign?>
) {
    if (showCoverEditor.value) {
        com.music.musicflame.ui.components.CoverDesignEditorDialog(
            initial = editingCoverDesign.value,
            previewSong = playerManager.currentSong.value,
            onDismiss = { showCoverEditor.value = false },
            onSave = { saved ->
                val list = coverDesigns.value
                val updated = if (list.any { it.id == saved.id }) list.map { if (it.id == saved.id) saved else it } else list + saved
                settingsRepo.saveCoverDesigns(updated)
                coverDesigns.value = updated
                // Si se editó el diseño que está en uso, se aplica al instante (app y widgets).
                if (albumArtShapePref.value == com.music.musicflame.AlbumArtShapeType.CUSTOM && activeCoverDesignId.value == saved.id) {
                    onCoverDesignChanged(saved)
                    MusicFlameWidgetProvider.refreshAllWidgets(context)
                }
                // El diálogo de forma deja marcado el diseño recién guardado (se aplica con "Guardar").
                shapeDialogShape.value = com.music.musicflame.AlbumArtShapeType.CUSTOM
                shapeDialogDesignId.value = saved.id
                showCoverEditor.value = false
            }
        )
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsCoverDesignDeleteDialog(
    onAlbumArtShapeChanged: (com.music.musicflame.AlbumArtShapeType) -> Unit,
    onCoverDesignChanged: (com.music.musicflame.data.CoverDesign?) -> Unit,
    context: Context,
    settingsRepo: SettingsRepository,
    albumArtShapePref: MutableState<com.music.musicflame.AlbumArtShapeType>,
    coverDesigns: MutableState<List<com.music.musicflame.data.CoverDesign>>,
    activeCoverDesignId: MutableState<String?>,
    shapeDialogShape: MutableState<com.music.musicflame.AlbumArtShapeType>,
    shapeDialogDesignId: MutableState<String?>,
    coverDesignToDelete: MutableState<com.music.musicflame.data.CoverDesign?>
) {
    coverDesignToDelete.value?.let { design ->
        AlertDialog(
            onDismissRequest = { coverDesignToDelete.value = null },
            title = { Text(stringResource(R.string.settings_borrar_diseno), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.settings_borrar_1_s_esta_accion_no_se_puede, design.name)) },
            confirmButton = {
                TextButton(onClick = {
                    val remaining = coverDesigns.value.filter { it.id != design.id }
                    settingsRepo.saveCoverDesigns(remaining)
                    coverDesigns.value = remaining
                    if (shapeDialogDesignId.value == design.id) {
                        shapeDialogDesignId.value = null
                        if (shapeDialogShape.value == com.music.musicflame.AlbumArtShapeType.CUSTOM) shapeDialogShape.value = com.music.musicflame.AlbumArtShapeType.SQUARE
                    }
                    // Si era el diseño en uso, vuelve a cuadrado para no dejar una forma sin definición.
                    if (activeCoverDesignId.value == design.id) {
                        settingsRepo.saveActiveCoverDesignId(null)
                        activeCoverDesignId.value = null
                        if (albumArtShapePref.value == com.music.musicflame.AlbumArtShapeType.CUSTOM) {
                            settingsRepo.saveAlbumArtShape(com.music.musicflame.AlbumArtShapeType.SQUARE)
                            albumArtShapePref.value = com.music.musicflame.AlbumArtShapeType.SQUARE
                            onAlbumArtShapeChanged(com.music.musicflame.AlbumArtShapeType.SQUARE)
                        }
                        onCoverDesignChanged(null)
                        MusicFlameWidgetProvider.refreshAllWidgets(context)
                    }
                    coverDesignToDelete.value = null
                }) { Text(stringResource(R.string.action_clear), fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { coverDesignToDelete.value = null }) { Text(stringResource(R.string.action_cancel), fontWeight = FontWeight.Bold) } }
        )
    }
}

// Diálogos de color → SettingsColorDialogs.kt
// Diálogos de fondo → SettingsBgDialogs.kt
// Diálogos de ecualizador → SettingsEqualizerDialog.kt
