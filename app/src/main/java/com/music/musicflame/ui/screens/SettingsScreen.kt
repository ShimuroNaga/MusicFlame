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
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Description
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
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryStd
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.ui.res.stringResource
import com.music.musicflame.audio.EqPresets
import com.music.musicflame.R
import com.music.musicflame.data.AppIconManager
import com.music.musicflame.data.SettingsRepository
import com.music.musicflame.data.LicenseRepository
import com.music.musicflame.data.SavingsGoalRepository
import com.music.musicflame.together.SupabaseSalas
import com.music.musicflame.data.DeviceInfoProvider
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
    onCoverDesignChanged: (com.music.musicflame.data.CoverDesign?) -> Unit = {},
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
    val showProEqualizerDialog = remember { mutableStateOf(false) }
    // --- NUEVO: "Búsqueda de anomalías" (Ajustes > Canciones) ---
    val showAnomalyScanDialog = remember { mutableStateOf(false) }
    val showEqualizerStyleDialog = remember { mutableStateOf(false) }
    val showFontDialog = remember { mutableStateOf(false) }
    val showFontSizeDialog = remember { mutableStateOf(false) }
    val showLanguageDialog = remember { mutableStateOf(false) }
    val appLanguagePref = remember { mutableStateOf(com.music.musicflame.data.LanguageManager.current(context)) }
    val showTextColorDialog = remember { mutableStateOf(false) }
    val showEqualizerColorDialog = remember { mutableStateOf(false) }
    val showMomentsColorDialog = remember { mutableStateOf(false) }
    // --- Fondo propio del reproductor expandido (independiente del fondo global) ---
    val showFullPlayerBgDialog = remember { mutableStateOf(false) }
    // --- Fondo general de la app (imagen / GIF + brillo) en un solo diálogo ---
    val showAppBgDialog = remember { mutableStateOf(false) }
    val fullPlayerBgModePref = remember { mutableStateOf(settingsRepo.getFullPlayerBgMode()) }
    val fullPlayerBgIsGifPref = remember { mutableStateOf(settingsRepo.isFullPlayerBgGif()) }
    val showTogetherColorDialog = remember { mutableStateOf(false) }
    val showTogetherTextColorDialog = remember { mutableStateOf(false) }
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
    // Color de las franjas de Momentos (barra de progreso del reproductor completo).
    val momentsColorModePref = remember { mutableStateOf(settingsRepo.getMomentsColorMode()) }
    val momentsCustomColorHexPref = remember { mutableStateOf(settingsRepo.getMomentsCustomColorHex()) }
    // Color de acento de la pantalla "En compañía".
    val togetherColorModePref = remember { mutableStateOf(settingsRepo.getTogetherColorMode()) }
    val togetherCustomColorHexPref = remember { mutableStateOf(settingsRepo.getTogetherCustomColorHex()) }
    // Color de TEXTO de "En compañía" (encabezado, botones, chat).
    val togetherTextColorModePref = remember { mutableStateOf(settingsRepo.getTogetherTextColorMode()) }
    val togetherTextCustomColorHexPref = remember { mutableStateOf(settingsRepo.getTogetherTextCustomColorHex()) }
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
    // --- Editor de diseños de carátula (Forma de la carátula > "+ Crear diseño") ---
    val coverDesigns = remember { mutableStateOf(settingsRepo.getCoverDesigns()) }
    val activeCoverDesignId = remember { mutableStateOf(settingsRepo.getActiveCoverDesignId()) }
    // Selección provisional del diálogo de forma (se aplica con "Guardar"). Viven aquí,
    // y no dentro del diálogo, para que el editor pueda dejar marcado el diseño recién guardado.
    val shapeDialogShape = remember { mutableStateOf(albumArtShapePref.value) }
    val shapeDialogDesignId = remember { mutableStateOf<String?>(null) }
    val showCoverEditor = remember { mutableStateOf(false) }
    val editingCoverDesign = remember { mutableStateOf<com.music.musicflame.data.CoverDesign?>(null) }
    val coverDesignToDelete = remember { mutableStateOf<com.music.musicflame.data.CoverDesign?>(null) }
    val iconPickerExpanded = remember { mutableStateOf(false) }
    val selectedAppIcon = remember { mutableStateOf(settingsRepo.getSelectedAppIcon()) }
    val appIconOptions = remember {
        listOf(
            Triple("default", "MusicFlame", R.mipmap.ic_launcher_musicflameupgrade),
            Triple("classic", context.getString(R.string.settings_original_version_anterior), R.mipmap.ic_launcher),
            Triple("brilliant", context.getString(R.string.settings_brillante), R.mipmap.ic_launcher_brilliant),
            Triple("pixel", "Pixelart", R.mipmap.ic_launcher_pixel),
            Triple("cookies", "Cookies N Cream", R.mipmap.ic_launcher_cookies),
            Triple("gray", context.getString(R.string.settings_escala_de_grises), R.mipmap.ic_launcher_gray),
            Triple("remix", "RemixFlame", R.mipmap.ic_launcher_remixflame),
            Triple("demonmusic", "DemonMusic", R.mipmap.ic_launcher_demonmusic),
            Triple("musicpika", "MusicPika", R.mipmap.ic_launcher_musicpika),
            Triple("shimuflame", "ShimuFlame", R.mipmap.ic_launcher_shimuflame),
            Triple("musicbear", "BearFlame", R.mipmap.ic_launcher_musicbear)
        )
    }

    // Estado de la sección "Pagos (opcional)" — se declara aquí (contexto @Composable
    // de la función) en vez de dentro del bloque `if` de la LazyColumn, porque el
    // lambda de contenido de LazyColumn es un LazyListScope normal, no @Composable,
    // así que remember{} no puede invocarse ahí directamente salvo dentro de item{}.
    //
    // CAMBIO DE ARQUITECTURA (venta por ítem separado, ya NO todo-o-nada): ahora
    // el usuario puede terminar con VARIAS license keys guardadas (una por cada
    // producto que compró en Ko-fi), por eso el estado es una lista
    // (savedLicenses) en vez de una sola key/status/productName como antes.
    val licenseRepo = remember { LicenseRepository(context) }
    val restoreEmailState = remember { mutableStateOf("") }
    val purchasedState = remember { mutableStateOf(licenseRepo.recognizedProducts()) }
    val isRestoringState = remember { mutableStateOf(false) }
    val errorMessageState = remember { mutableStateOf<String?>(null) }
    var errorMessage by errorMessageState
    val paymentsScope = rememberCoroutineScope()
    // Bandera temporal: la sección está lista en UI pero aún bloqueada
    // para interacción (checkout/backend todavía no confirmados).
    // Cambiar a false cuando se habilite el flujo real.
    val paymentsSectionLocked = false

    // Gatilla real de las personalizaciones de pago del catálogo (estilos de
    // ecualizador, colores, widget vinilo, EQ Pro): true ítem por ítem, según
    // qué compró el usuario (o si es el dueño, ver LicenseRepository.isOwnerAccount).
    // Independiente de paymentsSectionLocked de arriba, que solo bloquea la UI de
    // "restaurar compras" mientras no exista la tienda real.
    // Reactivo (ver ProStatusHolder): se actualiza solo apenas se valida una
    // key o se inicia sesión con la cuenta dueña, sin esperar a reabrir esta
    // pantalla ni la app.
    val unlockedIds = com.music.musicflame.data.ProStatusHolder.unlockedIds
    androidx.compose.runtime.LaunchedEffect(Unit) {
        com.music.musicflame.data.ProStatusHolder.refresh(context)
    }
    // Al tocar algo bloqueado: abre en Ko-fi el producto (pack) que vende ese ítem del catálogo.
    // Si ese producto todavía no tiene link, solo avisa que es de pago.
    fun showLockedFeatureToast(catalogId: String) {
        val url = LicenseRepository.checkoutUrlFor(catalogId)
        if (url == null) {
            Toast.makeText(context, context.getString(R.string.settings_locked_toast), Toast.LENGTH_SHORT).show()
            return
        }
        val product = LicenseRepository.productFor(catalogId)
        Toast.makeText(
            context,
            context.getString(
                R.string.kofi_opening_pack,
                product?.displayName(context) ?: "",
                product?.let { LicenseRepository.productPriceMxn(it) } ?: 0
            ),
            Toast.LENGTH_SHORT
        ).show()
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            Toast.makeText(context, context.getString(R.string.settings_locked_toast), Toast.LENGTH_SHORT).show()
        }
    }

    // --- "Meta de ahorro" (arriba de todo en Ajustes) ---
    // Lectura pública (todos los usuarios) vía raw.githubusercontent.com;
    // escritura real solo si licenseRepo.isOwnerAccount() == true, usando la
    // API de contenidos de GitHub con un token que el dueño pega una sola vez
    // (ver SavingsGoalRepository, guardado cifrado con EncryptedSharedPreferences).
    val savingsGoalRepo = remember { SavingsGoalRepository(context) }
    var savingsGoalActual by remember { mutableStateOf(savingsGoalRepo.getCachedActual()) }
    var savingsGoalInput by remember { mutableStateOf(savingsGoalActual.toString()) }
    var savingsGoalSaving by remember { mutableStateOf(false) }
    var savingsGoalError by remember { mutableStateOf<String?>(null) }
    var showSavingsGoalTokenDialog by remember { mutableStateOf(false) }
    var savingsGoalTokenInput by remember { mutableStateOf("") }
    val savingsGoalScope = rememberCoroutineScope()

    // --- Almacenamiento de Supabase ("En compañía"): SOLO cuenta dueña, debajo de la Meta ---
    var supaUsage by remember { mutableStateOf<SupabaseSalas.Usage?>(null) }
    var supaLoading by remember { mutableStateOf(false) }
    var supaError by remember { mutableStateOf<String?>(null) }
    val supaScope = rememberCoroutineScope()
    val refreshSupaUsage: () -> Unit = {
        if (!supaLoading) {
            supaLoading = true
            supaError = null
            supaScope.launch {
                try {
                    supaUsage = SupabaseSalas.usage()
                } catch (t: Throwable) {
                    supaError = t.message ?: t.javaClass.simpleName
                } finally {
                    supaLoading = false
                }
            }
        }
    }
    LaunchedEffect(Unit) {
        if (licenseRepo.isRealOwnerAccount()) refreshSupaUsage()
    }

    LaunchedEffect(Unit) {
        val fetched = savingsGoalRepo.fetchActual()
        if (fetched != null) {
            savingsGoalActual = fetched
            savingsGoalInput = fetched.toString()
        }
        // si fetched es null (sin internet), se queda con getCachedActual() ya cargado arriba
    }

    val playInBackground = remember { mutableStateOf(settingsRepo.getPlayInBackground()) }
    val pauseOnDisconnect = remember { mutableStateOf(settingsRepo.getPauseOnDisconnect()) }
    val volumeNormalizationEnabled = remember { mutableStateOf(settingsRepo.getVolumeNormalizationEnabled()) }
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
    val isIgnoringBatteryState = remember { mutableStateOf(powerManager.isIgnoringBatteryOptimizations(context.packageName)) }
    var isIgnoringBattery by isIgnoringBatteryState
    // --- Guardar etiquetas/carátula reales en el archivo (RealTagWriter) ---
    val hasFileAccessPermissionState = remember { mutableStateOf(com.music.musicflame.data.RealTagWriter.hasFileAccessPermission()) }
    var hasFileAccessPermission by hasFileAccessPermissionState
    val realTagWritingEnabledState = remember { mutableStateOf(settingsRepo.isRealTagWritingEnabled()) }
    var realTagWritingEnabled by realTagWritingEnabledState
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
    val settingsStyle = SettingsStyle(highEmphasis, mediumEmphasis, trailingColor, listItemColors, dividerColor)

    var isRefreshing by remember { mutableStateOf(false) }
    val avatarRefreshKeyState = remember { mutableIntStateOf(0) }
    var avatarRefreshKey by avatarRefreshKeyState
    val refreshScope = rememberCoroutineScope()
    val pullState = rememberPullToRefreshState()

    // --- EXPORTAR / IMPORTAR CONFIGURACIÓN (copia de seguridad entre dispositivos) ---
    val exportConfigLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let {
            try {
                context.contentResolver.openOutputStream(it)?.use { out ->
                    out.write(com.music.musicflame.data.ConfigExportRepository.exportToJson(context).toByteArray())
                }
                Toast.makeText(context, context.getString(R.string.settings_configuracion_exportada), Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, context.getString(R.string.settings_no_se_pudo_exportar_1_s, e.message), Toast.LENGTH_LONG).show()
            }
        }
    }

    val importConfigLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            try {
                val text = context.contentResolver.openInputStream(it)?.bufferedReader()?.use { reader -> reader.readText() } ?: ""
                val applied = com.music.musicflame.data.ConfigExportRepository.importFromJson(context, text)
                Toast.makeText(context, context.getString(R.string.settings_se_aplicaron_1_s_ajustes_reinicia, applied), Toast.LENGTH_LONG).show()
            } catch (e: IllegalArgumentException) {
                Toast.makeText(context, e.message ?: context.getString(R.string.settings_archivo_invalido), Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(context, context.getString(R.string.settings_no_se_pudo_importar_1_s, e.message), Toast.LENGTH_LONG).show()
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                // Degradado en el borde superior: evita la línea oscura bajo la barra superior.
                if (hasAnyBackground) androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background.copy(alpha = 0f),
                        MaterialTheme.colorScheme.background.copy(alpha = 0.80f)
                    ),
                    startY = 0f,
                    endY = with(androidx.compose.ui.platform.LocalDensity.current) { 40.dp.toPx() }
                ) else androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.background)
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
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(stringResource(R.string.settings_meta_de_ahorro), fontWeight = FontWeight.Black, fontSize = 16.sp, color = highEmphasis)
                                        val pct = ((savingsGoalActual.toFloat() / SavingsGoalRepository.META_MAX) * 100).toInt().coerceIn(0, 100)
                                        Text("$pct%", fontSize = 14.sp, color = mediumEmphasis)
                                    }
                                    Spacer(Modifier.height(10.dp))
                                    androidx.compose.material3.LinearProgressIndicator(
                                        progress = { (savingsGoalActual.toFloat() / SavingsGoalRepository.META_MAX).coerceIn(0f, 1f) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(3.dp)),
                                        color = MaterialTheme.colorScheme.primary,
                                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                                    )
                                    Spacer(Modifier.height(10.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            stringResource(R.string.savings_goal_progress, savingsGoalActual.toString(), SavingsGoalRepository.META_MAX.toString()),
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Black,
                                            color = highEmphasis
                                        )

                                        // Campo editable + botón Guardar: SOLO para la cuenta dueña real.
                                        if (licenseRepo.isRealOwnerAccount()) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                OutlinedTextField(
                                                    value = savingsGoalInput,
                                                    onValueChange = { savingsGoalInput = it.filter(Char::isDigit) },
                                                    singleLine = true,
                                                    modifier = Modifier.width(90.dp),
                                                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp),
                                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                                )
                                                Spacer(Modifier.width(8.dp))
                                                Button(
                                                    enabled = !savingsGoalSaving,
                                                    onClick = {
                                                        val value = savingsGoalInput.toIntOrNull()
                                                            ?.coerceIn(0, SavingsGoalRepository.META_MAX)
                                                        if (value == null) return@Button
                                                        if (!savingsGoalRepo.hasToken()) {
                                                            // Si el almacén cifrado del celular falla, se muestra el motivo
                                                            // en pantalla (antes esto cerraba la app).
                                                            val storageError = savingsGoalRepo.secureStorageError()
                                                            if (storageError != null) {
                                                                savingsGoalError = context.getString(R.string.settings_no_se_pudo_abrir_el_almacen_cifrad, storageError)
                                                            } else {
                                                                showSavingsGoalTokenDialog = true
                                                            }
                                                            return@Button
                                                        }
                                                        savingsGoalSaving = true
                                                        savingsGoalError = null
                                                        savingsGoalScope.launch {
                                                            try {
                                                                when (val result = savingsGoalRepo.pushActual(value)) {
                                                                    is SavingsGoalRepository.PushResult.Success -> {
                                                                        savingsGoalActual = value
                                                                    }
                                                                    is SavingsGoalRepository.PushResult.NoToken -> {
                                                                        showSavingsGoalTokenDialog = true
                                                                    }
                                                                    is SavingsGoalRepository.PushResult.Error -> {
                                                                        savingsGoalError = result.message
                                                                    }
                                                                }
                                                            } catch (t: Throwable) {
                                                                savingsGoalError = context.getString(R.string.settings_unexpected_error, t.javaClass.simpleName, t.message ?: "")
                                                            } finally {
                                                                savingsGoalSaving = false
                                                            }
                                                        }
                                                    }
                                                ) { Text(if (savingsGoalSaving) "..." else stringResource(R.string.action_save)) }
                                            }
                                        }
                                    }
                                    savingsGoalError?.let {
                                        Spacer(Modifier.height(6.dp))
                                        Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                                    }
                                }
                            }

                            if (showSavingsGoalTokenDialog) {
                                AlertDialog(
                                    onDismissRequest = { showSavingsGoalTokenDialog = false },
                                    title = { Text(stringResource(R.string.settings_conectar_con_github)) },
                                    text = {
                                        Column {
                                            Text(stringResource(R.string.settings_pega_tu_personal_access_token_de_g))
                                            Spacer(Modifier.height(8.dp))
                                            OutlinedTextField(
                                                value = savingsGoalTokenInput,
                                                onValueChange = { savingsGoalTokenInput = it },
                                                singleLine = true,
                                                label = { Text("Token") }
                                            )
                                        }
                                    },
                                    confirmButton = {
                                        TextButton(onClick = {
                                            if (savingsGoalTokenInput.isNotBlank()) {
                                                val saved = savingsGoalRepo.saveToken(savingsGoalTokenInput)
                                                savingsGoalTokenInput = ""
                                                showSavingsGoalTokenDialog = false
                                                if (!saved) {
                                                    savingsGoalError = context.getString(
                                                        R.string.settings_token_save_failed,
                                                        savingsGoalRepo.secureStorageError() ?: context.getString(R.string.settings_error_desconocido)
                                                    )
                                                }
                                            }
                                        }) { Text(stringResource(R.string.action_save)) }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { showSavingsGoalTokenDialog = false }) { Text(stringResource(R.string.action_cancel)) }
                                    }
                                )
                            }
                        }
                        // Almacenamiento de Supabase (solo dueño): cuánto se ha usado y cuánto queda.
                        if (licenseRepo.isRealOwnerAccount()) {
                            item {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 6.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        val usage = supaUsage
                                        val pct = ((usage?.fraction ?: 0f) * 100).toInt()
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(stringResource(R.string.settings_almacenamiento_supabase), fontWeight = FontWeight.Black, fontSize = 16.sp, color = highEmphasis)
                                            Text(if (usage == null) "—" else "$pct%", fontSize = 14.sp, color = mediumEmphasis)
                                        }
                                        Spacer(Modifier.height(10.dp))
                                        androidx.compose.material3.LinearProgressIndicator(
                                            progress = { usage?.fraction ?: 0f },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(6.dp)
                                                .clip(RoundedCornerShape(3.dp)),
                                            // Rojo cuando ya se usó el 80% o más.
                                            color = if (pct >= 80) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                                        )
                                        Spacer(Modifier.height(10.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                if (usage == null) stringResource(R.string.settings_calculando) else stringResource(R.string.settings_1_s_de_1_gb, SupabaseSalas.formatSize(usage.usedBytes)),
                                                fontSize = 18.sp,
                                                fontWeight = FontWeight.Black,
                                                color = highEmphasis
                                            )
                                            TextButton(onClick = refreshSupaUsage, enabled = !supaLoading) {
                                                Text(if (supaLoading) "..." else stringResource(R.string.settings_actualizar))
                                            }
                                        }
                                        if (usage != null) {
                                            Text(
                                                stringResource(R.string.settings_te_quedan_1_s_2_s_archivo_s_en_sal, SupabaseSalas.formatSize(usage.remainingBytes), usage.files),
                                                fontSize = 12.sp,
                                                color = mediumEmphasis
                                            )
                                        }
                                        Text(
                                            stringResource(R.string.settings_solo_cuenta_lo_que_hay_en_el_bucke),
                                            fontSize = 11.sp,
                                            color = mediumEmphasis
                                        )
                                        supaError?.let {
                                            Spacer(Modifier.height(6.dp))
                                            Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                        item {
                            listOf(
                                Triple("Hogar de Shimuro", stringResource(R.string.settings_tu_mascota_interactiva), Icons.Filled.Home),
                                Triple("Cuenta", stringResource(R.string.settings_cuenta_de_google_y_sesion), Icons.Filled.AccountCircle),
                                Triple("Apariencia", stringResource(R.string.settings_fondo_colores_caratula_icono), Icons.Filled.Palette),
                                Triple("IA", stringResource(R.string.settings_ai_section_sub), Icons.Filled.AutoAwesome),
                                Triple("Canciones", stringResource(R.string.settings_manejo_de_canciones_y_reproduccion), Icons.Filled.MusicNote),
                                Triple("Copia de seguridad", stringResource(R.string.settings_exportar_importar_tu_configuracion), Icons.Filled.Save),
                                Triple("Especificaciones", stringResource(R.string.settings_version_comunidad), Icons.Filled.Info),
                                Triple("Lyrics", stringResource(R.string.settings_velocidad_animacion_y_color_de_la), Icons.Filled.MusicNote),
                                Triple("Pagos (opcional)", stringResource(R.string.settings_licencia_de_apoyo_y_donacion_opcio), Icons.Filled.Favorite),
                                Triple("Aviso de Uso", stringResource(R.string.settings_redistribucion_promocion_y_termino), Icons.Filled.Warning),
                                Triple("Licencias de terceros", stringResource(R.string.settings_third_party_licenses_sub), Icons.Filled.Description)
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
                                                settingsSectionTitle(catKey), fontWeight = FontWeight.Bold, fontSize = 16.sp,
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
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.settings_volver), tint = highEmphasis)
                                    Spacer(Modifier.width(12.dp))
                                    Text(settingsSectionTitle(activeSection.value ?: ""), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = highEmphasis)
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

                    // COPIA DE SEGURIDAD (exportar/importar configuración)
                    if (activeSection.value == "Copia de seguridad") {
                        settingsCopySecuritySection(
                            style = settingsStyle,
                            onExport = { exportConfigLauncher.launch("musicflame_config.json") },
                            onImport = { importConfigLauncher.launch("application/json") }
                        )
                    }

                    // CUENTA
                    if (activeSection.value == "Cuenta") {
                        settingsAccountSection(
                            style = settingsStyle,
                            isUserSignedIn = isUserSignedIn,
                            userName = userName,
                            userPhotoUrl = userPhotoUrl,
                            onSignInClick = onSignInClick,
                            onProfileClick = onProfileClick,
                            isDriveLinked = isDriveLinked,
                            onLinkDriveClick = onLinkDriveClick,
                            context = context,
                            avatarRefreshKeyState = avatarRefreshKeyState
                        )
                    }
                    if (activeSection.value == "Apariencia") {
                        settingsAppearanceSection(
                            style = settingsStyle,
                            onRoundCornersChanged = onRoundCornersChanged,
                            onAlbumGridColumnsChanged = onAlbumGridColumnsChanged,
                            context = context,
                            settingsRepo = settingsRepo,
                            showThemeDialog = showThemeDialog,
                            showEqualizerStyleDialog = showEqualizerStyleDialog,
                            showFontDialog = showFontDialog,
                            showFontSizeDialog = showFontSizeDialog,
                            showLanguageDialog = showLanguageDialog,
                            appLanguagePref = appLanguagePref,
                            showTextColorDialog = showTextColorDialog,
                            showEqualizerColorDialog = showEqualizerColorDialog,
                            showMomentsColorDialog = showMomentsColorDialog,
                            showFullPlayerBgDialog = showFullPlayerBgDialog,
                            showAppBgDialog = showAppBgDialog,
                            fullPlayerBgModePref = fullPlayerBgModePref,
                            fullPlayerBgIsGifPref = fullPlayerBgIsGifPref,
                            showTogetherColorDialog = showTogetherColorDialog,
                            showTogetherTextColorDialog = showTogetherTextColorDialog,
                            showNowPlayingColorDialog = showNowPlayingColorDialog,
                            appTheme = appTheme,
                            amoledMode = amoledMode,
                            useRoundCorners = useRoundCorners,
                            albumGridColumns = albumGridColumns,
                            equalizerBarCount = equalizerBarCount,
                            equalizerStyle = equalizerStyle,
                            appFontPref = appFontPref,
                            appFontSizePref = appFontSizePref,
                            equalizerColorModePref = equalizerColorModePref,
                            equalizerCustomColorHexPref = equalizerCustomColorHexPref,
                            momentsColorModePref = momentsColorModePref,
                            momentsCustomColorHexPref = momentsCustomColorHexPref,
                            togetherColorModePref = togetherColorModePref,
                            togetherCustomColorHexPref = togetherCustomColorHexPref,
                            togetherTextColorModePref = togetherTextColorModePref,
                            togetherTextCustomColorHexPref = togetherTextCustomColorHexPref,
                            nowPlayingColorModePref = nowPlayingColorModePref,
                            nowPlayingCustomColorHexPref = nowPlayingCustomColorHexPref,
                            albumArtShapePref = albumArtShapePref,
                            showAlbumArtShapeDialog = showAlbumArtShapeDialog,
                            coverDesigns = coverDesigns,
                            activeCoverDesignId = activeCoverDesignId,
                            shapeDialogShape = shapeDialogShape,
                            shapeDialogDesignId = shapeDialogDesignId,
                            iconPickerExpanded = iconPickerExpanded,
                            selectedAppIcon = selectedAppIcon,
                            appIconOptions = appIconOptions,
                            appTextColorPref = appTextColorPref,
                            widgetBackgroundOpacity = widgetBackgroundOpacity,
                            isBgPresent = isBgPresent,
                            isGifPresent = isGifPresent
                        )
                    }
                    if (activeSection.value == "Canciones") {
                        settingsSongsSection(
                            style = settingsStyle,
                            playerManager = playerManager,
                            context = context,
                            settingsRepo = settingsRepo,
                            showDurationFilterDialog = showDurationFilterDialog,
                            showAudioFormatsDialog = showAudioFormatsDialog,
                            showSleepTimerDialog = showSleepTimerDialog,
                            showEqualizerDialog = showEqualizerDialog,
                            showProEqualizerDialog = showProEqualizerDialog,
                            showAnomalyScanDialog = showAnomalyScanDialog,
                            playInBackground = playInBackground,
                            pauseOnDisconnect = pauseOnDisconnect,
                            volumeNormalizationEnabled = volumeNormalizationEnabled,
                            eqPresetSelected = eqPresetSelected,
                            isIgnoringBatteryState = isIgnoringBatteryState,
                            hasFileAccessPermissionState = hasFileAccessPermissionState,
                            realTagWritingEnabledState = realTagWritingEnabledState
                        )
                    }
                    if (activeSection.value == "Especificaciones") {
                        settingsEspecificacionesSection(
                            style = settingsStyle,
                            onCheckForUpdates = onCheckForUpdates,
                            context = context
                        )
                    }

                    // LYRICS
                    if (activeSection.value == "Lyrics") {
                        settingsLyricsSection(
                            context = context,
                            settingsRepo = settingsRepo,
                            showLyricsColorDialog = showLyricsColorDialog,
                            lyricsColorModePref = lyricsColorModePref,
                            lyricsCustomColorHexPref = lyricsCustomColorHexPref,
                            unlockedIds = unlockedIds,
                            showLockedFeatureToast = { id -> showLockedFeatureToast(id) }
                        )
                    }

                    // PAGOS (OPCIONAL) — Licencia de apoyo vía Ko-fi
                    // CAMBIO DE ARQUITECTURA (venta por ítem separado, ya NO todo-o-nada):
                    // cada ítem (o grupo chico de 2, agrupados solo por precio mínimo de
                    // Ko-fi) es un producto propio con su propia license key. El
                    // usuario puede comprar solo lo que le interesa, o "TODO" de una vez.
                    // Puede terminar con VARIAS keys guardadas — ver savedLicenses arriba.
                    if (activeSection.value == "Pagos (opcional)") {
                        settingsPaymentsSection(
                            style = settingsStyle,
                            context = context,
                            licenseRepo = licenseRepo,
                            restoreEmailState = restoreEmailState,
                            purchasedState = purchasedState,
                            isRestoringState = isRestoringState,
                            errorMessageState = errorMessageState,
                            paymentsScope = paymentsScope,
                            paymentsSectionLocked = paymentsSectionLocked,
                            unlockedIds = unlockedIds
                        )
                    }


                    // AVISO DE USO
                    if (activeSection.value == "Aviso de Uso") {
                        settingsAvisoDeUsoSection()
                    }

                    // LICENCIAS DE TERCEROS
                    if (activeSection.value == "Licencias de terceros") {
                        settingsLicenseSection(settingsStyle)
                    }

                    // IA (chat): API key, nombre, burbujas y fondo
                    if (activeSection.value == "IA") {
                        settingsAiSection(settingsStyle)
                    }

                    // HOGAR DE SHIMURO
                    if (activeSection.value == "Hogar de Shimuro") {
                        settingsHomeShimuroSection(settingsStyle)
                    }
                }
            }
        }

        // DIÁLOGOS ADICIONALES ORIGINALES

        SettingsDurationFilterDialog(
            context = context,
            settingsRepo = settingsRepo,
            showDurationFilterDialog = showDurationFilterDialog,
            durationMin = durationMin,
            durationMax = durationMax,
            filterMode = filterMode,
            refreshScope = refreshScope
        )

        SettingsAudioFormatsDialog(
            style = settingsStyle,
            context = context,
            settingsRepo = settingsRepo,
            showAudioFormatsDialog = showAudioFormatsDialog,
            refreshScope = refreshScope
        )

        SettingsSleepTimerDialog(
            playerManager = playerManager,
            showSleepTimerDialog = showSleepTimerDialog
        )

        SettingsThemeDialog(
            settingsRepo = settingsRepo,
            showThemeDialog = showThemeDialog,
            appTheme = appTheme
        )

        SettingsEqualizerStyleDialog(
            settingsRepo = settingsRepo,
            showEqualizerStyleDialog = showEqualizerStyleDialog,
            equalizerStyle = equalizerStyle,
            unlockedIds = unlockedIds,
            showLockedFeatureToast = { id -> showLockedFeatureToast(id) }
        )

        SettingsFontDialog(
            settingsRepo = settingsRepo,
            showFontDialog = showFontDialog,
            appFontPref = appFontPref,
            unlockedIds = unlockedIds,
            showLockedFeatureToast = { id -> showLockedFeatureToast(id) }
        )

        SettingsFontSizeDialog(
            settingsRepo = settingsRepo,
            showFontSizeDialog = showFontSizeDialog,
            appFontPref = appFontPref,
            appFontSizePref = appFontSizePref
        )

        SettingsLanguageDialog(
            context = context,
            showLanguageDialog = showLanguageDialog,
            appLanguagePref = appLanguagePref
        )

        SettingsAlbumArtShapeDialog(
            onAlbumArtShapeChanged = onAlbumArtShapeChanged,
            onCoverDesignChanged = onCoverDesignChanged,
            context = context,
            settingsRepo = settingsRepo,
            albumArtShapePref = albumArtShapePref,
            showAlbumArtShapeDialog = showAlbumArtShapeDialog,
            coverDesigns = coverDesigns,
            activeCoverDesignId = activeCoverDesignId,
            shapeDialogShape = shapeDialogShape,
            shapeDialogDesignId = shapeDialogDesignId,
            showCoverEditor = showCoverEditor,
            editingCoverDesign = editingCoverDesign,
            coverDesignToDelete = coverDesignToDelete
        )

        // Editor de diseños (pantalla completa, con vista previa en vivo).
        SettingsCoverEditorDialog(
            onCoverDesignChanged = onCoverDesignChanged,
            playerManager = playerManager,
            context = context,
            settingsRepo = settingsRepo,
            albumArtShapePref = albumArtShapePref,
            coverDesigns = coverDesigns,
            activeCoverDesignId = activeCoverDesignId,
            shapeDialogShape = shapeDialogShape,
            shapeDialogDesignId = shapeDialogDesignId,
            showCoverEditor = showCoverEditor,
            editingCoverDesign = editingCoverDesign
        )

        // Confirmación antes de borrar un diseño.
        SettingsCoverDesignDeleteDialog(
            onAlbumArtShapeChanged = onAlbumArtShapeChanged,
            onCoverDesignChanged = onCoverDesignChanged,
            context = context,
            settingsRepo = settingsRepo,
            albumArtShapePref = albumArtShapePref,
            coverDesigns = coverDesigns,
            activeCoverDesignId = activeCoverDesignId,
            shapeDialogShape = shapeDialogShape,
            shapeDialogDesignId = shapeDialogDesignId,
            coverDesignToDelete = coverDesignToDelete
        )

        SettingsTextColorDialog(
            style = settingsStyle,
            settingsRepo = settingsRepo,
            showTextColorDialog = showTextColorDialog,
            unlockedIds = unlockedIds,
            showLockedFeatureToast = { id -> showLockedFeatureToast(id) },
            appTextColorPref = appTextColorPref,
            customTextColorHex = customTextColorHex
        )

        SettingsEqualizerColorDialog(
            style = settingsStyle,
            settingsRepo = settingsRepo,
            showEqualizerColorDialog = showEqualizerColorDialog,
            equalizerColorModePref = equalizerColorModePref,
            equalizerCustomColorHexPref = equalizerCustomColorHexPref,
            unlockedIds = unlockedIds,
            showLockedFeatureToast = { id -> showLockedFeatureToast(id) }
        )

        SettingsFullPlayerBgDialog(
            style = settingsStyle,
            context = context,
            settingsRepo = settingsRepo,
            showFullPlayerBgDialog = showFullPlayerBgDialog,
            fullPlayerBgModePref = fullPlayerBgModePref,
            fullPlayerBgIsGifPref = fullPlayerBgIsGifPref,
            refreshScope = refreshScope
        )

        SettingsAppBgDialog(
            style = settingsStyle,
            onBackgroundImageChanged = onBackgroundImageChanged,
            context = context,
            settingsRepo = settingsRepo,
            showAppBgDialog = showAppBgDialog,
            backgroundImageUri = backgroundImageUri,
            playerGifUri = playerGifUri,
            backgroundBrightness = backgroundBrightness
        )

        SettingsMomentsColorDialog(
            style = settingsStyle,
            settingsRepo = settingsRepo,
            showMomentsColorDialog = showMomentsColorDialog,
            momentsColorModePref = momentsColorModePref,
            momentsCustomColorHexPref = momentsCustomColorHexPref
        )

        SettingsTogetherColorDialog(
            style = settingsStyle,
            settingsRepo = settingsRepo,
            showTogetherColorDialog = showTogetherColorDialog,
            togetherColorModePref = togetherColorModePref,
            togetherCustomColorHexPref = togetherCustomColorHexPref
        )

        SettingsTogetherTextColorDialog(
            style = settingsStyle,
            settingsRepo = settingsRepo,
            showTogetherTextColorDialog = showTogetherTextColorDialog,
            togetherTextColorModePref = togetherTextColorModePref,
            togetherTextCustomColorHexPref = togetherTextCustomColorHexPref
        )

        SettingsLyricsColorDialog(
            style = settingsStyle,
            settingsRepo = settingsRepo,
            showLyricsColorDialog = showLyricsColorDialog,
            lyricsColorModePref = lyricsColorModePref,
            lyricsCustomColorHexPref = lyricsCustomColorHexPref
        )

        SettingsNowPlayingColorDialog(
            style = settingsStyle,
            settingsRepo = settingsRepo,
            showNowPlayingColorDialog = showNowPlayingColorDialog,
            nowPlayingColorModePref = nowPlayingColorModePref,
            nowPlayingCustomColorHexPref = nowPlayingCustomColorHexPref,
            unlockedIds = unlockedIds,
            showLockedFeatureToast = { id -> showLockedFeatureToast(id) }
        )

        SettingsAnomalyScanDialog(
            playerManager = playerManager,
            settingsRepo = settingsRepo,
            showAnomalyScanDialog = showAnomalyScanDialog
        )

        SettingsProEqualizerDialog(
            showProEqualizerDialog = showProEqualizerDialog
        )

        SettingsEqualizerDialog(
            context = context,
            settingsRepo = settingsRepo,
            sharedPrefs = sharedPrefs,
            showEqualizerDialog = showEqualizerDialog,
            eqPresetSelected = eqPresetSelected,
            slidersValues = slidersValues,
            bassBoost = bassBoost,
            virtualizer = virtualizer,
            eqVolume = eqVolume
        )
    }
}

@Composable
internal fun DeviceInfoCard(
    highEmphasis: Color,
    mediumEmphasis: Color,
    trailingColor: Color
) {
    val context = LocalContext.current
    val deviceInfo = remember { DeviceInfoProvider.get(context) }
    var batteryExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // Encabezado: fabricante + modelo + hardware
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.PhoneAndroid,
                    contentDescription = null,
                    tint = trailingColor,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        "${deviceInfo.manufacturer} ${deviceInfo.model}".trim(),
                        fontWeight = FontWeight.Black,
                        fontSize = 16.sp,
                        color = highEmphasis
                    )
                    if (deviceInfo.hardware.isNotBlank()) {
                        Text(
                            deviceInfo.model + " (" + deviceInfo.hardware + ")",
                            fontSize = 13.sp,
                            color = mediumEmphasis
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Versión de Android
            val androidTitle = if (deviceInfo.codename.isNotBlank())
                "Android ${deviceInfo.androidRelease} (${deviceInfo.codename})"
            else
                "Android ${deviceInfo.androidRelease}"
            Text(androidTitle, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = trailingColor)
            val patchLine = if (deviceInfo.securityPatch.isNotBlank())
                "API ${deviceInfo.apiLevel} • Patch: ${deviceInfo.securityPatch}"
            else
                "API ${deviceInfo.apiLevel}"
            Text(patchLine, fontSize = 12.sp, color = mediumEmphasis)
            if (deviceInfo.buildDisplay.isNotBlank()) {
                Text("Build: ${deviceInfo.buildDisplay}", fontSize = 11.sp, color = mediumEmphasis.copy(alpha = 0.8f))
            }

            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(Modifier.height(14.dp))

            // Almacenamiento y memoria
            Row(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Storage, null, tint = trailingColor, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(stringResource(R.string.settings_almacenamiento), fontSize = 12.sp, color = mediumEmphasis)
                        Text(
                            DeviceInfoProvider.bytesToGbLabel(deviceInfo.storageTotalBytes),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = highEmphasis
                        )
                    }
                }
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Memory, null, tint = trailingColor, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(stringResource(R.string.settings_memoria), fontSize = 12.sp, color = mediumEmphasis)
                        Text(
                            DeviceInfoProvider.bytesToGbLabel(deviceInfo.ramTotalBytes),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = highEmphasis
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(Modifier.height(4.dp))

            // Batería, expandible con el detalle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { batteryExpanded = !batteryExpanded }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (deviceInfo.battery.isCharging) Icons.Filled.BatteryChargingFull else Icons.Filled.BatteryStd,
                    contentDescription = null,
                    tint = trailingColor,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "${deviceInfo.battery.percent}%",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = highEmphasis
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.settings_bateria), fontSize = 14.sp, color = mediumEmphasis, modifier = Modifier.weight(1f))
                Icon(
                    imageVector = if (batteryExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = mediumEmphasis
                )
            }

            AnimatedVisibility(visible = batteryExpanded) {
                Column(modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)) {
                    DeviceInfoDetailRow(stringResource(R.string.settings_estado), deviceInfo.battery.statusLabel, mediumEmphasis, highEmphasis)
                    DeviceInfoDetailRow(stringResource(R.string.settings_salud), deviceInfo.battery.healthLabel, mediumEmphasis, highEmphasis)
                    deviceInfo.battery.temperatureC?.let {
                        DeviceInfoDetailRow(stringResource(R.string.settings_temperatura), "${it}°C", mediumEmphasis, highEmphasis)
                    }
                    deviceInfo.battery.voltageMv?.let {
                        DeviceInfoDetailRow(stringResource(R.string.settings_voltaje), "${it} mV", mediumEmphasis, highEmphasis)
                    }
                    deviceInfo.battery.technology?.let {
                        DeviceInfoDetailRow(stringResource(R.string.settings_tecnologia), it, mediumEmphasis, highEmphasis)
                    }
                    deviceInfo.battery.plugLabel?.let {
                        DeviceInfoDetailRow(stringResource(R.string.settings_fuente_de_carga), it, mediumEmphasis, highEmphasis)
                    }
                }
            }
        }
    }
}

@Composable
internal fun DeviceInfoDetailRow(label: String, value: String, mediumEmphasis: Color, highEmphasis: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 13.sp, color = mediumEmphasis)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = highEmphasis)
    }
}

/**
 * Los modos de color (y tipos de animación) se GUARDAN con su nombre en español
 * ("Adaptativo", "Personalizado", "Blanco"...) para no romper lo que los
 * usuarios ya tienen guardado; esta función solo traduce lo que se MUESTRA.
 */
@Composable
internal fun settingsOptionLabel(option: String): String = when (option) {
    "Adaptativo" -> stringResource(R.string.option_adaptive)
    "Siguiendo al sistema" -> stringResource(R.string.settings_siguiendo_al_sistema)
    "Fondo blanco" -> stringResource(R.string.settings_fondo_blanco)
    "Fondo oscuro" -> stringResource(R.string.settings_fondo_oscuro)
    "Personalizado" -> stringResource(R.string.option_custom)
    "Blanco" -> stringResource(R.string.option_white)
    "Negro" -> stringResource(R.string.option_black)
    "Deslizar" -> stringResource(R.string.option_anim_slide)
    "Desvanecer" -> stringResource(R.string.option_anim_fade)
    "Rebote" -> stringResource(R.string.option_anim_bounce)
    com.music.musicflame.ui.theme.COLOR_MODE_RAINBOW -> stringResource(R.string.option_rainbow)
    else -> option
}

/** Los presets se identifican por su nombre en español (se guardan así); esto traduce el nombre visible. */
@Composable
internal fun eqPresetLabel(preset: String): String = when (preset) {
    "Electronico" -> stringResource(R.string.eq_preset_electronic)
    "Refuerzo de graves" -> stringResource(R.string.eq_preset_bass_boost)
    "Refuerzo de agudos" -> stringResource(R.string.eq_preset_treble_boost)
    "Vocales" -> stringResource(R.string.eq_preset_vocals)
    "Customizar" -> stringResource(R.string.eq_preset_customize)
    else -> preset
}

/** Mismo criterio: las secciones de Ajustes se identifican por su nombre en español; esto traduce el título visible. */
@Composable
internal fun settingsSectionTitle(key: String): String = when (key) {
    "Hogar de Shimuro" -> stringResource(R.string.section_shimuro_home)
    "Cuenta" -> stringResource(R.string.section_account)
    "Apariencia" -> stringResource(R.string.section_appearance)
    "IA" -> stringResource(R.string.section_ai)
    "Canciones" -> stringResource(R.string.section_songs)
    "Copia de seguridad" -> stringResource(R.string.section_backup)
    "Especificaciones" -> stringResource(R.string.section_specs)
    "Pagos (opcional)" -> stringResource(R.string.section_payments)
    "Aviso de Uso" -> stringResource(R.string.section_usage_notice)
    "Licencias de terceros" -> stringResource(R.string.section_third_party_licenses)
    else -> key
}
