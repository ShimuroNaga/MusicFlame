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
import androidx.compose.ui.graphics.asImageBitmap
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

/** Sección "Cuenta" de Ajustes. Código movido tal cual desde SettingsScreen.kt (sin cambios de comportamiento). */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
internal fun LazyListScope.settingsAccountSection(
    style: SettingsStyle,
    isUserSignedIn: Boolean,
    userName: String?,
    userPhotoUrl: String?,
    onSignInClick: () -> Unit,
    onProfileClick: () -> Unit,
    isDriveLinked: Boolean,
    onLinkDriveClick: () -> Unit,
    context: Context,
    avatarRefreshKeyState: MutableIntState
) {
    var avatarRefreshKey by avatarRefreshKeyState

    item { SettingsSectionHeader(style, stringResource(R.string.section_account)) }

    item {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { if (isUserSignedIn) onProfileClick() else onSignInClick() }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(
                        if (isUserSignedIn) Color.Transparent
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isUserSignedIn && !userPhotoUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(userPhotoUrl)
                            .memoryCacheKey("$userPhotoUrl-$avatarRefreshKey")
                            .diskCacheKey("$userPhotoUrl-$avatarRefreshKey")
                            .build(),
                        contentDescription = stringResource(R.string.settings_foto_de_perfil),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.AccountCircle,
                        contentDescription = stringResource(R.string.settings_sin_foto_de_perfil),
                        modifier = Modifier.size(64.dp),
                        tint = if (isUserSignedIn) style.trailingColor
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }

            Spacer(Modifier.width(14.dp))

            Column {
                if (isUserSignedIn && !userName.isNullOrEmpty()) {
                    Text(userName, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = style.highEmphasis)
                    Text(stringResource(R.string.settings_toca_para_ver_tu_cuenta), fontSize = 13.sp, color = style.mediumEmphasis)
                } else {
                    Text(
                        stringResource(R.string.settings_sin_usuario_por_favor_registrese),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = style.mediumEmphasis
                    )
                    Text(
                        stringResource(R.string.settings_inicia_sesion_con_google),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = style.trailingColor
                    )
                }
            }
        }

        HorizontalDivider(color = style.dividerColor)
    }

    // ICONO DE "EN COMPAÑÍA": se muestra a los demás en la sala (editor de recorte estilo Discord)
    item {
        var avatarB64 by remember { mutableStateOf(com.music.musicflame.together.TogetherAvatar.load(context)) }
        val avatarBitmap = remember(avatarB64) {
            com.music.musicflame.together.TogetherAvatar.decode(avatarB64)?.asImageBitmap()
        }
        var pickedUri by remember { mutableStateOf<Uri?>(null) }
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) pickedUri = uri
        }

        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_together_icon_title)) },
            supportingContent = { Text(stringResource(R.string.settings_together_icon_desc)) },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (avatarBitmap != null) {
                        IconButton(onClick = {
                            com.music.musicflame.together.TogetherAvatar.clear(context)
                            avatarB64 = null
                        }) {
                            Icon(Icons.Filled.Delete, stringResource(R.string.settings_together_icon_remove), tint = style.mediumEmphasis)
                        }
                    }
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        if (avatarBitmap != null) {
                            Image(
                                bitmap = avatarBitmap,
                                contentDescription = stringResource(R.string.settings_together_icon_title),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(Icons.Filled.Add, null, tint = style.trailingColor)
                        }
                    }
                }
            },
            colors = style.listItemColors,
            modifier = Modifier.clickable { picker.launch("image/*") }
        )
        HorizontalDivider(color = style.dividerColor)

        pickedUri?.let { uri ->
            com.music.musicflame.ui.components.AvatarCropDialog(
                uri = uri,
                onDismiss = { pickedUri = null },
                onConfirm = { bmp ->
                    com.music.musicflame.together.TogetherAvatar.save(context, bmp)
                    avatarB64 = com.music.musicflame.together.TogetherAvatar.load(context)
                    pickedUri = null
                }
            )
        }
    }

    item {
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_vincular_google_drive)) },
            supportingContent = {
                Text(
                    if (isDriveLinked) stringResource(R.string.settings_google_drive_vinculado_con_tu_cuen)
                    else stringResource(R.string.settings_vincula_tu_cuenta_para_respaldar_t)
                )
            },
            trailingContent = {
                Icon(
                    imageVector = Icons.Filled.CloudDone,
                    contentDescription = null,
                    tint = if (isDriveLinked) style.trailingColor
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            },
            colors = style.listItemColors,
            modifier = Modifier.clickable {
                if (isUserSignedIn) onLinkDriveClick() else onSignInClick()
            }
        )
        HorizontalDivider(color = style.dividerColor)
    }

    item {
        Spacer(Modifier.height(4.dp))
        DeviceInfoCard(
            highEmphasis = style.highEmphasis,
            mediumEmphasis = style.mediumEmphasis,
            trailingColor = style.trailingColor
        )
    }

    // APARIENCIA
}
