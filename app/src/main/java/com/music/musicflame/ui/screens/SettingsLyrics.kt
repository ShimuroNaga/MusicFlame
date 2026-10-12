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

/** Sección "Lyrics" de Ajustes. Código movido tal cual desde SettingsScreen.kt (sin cambios de comportamiento). */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
internal fun LazyListScope.settingsLyricsSection(
    context: Context,
    settingsRepo: SettingsRepository,
    showLyricsColorDialog: MutableState<Boolean>,
    lyricsColorModePref: MutableState<String>,
    lyricsCustomColorHexPref: MutableState<String>,
    unlockedIds: Set<String>,
    showLockedLyricsColorToast: () -> Unit
) {
    item {
        val settingsRepo = remember { com.music.musicflame.data.SettingsRepository(context) }
        var speed by remember { mutableStateOf(settingsRepo.getLyricsSpeed()) }
        var animType by remember { mutableStateOf(settingsRepo.getLyricsAnimationType()) }
        var lyricsInWidget by remember { mutableStateOf(settingsRepo.isLyricsInWidgetEnabled()) }
        var fullLyricsSquareWidget by remember { mutableStateOf(settingsRepo.isFullLyricsSquareWidgetEnabled()) }

        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Lyrics",
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Text(
                    stringResource(R.string.settings_controla_como_se_anima_y_se_ve_la),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                )

                Spacer(Modifier.height(20.dp))
                Text(
                    stringResource(R.string.settings_anim_speed, "%.1f".format(speed)),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                androidx.compose.material3.Slider(
                    value = speed,
                    onValueChange = {
                        speed = it
                        settingsRepo.saveLyricsSpeed(it)
                    },
                    valueRange = 0.5f..2f,
                    colors = androidx.compose.material3.SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        activeTrackColor = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.settings_lenta), fontSize = 11.sp, color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f))
                    Text(stringResource(R.string.settings_rapida), fontSize = 11.sp, color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f))
                }

                Spacer(Modifier.height(20.dp))
                Text(
                    stringResource(R.string.settings_tipo_de_animacion),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Deslizar", "Desvanecer", "Rebote").forEach { opt ->
                        val selected = animType == opt
                        androidx.compose.material3.FilterChip(
                            selected = selected,
                            onClick = {
                                animType = opt
                                settingsRepo.saveLyricsAnimationType(opt)
                            },
                            label = { Text(settingsOptionLabel(opt), fontSize = 12.sp) },
                            colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.tertiaryContainer
                            )
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))
                Text(
                    stringResource(R.string.settings_color_del_texto),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Spacer(Modifier.height(8.dp))
                // Los chips se definen una vez para poder repartirlos en dos filas:
                // Blanco/Negro/Personalizado arriba y Arcoíris solo, abajo.
                val lyricsColorChip: @Composable (String) -> Unit = { opt ->
                    val selected = lyricsColorModePref.value == opt
                    // Personalizado y Arcoíris son de pago acá; Blanco y Negro gratis.
                    val locked = (opt == "Personalizado" && !unlockedIds.contains("lyrics_custom")) ||
                            (opt == com.music.musicflame.ui.theme.COLOR_MODE_RAINBOW && !unlockedIds.contains("lyrics_rainbow"))
                    androidx.compose.material3.FilterChip(
                        selected = selected,
                        enabled = !locked,
                        onClick = {
                            if (locked) {
                                showLockedLyricsColorToast()
                            } else if (opt == "Personalizado") {
                                // Selección visual inmediata; el modo recién se
                                // persiste al confirmar un color en el diálogo
                                // (igual que "Color del ecualizador").
                                lyricsColorModePref.value = "Personalizado"
                                showLyricsColorDialog.value = true
                            } else {
                                lyricsColorModePref.value = opt
                                settingsRepo.saveLyricsTextColorMode(opt)
                            }
                        },
                        leadingIcon = if (locked) {
                            { Icon(Icons.Filled.Lock, contentDescription = stringResource(R.string.settings_bloqueado), modifier = Modifier.size(14.dp)) }
                        } else null,
                        label = { Text(settingsOptionLabel(opt), fontSize = 12.sp) },
                        colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Blanco", "Negro", "Personalizado").forEach { lyricsColorChip(it) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    lyricsColorChip(com.music.musicflame.ui.theme.COLOR_MODE_RAINBOW)
                }
                if (lyricsColorModePref.value == "Personalizado") {
                    Spacer(Modifier.height(10.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { showLyricsColorDialog.value = true }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(com.music.musicflame.ui.theme.parseCustomTextColor(lyricsCustomColorHexPref.value))
                                .border(1.dp, Color.Gray, RoundedCornerShape(6.dp))
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.settings_1_s_cambiar, lyricsCustomColorHexPref.value),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.settings_este_color_tambien_se_aplica_a_la),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.65f)
                )

                Spacer(Modifier.height(20.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.2f))
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.settings_letra_en_vivo_en_el_widget),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Text(
                            stringResource(R.string.settings_muestra_la_linea_activa_de_la_letr),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = lyricsInWidget,
                        onCheckedChange = {
                            lyricsInWidget = it
                            settingsRepo.saveLyricsInWidgetEnabled(it)
                            MusicFlameWidgetProvider.refreshAllWidgets(context)
                        },
                        colors = androidx.compose.material3.SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.tertiaryContainer,
                            checkedTrackColor = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    )
                }

                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.settings_widget_cuadrado_de_letra_completa),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Text(
                            stringResource(R.string.settings_cambia_el_widget_del_home_screen_a),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = fullLyricsSquareWidget,
                        onCheckedChange = {
                            fullLyricsSquareWidget = it
                            settingsRepo.saveFullLyricsSquareWidgetEnabled(it)
                            MusicFlameWidgetProvider.refreshAllWidgets(context)
                        },
                        colors = androidx.compose.material3.SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.tertiaryContainer,
                            checkedTrackColor = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    )
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.settings_para_ver_la_letra_abre_el_reproduc),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.settings_nota_la_letra_encontrada_mediante),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.settings_obtener_letra_sincronizada_lrc_man),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                    modifier = Modifier.clickable {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://lrclib.net"))
                        context.startActivity(intent)
                    }
                )
            }
        }
    }
}
