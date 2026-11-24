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

/** Diálogos de ecualizador de Ajustes. Código movido desde SettingsDialogs.kt. */

@OptIn(ExperimentalMaterial3Api::class)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsProEqualizerDialog(
    showProEqualizerDialog: MutableState<Boolean>
) {
    if (showProEqualizerDialog.value) {
        ProEqualizerDialog(onDismiss = { showProEqualizerDialog.value = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
        val proEqAvailable = com.music.musicflame.data.ProStatusHolder.isItemUnlocked("pro_eq_10band")
        val proExclusiveOn = remember { mutableStateOf(sharedPrefs.getBoolean("pro_eq_exclusive", true)) }
        val showSaveCustomDialog = remember { mutableStateOf(false) }
        val customPresetName = remember { mutableStateOf("") }
        val customNamesString = sharedPrefs.getString("custom_preset_names", "") ?: ""
        val customPresetsList = if (customNamesString.isNotEmpty()) customNamesString.split(",") else emptyList()
        val basePresets = listOf("Flat", "Rock", "Pop", "Hip hop", "Jazz", "Classical", "Electronico", "Refuerzo de graves", "Refuerzo de agudos", "Vocales") + EqPresets.FREE_NAMES + "Customizar"
        val allPresets = basePresets + customPresetsList

        val fallbackFreqsHz = listOf(60, 230, 910, 3600, 14000)
        val realDeviceBandFreqsHz = remember {
            try {
                val probe = Equalizer(0, 0)
                val realBandCount = probe.numberOfBands.toInt()
                val freqs = (0 until realBandCount).map { i -> probe.getCenterFreq(i.toShort()) / 1000 }
                probe.release()
                freqs
            } catch (e: Exception) { emptyList<Int>() }
        }
        val eqBandCount = if (realDeviceBandFreqsHz.isNotEmpty()) minOf(5, realDeviceBandFreqsHz.size) else 5
        val eqFreqsHz = List(5) { i -> if (i < realDeviceBandFreqsHz.size) realDeviceBandFreqsHz[i] else fallbackFreqsHz[i] }

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

        Dialog(onDismissRequest = { showEqualizerDialog.value = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column {
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        IconButton(onClick = { showEqualizerDialog.value = false }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_close), tint = MaterialTheme.colorScheme.onBackground) }
                        Text(stringResource(R.string.settings_studio_pro_eq), fontSize = 20.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onBackground)
                        Button(onClick = {
                            settingsRepo.saveEqPresetSelected(tempPreset.value)
                            eqPresetSelected.value = tempPreset.value
                            for (i in 0 until 5) { settingsRepo.saveEqBand(i, tempSliders[i].value); slidersValues[i].value = tempSliders[i].value }
                            settingsRepo.saveBassBoost(tempBass.value); settingsRepo.saveVirtualizer(tempVirtualizer.value); settingsRepo.saveEqVolume(tempVolume.value)
                            bassBoost.value = tempBass.value; virtualizer.value = tempVirtualizer.value; eqVolume.value = tempVolume.value
                            sharedPrefs.edit().putFloat("loudness_enhancer", tempLoudness.value).putInt("reverb_preset", tempReverb.value).apply()
                            val intent = Intent("com.music.musicflame.UPDATE_EQ").also { it.setPackage(context.packageName) }
                            intent.putExtra("bass_boost", tempBass.value); intent.putExtra("virtualizer", tempVirtualizer.value)
                            intent.putExtra("loudness", tempLoudness.value); intent.putExtra("reverb", tempReverb.value)
                            for (i in 0 until 5) intent.putExtra("eq_band_$i", tempSliders[i].value)
                            context.sendBroadcast(intent)
                            showEqualizerDialog.value = false
                            Toast.makeText(context, context.getString(R.string.settings_audio_pro_activado), Toast.LENGTH_SHORT).show()
                        }) { Text(stringResource(R.string.settings_aplicar), fontWeight = FontWeight.Bold) }
                    }

                    LazyColumn(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        item { Spacer(Modifier.height(8.dp)) }
                        if (proEqAvailable) {
                            item {
                                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = if (proExclusiveOn.value) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainer)) {
                                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(if (proExclusiveOn.value) stringResource(R.string.settings_modo_pro_exclusivo_activo) else stringResource(R.string.settings_modo_pro_exclusivo_apagado), fontWeight = FontWeight.Black, fontSize = 14.sp)
                                            Text(if (proExclusiveOn.value) stringResource(R.string.settings_este_ecualizador_de_5_bandas_esta) else stringResource(R.string.settings_este_ecualizador_de_5_bandas_suena), fontSize = 12.sp)
                                        }
                                        Switch(checked = proExclusiveOn.value, onCheckedChange = { on ->
                                            proExclusiveOn.value = on
                                            sharedPrefs.edit().putBoolean("pro_eq_exclusive", on).apply()
                                            val sync = Intent("com.music.musicflame.UPDATE_EQ").also { it.setPackage(context.packageName) }
                                            context.sendBroadcast(sync)
                                        })
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
                                        for (i in 0 until eqBandCount) {
                                            val hzValue = eqFreqsHz[i]
                                            val label = if (viewKHz.value) String.format("%.2f", hzValue / 1000f) else hzValue.toString()
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                Spacer(Modifier.height(8.dp))
                                                Box(modifier = Modifier.height(150.dp).width(40.dp), contentAlignment = Alignment.Center) {
                                                    VerticalSlider(value = tempSliders[i].value, onValueChange = { tempSliders[i].value = it; tempPreset.value = "Customizar" }, valueRange = -1f..1f, modifier = Modifier.fillMaxSize())
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
                                    sharedPrefs.edit().putString("preset_${name}_bands", bandsStr).putFloat("preset_${name}_bass", tempBass.value).putFloat("preset_${name}_virt", tempVirtualizer.value).putFloat("preset_${name}_loud", tempLoudness.value).putInt("preset_${name}_reverb", tempReverb.value).apply()
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
