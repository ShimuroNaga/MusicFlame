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

/** Diálogos de selección de color de Ajustes. Código movido desde SettingsDialogs.kt. */

private val PRESET_COLORS = listOf(
    "#FFFFFF", "#000000", "#F44336", "#E91E63", "#9C27B0",
    "#673AB7", "#3F51B5", "#2196F3", "#03A9F4", "#00BCD4",
    "#009688", "#4CAF50", "#8BC34A", "#CDDC39", "#FFEB3B",
    "#FFC107", "#FF9800", "#FF5722", "#795548", "#9E9E9E"
)

@Composable
private fun ColorPresetRow(selectedHex: String, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        PRESET_COLORS.forEach { hex ->
            val isSelected = selectedHex.equals(hex, ignoreCase = true)
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
                    .clickable { onSelect(hex) }
            )
        }
    }
}

@Composable
private fun ColorHexFieldAndPreview(hexValue: String, onHexChange: (String) -> Unit) {
    OutlinedTextField(
        value = hexValue,
        onValueChange = onHexChange,
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
                .background(com.music.musicflame.ui.theme.parseCustomTextColor(hexValue))
                .border(1.dp, Color.Gray, RoundedCornerShape(6.dp))
        )
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.settings_vista_previa), fontSize = 12.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
                        val locked = !unlockedIds.contains("text_color_rainbow") && colorOption == com.music.musicflame.ui.theme.COLOR_MODE_RAINBOW
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .alpha(if (locked) 0.5f else 1f)
                                .clickable { if (locked) showLockedFeatureToast("text_color_rainbow") else tempTextColor.value = colorOption }
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
                                    Text(stringResource(R.string.settings_blanco_o_negro_segun_el_fondo_como), fontSize = 11.sp, color = style.mediumEmphasis)
                                } else if (colorOption == com.music.musicflame.ui.theme.COLOR_MODE_RAINBOW) {
                                    Text(stringResource(R.string.settings_colores_del_espectro_en_movimiento), fontSize = 11.sp, color = style.mediumEmphasis)
                                }
                            }
                        }
                    }
                    if (tempTextColor.value == "Personalizado") {
                        Spacer(Modifier.height(4.dp))
                        ColorPresetRow(selectedHex = tempCustomHex.value, onSelect = { tempCustomHex.value = it })
                        Spacer(Modifier.height(12.dp))
                        ColorHexFieldAndPreview(hexValue = tempCustomHex.value, onHexChange = { tempCustomHex.value = it })
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

@OptIn(ExperimentalMaterial3Api::class)
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
                    Text(stringResource(R.string.settings_aplica_al_ecualizador_grafico_anim), fontSize = 12.sp, color = style.mediumEmphasis, modifier = Modifier.padding(bottom = 8.dp))
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
                                .clickable { if (locked) showLockedFeatureToast(if (colorOption == "Personalizado") "eq_color_custom" else "eq_color_rainbow") else tempEqColorMode.value = colorOption }
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
                                    Text(stringResource(R.string.settings_blanco_o_negro_segun_el_fondo_como), fontSize = 11.sp, color = style.mediumEmphasis)
                                } else if (colorOption == com.music.musicflame.ui.theme.COLOR_MODE_RAINBOW) {
                                    Text(stringResource(R.string.settings_colores_del_espectro_en_movimiento), fontSize = 11.sp, color = style.mediumEmphasis)
                                }
                            }
                        }
                    }
                    if (tempEqColorMode.value == "Personalizado" && unlockedIds.contains("eq_color_custom")) {
                        Spacer(Modifier.height(4.dp))
                        ColorPresetRow(selectedHex = tempEqCustomHex.value, onSelect = { tempEqCustomHex.value = it })
                        Spacer(Modifier.height(12.dp))
                        ColorHexFieldAndPreview(hexValue = tempEqCustomHex.value, onHexChange = { tempEqCustomHex.value = it })
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

@OptIn(ExperimentalMaterial3Api::class)
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
                    Text(stringResource(R.string.settings_color_de_las_franjas_que_marcan_tu), fontSize = 12.sp, color = style.mediumEmphasis, modifier = Modifier.padding(bottom = 8.dp))
                    listOf("Adaptativo", "Personalizado", com.music.musicflame.ui.theme.COLOR_MODE_RAINBOW).forEach { colorOption ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { tempMomentsMode.value = colorOption }.padding(vertical = 8.dp)
                        ) {
                            RadioButton(selected = tempMomentsMode.value == colorOption, onClick = { tempMomentsMode.value = colorOption })
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(settingsOptionLabel(colorOption), fontSize = 14.sp)
                                Text(
                                    when (colorOption) {
                                        "Adaptativo" -> stringResource(R.string.settings_usa_el_color_del_tema_material_you)
                                        "Personalizado" -> stringResource(R.string.settings_elige_tu_propio_color)
                                        else -> stringResource(R.string.settings_colores_del_espectro_en_movimiento)
                                    },
                                    fontSize = 11.sp, color = style.mediumEmphasis
                                )
                            }
                        }
                    }
                    if (tempMomentsMode.value == "Personalizado") {
                        Spacer(Modifier.height(4.dp))
                        ColorPresetRow(selectedHex = tempMomentsHex.value, onSelect = { tempMomentsHex.value = it })
                        Spacer(Modifier.height(12.dp))
                        ColorHexFieldAndPreview(hexValue = tempMomentsHex.value, onHexChange = { tempMomentsHex.value = it })
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

@OptIn(ExperimentalMaterial3Api::class)
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
                    Text(stringResource(R.string.settings_color_de_acento_de_la_pantalla_en), fontSize = 12.sp, color = style.mediumEmphasis, modifier = Modifier.padding(bottom = 8.dp))
                    listOf("Adaptativo", "Personalizado", com.music.musicflame.ui.theme.COLOR_MODE_RAINBOW).forEach { colorOption ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { tempTogetherMode.value = colorOption }.padding(vertical = 8.dp)
                        ) {
                            RadioButton(selected = tempTogetherMode.value == colorOption, onClick = { tempTogetherMode.value = colorOption })
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(settingsOptionLabel(colorOption), fontSize = 14.sp)
                                Text(
                                    when (colorOption) {
                                        "Adaptativo" -> stringResource(R.string.settings_usa_el_color_del_tema_material_you)
                                        "Personalizado" -> stringResource(R.string.settings_elige_tu_propio_color)
                                        else -> stringResource(R.string.settings_colores_del_espectro_en_movimiento)
                                    },
                                    fontSize = 11.sp, color = style.mediumEmphasis
                                )
                            }
                        }
                    }
                    if (tempTogetherMode.value == "Personalizado") {
                        Spacer(Modifier.height(4.dp))
                        ColorPresetRow(selectedHex = tempTogetherHex.value, onSelect = { tempTogetherHex.value = it })
                        Spacer(Modifier.height(12.dp))
                        ColorHexFieldAndPreview(hexValue = tempTogetherHex.value, onHexChange = { tempTogetherHex.value = it })
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

@OptIn(ExperimentalMaterial3Api::class)
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
                    Text(stringResource(R.string.settings_color_texto_en_compania_desc), fontSize = 12.sp, color = style.mediumEmphasis, modifier = Modifier.padding(bottom = 8.dp))
                    listOf("Adaptativo", "Personalizado", com.music.musicflame.ui.theme.COLOR_MODE_RAINBOW).forEach { colorOption ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { tempTogetherMode.value = colorOption }.padding(vertical = 8.dp)
                        ) {
                            RadioButton(selected = tempTogetherMode.value == colorOption, onClick = { tempTogetherMode.value = colorOption })
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(settingsOptionLabel(colorOption), fontSize = 14.sp)
                                Text(
                                    when (colorOption) {
                                        "Adaptativo" -> stringResource(R.string.settings_together_text_adaptive_desc)
                                        "Personalizado" -> stringResource(R.string.settings_elige_tu_propio_color)
                                        else -> stringResource(R.string.settings_colores_del_espectro_en_movimiento)
                                    },
                                    fontSize = 11.sp, color = style.mediumEmphasis
                                )
                            }
                        }
                    }
                    if (tempTogetherMode.value == "Personalizado") {
                        Spacer(Modifier.height(4.dp))
                        ColorPresetRow(selectedHex = tempTogetherHex.value, onSelect = { tempTogetherHex.value = it })
                        Spacer(Modifier.height(12.dp))
                        ColorHexFieldAndPreview(hexValue = tempTogetherHex.value, onHexChange = { tempTogetherHex.value = it })
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

@OptIn(ExperimentalMaterial3Api::class)
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
                    Text(stringResource(R.string.settings_se_aplica_al_texto_de_la_letra_sin), fontSize = 12.sp, color = style.mediumEmphasis, modifier = Modifier.padding(bottom = 8.dp))
                    ColorPresetRow(selectedHex = tempLyricsCustomHex.value, onSelect = { tempLyricsCustomHex.value = it })
                    Spacer(Modifier.height(12.dp))
                    ColorHexFieldAndPreview(hexValue = tempLyricsCustomHex.value, onHexChange = { tempLyricsCustomHex.value = it })
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

@OptIn(ExperimentalMaterial3Api::class)
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
                    Text(stringResource(R.string.settings_aplica_al_indicador_animado_de_rep), fontSize = 12.sp, color = style.mediumEmphasis, modifier = Modifier.padding(bottom = 8.dp))
                    listOf("Adaptativo", "Personalizado", com.music.musicflame.ui.theme.COLOR_MODE_RAINBOW).forEach { colorOption ->
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
                                .clickable { if (locked) showLockedFeatureToast(if (colorOption == "Personalizado") "now_playing_custom" else "now_playing_rainbow") else tempNowPlayingColorMode.value = colorOption }
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
                                    Text(stringResource(R.string.settings_blanco_o_negro_segun_el_fondo_como), fontSize = 11.sp, color = style.mediumEmphasis)
                                } else if (colorOption == com.music.musicflame.ui.theme.COLOR_MODE_RAINBOW) {
                                    Text(stringResource(R.string.settings_colores_del_espectro_en_movimiento), fontSize = 11.sp, color = style.mediumEmphasis)
                                }
                            }
                        }
                    }
                    if (tempNowPlayingColorMode.value == "Personalizado" && unlockedIds.contains("now_playing_custom")) {
                        Spacer(Modifier.height(4.dp))
                        ColorPresetRow(selectedHex = tempNowPlayingCustomHex.value, onSelect = { tempNowPlayingCustomHex.value = it })
                        Spacer(Modifier.height(12.dp))
                        ColorHexFieldAndPreview(hexValue = tempNowPlayingCustomHex.value, onHexChange = { tempNowPlayingCustomHex.value = it })
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
