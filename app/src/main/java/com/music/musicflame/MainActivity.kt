package com.music.musicflame

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build.VERSION.SDK_INT
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.music.musicflame.data.*
import com.music.musicflame.navigation.Screen
import com.music.musicflame.navigation.bottomNavItems
import com.music.musicflame.ui.screens.*
import com.music.musicflame.ui.theme.MusicFlameTheme
import com.music.musicflame.ui.theme.LocalAppTextColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import com.music.musicflame.ui.components.YoutubePlayerScreen
import com.music.musicflame.ui.screens.onboarding.OnboardingScreen

// ⚠️ Reemplaza esto con tu "Web Client ID" de Google Cloud Console.
private const val WEB_CLIENT_ID = "176181653925-etnugbpe1mqhu1gl3lu1njbu9iihcn1k.apps.googleusercontent.com"

val LocalUseRoundCorners = compositionLocalOf { true }

// --- FORMA DE LA CARÁTULA (configurable desde Ajustes > Apariencia) ---
enum class AlbumArtShapeType {
    SQUARE, CIRCLE, HEXAGON, VINYL, SQUIRCLE
}
val LocalAlbumArtShape = compositionLocalOf { AlbumArtShapeType.SQUARE }

// --- CANTIDAD DE CARÁTULAS POR RENGLÓN EN ÁLBUMES (configurable desde Ajustes > Apariencia) ---
val LocalAlbumGridColumns = compositionLocalOf { 2 }

enum class SearchMode {
    LOCAL,
    YOUTUBE
}

class MainActivity : ComponentActivity() {
    private lateinit var playerManager: MusicPlayerManager
    private lateinit var playlistRepo: PlaylistRepository
    private lateinit var favoritesRepo: FavoritesRepository
    private lateinit var songCustomizationRepo: SongCustomizationRepository
    private var selectedPlaylistForCover: String? = null

    private val importM3ULauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { playlistRepo.importFromM3U(this, it) }
    }

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            try {
                contentResolver.takePersistableUriPermission(it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) {}

            selectedPlaylistForCover?.let { playlistId ->
                if (playlistId == "favorites") {
                    favoritesRepo.saveCoverUri(it.toString())
                } else {
                    playlistRepo.updatePlaylistCover(playlistId, it.toString())
                }
                selectedPlaylistForCover = null
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        // --- Splash dinámico: el icono del splash sigue al icono de app elegido ---
        // Debe ir ANTES de super.onCreate() para que el SplashScreen tome el tema correcto.
        val selectedIconKey = SettingsRepository(this).getSelectedAppIcon()
        setTheme(AppIconManager.splashThemeFor(selectedIconKey))

        super.onCreate(savedInstanceState)

        // Animación de salida "pulso de llama": el icono late 2 veces (como
        // un parpadeo de fuego) y luego crece y se desvanece.
        splashScreen.setOnExitAnimationListener { splashScreenView ->
            val iconView = splashScreenView.iconView

            val flamePulse = android.animation.ObjectAnimator.ofPropertyValuesHolder(
                iconView,
                android.animation.PropertyValuesHolder.ofFloat(
                    android.view.View.SCALE_X, 1f, 1.2f, 0.96f, 1.12f, 1f
                ),
                android.animation.PropertyValuesHolder.ofFloat(
                    android.view.View.SCALE_Y, 1f, 1.2f, 0.96f, 1.12f, 1f
                )
            ).apply {
                duration = 190L
                interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            }

            val growAndFade = android.animation.ObjectAnimator.ofPropertyValuesHolder(
                iconView,
                android.animation.PropertyValuesHolder.ofFloat(android.view.View.SCALE_X, 1f, 1.3f),
                android.animation.PropertyValuesHolder.ofFloat(android.view.View.SCALE_Y, 1f, 1.3f),
                android.animation.PropertyValuesHolder.ofFloat(android.view.View.ALPHA, 1f, 0f)
            ).apply {
                duration = 110L
                interpolator = android.view.animation.AccelerateInterpolator()
            }

            android.animation.AnimatorSet().apply {
                playSequentially(flamePulse, growAndFade)
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        splashScreenView.remove()
                    }
                })
                start()
            }
        }

        // --- Anti-tampering: verificación de firma ---
        if (!AppConfig.isEnvironmentReady(this)) {
            finishAffinity()
            return
        }

        playerManager = MusicPlayerManager(this)
        playlistRepo = PlaylistRepository(this)
        favoritesRepo = FavoritesRepository(this)
        songCustomizationRepo = SongCustomizationRepository(this)

        setContent {
            MusicFlameTheme {
                val context = this@MainActivity

                // --- Lógica Google Sign-In ---
                var isUserLoggedIn by remember { mutableStateOf(false) }
                var userName by remember { mutableStateOf<String?>(null) }
                var userPhotoUrl by remember { mutableStateOf<String?>(null) }

                // --- Google Sign-In (sin Firebase) ---
                var isYouTubeLinked by remember { mutableStateOf(false) }
                var isDriveLinked by remember { mutableStateOf(false) }

                val driveScope = remember { Scope("https://www.googleapis.com/auth/drive.file") }
                val youtubeScope = remember { Scope("https://www.googleapis.com/auth/youtube.readonly") }
                val gso = remember {
                    GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestEmail()
                        .requestProfile()
                        .requestIdToken(WEB_CLIENT_ID)
                        .requestScopes(driveScope)
                        .build()
                }
                val googleSignInClient = remember { GoogleSignIn.getClient(context, gso) }

                LaunchedEffect(Unit) {
                    val account = GoogleSignIn.getLastSignedInAccount(context)
                    if (account != null) {
                        isUserLoggedIn = true
                        userName = account.displayName
                        userPhotoUrl = account.photoUrl?.toString()
                        isYouTubeLinked = GoogleSignIn.hasPermissions(account, youtubeScope)
                        isDriveLinked = GoogleSignIn.hasPermissions(account, driveScope)
                    }
                }

                val signInLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                    val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                    try {
                        val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
                        isUserLoggedIn = true
                        userName = account.displayName
                        userPhotoUrl = account.photoUrl?.toString()
                        isYouTubeLinked = GoogleSignIn.hasPermissions(account, youtubeScope)
                        isDriveLinked = GoogleSignIn.hasPermissions(account, driveScope)
                    } catch (e: Exception) {
                        Toast.makeText(context, "Error al iniciar sesión", Toast.LENGTH_SHORT).show()
                    }
                }

                val audioPermission = if (SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE

                val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
                    if (!isGranted) Toast.makeText(context, "Se requiere permiso para leer tu música", Toast.LENGTH_LONG).show()
                }

                LaunchedEffect(Unit) {
                    val hasPermission = ContextCompat.checkSelfPermission(context, audioPermission) == PackageManager.PERMISSION_GRANTED
                    if (!hasPermission) permissionLauncher.launch(audioPermission)
                }

                val settingsRepo = remember { SettingsRepository(context) }
                val trashRepo = remember { TrashRepository(context) }

                // --- Onboarding de primer uso: se muestra una sola vez ---
                var showOnboarding by remember { mutableStateOf(!settingsRepo.isOnboardingCompleted()) }

                // --- SISTEMA DE ACTUALIZACIONES ---
                val updatePreferences = remember { UpdatePreferences(context) }
                val ignoredVersion by updatePreferences.ignoredVersionFlow.collectAsState(initial = null)

                var showUpdateDialog by remember { mutableStateOf(false) }
                var latestVersionTag by remember { mutableStateOf("") }
                var latestReleaseUrl by remember { mutableStateOf("") }
                val coroutineScope = rememberCoroutineScope()

                fun checkForUpdates(isManualCheck: Boolean) {
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            val url = java.net.URL("https://api.github.com/repos/ShimuroNaga/MusicFlame/releases/latest")
                            val connection = url.openConnection() as java.net.HttpURLConnection
                            connection.requestMethod = "GET"
                            connection.setRequestProperty("Accept", "application/vnd.github.v3+json")

                            if (connection.responseCode == 200) {
                                val response = connection.inputStream.bufferedReader().use { it.readText() }

                                val tagMatch = "\"tag_name\"\\s*:\\s*\"([^\"]+)\"".toRegex().find(response)
                                val apkMatch = "\"browser_download_url\"\\s*:\\s*\"([^\"]+\\.apk)\"".toRegex().find(response)

                                if (tagMatch != null) {
                                    val fetchedVersion = tagMatch.groupValues[1]
                                    val downloadUrl = apkMatch?.groupValues?.get(1) ?: ""

                                    val versionName = context.packageManager.getPackageInfo(context.packageName, 0).versionName
                                    val currentVersion = "v$versionName"

                                    withContext(Dispatchers.Main) {
                                        if (fetchedVersion > currentVersion) {
                                            if (isManualCheck || ignoredVersion != fetchedVersion) {
                                                latestVersionTag = fetchedVersion
                                                latestReleaseUrl = downloadUrl
                                                showUpdateDialog = true
                                            } else if (isManualCheck) {
                                                Toast.makeText(context, "Ya tienes la última versión", Toast.LENGTH_SHORT).show()
                                            }
                                        } else if (isManualCheck) {
                                            Toast.makeText(context, "Estás al día", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            if (isManualCheck) {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Error de red al buscar actualizaciones", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                }

                LaunchedEffect(Unit) {
                    checkForUpdates(isManualCheck = false)
                }
                // ----------------------------------

                val backgroundImageUri = remember { mutableStateOf(settingsRepo.getBackgroundImageUri()) }
                val playerGifUri = remember { mutableStateOf(settingsRepo.getPlayerGifUri()) }
                val bgBrightness = remember { mutableFloatStateOf(settingsRepo.getBackgroundBrightness()) }
                val hasBackgroundImage = backgroundImageUri.value != null || playerGifUri.value != null
                val useRoundCornersState = remember { mutableStateOf(settingsRepo.getUseRoundCorners()) }
                val albumArtShapeState = remember { mutableStateOf(settingsRepo.getAlbumArtShape()) }
                val albumGridColumnsState = remember { mutableStateOf(settingsRepo.getAlbumGridColumns()) }

                val pagerState = rememberPagerState(pageCount = { bottomNavItems.size })

                val currentSong by playerManager.currentSong
                val isPlaying by playerManager.isPlayingState
                var songList by remember { mutableStateOf<List<Song>>(emptyList()) }

                // FIX: cuando la app se reabre (Activity recreada tras salir/segundo plano),
                // currentSong se restaura solo desde el MediaSession que sigue sonando, pero
                // songList vuelve a emptyList() porque solo se llena cuando tocas una canción
                // manualmente. Ese desfase dejaba al HorizontalPager del reproductor expandido
                // con 0 páginas (carátula invisible) y provocaba el cierre de la app al saltar
                // de canción, porque el pager quedaba en un estado inconsistente. Aquí
                // reconstruimos songList con la librería completa en cuanto detectamos el desfase.
                LaunchedEffect(currentSong?.id) {
                    if (currentSong != null && songList.isEmpty()) {
                        val restoredSongs = withContext(Dispatchers.IO) {
                            val trashedIds = trashRepo.getTrash().map { it.song.id }
                            loadSongsFromDevice(context).filter { it.id !in trashedIds }
                        }
                        songList = restoredSongs
                    }
                }

                var songToAddToPlaylist by remember { mutableStateOf<Song?>(null) }
                var showAddToPlaylist by remember { mutableStateOf(false) }

                var selectedPlaylist by remember { mutableStateOf<Playlist?>(null) }
                var selectedPlaylistIsFavorites by remember { mutableStateOf(false) }
                var selectedAlbum by remember { mutableStateOf<com.music.musicflame.data.Album?>(null) }
                var showSettings by remember { mutableStateOf(false) }

                // --- Selección múltiple de canciones (playlists, drive, etc.) ---

                var isSearchActive by remember { mutableStateOf(false) }
                var searchQuery by remember { mutableStateOf("") }
                var searchMode by remember { mutableStateOf(SearchMode.LOCAL) }
                var showSearchModeMenu by remember { mutableStateOf(false) }

                var showFullScreenPlayer by remember { mutableStateOf(false) }
                var isMiniPlayerVisible by remember { mutableStateOf(true) }

                // --- Variables de Estado para Favoritos y Drive ---
                var favoriteIds by remember { mutableStateOf(favoritesRepo.getAllFavoriteIds()) }
                var syncedFileNames by remember { mutableStateOf(setOf<String>()) } // <-- NUEVO

                val selectedSongs = remember { mutableStateListOf<Song>() }
                val selectedPlaylists = remember { mutableStateListOf<Playlist>() }

                // Permite entrar en modo selección de playlists tocando un botón, sin mantener presionado
                var manualPlaylistSelectionMode by remember { mutableStateOf(false) }

                // Permite entrar en modo selección tocando un botón, sin necesidad de mantener presionado
                var manualSongSelectionMode by remember { mutableStateOf(false) }

                val isSongSelectionMode = selectedSongs.isNotEmpty() || manualSongSelectionMode
                val isPlaylistSelectionMode = selectedPlaylists.isNotEmpty() || manualPlaylistSelectionMode
                val isAnySelectionMode = isSongSelectionMode || isPlaylistSelectionMode

                var totalSongsOnDevice by remember { mutableIntStateOf(0) }

                var showSelectionMenu by remember { mutableStateOf(false) }
                var showPlaylistSelectionMenu by remember { mutableStateOf(false) }
                var showMultiPlaylistDialog by remember { mutableStateOf(false) }
                var showCreatePlaylistFromSelection by remember { mutableStateOf(false) }
                var newPlaylistNameFromSelection by remember { mutableStateOf("") }
                var showMultiDeleteDialog by remember { mutableStateOf(false) }
                var showDeletePlaylistsDialog by remember { mutableStateOf(false) }

                // --- Editar carátula (imagen/GIF) y nombre de canción(es), desde la selección múltiple ---
                var showEditSongDialog by remember { mutableStateOf(false) }
                var songsPatchTrigger by remember { mutableIntStateOf(0) }
                var pendingSongPatches by remember { mutableStateOf<List<SongEditPatch>>(emptyList()) }

                // --- Refresca el icono de "letra disponible" en la lista cuando cambia
                // desde el reproductor a pantalla completa (borrar, encontrar, insertar). ---
                var lyricsRefreshTrigger by remember { mutableIntStateOf(0) }


                var youtubeVideoId by remember { mutableStateOf<String?>(null) }
                var youtubeRecommendedSongs by remember { mutableStateOf<List<Song>>(emptyList()) }

                fun fetchInitialYoutubeVideos() {
                    coroutineScope.launch {
                        try {
                            val response = com.music.musicflame.api.RetrofitClient.instance.getPopularMusicVideos()
                            youtubeRecommendedSongs = response.items.mapNotNull { item ->
                                val realVideoId = item.id?.videoId ?: (item.id as? String) ?: return@mapNotNull null
                                Song(
                                    id = realVideoId.hashCode().toLong(),
                                    title = item.snippet?.title ?: "Sin título",
                                    artist = item.snippet?.channelTitle ?: "Desconocido",
                                    albumArtUri = item.snippet?.thumbnails?.high?.url ?: "",
                                    path = "",
                                    dateAdded = 0L,
                                    duration = 0L,
                                    youtubeVideoId = realVideoId
                                )
                            }
                        } catch (e: Exception) {
                            if (e is kotlinx.coroutines.CancellationException) throw e
                            android.util.Log.e("YOUTUBE_API", "Error al cargar recomendados", e)
                        }
                    }
                }

                LaunchedEffect(searchMode, isYouTubeLinked) {
                    if (searchMode == SearchMode.YOUTUBE && youtubeRecommendedSongs.isEmpty()) {
                        fetchInitialYoutubeVideos()
                    }
                }

                LaunchedEffect(Unit) {
                    totalSongsOnDevice = loadSongsFromDevice(context).size
                }

                // --- NUEVO: Efecto para cargar los archivos de Drive ---
                LaunchedEffect(isDriveLinked) {
                    if (isDriveLinked) {
                        val account = GoogleSignIn.getLastSignedInAccount(context)
                        if (account != null) {
                            try {
                                val driveRepo = DriveRepository(context)
                                val folderId = driveRepo.getOrCreateAppFolder(account)
                                if (folderId != null) {
                                    val files = driveRepo.getSongsFromFolder(account, folderId)
                                    syncedFileNames = files.map { it.name }.toSet()
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                }
                // -------------------------------------------------------

                LaunchedEffect(pagerState.currentPage) {
                    val currentScreen = bottomNavItems[pagerState.currentPage]
                    isSearchActive = false
                    searchQuery = ""

                    selectedSongs.clear(); manualSongSelectionMode = false
                    selectedPlaylists.clear(); manualPlaylistSelectionMode = false
                    if (currentScreen != Screen.Playlists) selectedPlaylist = null
                    if (currentScreen != Screen.Album) selectedAlbum = null
                }

                CompositionLocalProvider(LocalUseRoundCorners provides useRoundCornersState.value, LocalAlbumArtShape provides albumArtShapeState.value, LocalAlbumGridColumns provides albumGridColumnsState.value) {
                    BackHandler(enabled = showFullScreenPlayer || isAnySelectionMode) {
                        if (showFullScreenPlayer) showFullScreenPlayer = false
                        else {
                            selectedSongs.clear(); manualSongSelectionMode = false
                            selectedPlaylists.clear(); manualPlaylistSelectionMode = false
                        }
                    }

                    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {

                        if (hasBackgroundImage) {
                            if (playerGifUri.value != null) {
                                AsyncImage(model = ImageRequest.Builder(context).data(playerGifUri.value).decoderFactory(if (SDK_INT >= 28) ImageDecoderDecoder.Factory() else GifDecoder.Factory()).build(), contentDescription = "Fondo", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            } else {
                                AsyncImage(model = backgroundImageUri.value, contentDescription = "Fondo", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            }

                            val brightness = bgBrightness.floatValue
                            if (brightness != 0f) Box(modifier = Modifier.fillMaxSize().background(if (brightness < 0) Color.Black.copy(alpha = abs(brightness)) else Color.White.copy(alpha = brightness)))
                            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.2f)))
                        }

                        // Mostrar cuadro de nueva versión si el estado es true
                        if (showUpdateDialog) {
                            UpdateDialog(
                                newVersion = latestVersionTag,
                                hasBackgroundImage = hasBackgroundImage,
                                onConfirm = {
                                    showUpdateDialog = false
                                    downloadApk(context, latestReleaseUrl, "MusicFlame_$latestVersionTag.apk")
                                },
                                onDismiss = {
                                    showUpdateDialog = false
                                    coroutineScope.launch {
                                        updatePreferences.saveIgnoredVersion(latestVersionTag)
                                    }
                                }
                            )
                        }

                        Scaffold(
                            modifier = Modifier.fillMaxSize(),
                            containerColor = Color.Transparent,
                            topBar = {
                                if (isSongSelectionMode) {
                                    TopAppBar(
                                        title = { Text("${selectedSongs.size} / $totalSongsOnDevice", fontWeight = FontWeight.Bold) },
                                        navigationIcon = {
                                            IconButton(onClick = { selectedSongs.clear(); manualSongSelectionMode = false }) { Icon(Icons.Filled.Close, "Cancelar Selección") }
                                        },
                                        actions = {
                                            IconButton(onClick = { showSelectionMenu = true }) { Icon(Icons.Filled.MoreVert, "Opciones") }

                                            DropdownMenu(expanded = showSelectionMenu, onDismissRequest = { showSelectionMenu = false }) {
                                                DropdownMenuItem(
                                                    text = { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.SelectAll, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(8.dp)); Text("Seleccionar todos") } },
                                                    onClick = {
                                                        showSelectionMenu = false
                                                        val trashedIds = trashRepo.getTrash().map { it.song.id }
                                                        val allSongs = loadSongsFromDevice(context).filter { it.id !in trashedIds }
                                                        selectedSongs.clear()
                                                        selectedSongs.addAll(allSongs)
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.PlaylistAdd, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(8.dp)); Text("Añadir a playlist") } },
                                                    onClick = { showSelectionMenu = false; showMultiPlaylistDialog = true }
                                                )
                                                run {
                                                    val allSelectedAreFavorites = selectedSongs.isNotEmpty() && selectedSongs.all { favoriteIds.contains(it.id) }
                                                    DropdownMenuItem(
                                                        text = {
                                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                                Icon(
                                                                    if (allSelectedAreFavorites) Icons.Filled.HeartBroken else Icons.Filled.Favorite,
                                                                    null,
                                                                    modifier = Modifier.size(20.dp),
                                                                    tint = Color(0xFFE91E63)
                                                                )
                                                                Spacer(Modifier.width(8.dp))
                                                                Text(if (allSelectedAreFavorites) "Quitar de favoritos" else "Añadir a favoritos")
                                                            }
                                                        },
                                                        onClick = {
                                                            showSelectionMenu = false
                                                            if (allSelectedAreFavorites) {
                                                                selectedSongs.forEach { song -> if (favoriteIds.contains(song.id)) favoritesRepo.toggleFavorite(song.id) }
                                                            } else {
                                                                selectedSongs.forEach { song -> if (!favoriteIds.contains(song.id)) favoritesRepo.toggleFavorite(song.id) }
                                                            }
                                                            favoriteIds = favoritesRepo.getAllFavoriteIds()
                                                            val intent = android.content.Intent("com.music.musicflame.FAVORITES_CHANGED")
                                                            intent.setPackage(packageName)
                                                            sendBroadcast(intent)
                                                            Toast.makeText(context, if (allSelectedAreFavorites) "${selectedSongs.size} quitadas de Favoritos" else "${selectedSongs.size} añadidas a Favoritos", Toast.LENGTH_SHORT).show()
                                                            selectedSongs.clear(); manualSongSelectionMode = false
                                                        }
                                                    )
                                                }
                                                // --- OPCIÓN SUBIR A GOOGLE DRIVE ---
                                                DropdownMenuItem(
                                                    text = {
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Icon(Icons.Filled.CloudUpload, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                                                            Spacer(Modifier.width(8.dp))
                                                            Text("Subir a Google Drive")
                                                        }
                                                    },
                                                    onClick = {
                                                        showSelectionMenu = false
                                                        val account = GoogleSignIn.getLastSignedInAccount(context)

                                                        if (account != null && isDriveLinked) {
                                                            coroutineScope.launch {
                                                                Toast.makeText(context, "Subiendo ${selectedSongs.size} canciones a la nube...", Toast.LENGTH_SHORT).show()

                                                                val driveRepo = DriveRepository(context)
                                                                val folderId = driveRepo.getOrCreateAppFolder(account)

                                                                if (folderId != null) {
                                                                    var successCount = 0
                                                                    selectedSongs.forEach { song ->
                                                                        val success = driveRepo.uploadSong(account, folderId, song.path)
                                                                        if (success) successCount++
                                                                    }

                                                                    withContext(Dispatchers.Main) {
                                                                        Toast.makeText(context, "$successCount de ${selectedSongs.size} canciones subidas con éxito", Toast.LENGTH_LONG).show()
                                                                        // Actualizamos la lista visual instantáneamente
                                                                        val files = driveRepo.getSongsFromFolder(account, folderId)
                                                                        syncedFileNames = files.map { it.name }.toSet()
                                                                    }
                                                                } else {
                                                                    withContext(Dispatchers.Main) {
                                                                        Toast.makeText(context, "Error al acceder a la carpeta de Drive", Toast.LENGTH_SHORT).show()
                                                                    }
                                                                }
                                                                selectedSongs.clear(); manualSongSelectionMode = false
                                                            }
                                                        } else {
                                                            Toast.makeText(context, "Por favor, vincula Google Drive en CONFIGURACION primero", Toast.LENGTH_LONG).show()
                                                        }
                                                    }
                                                )
                                                // --- NUEVO: EDITAR CARÁTULA (imagen/GIF) Y NOMBRE ---
                                                if (selectedSongs.isNotEmpty()) {
                                                    DropdownMenuItem(
                                                        text = {
                                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                                Icon(Icons.Filled.Edit, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                                                                Spacer(Modifier.width(8.dp))
                                                                Text(if (selectedSongs.size == 1) "Editar carátula y nombre" else "Editar carátula de todas")
                                                            }
                                                        },
                                                        onClick = {
                                                            showSelectionMenu = false
                                                            showEditSongDialog = true
                                                        }
                                                    )
                                                }
                                                DropdownMenuItem(
                                                    text = { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.Delete, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error); Spacer(Modifier.width(8.dp)); Text("Mover a papelera") } },
                                                    onClick = { showSelectionMenu = false; showMultiDeleteDialog = true }
                                                )
                                            }
                                        },
                                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer, titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer, navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer, actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer)
                                    )
                                } else if (isPlaylistSelectionMode) {
                                    TopAppBar(
                                        title = { Text("${selectedPlaylists.size} seleccionadas", fontWeight = FontWeight.Bold) },
                                        navigationIcon = {
                                            IconButton(onClick = { selectedPlaylists.clear(); manualPlaylistSelectionMode = false }) { Icon(Icons.Filled.Close, "Cancelar Selección") }
                                        },
                                        actions = {
                                            IconButton(onClick = { showPlaylistSelectionMenu = true }) { Icon(Icons.Filled.MoreVert, "Opciones") }

                                            DropdownMenu(expanded = showPlaylistSelectionMenu, onDismissRequest = { showPlaylistSelectionMenu = false }) {
                                                DropdownMenuItem(
                                                    text = { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.SelectAll, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(8.dp)); Text("Seleccionar todos") } },
                                                    onClick = {
                                                        showPlaylistSelectionMenu = false
                                                        val favoritesPlaylist = Playlist("favorites", "Favoritos", favoriteIds.toList())
                                                        val allPlaylists = listOf(favoritesPlaylist) + playlistRepo.getPlaylists()
                                                        selectedPlaylists.clear()
                                                        selectedPlaylists.addAll(allPlaylists)
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.Download, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.secondary); Spacer(Modifier.width(8.dp)); Text("Exportar a M3U") } },
                                                    onClick = {
                                                        showPlaylistSelectionMenu = false
                                                        var successCount = 0
                                                        selectedPlaylists.forEach { playlist ->
                                                            val exportable = if (playlist.id == "favorites") playlist.copy(songIds = favoriteIds.toList()) else playlist
                                                            if (playlistRepo.exportToM3U(context, exportable)) successCount++
                                                        }
                                                        Toast.makeText(context, "$successCount de ${selectedPlaylists.size} exportadas a Descargas", Toast.LENGTH_SHORT).show()
                                                        selectedPlaylists.clear(); manualPlaylistSelectionMode = false
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Mover a papelera", color = MaterialTheme.colorScheme.error) } },
                                                    onClick = { showPlaylistSelectionMenu = false; showDeletePlaylistsDialog = true }
                                                )
                                            }
                                        },
                                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer, titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer, navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer, actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer)
                                    )
                                } else {
                                    CenterAlignedTopAppBar(
                                        title = {
                                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                                                AnimatedVisibility(visible = isSearchActive, enter = fadeIn(), exit = fadeOut()) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth().padding(end = 8.dp).height(42.dp).clip(RoundedCornerShape(if (useRoundCornersState.value) 24.dp else 0.dp)).background(if (hasBackgroundImage) Color.Black.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 12.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {

                                                        Box {
                                                            Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                modifier = Modifier
                                                                    .clickable { showSearchModeMenu = true }
                                                                    .padding(end = 8.dp)
                                                            ) {
                                                                Icon(
                                                                    imageVector = if (searchMode == SearchMode.LOCAL) Icons.Filled.PhoneAndroid else Icons.Filled.OndemandVideo,
                                                                    contentDescription = "Modo de búsqueda",
                                                                    modifier = Modifier.size(20.dp),
                                                                    tint = if (hasBackgroundImage) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.primary
                                                                )
                                                                Icon(
                                                                    Icons.Filled.ArrowDropDown,
                                                                    contentDescription = null,
                                                                    tint = if (hasBackgroundImage) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.primary
                                                                )
                                                            }

                                                            DropdownMenu(
                                                                expanded = showSearchModeMenu,
                                                                onDismissRequest = { showSearchModeMenu = false }
                                                            ) {
                                                                DropdownMenuItem(
                                                                    text = { Text("Dispositivo Local") },
                                                                    leadingIcon = { Icon(Icons.Filled.PhoneAndroid, contentDescription = null) },
                                                                    onClick = {
                                                                        searchMode = SearchMode.LOCAL
                                                                        showSearchModeMenu = false
                                                                    }
                                                                )
                                                                DropdownMenuItem(
                                                                    text = { Text("YouTube") },
                                                                    leadingIcon = { Icon(Icons.Filled.OndemandVideo, contentDescription = null) },
                                                                    onClick = {
                                                                        searchMode = SearchMode.YOUTUBE
                                                                        showSearchModeMenu = false
                                                                    }
                                                                )
                                                            }
                                                        }

                                                        Box(modifier = Modifier.weight(1f).padding(horizontal = 8.dp), contentAlignment = Alignment.CenterStart) {
                                                            if (searchQuery.isEmpty()) {
                                                                val hintText = if (searchMode == SearchMode.LOCAL) "Buscar en dispositivo..." else "Buscar en YouTube..."
                                                                Text(hintText, fontSize = 14.sp, color = LocalAppTextColor.current.copy(alpha = 0.5f))
                                                            }
                                                            BasicTextField(value = searchQuery, onValueChange = { searchQuery = it }, singleLine = true, textStyle = TextStyle(fontSize = 15.sp, color = LocalAppTextColor.current), cursorBrush = SolidColor(if (hasBackgroundImage) Color.White else MaterialTheme.colorScheme.primary), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), modifier = Modifier.fillMaxWidth())
                                                        }

                                                        IconButton(onClick = { if (searchQuery.isNotEmpty()) searchQuery = "" else isSearchActive = false }, modifier = Modifier.size(28.dp)) { Icon(Icons.Filled.Close, "Cerrar", modifier = Modifier.size(18.dp), tint = if (hasBackgroundImage) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant) }
                                                    }
                                                }

                                                AnimatedVisibility(visible = !isSearchActive, enter = fadeIn(), exit = fadeOut()) {
                                                    Text(
                                                        text = when {
                                                            showSettings -> "CONFIGURACION"
                                                            selectedPlaylist != null -> selectedPlaylist!!.name
                                                            selectedAlbum != null -> selectedAlbum!!.name
                                                            else -> "MusicFlame"
                                                        },
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 20.sp
                                                    )
                                                }
                                            }
                                        },
                                        navigationIcon = {
                                            if (selectedPlaylist != null || selectedAlbum != null || showSettings) IconButton(onClick = { selectedPlaylist = null; selectedAlbum = null; showSettings = false }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás") }
                                        },
                                        actions = {
                                            if (selectedPlaylist == null && selectedAlbum == null && !showSettings && !isSearchActive) {
                                                IconButton(onClick = {
                                                    isSearchActive = true
                                                    youtubeVideoId = null
                                                }) { Icon(Icons.Filled.Search, "Buscar") }

                                                IconButton(onClick = { showSettings = true }) { Icon(Icons.Filled.Settings, "Configuración") }
                                            }
                                            if (selectedPlaylist != null) {
                                                IconButton(onClick = {
                                                    val playlist = selectedPlaylist!!
                                                    val exportable = if (selectedPlaylistIsFavorites) playlist.copy(songIds = favoriteIds.toList()) else playlist
                                                    val success = playlistRepo.exportToM3U(context, exportable)
                                                    Toast.makeText(context, if (success) "Playlist exportada a Descargas" else "Error al exportar", Toast.LENGTH_SHORT).show()
                                                }) { Icon(Icons.Filled.Download, "Exportar a M3U") }
                                            }
                                            if (selectedAlbum != null) {
                                                IconButton(onClick = {
                                                    val ok = exportAlbumToM3U(context, selectedAlbum!!.name, selectedAlbum!!.songs)
                                                    Toast.makeText(context, if (ok) "Álbum exportado a Descargas" else "Error al exportar", Toast.LENGTH_SHORT).show()
                                                }) { Icon(Icons.Filled.Download, "Exportar a M3U") }
                                            }
                                        },
                                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                            containerColor = if (hasBackgroundImage) Color.Transparent else MaterialTheme.colorScheme.surface,
                                            titleContentColor = LocalAppTextColor.current,
                                            navigationIconContentColor = if (hasBackgroundImage) Color.White else MaterialTheme.colorScheme.onSurface,
                                            actionIconContentColor = if (hasBackgroundImage) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    )
                                }
                            },
                            bottomBar = {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        // Reserva EXACTAMENTE el inset real de la barra de navegación
                                        // del sistema (gestos, 3 botones tipo Honor/EMUI, lo que sea).
                                        // Esto va primero (más "afuera") para que sea lo único que
                                        // separa la barra de los botones físicos/virtuales del sistema.
                                        .navigationBarsPadding()
                                        // Aire visual solo arriba y a los lados; el bottom ya lo puso
                                        // navigationBarsPadding de sobra, así que no se suma dos veces.
                                        .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 0.dp)
                                ) {
                                    if (currentSong != null) {
                                        // Reaparece automáticamente si cambia la canción, para no dejar "atrapado" al usuario
                                        LaunchedEffect(currentSong?.id) { isMiniPlayerVisible = true }

                                        AnimatedVisibility(
                                            visible = isMiniPlayerVisible,
                                            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                                            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                                        ) {
                                            Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(if (useRoundCornersState.value) 16.dp else 0.dp))) {
                                                MiniPlayer(
                                                    currentSong = currentSong!!,
                                                    isPlaying = isPlaying,
                                                    playerManager = playerManager,
                                                    hasBackgroundImage = hasBackgroundImage,
                                                    onExpand = { showFullScreenPlayer = true },
                                                    onPlayPause = { playerManager.togglePlayPause() },
                                                    onSkipNext = { playerManager.skipNext() },
                                                    onSkipPrevious = { playerManager.skipPrevious() },
                                                    onSwipeDownToHide = { isMiniPlayerVisible = false }
                                                )
                                            }
                                        }

                                        // Pestañita para volver a mostrar el mini-reproductor si lo ocultaste
                                        AnimatedVisibility(visible = !isMiniPlayerVisible, enter = fadeIn(), exit = fadeOut()) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 6.dp)
                                                    .clickable { isMiniPlayerVisible = true },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .width(40.dp)
                                                        .height(4.dp)
                                                        .clip(RoundedCornerShape(2.dp))
                                                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                                                )
                                            }
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(if (useRoundCornersState.value) 24.dp else 0.dp)).background(if (hasBackgroundImage) MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.70f) else MaterialTheme.colorScheme.surfaceContainer).padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceEvenly
                                    ) {
                                        bottomNavItems.forEachIndexed { index, screen ->
                                            NavigationBarItem(
                                                selected = pagerState.currentPage == index,
                                                onClick = {
                                                    selectedPlaylist = null
                                                    selectedAlbum = null
                                                    showSettings = false
                                                    isSearchActive = false
                                                    searchQuery = ""
                                                    selectedSongs.clear(); manualSongSelectionMode = false
                                                    selectedPlaylists.clear(); manualPlaylistSelectionMode = false
                                                    coroutineScope.launch { pagerState.animateScrollToPage(index) }
                                                },
                                                icon = { Icon(screen.icon, screen.label) },
                                                label = { Text(screen.label) }
                                            )
                                        }
                                    }
                                }
                            }
                        ) { innerPadding ->
                            if (showSettings) {
                                SettingsScreen(
                                    modifier = Modifier.padding(innerPadding),
                                    playerManager = playerManager,
                                    onBackgroundImageChanged = {
                                        backgroundImageUri.value = settingsRepo.getBackgroundImageUri()
                                        playerGifUri.value = settingsRepo.getPlayerGifUri()
                                        bgBrightness.floatValue = settingsRepo.getBackgroundBrightness()
                                    },
                                    onRoundCornersChanged = { newState -> useRoundCornersState.value = newState },
                                    onAlbumGridColumnsChanged = { newState -> albumGridColumnsState.value = newState },
                                    onAlbumArtShapeChanged = { newShape -> albumArtShapeState.value = newShape },
                                    hasBackgroundImage = hasBackgroundImage,
                                    isUserSignedIn = isUserLoggedIn,
                                    userName = userName,
                                    userPhotoUrl = userPhotoUrl,
                                    onCheckForUpdates = { checkForUpdates(isManualCheck = true) },
                                    onSignInClick = { signInLauncher.launch(googleSignInClient.signInIntent) },
                                    onProfileClick = {
                                        googleSignInClient.signOut().addOnCompleteListener {
                                            isUserLoggedIn = false
                                            userName = null
                                            userPhotoUrl = null
                                            isYouTubeLinked = false
                                            isDriveLinked = false
                                            syncedFileNames = emptySet() // Limpiar al salir
                                        }
                                    },
                                    onRefreshUserProfile = {
                                        val account = GoogleSignIn.getLastSignedInAccount(context)
                                        if (account != null) {
                                            userName = account.displayName
                                            userPhotoUrl = account.photoUrl?.toString()
                                            isYouTubeLinked = GoogleSignIn.hasPermissions(account, youtubeScope)
                                            isDriveLinked = GoogleSignIn.hasPermissions(account, driveScope)
                                        }
                                    },
                                    isDriveLinked = isDriveLinked,
                                    onLinkDriveClick = {
                                        if (isDriveLinked) {
                                            Toast.makeText(context, "Google Drive ya está vinculado con MusicFlame", Toast.LENGTH_SHORT).show()
                                        } else {
                                            signInLauncher.launch(googleSignInClient.signInIntent)
                                        }
                                    }
                                )
                            } else {
                                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize().padding(innerPadding)) { page ->

                                    val onToggleSong: (Song) -> Unit = { song -> if (selectedSongs.contains(song)) selectedSongs.remove(song) else selectedSongs.add(song) }
                                    val onTogglePlaylist: (Playlist) -> Unit = { playlist -> if (selectedPlaylists.contains(playlist)) selectedPlaylists.remove(playlist) else selectedPlaylists.add(playlist) }

                                    when (bottomNavItems[page]) {

                                        Screen.Songs -> Box(modifier = Modifier.fillMaxSize()) {
                                            // --- AQUÍ PASAMOS LOS NUEVOS PARÁMETROS ---
                                            SongsScreen(
                                                onSongClick = { song, list ->
                                                    if (searchMode == SearchMode.LOCAL) {
                                                        songList = list
                                                        playerManager.playSong(song, list)
                                                    } else {
                                                        youtubeVideoId = song.youtubeVideoId
                                                    }
                                                },
                                                hasBackgroundImage = hasBackgroundImage,
                                                searchQuery = searchQuery,
                                                searchMode = searchMode,
                                                selectedSongs = selectedSongs,
                                                onToggleSelection = onToggleSong,
                                                youtubeRecommendedSongs = youtubeRecommendedSongs,
                                                isYoutubeLoggedIn = isYouTubeLinked,
                                                favoriteIds = favoriteIds,
                                                onToggleFavorite = { song ->
                                                    favoritesRepo.toggleFavorite(song.id)
                                                    favoriteIds = favoritesRepo.getAllFavoriteIds()
                                                    val intent = android.content.Intent("com.music.musicflame.FAVORITES_CHANGED")
                                                    intent.setPackage(packageName)
                                                    sendBroadcast(intent)
                                                },
                                                syncedFileNames = syncedFileNames,
                                                selectionModeActive = manualSongSelectionMode,
                                                onToggleSelectionModeButton = { manualSongSelectionMode = !manualSongSelectionMode },
                                                patchTrigger = songsPatchTrigger,
                                                pendingPatches = pendingSongPatches,
                                                lyricsRefreshTrigger = lyricsRefreshTrigger
                                            )

                                            if (youtubeVideoId != null) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .background(Color.Black.copy(alpha = 0.95f))
                                                ) {
                                                    YoutubePlayerScreen(
                                                        videoId = youtubeVideoId!!,
                                                        modifier = Modifier.fillMaxWidth().align(Alignment.Center)
                                                    )

                                                    IconButton(
                                                        onClick = { youtubeVideoId = null },
                                                        modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
                                                    ) {
                                                        Icon(Icons.Filled.Close, "Cerrar", tint = Color.White)
                                                    }
                                                }
                                            }
                                        }

                                        Screen.Playlists -> if (selectedPlaylist != null) {
                                            PlaylistDetailScreen(
                                                playlist = selectedPlaylist!!,
                                                isFavorites = selectedPlaylistIsFavorites,
                                                onBack = { selectedPlaylist = null },
                                                onSongClick = { song, list -> songList = list; playerManager.playSong(song, list) },
                                                hasBackgroundImage = hasBackgroundImage,
                                                selectedSongs = selectedSongs,
                                                onToggleSelection = onToggleSong,
                                                selectionModeActive = manualSongSelectionMode,
                                                onToggleSelectionModeButton = { manualSongSelectionMode = !manualSongSelectionMode }
                                            )
                                        } else PlaylistsScreen(
                                            onPlaylistClick = { playlist, isFavorites -> selectedPlaylist = playlist; selectedPlaylistIsFavorites = isFavorites },
                                            onImportClick = { importM3ULauncher.launch("audio/x-mpegurl") },
                                            onChangeCoverClick = { playlistId -> selectedPlaylistForCover = playlistId; pickImageLauncher.launch("image/*") },
                                            hasBackgroundImage = hasBackgroundImage,
                                            selectedPlaylists = selectedPlaylists,
                                            onToggleSelection = onTogglePlaylist,
                                            selectionModeActive = manualPlaylistSelectionMode,
                                            onToggleSelectionModeButton = { manualPlaylistSelectionMode = !manualPlaylistSelectionMode }
                                        )
                                        Screen.Mix -> MixScreen(onSongClick = { song, list -> songList = list; playerManager.playSong(song, list) }, hasBackgroundImage = hasBackgroundImage, selectedSongs = selectedSongs, onToggleSelection = onToggleSong)
                                        Screen.Album -> AlbumScreen(
                                            hasBackgroundImage = hasBackgroundImage,
                                            selectedAlbum = selectedAlbum,
                                            onAlbumClick = { selectedAlbum = it },
                                            onPlaySong = { song, list -> songList = list; playerManager.playSong(song, list) },
                                            selectedSongs = selectedSongs,
                                            onToggleSelection = onToggleSong,
                                            selectionModeActive = manualSongSelectionMode,
                                            onToggleSelectionModeButton = { manualSongSelectionMode = !manualSongSelectionMode }
                                        )
                                        Screen.Trash -> TrashScreen(
                                            onSongClick = { song, list -> songList = list; playerManager.playSong(song, list) },
                                            hasBackgroundImage = hasBackgroundImage,
                                            selectedSongs = selectedSongs,
                                            onToggleSelection = onToggleSong
                                        )
                                        else -> {}
                                    }
                                }
                            }

                            if (showAddToPlaylist && songToAddToPlaylist != null) AddToPlaylistDialog(song = songToAddToPlaylist!!, onDismiss = { showAddToPlaylist = false; songToAddToPlaylist = null })

                            if (showMultiPlaylistDialog) {
                                val playlists = playlistRepo.getPlaylists()
                                AlertDialog(
                                    onDismissRequest = { showMultiPlaylistDialog = false },
                                    title = { Text("Añadir a playlist", fontWeight = FontWeight.Bold) },
                                    text = {
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        showMultiPlaylistDialog = false
                                                        newPlaylistNameFromSelection = ""
                                                        showCreatePlaylistFromSelection = true
                                                    }
                                                    .padding(vertical = 12.dp, horizontal = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(Icons.Filled.Add, null, tint = MaterialTheme.colorScheme.primary)
                                                Spacer(Modifier.width(8.dp))
                                                Text("Crear nueva playlist", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium, fontSize = 16.sp)
                                            }

                                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                                            if (playlists.isEmpty()) {
                                                Text("No tienes playlists creadas.", modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp))
                                            } else {
                                                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                                                    items(playlists) { playlist ->
                                                        Text(
                                                            text = playlist.name,
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .clickable {
                                                                    selectedSongs.forEach { playlistRepo.addSongToPlaylist(playlist.id, it.id) }
                                                                    Toast.makeText(context, "${selectedSongs.size} canciones añadidas a ${playlist.name}", Toast.LENGTH_SHORT).show()
                                                                    showMultiPlaylistDialog = false
                                                                    selectedSongs.clear(); manualSongSelectionMode = false
                                                                }
                                                                .padding(vertical = 12.dp, horizontal = 8.dp),
                                                            fontSize = 16.sp
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    },
                                    confirmButton = { TextButton(onClick = { showMultiPlaylistDialog = false }) { Text("Cancelar") } }
                                )
                            }

                            if (showCreatePlaylistFromSelection) {
                                AlertDialog(
                                    onDismissRequest = { showCreatePlaylistFromSelection = false },
                                    title = { Text("Nueva Playlist", fontWeight = FontWeight.Bold) },
                                    text = {
                                        OutlinedTextField(
                                            value = newPlaylistNameFromSelection,
                                            onValueChange = { newPlaylistNameFromSelection = it },
                                            label = { Text("Nombre") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    },
                                    confirmButton = {
                                        Button(
                                            onClick = {
                                                val trimmedName = newPlaylistNameFromSelection.trim()
                                                if (trimmedName.isNotEmpty()) {
                                                    playlistRepo.createPlaylist(trimmedName)
                                                    val newPlaylist = playlistRepo.getAllPlaylists()
                                                        .filter { it.name == trimmedName }
                                                        .maxByOrNull { it.id.toLongOrNull() ?: 0L }
                                                    if (newPlaylist != null) {
                                                        selectedSongs.forEach { playlistRepo.addSongToPlaylist(newPlaylist.id, it.id) }
                                                        Toast.makeText(context, "${selectedSongs.size} canciones añadidas a $trimmedName", Toast.LENGTH_SHORT).show()
                                                    }
                                                    showCreatePlaylistFromSelection = false
                                                    selectedSongs.clear(); manualSongSelectionMode = false
                                                }
                                            }
                                        ) { Text("Crear y añadir") }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { showCreatePlaylistFromSelection = false }) { Text("Cancelar") }
                                    }
                                )
                            }

                            // --- Diálogo: Editar carátula (imagen/GIF) y nombre de canción(es) ---
                            if (showEditSongDialog && selectedSongs.isNotEmpty()) {
                                EditSongDialog(
                                    selectedSongs = selectedSongs.toList(),
                                    customizationRepo = songCustomizationRepo,
                                    onDismiss = { showEditSongDialog = false },
                                    onSaved = { patches ->
                                        showEditSongDialog = false
                                        pendingSongPatches = patches
                                        songsPatchTrigger++
                                        val count = selectedSongs.size
                                        selectedSongs.clear(); manualSongSelectionMode = false
                                        Toast.makeText(
                                            context,
                                            if (count == 1) "Canción actualizada" else "$count canciones actualizadas",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                )
                            }

                            if (showMultiDeleteDialog) {
                                AlertDialog(
                                    onDismissRequest = { showMultiDeleteDialog = false },
                                    icon = { Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.primary) },
                                    title = { Text("Mover a la papelera", fontWeight = FontWeight.Bold) },
                                    text = { Text("¿Mover ${selectedSongs.size} canciones a la papelera?\nPodrás recuperarlas más tarde desde ahí.") },
                                    confirmButton = {
                                        Button(
                                            onClick = {
                                                trashRepo.moveToTrash(selectedSongs.toList())
                                                showMultiDeleteDialog = false
                                                selectedSongs.clear(); manualSongSelectionMode = false
                                            }
                                        ) { Text("Mover") }
                                    },
                                    dismissButton = { TextButton(onClick = { showMultiDeleteDialog = false }) { Text("Cancelar") } }
                                )
                            }

                            if (showDeletePlaylistsDialog) {
                                val deletablePlaylists = selectedPlaylists.filter { it.id != "favorites" }
                                AlertDialog(
                                    onDismissRequest = { showDeletePlaylistsDialog = false },
                                    title = { Text("Mover a papelera", fontWeight = FontWeight.Bold) },
                                    text = {
                                        if (deletablePlaylists.isEmpty()) {
                                            Text("La playlist de Favoritos no se puede mover a la papelera.")
                                        } else {
                                            Text("¿Mover ${deletablePlaylists.size} playlists a la papelera?\nLas canciones no se borrarán del dispositivo, solo la playlist. Podrás restaurarla dentro de 30 días.")
                                        }
                                    },
                                    confirmButton = {
                                        Button(
                                            onClick = {
                                                deletablePlaylists.forEach { playlist ->
                                                    trashRepo.trashPlaylist(playlist)
                                                    playlistRepo.deletePlaylist(playlist.id)
                                                }
                                                Toast.makeText(context, "${deletablePlaylists.size} playlists movidas a la papelera", Toast.LENGTH_SHORT).show()
                                                selectedPlaylists.clear(); manualPlaylistSelectionMode = false
                                                showDeletePlaylistsDialog = false
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                        ) { Text("Mover a papelera") }
                                    },
                                    dismissButton = { TextButton(onClick = { showDeletePlaylistsDialog = false }) { Text("Cancelar") } }
                                )
                            }
                        }

                        AnimatedVisibility(
                            visible = showFullScreenPlayer,
                            enter = slideInVertically(initialOffsetY = { fullHeight -> fullHeight }),
                            exit = slideOutVertically(targetOffsetY = { fullHeight -> fullHeight })
                        ) {
                            if (currentSong != null) {
                                FullScreenPlayer(
                                    song = currentSong!!, songList = songList, playerManager = playerManager, isPlaying = isPlaying, isFavorite = favoriteIds.contains(currentSong!!.id),
                                    onCollapse = { showFullScreenPlayer = false }, onPlayPause = { playerManager.togglePlayPause() },
                                    onToggleFavorite = {
                                        favoritesRepo.toggleFavorite(currentSong!!.id)
                                        favoriteIds = favoritesRepo.getAllFavoriteIds()
                                        val intent = android.content.Intent("com.music.musicflame.FAVORITES_CHANGED")
                                        intent.setPackage(packageName)
                                        sendBroadcast(intent)
                                    },
                                    onSkipNext = { playerManager.skipNext() }, onSkipPrevious = { playerManager.skipPrevious() }, onAddToPlaylist = { songToAddToPlaylist = currentSong; showAddToPlaylist = true },
                                    hasBackgroundImage = hasBackgroundImage,
                                    onLyricsChanged = { lyricsRefreshTrigger++ }
                                )
                            }
                        }

                        // --- Onboarding de primer uso: cubre toda la pantalla hasta completarse ---
                        if (showOnboarding) {
                            OnboardingScreen(
                                isUserSignedIn = isUserLoggedIn,
                                userName = userName,
                                onSignInClick = { signInLauncher.launch(googleSignInClient.signInIntent) },
                                onFinished = { showOnboarding = false }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (!SettingsRepository(this).getPlayInBackground()) playerManager.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        playerManager.release()
    }
}

@Composable
fun UpdateDialog(
    newVersion: String,
    hasBackgroundImage: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val dialogColor = if (hasBackgroundImage) Color.White else Color.Gray

    AlertDialog(
        onDismissRequest = { onDismiss() },
        containerColor = dialogColor,
        title = { Text(text = "¡Actualización disponible!") },
        text = {
            Text(text = "La versión $newVersion ya está lista para instalarse. ¿Deseas descargarla ahora?")
        },
        confirmButton = {
            Button(onClick = { onConfirm() }) {
                Text("Actualizar")
            }
        },
        dismissButton = {
            TextButton(onClick = { onDismiss() }) {
                Text("Más tarde")
            }
        }
    )
}

fun downloadApk(context: android.content.Context, url: String, fileName: String) {
    try {
        val request = android.app.DownloadManager.Request(android.net.Uri.parse(url))
            .setTitle("Descargando actualización")
            .setDescription("Descargando la nueva versión...")
            .setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, fileName)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        val downloadManager = context.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
        downloadManager.enqueue(request)
        android.widget.Toast.makeText(context, "Descarga iniciada...", android.widget.Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        android.widget.Toast.makeText(context, "Error al iniciar la descarga", android.widget.Toast.LENGTH_SHORT).show()
    }
}