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

/** Diálogos de fondo del reproductor y de la app. Código movido desde SettingsDialogs.kt. */

@OptIn(ExperimentalMaterial3Api::class)
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
        val savedFpUri = remember { settingsRepo.getFullPlayerBgUri() }
        val savedFpConfig = remember { settingsRepo.loadFullPlayerBg() }
        val tempFpMode = remember { mutableStateOf(if (savedFpConfig != null) "custom" else "none") }
        val tempFpUri = remember { mutableStateOf(savedFpConfig?.uri) }
        val tempFpIsGif = remember { mutableStateOf(savedFpConfig?.isGif ?: false) }
        val tempFpBrightness = remember { mutableStateOf(settingsRepo.getFullPlayerBgBrightness().coerceIn(-1f, 1f)) }
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
                        settingsRepo.deleteFullPlayerBgFile(result.first)
                    } else if (result != null) {
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
            onDismissRequest = { discardCreatedFpFiles(except = null); showFullPlayerBgDialog.value = false },
            title = { Text(stringResource(R.string.settings_fondo_del_reproductor), fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(stringResource(R.string.settings_fondo_propio_solo_para_el_reproduc), fontSize = 12.sp, color = style.mediumEmphasis, modifier = Modifier.padding(bottom = 8.dp))
                    listOf("none", "custom").forEach { modeOption ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { tempFpMode.value = modeOption }.padding(vertical = 8.dp)
                        ) {
                            RadioButton(selected = tempFpMode.value == modeOption, onClick = { tempFpMode.value = modeOption })
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(if (modeOption == "none") stringResource(R.string.settings_sin_fondo) else stringResource(R.string.settings_imagen_gif), fontSize = 14.sp)
                                Text(if (modeOption == "none") stringResource(R.string.settings_el_reproductor_se_ve_como_siempre) else stringResource(R.string.settings_usa_tu_propia_imagen_o_gif_animado), fontSize = 11.sp, color = style.mediumEmphasis)
                            }
                        }
                    }

                    if (tempFpMode.value == "custom") {
                        Spacer(Modifier.height(4.dp))
                        OutlinedButton(onClick = { pickFullPlayerBgLauncher.launch("image/*") }, enabled = !isCopyingFp.value, modifier = Modifier.fillMaxWidth()) {
                            Text(if (isCopyingFp.value) stringResource(R.string.settings_copying) else stringResource(R.string.settings_elegir_imagen_o_gif), fontWeight = FontWeight.Bold)
                        }
                        val previewUri = tempFpUri.value
                        if (previewUri == null) {
                            Text(stringResource(R.string.settings_elige_una_imagen_o_gif_para_activa), fontSize = 11.sp, color = style.mediumEmphasis, modifier = Modifier.padding(top = 8.dp))
                        } else {
                            Spacer(Modifier.height(12.dp))
                            Text(stringResource(R.string.settings_vista_previa), fontSize = 12.sp, color = style.mediumEmphasis)
                            Spacer(Modifier.height(4.dp))
                            val previewRequest = remember(previewUri) {
                                ImageRequest.Builder(context).data(previewUri)
                                    .decoderFactory(if (android.os.Build.VERSION.SDK_INT >= 28) coil.decode.ImageDecoderDecoder.Factory() else coil.decode.GifDecoder.Factory())
                                    .build()
                            }
                            Box(modifier = Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(12.dp)).background(Color.Black)) {
                                AsyncImage(model = previewRequest, contentDescription = stringResource(R.string.settings_vista_previa_del_fondo), modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                val previewBrightness = tempFpBrightness.value
                                if (previewBrightness != 0f) {
                                    Box(modifier = Modifier.fillMaxSize().background(if (previewBrightness < 0f) Color.Black.copy(alpha = kotlin.math.abs(previewBrightness)) else Color.White.copy(alpha = previewBrightness)))
                                }
                                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.2f)))
                            }
                            Spacer(Modifier.height(12.dp))
                            Text(stringResource(R.string.settings_brillo_del_fondo), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Slider(value = tempFpBrightness.value, onValueChange = { tempFpBrightness.value = it }, valueRange = -1f..1f, steps = 20, colors = SliderDefaults.colors(thumbColor = style.trailingColor, activeTrackColor = style.trailingColor))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween) {
                                Text(stringResource(R.string.settings_oscuro), fontSize = 12.sp, color = style.mediumEmphasis)
                                Text(stringResource(R.string.settings_original), fontSize = 12.sp, color = style.mediumEmphasis)
                                Text(stringResource(R.string.settings_brillante), fontSize = 12.sp, color = style.mediumEmphasis)
                            }
                            TextButton(onClick = { tempFpBrightness.value = 0f }, enabled = tempFpBrightness.value != 0f) { Text(stringResource(R.string.settings_volver_a_0), fontWeight = FontWeight.Bold) }
                            Spacer(Modifier.height(4.dp))
                            OutlinedButton(
                                onClick = {
                                    val current = tempFpUri.value
                                    if (current != null && createdFpFiles.contains(current)) { settingsRepo.deleteFullPlayerBgFile(current); createdFpFiles.remove(current) }
                                    tempFpUri.value = null; tempFpIsGif.value = false; tempFpBrightness.value = 0f
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) { Text(stringResource(R.string.settings_quitar), fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            },
            confirmButton = {
                Button(enabled = !isCopyingFp.value, onClick = {
                    val finalUri = tempFpUri.value
                    val finalMode = if (tempFpMode.value == "custom" && finalUri != null) "custom" else "none"
                    if (savedFpUri != null && savedFpUri != finalUri) settingsRepo.deleteFullPlayerBgFile(savedFpUri)
                    discardCreatedFpFiles(except = finalUri)
                    settingsRepo.saveFullPlayerBgAll(mode = finalMode, uri = finalUri, isGif = tempFpIsGif.value, brightness = tempFpBrightness.value)
                    fullPlayerBgModePref.value = finalMode
                    fullPlayerBgIsGifPref.value = tempFpIsGif.value
                    showFullPlayerBgDialog.value = false
                }) { Text(stringResource(R.string.action_save), fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { discardCreatedFpFiles(except = null); showFullPlayerBgDialog.value = false }) { Text(stringResource(R.string.action_cancel), fontWeight = FontWeight.Bold) } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
        val tempAppUri = remember { mutableStateOf(backgroundImageUri.value ?: playerGifUri.value) }
        val tempAppIsGif = remember { mutableStateOf(backgroundImageUri.value == null && playerGifUri.value != null) }
        val tempAppMode = remember { mutableStateOf(if (backgroundImageUri.value != null || playerGifUri.value != null) "custom" else "none") }
        val tempAppBrightness = remember { mutableStateOf(backgroundBrightness.value.coerceIn(-1f, 1f)) }

        val pickAppBgLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { picked ->
            picked?.let {
                try { context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) {}
                tempAppUri.value = it.toString()
                tempAppIsGif.value = context.contentResolver.getType(it) == "image/gif" || it.toString().lowercase().endsWith(".gif")
                tempAppMode.value = "custom"
            }
        }

        AlertDialog(
            onDismissRequest = { showAppBgDialog.value = false },
            title = { Text(stringResource(R.string.settings_fondo_de_la_app), fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(stringResource(R.string.settings_imagen_o_gif_de_fondo_general_de_l), fontSize = 12.sp, color = style.mediumEmphasis, modifier = Modifier.padding(bottom = 8.dp))
                    listOf("none", "custom").forEach { modeOption ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { tempAppMode.value = modeOption }.padding(vertical = 8.dp)
                        ) {
                            RadioButton(selected = tempAppMode.value == modeOption, onClick = { tempAppMode.value = modeOption })
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(if (modeOption == "none") stringResource(R.string.settings_sin_fondo) else stringResource(R.string.settings_imagen_gif), fontSize = 14.sp)
                                Text(if (modeOption == "none") stringResource(R.string.settings_la_app_se_ve_como_siempre) else stringResource(R.string.settings_usa_tu_propia_imagen_o_gif_animado), fontSize = 11.sp, color = style.mediumEmphasis)
                            }
                        }
                    }
                    if (tempAppMode.value == "custom") {
                        Spacer(Modifier.height(4.dp))
                        OutlinedButton(onClick = { pickAppBgLauncher.launch("image/*") }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.settings_elegir_imagen_o_gif), fontWeight = FontWeight.Bold) }
                        val previewUri = tempAppUri.value
                        if (previewUri == null) {
                            Text(stringResource(R.string.settings_elige_una_imagen_o_gif_para_activa), fontSize = 11.sp, color = style.mediumEmphasis, modifier = Modifier.padding(top = 8.dp))
                        } else {
                            Spacer(Modifier.height(12.dp))
                            Text(stringResource(R.string.settings_vista_previa), fontSize = 12.sp, color = style.mediumEmphasis)
                            Spacer(Modifier.height(4.dp))
                            val previewRequest = remember(previewUri) {
                                ImageRequest.Builder(context).data(previewUri)
                                    .decoderFactory(if (android.os.Build.VERSION.SDK_INT >= 28) coil.decode.ImageDecoderDecoder.Factory() else coil.decode.GifDecoder.Factory())
                                    .build()
                            }
                            Box(modifier = Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(12.dp)).background(Color.Black)) {
                                AsyncImage(model = previewRequest, contentDescription = stringResource(R.string.settings_vista_previa_del_fondo), modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                val previewBrightness = tempAppBrightness.value
                                if (previewBrightness != 0f) {
                                    Box(modifier = Modifier.fillMaxSize().background(if (previewBrightness < 0f) Color.Black.copy(alpha = kotlin.math.abs(previewBrightness)) else Color.White.copy(alpha = previewBrightness)))
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            Text(stringResource(R.string.settings_brillo_del_fondo), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Slider(value = tempAppBrightness.value, onValueChange = { tempAppBrightness.value = it }, valueRange = -1f..1f, steps = 20, colors = SliderDefaults.colors(thumbColor = style.trailingColor, activeTrackColor = style.trailingColor))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween) {
                                Text(stringResource(R.string.settings_oscuro), fontSize = 12.sp, color = style.mediumEmphasis)
                                Text(stringResource(R.string.settings_original), fontSize = 12.sp, color = style.mediumEmphasis)
                                Text(stringResource(R.string.settings_brillante), fontSize = 12.sp, color = style.mediumEmphasis)
                            }
                            TextButton(onClick = { tempAppBrightness.value = 0f }, enabled = tempAppBrightness.value != 0f) { Text(stringResource(R.string.settings_volver_a_0), fontWeight = FontWeight.Bold) }
                            Spacer(Modifier.height(4.dp))
                            OutlinedButton(
                                onClick = { tempAppUri.value = null; tempAppIsGif.value = false; tempAppBrightness.value = 0f },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) { Text(stringResource(R.string.settings_quitar), fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val finalUri = if (tempAppMode.value == "custom") tempAppUri.value else null
                    if (finalUri == null) {
                        settingsRepo.removeBackgroundImage(); backgroundImageUri.value = null
                        settingsRepo.removePlayerGifUri(); playerGifUri.value = null
                    } else if (tempAppIsGif.value) {
                        settingsRepo.savePlayerGifUri(finalUri); playerGifUri.value = finalUri
                        settingsRepo.removeBackgroundImage(); backgroundImageUri.value = null
                    } else {
                        settingsRepo.saveBackgroundImageUri(finalUri); backgroundImageUri.value = finalUri
                        settingsRepo.removePlayerGifUri(); playerGifUri.value = null
                    }
                    settingsRepo.saveBackgroundBrightness(tempAppBrightness.value)
                    backgroundBrightness.value = tempAppBrightness.value
                    onBackgroundImageChanged()
                    showAppBgDialog.value = false
                }) { Text(stringResource(R.string.action_save), fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showAppBgDialog.value = false }) { Text(stringResource(R.string.action_cancel), fontWeight = FontWeight.Bold) } }
        )
    }
}
