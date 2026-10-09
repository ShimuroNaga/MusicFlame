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
import com.music.musicflame.data.LicenseStatus
import com.music.musicflame.data.SavingsGoalRepository
import com.music.musicflame.together.SupabaseSalas
import com.music.musicflame.data.LicenseValidationResult
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
    // producto que compró en Lemon Squeezy), por eso el estado es una lista
    // (savedLicenses) en vez de una sola key/status/productName como antes.
    val licenseRepo = remember { LicenseRepository(context) }
    var licenseInput by remember { mutableStateOf("") }
    var savedLicenses by remember { mutableStateOf(licenseRepo.getSavedLicenses()) }
    var isValidating by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val paymentsScope = rememberCoroutineScope()
    // Bandera temporal: la sección está lista en UI pero aún bloqueada
    // para interacción (checkout/backend todavía no confirmados).
    // Cambiar a false cuando se habilite el flujo real.
    val paymentsSectionLocked = false

    // Gatilla real de las personalizaciones de pago del catálogo (estilos de
    // ecualizador, colores, widget vinilo, EQ Pro): true ítem por ítem, según
    // qué compró el usuario (o si es el dueño, ver LicenseRepository.isOwnerAccount).
    // Independiente de paymentsSectionLocked de arriba, que solo bloquea la UI de
    // "pegar license key" mientras no exista la tienda real.
    // Reactivo (ver ProStatusHolder): se actualiza solo apenas se valida una
    // key o se inicia sesión con la cuenta dueña, sin esperar a reabrir esta
    // pantalla ni la app.
    val unlockedIds = com.music.musicflame.data.ProStatusHolder.unlockedIds
    androidx.compose.runtime.LaunchedEffect(Unit) {
        com.music.musicflame.data.ProStatusHolder.refresh(context)
    }
    fun showLockedFeatureToast() {
        Toast.makeText(
            context,
            context.getString(R.string.settings_locked_toast),
            Toast.LENGTH_SHORT
        ).show()
    }
    fun showLockedLyricsColorToast() {
        Toast.makeText(
            context,
            context.getString(R.string.settings_locked_lyrics_toast),
            Toast.LENGTH_SHORT
        ).show()
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
        if (licenseRepo.isOwnerAccount()) refreshSupaUsage()
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
                                            "$${savingsGoalActual} de $${SavingsGoalRepository.META_MAX}",
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Black,
                                            color = highEmphasis
                                        )

                                        // Campo editable + botón Guardar: SOLO para la cuenta dueña real.
                                        if (licenseRepo.isOwnerAccount()) {
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
                        if (licenseRepo.isOwnerAccount()) {
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
                                Triple("Canciones", stringResource(R.string.settings_manejo_de_canciones_y_reproduccion), Icons.Filled.MusicNote),
                                Triple("Copia de seguridad", stringResource(R.string.settings_exportar_importar_tu_configuracion), Icons.Filled.Save),
                                Triple("Especificaciones", stringResource(R.string.settings_version_comunidad), Icons.Filled.Info),
                                Triple("Lyrics", stringResource(R.string.settings_velocidad_animacion_y_color_de_la), Icons.Filled.MusicNote),
                                Triple("Pagos (opcional)", stringResource(R.string.settings_licencia_de_apoyo_y_donacion_opcio), Icons.Filled.Favorite),
                                Triple("Aviso de Uso", stringResource(R.string.settings_redistribucion_promocion_y_termino), Icons.Filled.Warning)
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
                        item { sectionHeader(stringResource(R.string.section_backup)) }

                        item {
                            Text(
                                text = stringResource(R.string.settings_guarda_tus_ajustes_de_apariencia_e),
                                fontSize = 13.sp,
                                color = mediumEmphasis,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }

                        item {
                            ListItem(
                                headlineContent = { Text(stringResource(R.string.settings_exportar_configuracion)) },
                                supportingContent = { Text(stringResource(R.string.settings_guardar_como_archivo_json)) },
                                trailingContent = { Icon(Icons.Filled.Save, contentDescription = null, tint = trailingColor) },
                                colors = listItemColors,
                                modifier = Modifier.clickable { exportConfigLauncher.launch("musicflame_config.json") }
                            )
                            HorizontalDivider(color = dividerColor)
                        }

                        item {
                            ListItem(
                                headlineContent = { Text(stringResource(R.string.settings_importar_configuracion)) },
                                supportingContent = { Text(stringResource(R.string.settings_elegir_un_archivo_json_exportado_a)) },
                                trailingContent = { Icon(Icons.Filled.FileOpen, contentDescription = null, tint = trailingColor) },
                                colors = listItemColors,
                                modifier = Modifier.clickable { importConfigLauncher.launch("application/json") }
                            )
                            HorizontalDivider(color = dividerColor)
                        }
                    }

                    // CUENTA
                    if (activeSection.value == "Cuenta") {
                        item { sectionHeader(stringResource(R.string.section_account)) }

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
                                            tint = if (isUserSignedIn) trailingColor
                                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                        )
                                    }
                                }

                                Spacer(Modifier.width(14.dp))

                                Column {
                                    if (isUserSignedIn && !userName.isNullOrEmpty()) {
                                        Text(userName, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = highEmphasis)
                                        Text(stringResource(R.string.settings_toca_para_ver_tu_cuenta), fontSize = 13.sp, color = mediumEmphasis)
                                    } else {
                                        Text(
                                            stringResource(R.string.settings_sin_usuario_por_favor_registrese),
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = mediumEmphasis
                                        )
                                        Text(
                                            stringResource(R.string.settings_inicia_sesion_con_google),
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

                        item {
                            Spacer(Modifier.height(4.dp))
                            DeviceInfoCard(
                                highEmphasis = highEmphasis,
                                mediumEmphasis = mediumEmphasis,
                                trailingColor = trailingColor
                            )
                        }

                        // APARIENCIA
                    }
                    if (activeSection.value == "Apariencia") {
                        item { sectionHeader(stringResource(R.string.section_appearance)) }

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
                                        Text(stringResource(R.string.action_change), fontWeight = FontWeight.ExtraBold, color = trailingColor)
                                    }
                                },
                                colors = listItemColors,
                                modifier = Modifier.clickable { showAppBgDialog.value = true }
                            )
                            HorizontalDivider(color = dividerColor)
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
                                        Text(stringResource(R.string.action_change), fontWeight = FontWeight.ExtraBold, color = trailingColor)
                                    }
                                },
                                colors = listItemColors,
                                modifier = Modifier.clickable { showFullPlayerBgDialog.value = true }
                            )
                            HorizontalDivider(color = dividerColor)
                        }

                        item {
                            ListItem(
                                headlineContent = { Text(stringResource(R.string.settings_apariencia_de_la_aplicacion)) },
                                supportingContent = { Text(stringResource(R.string.settings_tema_actual_1_s, settingsOptionLabel(appTheme.value))) },
                                trailingContent = { TextButton(onClick = { showThemeDialog.value = true }) { Text(stringResource(R.string.action_change), fontWeight = FontWeight.ExtraBold, color = trailingColor) } },
                                colors = listItemColors
                            )
                            HorizontalDivider(color = dividerColor)
                        }

                        item {
                            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                                Text(stringResource(R.string.settings_tamano_de_caratulas_de_albumes), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = trailingColor)
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
                                    Text(stringResource(R.string.settings_grande), fontSize = 12.sp, color = mediumEmphasis)
                                    Text(stringResource(R.string.settings_chico), fontSize = 12.sp, color = mediumEmphasis)
                                }
                            }
                            HorizontalDivider(color = dividerColor)
                        }

                        item {
                            // NUEVO: cantidad de barras del ecualizador gráfico animado que se
                            // ve en el reproductor a pantalla completa. 6 = mínimo, 32 =
                            // estándar (default), 64 = máximo.
                            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                                Text(stringResource(R.string.settings_barras_del_ecualizador_grafico), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = trailingColor)
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
                                    Text(stringResource(R.string.settings_6_minimo), fontSize = 12.sp, color = mediumEmphasis)
                                    Text(stringResource(R.string.settings_1_s_barras, equalizerBarCount.value), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = trailingColor)
                                    Text(stringResource(R.string.settings_64_maximo), fontSize = 12.sp, color = mediumEmphasis)
                                }
                                Text(
                                    stringResource(R.string.settings_se_aplica_la_proxima_vez_que_abras),
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
                                headlineContent = { Text(stringResource(R.string.settings_estilo_de_ecualizador_grafico)) },
                                supportingContent = { Text(equalizerStyle.value.displayName) },
                                trailingContent = {
                                    TextButton(onClick = { showEqualizerStyleDialog.value = true }) {
                                        Text(stringResource(R.string.action_change), fontWeight = FontWeight.ExtraBold, color = trailingColor)
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
                                headlineContent = { Text(stringResource(R.string.settings_tipo_de_letra)) },
                                supportingContent = {
                                    Text(appFontPref.value.displayName, fontFamily = appFontPref.value.fontFamily)
                                },
                                trailingContent = {
                                    TextButton(onClick = { showFontDialog.value = true }) {
                                        Text(stringResource(R.string.action_change), fontWeight = FontWeight.ExtraBold, color = trailingColor)
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
                                headlineContent = { Text(stringResource(R.string.settings_tamano_de_letra)) },
                                supportingContent = { Text("${appFontSizePref.value.toInt()} sp") },
                                trailingContent = {
                                    TextButton(onClick = { showFontSizeDialog.value = true }) {
                                        Text(stringResource(R.string.action_change), fontWeight = FontWeight.ExtraBold, color = trailingColor)
                                    }
                                },
                                colors = listItemColors,
                                modifier = Modifier.clickable { showFontSizeDialog.value = true }
                            )
                            HorizontalDivider(color = dividerColor)
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
                                        Text(stringResource(R.string.action_change), fontWeight = FontWeight.ExtraBold, color = trailingColor)
                                    }
                                },
                                colors = listItemColors,
                                modifier = Modifier.clickable { showLanguageDialog.value = true }
                            )
                            HorizontalDivider(color = dividerColor)
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
                                        Text(stringResource(R.string.action_change), fontWeight = FontWeight.ExtraBold, color = trailingColor)
                                    }
                                },
                                colors = listItemColors,
                                modifier = Modifier.clickable { showEqualizerColorDialog.value = true }
                            )
                            HorizontalDivider(color = dividerColor)
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
                                        Text(stringResource(R.string.action_change), fontWeight = FontWeight.ExtraBold, color = trailingColor)
                                    }
                                },
                                colors = listItemColors,
                                modifier = Modifier.clickable { showMomentsColorDialog.value = true }
                            )
                            HorizontalDivider(color = dividerColor)
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
                                        Text(stringResource(R.string.action_change), fontWeight = FontWeight.ExtraBold, color = trailingColor)
                                    }
                                },
                                colors = listItemColors,
                                modifier = Modifier.clickable { showTogetherColorDialog.value = true }
                            )
                            HorizontalDivider(color = dividerColor)
                        }

                        item {
                            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                                Text(stringResource(R.string.settings_opacidad_del_fondo_del_widget), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = trailingColor)
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
                                    Text(stringResource(R.string.settings_transparente), fontSize = 12.sp, color = mediumEmphasis)
                                    Text(stringResource(R.string.settings_opaco), fontSize = 12.sp, color = mediumEmphasis)
                                }
                                Text(
                                    stringResource(R.string.settings_el_texto_del_widget_siempre_lleva),
                                    fontSize = 11.sp,
                                    color = mediumEmphasis,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                            HorizontalDivider(color = dividerColor)
                        }

                        item {
                            ListItem(
                                headlineContent = { Text(stringResource(R.string.settings_color_de_texto)) },
                                supportingContent = {
                                    Text(
                                        stringResource(R.string.settings_current_color, if (appTextColorPref.value == com.music.musicflame.ui.theme.COLOR_MODE_RAINBOW) stringResource(R.string.settings_arcoiris_en_movimiento) else settingsOptionLabel(appTextColorPref.value))
                                    )
                                },
                                trailingContent = { TextButton(onClick = { showTextColorDialog.value = true }) { Text(stringResource(R.string.action_change), fontWeight = FontWeight.ExtraBold, color = trailingColor) } },
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
                                        Text(stringResource(R.string.action_change), fontWeight = FontWeight.ExtraBold, color = trailingColor)
                                    }
                                },
                                colors = listItemColors,
                                modifier = Modifier.clickable { showNowPlayingColorDialog.value = true }
                            )
                            HorizontalDivider(color = dividerColor)
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
                                colors = listItemColors
                            )
                            HorizontalDivider(color = dividerColor)
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
                                colors = listItemColors
                            )
                            HorizontalDivider(color = dividerColor)
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
                                }) { Text(stringResource(R.string.action_change), fontWeight = FontWeight.ExtraBold, color = trailingColor) } },
                                colors = listItemColors
                            )
                            HorizontalDivider(color = dividerColor)
                        }

                        item {
                            ListItem(
                                headlineContent = { Text(stringResource(R.string.settings_icono_de_la_app)) },
                                supportingContent = { Text(stringResource(R.string.settings_elige_entre_los_iconos_predetermin)) },
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
                                                Toast.makeText(context, context.getString(R.string.settings_icono_cambiado_a_1_s, label), Toast.LENGTH_SHORT).show()
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
                        item { sectionHeader(stringResource(R.string.settings_manejo_de_canciones)) }

                        item {
                            ListItem(
                                headlineContent = { Text(stringResource(R.string.settings_filtrar_por_duracion)) },
                                supportingContent = { Text(stringResource(R.string.settings_excluir_o_mostrar_solo_canciones_d)) },
                                trailingContent = { TextButton(onClick = { showDurationFilterDialog.value = true }) { Text(stringResource(R.string.settings_configurar), fontWeight = FontWeight.ExtraBold, color = trailingColor) } },
                                colors = listItemColors
                            )
                            HorizontalDivider(color = dividerColor)
                        }

                        item {
                            ListItem(
                                headlineContent = { Text(stringResource(R.string.settings_formatos_de_audio_a_escuchar)) },
                                supportingContent = { Text(stringResource(R.string.settings_elige_que_formatos_de_tu_bibliotec)) },
                                trailingContent = { TextButton(onClick = { showAudioFormatsDialog.value = true }) { Text(stringResource(R.string.settings_ver_formatos), fontWeight = FontWeight.ExtraBold, color = trailingColor) } },
                                colors = listItemColors
                            )
                            HorizontalDivider(color = dividerColor)
                        }

                        item {
                            ListItem(
                                headlineContent = { Text(stringResource(R.string.settings_busqueda_de_anomalias)) },
                                supportingContent = { Text(stringResource(R.string.settings_detecta_caratulas_corruptas_metada)) },
                                trailingContent = {
                                    TextButton(onClick = { showAnomalyScanDialog.value = true }) {
                                        Text(stringResource(R.string.settings_analizar), fontWeight = FontWeight.ExtraBold, color = trailingColor)
                                    }
                                },
                                colors = listItemColors
                            )
                            HorizontalDivider(color = dividerColor)
                        }

                        item {
                            ListItem(
                                headlineContent = { Text(stringResource(R.string.settings_guardar_etiquetas_reales_en_el_arc)) },
                                supportingContent = {
                                    Text(
                                        text = when {
                                            !hasFileAccessPermission -> stringResource(R.string.settings_requiere_el_permiso_acceso_a_todos)
                                            realTagWritingEnabled -> stringResource(R.string.settings_activado_caratula_titulo_artista_y)
                                            else -> stringResource(R.string.settings_desactivado_los_cambios_solo_se_ve)
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
                                                        context.getString(R.string.settings_concede_acceso_a_todos_los_archivo),
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
                                headlineContent = { Text(stringResource(R.string.settings_temporizador_de_apagado)) },
                                supportingContent = {
                                    Text(
                                        when {
                                            sleepEndOfSong -> stringResource(R.string.settings_se_pausara_al_terminar_la_cancion)
                                            sleepActive -> {
                                                val totalSeconds = (sleepRemainingMs / 1000L).coerceAtLeast(0L)
                                                val mm = totalSeconds / 60
                                                val ss = totalSeconds % 60
                                                stringResource(R.string.settings_pausando_en_02d_02d).format(mm, ss)
                                            }
                                            else -> stringResource(R.string.settings_pausa_la_reproduccion_automaticame)
                                        }
                                    )
                                },
                                trailingContent = {
                                    if (sleepActive) {
                                        TextButton(onClick = { playerManager.cancelSleepTimer() }) {
                                            Text(stringResource(R.string.action_cancel), fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.error)
                                        }
                                    } else {
                                        TextButton(onClick = { showSleepTimerDialog.value = true }) {
                                            Text(stringResource(R.string.settings_configurar), fontWeight = FontWeight.ExtraBold, color = trailingColor)
                                        }
                                    }
                                },
                                colors = listItemColors
                            )
                            HorizontalDivider(color = dividerColor)
                        }

                        item { sectionHeader(stringResource(R.string.settings_reproduccion)) }

                        item {
                            ListItem(
                                headlineContent = { Text(stringResource(R.string.settings_reproducir_en_segundo_plano)) },
                                supportingContent = { Text(stringResource(R.string.settings_mantiene_el_reproductor_activo_fue)) },
                                trailingContent = {
                                    Switch(
                                        checked = playInBackground.value,
                                        onCheckedChange = {
                                            playInBackground.value = it
                                            settingsRepo.savePlayInBackground(it)
                                            Toast.makeText(context, if (it) context.getString(R.string.settings_segundo_plano_activado) else context.getString(R.string.settings_segundo_plano_desactivado), Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                },
                                colors = listItemColors
                            )
                            HorizontalDivider(color = dividerColor)
                        }
                        item {
                            ListItem(
                                headlineContent = { Text(stringResource(R.string.settings_pausar_al_desconectar_audifonos)) },
                                supportingContent = { Text(stringResource(R.string.settings_detiene_la_cancion_si_te_quitas_lo)) },
                                trailingContent = {
                                    Switch(
                                        checked = pauseOnDisconnect.value,
                                        onCheckedChange = {
                                            pauseOnDisconnect.value = it
                                            settingsRepo.savePauseOnDisconnect(it)
                                            Toast.makeText(context, if (it) context.getString(R.string.settings_pausa_automatica_activada) else context.getString(R.string.settings_pausa_automatica_desactivada), Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                },
                                colors = listItemColors
                            )
                            HorizontalDivider(color = dividerColor)
                        }

                        item {
                            ListItem(
                                headlineContent = { Text(stringResource(R.string.settings_optimizacion_de_bateria)) },
                                supportingContent = {
                                    Text(
                                        text = if (isIgnoringBattery)
                                            stringResource(R.string.settings_optimizado_para_musica_continua_re)
                                        else
                                            stringResource(R.string.settings_restringido_android_podria_pausar),
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
                                                    Toast.makeText(context, context.getString(R.string.settings_no_se_pudo_abrir_la_configuracion), Toast.LENGTH_SHORT).show()
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

                        item { sectionHeader(stringResource(R.string.settings_ecualizador)) }

                        item {
                            ListItem(
                                headlineContent = { Text(stringResource(R.string.settings_studio_pro_eq)) },
                                supportingContent = { Text(stringResource(R.string.settings_preset_activo_1_s, eqPresetSelected.value)) },
                                trailingContent = {
                                    Button(
                                        onClick = { showEqualizerDialog.value = true },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        )
                                    ) { Text(stringResource(R.string.settings_abrir_consola), fontWeight = FontWeight.ExtraBold) }
                                },
                                colors = listItemColors
                            )
                            HorizontalDivider(color = dividerColor)
                        }

                        item {
                            val isProEqUnlocked = com.music.musicflame.data.ProStatusHolder.isItemUnlocked("pro_eq_10band")
                            ListItem(
                                headlineContent = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(stringResource(R.string.settings_eq_pro_10_bandas))
                                        if (!isProEqUnlocked) {
                                            Spacer(Modifier.width(6.dp))
                                            Icon(Icons.Filled.Lock, contentDescription = stringResource(R.string.settings_bloqueado), modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.error)
                                            Spacer(Modifier.width(2.dp))
                                            Text("$15 MXN", fontSize = 10.sp, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                },
                                supportingContent = {
                                    Text(
                                        if (isProEqUnlocked) stringResource(R.string.settings_motor_propio_pre_amp_presets_y_nor)
                                        else stringResource(R.string.settings_funcion_de_pago_toca_para_ver_mas)
                                    )
                                },
                                trailingContent = {
                                    Button(
                                        onClick = { showProEqualizerDialog.value = true },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        )
                                    ) { Text(if (isProEqUnlocked) stringResource(R.string.settings_abrir) else stringResource(R.string.settings_ver), fontWeight = FontWeight.ExtraBold) }
                                },
                                colors = listItemColors
                            )
                            HorizontalDivider(color = dividerColor)
                        }

                    }
                    if (activeSection.value == "Especificaciones") {
                        item { sectionHeader(stringResource(R.string.settings_sobre)) }
                        item { ListItem(headlineContent = { Text(stringResource(R.string.settings_version)) }, supportingContent = { Text("3.13") }, colors = listItemColors); HorizontalDivider(color = dividerColor) }

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

                        item { sectionHeader(stringResource(R.string.settings_especificaciones_tecnicas)) }

                        item {
                            ListItem(
                                headlineContent = { Text(stringResource(R.string.settings_framework_ui)) },
                                supportingContent = { Text("Jetpack Compose") },
                                colors = listItemColors
                            ); HorizontalDivider(color = dividerColor)
                        }

                        item {
                            ListItem(
                                headlineContent = { Text(stringResource(R.string.settings_lenguaje_de_diseno)) },
                                supportingContent = { Text("Material Design 3") },
                                colors = listItemColors
                            ); HorizontalDivider(color = dividerColor)
                        }

                        item {
                            ListItem(
                                headlineContent = { Text(stringResource(R.string.settings_paleta_de_colores)) },
                                supportingContent = { Text(stringResource(R.string.settings_material_you_dynamic)) },
                                colors = listItemColors
                            ); HorizontalDivider(color = dividerColor)
                        }

                        item {
                            ListItem(
                                headlineContent = { Text(stringResource(R.string.settings_arquitectura)) },
                                supportingContent = { Text(stringResource(R.string.settings_declarativa_y_modular)) },
                                colors = listItemColors
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
                                        contentDescription = stringResource(R.string.settings_avatar_de_naofresita18),
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
                                        contentDescription = stringResource(R.string.settings_avatar_de_deivid_boop),
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

                        item { sectionHeader(stringResource(R.string.settings_comunidad)) }

                        item {
                            ListItem(
                                headlineContent = { Text(stringResource(R.string.settings_repositorio_en_github)) },
                                supportingContent = { Text(stringResource(R.string.settings_codigo_fuente_y_changelog_de_music)) },
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
                                colors = listItemColors,
                                modifier = Modifier.clickable {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://discord.gg/gGZ4zCZvab"))
                                    context.startActivity(intent)
                                }
                            )
                        }

                        item { sectionHeader(stringResource(R.string.settings_colaboraciones)) }

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
                                colors = listItemColors,
                                modifier = Modifier.clickable {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://open.spotify.com/artist/0jaY21Zd3ouygWqNdIpr8N?si=3IVegY4ESPuKJyJTLgiU9A&utm_source=copy-link"))
                                    context.startActivity(intent)
                                }
                            )
                            HorizontalDivider(color = dividerColor)
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
                                colors = listItemColors,
                                modifier = Modifier.clickable {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.instagram.com/berspring7"))
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

                    // PAGOS (OPCIONAL) — Licencia de apoyo vía Lemon Squeezy
                    // CAMBIO DE ARQUITECTURA (venta por ítem separado, ya NO todo-o-nada):
                    // cada ítem (o grupo chico de 2, agrupados solo por precio mínimo de
                    // Lemon Squeezy) es un producto propio con su propia license key. El
                    // usuario puede comprar solo lo que le interesa, o "TODO" de una vez.
                    // Puede terminar con VARIAS keys guardadas — ver savedLicenses arriba.
                    if (activeSection.value == "Pagos (opcional)") {
                        item { sectionHeader(stringResource(R.string.settings_licencia_de_apoyo_opcional)) }

                        // --- PASO A PASO: cómo comprar y activar ---
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
                                        stringResource(R.string.settings_como_funciona),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                    Text(
                                        stringResource(R.string.settings_es_pago_unico_no_es_suscripcion_pa),
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                                    )

                                    val steps = listOf(
                                        stringResource(R.string.settings_elegi_que_comprar_un_producto_suel, com.music.musicflame.data.PaymentCatalog.ITEMS.size),
                                        stringResource(R.string.settings_toca_comprar_se_abre_el_checkout_d),
                                        stringResource(R.string.settings_paga_ahi_con_tarjeta_o_el_metodo_q),
                                        stringResource(R.string.settings_te_llega_un_correo_con_tu_license),
                                        stringResource(R.string.settings_volve_a_la_app_pega_esa_key_en_tus)
                                    )
                                    steps.forEachIndexed { index, step ->
                                        Row(
                                            verticalAlignment = Alignment.Top,
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(20.dp)
                                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                                    .background(MaterialTheme.colorScheme.primary),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    "${index + 1}",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onPrimary
                                                )
                                            }
                                            Spacer(Modifier.width(10.dp))
                                            Text(step, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                        }
                                    }

                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        stringResource(R.string.settings_compraste_varios_productos_sueltos),
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
                        }

                        item {
                            Text(
                                stringResource(R.string.settings_compra_solo_lo_que_te_interesa_o_t, com.music.musicflame.data.PaymentCatalog.TOTAL_PRICE_MXN),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                            )
                        }

                        // --- "COMPRAR TODO" (producto TODO de Lemon Squeezy) ---
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
                                            stringResource(R.string.settings_una_sola_compra_de_todo_desbloquea, com.music.musicflame.data.PaymentCatalog.ITEMS.size),
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
                                                stringResource(R.string.settings_disponible_proximamente_esta_secci),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                                            )
                                        }
                                    }

                                    Spacer(Modifier.height(12.dp))

                                    val hasEverything = com.music.musicflame.data.PaymentCatalog.ITEMS.all { unlockedIds.contains(it.id) }
                                    if (hasEverything) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Filled.CloudDone,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onTertiaryContainer
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                stringResource(R.string.settings_ya_tenes_todo_desbloqueado),
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onTertiaryContainer
                                            )
                                        }
                                    } else {
                                        Button(
                                            onClick = {
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(com.music.musicflame.data.LicenseRepository.CHECKOUT_URL_TODO))
                                                context.startActivity(intent)
                                            },
                                            enabled = !paymentsSectionLocked,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(stringResource(R.string.settings_comprar_todo_por_1_s_mxn, com.music.musicflame.data.PaymentCatalog.TOTAL_PRICE_MXN), fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }

                        // --- TABLA DEL CATÁLOGO: estado real por ítem + botón "Comprar" por producto ---
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
                                        stringResource(R.string.settings_o_compra_por_separado),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                    Text(
                                        stringResource(R.string.settings_cada_fila_es_un_producto_real_de_l),
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                                    )

                                    // Un "producto" = una entrada del mapa (no un ítem del catálogo
                                    // suelto), así que si 2 ítems están agrupados aparecen en UNA
                                    // sola fila con un solo botón "Comprar" (mismo checkout link).
                                    com.music.musicflame.data.LicenseRepository.PRODUCT_NAME_TO_CATALOG_IDS
                                        .filterKeys { it != "TODO" }
                                        .forEach { (productName, ids) ->
                                            val itemsInProduct = com.music.musicflame.data.PaymentCatalog.ITEMS.filter { it.id in ids }
                                            val label = itemsInProduct.joinToString(" + ") { it.label }
                                            val priceMxn = itemsInProduct.sumOf { it.priceMxn }
                                            val owned = ids.all { unlockedIds.contains(it) }
                                            val checkoutUrl = com.music.musicflame.data.LicenseRepository.PRODUCT_NAME_TO_CHECKOUT_URL[productName]

                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 6.dp)
                                            ) {
                                                Icon(
                                                    if (owned) Icons.Filled.CloudDone else Icons.Filled.Lock,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp),
                                                    tint = if (owned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Spacer(Modifier.width(8.dp))
                                                Text(label, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                                if (owned) {
                                                    Text(
                                                        stringResource(R.string.settings_ya_la_tenes),
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                } else {
                                                    OutlinedButton(
                                                        onClick = {
                                                            if (checkoutUrl != null) {
                                                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(checkoutUrl)))
                                                            }
                                                        },
                                                        enabled = !paymentsSectionLocked && checkoutUrl != null,
                                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                                    ) {
                                                        Text("$$priceMxn MXN", fontSize = 12.sp)
                                                    }
                                                }
                                            }
                                        }
                                }
                            }
                        }

                        // --- LICENCIAS GUARDADAS + CAMPO PARA PEGAR UNA NUEVA ---
                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                                    .alpha(if (paymentsSectionLocked) 0.6f else 1f),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                                    Text(
                                        stringResource(R.string.settings_tus_licencias),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                    Text(
                                        stringResource(R.string.settings_cada_compra_te_llega_por_correo_co),
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                                    )

                                    if (savedLicenses.isEmpty()) {
                                        Text(
                                            stringResource(R.string.settings_todavia_no_activaste_ninguna_licen),
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    } else {
                                        savedLicenses.forEach { lic ->
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                                            ) {
                                                Icon(
                                                    if (lic.status == LicenseStatus.ACTIVE.name) Icons.Filled.CloudDone else Icons.Filled.Lock,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp),
                                                    tint = if (lic.status == LicenseStatus.ACTIVE.name) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                                )
                                                Spacer(Modifier.width(8.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        licenseRepo.labelFor(lic.unlockedItemIds),
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Text(
                                                        licenseRepo.mask(lic.key) + (lic.lastError?.let { " · $it" } ?: ""),
                                                        fontSize = 11.sp,
                                                        color = if (lic.lastError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                TextButton(
                                                    onClick = {
                                                        licenseRepo.removeLicense(lic.key)
                                                        savedLicenses = licenseRepo.getSavedLicenses()
                                                        com.music.musicflame.data.ProStatusHolder.refresh(context)
                                                    },
                                                    enabled = !paymentsSectionLocked
                                                ) {
                                                    Text(stringResource(R.string.settings_quitar), color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                                                }
                                            }
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

                                    // --- CAMPO PARA PEGAR UNA LICENSE KEY NUEVA ---
                                    OutlinedTextField(
                                        value = licenseInput,
                                        onValueChange = { licenseInput = it },
                                        label = { Text("License key") },
                                        placeholder = { Text(stringResource(R.string.settings_pega_aqui_la_key_que_te_llego_por)) },
                                        singleLine = true,
                                        enabled = !isValidating && !paymentsSectionLocked,
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    Spacer(Modifier.height(12.dp))

                                    Button(
                                        onClick = {
                                            val keyToValidate = licenseInput
                                            isValidating = true
                                            errorMessage = null
                                            paymentsScope.launch {
                                                when (val result = licenseRepo.validateAndAdd(keyToValidate)) {
                                                    is LicenseValidationResult.Success -> {
                                                        savedLicenses = licenseRepo.getSavedLicenses()
                                                        licenseInput = ""
                                                        errorMessage = null
                                                        // Activa la personalización comprada al instante
                                                        // en toda la app (tema, reproductor, etc.).
                                                        com.music.musicflame.data.ProStatusHolder.refresh(context)
                                                        Toast.makeText(
                                                            context,
                                                            context.getString(R.string.settings_activado_1_s_gracias_por_tu_apoyo, result.unlockedLabel),
                                                            Toast.LENGTH_LONG
                                                        ).show()
                                                    }
                                                    is LicenseValidationResult.Invalid -> {
                                                        savedLicenses = licenseRepo.getSavedLicenses()
                                                        errorMessage = result.reason
                                                    }
                                                    LicenseValidationResult.AlreadyAdded -> {
                                                        errorMessage = context.getString(R.string.settings_esa_key_ya_esta_activada)
                                                    }
                                                    LicenseValidationResult.NetworkError -> {
                                                        errorMessage = context.getString(R.string.settings_sin_conexion_revisa_tu_internet_e)
                                                    }
                                                }
                                                isValidating = false
                                            }
                                        },
                                        enabled = !isValidating && !paymentsSectionLocked && licenseInput.isNotBlank(),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        if (isValidating) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(18.dp),
                                                strokeWidth = 2.dp,
                                                color = MaterialTheme.colorScheme.onPrimary
                                            )
                                        } else {
                                            Text(stringResource(R.string.settings_activar))
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
                                            stringResource(R.string.settings_aviso_de_uso_y_redistribucion),
                                            fontWeight = FontWeight.Black,
                                            fontSize = 16.sp,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                    Spacer(Modifier.height(12.dp))
                                    Text(
                                        stringResource(R.string.settings_musicflame_es_un_proyecto_personal),
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Spacer(Modifier.height(10.dp))

                                    val avisoReglas = listOf(
                                        stringResource(R.string.settings_contacto_previo_obligatorio_cualqu),
                                        stringResource(R.string.settings_reparto_de_ingresos_si_dicha_redis),
                                        stringResource(R.string.settings_creditos_intactos_no_esta_permitid),
                                        stringResource(R.string.settings_sin_venta_no_autorizada_musicflame),
                                        stringResource(R.string.settings_incumplimiento_toda_redistribucion)
                                    )

                                    avisoReglas.forEach { regla ->
                                        Row(modifier = Modifier.padding(vertical = 4.dp)) {
                                            Text("•  ", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onErrorContainer)
                                            Text(regla, fontSize = 13.sp, color = MaterialTheme.colorScheme.onErrorContainer)
                                        }
                                    }

                                    Spacer(Modifier.height(10.dp))
                                    Text(
                                        stringResource(R.string.settings_para_solicitar_autorizacion_o_coor),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }
                    }

                    // HOGAR DE SHIMURO
                    if (activeSection.value == "Hogar de Shimuro") {
                        item { sectionHeader(stringResource(R.string.section_shimuro_home)) }
                        item {
                            ShimuroHomeCard()
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
                title = { Text(stringResource(R.string.settings_filtrar_por_duracion), fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        OutlinedTextField(value = tempMin.value, onValueChange = { tempMin.value = it }, label = { Text(stringResource(R.string.settings_duracion_minima_segundos)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(value = tempMax.value, onValueChange = { tempMax.value = it }, label = { Text(stringResource(R.string.settings_duracion_maxima_segundos)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.settings_modo), fontWeight = FontWeight.Bold)
                        TextButton(onClick = { tempMode.value = "exclude" }) { Text(stringResource(R.string.settings_excluir), fontWeight = FontWeight.Bold, color = if (tempMode.value == "exclude") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) }
                        TextButton(onClick = { tempMode.value = "only" }) { Text(stringResource(R.string.settings_solo_mostrar), fontWeight = FontWeight.Bold, color = if (tempMode.value == "only") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) }
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
                    }) { Text(stringResource(R.string.action_save), fontWeight = FontWeight.Bold) }
                },
                dismissButton = { TextButton(onClick = { showDurationFilterDialog.value = false }) { Text(stringResource(R.string.action_cancel), fontWeight = FontWeight.Bold) } }
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
                title = { Text(stringResource(R.string.settings_formatos_de_audio_a_escuchar), fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text(
                            stringResource(R.string.settings_los_que_ya_tienes_en_tu_biblioteca),
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
                                                    format.isPlaylistFormat -> stringResource(R.string.settings_solo_playlists)
                                                    format.usable -> stringResource(R.string.settings_usable)
                                                    else -> stringResource(R.string.settings_no_usable)
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
                                                    text = stringResource(R.string.settings_detectado),
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
                    }) { Text(stringResource(R.string.action_save), fontWeight = FontWeight.Bold) }
                },
                dismissButton = { TextButton(onClick = { showAudioFormatsDialog.value = false }) { Text(stringResource(R.string.action_cancel), fontWeight = FontWeight.Bold) } }
            )
        }

        if (showSleepTimerDialog.value) {
            val customMinutes = remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showSleepTimerDialog.value = false },
                title = { Text(stringResource(R.string.settings_temporizador_de_apagado), fontWeight = FontWeight.Bold) },
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
                                Text(stringResource(R.string.settings_1_s_minutos, minutes), modifier = Modifier.fillMaxWidth(), fontWeight = FontWeight.Bold)
                            }
                        }
                        TextButton(
                            onClick = {
                                playerManager.startSleepTimerEndOfSong()
                                showSleepTimerDialog.value = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.settings_al_terminar_la_cancion_actual), modifier = Modifier.fillMaxWidth(), fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = customMinutes.value,
                            onValueChange = { customMinutes.value = it.filter { c -> c.isDigit() } },
                            label = { Text(stringResource(R.string.settings_minutos_personalizados)) },
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
                    }) { Text(stringResource(R.string.settings_iniciar), fontWeight = FontWeight.Bold) }
                },
                dismissButton = { TextButton(onClick = { showSleepTimerDialog.value = false }) { Text(stringResource(R.string.action_cancel), fontWeight = FontWeight.Bold) } }
            )
        }

        if (showThemeDialog.value) {
            val tempTheme = remember { mutableStateOf(appTheme.value) }
            AlertDialog(
                onDismissRequest = { showThemeDialog.value = false },
                title = { Text(stringResource(R.string.settings_elegir_tema), fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        listOf("Siguiendo al sistema", "Fondo blanco", "Fondo oscuro").forEach { theme ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { tempTheme.value = theme }.padding(vertical = 8.dp)) {
                                RadioButton(selected = tempTheme.value == theme, onClick = { tempTheme.value = theme })
                                Spacer(Modifier.width(8.dp))
                                Text(settingsOptionLabel(theme), fontSize = 14.sp)
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        settingsRepo.saveAppTheme(tempTheme.value)
                        appTheme.value = tempTheme.value
                        showThemeDialog.value = false
                    }) { Text(stringResource(R.string.action_save), fontWeight = FontWeight.Bold) }
                },
                dismissButton = { TextButton(onClick = { showThemeDialog.value = false }) { Text(stringResource(R.string.action_cancel), fontWeight = FontWeight.Bold) } }
            )
        }

        if (showEqualizerStyleDialog.value) {
            com.music.musicflame.ui.components.EqualizerStylePickerDialog(
                currentStyle = equalizerStyle.value,
                isStyleUnlocked = { style -> style.catalogId == null || unlockedIds.contains(style.catalogId) },
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
                // Las 5 fuentes premium se venden juntas como un solo "Paquete"
                // (ver PaymentCatalog/LicenseRepository), así que cualquiera de
                // sus ids representa el desbloqueo del paquete completo.
                isUnlocked = unlockedIds.contains("font_comfortaa"),
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

        if (showLanguageDialog.value) {
            com.music.musicflame.ui.components.LanguagePickerDialog(
                currentLanguage = appLanguagePref.value,
                onDismiss = { showLanguageDialog.value = false },
                onConfirm = { newLanguage ->
                    showLanguageDialog.value = false
                    if (newLanguage != appLanguagePref.value) {
                        appLanguagePref.value = newLanguage
                        // Android 13+: el sistema recrea la Activity solo. Android 12/12L: recreate().
                        with(com.music.musicflame.data.LanguageManager) {
                            context.findActivity()?.let { apply(it, newLanguage) }
                        }
                    }
                }
            )
        }

        if (showAlbumArtShapeDialog.value) {
            AlertDialog(
                onDismissRequest = { showAlbumArtShapeDialog.value = false },
                title = { Text(stringResource(R.string.settings_forma_de_la_caratula), fontWeight = FontWeight.Bold) },
                text = {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        listOf(
                            com.music.musicflame.AlbumArtShapeType.SQUARE to stringResource(R.string.settings_cuadrado),
                            com.music.musicflame.AlbumArtShapeType.CIRCLE to stringResource(R.string.settings_circulo),
                            com.music.musicflame.AlbumArtShapeType.HEXAGON to stringResource(R.string.settings_hexagono),
                            com.music.musicflame.AlbumArtShapeType.VINYL to stringResource(R.string.settings_vinilo),
                            com.music.musicflame.AlbumArtShapeType.SQUIRCLE to stringResource(R.string.settings_squircle)
                        ).forEach { (shape, label) ->
                            val isSelected = shapeDialogShape.value == shape
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { shapeDialogShape.value = shape }
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
                                RadioButton(selected = isSelected, onClick = { shapeDialogShape.value = shape })
                            }
                        }

                        // --- Diseños propios ---
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Text(
                            stringResource(R.string.settings_mis_disenos),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                        coverDesigns.value.forEach { design ->
                            val isSelected = shapeDialogShape.value == com.music.musicflame.AlbumArtShapeType.CUSTOM && shapeDialogDesignId.value == design.id
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        shapeDialogShape.value = com.music.musicflame.AlbumArtShapeType.CUSTOM
                                        shapeDialogDesignId.value = design.id
                                    }
                                    .padding(vertical = 6.dp)
                            ) {
                                com.music.musicflame.ui.components.AlbumArtShapePreview(
                                    shape = com.music.musicflame.AlbumArtShapeType.CUSTOM,
                                    size = 40.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    customDesign = design
                                )
                                Spacer(Modifier.width(14.dp))
                                Text(
                                    design.name,
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = {
                                    editingCoverDesign.value = design
                                    showCoverEditor.value = true
                                }) { Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.settings_editar_diseno)) }
                                IconButton(onClick = { coverDesignToDelete.value = design }) {
                                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.settings_borrar_diseno))
                                }
                                RadioButton(selected = isSelected, onClick = {
                                    shapeDialogShape.value = com.music.musicflame.AlbumArtShapeType.CUSTOM
                                    shapeDialogDesignId.value = design.id
                                })
                            }
                        }
                        TextButton(
                            onClick = {
                                editingCoverDesign.value = null
                                showCoverEditor.value = true
                            },
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.settings_crear_diseno), fontWeight = FontWeight.Bold)
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        // Un diseño propio solo es válido si todavía existe en la lista.
                        val chosenDesign = if (shapeDialogShape.value == com.music.musicflame.AlbumArtShapeType.CUSTOM)
                            coverDesigns.value.firstOrNull { it.id == shapeDialogDesignId.value }
                        else null
                        val finalShape = if (shapeDialogShape.value == com.music.musicflame.AlbumArtShapeType.CUSTOM && chosenDesign == null)
                            com.music.musicflame.AlbumArtShapeType.SQUARE
                        else shapeDialogShape.value
                        settingsRepo.saveAlbumArtShape(finalShape)
                        if (chosenDesign != null) {
                            settingsRepo.saveActiveCoverDesignId(chosenDesign.id)
                            activeCoverDesignId.value = chosenDesign.id
                        }
                        albumArtShapePref.value = finalShape
                        onAlbumArtShapeChanged(finalShape)
                        onCoverDesignChanged(settingsRepo.getActiveCoverDesign())
                        // Los widgets leen la forma al dibujarse: refrescarlos para que la tomen ya.
                        MusicFlameWidgetProvider.refreshAllWidgets(context)
                        showAlbumArtShapeDialog.value = false
                    }) { Text(stringResource(R.string.action_save), fontWeight = FontWeight.Bold) }
                },
                dismissButton = { TextButton(onClick = { showAlbumArtShapeDialog.value = false }) { Text(stringResource(R.string.action_cancel), fontWeight = FontWeight.Bold) } }
            )
        }

        // Editor de diseños (pantalla completa, con vista previa en vivo).
        if (showCoverEditor.value) {
            com.music.musicflame.ui.components.CoverDesignEditorDialog(
                initial = editingCoverDesign.value,
                previewSong = playerManager.currentSong.value,
                onDismiss = { showCoverEditor.value = false },
                onSave = { saved ->
                    val list = coverDesigns.value
                    val updated = if (list.any { it.id == saved.id }) list.map { if (it.id == saved.id) saved else it } else list + saved
                    settingsRepo.saveCoverDesigns(updated)
                    coverDesigns.value = updated
                    // Si se editó el diseño que está en uso, se aplica al instante (app y widgets).
                    if (albumArtShapePref.value == com.music.musicflame.AlbumArtShapeType.CUSTOM && activeCoverDesignId.value == saved.id) {
                        onCoverDesignChanged(saved)
                        MusicFlameWidgetProvider.refreshAllWidgets(context)
                    }
                    // El diálogo de forma deja marcado el diseño recién guardado (se aplica con "Guardar").
                    shapeDialogShape.value = com.music.musicflame.AlbumArtShapeType.CUSTOM
                    shapeDialogDesignId.value = saved.id
                    showCoverEditor.value = false
                }
            )
        }

        // Confirmación antes de borrar un diseño.
        coverDesignToDelete.value?.let { design ->
            AlertDialog(
                onDismissRequest = { coverDesignToDelete.value = null },
                title = { Text(stringResource(R.string.settings_borrar_diseno), fontWeight = FontWeight.Bold) },
                text = { Text(stringResource(R.string.settings_borrar_1_s_esta_accion_no_se_puede, design.name)) },
                confirmButton = {
                    TextButton(onClick = {
                        val remaining = coverDesigns.value.filter { it.id != design.id }
                        settingsRepo.saveCoverDesigns(remaining)
                        coverDesigns.value = remaining
                        if (shapeDialogDesignId.value == design.id) {
                            shapeDialogDesignId.value = null
                            if (shapeDialogShape.value == com.music.musicflame.AlbumArtShapeType.CUSTOM) shapeDialogShape.value = com.music.musicflame.AlbumArtShapeType.SQUARE
                        }
                        // Si era el diseño en uso, vuelve a cuadrado para no dejar una forma sin definición.
                        if (activeCoverDesignId.value == design.id) {
                            settingsRepo.saveActiveCoverDesignId(null)
                            activeCoverDesignId.value = null
                            if (albumArtShapePref.value == com.music.musicflame.AlbumArtShapeType.CUSTOM) {
                                settingsRepo.saveAlbumArtShape(com.music.musicflame.AlbumArtShapeType.SQUARE)
                                albumArtShapePref.value = com.music.musicflame.AlbumArtShapeType.SQUARE
                                onAlbumArtShapeChanged(com.music.musicflame.AlbumArtShapeType.SQUARE)
                            }
                            onCoverDesignChanged(null)
                            MusicFlameWidgetProvider.refreshAllWidgets(context)
                        }
                        coverDesignToDelete.value = null
                    }) { Text(stringResource(R.string.action_clear), fontWeight = FontWeight.Bold) }
                },
                dismissButton = { TextButton(onClick = { coverDesignToDelete.value = null }) { Text(stringResource(R.string.action_cancel), fontWeight = FontWeight.Bold) } }
            )
        }

        if (showTextColorDialog.value) {
            val tempTextColor = remember { mutableStateOf(appTextColorPref.value) }
            val tempCustomHex = remember { mutableStateOf(customTextColorHex.value) }
            AlertDialog(
                onDismissRequest = { showTextColorDialog.value = false },
                title = { Text(stringResource(R.string.settings_color_de_texto), fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        listOf("Adaptativo", "Personalizado", com.music.musicflame.ui.theme.COLOR_MODE_RAINBOW).forEach { colorOption ->
                            // Solo Arcoíris es de pago acá; Adaptativo y Personalizado son gratis.
                            val locked = !unlockedIds.contains("text_color_rainbow") && colorOption == com.music.musicflame.ui.theme.COLOR_MODE_RAINBOW
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
                                        Text(settingsOptionLabel(colorOption), fontSize = 14.sp)
                                        if (locked) {
                                            Spacer(Modifier.width(6.dp))
                                            Icon(Icons.Filled.Lock, contentDescription = stringResource(R.string.settings_bloqueado), modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.error)
                                            Spacer(Modifier.width(2.dp))
                                            Text("$5 MXN", fontSize = 10.sp, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    if (colorOption == "Adaptativo") {
                                        Text(
                                            stringResource(R.string.settings_blanco_o_negro_segun_el_fondo_como),
                                            fontSize = 11.sp,
                                            color = mediumEmphasis
                                        )
                                    } else if (colorOption == com.music.musicflame.ui.theme.COLOR_MODE_RAINBOW) {
                                        Text(
                                            stringResource(R.string.settings_colores_del_espectro_en_movimiento),
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
                                        .background(com.music.musicflame.ui.theme.parseCustomTextColor(tempCustomHex.value))
                                        .border(1.dp, Color.Gray, RoundedCornerShape(6.dp))
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.settings_vista_previa), fontSize = 12.sp)
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
                    }) { Text(stringResource(R.string.action_save), fontWeight = FontWeight.Bold) }
                },
                dismissButton = { TextButton(onClick = { showTextColorDialog.value = false }) { Text(stringResource(R.string.action_cancel), fontWeight = FontWeight.Bold) } }
            )
        }

        if (showEqualizerColorDialog.value) {
            val tempEqColorMode = remember { mutableStateOf(equalizerColorModePref.value) }
            val tempEqCustomHex = remember { mutableStateOf(equalizerCustomColorHexPref.value) }
            AlertDialog(
                onDismissRequest = { showEqualizerColorDialog.value = false },
                title = { Text(stringResource(R.string.settings_color_del_ecualizador), fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text(
                            stringResource(R.string.settings_aplica_al_ecualizador_grafico_anim),
                            fontSize = 12.sp,
                            color = mediumEmphasis,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        // "Adaptativo" es gratis acá también (igual que en "Color de
                        // texto"/"Now Playing") — Personalizado y Arcoíris son de pago,
                        // vendidos juntos en un solo producto (eq_color_custom + eq_color_rainbow).
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
                                        Text(settingsOptionLabel(colorOption), fontSize = 14.sp)
                                        if (locked) {
                                            Spacer(Modifier.width(6.dp))
                                            Icon(Icons.Filled.Lock, contentDescription = stringResource(R.string.settings_bloqueado), modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.error)
                                            Spacer(Modifier.width(2.dp))
                                            Text("$5 MXN", fontSize = 10.sp, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    if (colorOption == "Adaptativo") {
                                        Text(
                                            stringResource(R.string.settings_blanco_o_negro_segun_el_fondo_como),
                                            fontSize = 11.sp,
                                            color = mediumEmphasis
                                        )
                                    } else if (colorOption == com.music.musicflame.ui.theme.COLOR_MODE_RAINBOW) {
                                        Text(
                                            stringResource(R.string.settings_colores_del_espectro_en_movimiento),
                                            fontSize = 11.sp,
                                            color = mediumEmphasis
                                        )
                                    }
                                }
                            }
                        }

                        if (tempEqColorMode.value == "Personalizado" && unlockedIds.contains("eq_color_custom")) {
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
                                        .background(com.music.musicflame.ui.theme.parseCustomTextColor(tempEqCustomHex.value))
                                        .border(1.dp, Color.Gray, RoundedCornerShape(6.dp))
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.settings_vista_previa), fontSize = 12.sp)
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
                    }) { Text(stringResource(R.string.action_save), fontWeight = FontWeight.Bold) }
                },
                dismissButton = { TextButton(onClick = { showEqualizerColorDialog.value = false }) { Text(stringResource(R.string.action_cancel), fontWeight = FontWeight.Bold) } }
            )
        }

        if (showFullPlayerBgDialog.value) {
            // Estados TEMPORALES: nada se guarda hasta pulsar "Guardar".
            val savedFpUri = remember { settingsRepo.getFullPlayerBgUri() }
            val savedFpConfig = remember { settingsRepo.loadFullPlayerBg() }
            // Si el archivo guardado ya no existe, se arranca como "sin imagen".
            val tempFpMode = remember { mutableStateOf(if (savedFpConfig != null) "custom" else "none") }
            val tempFpUri = remember { mutableStateOf(savedFpConfig?.uri) }
            val tempFpIsGif = remember { mutableStateOf(savedFpConfig?.isGif ?: false) }
            val tempFpBrightness = remember { mutableStateOf(settingsRepo.getFullPlayerBgBrightness().coerceIn(-1f, 1f)) }
            // Copias creadas durante ESTE diálogo (se borran si se reemplazan o se cancela).
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
                            // El diálogo se cerró mientras se copiaba: no queda archivo huérfano.
                            settingsRepo.deleteFullPlayerBgFile(result.first)
                        } else if (result != null) {
                            // Reemplazo: si la copia anterior se creó en este diálogo, se borra ya.
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
                onDismissRequest = {
                    discardCreatedFpFiles(except = null)
                    showFullPlayerBgDialog.value = false
                },
                title = { Text(stringResource(R.string.settings_fondo_del_reproductor), fontWeight = FontWeight.Bold) },
                text = {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        Text(
                            stringResource(R.string.settings_fondo_propio_solo_para_el_reproduc),
                            fontSize = 12.sp,
                            color = mediumEmphasis,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        listOf("none", "custom").forEach { modeOption ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { tempFpMode.value = modeOption }
                                    .padding(vertical = 8.dp)
                            ) {
                                RadioButton(
                                    selected = tempFpMode.value == modeOption,
                                    onClick = { tempFpMode.value = modeOption }
                                )
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(if (modeOption == "none") stringResource(R.string.settings_sin_fondo) else stringResource(R.string.settings_imagen_gif), fontSize = 14.sp)
                                    Text(
                                        if (modeOption == "none") stringResource(R.string.settings_el_reproductor_se_ve_como_siempre) else stringResource(R.string.settings_usa_tu_propia_imagen_o_gif_animado),
                                        fontSize = 11.sp,
                                        color = mediumEmphasis
                                    )
                                }
                            }
                        }

                        if (tempFpMode.value == "custom") {
                            Spacer(Modifier.height(4.dp))
                            OutlinedButton(
                                onClick = { pickFullPlayerBgLauncher.launch("image/*") },
                                enabled = !isCopyingFp.value,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    if (isCopyingFp.value) stringResource(R.string.settings_copying) else stringResource(R.string.settings_elegir_imagen_o_gif),
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            val previewUri = tempFpUri.value
                            if (previewUri == null) {
                                Text(
                                    stringResource(R.string.settings_elige_una_imagen_o_gif_para_activa),
                                    fontSize = 11.sp,
                                    color = mediumEmphasis,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            } else {
                                Spacer(Modifier.height(12.dp))
                                Text(stringResource(R.string.settings_vista_previa), fontSize = 12.sp, color = mediumEmphasis)
                                Spacer(Modifier.height(4.dp))
                                val previewRequest = remember(previewUri) {
                                    ImageRequest.Builder(context)
                                        .data(previewUri)
                                        .decoderFactory(
                                            if (android.os.Build.VERSION.SDK_INT >= 28) coil.decode.ImageDecoderDecoder.Factory()
                                            else coil.decode.GifDecoder.Factory()
                                        )
                                        .build()
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(160.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.Black)
                                ) {
                                    AsyncImage(
                                        model = previewRequest,
                                        contentDescription = stringResource(R.string.settings_vista_previa_del_fondo),
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                    // Brillo en vivo: misma convención que el brillo global.
                                    val previewBrightness = tempFpBrightness.value
                                    if (previewBrightness != 0f) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(
                                                    if (previewBrightness < 0f) Color.Black.copy(alpha = kotlin.math.abs(previewBrightness))
                                                    else Color.White.copy(alpha = previewBrightness)
                                                )
                                        )
                                    }
                                    // Mismo scrim suave que usa el reproductor.
                                    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.2f)))
                                }

                                Spacer(Modifier.height(12.dp))
                                Text(stringResource(R.string.settings_brillo_del_fondo), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Slider(
                                    value = tempFpBrightness.value,
                                    onValueChange = { tempFpBrightness.value = it },
                                    valueRange = -1f..1f,
                                    steps = 20,
                                    colors = SliderDefaults.colors(
                                        thumbColor = trailingColor,
                                        activeTrackColor = trailingColor
                                    )
                                )
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(stringResource(R.string.settings_oscuro), fontSize = 12.sp, color = mediumEmphasis)
                                    Text(stringResource(R.string.settings_original), fontSize = 12.sp, color = mediumEmphasis)
                                    Text(stringResource(R.string.settings_brillante), fontSize = 12.sp, color = mediumEmphasis)
                                }
                                TextButton(
                                    onClick = { tempFpBrightness.value = 0f },
                                    enabled = tempFpBrightness.value != 0f
                                ) { Text(stringResource(R.string.settings_volver_a_0), fontWeight = FontWeight.Bold) }

                                Spacer(Modifier.height(4.dp))
                                OutlinedButton(
                                    onClick = {
                                        // Si la copia se creó en este diálogo se borra ya; la guardada
                                        // anteriormente se borra recién al pulsar "Guardar".
                                        val current = tempFpUri.value
                                        if (current != null && createdFpFiles.contains(current)) {
                                            settingsRepo.deleteFullPlayerBgFile(current)
                                            createdFpFiles.remove(current)
                                        }
                                        tempFpUri.value = null
                                        tempFpIsGif.value = false
                                        tempFpBrightness.value = 0f
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) { Text(stringResource(R.string.settings_quitar), fontWeight = FontWeight.Bold) }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        enabled = !isCopyingFp.value,
                        onClick = {
                            val finalUri = tempFpUri.value
                            val finalMode = if (tempFpMode.value == "custom" && finalUri != null) "custom" else "none"
                            // Al quitar o reemplazar, se borra el archivo anterior guardado.
                            if (savedFpUri != null && savedFpUri != finalUri) {
                                settingsRepo.deleteFullPlayerBgFile(savedFpUri)
                            }
                            discardCreatedFpFiles(except = finalUri)
                            settingsRepo.saveFullPlayerBgAll(
                                mode = finalMode,
                                uri = finalUri,
                                isGif = tempFpIsGif.value,
                                brightness = tempFpBrightness.value
                            )
                            fullPlayerBgModePref.value = finalMode
                            fullPlayerBgIsGifPref.value = tempFpIsGif.value
                            showFullPlayerBgDialog.value = false
                        }
                    ) { Text(stringResource(R.string.action_save), fontWeight = FontWeight.Bold) }
                },
                dismissButton = {
                    TextButton(onClick = {
                        discardCreatedFpFiles(except = null)
                        showFullPlayerBgDialog.value = false
                    }) { Text(stringResource(R.string.action_cancel), fontWeight = FontWeight.Bold) }
                }
            )
        }

        if (showAppBgDialog.value) {
            // Estados TEMPORALES: nada se guarda hasta pulsar "Guardar".
            val tempAppUri = remember { mutableStateOf(backgroundImageUri.value ?: playerGifUri.value) }
            val tempAppIsGif = remember { mutableStateOf(backgroundImageUri.value == null && playerGifUri.value != null) }
            val tempAppMode = remember { mutableStateOf(if (backgroundImageUri.value != null || playerGifUri.value != null) "custom" else "none") }
            val tempAppBrightness = remember { mutableStateOf(backgroundBrightness.value.coerceIn(-1f, 1f)) }

            val pickAppBgLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { picked ->
                picked?.let {
                    try {
                        context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    } catch (e: Exception) {}
                    tempAppUri.value = it.toString()
                    tempAppIsGif.value = context.contentResolver.getType(it) == "image/gif" ||
                            it.toString().lowercase().endsWith(".gif")
                    tempAppMode.value = "custom"
                }
            }

            AlertDialog(
                onDismissRequest = { showAppBgDialog.value = false },
                title = { Text(stringResource(R.string.settings_fondo_de_la_app), fontWeight = FontWeight.Bold) },
                text = {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        Text(
                            stringResource(R.string.settings_imagen_o_gif_de_fondo_general_de_l),
                            fontSize = 12.sp,
                            color = mediumEmphasis,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        listOf("none", "custom").forEach { modeOption ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { tempAppMode.value = modeOption }
                                    .padding(vertical = 8.dp)
                            ) {
                                RadioButton(
                                    selected = tempAppMode.value == modeOption,
                                    onClick = { tempAppMode.value = modeOption }
                                )
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(if (modeOption == "none") stringResource(R.string.settings_sin_fondo) else stringResource(R.string.settings_imagen_gif), fontSize = 14.sp)
                                    Text(
                                        if (modeOption == "none") stringResource(R.string.settings_la_app_se_ve_como_siempre) else stringResource(R.string.settings_usa_tu_propia_imagen_o_gif_animado),
                                        fontSize = 11.sp,
                                        color = mediumEmphasis
                                    )
                                }
                            }
                        }

                        if (tempAppMode.value == "custom") {
                            Spacer(Modifier.height(4.dp))
                            OutlinedButton(
                                onClick = { pickAppBgLauncher.launch("image/*") },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text(stringResource(R.string.settings_elegir_imagen_o_gif), fontWeight = FontWeight.Bold) }

                            val previewUri = tempAppUri.value
                            if (previewUri == null) {
                                Text(
                                    stringResource(R.string.settings_elige_una_imagen_o_gif_para_activa),
                                    fontSize = 11.sp,
                                    color = mediumEmphasis,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            } else {
                                Spacer(Modifier.height(12.dp))
                                Text(stringResource(R.string.settings_vista_previa), fontSize = 12.sp, color = mediumEmphasis)
                                Spacer(Modifier.height(4.dp))
                                val previewRequest = remember(previewUri) {
                                    ImageRequest.Builder(context)
                                        .data(previewUri)
                                        .decoderFactory(
                                            if (android.os.Build.VERSION.SDK_INT >= 28) coil.decode.ImageDecoderDecoder.Factory()
                                            else coil.decode.GifDecoder.Factory()
                                        )
                                        .build()
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(160.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.Black)
                                ) {
                                    AsyncImage(
                                        model = previewRequest,
                                        contentDescription = stringResource(R.string.settings_vista_previa_del_fondo),
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                    val previewBrightness = tempAppBrightness.value
                                    if (previewBrightness != 0f) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(
                                                    if (previewBrightness < 0f) Color.Black.copy(alpha = kotlin.math.abs(previewBrightness))
                                                    else Color.White.copy(alpha = previewBrightness)
                                                )
                                        )
                                    }
                                }

                                Spacer(Modifier.height(12.dp))
                                Text(stringResource(R.string.settings_brillo_del_fondo), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Slider(
                                    value = tempAppBrightness.value,
                                    onValueChange = { tempAppBrightness.value = it },
                                    valueRange = -1f..1f,
                                    steps = 20,
                                    colors = SliderDefaults.colors(
                                        thumbColor = trailingColor,
                                        activeTrackColor = trailingColor
                                    )
                                )
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(stringResource(R.string.settings_oscuro), fontSize = 12.sp, color = mediumEmphasis)
                                    Text(stringResource(R.string.settings_original), fontSize = 12.sp, color = mediumEmphasis)
                                    Text(stringResource(R.string.settings_brillante), fontSize = 12.sp, color = mediumEmphasis)
                                }
                                TextButton(
                                    onClick = { tempAppBrightness.value = 0f },
                                    enabled = tempAppBrightness.value != 0f
                                ) { Text(stringResource(R.string.settings_volver_a_0), fontWeight = FontWeight.Bold) }

                                Spacer(Modifier.height(4.dp))
                                OutlinedButton(
                                    onClick = {
                                        tempAppUri.value = null
                                        tempAppIsGif.value = false
                                        tempAppBrightness.value = 0f
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) { Text(stringResource(R.string.settings_quitar), fontWeight = FontWeight.Bold) }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val finalUri = if (tempAppMode.value == "custom") tempAppUri.value else null
                            if (finalUri == null) {
                                settingsRepo.removeBackgroundImage()
                                backgroundImageUri.value = null
                                settingsRepo.removePlayerGifUri()
                                playerGifUri.value = null
                            } else if (tempAppIsGif.value) {
                                settingsRepo.savePlayerGifUri(finalUri)
                                playerGifUri.value = finalUri
                                settingsRepo.removeBackgroundImage()
                                backgroundImageUri.value = null
                            } else {
                                settingsRepo.saveBackgroundImageUri(finalUri)
                                backgroundImageUri.value = finalUri
                                settingsRepo.removePlayerGifUri()
                                playerGifUri.value = null
                            }
                            settingsRepo.saveBackgroundBrightness(tempAppBrightness.value)
                            backgroundBrightness.value = tempAppBrightness.value
                            onBackgroundImageChanged()
                            showAppBgDialog.value = false
                        }
                    ) { Text(stringResource(R.string.action_save), fontWeight = FontWeight.Bold) }
                },
                dismissButton = {
                    TextButton(onClick = { showAppBgDialog.value = false }) { Text(stringResource(R.string.action_cancel), fontWeight = FontWeight.Bold) }
                }
            )
        }

        if (showMomentsColorDialog.value) {
            val tempMomentsMode = remember { mutableStateOf(momentsColorModePref.value) }
            val tempMomentsHex = remember { mutableStateOf(momentsCustomColorHexPref.value) }
            AlertDialog(
                onDismissRequest = { showMomentsColorDialog.value = false },
                title = { Text(stringResource(R.string.settings_color_de_momentos), fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text(
                            stringResource(R.string.settings_color_de_las_franjas_que_marcan_tu),
                            fontSize = 12.sp,
                            color = mediumEmphasis,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        listOf("Adaptativo", "Personalizado", com.music.musicflame.ui.theme.COLOR_MODE_RAINBOW).forEach { colorOption ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { tempMomentsMode.value = colorOption }
                                    .padding(vertical = 8.dp)
                            ) {
                                RadioButton(
                                    selected = tempMomentsMode.value == colorOption,
                                    onClick = { tempMomentsMode.value = colorOption }
                                )
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(settingsOptionLabel(colorOption), fontSize = 14.sp)
                                    Text(
                                        when (colorOption) {
                                            "Adaptativo" -> stringResource(R.string.settings_usa_el_color_del_tema_material_you)
                                            "Personalizado" -> stringResource(R.string.settings_elige_tu_propio_color)
                                            else -> stringResource(R.string.settings_colores_del_espectro_en_movimiento)
                                        },
                                        fontSize = 11.sp,
                                        color = mediumEmphasis
                                    )
                                }
                            }
                        }

                        if (tempMomentsMode.value == "Personalizado") {
                            Spacer(Modifier.height(4.dp))
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
                                    val isSelected = tempMomentsHex.value.equals(hex, ignoreCase = true)
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
                                            .clickable { tempMomentsHex.value = hex }
                                    )
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            OutlinedTextField(
                                value = tempMomentsHex.value,
                                onValueChange = { tempMomentsHex.value = it },
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
                                        .background(com.music.musicflame.ui.theme.parseCustomTextColor(tempMomentsHex.value))
                                        .border(1.dp, Color.Gray, RoundedCornerShape(6.dp))
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.settings_vista_previa), fontSize = 12.sp)
                            }
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

        if (showTogetherColorDialog.value) {
            val tempTogetherMode = remember { mutableStateOf(togetherColorModePref.value) }
            val tempTogetherHex = remember { mutableStateOf(togetherCustomColorHexPref.value) }
            AlertDialog(
                onDismissRequest = { showTogetherColorDialog.value = false },
                title = { Text(stringResource(R.string.settings_color_de_en_compania), fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text(
                            stringResource(R.string.settings_color_de_acento_de_la_pantalla_en),
                            fontSize = 12.sp,
                            color = mediumEmphasis,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        listOf("Adaptativo", "Personalizado", com.music.musicflame.ui.theme.COLOR_MODE_RAINBOW).forEach { colorOption ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { tempTogetherMode.value = colorOption }
                                    .padding(vertical = 8.dp)
                            ) {
                                RadioButton(
                                    selected = tempTogetherMode.value == colorOption,
                                    onClick = { tempTogetherMode.value = colorOption }
                                )
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(settingsOptionLabel(colorOption), fontSize = 14.sp)
                                    Text(
                                        when (colorOption) {
                                            "Adaptativo" -> stringResource(R.string.settings_usa_el_color_del_tema_material_you)
                                            "Personalizado" -> stringResource(R.string.settings_elige_tu_propio_color)
                                            else -> stringResource(R.string.settings_colores_del_espectro_en_movimiento)
                                        },
                                        fontSize = 11.sp,
                                        color = mediumEmphasis
                                    )
                                }
                            }
                        }

                        if (tempTogetherMode.value == "Personalizado") {
                            Spacer(Modifier.height(4.dp))
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
                                    val isSelected = tempTogetherHex.value.equals(hex, ignoreCase = true)
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
                                            .clickable { tempTogetherHex.value = hex }
                                    )
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            OutlinedTextField(
                                value = tempTogetherHex.value,
                                onValueChange = { tempTogetherHex.value = it },
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
                                        .background(com.music.musicflame.ui.theme.parseCustomTextColor(tempTogetherHex.value))
                                        .border(1.dp, Color.Gray, RoundedCornerShape(6.dp))
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.settings_vista_previa), fontSize = 12.sp)
                            }
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
                title = { Text(stringResource(R.string.settings_color_del_texto_de_la_letra), fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text(
                            stringResource(R.string.settings_se_aplica_al_texto_de_la_letra_sin),
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
                                    .background(com.music.musicflame.ui.theme.parseCustomTextColor(tempLyricsCustomHex.value))
                                    .border(1.dp, Color.Gray, RoundedCornerShape(6.dp))
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.settings_vista_previa), fontSize = 12.sp)
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
                    }) { Text(stringResource(R.string.action_save), fontWeight = FontWeight.Bold) }
                },
                dismissButton = { TextButton(onClick = revertIfNotConfirmed) { Text(stringResource(R.string.action_cancel), fontWeight = FontWeight.Bold) } }
            )
        }

        if (showNowPlayingColorDialog.value) {
            val tempNowPlayingColorMode = remember { mutableStateOf(nowPlayingColorModePref.value) }
            val tempNowPlayingCustomHex = remember { mutableStateOf(nowPlayingCustomColorHexPref.value) }
            AlertDialog(
                onDismissRequest = { showNowPlayingColorDialog.value = false },
                title = { Text(stringResource(R.string.settings_color_del_now_playing), fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text(
                            stringResource(R.string.settings_aplica_al_indicador_animado_de_rep),
                            fontSize = 12.sp,
                            color = mediumEmphasis,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        listOf("Adaptativo", "Personalizado", com.music.musicflame.ui.theme.COLOR_MODE_RAINBOW).forEach { colorOption ->
                            // Personalizado y Arcoíris son de pago acá; Adaptativo es gratis.
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
                                        Text(settingsOptionLabel(colorOption), fontSize = 14.sp)
                                        if (locked) {
                                            Spacer(Modifier.width(6.dp))
                                            Icon(Icons.Filled.Lock, contentDescription = stringResource(R.string.settings_bloqueado), modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.error)
                                            Spacer(Modifier.width(2.dp))
                                            Text("$5 MXN", fontSize = 10.sp, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    if (colorOption == "Adaptativo") {
                                        Text(
                                            stringResource(R.string.settings_blanco_o_negro_segun_el_fondo_como),
                                            fontSize = 11.sp,
                                            color = mediumEmphasis
                                        )
                                    } else if (colorOption == com.music.musicflame.ui.theme.COLOR_MODE_RAINBOW) {
                                        Text(
                                            stringResource(R.string.settings_colores_del_espectro_en_movimiento),
                                            fontSize = 11.sp,
                                            color = mediumEmphasis
                                        )
                                    }
                                }
                            }
                        }

                        if (tempNowPlayingColorMode.value == "Personalizado" && unlockedIds.contains("now_playing_custom")) {
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
                                        .background(com.music.musicflame.ui.theme.parseCustomTextColor(tempNowPlayingCustomHex.value))
                                        .border(1.dp, Color.Gray, RoundedCornerShape(6.dp))
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.settings_vista_previa), fontSize = 12.sp)
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
                    }) { Text(stringResource(R.string.action_save), fontWeight = FontWeight.Bold) }
                },
                dismissButton = { TextButton(onClick = { showNowPlayingColorDialog.value = false }) { Text(stringResource(R.string.action_cancel), fontWeight = FontWeight.Bold) } }
            )
        }

        if (showAnomalyScanDialog.value) {
            AnomalyScanDialog(
                settingsRepo = settingsRepo,
                playerManager = playerManager,
                onDismiss = { showAnomalyScanDialog.value = false }
            )
        }

        if (showProEqualizerDialog.value) {
            ProEqualizerDialog(onDismiss = { showProEqualizerDialog.value = false })
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
            // MODO PRO EXCLUSIVO: con Pro desbloqueado (o cuenta dueña) y este modo activo (default),
            // MusicPlaybackService APAGA el Equalizer nativo de 5 bandas por completo, así que los
            // sliders de este diálogo no se oían. Aquí se muestra el estado y se puede apagar.
            val proEqAvailable = com.music.musicflame.data.ProStatusHolder.isItemUnlocked("pro_eq_10band")
            val proExclusiveOn = remember { mutableStateOf(sharedPrefs.getBoolean("pro_eq_exclusive", true)) }
            val showSaveCustomDialog = remember { mutableStateOf(false) }
            val customPresetName = remember { mutableStateOf("") }
            val customNamesString = sharedPrefs.getString("custom_preset_names", "") ?: ""
            val customPresetsList = if (customNamesString.isNotEmpty()) customNamesString.split(",") else emptyList()
            val basePresets = listOf("Flat", "Rock", "Pop", "Hip hop", "Jazz", "Classical", "Electronico", "Refuerzo de graves", "Refuerzo de agudos", "Vocales") + EqPresets.FREE_NAMES + "Customizar"
            val allPresets = basePresets + customPresetsList

            // ARREGLO GRATIS: antes las 5 frecuencias (60/230/910/3600/14000 Hz) estaban
            // hardcodeadas en la UI asumiendo que TODOS los celulares traen exactamente
            // 5 bandas en esas frecuencias. Muchos Samsung/Xiaomi con DSP propio reportan
            // numberOfBands distinto, así que aquí se consulta el ecualizador REAL del
            // dispositivo (con audioSessionId=0, que solo sirve para leer capacidades, sin
            // necesidad de que haya audio sonando) para saber cuántas bandas hay de verdad
            // y a qué frecuencia responde cada una. Si algo falla o el dispositivo no
            // reporta nada usable, se cae de vuelta a los valores de siempre para no romper
            // la app.
            val fallbackFreqsHz = listOf(60, 230, 910, 3600, 14000)
            val realDeviceBandFreqsHz = remember {
                try {
                    val probe = Equalizer(0, 0)
                    val realBandCount = probe.numberOfBands.toInt()
                    val freqs = (0 until realBandCount).map { i -> probe.getCenterFreq(i.toShort()) / 1000 }
                    probe.release()
                    freqs
                } catch (e: Exception) {
                    emptyList<Int>()
                }
            }
            // Cuántos sliders tiene sentido mostrar/controlar en ESTE celular: nunca más de
            // 5 (nuestros presets son de 5 bandas), pero si el hardware real solo tiene
            // menos, no mostramos sliders para bandas que no existen.
            val eqBandCount = if (realDeviceBandFreqsHz.isNotEmpty()) minOf(5, realDeviceBandFreqsHz.size) else 5
            val eqFreqsHz = List(5) { i ->
                if (i < realDeviceBandFreqsHz.size) realDeviceBandFreqsHz[i] else fallbackFreqsHz[i]
            }

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
                            IconButton(onClick = { showEqualizerDialog.value = false }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_close), tint = MaterialTheme.colorScheme.onBackground) }
                            Text(stringResource(R.string.settings_studio_pro_eq), fontSize = 20.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onBackground)

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
                                Toast.makeText(context, context.getString(R.string.settings_audio_pro_activado), Toast.LENGTH_SHORT).show()
                            }) { Text(stringResource(R.string.settings_aplicar), fontWeight = FontWeight.Bold) }
                        }

                        LazyColumn(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            item { Spacer(Modifier.height(8.dp)) }
                            if (proEqAvailable) {
                                item {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (proExclusiveOn.value) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainer
                                        )
                                    ) {
                                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    if (proExclusiveOn.value) stringResource(R.string.settings_modo_pro_exclusivo_activo) else stringResource(R.string.settings_modo_pro_exclusivo_apagado),
                                                    fontWeight = FontWeight.Black, fontSize = 14.sp
                                                )
                                                Text(
                                                    if (proExclusiveOn.value) stringResource(R.string.settings_este_ecualizador_de_5_bandas_esta)
                                                    else stringResource(R.string.settings_este_ecualizador_de_5_bandas_suena),
                                                    fontSize = 12.sp
                                                )
                                            }
                                            Switch(
                                                checked = proExclusiveOn.value,
                                                onCheckedChange = { on ->
                                                    proExclusiveOn.value = on
                                                    sharedPrefs.edit().putBoolean("pro_eq_exclusive", on).apply()
                                                    // Sin extras: el servicio solo relee el estado y reaplica.
                                                    val sync = Intent("com.music.musicflame.UPDATE_EQ")
                                                    sync.setPackage(context.packageName)
                                                    context.sendBroadcast(sync)
                                                }
                                            )
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
                                            Text(text = preset, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp), color = if (tempPreset.value == preset) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.Bold)
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
                                            // Etiquetas armadas a partir de eqFreqsHz (real del dispositivo cuando se pudo
                                            // leer, o el valor de siempre como respaldo) en vez de un texto fijo.
                                            for (i in 0 until eqBandCount) {
                                                val hzValue = eqFreqsHz[i]
                                                val label = if (viewKHz.value) {
                                                    String.format("%.2f", hzValue / 1000f)
                                                } else {
                                                    hzValue.toString()
                                                }
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                                        sharedPrefs.edit()
                                            .putString("preset_${name}_bands", bandsStr)
                                            .putFloat("preset_${name}_bass", tempBass.value)
                                            .putFloat("preset_${name}_virt", tempVirtualizer.value)
                                            .putFloat("preset_${name}_loud", tempLoudness.value)
                                            .putInt("preset_${name}_reverb", tempReverb.value)
                                            .apply()

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
}

@Composable
private fun DeviceInfoCard(
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
private fun DeviceInfoDetailRow(label: String, value: String, mediumEmphasis: Color, highEmphasis: Color) {
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
private fun settingsOptionLabel(option: String): String = when (option) {
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

/** Mismo criterio: las secciones de Ajustes se identifican por su nombre en español; esto traduce el título visible. */
@Composable
private fun settingsSectionTitle(key: String): String = when (key) {
    "Hogar de Shimuro" -> stringResource(R.string.section_shimuro_home)
    "Cuenta" -> stringResource(R.string.section_account)
    "Apariencia" -> stringResource(R.string.section_appearance)
    "Canciones" -> stringResource(R.string.section_songs)
    "Copia de seguridad" -> stringResource(R.string.section_backup)
    "Especificaciones" -> stringResource(R.string.section_specs)
    "Pagos (opcional)" -> stringResource(R.string.section_payments)
    "Aviso de Uso" -> stringResource(R.string.section_usage_notice)
    else -> key
}
