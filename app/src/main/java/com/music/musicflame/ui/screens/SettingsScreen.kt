package com.music.musicflame.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SystemUpdate
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.res.painterResource
import com.music.musicflame.R
import com.music.musicflame.data.AppIconManager
import com.music.musicflame.data.SettingsRepository
import com.music.musicflame.ui.theme.LocalAppTextColor

@Composable
fun VerticalSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    colors: androidx.compose.material3.SliderColors = SliderDefaults.colors()
) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        colors = colors,
        modifier = modifier
            .graphicsLayer {
                rotationZ = -90f
                transformOrigin = TransformOrigin(0f, 0f)
            }
            .layout { measurable, constraints ->
                val placeable = measurable.measure(
                    Constraints(
                        minWidth = constraints.minHeight,
                        maxWidth = constraints.maxHeight,
                        minHeight = constraints.minWidth,
                        maxHeight = constraints.maxWidth
                    )
                )
                layout(placeable.height, placeable.width) {
                    placeable.place(-placeable.width, 0)
                }
            }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    onBackgroundImageChanged: () -> Unit = {},
    onRoundCornersChanged: (Boolean) -> Unit = {},
    onAlbumArtShapeChanged: (com.music.musicflame.AlbumArtShapeType) -> Unit = {},
    hasBackgroundImage: Boolean = false,
    isUserSignedIn: Boolean = false,
    userName: String? = null,
    userPhotoUrl: String? = null,
    onSignInClick: () -> Unit = { /* Lógica de inicio de sesión por defecto */ },
    onProfileClick: () -> Unit = { /* Lógica de perfil por defecto */ },
    onRefreshUserProfile: () -> Unit = { /* Lógica opcional para re-sincronizar la sesión */ },
    linkedAccountsCount: Int? = null,
    onRequestLinkedAccountsCount: () -> Unit = { /* Dispara la consulta al backend que cuenta cuentas vinculadas */ },
    isDriveLinked: Boolean = false,
    onLinkDriveClick: () -> Unit = { /* Lógica para pedir el scope de Google Drive */ },
    onCheckForUpdates: () -> Unit
) {
    val context = LocalContext.current
    val settingsRepo = remember { SettingsRepository(context) }
    val sharedPrefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }

    val showDurationFilterDialog = remember { mutableStateOf(false) }
    val showThemeDialog = remember { mutableStateOf(false) }
    val showEqualizerDialog = remember { mutableStateOf(false) }
    val showTextColorDialog = remember { mutableStateOf(false) }

    val durationMin = remember { mutableStateOf(settingsRepo.getDurationFilterMin().toString()) }
    val durationMax = remember { mutableStateOf(settingsRepo.getDurationFilterMax().let { if (it == Int.MAX_VALUE) "" else it.toString() }) }
    val filterMode = remember { mutableStateOf(settingsRepo.getDurationFilterMode()) }

    val appTheme = remember { mutableStateOf(settingsRepo.getAppTheme()) }
    val amoledMode = remember { mutableStateOf(settingsRepo.isAmoledModeEnabled()) }
    val useRoundCorners = remember { mutableStateOf(settingsRepo.getUseRoundCorners()) }

    // --- NAVEGACIÓN POR SUB-PÁGINAS: null = muestra las cards de categorías, si no, muestra solo esa sección ---
    val activeSection = remember { mutableStateOf<String?>(null) }

    // Si estás dentro de una sub-sección (Cuenta, Apariencia, etc.), el botón de
    // volver (gesto o botón físico) te regresa a la pantalla principal de
    // Configuración en vez de salir de la pantalla/app.
    BackHandler(enabled = activeSection.value != null) {
        activeSection.value = null
    }
    val albumArtShapePref = remember { mutableStateOf(settingsRepo.getAlbumArtShape()) }
    val showAlbumArtShapeDialog = remember { mutableStateOf(false) }
    val iconPickerExpanded = remember { mutableStateOf(false) }
    val selectedAppIcon = remember { mutableStateOf(settingsRepo.getSelectedAppIcon()) }
    val appIconOptions = remember {
        listOf(
            Triple("default", "Original", R.mipmap.ic_launcher),
            Triple("brilliant", "Brillante", R.mipmap.ic_launcher_brilliant),
            Triple("pixel", "Pixelart", R.mipmap.ic_launcher_pixel),
            Triple("cookies", "Cookies N Cream", R.mipmap.ic_launcher_cookies),
            Triple("gray", "Escala de grises", R.mipmap.ic_launcher_gray)
        )
    }

    val playInBackground = remember { mutableStateOf(settingsRepo.getPlayInBackground()) }
    val pauseOnDisconnect = remember { mutableStateOf(settingsRepo.getPauseOnDisconnect()) }
    val eqPresetSelected = remember { mutableStateOf(settingsRepo.getEqPresetSelected()) }
    val appTextColorPref = remember { mutableStateOf(settingsRepo.getAppTextColor()) }
    val customTextColorHex = remember { mutableStateOf(settingsRepo.getCustomTextColorHex()) }

    val backgroundImageUri = remember { mutableStateOf(settingsRepo.getBackgroundImageUri()) }
    val playerGifUri = remember { mutableStateOf(settingsRepo.getPlayerGifUri()) }

    val backgroundBrightness = remember { mutableStateOf(settingsRepo.getBackgroundBrightness()) }

    val powerManager = remember { context.getSystemService(Context.POWER_SERVICE) as PowerManager }
    var isIgnoringBattery by remember { mutableStateOf(powerManager.isIgnoringBatteryOptimizations(context.packageName)) }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                isIgnoringBattery = powerManager.isIgnoringBatteryOptimizations(context.packageName)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val isBgPresent = backgroundImageUri.value != null
    val isGifPresent = playerGifUri.value != null
    val hasAnyBackground = isBgPresent || isGifPresent

    val slidersValues = remember { List(5) { index -> mutableStateOf(settingsRepo.getEqBand(index)) } }
    val bassBoost = remember { mutableStateOf(settingsRepo.getBassBoost()) }
    val virtualizer = remember { mutableStateOf(settingsRepo.getVirtualizer()) }
    val eqVolume = remember { mutableStateOf(settingsRepo.getEqVolume()) }

    // El color de texto global (elegido por el usuario en "Color de texto") ahora
    // controla el highEmphasis/mediumEmphasis de toda esta pantalla, en lugar de
    // depender únicamente de colorScheme.onBackground.
    val highEmphasis = LocalAppTextColor.current
    val mediumEmphasis = LocalAppTextColor.current.copy(alpha = 0.7f)
    val trailingColor = MaterialTheme.colorScheme.primary

    val listItemColors = ListItemDefaults.colors(
        containerColor = Color.Transparent,
        headlineColor = highEmphasis,
        supportingColor = mediumEmphasis,
        trailingIconColor = trailingColor
    )

    val dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (hasAnyBackground) 0.5f else 1f)

    var isRefreshing by remember { mutableStateOf(false) }
    var avatarRefreshKey by remember { mutableIntStateOf(0) }
    val refreshScope = rememberCoroutineScope()
    val pullState = rememberPullToRefreshState()

    // Pide el conteo de cuentas de Google vinculadas al entrar a la pantalla, sin bloquear la UI.
    LaunchedEffect(Unit) {
        onRequestLinkedAccountsCount()
    }

    val pickImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) {}
            settingsRepo.saveBackgroundImageUri(it.toString())
            backgroundImageUri.value = it.toString()
            settingsRepo.removePlayerGifUri()
            playerGifUri.value = null
            onBackgroundImageChanged()
        }
    }

    val pickGifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) {}
            settingsRepo.savePlayerGifUri(it.toString())
            playerGifUri.value = it.toString()
            settingsRepo.removeBackgroundImage()
            backgroundImageUri.value = null
            onBackgroundImageChanged()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                if (hasAnyBackground) MaterialTheme.colorScheme.background.copy(alpha = 0.80f)
                else MaterialTheme.colorScheme.background
            )
    ) {
        CompositionLocalProvider(LocalContentColor provides highEmphasis) {
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    isRefreshing = true
                    avatarRefreshKey++ // fuerza a Coil a re-descargar el ícono en vez de usar el cache
                    onRefreshUserProfile() // aquí el caller puede re-sincronizar nombre/foto reales
                    onRequestLinkedAccountsCount() // re-consulta el conteo de cuentas vinculadas
                    refreshScope.launch {
                        delay(800)
                        isRefreshing = false
                    }
                },
                state = pullState,
                modifier = Modifier.fillMaxSize(),
                indicator = {
                    PullToRefreshDefaults.Indicator(
                        state = pullState,
                        isRefreshing = isRefreshing,
                        color = MaterialTheme.colorScheme.primary,
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
                }
            ) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {

                    // --- LISTA DE CATEGORÍAS cuando no hay sub-página activa ---
                    // (Ecualizador, IA y Actualizaciones ya no navegan: se muestran fijas más abajo)
                    if (activeSection.value == null) {
                        item {
                            listOf(
                                Triple("Cuenta", "Cuenta de Google y sesión", Icons.Filled.AccountCircle),
                                Triple("Apariencia", "Fondo, colores, carátula, ícono", Icons.Filled.Palette),
                                Triple("Canciones", "Manejo de canciones y reproducción", Icons.Filled.MusicNote),
                                Triple("Especificaciones", "Versión, comunidad", Icons.Filled.Info)
                            ).forEach { (catKey, subtitle, icon) ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 6.dp)
                                        .clickable { activeSection.value = catKey },
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(icon, contentDescription = null, tint = trailingColor)
                                        Spacer(Modifier.width(16.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(catKey, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = highEmphasis)
                                            Text(subtitle, fontSize = 12.sp, color = mediumEmphasis)
                                        }
                                        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = mediumEmphasis)
                                    }
                                }
                            }
                        }
                    }

                    // --- ENCABEZADO DE REGRESO cuando hay una sub-página activa ---
                    if (activeSection.value != null) {
                        item {
                            Column {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { activeSection.value = null }
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = highEmphasis)
                                    Spacer(Modifier.width(12.dp))
                                    Text(activeSection.value ?: "", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = highEmphasis)
                                }
                                HorizontalDivider(color = dividerColor)
                            }
                        }
                    }


                    val sectionHeader = @Composable { text: String ->
                        Text(
                            text = text,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black,
                            color = trailingColor
                        )
                    }

                    // CUENTA
                    if (activeSection.value == "Cuenta") {
                        item { sectionHeader("Cuenta") }

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
                                            contentDescription = "Foto de perfil",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(CircleShape)
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Filled.AccountCircle,
                                            contentDescription = "Sin foto de perfil",
                                            modifier = Modifier.size(64.dp),
                                            tint = if (isUserSignedIn) trailingColor
                                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                        )
                                    }
                                }

                                Spacer(Modifier.width(14.dp))

                                Column {
                                    if (isUserSignedIn && !userName.isNullOrEmpty()) {
                                        Text(userName, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = highEmphasis)
                                        Text("Toca para ver tu cuenta", fontSize = 13.sp, color = mediumEmphasis)
                                    } else {
                                        Text(
                                            "Sin usuario, por favor regístrese",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = mediumEmphasis
                                        )
                                        Text(
                                            "Inicia sesión con Google",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = trailingColor
                                        )
                                    }
                                }
                            }

                            // Contador de cuentas de Google vinculadas, se actualiza en 2do plano (ver onRequestLinkedAccountsCount)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (linkedAccountsCount != null) {
                                    Text(
                                        text = "Cuentas de Google vinculadas: $linkedAccountsCount/100",
                                        fontSize = 12.sp,
                                        color = mediumEmphasis
                                    )
                                } else {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(12.dp),
                                        strokeWidth = 1.5.dp,
                                        color = trailingColor
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text("Actualizando cuentas vinculadas...", fontSize = 12.sp, color = mediumEmphasis)
                                }
                            }

                            HorizontalDivider(color = dividerColor)
                        }

                        item {
                            ListItem(
                                headlineContent = { Text("Vincular Google Drive") },
                                supportingContent = {
                                    Text(
                                        if (isDriveLinked) "✓ Google Drive vinculado con tu cuenta"
                                        else "Vincula tu cuenta para respaldar tus playlists y ajustes"
                                    )
                                },
                                trailingContent = {
                                    Icon(
                                        imageVector = Icons.Filled.CloudDone,
                                        contentDescription = null,
                                        tint = if (isDriveLinked) trailingColor
                                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                },
                                colors = listItemColors,
                                modifier = Modifier.clickable {
                                    if (isUserSignedIn) onLinkDriveClick() else onSignInClick()
                                }
                            )
                            HorizontalDivider(color = dividerColor)
                        }

                        // APARIENCIA
                    }
                    if (activeSection.value == "Apariencia") {
                        item { sectionHeader("Apariencia") }

                        item {
                            ListItem(
                                headlineContent = { Text("Imagen de Fondo") },
                                supportingContent = { Text(if (isBgPresent) "✓ Imagen seleccionada" else "Selecciona una imagen estática") },
                                trailingContent = { Icon(Icons.Filled.Image, contentDescription = null, tint = trailingColor) },
                                colors = listItemColors,
                                modifier = Modifier.clickable { pickImageLauncher.launch("image/*") }
                            )
                            HorizontalDivider(color = dividerColor)
                        }

                        if (isBgPresent) {
                            item {
                                Button(
                                    onClick = {
                                        settingsRepo.removeBackgroundImage()
                                        backgroundImageUri.value = null
                                        onBackgroundImageChanged()
                                    },
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f),
                                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                ) { Text("Quitar Imagen de Fondo", fontWeight = FontWeight.Bold) }
                                HorizontalDivider(color = dividerColor)
                            }
                        }

                        item {
                            ListItem(
                                headlineContent = { Text("Fondo Animado (GIF)") },
                                supportingContent = { Text(if (isGifPresent) "✓ GIF activado" else "Añade un GIF animado como fondo") },
                                trailingContent = { Icon(Icons.Filled.Movie, contentDescription = null, modifier = Modifier.size(24.dp), tint = trailingColor) },
                                colors = listItemColors,
                                modifier = Modifier.clickable { pickGifLauncher.launch("image/gif") }
                            )
                            HorizontalDivider(color = dividerColor)
                        }

                        if (isGifPresent) {
                            item {
                                Button(
                                    onClick = {
                                        settingsRepo.removePlayerGifUri()
                                        playerGifUri.value = null
                                        onBackgroundImageChanged()
                                    },
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f),
                                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                ) { Text("Quitar Fondo GIF", fontWeight = FontWeight.Bold) }
                                HorizontalDivider(color = dividerColor)
                            }
                        }

                        if (hasAnyBackground) {
                            item {
                                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                                    Text("Brillo del fondo", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = trailingColor)
                                    Spacer(Modifier.height(4.dp))
                                    Slider(
                                        value = backgroundBrightness.value,
                                        onValueChange = {
                                            backgroundBrightness.value = it
                                            settingsRepo.saveBackgroundBrightness(it)
                                            onBackgroundImageChanged()
                                        },
                                        valueRange = -1f..1f,
                                        steps = 20,
                                        colors = SliderDefaults.colors(
                                            thumbColor = trailingColor,
                                            activeTrackColor = trailingColor
                                        )
                                    )
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Oscuro", fontSize = 12.sp, color = mediumEmphasis)
                                        Text("Original", fontSize = 12.sp, color = mediumEmphasis)
                                        Text("Brillante", fontSize = 12.sp, color = mediumEmphasis)
                                    }
                                }
                                HorizontalDivider(color = dividerColor)
                            }
                        }

                        item {
                            ListItem(
                                headlineContent = { Text("Apariencia de la aplicación") },
                                supportingContent = { Text("Tema actual: ${appTheme.value}") },
                                trailingContent = { TextButton(onClick = { showThemeDialog.value = true }) { Text("Cambiar", fontWeight = FontWeight.ExtraBold, color = trailingColor) } },
                                colors = listItemColors
                            )
                            HorizontalDivider(color = dividerColor)
                        }

                        item {
                            ListItem(
                                headlineContent = { Text("Color de texto") },
                                supportingContent = { Text("Color actual: ${appTextColorPref.value}") },
                                trailingContent = { TextButton(onClick = { showTextColorDialog.value = true }) { Text("Cambiar", fontWeight = FontWeight.ExtraBold, color = trailingColor) } },
                                colors = listItemColors
                            )
                            HorizontalDivider(color = dividerColor)
                        }

                        item {
                            ListItem(
                                headlineContent = { Text("Modo AMOLED (Negro Puro)") },
                                supportingContent = { Text("Apaga píxeles para ahorro extremo y contraste infinito") },
                                trailingContent = {
                                    Switch(
                                        checked = amoledMode.value,
                                        onCheckedChange = { isChecked ->
                                            amoledMode.value = isChecked
                                            settingsRepo.saveAmoledMode(isChecked)
                                        }
                                    )
                                },
                                colors = listItemColors
                            )
                            HorizontalDivider(color = dividerColor)
                        }

                        item {
                            ListItem(
                                headlineContent = { Text("Usar redondeado de cuadros") },
                                supportingContent = { Text("Aplica bordes curvos a las secciones y tarjetas") },
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
                                colors = listItemColors
                            )
                            HorizontalDivider(color = dividerColor)
                        }

                        item {
                            ListItem(
                                headlineContent = { Text("Forma de la carátula") },
                                supportingContent = {
                                    Text(
                                        "Actual: " + when (albumArtShapePref.value) {
                                            com.music.musicflame.AlbumArtShapeType.SQUARE -> "Cuadrado"
                                            com.music.musicflame.AlbumArtShapeType.DIAMOND -> "Rombo"
                                            com.music.musicflame.AlbumArtShapeType.CIRCLE -> "Círculo"
                                        }
                                    )
                                },
                                trailingContent = { TextButton(onClick = { showAlbumArtShapeDialog.value = true }) { Text("Cambiar", fontWeight = FontWeight.ExtraBold, color = trailingColor) } },
                                colors = listItemColors
                            )
                            HorizontalDivider(color = dividerColor)
                        }

                        item {
                            ListItem(
                                headlineContent = { Text("Icono de la app") },
                                supportingContent = { Text("Elige entre los iconos predeterminados") },
                                trailingContent = {
                                    Icon(
                                        imageVector = if (iconPickerExpanded.value) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                        contentDescription = null,
                                        tint = trailingColor
                                    )
                                },
                                colors = listItemColors,
                                modifier = Modifier.clickable { iconPickerExpanded.value = !iconPickerExpanded.value }
                            )
                            HorizontalDivider(color = dividerColor)
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
                                                Toast.makeText(context, "Icono cambiado a $label", Toast.LENGTH_SHORT).show()
                                            }
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(60.dp)
                                                    .clip(RoundedCornerShape(16.dp))
                                                    .background(
                                                        if (isSelected) trailingColor.copy(alpha = 0.15f)
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
                                                color = if (isSelected) trailingColor else mediumEmphasis
                                            )
                                        }
                                    }
                                }
                            }
                            HorizontalDivider(color = dividerColor)
                        }

                    }
                    if (activeSection.value == "Canciones") {
                        item { sectionHeader("Manejo de Canciones") }

                        item {
                            ListItem(
                                headlineContent = { Text("Filtrar por duración") },
                                supportingContent = { Text("Excluir o mostrar solo canciones de cierta duración") },
                                trailingContent = { TextButton(onClick = { showDurationFilterDialog.value = true }) { Text("Configurar", fontWeight = FontWeight.ExtraBold, color = trailingColor) } },
                                colors = listItemColors
                            )
                            HorizontalDivider(color = dividerColor)
                        }

                        item { sectionHeader("Reproducción") }

                        item {
                            ListItem(
                                headlineContent = { Text("Reproducir en segundo plano") },
                                supportingContent = { Text("Mantiene el reproductor activo fuera de la app") },
                                trailingContent = {
                                    Switch(
                                        checked = playInBackground.value,
                                        onCheckedChange = {
                                            playInBackground.value = it
                                            settingsRepo.savePlayInBackground(it)
                                            Toast.makeText(context, if (it) "Segundo plano activado" else "Segundo plano desactivado", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                },
                                colors = listItemColors
                            )
                            HorizontalDivider(color = dividerColor)
                        }
                        item {
                            ListItem(
                                headlineContent = { Text("Pausar al desconectar audífonos") },
                                supportingContent = { Text("Detiene la canción si te quitas los audífonos o se desconecta el Bluetooth") },
                                trailingContent = {
                                    Switch(
                                        checked = pauseOnDisconnect.value,
                                        onCheckedChange = {
                                            pauseOnDisconnect.value = it
                                            settingsRepo.savePauseOnDisconnect(it)
                                            Toast.makeText(context, if (it) "Pausa automática activada" else "Pausa automática desactivada", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                },
                                colors = listItemColors
                            )
                            HorizontalDivider(color = dividerColor)
                        }

                        item {
                            ListItem(
                                headlineContent = { Text("Optimización de batería") },
                                supportingContent = {
                                    Text(
                                        text = if (isIgnoringBattery)
                                            "Optimizado para música continua (Recomendado)"
                                        else
                                            "Restringido — Android podría pausar la música al apagar la pantalla",
                                        color = if (isIgnoringBattery) trailingColor else MaterialTheme.colorScheme.error
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
                                                    Toast.makeText(context, "No se pudo abrir la configuración de batería", Toast.LENGTH_SHORT).show()
                                                }
                                            } else {
                                                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                                context.startActivity(intent)
                                            }
                                        }
                                    )
                                },
                                colors = listItemColors
                            )
                            HorizontalDivider(color = dividerColor)
                        }

                        item { sectionHeader("Ecualizador") }

                        item {
                            ListItem(
                                headlineContent = { Text("Studio Pro EQ") },
                                supportingContent = { Text("Preset activo: ${eqPresetSelected.value}") },
                                trailingContent = {
                                    Button(
                                        onClick = { showEqualizerDialog.value = true },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        )
                                    ) { Text("Abrir Consola", fontWeight = FontWeight.ExtraBold) }
                                },
                                colors = listItemColors
                            )
                            HorizontalDivider(color = dividerColor)
                        }

                    }
                    if (activeSection.value == "Especificaciones") {
                        item { sectionHeader("Sobre") }
                        item { ListItem(headlineContent = { Text("Versión") }, supportingContent = { Text("3.0") }, colors = listItemColors); HorizontalDivider(color = dividerColor) }

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
                                        Text("Actualizaciones", fontWeight = FontWeight.Bold)
                                        Text("Buscar nueva versión", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }

                        item { sectionHeader("IA") }

                        item { ListItem(headlineContent = { Text("Proveedor") }, supportingContent = { Text("Gemini AI (vía Firebase AI Logic)") }, colors = listItemColors); HorizontalDivider(color = dividerColor) }

                        item {
                            ListItem(
                                headlineContent = { Text("Modelo de IA") },
                                supportingContent = { Text("Administrado automáticamente desde la nube") },
                                colors = listItemColors
                            )
                            HorizontalDivider(color = dividerColor)
                        }

                        item { sectionHeader("Especificaciones Técnicas") }

                        item {
                            ListItem(
                                headlineContent = { Text("Framework UI") },
                                supportingContent = { Text("Jetpack Compose") },
                                colors = listItemColors
                            ); HorizontalDivider(color = dividerColor)
                        }

                        item {
                            ListItem(
                                headlineContent = { Text("Lenguaje de Diseño") },
                                supportingContent = { Text("Material Design 3") },
                                colors = listItemColors
                            ); HorizontalDivider(color = dividerColor)
                        }

                        item {
                            ListItem(
                                headlineContent = { Text("Paleta de Colores") },
                                supportingContent = { Text("Material You (Dinámico)") },
                                colors = listItemColors
                            ); HorizontalDivider(color = dividerColor)
                        }

                        item {
                            ListItem(
                                headlineContent = { Text("Arquitectura") },
                                supportingContent = { Text("Declarativa y Modular") },
                                colors = listItemColors
                            )
                        }
                        item {
                            ListItem(
                                headlineContent = { Text("Creador de código") },
                                supportingContent = { Text("ShimuroNaga") },
                                leadingContent = {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data("https://github.com/ShimuroNaga.png")
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = "Avatar de ShimuroNaga",
                                        contentScale = ContentScale.Crop,
                                        placeholder = androidx.compose.ui.graphics.painter.ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                                        error = androidx.compose.ui.graphics.painter.ColorPainter(MaterialTheme.colorScheme.errorContainer),
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                    )
                                },
                                colors = listItemColors
                            )
                            HorizontalDivider(color = dividerColor)
                        }

                        item { sectionHeader("Tester") }

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
                                        contentDescription = "Avatar de Naofresita18",
                                        contentScale = ContentScale.Crop,
                                        placeholder = androidx.compose.ui.graphics.painter.ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                                        error = androidx.compose.ui.graphics.painter.ColorPainter(MaterialTheme.colorScheme.errorContainer),
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                    )
                                },
                                colors = listItemColors
                            )
                        }

                        item { sectionHeader("Comunidad") }

                        item {
                            ListItem(
                                headlineContent = { Text("Repositorio en GitHub") },
                                supportingContent = { Text("Código fuente y changelog de MusicFlame") },
                                leadingContent = {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_github),
                                        contentDescription = "GitHub",
                                        modifier = Modifier.size(28.dp),
                                        tint = highEmphasis
                                    )
                                },
                                colors = listItemColors,
                                modifier = Modifier.clickable {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/ShimuroNaga/MusicFlame"))
                                    context.startActivity(intent)
                                }
                            )
                            HorizontalDivider(color = dividerColor)
                        }

                        item {
                            ListItem(
                                headlineContent = { Text("Únete al Discord") },
                                supportingContent = { Text("Comunidad, soporte y novedades de la app") },
                                leadingContent = {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_discord),
                                        contentDescription = "Discord",
                                        modifier = Modifier.size(28.dp),
                                        tint = Color(0xFF5865F2)
                                    )
                                },
                                colors = listItemColors,
                                modifier = Modifier.clickable {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://discord.gg/gGZ4zCZvab"))
                                    context.startActivity(intent)
                                }
                            )
                        }
                    }
                }
            }
        }

        // DIÁLOGOS ADICIONALES ORIGINALES

        if (showDurationFilterDialog.value) {
            val tempMin = remember { mutableStateOf(durationMin.value) }
            val tempMax = remember { mutableStateOf(durationMax.value) }
            val tempMode = remember { mutableStateOf(filterMode.value) }
            AlertDialog(
                onDismissRequest = { showDurationFilterDialog.value = false },
                title = { Text("Filtrar por duración", fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        OutlinedTextField(value = tempMin.value, onValueChange = { tempMin.value = it }, label = { Text("Duración mínima (segundos)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(value = tempMax.value, onValueChange = { tempMax.value = it }, label = { Text("Duración máxima (segundos)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        Text("Modo:", fontWeight = FontWeight.Bold)
                        TextButton(onClick = { tempMode.value = "exclude" }) { Text("Excluir", fontWeight = FontWeight.Bold, color = if (tempMode.value == "exclude") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) }
                        TextButton(onClick = { tempMode.value = "only" }) { Text("Solo mostrar", fontWeight = FontWeight.Bold, color = if (tempMode.value == "only") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        val minVal = tempMin.value.toIntOrNull() ?: 0
                        val maxVal = tempMax.value.toIntOrNull() ?: Int.MAX_VALUE
                        settingsRepo.saveDurationFilterMin(minVal)
                        settingsRepo.saveDurationFilterMax(maxVal)
                        settingsRepo.saveDurationFilterMode(tempMode.value)
                        durationMin.value = tempMin.value
                        durationMax.value = tempMax.value
                        filterMode.value = tempMode.value
                        showDurationFilterDialog.value = false
                    }) { Text("Guardar", fontWeight = FontWeight.Bold) }
                },
                dismissButton = { TextButton(onClick = { showDurationFilterDialog.value = false }) { Text("Cancelar", fontWeight = FontWeight.Bold) } }
            )
        }

        if (showThemeDialog.value) {
            val tempTheme = remember { mutableStateOf(appTheme.value) }
            AlertDialog(
                onDismissRequest = { showThemeDialog.value = false },
                title = { Text("Elegir Tema", fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        listOf("Siguiendo al sistema", "Fondo blanco", "Fondo oscuro").forEach { theme ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { tempTheme.value = theme }.padding(vertical = 8.dp)) {
                                RadioButton(selected = tempTheme.value == theme, onClick = { tempTheme.value = theme })
                                Spacer(Modifier.width(8.dp))
                                Text(theme, fontSize = 14.sp)
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        settingsRepo.saveAppTheme(tempTheme.value)
                        appTheme.value = tempTheme.value
                        showThemeDialog.value = false
                    }) { Text("Guardar", fontWeight = FontWeight.Bold) }
                },
                dismissButton = { TextButton(onClick = { showThemeDialog.value = false }) { Text("Cancelar", fontWeight = FontWeight.Bold) } }
            )
        }

        if (showAlbumArtShapeDialog.value) {
            val tempShape = remember { mutableStateOf(albumArtShapePref.value) }
            AlertDialog(
                onDismissRequest = { showAlbumArtShapeDialog.value = false },
                title = { Text("Forma de la carátula", fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        listOf(
                            com.music.musicflame.AlbumArtShapeType.SQUARE to "Cuadrado",
                            com.music.musicflame.AlbumArtShapeType.DIAMOND to "Rombo",
                            com.music.musicflame.AlbumArtShapeType.CIRCLE to "Círculo"
                        ).forEach { (shape, label) ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { tempShape.value = shape }.padding(vertical = 8.dp)) {
                                RadioButton(selected = tempShape.value == shape, onClick = { tempShape.value = shape })
                                Spacer(Modifier.width(8.dp))
                                Text(label, fontSize = 14.sp)
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        settingsRepo.saveAlbumArtShape(tempShape.value)
                        albumArtShapePref.value = tempShape.value
                        onAlbumArtShapeChanged(tempShape.value)
                        showAlbumArtShapeDialog.value = false
                    }) { Text("Guardar", fontWeight = FontWeight.Bold) }
                },
                dismissButton = { TextButton(onClick = { showAlbumArtShapeDialog.value = false }) { Text("Cancelar", fontWeight = FontWeight.Bold) } }
            )
        }

        if (showTextColorDialog.value) {
            val tempTextColor = remember { mutableStateOf(appTextColorPref.value) }
            val tempCustomHex = remember { mutableStateOf(customTextColorHex.value) }
            AlertDialog(
                onDismissRequest = { showTextColorDialog.value = false },
                title = { Text("Color de texto", fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        listOf("Negro", "Blanco", "Personalizado").forEach { colorOption ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { tempTextColor.value = colorOption }
                                    .padding(vertical = 8.dp)
                            ) {
                                RadioButton(selected = tempTextColor.value == colorOption, onClick = { tempTextColor.value = colorOption })
                                Spacer(Modifier.width(8.dp))
                                Text(colorOption, fontSize = 14.sp)
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
                                label = { Text("Hex (#RRGGBB) o RGBA (r,g,b,a)") },
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
                                Text("Vista previa", fontSize = 12.sp)
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
                    }) { Text("Guardar", fontWeight = FontWeight.Bold) }
                },
                dismissButton = { TextButton(onClick = { showTextColorDialog.value = false }) { Text("Cancelar", fontWeight = FontWeight.Bold) } }
            )
        }

        if (showEqualizerDialog.value) {
            val tempPreset = remember { mutableStateOf(eqPresetSelected.value) }
            val viewKHz = remember { mutableStateOf(false) }
            val tempSliders = remember { List(5) { index -> mutableStateOf(slidersValues[index].value) } }
            val tempBass = remember { mutableStateOf(bassBoost.value) }
            val tempVirtualizer = remember { mutableStateOf(virtualizer.value) }
            val tempVolume = remember { mutableStateOf(eqVolume.value) }
            val tempLoudness = remember { mutableStateOf(sharedPrefs.getFloat("loudness_enhancer", 0f)) }
            val tempReverb = remember { mutableStateOf(sharedPrefs.getInt("reverb_preset", 0)) }
            val showSaveCustomDialog = remember { mutableStateOf(false) }
            val customPresetName = remember { mutableStateOf("") }
            val customNamesString = sharedPrefs.getString("custom_preset_names", "") ?: ""
            val customPresetsList = if (customNamesString.isNotEmpty()) customNamesString.split(",") else emptyList()
            val basePresets = listOf("Flat", "Rock", "Pop", "Hip hop", "Jazz", "Classical", "Electronico", "Refuerzo de graves", "Refuerzo de agudos", "Vocales", "Customizar")
            val allPresets = basePresets + customPresetsList

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
            )

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
                            IconButton(onClick = { showEqualizerDialog.value = false }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cerrar", tint = MaterialTheme.colorScheme.onBackground) }
                            Text("Studio Pro EQ", fontSize = 20.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onBackground)

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
                                Toast.makeText(context, "Audio Pro Activado 🎶", Toast.LENGTH_SHORT).show()
                            }) { Text("Aplicar", fontWeight = FontWeight.Bold) }
                        }

                        LazyColumn(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            item { Spacer(Modifier.height(8.dp)) }
                            item {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text("Ajustes Preestablecidos", fontWeight = FontWeight.Black, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                                    IconButton(onClick = { showSaveCustomDialog.value = true }, modifier = Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(12.dp)).size(36.dp)) {
                                        Icon(Icons.Filled.Add, "Guardar Preset", tint = MaterialTheme.colorScheme.primary)
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
                                            Text(text = preset, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp), color = if (tempPreset.value == preset) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }

                            item {
                                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                            Text("Ecualizador 5 Bandas", fontWeight = FontWeight.Black, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text("Hz", fontSize = 12.sp, color = if (!viewKHz.value) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                                Switch(checked = viewKHz.value, onCheckedChange = { viewKHz.value = it }, modifier = Modifier.padding(horizontal = 4.dp))
                                                Text("kHz", fontSize = 12.sp, color = if (viewKHz.value) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                        Spacer(Modifier.height(16.dp))
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                            val freqsHz = listOf("60", "230", "910", "3600", "14000")
                                            val freqsKHz = listOf("0.06", "0.23", "0.91", "3.6", "14.0")
                                            for (i in 0 until 5) {
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Text(if (viewKHz.value) freqsKHz[i] else freqsHz[i], fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                                            Text("Graves", fontSize = 12.sp, fontWeight = FontWeight.Black)
                                            Text("${tempBass.value.toInt()}%", fontSize = 16.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black)
                                            Slider(value = tempBass.value, onValueChange = { tempBass.value = it; tempPreset.value = "Customizar" }, valueRange = 0f..100f)
                                        }
                                    }
                                    Card(modifier = Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = proCardColor)) {
                                        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("Virtual 3D", fontSize = 12.sp, fontWeight = FontWeight.Black)
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
                            title = { Text("Guardar Preset") },
                            text = { OutlinedTextField(value = customPresetName.value, onValueChange = { customPresetName.value = it }, label = { Text("Nombre del Preset") }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
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
                                        Toast.makeText(context, "Preset guardado con éxito", Toast.LENGTH_SHORT).show()
                                    }
                                }) { Text("Guardar", fontWeight = FontWeight.Bold) }
                            },
                            dismissButton = { TextButton(onClick = { showSaveCustomDialog.value = false }) { Text("Cancelar") } }
                        )
                    }
                }
            }
        }
    }
}