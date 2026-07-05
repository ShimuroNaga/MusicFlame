package com.music.musicflame

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build.VERSION.SDK_INT
import android.os.Bundle
import android.provider.MediaStore
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
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.music.musicflame.data.*
import com.music.musicflame.navigation.Screen
import com.music.musicflame.navigation.bottomNavItems
import com.music.musicflame.ui.screens.*
import com.music.musicflame.ui.theme.MusicFlameTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.music.musicflame.ui.components.YoutubePlayerScreen
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

// ⚠️ Reemplaza esto con tu "Web Client ID" (tipo Aplicación Web) de Google Cloud Console.
// Es el mismo que usarías para Credential Manager; aquí se usa para requestIdToken().
private const val WEB_CLIENT_ID = "176181653925-etnugbpe1mqhu1gl3lu1njbu9iihcn1k.apps.googleusercontent.com"

val LocalUseRoundCorners = compositionLocalOf { true }

enum class SearchMode {
    LOCAL,
    YOUTUBE
}

class MainActivity : ComponentActivity() {
    private lateinit var playerManager: MusicPlayerManager
    private lateinit var playlistRepo: PlaylistRepository
    private lateinit var favoritesRepo: FavoritesRepository
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
                playlistRepo.updatePlaylistCover(playlistId, it.toString())
                selectedPlaylistForCover = null
            }
        }
    }

    // --- 1. AÑADIMOS LA FUNCIÓN DE AYUDA PARA OBTENER EL TOKEN ---
    private suspend fun getYouTubeAccessToken(context: android.content.Context): String? {
        return withContext(Dispatchers.IO) {
            try {
                // Obtenemos la cuenta activa actual
                val account = GoogleSignIn.getLastSignedInAccount(context)?.account
                if (account != null) {
                    // El prefijo "oauth2:" es OBLIGATORIO para GoogleAuthUtil
                    val scope = "oauth2:https://www.googleapis.com/auth/youtube.readonly"

                    // Esto devuelve el Access Token real en formato String
                    GoogleAuthUtil.getToken(context, account, scope)
                } else {
                    null
                }
            } catch (e: Exception) {
                android.util.Log.e("YOUTUBE_AUTH", "Error obteniendo token", e)
                null
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Firebase.appCheck.installAppCheckProviderFactory(
            PlayIntegrityAppCheckProviderFactory.getInstance()
        )

        playerManager = MusicPlayerManager(this)
        playlistRepo = PlaylistRepository(this)
        favoritesRepo = FavoritesRepository(this)

        setContent {
            MusicFlameTheme {
                val context = this@MainActivity

                // --- Lógica Google Sign-In ---
                var isUserLoggedIn by remember { mutableStateOf(false) }
                var userName by remember { mutableStateOf<String?>(null) }
                var userPhotoUrl by remember { mutableStateOf<String?>(null) }

                // --- Firebase Auth + Firestore (contador de cuentas vinculadas) ---
                val firebaseAuth = remember { FirebaseAuth.getInstance() }
                val firestore = remember { FirebaseFirestore.getInstance() }
                var linkedAccountsCount by remember { mutableStateOf<Int?>(null) }
                var isYouTubeLinked by remember { mutableStateOf(false) }
                val authScope = rememberCoroutineScope()

                suspend fun syncLinkedAccount(uid: String, name: String?, email: String?) {
                    firestore.collection("linked_accounts")
                        .document(uid)
                        .set(
                            mapOf(
                                "name" to name,
                                "email" to email,
                                "lastSignInAt" to com.google.firebase.Timestamp.now()
                            ),
                            SetOptions.merge()
                        )
                        .await()
                }

                suspend fun fetchLinkedAccountsCount(): Int {
                    val snapshot = firestore.collection("linked_accounts")
                        .count()
                        .get(AggregateSource.SERVER)
                        .await()
                    return snapshot.count.toInt()
                }

                suspend fun syncFirebaseSession(idToken: String?, uid_email: String?, uid_name: String?) {
                    if (idToken == null) return
                    try {
                        val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
                        val authResult = firebaseAuth.signInWithCredential(firebaseCredential).await()
                        val uid = authResult.user?.uid

                        authResult.user?.getIdToken(true)?.await()

                        if (uid != null) {
                            try {
                                syncLinkedAccount(uid, uid_name, uid_email)
                            } catch (e: Exception) {
                                if (e is kotlinx.coroutines.CancellationException) throw e
                            }
                        }
                        linkedAccountsCount = try {
                            fetchLinkedAccountsCount()
                        } catch (e: Exception) {
                            if (e is kotlinx.coroutines.CancellationException) throw e
                            linkedAccountsCount
                        }
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                    }
                }

                val youtubeScope = remember { Scope("https://www.googleapis.com/auth/youtube.readonly") }
                val gso = remember {
                    GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestEmail()
                        .requestProfile()
                        .requestIdToken(WEB_CLIENT_ID)
                        .requestScopes(youtubeScope)
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
                        syncFirebaseSession(account.idToken, account.email, account.displayName)
                    }
                }

                val signInLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                    Toast.makeText(context, "DEBUG: callback de login recibido", Toast.LENGTH_SHORT).show()
                    val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                    try {
                        val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
                        Toast.makeText(context, "DEBUG: cuenta obtenida, nombre=${account.displayName}", Toast.LENGTH_SHORT).show()
                        isUserLoggedIn = true
                        userName = account.displayName
                        userPhotoUrl = account.photoUrl?.toString()
                        isYouTubeLinked = GoogleSignIn.hasPermissions(account, youtubeScope)
                        authScope.launch { syncFirebaseSession(account.idToken, account.email, account.displayName) }
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

                val backgroundImageUri = remember { mutableStateOf(settingsRepo.getBackgroundImageUri()) }
                val playerGifUri = remember { mutableStateOf(settingsRepo.getPlayerGifUri()) }
                val bgBrightness = remember { mutableFloatStateOf(settingsRepo.getBackgroundBrightness()) }
                val hasBackgroundImage = backgroundImageUri.value != null || playerGifUri.value != null
                val useRoundCornersState = remember { mutableStateOf(settingsRepo.getUseRoundCorners()) }

                val pagerState = rememberPagerState(pageCount = { bottomNavItems.size })
                val coroutineScope = rememberCoroutineScope()

                val currentSong by playerManager.currentSong
                val isPlaying by playerManager.isPlayingState
                var songList by remember { mutableStateOf<List<Song>>(emptyList()) }

                var songToAddToPlaylist by remember { mutableStateOf<Song?>(null) }
                var showAddToPlaylist by remember { mutableStateOf(false) }

                var selectedPlaylist by remember { mutableStateOf<Playlist?>(null) }
                var selectedPlaylistIsFavorites by remember { mutableStateOf(false) }
                var showSettings by remember { mutableStateOf(false) }
                var geminiPrompt by remember { mutableStateOf("") }

                var isSearchActive by remember { mutableStateOf(false) }
                var searchQuery by remember { mutableStateOf("") }
                var searchMode by remember { mutableStateOf(SearchMode.LOCAL) }
                var showSearchModeMenu by remember { mutableStateOf(false) }

                val messages = remember { mutableStateListOf<ChatMessage>() }

                var showFullScreenPlayer by remember { mutableStateOf(false) }
                var favoriteIds by remember { mutableStateOf(favoritesRepo.getAllFavoriteIds()) }

                val selectedSongs = remember { mutableStateListOf<Song>() }
                val selectedPlaylists = remember { mutableStateListOf<Playlist>() }

                val isSongSelectionMode = selectedSongs.isNotEmpty()
                val isPlaylistSelectionMode = selectedPlaylists.isNotEmpty()
                val isAnySelectionMode = isSongSelectionMode || isPlaylistSelectionMode

                var totalSongsOnDevice by remember { mutableIntStateOf(0) }

                var showSelectionMenu by remember { mutableStateOf(false) }
                var showPlaylistSelectionMenu by remember { mutableStateOf(false) }
                var showMultiPlaylistDialog by remember { mutableStateOf(false) }
                var showMultiDeleteDialog by remember { mutableStateOf(false) }
                var showDeletePlaylistsDialog by remember { mutableStateOf(false) }

                var youtubeVideoId by remember { mutableStateOf<String?>(null) }
                var youtubeRecommendedSongs by remember { mutableStateOf<List<Song>>(emptyList()) }

                // --- NUEVO: trae videos recientes de tus canales suscritos ---
                suspend fun fetchSubscriptionVideos(token: String): List<Song> = withContext(Dispatchers.IO) {
                    try {
                        val authHeader = "Bearer $token"

                        // 1. Tus canales suscritos (quota barata: 1)
                        val subsResponse = com.music.musicflame.api.RetrofitClient.instance.getMySubscriptions(authHeader = authHeader)
                        val channelIds = subsResponse.items.mapNotNull { it.snippet?.resourceId?.channelId }.distinct()
                        if (channelIds.isEmpty()) return@withContext emptyList()

                        // 2. Su playlist de "Subidos" para cada canal, hasta 50 IDs en un solo request (quota: 1)
                        val channelsResponse = com.music.musicflame.api.RetrofitClient.instance.getChannelsContentDetails(
                            channelIds = channelIds.take(50).joinToString(",")
                        )
                        val uploadsPlaylistIds = channelsResponse.items.mapNotNull { it.contentDetails?.relatedPlaylists?.uploads }

                        // 3. Videos recientes de cada playlist (quota: 1 por canal).
                        // Limitamos a 8 canales para no gastar cuota de más en cada carga.
                        uploadsPlaylistIds.take(8).flatMap { playlistId ->
                            try {
                                val itemsResponse = com.music.musicflame.api.RetrofitClient.instance.getPlaylistItems(playlistId = playlistId)
                                itemsResponse.items.mapNotNull { item ->
                                    val videoId = item.snippet?.resourceId?.videoId ?: return@mapNotNull null
                                    Song(
                                        id = videoId.hashCode().toLong(),
                                        title = item.snippet.title ?: "Sin título",
                                        artist = item.snippet.channelTitle ?: "Desconocido",
                                        albumArtUri = item.snippet.thumbnails?.high?.url ?: "",
                                        path = "",
                                        dateAdded = 0L,
                                        duration = 0L,
                                        youtubeVideoId = videoId
                                    )
                                }
                            } catch (e: Exception) {
                                if (e is kotlinx.coroutines.CancellationException) throw e
                                emptyList()
                            }
                        }
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        emptyList()
                    }
                }

                // --- 2. FUNCIÓN ACTUALIZADA: combina Me gusta + canales suscritos ---
                fun fetchInitialYoutubeVideos() {
                    coroutineScope.launch {
                        try {
                            if (isYouTubeLinked) {
                                val token = getYouTubeAccessToken(context)

                                if (token != null) {
                                    val authHeader = "Bearer $token"

                                    // Videos con "Me gusta"
                                    val likedResponse = try {
                                        com.music.musicflame.api.RetrofitClient.instance.getLikedVideos(authHeader = authHeader)
                                    } catch (e: Exception) {
                                        if (e is kotlinx.coroutines.CancellationException) throw e
                                        null
                                    }

                                    val likedSongs = likedResponse?.items?.mapNotNull { item ->
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
                                    } ?: emptyList()

                                    // Videos de canales suscritos
                                    val subscriptionSongs = fetchSubscriptionVideos(token)

                                    // Combina ambas fuentes, sin duplicados
                                    youtubeRecommendedSongs = (likedSongs + subscriptionSongs).distinctBy { it.youtubeVideoId }

                                } else {
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
                                }
                            } else {
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
                            }
                        } catch (e: Exception) {
                            if (e is kotlinx.coroutines.CancellationException) throw e
                            android.util.Log.e("YOUTUBE_API", "Error al cargar recomendados", e)
                        }
                    }
                }

                // Disparamos la carga automáticamente cuando el usuario entra al modo YouTube
                LaunchedEffect(searchMode, isYouTubeLinked) {
                    if (searchMode == SearchMode.YOUTUBE && youtubeRecommendedSongs.isEmpty()) {
                        fetchInitialYoutubeVideos()
                    }
                }

                LaunchedEffect(Unit) {
                    totalSongsOnDevice = loadSongsFromDevice(context).size
                }

                LaunchedEffect(pagerState.currentPage) {
                    val currentScreen = bottomNavItems[pagerState.currentPage]
                    isSearchActive = false
                    searchQuery = ""

                    selectedSongs.clear()
                    selectedPlaylists.clear()
                    if (currentScreen != Screen.Gemini) geminiPrompt = ""
                    if (currentScreen != Screen.Playlists) selectedPlaylist = null
                }

                CompositionLocalProvider(LocalUseRoundCorners provides useRoundCornersState.value) {
                    BackHandler(enabled = showFullScreenPlayer || isAnySelectionMode) {
                        if (showFullScreenPlayer) showFullScreenPlayer = false
                        else {
                            selectedSongs.clear()
                            selectedPlaylists.clear()
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

                        Scaffold(
                            modifier = Modifier.fillMaxSize(),
                            containerColor = Color.Transparent,
                            topBar = {
                                if (isSongSelectionMode) {
                                    TopAppBar(
                                        title = { Text("${selectedSongs.size} / $totalSongsOnDevice", fontWeight = FontWeight.Bold) },
                                        navigationIcon = {
                                            IconButton(onClick = { selectedSongs.clear() }) { Icon(Icons.Filled.Close, "Cancelar Selección") }
                                        },
                                        actions = {
                                            IconButton(onClick = { showSelectionMenu = true }) { Icon(Icons.Filled.MoreVert, "Opciones") }

                                            DropdownMenu(expanded = showSelectionMenu, onDismissRequest = { showSelectionMenu = false }) {
                                                DropdownMenuItem(
                                                    text = { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.PlaylistAdd, null, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Añadir a playlist") } },
                                                    onClick = { showSelectionMenu = false; showMultiPlaylistDialog = true }
                                                )
                                                DropdownMenuItem(
                                                    text = { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.Favorite, null, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Añadir a favoritos") } },
                                                    onClick = {
                                                        showSelectionMenu = false
                                                        selectedSongs.forEach { song -> if (!favoriteIds.contains(song.id)) favoritesRepo.toggleFavorite(song.id) }
                                                        favoriteIds = favoritesRepo.getAllFavoriteIds()
                                                        val intent = android.content.Intent("com.music.musicflame.FAVORITES_CHANGED")
                                                        intent.setPackage(packageName)
                                                        sendBroadcast(intent)
                                                        Toast.makeText(context, "${selectedSongs.size} añadidas a Favoritos", Toast.LENGTH_SHORT).show()
                                                        selectedSongs.clear()
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.SmartToy, null, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Mandar a Gemini") } },
                                                    onClick = {
                                                        showSelectionMenu = false
                                                        geminiPrompt = "Analiza y recomiéndame música basándote en estas canciones: " + selectedSongs.joinToString(", ") { it.title }
                                                        selectedSongs.clear()
                                                        coroutineScope.launch { pagerState.animateScrollToPage(bottomNavItems.indexOf(Screen.Gemini)) }
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.Delete, null, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Mover a papelera") } },
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
                                            IconButton(onClick = { selectedPlaylists.clear() }) { Icon(Icons.Filled.Close, "Cancelar Selección") }
                                        },
                                        actions = {
                                            IconButton(onClick = { showPlaylistSelectionMenu = true }) { Icon(Icons.Filled.MoreVert, "Opciones") }

                                            DropdownMenu(expanded = showPlaylistSelectionMenu, onDismissRequest = { showPlaylistSelectionMenu = false }) {
                                                DropdownMenuItem(
                                                    text = { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.SmartToy, null, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Mandar a Gemini") } },
                                                    onClick = {
                                                        showPlaylistSelectionMenu = false
                                                        val allSongs = loadSongsFromDevice(context)
                                                        val allIds = selectedPlaylists.flatMap { if (it.id == "favorites") favoriteIds.toList() else it.songIds }.distinct().take(40)
                                                        val targetSongs = allSongs.filter { it.id in allIds }

                                                        if (allIds.size >= 40) Toast.makeText(context, "Limitado a 40 canciones para Gemini", Toast.LENGTH_SHORT).show()

                                                        geminiPrompt = "Analiza estas playlists con música como: " + targetSongs.joinToString(", ") { it.title }
                                                        selectedPlaylists.clear()
                                                        coroutineScope.launch { pagerState.animateScrollToPage(bottomNavItems.indexOf(Screen.Gemini)) }
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Eliminar", color = MaterialTheme.colorScheme.error) } },
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
                                                                Text(hintText, fontSize = 14.sp, color = if (hasBackgroundImage) Color.White.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                                                            }
                                                            BasicTextField(value = searchQuery, onValueChange = { searchQuery = it }, singleLine = true, textStyle = TextStyle(fontSize = 15.sp, color = if (hasBackgroundImage) Color.White else MaterialTheme.colorScheme.onSurface), cursorBrush = SolidColor(if (hasBackgroundImage) Color.White else MaterialTheme.colorScheme.primary), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), modifier = Modifier.fillMaxWidth())
                                                        }

                                                        IconButton(onClick = { if (searchQuery.isNotEmpty()) searchQuery = "" else isSearchActive = false }, modifier = Modifier.size(28.dp)) { Icon(Icons.Filled.Close, "Cerrar", modifier = Modifier.size(18.dp), tint = if (hasBackgroundImage) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant) }
                                                    }
                                                }

                                                AnimatedVisibility(visible = !isSearchActive, enter = fadeIn(), exit = fadeOut()) {
                                                    Text(text = when { showSettings -> "Ajustes"; selectedPlaylist != null -> selectedPlaylist!!.name; else -> "MusicFlame" }, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                                                }
                                            }
                                        },
                                        navigationIcon = {
                                            if (selectedPlaylist != null || showSettings) IconButton(onClick = { selectedPlaylist = null; showSettings = false }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás") }
                                        },
                                        actions = {
                                            if (selectedPlaylist == null && !showSettings && !isSearchActive) {
                                                IconButton(onClick = {
                                                    isSearchActive = true
                                                    youtubeVideoId = null
                                                }) { Icon(Icons.Filled.Search, "Buscar") }

                                                IconButton(onClick = { showSettings = true }) { Icon(Icons.Filled.Settings, "Ajustes") }
                                            }
                                        },
                                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                            containerColor = if (hasBackgroundImage) Color.Transparent else MaterialTheme.colorScheme.surface,
                                            titleContentColor = if (hasBackgroundImage) Color.White else MaterialTheme.colorScheme.onSurface,
                                            navigationIconContentColor = if (hasBackgroundImage) Color.White else MaterialTheme.colorScheme.onSurface,
                                            actionIconContentColor = if (hasBackgroundImage) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    )
                                }
                            },
                            bottomBar = {
                                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                                    if (currentSong != null) {
                                        Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(if (useRoundCornersState.value) 16.dp else 0.dp))) {
                                            MiniPlayer(currentSong = currentSong, isPlaying = isPlaying, playerManager = playerManager, hasBackgroundImage = hasBackgroundImage, onExpand = { showFullScreenPlayer = true }, onPlayPause = { playerManager.togglePlayPause() }, onSkipNext = { playerManager.skipNext() }, onSkipPrevious = { playerManager.skipPrevious() })
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
                                                    showSettings = false
                                                    isSearchActive = false
                                                    searchQuery = ""
                                                    selectedSongs.clear()
                                                    selectedPlaylists.clear()
                                                    if (screen != Screen.Gemini) geminiPrompt = ""
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
                                    onBackgroundImageChanged = {
                                        backgroundImageUri.value = settingsRepo.getBackgroundImageUri()
                                        playerGifUri.value = settingsRepo.getPlayerGifUri()
                                        bgBrightness.floatValue = settingsRepo.getBackgroundBrightness()
                                    },
                                    onRoundCornersChanged = { newState -> useRoundCornersState.value = newState },
                                    hasBackgroundImage = hasBackgroundImage,
                                    isUserSignedIn = isUserLoggedIn,
                                    userName = userName,
                                    userPhotoUrl = userPhotoUrl,
                                    onSignInClick = { signInLauncher.launch(googleSignInClient.signInIntent) },
                                    onProfileClick = {
                                        googleSignInClient.signOut().addOnCompleteListener {
                                            isUserLoggedIn = false
                                            userName = null
                                            userPhotoUrl = null
                                            isYouTubeLinked = false
                                            linkedAccountsCount = null
                                            firebaseAuth.signOut()
                                        }
                                    },
                                    onRefreshUserProfile = {
                                        val account = GoogleSignIn.getLastSignedInAccount(context)
                                        if (account != null) {
                                            userName = account.displayName
                                            userPhotoUrl = account.photoUrl?.toString()
                                            isYouTubeLinked = GoogleSignIn.hasPermissions(account, youtubeScope)
                                        }
                                    },
                                    linkedAccountsCount = linkedAccountsCount,
                                    onRequestLinkedAccountsCount = {
                                        authScope.launch {
                                            linkedAccountsCount = try { fetchLinkedAccountsCount() } catch (e: Exception) { linkedAccountsCount }
                                        }
                                    },
                                    isYouTubeLinked = isYouTubeLinked,
                                    onLinkYouTubeClick = {
                                        if (isYouTubeLinked) {
                                            Toast.makeText(context, "YouTube ya está vinculado con tu cuenta", Toast.LENGTH_SHORT).show()
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
                                            SongsScreen(
                                                onSongClick = { song, list ->
                                                    if (searchMode == SearchMode.LOCAL) {
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
                                                isYoutubeLoggedIn = isYouTubeLinked
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
                                                onSendToGemini = { songs -> geminiPrompt = "Analiza esta playlist: " + songs.joinToString { it.title }; coroutineScope.launch { pagerState.animateScrollToPage(bottomNavItems.indexOf(Screen.Gemini)) } },
                                                hasBackgroundImage = hasBackgroundImage,
                                                selectedSongs = selectedSongs,
                                                onToggleSelection = onToggleSong
                                            )
                                        } else PlaylistsScreen(
                                            onPlaylistClick = { playlist, isFavorites -> selectedPlaylist = playlist; selectedPlaylistIsFavorites = isFavorites },
                                            onImportClick = { importM3ULauncher.launch("audio/x-mpegurl") },
                                            onChangeCoverClick = { playlistId -> selectedPlaylistForCover = playlistId; pickImageLauncher.launch("image/*") },
                                            hasBackgroundImage = hasBackgroundImage,
                                            selectedPlaylists = selectedPlaylists,
                                            onToggleSelection = onTogglePlaylist
                                        )
                                        Screen.Mix -> MixScreen(onSongClick = { song, list -> songList = list; playerManager.playSong(song, list) }, hasBackgroundImage = hasBackgroundImage, selectedSongs = selectedSongs, onToggleSelection = onToggleSong)
                                        Screen.Gemini -> GeminiScreen(messages = messages, initialPrompt = geminiPrompt, hasBackgroundImage = hasBackgroundImage)
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
                                        if (playlists.isEmpty()) {
                                            Text("No tienes playlists creadas.")
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
                                                                selectedSongs.clear()
                                                            }
                                                            .padding(vertical = 12.dp, horizontal = 8.dp),
                                                        fontSize = 16.sp
                                                    )
                                                }
                                            }
                                        }
                                    },
                                    confirmButton = { TextButton(onClick = { showMultiPlaylistDialog = false }) { Text("Cancelar") } }
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
                                                selectedSongs.clear()
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
                                    title = { Text("Eliminar Playlists", fontWeight = FontWeight.Bold) },
                                    text = {
                                        if (deletablePlaylists.isEmpty()) {
                                            Text("La playlist de Favoritos no se puede eliminar.")
                                        } else {
                                            Text("¿Eliminar permanentemente ${deletablePlaylists.size} playlists creadas?\n(Las canciones no se borrarán del dispositivo).")
                                        }
                                    },
                                    confirmButton = {
                                        Button(
                                            onClick = {
                                                deletablePlaylists.forEach { playlistRepo.deletePlaylist(it.id) }
                                                Toast.makeText(context, "${deletablePlaylists.size} playlists eliminadas", Toast.LENGTH_SHORT).show()
                                                selectedPlaylists.clear()
                                                showDeletePlaylistsDialog = false
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                        ) { Text("Eliminar") }
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
                                    onSendToGemini = {
                                        showFullScreenPlayer = false
                                        geminiPrompt = "Háblame de la canción ${currentSong!!.title} de ${currentSong!!.artist}. Dame recomendaciones de canciones parecidas."
                                        coroutineScope.launch { pagerState.animateScrollToPage(bottomNavItems.indexOf(Screen.Gemini)) }
                                    },
                                    hasBackgroundImage = hasBackgroundImage
                                )
                            }
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