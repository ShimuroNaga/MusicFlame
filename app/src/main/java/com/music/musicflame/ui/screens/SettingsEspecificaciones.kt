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

/** Sección "Especificaciones" de Ajustes. Código movido tal cual desde SettingsScreen.kt (sin cambios de comportamiento). */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
internal fun LazyListScope.settingsEspecificacionesSection(
    style: SettingsStyle,
    onCheckForUpdates: () -> Unit,
    context: Context
) {
    item { SettingsSectionHeader(style, stringResource(R.string.settings_sobre)) }
    item { ListItem(headlineContent = { Text(stringResource(R.string.settings_version)) }, supportingContent = { Text("3.13") }, colors = style.listItemColors); HorizontalDivider(color = style.dividerColor) }

    // BOTÓN DE ACTUALIZACIONES (CARD)
    item {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .clickable { onCheckForUpdates() },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.SystemUpdate,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(stringResource(R.string.settings_actualizaciones), fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.settings_buscar_nueva_version), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    item { SettingsSectionHeader(style, stringResource(R.string.settings_especificaciones_tecnicas)) }

    item {
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_framework_ui)) },
            supportingContent = { Text("Jetpack Compose") },
            colors = style.listItemColors
        ); HorizontalDivider(color = style.dividerColor)
    }

    item {
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_lenguaje_de_diseno)) },
            supportingContent = { Text("Material Design 3") },
            colors = style.listItemColors
        ); HorizontalDivider(color = style.dividerColor)
    }

    item {
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_paleta_de_colores)) },
            supportingContent = { Text(stringResource(R.string.settings_material_you_dynamic)) },
            colors = style.listItemColors
        ); HorizontalDivider(color = style.dividerColor)
    }

    item {
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_arquitectura)) },
            supportingContent = { Text(stringResource(R.string.settings_declarativa_y_modular)) },
            colors = style.listItemColors
        )
    }
    item {
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_creador_de_codigo)) },
            supportingContent = { Text("ShimuroNaga") },
            leadingContent = {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data("https://github.com/ShimuroNaga.png")
                        .crossfade(true)
                        .build(),
                    contentDescription = stringResource(R.string.settings_avatar_de_shimuronaga),
                    contentScale = ContentScale.Crop,
                    placeholder = androidx.compose.ui.graphics.painter.ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                    error = androidx.compose.ui.graphics.painter.ColorPainter(MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                )
            },
            colors = style.listItemColors
        )
        HorizontalDivider(color = style.dividerColor)
    }

    item { SettingsSectionHeader(style, "Tester") }

    item {
        ListItem(
            headlineContent = { Text("Tester") },
            supportingContent = { Text("Naofresita18") },
            leadingContent = {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data("https://github.com/Naofresita18.png")
                        .crossfade(true)
                        .build(),
                    contentDescription = stringResource(R.string.settings_avatar_de_naofresita18),
                    contentScale = ContentScale.Crop,
                    placeholder = androidx.compose.ui.graphics.painter.ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                    error = androidx.compose.ui.graphics.painter.ColorPainter(MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                )
            },
            colors = style.listItemColors
        )
    }
    item {
        ListItem(
            headlineContent = { Text("Tester") },
            supportingContent = { Text("deivid-boop") },
            leadingContent = {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data("https://github.com/deivid-boop.png")
                        .crossfade(true)
                        .build(),
                    contentDescription = stringResource(R.string.settings_avatar_de_deivid_boop),
                    contentScale = ContentScale.Crop,
                    placeholder = androidx.compose.ui.graphics.painter.ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                    error = androidx.compose.ui.graphics.painter.ColorPainter(MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                )
            },
            colors = style.listItemColors
        )
    }

    item { SettingsSectionHeader(style, stringResource(R.string.settings_comunidad)) }

    item {
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_repositorio_en_github)) },
            supportingContent = { Text(stringResource(R.string.settings_codigo_fuente_y_changelog_de_music)) },
            leadingContent = {
                Icon(
                    painter = painterResource(id = R.drawable.ic_github),
                    contentDescription = "GitHub",
                    modifier = Modifier.size(28.dp),
                    tint = style.highEmphasis
                )
            },
            colors = style.listItemColors,
            modifier = Modifier.clickable {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/ShimuroNaga/MusicFlame"))
                context.startActivity(intent)
            }
        )
        HorizontalDivider(color = style.dividerColor)
    }

    item {
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_unete_al_discord)) },
            supportingContent = { Text(stringResource(R.string.settings_comunidad_soporte_y_novedades_de_l)) },
            leadingContent = {
                Icon(
                    painter = painterResource(id = R.drawable.ic_discord),
                    contentDescription = "Discord",
                    modifier = Modifier.size(28.dp),
                    tint = Color(0xFF5865F2)
                )
            },
            colors = style.listItemColors,
            modifier = Modifier.clickable {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://discord.gg/gGZ4zCZvab"))
                context.startActivity(intent)
            }
        )
    }

    item { SettingsSectionHeader(style, stringResource(R.string.settings_colaboraciones)) }

    item {
        ListItem(
            headlineContent = { Text("GreenyPika") },
            supportingContent = { Text(stringResource(R.string.settings_artista)) },
            leadingContent = {
                Image(
                    painter = painterResource(id = R.drawable.ic_artist_greenypika),
                    contentDescription = "GreenyPika",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                )
            },
            colors = style.listItemColors,
            modifier = Modifier.clickable {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://open.spotify.com/artist/0jaY21Zd3ouygWqNdIpr8N?si=3IVegY4ESPuKJyJTLgiU9A&utm_source=copy-link"))
                context.startActivity(intent)
            }
        )
        HorizontalDivider(color = style.dividerColor)
    }

    item {
        ListItem(
            headlineContent = { Text("Bear Spring") },
            supportingContent = { Text(stringResource(R.string.settings_artista)) },
            leadingContent = {
                Image(
                    painter = painterResource(id = R.drawable.ic_artist_bearspring),
                    contentDescription = "Bear Spring",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                )
            },
            colors = style.listItemColors,
            modifier = Modifier.clickable {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.instagram.com/berspring7"))
                context.startActivity(intent)
            }
        )
    }
}
