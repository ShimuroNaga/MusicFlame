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

/** Sección "Apariencia" de Ajustes. Código movido tal cual desde SettingsScreen.kt (sin cambios de comportamiento). */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
internal fun LazyListScope.settingsAppearanceSection(
    style: SettingsStyle,
    onRoundCornersChanged: (Boolean) -> Unit,
    onAlbumGridColumnsChanged: (Int) -> Unit,
    context: Context,
    settingsRepo: SettingsRepository,
    showThemeDialog: MutableState<Boolean>,
    showEqualizerStyleDialog: MutableState<Boolean>,
    showFontDialog: MutableState<Boolean>,
    showFontSizeDialog: MutableState<Boolean>,
    showLanguageDialog: MutableState<Boolean>,
    appLanguagePref: MutableState<com.music.musicflame.data.AppLanguage>,
    showTextColorDialog: MutableState<Boolean>,
    showEqualizerColorDialog: MutableState<Boolean>,
    showMomentsColorDialog: MutableState<Boolean>,
    showFullPlayerBgDialog: MutableState<Boolean>,
    showAppBgDialog: MutableState<Boolean>,
    fullPlayerBgModePref: MutableState<String>,
    fullPlayerBgIsGifPref: MutableState<Boolean>,
    showTogetherColorDialog: MutableState<Boolean>,
    showNowPlayingColorDialog: MutableState<Boolean>,
    appTheme: MutableState<String>,
    amoledMode: MutableState<Boolean>,
    useRoundCorners: MutableState<Boolean>,
    albumGridColumns: MutableState<Int>,
    equalizerBarCount: MutableState<Int>,
    equalizerStyle: MutableState<com.music.musicflame.ui.components.EqualizerStyle>,
    appFontPref: MutableState<com.music.musicflame.ui.theme.AppFont>,
    appFontSizePref: MutableState<Float>,
    equalizerColorModePref: MutableState<String>,
    equalizerCustomColorHexPref: MutableState<String>,
    momentsColorModePref: MutableState<String>,
    momentsCustomColorHexPref: MutableState<String>,
    togetherColorModePref: MutableState<String>,
    togetherCustomColorHexPref: MutableState<String>,
    nowPlayingColorModePref: MutableState<String>,
    nowPlayingCustomColorHexPref: MutableState<String>,
    albumArtShapePref: MutableState<com.music.musicflame.AlbumArtShapeType>,
    showAlbumArtShapeDialog: MutableState<Boolean>,
    coverDesigns: MutableState<List<com.music.musicflame.data.CoverDesign>>,
    activeCoverDesignId: MutableState<String?>,
    shapeDialogShape: MutableState<com.music.musicflame.AlbumArtShapeType>,
    shapeDialogDesignId: MutableState<String?>,
    iconPickerExpanded: MutableState<Boolean>,
    selectedAppIcon: MutableState<String>,
    appIconOptions: List<Triple<String, String, Int>>,
    appTextColorPref: MutableState<String>,
    widgetBackgroundOpacity: MutableState<Float>,
    isBgPresent: Boolean,
    isGifPresent: Boolean
) {
    item { SettingsSectionHeader(style, stringResource(R.string.section_appearance)) }

    item {
        // Fondo general de la app: mismo formato que "Fondo del reproductor".
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_fondo_de_la_app)) },
            supportingContent = {
                Text(
                    when {
                        isGifPresent -> stringResource(R.string.settings_gif_propio_activado)
                        isBgPresent -> stringResource(R.string.settings_imagen_propia_activada)
                        else -> stringResource(R.string.settings_sin_fondo)
                    }
                )
            },
            trailingContent = {
                TextButton(onClick = { showAppBgDialog.value = true }) {
                    Text(stringResource(R.string.action_change), fontWeight = FontWeight.ExtraBold, color = style.trailingColor)
                }
            },
            colors = style.listItemColors,
            modifier = Modifier.clickable { showAppBgDialog.value = true }
        )
        HorizontalDivider(color = style.dividerColor)
    }

    item {
        // NUEVO: fondo propio SOLO del reproductor expandido (no toca el fondo global).
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_fondo_del_reproductor)) },
            supportingContent = {
                Text(
                    if (fullPlayerBgModePref.value == "custom") {
                        if (fullPlayerBgIsGifPref.value) stringResource(R.string.settings_gif_propio_activado) else stringResource(R.string.settings_imagen_propia_activada)
                    } else {
                        stringResource(R.string.settings_sin_fondo)
                    }
                )
            },
            trailingContent = {
                TextButton(onClick = { showFullPlayerBgDialog.value = true }) {
                    Text(stringResource(R.string.action_change), fontWeight = FontWeight.ExtraBold, color = style.trailingColor)
                }
            },
            colors = style.listItemColors,
            modifier = Modifier.clickable { showFullPlayerBgDialog.value = true }
        )
        HorizontalDivider(color = style.dividerColor)
    }

    item {
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_apariencia_de_la_aplicacion)) },
            supportingContent = { Text(stringResource(R.string.settings_tema_actual_1_s, settingsOptionLabel(appTheme.value))) },
            trailingContent = { TextButton(onClick = { showThemeDialog.value = true }) { Text(stringResource(R.string.action_change), fontWeight = FontWeight.ExtraBold, color = style.trailingColor) } },
            colors = style.listItemColors
        )
        HorizontalDivider(color = style.dividerColor)
    }

    item {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(stringResource(R.string.settings_tamano_de_caratulas_de_albumes), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = style.trailingColor)
            Spacer(Modifier.height(4.dp))
            Slider(
                value = albumGridColumns.value.toFloat(),
                onValueChange = {
                    val columns = it.toInt()
                    albumGridColumns.value = columns
                    settingsRepo.saveAlbumGridColumns(columns)
                    onAlbumGridColumnsChanged(columns)
                },
                valueRange = 2f..4f,
                steps = 1, // dos pasos intermedios entre 2 y 4 -> valores posibles: 2, 3, 4
                colors = SliderDefaults.colors(
                    thumbColor = style.trailingColor,
                    activeTrackColor = style.trailingColor
                )
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.settings_grande), fontSize = 12.sp, color = style.mediumEmphasis)
                Text(stringResource(R.string.settings_chico), fontSize = 12.sp, color = style.mediumEmphasis)
            }
        }
        HorizontalDivider(color = style.dividerColor)
    }

    item {
        // NUEVO: cantidad de barras del ecualizador gráfico animado que se
        // ve en el reproductor a pantalla completa. 6 = mínimo, 32 =
        // estándar (default), 64 = máximo.
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(stringResource(R.string.settings_barras_del_ecualizador_grafico), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = style.trailingColor)
            Spacer(Modifier.height(4.dp))
            Slider(
                value = equalizerBarCount.value.toFloat(),
                onValueChange = {
                    val count = it.toInt()
                    equalizerBarCount.value = count
                    settingsRepo.saveEqualizerBarCount(count)
                },
                valueRange = 6f..64f,
                steps = 57, // un paso por cada valor entero entre 6 y 64
                colors = SliderDefaults.colors(
                    thumbColor = style.trailingColor,
                    activeTrackColor = style.trailingColor
                )
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.settings_6_minimo), fontSize = 12.sp, color = style.mediumEmphasis)
                Text(stringResource(R.string.settings_1_s_barras, equalizerBarCount.value), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = style.trailingColor)
                Text(stringResource(R.string.settings_64_maximo), fontSize = 12.sp, color = style.mediumEmphasis)
            }
            Text(
                stringResource(R.string.settings_se_aplica_la_proxima_vez_que_abras),
                fontSize = 11.sp,
                color = style.mediumEmphasis,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        HorizontalDivider(color = style.dividerColor)
    }

    item {
        // NUEVO: selector de estilo visual del ecualizador gráfico
        // (barras clásicas, doble espejado, ondas de agua, círculo
        // pulsante, partículas, barras finas o VU meter retro).
        // Parte del catálogo de personalizaciones estéticas — por
        // ahora libre para probar (ver
        // SettingsRepository.EQUALIZER_STYLES_UNLOCKED_FOR_TESTING).
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_estilo_de_ecualizador_grafico)) },
            supportingContent = { Text(stringResource(equalizerStyle.value.displayNameRes)) },
            trailingContent = {
                TextButton(onClick = { showEqualizerStyleDialog.value = true }) {
                    Text(stringResource(R.string.action_change), fontWeight = FontWeight.ExtraBold, color = style.trailingColor)
                }
            },
            colors = style.listItemColors,
            modifier = Modifier.clickable { showEqualizerStyleDialog.value = true }
        )
        HorizontalDivider(color = style.dividerColor)
    }

    item {
        // NUEVO: tipo de letra global (catálogo, ideas de fuentes).
        // Se aplica a TODA la app, no solo al título de la canción.
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_tipo_de_letra)) },
            supportingContent = {
                Text(appFontPref.value.displayName, fontFamily = appFontPref.value.fontFamily)
            },
            trailingContent = {
                TextButton(onClick = { showFontDialog.value = true }) {
                    Text(stringResource(R.string.action_change), fontWeight = FontWeight.ExtraBold, color = style.trailingColor)
                }
            },
            colors = style.listItemColors,
            modifier = Modifier.clickable { showFontDialog.value = true }
        )
        HorizontalDivider(color = style.dividerColor)
    }

    item {
        // NUEVO: tamaño de letra global. Gratis (usabilidad, no
        // cosmético puro) — afecta a toda la app vía appTypographyFor().
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_tamano_de_letra)) },
            supportingContent = { Text("${appFontSizePref.value.toInt()} sp") },
            trailingContent = {
                TextButton(onClick = { showFontSizeDialog.value = true }) {
                    Text(stringResource(R.string.action_change), fontWeight = FontWeight.ExtraBold, color = style.trailingColor)
                }
            },
            colors = style.listItemColors,
            modifier = Modifier.clickable { showFontSizeDialog.value = true }
        )
        HorizontalDivider(color = style.dividerColor)
    }

    item {
        // Idioma de la app (es / en / sistema). Ver LanguageManager.kt
        ListItem(
            headlineContent = { Text(stringResource(R.string.language_title)) },
            supportingContent = {
                Text(com.music.musicflame.ui.components.appLanguageLabel(appLanguagePref.value))
            },
            trailingContent = {
                TextButton(onClick = { showLanguageDialog.value = true }) {
                    Text(stringResource(R.string.action_change), fontWeight = FontWeight.ExtraBold, color = style.trailingColor)
                }
            },
            colors = style.listItemColors,
            modifier = Modifier.clickable { showLanguageDialog.value = true }
        )
        HorizontalDivider(color = style.dividerColor)
    }

    item {
        // NUEVO: color propio del ecualizador gráfico (catálogo,
        // punto 1). Ortogonal al estilo de arriba — aplica sea
        // cual sea el estilo elegido. Mismo patrón de diálogo
        // que "Color de texto" (presets + hex/RGBA manual).
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_color_del_ecualizador)) },
            supportingContent = {
                Text(
                    when (equalizerColorModePref.value) {
                        "Personalizado" -> stringResource(R.string.settings_personalizado_1_s, equalizerCustomColorHexPref.value)
                        com.music.musicflame.ui.theme.COLOR_MODE_RAINBOW -> stringResource(R.string.settings_arcoiris_en_movimiento)
                        else -> stringResource(R.string.settings_adaptativo_segun_el_fondo)
                    }
                )
            },
            trailingContent = {
                TextButton(onClick = { showEqualizerColorDialog.value = true }) {
                    Text(stringResource(R.string.action_change), fontWeight = FontWeight.ExtraBold, color = style.trailingColor)
                }
            },
            colors = style.listItemColors,
            modifier = Modifier.clickable { showEqualizerColorDialog.value = true }
        )
        HorizontalDivider(color = style.dividerColor)
    }

    item {
        // NUEVO: color de las franjas de Momentos en la barra de progreso.
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_color_de_momentos)) },
            supportingContent = {
                Text(
                    when (momentsColorModePref.value) {
                        "Personalizado" -> stringResource(R.string.settings_personalizado_1_s, momentsCustomColorHexPref.value)
                        com.music.musicflame.ui.theme.COLOR_MODE_RAINBOW -> stringResource(R.string.settings_arcoiris_en_movimiento)
                        else -> stringResource(R.string.settings_adaptativo_color_del_tema)
                    }
                )
            },
            trailingContent = {
                TextButton(onClick = { showMomentsColorDialog.value = true }) {
                    Text(stringResource(R.string.action_change), fontWeight = FontWeight.ExtraBold, color = style.trailingColor)
                }
            },
            colors = style.listItemColors,
            modifier = Modifier.clickable { showMomentsColorDialog.value = true }
        )
        HorizontalDivider(color = style.dividerColor)
    }

    item {
        // NUEVO: color de acento de la pantalla "En compañía".
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_color_de_en_compania)) },
            supportingContent = {
                Text(
                    when (togetherColorModePref.value) {
                        "Personalizado" -> stringResource(R.string.settings_personalizado_1_s, togetherCustomColorHexPref.value)
                        com.music.musicflame.ui.theme.COLOR_MODE_RAINBOW -> stringResource(R.string.settings_arcoiris_en_movimiento)
                        else -> stringResource(R.string.settings_adaptativo_color_del_tema)
                    }
                )
            },
            trailingContent = {
                TextButton(onClick = { showTogetherColorDialog.value = true }) {
                    Text(stringResource(R.string.action_change), fontWeight = FontWeight.ExtraBold, color = style.trailingColor)
                }
            },
            colors = style.listItemColors,
            modifier = Modifier.clickable { showTogetherColorDialog.value = true }
        )
        HorizontalDivider(color = style.dividerColor)
    }

    item {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(stringResource(R.string.settings_opacidad_del_fondo_del_widget), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = style.trailingColor)
            Spacer(Modifier.height(4.dp))
            Slider(
                value = widgetBackgroundOpacity.value,
                onValueChange = {
                    widgetBackgroundOpacity.value = it
                    settingsRepo.saveWidgetBackgroundOpacity(it)
                    MusicFlameWidgetProvider.refreshAllWidgets(context)
                },
                valueRange = 0f..1f,
                steps = 9, // pasos de 10% en 10%
                colors = SliderDefaults.colors(
                    thumbColor = style.trailingColor,
                    activeTrackColor = style.trailingColor
                )
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.settings_transparente), fontSize = 12.sp, color = style.mediumEmphasis)
                Text(stringResource(R.string.settings_opaco), fontSize = 12.sp, color = style.mediumEmphasis)
            }
            Text(
                stringResource(R.string.settings_el_texto_del_widget_siempre_lleva),
                fontSize = 11.sp,
                color = style.mediumEmphasis,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        HorizontalDivider(color = style.dividerColor)
    }

    item {
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_color_de_texto)) },
            supportingContent = {
                Text(
                    stringResource(R.string.settings_current_color, if (appTextColorPref.value == com.music.musicflame.ui.theme.COLOR_MODE_RAINBOW) stringResource(R.string.settings_arcoiris_en_movimiento) else settingsOptionLabel(appTextColorPref.value))
                )
            },
            trailingContent = { TextButton(onClick = { showTextColorDialog.value = true }) { Text(stringResource(R.string.action_change), fontWeight = FontWeight.ExtraBold, color = style.trailingColor) } },
            colors = style.listItemColors
        )
        HorizontalDivider(color = style.dividerColor)
    }

    item {
        // NUEVO: color único del "Now Playing" indicator (catálogo,
        // punto 3). Mismo patrón de diálogo que "Color del
        // ecualizador": Adaptativo (blanco/negro según el fondo,
        // como hasta ahora) o Personalizado (presets + hex/RGBA).
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_color_del_now_playing)) },
            supportingContent = {
                Text(
                    when (nowPlayingColorModePref.value) {
                        "Personalizado" -> stringResource(R.string.settings_personalizado_1_s, nowPlayingCustomColorHexPref.value)
                        com.music.musicflame.ui.theme.COLOR_MODE_RAINBOW -> stringResource(R.string.settings_arcoiris_en_movimiento)
                        else -> stringResource(R.string.settings_adaptativo_segun_el_fondo)
                    }
                )
            },
            trailingContent = {
                TextButton(onClick = { showNowPlayingColorDialog.value = true }) {
                    Text(stringResource(R.string.action_change), fontWeight = FontWeight.ExtraBold, color = style.trailingColor)
                }
            },
            colors = style.listItemColors,
            modifier = Modifier.clickable { showNowPlayingColorDialog.value = true }
        )
        HorizontalDivider(color = style.dividerColor)
    }

    item {
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_modo_amoled_negro_puro)) },
            supportingContent = { Text(stringResource(R.string.settings_apaga_pixeles_para_ahorro_extremo)) },
            trailingContent = {
                Switch(
                    checked = amoledMode.value,
                    onCheckedChange = { isChecked ->
                        amoledMode.value = isChecked
                        settingsRepo.saveAmoledMode(isChecked)
                    }
                )
            },
            colors = style.listItemColors
        )
        HorizontalDivider(color = style.dividerColor)
    }

    item {
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_usar_redondeado_de_cuadros)) },
            supportingContent = { Text(stringResource(R.string.settings_aplica_bordes_curvos_a_las_seccion)) },
            trailingContent = {
                Switch(
                    checked = useRoundCorners.value,
                    onCheckedChange = { isChecked ->
                        useRoundCorners.value = isChecked
                        settingsRepo.saveUseRoundCorners(isChecked)
                        onRoundCornersChanged(isChecked)
                    }
                )
            },
            colors = style.listItemColors
        )
        HorizontalDivider(color = style.dividerColor)
    }

    item {
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_forma_de_la_caratula)) },
            supportingContent = {
                Text(
                    stringResource(R.string.settings_current_shape, when (albumArtShapePref.value) {
                        com.music.musicflame.AlbumArtShapeType.SQUARE -> stringResource(R.string.settings_cuadrado)
                        com.music.musicflame.AlbumArtShapeType.CIRCLE -> stringResource(R.string.settings_circulo)
                        com.music.musicflame.AlbumArtShapeType.HEXAGON -> stringResource(R.string.settings_hexagono)
                        com.music.musicflame.AlbumArtShapeType.VINYL -> stringResource(R.string.settings_vinilo)
                        com.music.musicflame.AlbumArtShapeType.SQUIRCLE -> stringResource(R.string.settings_squircle)
                        com.music.musicflame.AlbumArtShapeType.CUSTOM ->
                            coverDesigns.value.firstOrNull { it.id == activeCoverDesignId.value }?.name ?: stringResource(R.string.option_custom)
                    })
                )
            },
            trailingContent = { TextButton(onClick = {
                // El diálogo arranca mostrando lo que está aplicado ahora mismo.
                shapeDialogShape.value = albumArtShapePref.value
                shapeDialogDesignId.value = activeCoverDesignId.value
                showAlbumArtShapeDialog.value = true
            }) { Text(stringResource(R.string.action_change), fontWeight = FontWeight.ExtraBold, color = style.trailingColor) } },
            colors = style.listItemColors
        )
        HorizontalDivider(color = style.dividerColor)
    }

    item {
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_icono_de_la_app)) },
            supportingContent = { Text(stringResource(R.string.settings_elige_entre_los_iconos_predetermin)) },
            trailingContent = {
                Icon(
                    imageVector = if (iconPickerExpanded.value) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = style.trailingColor
                )
            },
            colors = style.listItemColors,
            modifier = Modifier.clickable { iconPickerExpanded.value = !iconPickerExpanded.value }
        )
        HorizontalDivider(color = style.dividerColor)
    }

    item {
        AnimatedVisibility(visible = iconPickerExpanded.value) {
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                items(appIconOptions) { (key, label, previewRes) ->
                    val isSelected = selectedAppIcon.value == key
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable {
                            selectedAppIcon.value = key
                            settingsRepo.saveSelectedAppIcon(key)
                            AppIconManager.setIcon(context, key)
                            Toast.makeText(context, context.getString(R.string.settings_icono_cambiado_a_1_s, label), Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    if (isSelected) style.trailingColor.copy(alpha = 0.15f)
                                    else Color.Transparent
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = previewRes,
                                contentDescription = label,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            label,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) style.trailingColor else style.mediumEmphasis
                        )
                    }
                }
            }
        }
        HorizontalDivider(color = style.dividerColor)
    }

}
