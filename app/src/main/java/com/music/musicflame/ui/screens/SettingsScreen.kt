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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.draw.alpha
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
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.BugReport
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Lock
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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.Checkbox
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
import com.music.musicflame.data.LicenseRepository
import com.music.musicflame.data.LicenseStatus
import com.music.musicflame.data.LicenseValidationResult
import com.music.musicflame.ui.theme.LocalAppTextColor
import com.music.musicflame.widget.MusicFlameWidgetProvider

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
    onAlbumGridColumnsChanged: (Int) -> Unit = {},
    onAlbumArtShapeChanged: (com.music.musicflame.AlbumArtShapeType) -> Unit = {},
    hasBackgroundImage: Boolean = false,
    isUserSignedIn: Boolean = false,
    userName: String? = null,
    userPhotoUrl: String? = null,
    onSignInClick: () -> Unit = { /* Lógica de inicio de sesión por defecto */ },
    onProfileClick: () -> Unit = { /* Lógica de perfil por defecto */ },
    onRefreshUserProfile: () -> Unit = { /* Lógica opcional para re-sincronizar la sesión */ },
    isDriveLinked: Boolean = false,
    onLinkDriveClick: () -> Unit = { /* Lógica para pedir el scope de Google Drive */ },
    onCheckForUpdates: () -> Unit,
    playerManager: com.music.musicflame.data.MusicPlayerManager
) {
    val context = LocalContext.current
    val settingsRepo = remember { SettingsRepository(context) }
    val sharedPrefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }

    val showDurationFilterDialog = remember { mutableStateOf(false) }
    val showAudioFormatsDialog = remember { mutableStateOf(false) }
    val showSleepTimerDialog = remember { mutableStateOf(false) }
    val showThemeDialog = remember { mutableStateOf(false) }
    val showEqualizerDialog = remember { mutableStateOf(false) }
    // --- NUEVO: "Búsqueda de anomalías" (Ajustes > Canciones) ---
    val showAnomalyScanDialog = remember { mutableStateOf(false) }
    val showEqualizerStyleDialog = remember { mutableStateOf(false) }
    val showFontDialog = remember { mutableStateOf(false) }
    val showFontSizeDialog = remember { mutableStateOf(false) }
    val showTextColorDialog = remember { mutableStateOf(false) }
    val showEqualizerColorDialog = remember { mutableStateOf(false) }
    val showLyricsColorDialog = remember { mutableStateOf(false) }
    val showNowPlayingColorDialog = remember { mutableStateOf(false) }

    val durationMin = remember { mutableStateOf(settingsRepo.getDurationFilterMin().toString()) }
    val durationMax = remember { mutableStateOf(settingsRepo.getDurationFilterMax().let { if (it == Int.MAX_VALUE) "" else it.toString() }) }
    val filterMode = remember { mutableStateOf(settingsRepo.getDurationFilterMode()) }

    val appTheme = remember { mutableStateOf(settingsRepo.getAppTheme()) }
    val amoledMode = remember { mutableStateOf(settingsRepo.isAmoledModeEnabled()) }
    val useRoundCorners = remember { mutableStateOf(settingsRepo.getUseRoundCorners()) }
    val albumGridColumns = remember { mutableStateOf(settingsRepo.getAlbumGridColumns()) }
    val equalizerBarCount = remember { mutableStateOf(settingsRepo.getEqualizerBarCount()) }
    // Estilo visual del ecualizador gráfico (catálogo de personalizaciones
    // estéticas, punto 4): barras clásicas, doble espejado, ondas de agua,
    // círculo pulsante, partículas, barras finas o VU meter retro.
    val equalizerStyle = remember { mutableStateOf(settingsRepo.getEqualizerStyle()) }
    // Tipo de letra global (catálogo, ideas de fuentes): aplica a TODA la
    // app (ver MusicFlameTheme). Roboto y otras 5 son gratis, 5 son de pago.
    val appFontPref = remember { mutableStateOf(com.music.musicflame.ui.theme.AppFont.fromId(settingsRepo.getAppFont())) }
    val appFontSizePref = remember { mutableStateOf(settingsRepo.getAppFontSizeSp()) }
    // Color propio del ecualizador gráfico (catálogo, punto 1): "Adaptativo"
    // (default, blanco/negro según fondo, sin cambios de comportamiento) o
    // "Personalizado" (equalizerCustomColorHexPref). Mismo patrón que
    // appTextColorPref/customTextColorHex de más abajo.
    val equalizerColorModePref = remember { mutableStateOf(settingsRepo.getEqualizerColorMode()) }
    val equalizerCustomColorHexPref = remember { mutableStateOf(settingsRepo.getEqualizerCustomColorHex()) }
    // Color propio del texto de la letra sincronizada (catálogo, punto 2):
    // "Blanco", "Negro" o "Personalizado" (lyricsCustomColorHexPref). Mismo
    // patrón que equalizerColorModePref/equalizerCustomColorHexPref de arriba;
    // se resuelve con resolveLyricsTextColor() en LyricsView/FullScreenPlayer.
    val lyricsColorModePref = remember { mutableStateOf(settingsRepo.getLyricsTextColorMode()) }
    val lyricsCustomColorHexPref = remember { mutableStateOf(settingsRepo.getLyricsCustomColorHex()) }
    // Color único del "Now Playing" indicator (catálogo, punto 3): "Adaptativo"
    // (default, blanco/negro según el fondo, sin cambios de comportamiento) o
    // "Personalizado" (nowPlayingCustomColorHexPref). Mismo patrón que
    // equalizerColorModePref/equalizerCustomColorHexPref de arriba; se resuelve
    // globalmente en MusicFlameTheme vía LocalNowPlayingIndicatorColor.
    val nowPlayingColorModePref = remember { mutableStateOf(settingsRepo.getNowPlayingColorMode()) }
    val nowPlayingCustomColorHexPref = remember { mutableStateOf(settingsRepo.getNowPlayingCustomColorHex()) }

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
            Triple("gray", "Escala de grises", R.mipmap.ic_launcher_gray),
            Triple("remix", "RemixFlame", R.mipmap.ic_launcher_remixflame)
        )
    }

    // Estado de la sección "Pagos (opcional)" — se declara aquí (contexto @Composable
    // de la función) en vez de dentro del bloque `if` de la LazyColumn, porque el
    // lambda de contenido de LazyColumn es un LazyListScope normal, no @Composable,
    // así que remember{} no puede invocarse ahí directamente salvo dentro de item{}.
    val licenseRepo = remember { LicenseRepository(context) }
    var licenseInput by remember { mutableStateOf("") }
    var licenseStatus by remember { mutableStateOf(licenseRepo.getStatus()) }
    var maskedKey by remember { mutableStateOf(licenseRepo.maskedKey()) }
    var productName by remember { mutableStateOf(licenseRepo.getProductName()) }
    var isValidating by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(licenseRepo.getLastError()) }
    val paymentsScope = rememberCoroutineScope()
    // Bandera temporal: la sección está lista en UI pero aún bloqueada
    // para interacción (checkout/backend todavía no confirmados).
    // Cambiar a false cuando se habilite el flujo real.
    val paymentsSectionLocked = true

    // Gatilla real de las 15 personalizaciones de pago del catálogo (estilos
    // de ecualizador, colores, widget vinilo): true solo si hay licencia
    // activa o si quien inició sesión con Google en la app es el dueño (ver
    // LicenseRepository.isOwnerAccount). Independiente de paymentsSectionLocked
    // de arriba, que solo bloquea la UI de "pegar license key" mientras no
    // exista la tienda real.
    // Reactivo (ver ProStatusHolder): se actualiza solo apenas se valida una
    // key o se inicia sesión con la cuenta dueña, sin esperar a reabrir esta
    // pantalla ni la app.
    val isProUnlocked = com.music.musicflame.data.ProStatusHolder.isProUnlocked
    androidx.compose.runtime.LaunchedEffect(Unit) {
        com.music.musicflame.data.ProStatusHolder.refresh(context)
    }
    fun showLockedFeatureToast() {
        Toast.makeText(
            context,
            "Esto es de pago ($5 MXN). Actívalo en Ajustes > Pagos (opcional).",
            Toast.LENGTH_SHORT
        ).show()
    }

    // Selección del usuario en la tabla informativa/preview de Ajustes > Pagos
    // (ver PaymentCatalog): NO desbloquea nada por sí sola — el desbloqueo real
    // es "todo o nada" con una sola licencia (isProUnlocked) — solo sirve para
    // que el usuario vea cuánto costaría lo que le interesa antes de comprar.
    val selectedCatalogItemIds = remember { mutableStateOf(setOf<String>()) }

    val playInBackground = remember { mutableStateOf(settingsRepo.getPlayInBackground()) }
    val pauseOnDisconnect = remember { mutableStateOf(settingsRepo.getPauseOnDisconnect()) }
    val eqPresetSelected = remember { mutableStateOf(settingsRepo.getEqPresetSelected()) }
    // Igual que en el wizard de bienvenida: "Negro" y "Blanco" se fusionaron en una sola
    // opción "Adaptativo", porque MusicFlameTheme siempre auto-corrige el texto contra la
    // luminancia real del fondo (nunca deja el texto invisible). Si lo guardado es un
    // preset legado ("Negro"/"Blanco" de instalaciones previas), se muestra como
    // "Adaptativo"; "Personalizado" y "Arcoíris" se respetan tal cual.
    val appTextColorPref = remember {
        mutableStateOf(
            settingsRepo.getAppTextColor().let { stored ->
                if (stored == "Personalizado" || stored == com.music.musicflame.ui.theme.COLOR_MODE_RAINBOW) stored
                else "Adaptativo"
            }
        )
    }
    val customTextColorHex = remember { mutableStateOf(settingsRepo.getCustomTextColorHex()) }

    val backgroundImageUri = remember { mutableStateOf(settingsRepo.getBackgroundImageUri()) }
    val playerGifUri = remember { mutableStateOf(settingsRepo.getPlayerGifUri()) }

    val backgroundBrightness = remember { mutableStateOf(settingsRepo.getBackgroundBrightness()) }
    val widgetBackgroundOpacity = remember { mutableStateOf(settingsRepo.getWidgetBackgroundOpacity()) }

    val powerManager = remember { context.getSystemService(Context.POWER_SERVICE) as PowerManager }
    var isIgnoringBattery by remember { mutableStateOf(powerManager.isIgnoringBatteryOptimizations(context.packageName)) }

    // --- Guardar etiquetas/carátula reales en el archivo (RealTagWriter) ---
    var hasFileAccessPermission by remember { mutableStateOf(com.music.musicflame.data.RealTagWriter.hasFileAccessPermission()) }
    var realTagWritingEnabled by remember { mutableStateOf(settingsRepo.isRealTagWritingEnabled()) }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                isIgnoringBattery = powerManager.isIgnoringBatteryOptimizations(context.packageName)

                val nowHasPermission = com.music.musicflame.data.RealTagWriter.hasFileAccessPermission()
                if (!nowHasPermission && realTagWritingEnabled) {
                    // Permiso revocado por fuera (Ajustes del sistema): apagamos el
                    // switch para no dejarlo "activado" sin poder escribir de verdad.
                    realTagWritingEnabled = false
                    settingsRepo.saveRealTagWritingEnabled(false)
                }
                hasFileAccessPermission = nowHasPermission
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
                                Triple("Especificaciones", "Versión, comunidad", Icons.Filled.Info),
                                Triple("Lyrics", "Velocidad, animación y color de la letra", Icons.Filled.MusicNote),
                                Triple("Pagos (opcional)", "Licencia de apoyo y donación opcional", Icons.Filled.Favorite),
                                Triple("Aviso de Uso", "Redistribución, promoción y términos", Icons.Filled.Warning)
                            ).forEach { (catKey, subtitle, icon) ->
                                val isLyricsCard = catKey == "Lyrics"
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 6.dp)
                                        .clickable { activeSection.value = catKey },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isLyricsCard)
                                            MaterialTheme.colorScheme.tertiaryContainer
                                        else MaterialTheme.colorScheme.surfaceContainerHigh
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            icon,
                                            contentDescription = null,
                                            tint = if (isLyricsCard) MaterialTheme.colorScheme.onTertiaryContainer else trailingColor
                                        )
                                        Spacer(Modifier.width(16.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                catKey, fontWeight = FontWeight.Bold, fontSize = 16.sp,
                                                color = if (isLyricsCard) MaterialTheme.colorScheme.onTertiaryContainer else highEmphasis
                                            )
                                            Text(
                                                subtitle, fontSize = 12.sp,
                                                color = if (isLyricsCard) MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.75f) else mediumEmphasis
                                            )
                                        }
                                        Icon(
                                            Icons.Filled.ChevronRight, contentDescription = null,
                                            tint = if (isLyricsCard) MaterialTheme.colorScheme.onTertiaryContainer else mediumEmphasis
                                        )
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
                            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                                Text("Tamaño de carátulas de álbumes", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = trailingColor)
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
                                        thumbColor = trailingColor,
                                        activeTrackColor = trailingColor
                                    )
                                )
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Grande", fontSize = 12.sp, color = mediumEmphasis)
                                    Text("Chico", fontSize = 12.sp, color = mediumEmphasis)
                                }
                            }
                            HorizontalDivider(color = dividerColor)
                        }

                        item {
                            // NUEVO: cantidad de barras del ecualizador gráfico animado que se
                            // ve en el reproductor a pantalla completa. 6 = mínimo, 32 =
                            // estándar (default), 64 = máximo.
                            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                                Text("Barras del ecualizador gráfico", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = trailingColor)
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
                                        thumbColor = trailingColor,
                                        activeTrackColor = trailingColor
                                    )
                                )
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("6 (mínimo)", fontSize = 12.sp, color = mediumEmphasis)
                                    Text("${equalizerBarCount.value} barras", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = trailingColor)
                                    Text("64 (máximo)", fontSize = 12.sp, color = mediumEmphasis)
                                }
                                Text(
                                    "Se aplica la próxima vez que abras el reproductor a pantalla completa.",
                                    fontSize = 11.sp,
                                    color = mediumEmphasis,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                            HorizontalDivider(color = dividerColor)
                        }

                        item {
                            // NUEVO: selector de estilo visual del ecualizador gráfico
                            // (barras clásicas, doble espejado, ondas de agua, círculo
                            // pulsante, partículas, barras finas o VU meter retro).
                            // Parte del catálogo de personalizaciones estéticas — por
                            // ahora libre para probar (ver
                            // SettingsRepository.EQUALIZER_STYLES_UNLOCKED_FOR_TESTING).
                            ListItem(
                                headlineContent = { Text("Estilo de ecualizador gráfico") },
                                supportingContent = { Text(equalizerStyle.value.displayName) },
                                trailingContent = {
                                    TextButton(onClick = { showEqualizerStyleDialog.value = true }) {
                                        Text("Cambiar", fontWeight = FontWeight.ExtraBold, color = trailingColor)
                                    }
                                },
                                colors = listItemColors,
                                modifier = Modifier.clickable { showEqualizerStyleDialog.value = true }
                            )
                            HorizontalDivider(color = dividerColor)
                        }

                        item {
                            // NUEVO: tipo de letra global (catálogo, ideas de fuentes).
                            // Se aplica a TODA la app, no solo al título de la canción.
                            ListItem(
                                headlineContent = { Text("Tipo de letra") },
                                supportingContent = {
                                    Text(appFontPref.value.displayName, fontFamily = appFontPref.value.fontFamily)
                                },
                                trailingContent = {
                                    TextButton(onClick = { showFontDialog.value = true }) {
                                        Text("Cambiar", fontWeight = FontWeight.ExtraBold, color = trailingColor)
                                    }
                                },
                                colors = listItemColors,
                                modifier = Modifier.clickable { showFontDialog.value = true }
                            )
                            HorizontalDivider(color = dividerColor)
                        }

                        item {
                            // NUEVO: tamaño de letra global. Gratis (usabilidad, no
                            // cosmético puro) — afecta a toda la app vía appTypographyFor().
                            ListItem(
                                headlineContent = { Text("Tamaño de letra") },
                                supportingContent = { Text("${appFontSizePref.value.toInt()} sp") },
                                trailingContent = {
                                    TextButton(onClick = { showFontSizeDialog.value = true }) {
                                        Text("Cambiar", fontWeight = FontWeight.ExtraBold, color = trailingColor)
                                    }
                                },
                                colors = listItemColors,
                                modifier = Modifier.clickable { showFontSizeDialog.value = true }
                            )
                            HorizontalDivider(color = dividerColor)
                        }

                        item {
                            // NUEVO: color propio del ecualizador gráfico (catálogo,
                            // punto 1). Ortogonal al estilo de arriba — aplica sea
                            // cual sea el estilo elegido. Mismo patrón de diálogo
                            // que "Color de texto" (presets + hex/RGBA manual).
                            ListItem(
                                headlineContent = { Text("Color del ecualizador") },
                                supportingContent = {
                                    Text(
                                        when (equalizerColorModePref.value) {
                                            "Personalizado" -> "Personalizado: ${equalizerCustomColorHexPref.value}"
                                            com.music.musicflame.ui.theme.COLOR_MODE_RAINBOW -> "Arcoíris (en movimiento)"
                                            else -> "Adaptativo (según el fondo)"
                                        }
                                    )
                                },
                                trailingContent = {
                                    TextButton(onClick = { showEqualizerColorDialog.value = true }) {
                                        Text("Cambiar", fontWeight = FontWeight.ExtraBold, color = trailingColor)
                                    }
                                },
                                colors = listItemColors,
                                modifier = Modifier.clickable { showEqualizerColorDialog.value = true }
                            )
                            HorizontalDivider(color = dividerColor)
                        }

                        item {
                            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                                Text("Opacidad del fondo del widget", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = trailingColor)
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
                                        thumbColor = trailingColor,
                                        activeTrackColor = trailingColor
                                    )
                                )
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Transparente", fontSize = 12.sp, color = mediumEmphasis)
                                    Text("Opaco", fontSize = 12.sp, color = mediumEmphasis)
                                }
                                Text(
                                    "El texto del widget siempre lleva una sombra para seguir viéndose claro aunque el fondo quede casi transparente.",
                                    fontSize = 11.sp,
                                    color = mediumEmphasis,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                            HorizontalDivider(color = dividerColor)
                        }

                        item {
                            ListItem(
                                headlineContent = { Text("Color de texto") },
                                supportingContent = {
                                    Text(
                                        "Color actual: " + if (appTextColorPref.value == com.music.musicflame.ui.theme.COLOR_MODE_RAINBOW) "Arcoíris (en movimiento)" else appTextColorPref.value
                                    )
                                },
                                trailingContent = { TextButton(onClick = { showTextColorDialog.value = true }) { Text("Cambiar", fontWeight = FontWeight.ExtraBold, color = trailingColor) } },
                                colors = listItemColors
                            )
                            HorizontalDivider(color = dividerColor)
                        }

                        item {
                            // NUEVO: color único del "Now Playing" indicator (catálogo,
                            // punto 3). Mismo patrón de diálogo que "Color del
                            // ecualizador": Adaptativo (blanco/negro según el fondo,
                            // como hasta ahora) o Personalizado (presets + hex/RGBA).
                            ListItem(
                                headlineContent = { Text("Color del \"Now Playing\"") },
                                supportingContent = {
                                    Text(
                                        when (nowPlayingColorModePref.value) {
                                            "Personalizado" -> "Personalizado: ${nowPlayingCustomColorHexPref.value}"
                                            com.music.musicflame.ui.theme.COLOR_MODE_RAINBOW -> "Arcoíris (en movimiento)"
                                            else -> "Adaptativo (según el fondo)"
                                        }
                                    )
                                },
                                trailingContent = {
                                    TextButton(onClick = { showNowPlayingColorDialog.value = true }) {
                                        Text("Cambiar", fontWeight = FontWeight.ExtraBold, color = trailingColor)
                                    }
                                },
                                colors = listItemColors,
                                modifier = Modifier.clickable { showNowPlayingColorDialog.value = true }
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
                                            com.music.musicflame.AlbumArtShapeType.CIRCLE -> "Círculo"
                                            com.music.musicflame.AlbumArtShapeType.HEXAGON -> "Hexágono"
                                            com.music.musicflame.AlbumArtShapeType.VINYL -> "Vinilo"
                                            com.music.musicflame.AlbumArtShapeType.SQUIRCLE -> "Squircle"
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

                        item {
                            ListItem(
                                headlineContent = { Text("Formatos de audio a escuchar") },
                                supportingContent = { Text("Elige qué formatos de tu biblioteca mostrar y reproducir") },
                                trailingContent = { TextButton(onClick = { showAudioFormatsDialog.value = true }) { Text("Ver formatos", fontWeight = FontWeight.ExtraBold, color = trailingColor) } },
                                colors = listItemColors
                            )
                            HorizontalDivider(color = dividerColor)
                        }

                        item {
                            ListItem(
                                headlineContent = { Text("Búsqueda de anomalías") },
                                supportingContent = { Text("Detecta carátulas corruptas, metadata sospechosa, formatos sin soporte, canciones truncadas y posibles duplicados") },
                                trailingContent = {
                                    TextButton(onClick = { showAnomalyScanDialog.value = true }) {
                                        Text("Analizar", fontWeight = FontWeight.ExtraBold, color = trailingColor)
                                    }
                                },
                                colors = listItemColors
                            )
                            HorizontalDivider(color = dividerColor)
                        }

                        item {
                            ListItem(
                                headlineContent = { Text("Guardar etiquetas reales en el archivo") },
                                supportingContent = {
                                    Text(
                                        text = when {
                                            !hasFileAccessPermission -> "Requiere el permiso \"Acceso a todos los archivos\" de Android"
                                            realTagWritingEnabled -> "Activado: carátula, título, artista y álbum se escriben de verdad en el archivo"
                                            else -> "Desactivado: los cambios solo se ven dentro de la app, como ahora"
                                        },
                                        color = if (!hasFileAccessPermission) MaterialTheme.colorScheme.error else trailingColor
                                    )
                                },
                                trailingContent = {
                                    Switch(
                                        checked = realTagWritingEnabled,
                                        onCheckedChange = { checked ->
                                            if (checked) {
                                                if (hasFileAccessPermission) {
                                                    realTagWritingEnabled = true
                                                    settingsRepo.saveRealTagWritingEnabled(true)
                                                } else {
                                                    com.music.musicflame.data.RealTagWriter.requestFileAccessPermission(context)
                                                    Toast.makeText(
                                                        context,
                                                        "Concede \"Acceso a todos los archivos\" y vuelve a activar el interruptor",
                                                        Toast.LENGTH_LONG
                                                    ).show()
                                                }
                                            } else {
                                                realTagWritingEnabled = false
                                                settingsRepo.saveRealTagWritingEnabled(false)
                                            }
                                        }
                                    )
                                },
                                colors = listItemColors
                            )
                            HorizontalDivider(color = dividerColor)
                        }

                        item {
                            val sleepActive by playerManager.sleepTimerActive
                            val sleepEndOfSong by playerManager.sleepTimerEndOfSongActive
                            val sleepRemainingMs by playerManager.sleepTimerRemainingMs

                            ListItem(
                                headlineContent = { Text("Temporizador de apagado") },
                                supportingContent = {
                                    Text(
                                        when {
                                            sleepEndOfSong -> "Se pausará al terminar la canción actual"
                                            sleepActive -> {
                                                val totalSeconds = (sleepRemainingMs / 1000L).coerceAtLeast(0L)
                                                val mm = totalSeconds / 60
                                                val ss = totalSeconds % 60
                                                "Pausando en %02d:%02d".format(mm, ss)
                                            }
                                            else -> "Pausa la reproducción automáticamente"
                                        }
                                    )
                                },
                                trailingContent = {
                                    if (sleepActive) {
                                        TextButton(onClick = { playerManager.cancelSleepTimer() }) {
                                            Text("Cancelar", fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.error)
                                        }
                                    } else {
                                        TextButton(onClick = { showSleepTimerDialog.value = true }) {
                                            Text("Configurar", fontWeight = FontWeight.ExtraBold, color = trailingColor)
                                        }
                                    }
                                },
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
                        item { ListItem(headlineContent = { Text("Versión") }, supportingContent = { Text("3.12") }, colors = listItemColors); HorizontalDivider(color = dividerColor) }

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
                                        contentDescription = "Avatar de deivid-boop",
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

                    // LYRICS
                    if (activeSection.value == "Lyrics") {
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
                                        "Controla cómo se anima y se ve la letra sincronizada al deslizar dentro de una lista de reproducción.",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                                    )

                                    Spacer(Modifier.height(20.dp))
                                    Text(
                                        "Velocidad de animación: ${"%.1f".format(speed)}x",
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
                                        Text("Lenta", fontSize = 11.sp, color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f))
                                        Text("Rápida", fontSize = 11.sp, color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f))
                                    }

                                    Spacer(Modifier.height(20.dp))
                                    Text(
                                        "Tipo de animación",
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
                                                label = { Text(opt, fontSize = 12.sp) },
                                                colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                                    selectedLabelColor = MaterialTheme.colorScheme.tertiaryContainer
                                                )
                                            )
                                        }
                                    }

                                    Spacer(Modifier.height(20.dp))
                                    Text(
                                        "Color del texto",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        listOf("Blanco", "Negro", "Personalizado", com.music.musicflame.ui.theme.COLOR_MODE_RAINBOW).forEach { opt ->
                                            val selected = lyricsColorModePref.value == opt
                                            // Personalizado y Arcoíris son de pago acá; Blanco y Negro gratis.
                                            val locked = !isProUnlocked && (opt == "Personalizado" || opt == com.music.musicflame.ui.theme.COLOR_MODE_RAINBOW)
                                            androidx.compose.material3.FilterChip(
                                                selected = selected,
                                                enabled = !locked,
                                                onClick = {
                                                    if (locked) {
                                                        showLockedFeatureToast()
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
                                                    { Icon(Icons.Filled.Lock, contentDescription = "Bloqueado", modifier = Modifier.size(14.dp)) }
                                                } else null,
                                                label = { Text(if (opt == com.music.musicflame.ui.theme.COLOR_MODE_RAINBOW) "Arcoíris" else opt, fontSize = 12.sp) },
                                                colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                                    selectedLabelColor = MaterialTheme.colorScheme.tertiaryContainer
                                                )
                                            )
                                        }
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
                                                "${lyricsCustomColorHexPref.value} · Cambiar",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        "Este color también se aplica a la letra que se muestra en el widget de home screen.",
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
                                                "Letra en vivo en el widget",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = MaterialTheme.colorScheme.onTertiaryContainer
                                            )
                                            Text(
                                                "Muestra la línea activa de la letra sincronizada en el widget de home screen, en vez del nombre del artista.",
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
                                                "Widget cuadrado de letra completa",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = MaterialTheme.colorScheme.onTertiaryContainer
                                            )
                                            Text(
                                                "Cambia el widget del home screen a una variante con letra completa (mínimo 3 líneas) y controles en grid. Se aplica al instante, sin tener que agrandar el widget a mano.",
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
                                        "Para ver la letra: abre el reproductor a pantalla completa y desliza la pantalla (no la carátula) hacia la derecha.",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        "Nota: la letra encontrada mediante \"Verificar en YouTube\" no trae marcas de tiempo, así que se muestra estática, sin la animación de línea activa. Si la insertas tú manualmente en formato LRC, sí tendrá animación.",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "Obtener letra sincronizada (LRC) manualmente: lrclib.net",
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

                    // PAGOS (OPCIONAL) — Licencia de apoyo vía Lemon Squeezy
                    if (activeSection.value == "Pagos (opcional)") {
                        item { sectionHeader("Licencia de apoyo (opcional)") }

                        // --- TABLA SELECCIONABLE DEL CATÁLOGO (informativa/preview) ---
                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                                    Text(
                                        "Personalizaciones disponibles",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                    Text(
                                        "Marca lo que te interese para ver cuánto costaría. La compra real desbloquea TODO de una sola vez (no se puede comprar solo una parte).",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                                    )

                                    com.music.musicflame.data.PaymentCatalog.ITEMS
                                        .groupBy { it.section }
                                        .forEach { (section, itemsInSection) ->
                                            Text(
                                                section,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                                            )
                                            itemsInSection.forEach { catalogItem ->
                                                val checked = selectedCatalogItemIds.value.contains(catalogItem.id)
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable {
                                                            selectedCatalogItemIds.value =
                                                                if (checked) selectedCatalogItemIds.value - catalogItem.id
                                                                else selectedCatalogItemIds.value + catalogItem.id
                                                        }
                                                        .padding(vertical = 4.dp)
                                                ) {
                                                    Checkbox(
                                                        checked = checked,
                                                        onCheckedChange = { isChecked ->
                                                            selectedCatalogItemIds.value =
                                                                if (isChecked) selectedCatalogItemIds.value + catalogItem.id
                                                                else selectedCatalogItemIds.value - catalogItem.id
                                                        }
                                                    )
                                                    Spacer(Modifier.width(4.dp))
                                                    Text(catalogItem.label, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                                    Text(
                                                        "$${com.music.musicflame.data.PaymentCatalog.PRICE_PER_ITEM_MXN} MXN",
                                                        fontSize = 12.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }

                                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                                    val selectedCount = selectedCatalogItemIds.value.size
                                    val selectedTotal = selectedCount * com.music.musicflame.data.PaymentCatalog.PRICE_PER_ITEM_MXN
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "Total seleccionado ($selectedCount de ${com.music.musicflame.data.PaymentCatalog.ITEMS.size})",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            "$$selectedTotal MXN",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    Spacer(Modifier.height(12.dp))

                                    Button(
                                        onClick = {
                                            if (selectedCount == 0) {
                                                Toast.makeText(context, "Marca al menos un ítem primero.", Toast.LENGTH_SHORT).show()
                                            } else {
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(LicenseRepository.CHECKOUT_URL))
                                                context.startActivity(intent)
                                            }
                                        },
                                        enabled = !paymentsSectionLocked,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            if (selectedCount == 0) "Selecciona algo para desbloquear"
                                            else "Desbloquear por $$selectedTotal MXN",
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Text(
                                        "Recuerda: aunque marques menos de $${com.music.musicflame.data.PaymentCatalog.TOTAL_PRICE_MXN} MXN, la compra abre TODAS las personalizaciones (Lemon Squeezy no vende partes sueltas de un mismo producto).",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 8.dp)
                                    )
                                }
                            }
                        }

                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                                    .alpha(if (paymentsSectionLocked) 0.6f else 1f),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                                )
                            ) {
                                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Filled.Favorite,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onTertiaryContainer
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Text(
                                            "Una sola licencia desbloquea las ${com.music.musicflame.data.PaymentCatalog.ITEMS.size} personalizaciones de la tabla de arriba ($${com.music.musicflame.data.PaymentCatalog.TOTAL_PRICE_MXN} MXN en total). MusicFlame en sí es y seguirá siendo gratis.",
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer
                                        )
                                    }

                                    if (paymentsSectionLocked) {
                                        Spacer(Modifier.height(12.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Filled.Lock,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f),
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                "Disponible próximamente. Esta sección todavía no está activa.",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                                            )
                                        }
                                    }

                                    Spacer(Modifier.height(16.dp))

                                    // --- INDICADOR DE ESTADO ---
                                    when (licenseStatus) {
                                        LicenseStatus.ACTIVE -> {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    Icons.Filled.CloudDone,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                                                )
                                                Spacer(Modifier.width(8.dp))
                                                Column {
                                                    Text(
                                                        "Licencia activa" + (productName?.let { " · $it" } ?: ""),
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                                    )
                                                    Text(
                                                        maskedKey ?: "",
                                                        fontSize = 12.sp,
                                                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                                                    )
                                                }
                                            }
                                        }
                                        LicenseStatus.INACTIVE -> {
                                            Text(
                                                "Sin activar",
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                                            )
                                        }
                                        LicenseStatus.ERROR -> {
                                            Text(
                                                "No se pudo verificar la última vez",
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }

                                    if (errorMessage != null) {
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            errorMessage ?: "",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }

                                    Spacer(Modifier.height(16.dp))

                                    // --- CAMPO PARA PEGAR LA LICENSE KEY ---
                                    OutlinedTextField(
                                        value = licenseInput,
                                        onValueChange = { licenseInput = it },
                                        label = { Text("License key") },
                                        placeholder = { Text("Pega aquí la key que te llegó por correo") },
                                        singleLine = true,
                                        enabled = !isValidating && !paymentsSectionLocked,
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    Spacer(Modifier.height(12.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                val intent = Intent(
                                                    Intent.ACTION_VIEW,
                                                    Uri.parse(LicenseRepository.CHECKOUT_URL)
                                                )
                                                context.startActivity(intent)
                                            },
                                            enabled = !paymentsSectionLocked,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Comprar")
                                        }

                                        Button(
                                            onClick = {
                                                val keyToValidate = licenseInput
                                                isValidating = true
                                                errorMessage = null
                                                paymentsScope.launch {
                                                    when (val result = licenseRepo.validateAndSave(keyToValidate)) {
                                                        is LicenseValidationResult.Success -> {
                                                            licenseStatus = LicenseStatus.ACTIVE
                                                            maskedKey = licenseRepo.maskedKey()
                                                            productName = result.productName
                                                            licenseInput = ""
                                                            errorMessage = null
                                                            // Activa Arcoíris y el resto de cosméticos de pago
                                                            // al instante en toda la app (tema, reproductor, etc.).
                                                            com.music.musicflame.data.ProStatusHolder.refresh(context)
                                                            Toast.makeText(
                                                                context,
                                                                "¡Licencia activada! Gracias por tu apoyo.",
                                                                Toast.LENGTH_LONG
                                                            ).show()
                                                        }
                                                        is LicenseValidationResult.Invalid -> {
                                                            licenseStatus = licenseRepo.getStatus()
                                                            errorMessage = result.reason
                                                        }
                                                        LicenseValidationResult.NetworkError -> {
                                                            errorMessage = "Sin conexión. Revisa tu internet e intenta de nuevo."
                                                        }
                                                    }
                                                    isValidating = false
                                                }
                                            },
                                            enabled = !isValidating && !paymentsSectionLocked && licenseInput.isNotBlank(),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            if (isValidating) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(18.dp),
                                                    strokeWidth = 2.dp,
                                                    color = MaterialTheme.colorScheme.onPrimary
                                                )
                                            } else {
                                                Text("Activar")
                                            }
                                        }
                                    }

                                    if (licenseRepo.getSavedLicenseKey() != null) {
                                        Spacer(Modifier.height(4.dp))
                                        TextButton(
                                            onClick = {
                                                licenseRepo.clearLicense()
                                                licenseStatus = LicenseStatus.INACTIVE
                                                maskedKey = null
                                                productName = null
                                                errorMessage = null
                                                licenseInput = ""
                                                // Bloquea Arcoíris y el resto de cosméticos de pago
                                                // al instante en toda la app.
                                                com.music.musicflame.data.ProStatusHolder.refresh(context)
                                            },
                                            enabled = !paymentsSectionLocked,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("Quitar licencia", color = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // AVISO DE USO
                    if (activeSection.value == "Aviso de Uso") {
                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Warning,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Text(
                                            "Aviso de Uso y Redistribución",
                                            fontWeight = FontWeight.Black,
                                            fontSize = 16.sp,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                    Spacer(Modifier.height(12.dp))
                                    Text(
                                        "MusicFlame es un proyecto personal e independiente. Antes de redistribuir, republicar o promocionar esta aplicación en otra tienda, canal, página o red social, deben respetarse las siguientes condiciones:",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Spacer(Modifier.height(10.dp))

                                    val avisoReglas = listOf(
                                        "Contacto previo obligatorio: cualquier redistribución o promoción (subida a otras tiendas, sitios web, canales o redes sociales) requiere autorización previa por parte del desarrollador.",
                                        "Reparto de ingresos: si dicha redistribución genera ingresos de cualquier tipo (anuncios, donaciones, suscripciones, etc.), el 50% de los mismos deberá entregarse al desarrollador original, previo acuerdo.",
                                        "Créditos intactos: no está permitido eliminar, ocultar ni modificar el nombre del desarrollador, la identidad de la app ni su firma digital.",
                                        "Sin venta no autorizada: MusicFlame no puede venderse ni ofrecerse como producto de pago sin consentimiento explícito y por escrito.",
                                        "Incumplimiento: toda redistribución no autorizada será reportada y removida de la plataforma correspondiente, y el responsable quedará vetado de futuras versiones, actualizaciones y soporte."
                                    )

                                    avisoReglas.forEach { regla ->
                                        Row(modifier = Modifier.padding(vertical = 4.dp)) {
                                            Text("•  ", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onErrorContainer)
                                            Text(regla, fontSize = 13.sp, color = MaterialTheme.colorScheme.onErrorContainer)
                                        }
                                    }

                                    Spacer(Modifier.height(10.dp))
                                    Text(
                                        "Para solicitar autorización o coordinar una colaboración, contacta al desarrollador a través del repositorio de GitHub o el servidor de Discord de la comunidad (sección \"Especificaciones\" > \"Comunidad\").",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
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
                        // El filtro cambia qué canciones cuentan como parte de la
                        // librería, así que el cache compartido necesita re-escanear.
                        refreshScope.launch { com.music.musicflame.data.SongLibraryHolder.refresh(context) }
                        durationMin.value = tempMin.value
                        durationMax.value = tempMax.value
                        filterMode.value = tempMode.value
                        showDurationFilterDialog.value = false
                    }) { Text("Guardar", fontWeight = FontWeight.Bold) }
                },
                dismissButton = { TextButton(onClick = { showDurationFilterDialog.value = false }) { Text("Cancelar", fontWeight = FontWeight.Bold) } }
            )
        }

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
                title = { Text("Formatos de audio a escuchar", fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text(
                            "Los que ya tienes en tu biblioteca dicen \"detectado\".",
                            color = trailingColor,
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
                                                    format.isPlaylistFormat -> "Solo Playlists"
                                                    format.usable -> "Usable"
                                                    else -> "No usable"
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
                                                    text = "· detectado",
                                                    color = trailingColor
                                                )
                                            }
                                        }
                                        format.note?.let { note ->
                                            Text(text = note, color = trailingColor)
                                        }
                                    }
                                }
                                HorizontalDivider(color = dividerColor)
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
                    }) { Text("Guardar", fontWeight = FontWeight.Bold) }
                },
                dismissButton = { TextButton(onClick = { showAudioFormatsDialog.value = false }) { Text("Cancelar", fontWeight = FontWeight.Bold) } }
            )
        }

        if (showSleepTimerDialog.value) {
            val customMinutes = remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showSleepTimerDialog.value = false },
                title = { Text("Temporizador de apagado", fontWeight = FontWeight.Bold) },
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
                                Text("$minutes minutos", modifier = Modifier.fillMaxWidth(), fontWeight = FontWeight.Bold)
                            }
                        }
                        TextButton(
                            onClick = {
                                playerManager.startSleepTimerEndOfSong()
                                showSleepTimerDialog.value = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Al terminar la canción actual", modifier = Modifier.fillMaxWidth(), fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = customMinutes.value,
                            onValueChange = { customMinutes.value = it.filter { c -> c.isDigit() } },
                            label = { Text("Minutos personalizados") },
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
                    }) { Text("Iniciar", fontWeight = FontWeight.Bold) }
                },
                dismissButton = { TextButton(onClick = { showSleepTimerDialog.value = false }) { Text("Cancelar", fontWeight = FontWeight.Bold) } }
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

        if (showEqualizerStyleDialog.value) {
            com.music.musicflame.ui.components.EqualizerStylePickerDialog(
                currentStyle = equalizerStyle.value,
                isUnlocked = isProUnlocked,
                onDismiss = { showEqualizerStyleDialog.value = false },
                onConfirm = { newStyle ->
                    equalizerStyle.value = newStyle
                    settingsRepo.saveEqualizerStyle(newStyle)
                    showEqualizerStyleDialog.value = false
                },
                onLockedStyleClick = { showLockedFeatureToast() }
            )
        }

        if (showFontDialog.value) {
            com.music.musicflame.ui.components.AppFontPickerDialog(
                currentFont = appFontPref.value,
                isUnlocked = isProUnlocked,
                onDismiss = { showFontDialog.value = false },
                onConfirm = { newFont ->
                    appFontPref.value = newFont
                    settingsRepo.saveAppFont(newFont.id)
                    showFontDialog.value = false
                },
                onLockedFontClick = { showLockedFeatureToast() }
            )
        }

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

        if (showAlbumArtShapeDialog.value) {
            val tempShape = remember { mutableStateOf(albumArtShapePref.value) }
            AlertDialog(
                onDismissRequest = { showAlbumArtShapeDialog.value = false },
                title = { Text("Forma de la carátula", fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        listOf(
                            com.music.musicflame.AlbumArtShapeType.SQUARE to "Cuadrado",
                            com.music.musicflame.AlbumArtShapeType.CIRCLE to "Círculo",
                            com.music.musicflame.AlbumArtShapeType.HEXAGON to "Hexágono",
                            com.music.musicflame.AlbumArtShapeType.VINYL to "Vinilo",
                            com.music.musicflame.AlbumArtShapeType.SQUIRCLE to "Squircle"
                        ).forEach { (shape, label) ->
                            val isSelected = tempShape.value == shape
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { tempShape.value = shape }
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
                                RadioButton(selected = isSelected, onClick = { tempShape.value = shape })
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
                        listOf("Adaptativo", "Personalizado", com.music.musicflame.ui.theme.COLOR_MODE_RAINBOW).forEach { colorOption ->
                            // Solo Arcoíris es de pago acá; Adaptativo y Personalizado son gratis.
                            val locked = !isProUnlocked && colorOption == com.music.musicflame.ui.theme.COLOR_MODE_RAINBOW
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .alpha(if (locked) 0.5f else 1f)
                                    .clickable {
                                        if (locked) showLockedFeatureToast() else tempTextColor.value = colorOption
                                    }
                                    .padding(vertical = 8.dp)
                            ) {
                                RadioButton(
                                    selected = tempTextColor.value == colorOption,
                                    enabled = !locked,
                                    onClick = { if (locked) showLockedFeatureToast() else tempTextColor.value = colorOption }
                                )
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(if (colorOption == com.music.musicflame.ui.theme.COLOR_MODE_RAINBOW) "Arcoíris" else colorOption, fontSize = 14.sp)
                                        if (locked) {
                                            Spacer(Modifier.width(6.dp))
                                            Icon(Icons.Filled.Lock, contentDescription = "Bloqueado", modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.error)
                                            Spacer(Modifier.width(2.dp))
                                            Text("$5 MXN", fontSize = 10.sp, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    if (colorOption == "Adaptativo") {
                                        Text(
                                            "Blanco o negro según el fondo, como hasta ahora",
                                            fontSize = 11.sp,
                                            color = mediumEmphasis
                                        )
                                    } else if (colorOption == com.music.musicflame.ui.theme.COLOR_MODE_RAINBOW) {
                                        Text(
                                            "Colores del espectro en movimiento continuo",
                                            fontSize = 11.sp,
                                            color = mediumEmphasis
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

        if (showEqualizerColorDialog.value) {
            val tempEqColorMode = remember { mutableStateOf(equalizerColorModePref.value) }
            val tempEqCustomHex = remember { mutableStateOf(equalizerCustomColorHexPref.value) }
            AlertDialog(
                onDismissRequest = { showEqualizerColorDialog.value = false },
                title = { Text("Color del ecualizador", fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text(
                            "Aplica al ecualizador gráfico animado del reproductor a pantalla completa, sea cual sea el estilo elegido arriba.",
                            fontSize = 12.sp,
                            color = mediumEmphasis,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        // Los 3 modos (Adaptativo, Personalizado, Arcoíris) son de pago
                        // para este selector en particular (a diferencia de "Color de
                        // texto"/"Now Playing", donde Adaptativo es gratis): el gratis
                        // acá es simplemente no tocar este selector.
                        listOf("Adaptativo", "Personalizado", com.music.musicflame.ui.theme.COLOR_MODE_RAINBOW).forEach { colorOption ->
                            val locked = !isProUnlocked
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .alpha(if (locked) 0.5f else 1f)
                                    .clickable {
                                        if (locked) showLockedFeatureToast() else tempEqColorMode.value = colorOption
                                    }
                                    .padding(vertical = 8.dp)
                            ) {
                                RadioButton(
                                    selected = tempEqColorMode.value == colorOption,
                                    enabled = !locked,
                                    onClick = { if (locked) showLockedFeatureToast() else tempEqColorMode.value = colorOption }
                                )
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(if (colorOption == com.music.musicflame.ui.theme.COLOR_MODE_RAINBOW) "Arcoíris" else colorOption, fontSize = 14.sp)
                                        if (locked) {
                                            Spacer(Modifier.width(6.dp))
                                            Icon(Icons.Filled.Lock, contentDescription = "Bloqueado", modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.error)
                                            Spacer(Modifier.width(2.dp))
                                            Text("$5 MXN", fontSize = 10.sp, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    if (colorOption == "Adaptativo") {
                                        Text(
                                            "Blanco o negro según el fondo, como hasta ahora",
                                            fontSize = 11.sp,
                                            color = mediumEmphasis
                                        )
                                    } else if (colorOption == com.music.musicflame.ui.theme.COLOR_MODE_RAINBOW) {
                                        Text(
                                            "Colores del espectro en movimiento continuo",
                                            fontSize = 11.sp,
                                            color = mediumEmphasis
                                        )
                                    }
                                }
                            }
                        }

                        if (tempEqColorMode.value == "Personalizado" && isProUnlocked) {
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
                                        .background(com.music.musicflame.ui.theme.parseCustomTextColor(tempEqCustomHex.value))
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
                        settingsRepo.saveEqualizerColorMode(tempEqColorMode.value)
                        equalizerColorModePref.value = tempEqColorMode.value
                        if (tempEqColorMode.value == "Personalizado") {
                            settingsRepo.saveEqualizerCustomColorHex(tempEqCustomHex.value)
                            equalizerCustomColorHexPref.value = tempEqCustomHex.value
                        }
                        showEqualizerColorDialog.value = false
                    }) { Text("Guardar", fontWeight = FontWeight.Bold) }
                },
                dismissButton = { TextButton(onClick = { showEqualizerColorDialog.value = false }) { Text("Cancelar", fontWeight = FontWeight.Bold) } }
            )
        }

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
                title = { Text("Color del texto de la letra", fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text(
                            "Se aplica al texto de la letra sincronizada. La línea activa se ve a máxima intensidad y el resto atenuada, igual que con Blanco/Negro.",
                            fontSize = 12.sp,
                            color = mediumEmphasis,
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
                                    .background(com.music.musicflame.ui.theme.parseCustomTextColor(tempLyricsCustomHex.value))
                                    .border(1.dp, Color.Gray, RoundedCornerShape(6.dp))
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Vista previa", fontSize = 12.sp)
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
                    }) { Text("Guardar", fontWeight = FontWeight.Bold) }
                },
                dismissButton = { TextButton(onClick = revertIfNotConfirmed) { Text("Cancelar", fontWeight = FontWeight.Bold) } }
            )
        }

        if (showNowPlayingColorDialog.value) {
            val tempNowPlayingColorMode = remember { mutableStateOf(nowPlayingColorModePref.value) }
            val tempNowPlayingCustomHex = remember { mutableStateOf(nowPlayingCustomColorHexPref.value) }
            AlertDialog(
                onDismissRequest = { showNowPlayingColorDialog.value = false },
                title = { Text("Color del \"Now Playing\"", fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text(
                            "Aplica al indicador animado de \"reproduciendo ahora\" en las listas de canciones, la cola y las playlists.",
                            fontSize = 12.sp,
                            color = mediumEmphasis,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        listOf("Adaptativo", "Personalizado", com.music.musicflame.ui.theme.COLOR_MODE_RAINBOW).forEach { colorOption ->
                            // Personalizado y Arcoíris son de pago acá; Adaptativo es gratis.
                            val locked = !isProUnlocked && colorOption != "Adaptativo"
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .alpha(if (locked) 0.5f else 1f)
                                    .clickable {
                                        if (locked) showLockedFeatureToast() else tempNowPlayingColorMode.value = colorOption
                                    }
                                    .padding(vertical = 8.dp)
                            ) {
                                RadioButton(
                                    selected = tempNowPlayingColorMode.value == colorOption,
                                    enabled = !locked,
                                    onClick = { if (locked) showLockedFeatureToast() else tempNowPlayingColorMode.value = colorOption }
                                )
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(if (colorOption == com.music.musicflame.ui.theme.COLOR_MODE_RAINBOW) "Arcoíris" else colorOption, fontSize = 14.sp)
                                        if (locked) {
                                            Spacer(Modifier.width(6.dp))
                                            Icon(Icons.Filled.Lock, contentDescription = "Bloqueado", modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.error)
                                            Spacer(Modifier.width(2.dp))
                                            Text("$5 MXN", fontSize = 10.sp, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    if (colorOption == "Adaptativo") {
                                        Text(
                                            "Blanco o negro según el fondo, como hasta ahora",
                                            fontSize = 11.sp,
                                            color = mediumEmphasis
                                        )
                                    } else if (colorOption == com.music.musicflame.ui.theme.COLOR_MODE_RAINBOW) {
                                        Text(
                                            "Colores del espectro en movimiento continuo",
                                            fontSize = 11.sp,
                                            color = mediumEmphasis
                                        )
                                    }
                                }
                            }
                        }

                        if (tempNowPlayingColorMode.value == "Personalizado" && isProUnlocked) {
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
                                        .background(com.music.musicflame.ui.theme.parseCustomTextColor(tempNowPlayingCustomHex.value))
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
                        settingsRepo.saveNowPlayingColorMode(tempNowPlayingColorMode.value)
                        nowPlayingColorModePref.value = tempNowPlayingColorMode.value
                        if (tempNowPlayingColorMode.value == "Personalizado") {
                            settingsRepo.saveNowPlayingCustomColorHex(tempNowPlayingCustomHex.value)
                            nowPlayingCustomColorHexPref.value = tempNowPlayingCustomHex.value
                        }
                        showNowPlayingColorDialog.value = false
                    }) { Text("Guardar", fontWeight = FontWeight.Bold) }
                },
                dismissButton = { TextButton(onClick = { showNowPlayingColorDialog.value = false }) { Text("Cancelar", fontWeight = FontWeight.Bold) } }
            )
        }

        if (showAnomalyScanDialog.value) {
            AnomalyScanDialog(
                settingsRepo = settingsRepo,
                playerManager = playerManager,
                onDismiss = { showAnomalyScanDialog.value = false }
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