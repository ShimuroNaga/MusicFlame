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
    appFontPref: MutableState<com.music.musicflame.ui.theme.AppFont>,
    unlockedIds: Set<String>,
    showLockedFeatureToast: (String) -> Unit
) {
    if (showFontDialog.value) {
        com.music.musicflame.ui.components.AppFontPickerDialog(
            currentFont = appFontPref.value,
            // Las 5 fuentes premium se venden juntas como un solo "Paquete"
            // (ver PaymentCatalog/LicenseRepository), así que cualquiera de
            // sus ids representa el desbloqueo del paquete completo.
            isUnlocked = unlockedIds.contains("font_comfortaa"),
            onDismiss = { showFontDialog.value = false },
            onConfirm = { newFont ->
                appFontPref.value = newFont
                settingsRepo.saveAppFont(newFont.id)
                showFontDialog.value = false
            },
            onLockedFontClick = { showLockedFeatureToast("font_comfortaa") }
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

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsTextColorDialog(
    style: SettingsStyle,
    settingsRepo: SettingsRepository,
    showTextColorDialog: MutableState<Boolean>,
    unlockedIds: Set<String>,
    showLockedFeatureToast: (String) -> Unit,
    appTextColorPref: MutableState<String>,
    customTextColorHex: MutableState<String>
) {
    if (showTextColorDialog.value) {
        val tempTextColor = remember { mutableStateOf(appTextColorPref.value) }
        val tempCustomHex = remember { mutableStateOf(customTextColorHex.value) }
        AlertDialog(
            onDismissRequest = { showTextColorDialog.value = false },
            title = { Text(stringResource(R.string.settings_color_de_texto), fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    listOf("Adaptativo", "Personalizado", com.music.musicflame.ui.theme.COLOR_MODE_RAINBOW).forEach { colorOption ->
                        // Solo Arcoíris es de pago acá; Adaptativo y Personalizado son gratis.
                        val locked = !unlockedIds.contains("text_color_rainbow") && colorOption == com.music.musicflame.ui.theme.COLOR_MODE_RAINBOW
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .alpha(if (locked) 0.5f else 1f)
                                .clickable {
                                    if (locked) showLockedFeatureToast("text_color_rainbow") else tempTextColor.value = colorOption
                                }
                                .padding(vertical = 8.dp)
                        ) {
                            RadioButton(
                                selected = tempTextColor.value == colorOption,
                                enabled = !locked,
                                onClick = { if (locked) showLockedFeatureToast("text_color_rainbow") else tempTextColor.value = colorOption }
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(settingsOptionLabel(colorOption), fontSize = 14.sp)
                                    if (locked) {
                                        Spacer(Modifier.width(6.dp))
                                        Icon(Icons.Filled.Lock, contentDescription = stringResource(R.string.settings_bloqueado), modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.error)
                                        Spacer(Modifier.width(2.dp))
                                        Text("$5 MXN", fontSize = 10.sp, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                                    }
                                }
                                if (colorOption == "Adaptativo") {
                                    Text(
                                        stringResource(R.string.settings_blanco_o_negro_segun_el_fondo_como),
                                        fontSize = 11.sp,
                                        color = style.mediumEmphasis
                                    )
                                } else if (colorOption == com.music.musicflame.ui.theme.COLOR_MODE_RAINBOW) {
                                    Text(
                                        stringResource(R.string.settings_colores_del_espectro_en_movimiento),
                                        fontSize = 11.sp,
                                        color = style.mediumEmphasis
                                    )
                                }
                            }
                        }
                    }

                    if (tempTextColor.value == "Personalizado") {
                        Spacer(Modifier.height(4.dp))

                        // --- SELECTOR DE COLOR: cuadros tocables con colores comunes ---
                        val presetColors = listOf(
                            "#FFFFFF", "#000000", "#F44336", "#E91E63", "#9C27B0",
                            "#673AB7", "#3F51B5", "#2196F3", "#03A9F4", "#00BCD4",
                            "#009688", "#4CAF50", "#8BC34A", "#CDDC39", "#FFEB3B",
                            "#FFC107", "#FF9800", "#FF5722", "#795548", "#9E9E9E"
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            presetColors.forEach { hex ->
                                val isSelected = tempCustomHex.value.equals(hex, ignoreCase = true)
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(com.music.musicflame.ui.theme.parseCustomTextColor(hex))
                                        .border(
                                            width = if (isSelected) 3.dp else 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable { tempCustomHex.value = hex }
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))

                        OutlinedTextField(
                            value = tempCustomHex.value,
                            onValueChange = { tempCustomHex.value = it },
                            label = { Text(stringResource(R.string.settings_hex_rrggbb_o_rgba_r_g_b_a)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(com.music.musicflame.ui.theme.parseCustomTextColor(tempCustomHex.value))
                                    .border(1.dp, Color.Gray, RoundedCornerShape(6.dp))
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.settings_vista_previa), fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    settingsRepo.saveAppTextColor(tempTextColor.value)
                    appTextColorPref.value = tempTextColor.value
                    if (tempTextColor.value == "Personalizado") {
                        settingsRepo.saveCustomTextColorHex(tempCustomHex.value)
                        customTextColorHex.value = tempCustomHex.value
                    }
                    showTextColorDialog.value = false
                }) { Text(stringResource(R.string.action_save), fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showTextColorDialog.value = false }) { Text(stringResource(R.string.action_cancel), fontWeight = FontWeight.Bold) } }
        )
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsEqualizerColorDialog(
    style: SettingsStyle,
    settingsRepo: SettingsRepository,
    showEqualizerColorDialog: MutableState<Boolean>,
    equalizerColorModePref: MutableState<String>,
    equalizerCustomColorHexPref: MutableState<String>,
    unlockedIds: Set<String>,
    showLockedFeatureToast: (String) -> Unit
) {
    if (showEqualizerColorDialog.value) {
        val tempEqColorMode = remember { mutableStateOf(equalizerColorModePref.value) }
        val tempEqCustomHex = remember { mutableStateOf(equalizerCustomColorHexPref.value) }
        AlertDialog(
            onDismissRequest = { showEqualizerColorDialog.value = false },
            title = { Text(stringResource(R.string.settings_color_del_ecualizador), fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        stringResource(R.string.settings_aplica_al_ecualizador_grafico_anim),
                        fontSize = 12.sp,
                        color = style.mediumEmphasis,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    // "Adaptativo" es gratis acá también (igual que en "Color de
                    // texto"/"Now Playing") — Personalizado y Arcoíris son de pago,
                    // vendidos juntos en un solo producto (eq_color_custom + eq_color_rainbow).
                    listOf("Adaptativo", "Personalizado", com.music.musicflame.ui.theme.COLOR_MODE_RAINBOW).forEach { colorOption ->
                        val locked = when (colorOption) {
                            "Personalizado" -> !unlockedIds.contains("eq_color_custom")
                            com.music.musicflame.ui.theme.COLOR_MODE_RAINBOW -> !unlockedIds.contains("eq_color_rainbow")
                            else -> false
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .alpha(if (locked) 0.5f else 1f)
                                .clickable {
                                    if (locked) showLockedFeatureToast(if (colorOption == "Personalizado") "eq_color_custom" else "eq_color_rainbow") else tempEqColorMode.value = colorOption
                                }
                                .padding(vertical = 8.dp)
                        ) {
                            RadioButton(
                                selected = tempEqColorMode.value == colorOption,
                                enabled = !locked,
                                onClick = { if (locked) showLockedFeatureToast(if (colorOption == "Personalizado") "eq_color_custom" else "eq_color_rainbow") else tempEqColorMode.value = colorOption }
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(settingsOptionLabel(colorOption), fontSize = 14.sp)
                                    if (locked) {
                                        Spacer(Modifier.width(6.dp))
                                        Icon(Icons.Filled.Lock, contentDescription = stringResource(R.string.settings_bloqueado), modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.error)
                                        Spacer(Modifier.width(2.dp))
                                        Text("$5 MXN", fontSize = 10.sp, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                                    }
                                }
                                if (colorOption == "Adaptativo") {
                                    Text(
                                        stringResource(R.string.settings_blanco_o_negro_segun_el_fondo_como),
                                        fontSize = 11.sp,
                                        color = style.mediumEmphasis
                                    )
                                } else if (colorOption == com.music.musicflame.ui.theme.COLOR_MODE_RAINBOW) {
                                    Text(
                                        stringResource(R.string.settings_colores_del_espectro_en_movimiento),
                                        fontSize = 11.sp,
                                        color = style.mediumEmphasis
                                    )
                                }
                            }
                        }
                    }

                    if (tempEqColorMode.value == "Personalizado" && unlockedIds.contains("eq_color_custom")) {
                        Spacer(Modifier.height(4.dp))

                        // --- SELECTOR DE COLOR: mismos cuadros tocables que "Color de texto" ---
                        val presetColors = listOf(
                            "#FFFFFF", "#000000", "#F44336", "#E91E63", "#9C27B0",
                            "#673AB7", "#3F51B5", "#2196F3", "#03A9F4", "#00BCD4",
                            "#009688", "#4CAF50", "#8BC34A", "#CDDC39", "#FFEB3B",
                            "#FFC107", "#FF9800", "#FF5722", "#795548", "#9E9E9E"
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            presetColors.forEach { hex ->
                                val isSelected = tempEqCustomHex.value.equals(hex, ignoreCase = true)
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(com.music.musicflame.ui.theme.parseCustomTextColor(hex))
                                        .border(
                                            width = if (isSelected) 3.dp else 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable { tempEqCustomHex.value = hex }
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))

                        OutlinedTextField(
                            value = tempEqCustomHex.value,
                            onValueChange = { tempEqCustomHex.value = it },
                            label = { Text(stringResource(R.string.settings_hex_rrggbb_o_rgba_r_g_b_a)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(com.music.musicflame.ui.theme.parseCustomTextColor(tempEqCustomHex.value))
                                    .border(1.dp, Color.Gray, RoundedCornerShape(6.dp))
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.settings_vista_previa), fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    settingsRepo.saveEqualizerColorMode(tempEqColorMode.value)
                    equalizerColorModePref.value = tempEqColorMode.value
                    if (tempEqColorMode.value == "Personalizado") {
                        settingsRepo.saveEqualizerCustomColorHex(tempEqCustomHex.value)
                        equalizerCustomColorHexPref.value = tempEqCustomHex.value
                    }
                    showEqualizerColorDialog.value = false
                }) { Text(stringResource(R.string.action_save), fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showEqualizerColorDialog.value = false }) { Text(stringResource(R.string.action_cancel), fontWeight = FontWeight.Bold) } }
        )
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsFullPlayerBgDialog(
    style: SettingsStyle,
    context: Context,
    settingsRepo: SettingsRepository,
    showFullPlayerBgDialog: MutableState<Boolean>,
    fullPlayerBgModePref: MutableState<String>,
    fullPlayerBgIsGifPref: MutableState<Boolean>,
    refreshScope: kotlinx.coroutines.CoroutineScope
) {
    if (showFullPlayerBgDialog.value) {
        // Estados TEMPORALES: nada se guarda hasta pulsar "Guardar".
        val savedFpUri = remember { settingsRepo.getFullPlayerBgUri() }
        val savedFpConfig = remember { settingsRepo.loadFullPlayerBg() }
        // Si el archivo guardado ya no existe, se arranca como "sin imagen".
        val tempFpMode = remember { mutableStateOf(if (savedFpConfig != null) "custom" else "none") }
        val tempFpUri = remember { mutableStateOf(savedFpConfig?.uri) }
        val tempFpIsGif = remember { mutableStateOf(savedFpConfig?.isGif ?: false) }
        val tempFpBrightness = remember { mutableStateOf(settingsRepo.getFullPlayerBgBrightness().coerceIn(-1f, 1f)) }
        // Copias creadas durante ESTE diálogo (se borran si se reemplazan o se cancela).
        val createdFpFiles = remember { mutableListOf<String>() }
        val isCopyingFp = remember { mutableStateOf(false) }

        fun discardCreatedFpFiles(except: String?) {
            createdFpFiles.toList().forEach { created ->
                if (created != except) {
                    settingsRepo.deleteFullPlayerBgFile(created)
                    createdFpFiles.remove(created)
                }
            }
        }

        val pickFullPlayerBgLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { pickedUri ->
            if (pickedUri != null) {
                isCopyingFp.value = true
                refreshScope.launch {
                    val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        settingsRepo.importFullPlayerBgFile(pickedUri)
                    }
                    isCopyingFp.value = false
                    if (result != null && !showFullPlayerBgDialog.value) {
                        // El diálogo se cerró mientras se copiaba: no queda archivo huérfano.
                        settingsRepo.deleteFullPlayerBgFile(result.first)
                    } else if (result != null) {
                        // Reemplazo: si la copia anterior se creó en este diálogo, se borra ya.
                        val previous = tempFpUri.value
                        if (previous != null && createdFpFiles.contains(previous)) {
                            settingsRepo.deleteFullPlayerBgFile(previous)
                            createdFpFiles.remove(previous)
                        }
                        createdFpFiles.add(result.first)
                        tempFpUri.value = result.first
                        tempFpIsGif.value = result.second
                        tempFpMode.value = "custom"
                    } else {
                        Toast.makeText(context, context.getString(R.string.settings_no_se_pudo_cargar_el_archivo), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        AlertDialog(
            onDismissRequest = {
                discardCreatedFpFiles(except = null)
                showFullPlayerBgDialog.value = false
            },
            title = { Text(stringResource(R.string.settings_fondo_del_reproductor), fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        stringResource(R.string.settings_fondo_propio_solo_para_el_reproduc),
                        fontSize = 12.sp,
                        color = style.mediumEmphasis,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    listOf("none", "custom").forEach { modeOption ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { tempFpMode.value = modeOption }
                                .padding(vertical = 8.dp)
                        ) {
                            RadioButton(
                                selected = tempFpMode.value == modeOption,
                                onClick = { tempFpMode.value = modeOption }
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(if (modeOption == "none") stringResource(R.string.settings_sin_fondo) else stringResource(R.string.settings_imagen_gif), fontSize = 14.sp)
                                Text(
                                    if (modeOption == "none") stringResource(R.string.settings_el_reproductor_se_ve_como_siempre) else stringResource(R.string.settings_usa_tu_propia_imagen_o_gif_animado),
                                    fontSize = 11.sp,
                                    color = style.mediumEmphasis
                                )
                            }
                        }
                    }

                    if (tempFpMode.value == "custom") {
                        Spacer(Modifier.height(4.dp))
                        OutlinedButton(
                            onClick = { pickFullPlayerBgLauncher.launch("image/*") },
                            enabled = !isCopyingFp.value,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                if (isCopyingFp.value) stringResource(R.string.settings_copying) else stringResource(R.string.settings_elegir_imagen_o_gif),
                                fontWeight = FontWeight.Bold
                            )
                        }

                        val previewUri = tempFpUri.value
                        if (previewUri == null) {
                            Text(
                                stringResource(R.string.settings_elige_una_imagen_o_gif_para_activa),
                                fontSize = 11.sp,
                                color = style.mediumEmphasis,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        } else {
                            Spacer(Modifier.height(12.dp))
                            Text(stringResource(R.string.settings_vista_previa), fontSize = 12.sp, color = style.mediumEmphasis)
                            Spacer(Modifier.height(4.dp))
                            val previewRequest = remember(previewUri) {
                                ImageRequest.Builder(context)
                                    .data(previewUri)
                                    .decoderFactory(
                                        if (android.os.Build.VERSION.SDK_INT >= 28) coil.decode.ImageDecoderDecoder.Factory()
                                        else coil.decode.GifDecoder.Factory()
                                    )
                                    .build()
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(160.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.Black)
                            ) {
                                AsyncImage(
                                    model = previewRequest,
                                    contentDescription = stringResource(R.string.settings_vista_previa_del_fondo),
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                // Brillo en vivo: misma convención que el brillo global.
                                val previewBrightness = tempFpBrightness.value
                                if (previewBrightness != 0f) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(
                                                if (previewBrightness < 0f) Color.Black.copy(alpha = kotlin.math.abs(previewBrightness))
                                                else Color.White.copy(alpha = previewBrightness)
                                            )
                                    )
                                }
                                // Mismo scrim suave que usa el reproductor.
                                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.2f)))
                            }

                            Spacer(Modifier.height(12.dp))
                            Text(stringResource(R.string.settings_brillo_del_fondo), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Slider(
                                value = tempFpBrightness.value,
                                onValueChange = { tempFpBrightness.value = it },
                                valueRange = -1f..1f,
                                steps = 20,
                                colors = SliderDefaults.colors(
                                    thumbColor = style.trailingColor,
                                    activeTrackColor = style.trailingColor
                                )
                            )
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(stringResource(R.string.settings_oscuro), fontSize = 12.sp, color = style.mediumEmphasis)
                                Text(stringResource(R.string.settings_original), fontSize = 12.sp, color = style.mediumEmphasis)
                                Text(stringResource(R.string.settings_brillante), fontSize = 12.sp, color = style.mediumEmphasis)
                            }
                            TextButton(
                                onClick = { tempFpBrightness.value = 0f },
                                enabled = tempFpBrightness.value != 0f
                            ) { Text(stringResource(R.string.settings_volver_a_0), fontWeight = FontWeight.Bold) }

                            Spacer(Modifier.height(4.dp))
                            OutlinedButton(
                                onClick = {
                                    // Si la copia se creó en este diálogo se borra ya; la guardada
                                    // anteriormente se borra recién al pulsar "Guardar".
                                    val current = tempFpUri.value
                                    if (current != null && createdFpFiles.contains(current)) {
                                        settingsRepo.deleteFullPlayerBgFile(current)
                                        createdFpFiles.remove(current)
                                    }
                                    tempFpUri.value = null
                                    tempFpIsGif.value = false
                                    tempFpBrightness.value = 0f
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) { Text(stringResource(R.string.settings_quitar), fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !isCopyingFp.value,
                    onClick = {
                        val finalUri = tempFpUri.value
                        val finalMode = if (tempFpMode.value == "custom" && finalUri != null) "custom" else "none"
                        // Al quitar o reemplazar, se borra el archivo anterior guardado.
                        if (savedFpUri != null && savedFpUri != finalUri) {
                            settingsRepo.deleteFullPlayerBgFile(savedFpUri)
                        }
                        discardCreatedFpFiles(except = finalUri)
                        settingsRepo.saveFullPlayerBgAll(
                            mode = finalMode,
                            uri = finalUri,
                            isGif = tempFpIsGif.value,
                            brightness = tempFpBrightness.value
                        )
                        fullPlayerBgModePref.value = finalMode
                        fullPlayerBgIsGifPref.value = tempFpIsGif.value
                        showFullPlayerBgDialog.value = false
                    }
                ) { Text(stringResource(R.string.action_save), fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = {
                    discardCreatedFpFiles(except = null)
                    showFullPlayerBgDialog.value = false
                }) { Text(stringResource(R.string.action_cancel), fontWeight = FontWeight.Bold) }
            }
        )
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsAppBgDialog(
    style: SettingsStyle,
    onBackgroundImageChanged: () -> Unit,
    context: Context,
    settingsRepo: SettingsRepository,
    showAppBgDialog: MutableState<Boolean>,
    backgroundImageUri: MutableState<String?>,
    playerGifUri: MutableState<String?>,
    backgroundBrightness: MutableState<Float>
) {
    if (showAppBgDialog.value) {
        // Estados TEMPORALES: nada se guarda hasta pulsar "Guardar".
        val tempAppUri = remember { mutableStateOf(backgroundImageUri.value ?: playerGifUri.value) }
        val tempAppIsGif = remember { mutableStateOf(backgroundImageUri.value == null && playerGifUri.value != null) }
        val tempAppMode = remember { mutableStateOf(if (backgroundImageUri.value != null || playerGifUri.value != null) "custom" else "none") }
        val tempAppBrightness = remember { mutableStateOf(backgroundBrightness.value.coerceIn(-1f, 1f)) }

        val pickAppBgLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { picked ->
            picked?.let {
                try {
                    context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (e: Exception) {}
                tempAppUri.value = it.toString()
                tempAppIsGif.value = context.contentResolver.getType(it) == "image/gif" ||
                        it.toString().lowercase().endsWith(".gif")
                tempAppMode.value = "custom"
            }
        }

        AlertDialog(
            onDismissRequest = { showAppBgDialog.value = false },
            title = { Text(stringResource(R.string.settings_fondo_de_la_app), fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        stringResource(R.string.settings_imagen_o_gif_de_fondo_general_de_l),
                        fontSize = 12.sp,
                        color = style.mediumEmphasis,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    listOf("none", "custom").forEach { modeOption ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { tempAppMode.value = modeOption }
                                .padding(vertical = 8.dp)
                        ) {
                            RadioButton(
                                selected = tempAppMode.value == modeOption,
                                onClick = { tempAppMode.value = modeOption }
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(if (modeOption == "none") stringResource(R.string.settings_sin_fondo) else stringResource(R.string.settings_imagen_gif), fontSize = 14.sp)
                                Text(
                                    if (modeOption == "none") stringResource(R.string.settings_la_app_se_ve_como_siempre) else stringResource(R.string.settings_usa_tu_propia_imagen_o_gif_animado),
                                    fontSize = 11.sp,
                                    color = style.mediumEmphasis
                                )
                            }
                        }
                    }

                    if (tempAppMode.value == "custom") {
                        Spacer(Modifier.height(4.dp))
                        OutlinedButton(
                            onClick = { pickAppBgLauncher.launch("image/*") },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(stringResource(R.string.settings_elegir_imagen_o_gif), fontWeight = FontWeight.Bold) }

                        val previewUri = tempAppUri.value
                        if (previewUri == null) {
                            Text(
                                stringResource(R.string.settings_elige_una_imagen_o_gif_para_activa),
                                fontSize = 11.sp,
                                color = style.mediumEmphasis,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        } else {
                            Spacer(Modifier.height(12.dp))
                            Text(stringResource(R.string.settings_vista_previa), fontSize = 12.sp, color = style.mediumEmphasis)
                            Spacer(Modifier.height(4.dp))
                            val previewRequest = remember(previewUri) {
                                ImageRequest.Builder(context)
                                    .data(previewUri)
                                    .decoderFactory(
                                        if (android.os.Build.VERSION.SDK_INT >= 28) coil.decode.ImageDecoderDecoder.Factory()
                                        else coil.decode.GifDecoder.Factory()
                                    )
                                    .build()
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(160.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.Black)
                            ) {
                                AsyncImage(
                                    model = previewRequest,
                                    contentDescription = stringResource(R.string.settings_vista_previa_del_fondo),
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                val previewBrightness = tempAppBrightness.value
                                if (previewBrightness != 0f) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(
                                                if (previewBrightness < 0f) Color.Black.copy(alpha = kotlin.math.abs(previewBrightness))
                                                else Color.White.copy(alpha = previewBrightness)
                                            )
                                    )
                                }
                            }

                            Spacer(Modifier.height(12.dp))
                            Text(stringResource(R.string.settings_brillo_del_fondo), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Slider(
                                value = tempAppBrightness.value,
                                onValueChange = { tempAppBrightness.value = it },
                                valueRange = -1f..1f,
                                steps = 20,
                                colors = SliderDefaults.colors(
                                    thumbColor = style.trailingColor,
                                    activeTrackColor = style.trailingColor
                                )
                            )
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(stringResource(R.string.settings_oscuro), fontSize = 12.sp, color = style.mediumEmphasis)
                                Text(stringResource(R.string.settings_original), fontSize = 12.sp, color = style.mediumEmphasis)
                                Text(stringResource(R.string.settings_brillante), fontSize = 12.sp, color = style.mediumEmphasis)
                            }
                            TextButton(
                                onClick = { tempAppBrightness.value = 0f },
                                enabled = tempAppBrightness.value != 0f
                            ) { Text(stringResource(R.string.settings_volver_a_0), fontWeight = FontWeight.Bold) }

                            Spacer(Modifier.height(4.dp))
                            OutlinedButton(
                                onClick = {
                                    tempAppUri.value = null
                                    tempAppIsGif.value = false
                                    tempAppBrightness.value = 0f
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) { Text(stringResource(R.string.settings_quitar), fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val finalUri = if (tempAppMode.value == "custom") tempAppUri.value else null
                        if (finalUri == null) {
                            settingsRepo.removeBackgroundImage()
                            backgroundImageUri.value = null
                            settingsRepo.removePlayerGifUri()
                            playerGifUri.value = null
                        } else if (tempAppIsGif.value) {
                            settingsRepo.savePlayerGifUri(finalUri)
                            playerGifUri.value = finalUri
                            settingsRepo.removeBackgroundImage()
                            backgroundImageUri.value = null
                        } else {
                            settingsRepo.saveBackgroundImageUri(finalUri)
                            backgroundImageUri.value = finalUri
                            settingsRepo.removePlayerGifUri()
                            playerGifUri.value = null
                        }
                        settingsRepo.saveBackgroundBrightness(tempAppBrightness.value)
                        backgroundBrightness.value = tempAppBrightness.value
                        onBackgroundImageChanged()
                        showAppBgDialog.value = false
                    }
                ) { Text(stringResource(R.string.action_save), fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showAppBgDialog.value = false }) { Text(stringResource(R.string.action_cancel), fontWeight = FontWeight.Bold) }
            }
        )
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsMomentsColorDialog(
    style: SettingsStyle,
    settingsRepo: SettingsRepository,
    showMomentsColorDialog: MutableState<Boolean>,
    momentsColorModePref: MutableState<String>,
    momentsCustomColorHexPref: MutableState<String>
) {
    if (showMomentsColorDialog.value) {
        val tempMomentsMode = remember { mutableStateOf(momentsColorModePref.value) }
        val tempMomentsHex = remember { mutableStateOf(momentsCustomColorHexPref.value) }
        AlertDialog(
            onDismissRequest = { showMomentsColorDialog.value = false },
            title = { Text(stringResource(R.string.settings_color_de_momentos), fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        stringResource(R.string.settings_color_de_las_franjas_que_marcan_tu),
                        fontSize = 12.sp,
                        color = style.mediumEmphasis,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    listOf("Adaptativo", "Personalizado", com.music.musicflame.ui.theme.COLOR_MODE_RAINBOW).forEach { colorOption ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { tempMomentsMode.value = colorOption }
                                .padding(vertical = 8.dp)
                        ) {
                            RadioButton(
                                selected = tempMomentsMode.value == colorOption,
                                onClick = { tempMomentsMode.value = colorOption }
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(settingsOptionLabel(colorOption), fontSize = 14.sp)
                                Text(
                                    when (colorOption) {
                                        "Adaptativo" -> stringResource(R.string.settings_usa_el_color_del_tema_material_you)
                                        "Personalizado" -> stringResource(R.string.settings_elige_tu_propio_color)
                                        else -> stringResource(R.string.settings_colores_del_espectro_en_movimiento)
                                    },
                                    fontSize = 11.sp,
                                    color = style.mediumEmphasis
                                )
                            }
                        }
                    }

                    if (tempMomentsMode.value == "Personalizado") {
                        Spacer(Modifier.height(4.dp))
                        val presetColors = listOf(
                            "#FFFFFF", "#000000", "#F44336", "#E91E63", "#9C27B0",
                            "#673AB7", "#3F51B5", "#2196F3", "#03A9F4", "#00BCD4",
                            "#009688", "#4CAF50", "#8BC34A", "#CDDC39", "#FFEB3B",
                            "#FFC107", "#FF9800", "#FF5722", "#795548", "#9E9E9E"
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            presetColors.forEach { hex ->
                                val isSelected = tempMomentsHex.value.equals(hex, ignoreCase = true)
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(com.music.musicflame.ui.theme.parseCustomTextColor(hex))
                                        .border(
                                            width = if (isSelected) 3.dp else 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable { tempMomentsHex.value = hex }
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = tempMomentsHex.value,
                            onValueChange = { tempMomentsHex.value = it },
                            label = { Text(stringResource(R.string.settings_hex_rrggbb_o_rgba_r_g_b_a)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(com.music.musicflame.ui.theme.parseCustomTextColor(tempMomentsHex.value))
                                    .border(1.dp, Color.Gray, RoundedCornerShape(6.dp))
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.settings_vista_previa), fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    settingsRepo.saveMomentsColorMode(tempMomentsMode.value)
                    momentsColorModePref.value = tempMomentsMode.value
                    if (tempMomentsMode.value == "Personalizado") {
                        settingsRepo.saveMomentsCustomColorHex(tempMomentsHex.value)
                        momentsCustomColorHexPref.value = tempMomentsHex.value
                    }
                    showMomentsColorDialog.value = false
                }) { Text(stringResource(R.string.action_save), fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showMomentsColorDialog.value = false }) { Text(stringResource(R.string.action_cancel), fontWeight = FontWeight.Bold) } }
        )
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsTogetherColorDialog(
    style: SettingsStyle,
    settingsRepo: SettingsRepository,
    showTogetherColorDialog: MutableState<Boolean>,
    togetherColorModePref: MutableState<String>,
    togetherCustomColorHexPref: MutableState<String>
) {
    if (showTogetherColorDialog.value) {
        val tempTogetherMode = remember { mutableStateOf(togetherColorModePref.value) }
        val tempTogetherHex = remember { mutableStateOf(togetherCustomColorHexPref.value) }
        AlertDialog(
            onDismissRequest = { showTogetherColorDialog.value = false },
            title = { Text(stringResource(R.string.settings_color_de_en_compania), fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        stringResource(R.string.settings_color_de_acento_de_la_pantalla_en),
                        fontSize = 12.sp,
                        color = style.mediumEmphasis,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    listOf("Adaptativo", "Personalizado", com.music.musicflame.ui.theme.COLOR_MODE_RAINBOW).forEach { colorOption ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { tempTogetherMode.value = colorOption }
                                .padding(vertical = 8.dp)
                        ) {
                            RadioButton(
                                selected = tempTogetherMode.value == colorOption,
                                onClick = { tempTogetherMode.value = colorOption }
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(settingsOptionLabel(colorOption), fontSize = 14.sp)
                                Text(
                                    when (colorOption) {
                                        "Adaptativo" -> stringResource(R.string.settings_usa_el_color_del_tema_material_you)
                                        "Personalizado" -> stringResource(R.string.settings_elige_tu_propio_color)
                                        else -> stringResource(R.string.settings_colores_del_espectro_en_movimiento)
                                    },
                                    fontSize = 11.sp,
                                    color = style.mediumEmphasis
                                )
                            }
                        }
                    }

                    if (tempTogetherMode.value == "Personalizado") {
                        Spacer(Modifier.height(4.dp))
                        val presetColors = listOf(
                            "#FFFFFF", "#000000", "#F44336", "#E91E63", "#9C27B0",
                            "#673AB7", "#3F51B5", "#2196F3", "#03A9F4", "#00BCD4",
                            "#009688", "#4CAF50", "#8BC34A", "#CDDC39", "#FFEB3B",
                            "#FFC107", "#FF9800", "#FF5722", "#795548", "#9E9E9E"
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            presetColors.forEach { hex ->
                                val isSelected = tempTogetherHex.value.equals(hex, ignoreCase = true)
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(com.music.musicflame.ui.theme.parseCustomTextColor(hex))
                                        .border(
                                            width = if (isSelected) 3.dp else 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable { tempTogetherHex.value = hex }
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = tempTogetherHex.value,
                            onValueChange = { tempTogetherHex.value = it },
                            label = { Text(stringResource(R.string.settings_hex_rrggbb_o_rgba_r_g_b_a)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(com.music.musicflame.ui.theme.parseCustomTextColor(tempTogetherHex.value))
                                    .border(1.dp, Color.Gray, RoundedCornerShape(6.dp))
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.settings_vista_previa), fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    settingsRepo.saveTogetherColorMode(tempTogetherMode.value)
                    togetherColorModePref.value = tempTogetherMode.value
                    if (tempTogetherMode.value == "Personalizado") {
                        settingsRepo.saveTogetherCustomColorHex(tempTogetherHex.value)
                        togetherCustomColorHexPref.value = tempTogetherHex.value
                    }
                    showTogetherColorDialog.value = false
                }) { Text(stringResource(R.string.action_save), fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showTogetherColorDialog.value = false }) { Text(stringResource(R.string.action_cancel), fontWeight = FontWeight.Bold) } }
        )
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsTogetherTextColorDialog(
    style: SettingsStyle,
    settingsRepo: SettingsRepository,
    showTogetherTextColorDialog: MutableState<Boolean>,
    togetherTextColorModePref: MutableState<String>,
    togetherTextCustomColorHexPref: MutableState<String>
) {
    if (showTogetherTextColorDialog.value) {
        val tempTogetherMode = remember { mutableStateOf(togetherTextColorModePref.value) }
        val tempTogetherHex = remember { mutableStateOf(togetherTextCustomColorHexPref.value) }
        AlertDialog(
            onDismissRequest = { showTogetherTextColorDialog.value = false },
            title = { Text(stringResource(R.string.settings_color_texto_en_compania), fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        stringResource(R.string.settings_color_texto_en_compania_desc),
                        fontSize = 12.sp,
                        color = style.mediumEmphasis,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    listOf("Adaptativo", "Personalizado", com.music.musicflame.ui.theme.COLOR_MODE_RAINBOW).forEach { colorOption ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { tempTogetherMode.value = colorOption }
                                .padding(vertical = 8.dp)
                        ) {
                            RadioButton(
                                selected = tempTogetherMode.value == colorOption,
                                onClick = { tempTogetherMode.value = colorOption }
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(settingsOptionLabel(colorOption), fontSize = 14.sp)
                                Text(
                                    when (colorOption) {
                                        "Adaptativo" -> stringResource(R.string.settings_together_text_adaptive_desc)
                                        "Personalizado" -> stringResource(R.string.settings_elige_tu_propio_color)
                                        else -> stringResource(R.string.settings_colores_del_espectro_en_movimiento)
                                    },
                                    fontSize = 11.sp,
                                    color = style.mediumEmphasis
                                )
                            }
                        }
                    }

                    if (tempTogetherMode.value == "Personalizado") {
                        Spacer(Modifier.height(4.dp))
                        val presetColors = listOf(
                            "#FFFFFF", "#000000", "#F44336", "#E91E63", "#9C27B0",
                            "#673AB7", "#3F51B5", "#2196F3", "#03A9F4", "#00BCD4",
                            "#009688", "#4CAF50", "#8BC34A", "#CDDC39", "#FFEB3B",
                            "#FFC107", "#FF9800", "#FF5722", "#795548", "#9E9E9E"
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            presetColors.forEach { hex ->
                                val isSelected = tempTogetherHex.value.equals(hex, ignoreCase = true)
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(com.music.musicflame.ui.theme.parseCustomTextColor(hex))
                                        .border(
                                            width = if (isSelected) 3.dp else 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable { tempTogetherHex.value = hex }
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = tempTogetherHex.value,
                            onValueChange = { tempTogetherHex.value = it },
                            label = { Text(stringResource(R.string.settings_hex_rrggbb_o_rgba_r_g_b_a)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(com.music.musicflame.ui.theme.parseCustomTextColor(tempTogetherHex.value))
                                    .border(1.dp, Color.Gray, RoundedCornerShape(6.dp))
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.settings_vista_previa), fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    settingsRepo.saveTogetherTextColorMode(tempTogetherMode.value)
                    togetherTextColorModePref.value = tempTogetherMode.value
                    if (tempTogetherMode.value == "Personalizado") {
                        settingsRepo.saveTogetherTextCustomColorHex(tempTogetherHex.value)
                        togetherTextCustomColorHexPref.value = tempTogetherHex.value
                    }
                    showTogetherTextColorDialog.value = false
                }) { Text(stringResource(R.string.action_save), fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showTogetherTextColorDialog.value = false }) { Text(stringResource(R.string.action_cancel), fontWeight = FontWeight.Bold) } }
        )
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsLyricsColorDialog(
    style: SettingsStyle,
    settingsRepo: SettingsRepository,
    showLyricsColorDialog: MutableState<Boolean>,
    lyricsColorModePref: MutableState<String>,
    lyricsCustomColorHexPref: MutableState<String>
) {
    if (showLyricsColorDialog.value) {
        val tempLyricsCustomHex = remember { mutableStateOf(lyricsCustomColorHexPref.value) }
        // Si se cierra el diálogo sin confirmar un color, y el modo activo
        // guardado todavía no era "Personalizado", revertimos el chip a lo
        // que sí está persistido (evita dejar la selección visual en un
        // estado "Personalizado" fantasma sin color confirmado).
        val revertIfNotConfirmed = {
            if (settingsRepo.getLyricsTextColorMode() != "Personalizado") {
                lyricsColorModePref.value = settingsRepo.getLyricsTextColorMode()
            }
            showLyricsColorDialog.value = false
        }
        AlertDialog(
            onDismissRequest = revertIfNotConfirmed,
            title = { Text(stringResource(R.string.settings_color_del_texto_de_la_letra), fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        stringResource(R.string.settings_se_aplica_al_texto_de_la_letra_sin),
                        fontSize = 12.sp,
                        color = style.mediumEmphasis,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    // --- SELECTOR DE COLOR: mismos cuadros tocables que "Color del ecualizador" ---
                    val presetColors = listOf(
                        "#FFFFFF", "#000000", "#F44336", "#E91E63", "#9C27B0",
                        "#673AB7", "#3F51B5", "#2196F3", "#03A9F4", "#00BCD4",
                        "#009688", "#4CAF50", "#8BC34A", "#CDDC39", "#FFEB3B",
                        "#FFC107", "#FF9800", "#FF5722", "#795548", "#9E9E9E"
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        presetColors.forEach { hex ->
                            val isSelected = tempLyricsCustomHex.value.equals(hex, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(com.music.musicflame.ui.theme.parseCustomTextColor(hex))
                                    .border(
                                        width = if (isSelected) 3.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable { tempLyricsCustomHex.value = hex }
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = tempLyricsCustomHex.value,
                        onValueChange = { tempLyricsCustomHex.value = it },
                        label = { Text(stringResource(R.string.settings_hex_rrggbb_o_rgba_r_g_b_a)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(com.music.musicflame.ui.theme.parseCustomTextColor(tempLyricsCustomHex.value))
                                .border(1.dp, Color.Gray, RoundedCornerShape(6.dp))
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.settings_vista_previa), fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    settingsRepo.saveLyricsTextColorMode("Personalizado")
                    settingsRepo.saveLyricsCustomColorHex(tempLyricsCustomHex.value)
                    lyricsColorModePref.value = "Personalizado"
                    lyricsCustomColorHexPref.value = tempLyricsCustomHex.value
                    showLyricsColorDialog.value = false
                }) { Text(stringResource(R.string.action_save), fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = revertIfNotConfirmed) { Text(stringResource(R.string.action_cancel), fontWeight = FontWeight.Bold) } }
        )
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsNowPlayingColorDialog(
    style: SettingsStyle,
    settingsRepo: SettingsRepository,
    showNowPlayingColorDialog: MutableState<Boolean>,
    nowPlayingColorModePref: MutableState<String>,
    nowPlayingCustomColorHexPref: MutableState<String>,
    unlockedIds: Set<String>,
    showLockedFeatureToast: (String) -> Unit
) {
    if (showNowPlayingColorDialog.value) {
        val tempNowPlayingColorMode = remember { mutableStateOf(nowPlayingColorModePref.value) }
        val tempNowPlayingCustomHex = remember { mutableStateOf(nowPlayingCustomColorHexPref.value) }
        AlertDialog(
            onDismissRequest = { showNowPlayingColorDialog.value = false },
            title = { Text(stringResource(R.string.settings_color_del_now_playing), fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        stringResource(R.string.settings_aplica_al_indicador_animado_de_rep),
                        fontSize = 12.sp,
                        color = style.mediumEmphasis,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    listOf("Adaptativo", "Personalizado", com.music.musicflame.ui.theme.COLOR_MODE_RAINBOW).forEach { colorOption ->
                        // Personalizado y Arcoíris son de pago acá; Adaptativo es gratis.
                        val locked = when (colorOption) {
                            "Personalizado" -> !unlockedIds.contains("now_playing_custom")
                            com.music.musicflame.ui.theme.COLOR_MODE_RAINBOW -> !unlockedIds.contains("now_playing_rainbow")
                            else -> false
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .alpha(if (locked) 0.5f else 1f)
                                .clickable {
                                    if (locked) showLockedFeatureToast(if (colorOption == "Personalizado") "now_playing_custom" else "now_playing_rainbow") else tempNowPlayingColorMode.value = colorOption
                                }
                                .padding(vertical = 8.dp)
                        ) {
                            RadioButton(
                                selected = tempNowPlayingColorMode.value == colorOption,
                                enabled = !locked,
                                onClick = { if (locked) showLockedFeatureToast(if (colorOption == "Personalizado") "now_playing_custom" else "now_playing_rainbow") else tempNowPlayingColorMode.value = colorOption }
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(settingsOptionLabel(colorOption), fontSize = 14.sp)
                                    if (locked) {
                                        Spacer(Modifier.width(6.dp))
                                        Icon(Icons.Filled.Lock, contentDescription = stringResource(R.string.settings_bloqueado), modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.error)
                                        Spacer(Modifier.width(2.dp))
                                        Text("$5 MXN", fontSize = 10.sp, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                                    }
                                }
                                if (colorOption == "Adaptativo") {
                                    Text(
                                        stringResource(R.string.settings_blanco_o_negro_segun_el_fondo_como),
                                        fontSize = 11.sp,
                                        color = style.mediumEmphasis
                                    )
                                } else if (colorOption == com.music.musicflame.ui.theme.COLOR_MODE_RAINBOW) {
                                    Text(
                                        stringResource(R.string.settings_colores_del_espectro_en_movimiento),
                                        fontSize = 11.sp,
                                        color = style.mediumEmphasis
                                    )
                                }
                            }
                        }
                    }

                    if (tempNowPlayingColorMode.value == "Personalizado" && unlockedIds.contains("now_playing_custom")) {
                        Spacer(Modifier.height(4.dp))

                        // --- SELECTOR DE COLOR: mismos cuadros tocables que "Color del ecualizador" ---
                        val presetColors = listOf(
                            "#FFFFFF", "#000000", "#F44336", "#E91E63", "#9C27B0",
                            "#673AB7", "#3F51B5", "#2196F3", "#03A9F4", "#00BCD4",
                            "#009688", "#4CAF50", "#8BC34A", "#CDDC39", "#FFEB3B",
                            "#FFC107", "#FF9800", "#FF5722", "#795548", "#9E9E9E"
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            presetColors.forEach { hex ->
                                val isSelected = tempNowPlayingCustomHex.value.equals(hex, ignoreCase = true)
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(com.music.musicflame.ui.theme.parseCustomTextColor(hex))
                                        .border(
                                            width = if (isSelected) 3.dp else 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable { tempNowPlayingCustomHex.value = hex }
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))

                        OutlinedTextField(
                            value = tempNowPlayingCustomHex.value,
                            onValueChange = { tempNowPlayingCustomHex.value = it },
                            label = { Text(stringResource(R.string.settings_hex_rrggbb_o_rgba_r_g_b_a)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(com.music.musicflame.ui.theme.parseCustomTextColor(tempNowPlayingCustomHex.value))
                                    .border(1.dp, Color.Gray, RoundedCornerShape(6.dp))
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.settings_vista_previa), fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    settingsRepo.saveNowPlayingColorMode(tempNowPlayingColorMode.value)
                    nowPlayingColorModePref.value = tempNowPlayingColorMode.value
                    if (tempNowPlayingColorMode.value == "Personalizado") {
                        settingsRepo.saveNowPlayingCustomColorHex(tempNowPlayingCustomHex.value)
                        nowPlayingCustomColorHexPref.value = tempNowPlayingCustomHex.value
                    }
                    showNowPlayingColorDialog.value = false
                }) { Text(stringResource(R.string.action_save), fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showNowPlayingColorDialog.value = false }) { Text(stringResource(R.string.action_cancel), fontWeight = FontWeight.Bold) } }
        )
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsAnomalyScanDialog(
    playerManager: com.music.musicflame.data.MusicPlayerManager,
    settingsRepo: SettingsRepository,
    showAnomalyScanDialog: MutableState<Boolean>
) {
    if (showAnomalyScanDialog.value) {
        AnomalyScanDialog(
            settingsRepo = settingsRepo,
            playerManager = playerManager,
            onDismiss = { showAnomalyScanDialog.value = false }
        )
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsProEqualizerDialog(
    showProEqualizerDialog: MutableState<Boolean>
) {
    if (showProEqualizerDialog.value) {
        ProEqualizerDialog(onDismiss = { showProEqualizerDialog.value = false })
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsEqualizerDialog(
    context: Context,
    settingsRepo: SettingsRepository,
    sharedPrefs: com.music.musicflame.data.SettingsStore,
    showEqualizerDialog: MutableState<Boolean>,
    eqPresetSelected: MutableState<String>,
    slidersValues: List<MutableState<Float>>,
    bassBoost: MutableState<Float>,
    virtualizer: MutableState<Float>,
    eqVolume: MutableState<Float>
) {
    if (showEqualizerDialog.value) {
        val tempPreset = remember { mutableStateOf(eqPresetSelected.value) }
        val viewKHz = remember { mutableStateOf(false) }
        val tempSliders = remember { List(5) { index -> mutableStateOf(slidersValues[index].value) } }
        val tempBass = remember { mutableStateOf(bassBoost.value) }
        val tempVirtualizer = remember { mutableStateOf(virtualizer.value) }
        val tempVolume = remember { mutableStateOf(eqVolume.value) }
        val tempLoudness = remember { mutableStateOf(sharedPrefs.getFloat("loudness_enhancer", 0f)) }
        val tempReverb = remember { mutableStateOf(sharedPrefs.getInt("reverb_preset", 0)) }
        // MODO PRO EXCLUSIVO: con Pro desbloqueado (o cuenta dueña) y este modo activo (default),
        // MusicPlaybackService APAGA el Equalizer nativo de 5 bandas por completo, así que los
        // sliders de este diálogo no se oían. Aquí se muestra el estado y se puede apagar.
        val proEqAvailable = com.music.musicflame.data.ProStatusHolder.isItemUnlocked("pro_eq_10band")
        val proExclusiveOn = remember { mutableStateOf(sharedPrefs.getBoolean("pro_eq_exclusive", true)) }
        val showSaveCustomDialog = remember { mutableStateOf(false) }
        val customPresetName = remember { mutableStateOf("") }
        val customNamesString = sharedPrefs.getString("custom_preset_names", "") ?: ""
        val customPresetsList = if (customNamesString.isNotEmpty()) customNamesString.split(",") else emptyList()
        val basePresets = listOf("Flat", "Rock", "Pop", "Hip hop", "Jazz", "Classical", "Electronico", "Refuerzo de graves", "Refuerzo de agudos", "Vocales") + EqPresets.FREE_NAMES + "Customizar"
        val allPresets = basePresets + customPresetsList

        // ARREGLO GRATIS: antes las 5 frecuencias (60/230/910/3600/14000 Hz) estaban
        // hardcodeadas en la UI asumiendo que TODOS los celulares traen exactamente
        // 5 bandas en esas frecuencias. Muchos Samsung/Xiaomi con DSP propio reportan
        // numberOfBands distinto, así que aquí se consulta el ecualizador REAL del
        // dispositivo (con audioSessionId=0, que solo sirve para leer capacidades, sin
        // necesidad de que haya audio sonando) para saber cuántas bandas hay de verdad
        // y a qué frecuencia responde cada una. Si algo falla o el dispositivo no
        // reporta nada usable, se cae de vuelta a los valores de siempre para no romper
        // la app.
        val fallbackFreqsHz = listOf(60, 230, 910, 3600, 14000)
        val realDeviceBandFreqsHz = remember {
            try {
                val probe = Equalizer(0, 0)
                val realBandCount = probe.numberOfBands.toInt()
                val freqs = (0 until realBandCount).map { i -> probe.getCenterFreq(i.toShort()) / 1000 }
                probe.release()
                freqs
            } catch (e: Exception) {
                emptyList<Int>()
            }
        }
        // Cuántos sliders tiene sentido mostrar/controlar en ESTE celular: nunca más de
        // 5 (nuestros presets son de 5 bandas), pero si el hardware real solo tiene
        // menos, no mostramos sliders para bandas que no existen.
        val eqBandCount = if (realDeviceBandFreqsHz.isNotEmpty()) minOf(5, realDeviceBandFreqsHz.size) else 5
        val eqFreqsHz = List(5) { i ->
            if (i < realDeviceBandFreqsHz.size) realDeviceBandFreqsHz[i] else fallbackFreqsHz[i]
        }

        val presetConfigs = mapOf(
            "Flat" to listOf(0f, 0f, 0f, 0f, 0f),
            "Rock" to listOf(0.5f, 0.3f, -0.1f, 0.3f, 0.5f),
            "Pop" to listOf(-0.1f, 0.2f, 0.4f, 0.2f, -0.1f),
            "Hip hop" to listOf(0.7f, 0.4f, 0f, 0.2f, 0.4f),
            "Jazz" to listOf(0.3f, 0.2f, -0.1f, 0.2f, 0.4f),
            "Classical" to listOf(0.4f, 0.3f, -0.1f, 0.3f, 0.4f),
            "Electronico" to listOf(0.6f, 0.4f, -0.1f, 0.4f, 0.6f),
            "Refuerzo de graves" to listOf(0.9f, 0.5f, 0f, 0f, 0f),
            "Refuerzo de agudos" to listOf(0f, 0f, 0f, 0.5f, 0.9f),
            "Vocales" to listOf(-0.2f, 0f, 0.6f, 0.4f, -0.1f)
        ) + EqPresets.FREE_BANDS

        Dialog(
            onDismissRequest = { showEqualizerDialog.value = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(onClick = { showEqualizerDialog.value = false }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_close), tint = MaterialTheme.colorScheme.onBackground) }
                        Text(stringResource(R.string.settings_studio_pro_eq), fontSize = 20.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onBackground)

                        Button(onClick = {
                            settingsRepo.saveEqPresetSelected(tempPreset.value)
                            eqPresetSelected.value = tempPreset.value
                            for (i in 0 until 5) {
                                settingsRepo.saveEqBand(i, tempSliders[i].value)
                                slidersValues[i].value = tempSliders[i].value
                            }
                            settingsRepo.saveBassBoost(tempBass.value)
                            settingsRepo.saveVirtualizer(tempVirtualizer.value)
                            settingsRepo.saveEqVolume(tempVolume.value)
                            bassBoost.value = tempBass.value
                            virtualizer.value = tempVirtualizer.value
                            eqVolume.value = tempVolume.value
                            sharedPrefs.edit().putFloat("loudness_enhancer", tempLoudness.value).putInt("reverb_preset", tempReverb.value).apply()

                            val intent = Intent("com.music.musicflame.UPDATE_EQ")
                            intent.setPackage(context.packageName)
                            intent.putExtra("bass_boost", tempBass.value)
                            intent.putExtra("virtualizer", tempVirtualizer.value)
                            intent.putExtra("loudness", tempLoudness.value)
                            intent.putExtra("reverb", tempReverb.value)

                            for (i in 0 until 5) {
                                intent.putExtra("eq_band_$i", tempSliders[i].value)
                            }

                            context.sendBroadcast(intent)

                            showEqualizerDialog.value = false
                            Toast.makeText(context, context.getString(R.string.settings_audio_pro_activado), Toast.LENGTH_SHORT).show()
                        }) { Text(stringResource(R.string.settings_aplicar), fontWeight = FontWeight.Bold) }
                    }

                    LazyColumn(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        item { Spacer(Modifier.height(8.dp)) }
                        if (proEqAvailable) {
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (proExclusiveOn.value) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainer
                                    )
                                ) {
                                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                if (proExclusiveOn.value) stringResource(R.string.settings_modo_pro_exclusivo_activo) else stringResource(R.string.settings_modo_pro_exclusivo_apagado),
                                                fontWeight = FontWeight.Black, fontSize = 14.sp
                                            )
                                            Text(
                                                if (proExclusiveOn.value) stringResource(R.string.settings_este_ecualizador_de_5_bandas_esta)
                                                else stringResource(R.string.settings_este_ecualizador_de_5_bandas_suena),
                                                fontSize = 12.sp
                                            )
                                        }
                                        Switch(
                                            checked = proExclusiveOn.value,
                                            onCheckedChange = { on ->
                                                proExclusiveOn.value = on
                                                sharedPrefs.edit().putBoolean("pro_eq_exclusive", on).apply()
                                                // Sin extras: el servicio solo relee el estado y reaplica.
                                                val sync = Intent("com.music.musicflame.UPDATE_EQ")
                                                sync.setPackage(context.packageName)
                                                context.sendBroadcast(sync)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        item {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.settings_ajustes_preestablecidos), fontWeight = FontWeight.Black, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                                IconButton(onClick = { showSaveCustomDialog.value = true }, modifier = Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(12.dp)).size(36.dp)) {
                                    Icon(Icons.Filled.Add, stringResource(R.string.settings_guardar_preset), tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(allPresets) { preset ->
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = if (tempPreset.value == preset) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
                                        modifier = Modifier.clickable {
                                            tempPreset.value = preset
                                            if (presetConfigs.containsKey(preset)) {
                                                val config = presetConfigs[preset]!!
                                                for (i in 0 until 5) tempSliders[i].value = config[i]
                                                if (preset == "Flat") { tempBass.value = 0f; tempVirtualizer.value = 0f; tempLoudness.value = 0f; tempReverb.value = 0 }
                                                if (preset == "Refuerzo de graves") tempBass.value = 100f
                                                EqPresets.FREE_BASS[preset]?.let { tempBass.value = it }
                                            } else {
                                                val savedBands = sharedPrefs.getString("preset_${preset}_bands", "")
                                                if (savedBands != null && savedBands.isNotEmpty()) {
                                                    val vals = savedBands.split(",").map { it.toFloat() }
                                                    if (vals.size == 5) for (i in 0 until 5) tempSliders[i].value = vals[i]
                                                }
                                                tempBass.value = sharedPrefs.getFloat("preset_${preset}_bass", 0f)
                                                tempVirtualizer.value = sharedPrefs.getFloat("preset_${preset}_virt", 0f)
                                                tempLoudness.value = sharedPrefs.getFloat("preset_${preset}_loud", 0f)
                                                tempReverb.value = sharedPrefs.getInt("preset_${preset}_reverb", 0)
                                            }
                                        }
                                    ) {
                                        Text(text = eqPresetLabel(preset), modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp), color = if (tempPreset.value == preset) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        item {
                            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text(stringResource(R.string.settings_ecualizador_1_s_bandas, eqBandCount), fontWeight = FontWeight.Black, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("Hz", fontSize = 12.sp, color = if (!viewKHz.value) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                            Switch(checked = viewKHz.value, onCheckedChange = { viewKHz.value = it }, modifier = Modifier.padding(horizontal = 4.dp))
                                            Text("kHz", fontSize = 12.sp, color = if (viewKHz.value) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    Spacer(Modifier.height(16.dp))
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                        // Etiquetas armadas a partir de eqFreqsHz (real del dispositivo cuando se pudo
                                        // leer, o el valor de siempre como respaldo) en vez de un texto fijo.
                                        for (i in 0 until eqBandCount) {
                                            val hzValue = eqFreqsHz[i]
                                            val label = if (viewKHz.value) {
                                                String.format("%.2f", hzValue / 1000f)
                                            } else {
                                                hzValue.toString()
                                            }
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                Spacer(Modifier.height(8.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .height(150.dp)
                                                        .width(40.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    VerticalSlider(
                                                        value = tempSliders[i].value,
                                                        onValueChange = { tempSliders[i].value = it; tempPreset.value = "Customizar" },
                                                        valueRange = -1f..1f,
                                                        modifier = Modifier.fillMaxSize()
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                val proCardColor = MaterialTheme.colorScheme.surfaceContainerHighest
                                Card(modifier = Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = proCardColor)) {
                                    Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(stringResource(R.string.settings_graves), fontSize = 12.sp, fontWeight = FontWeight.Black)
                                        Text("${tempBass.value.toInt()}%", fontSize = 16.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black)
                                        Slider(value = tempBass.value, onValueChange = { tempBass.value = it; tempPreset.value = "Customizar" }, valueRange = 0f..100f)
                                    }
                                }
                                Card(modifier = Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = proCardColor)) {
                                    Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(stringResource(R.string.settings_virtual_3d), fontSize = 12.sp, fontWeight = FontWeight.Black)
                                        Text("${tempVirtualizer.value.toInt()}%", fontSize = 16.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black)
                                        Slider(value = tempVirtualizer.value, onValueChange = { tempVirtualizer.value = it; tempPreset.value = "Customizar" }, valueRange = 0f..100f)
                                    }
                                }
                            }
                        }
                    }
                }

                if (showSaveCustomDialog.value) {
                    AlertDialog(
                        onDismissRequest = { showSaveCustomDialog.value = false },
                        title = { Text(stringResource(R.string.settings_guardar_preset)) },
                        text = { OutlinedTextField(value = customPresetName.value, onValueChange = { customPresetName.value = it }, label = { Text(stringResource(R.string.settings_nombre_del_preset)) }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
                        confirmButton = {
                            Button(onClick = {
                                val name = customPresetName.value.trim()
                                if (name.isNotEmpty() && !basePresets.contains(name)) {
                                    val newList = customPresetsList.toMutableList()
                                    if (!newList.contains(name)) newList.add(name)
                                    sharedPrefs.edit().putString("custom_preset_names", newList.joinToString(",")).apply()

                                    val bandsStr = tempSliders.joinToString(",") { it.value.toString() }
                                    sharedPrefs.edit()
                                        .putString("preset_${name}_bands", bandsStr)
                                        .putFloat("preset_${name}_bass", tempBass.value)
                                        .putFloat("preset_${name}_virt", tempVirtualizer.value)
                                        .putFloat("preset_${name}_loud", tempLoudness.value)
                                        .putInt("preset_${name}_reverb", tempReverb.value)
                                        .apply()

                                    tempPreset.value = name
                                    showSaveCustomDialog.value = false
                                    Toast.makeText(context, context.getString(R.string.settings_preset_guardado_con_exito), Toast.LENGTH_SHORT).show()
                                }
                            }) { Text(stringResource(R.string.action_save), fontWeight = FontWeight.Bold) }
                        },
                        dismissButton = { TextButton(onClick = { showSaveCustomDialog.value = false }) { Text(stringResource(R.string.action_cancel)) } }
                    )
                }
            }
        }
    }
}
