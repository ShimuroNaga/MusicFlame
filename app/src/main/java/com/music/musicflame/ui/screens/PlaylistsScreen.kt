package com.music.musicflame.ui.screens

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.music.musicflame.LocalUseRoundCorners
import com.music.musicflame.LocalAlbumArtShape
import com.music.musicflame.data.FavoritesRepository
import com.music.musicflame.data.Playlist
import com.music.musicflame.data.PlaylistKind
import com.music.musicflame.data.PlaylistRepository
import com.music.musicflame.data.SmartPlaylistIds
import com.music.musicflame.data.buildMostPlayedPlaylist
import com.music.musicflame.data.buildNeverPlayedPlaylist
import com.music.musicflame.data.loadSongsFromDevice
import com.music.musicflame.ui.components.AlbumArt
import com.music.musicflame.ui.theme.LocalAppTextColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.util.concurrent.TimeUnit
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

fun formatPlaylistDuration(ms: Long): String {
    val hours = TimeUnit.MILLISECONDS.toHours(ms)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
    return if (hours > 0) {
        "$hours h $minutes min"
    } else {
        "$minutes min"
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PlaylistsScreen(
    modifier: Modifier = Modifier,
    onPlaylistClick: (Playlist, PlaylistKind) -> Unit = { _, _ -> },
    onImportClick: () -> Unit = {},
    onChangeCoverClick: (String) -> Unit = {},
    hasBackgroundImage: Boolean = false,
    selectedPlaylists: List<Playlist> = emptyList(),
    onToggleSelection: (Playlist) -> Unit = {},
    // --- MODO DE SELECCIÓN POR TAP (sin necesidad de mantener presionado) ---
    selectionModeActive: Boolean = false,
    onToggleSelectionModeButton: () -> Unit = {}
) {
    val context = LocalContext.current
    val playlistRepo = remember { PlaylistRepository(context) }
    val favoritesRepo = remember { FavoritesRepository(context) }
    LaunchedEffect(Unit) { com.music.musicflame.data.SongLibraryHolder.ensureLoaded(context) }

    val isSelectionMode = selectedPlaylists.isNotEmpty() || selectionModeActive

    val playlists = remember { mutableStateListOf<Playlist>() }
    val displayPlaylists = remember { mutableStateListOf<Playlist>() }
    val showCreateDialog = remember { mutableStateOf(false) }
    val isRefreshing = remember { mutableStateOf(false) }
    val pullState = rememberPullToRefreshState()
    val scope = rememberCoroutineScope()
    val sortType = remember { mutableStateOf(PlaylistSortType.DATE_CREATED) }
    val showSortMenu = remember { mutableStateOf(false) }
    val favoriteCount = remember { mutableStateOf(0) }
    // --- Playlists inteligentes: se recalculan al entrar y en cada "pull to refresh" ---
    val mostPlayedPlaylist = remember { mutableStateOf(Playlist(SmartPlaylistIds.MOST_PLAYED, "Lo Más Sonado", emptyList(), isDefault = true)) }
    val neverPlayedPlaylist = remember { mutableStateOf(Playlist(SmartPlaylistIds.NEVER_PLAYED, "Por Descubrir", emptyList(), isDefault = true)) }

    val isRounded = LocalUseRoundCorners.current
    val fabRadius = if (isRounded) 16.dp else 0.dp
    val dialogRadius = if (isRounded) 28.dp else 0.dp
    val textFieldRadius = if (isRounded) 4.dp else 0.dp

    val playlistToExport = remember { mutableStateOf<Playlist?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("audio/x-mpegurl")
    ) { uri: Uri? ->
        val playlist = playlistToExport.value
        if (uri != null && playlist != null) {
            scope.launch {
                try {
                    val allSongs = com.music.musicflame.data.SongLibraryHolder.songs
                    context.contentResolver.openOutputStream(uri).use { outputStream ->
                        if (outputStream != null) {
                            BufferedWriter(OutputStreamWriter(outputStream)).use { writer ->
                                writer.write("#EXTM3U\n")
                                playlist.songIds.forEach { id ->
                                    val song = allSongs.find { it.id == id }
                                    if (song != null) {
                                        val durationInSeconds = song.duration / 1000
                                        writer.write("#EXTINF:$durationInSeconds,${song.artist} - ${song.title}\n")
                                        writer.write("${song.path}\n")
                                    }
                                }
                            }
                        }
                    }
                    Toast.makeText(context, "Playlist exportada exitosamente", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Error al exportar: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                } finally {
                    playlistToExport.value = null
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        playlists.clear()
        playlists.addAll(playlistRepo.getPlaylists())
        favoriteCount.value = favoritesRepo.getAllFavoriteIds().size
        mostPlayedPlaylist.value = buildMostPlayedPlaylist(context)
        neverPlayedPlaylist.value = buildNeverPlayedPlaylist(context)
        displayPlaylists.clear()
        displayPlaylists.addAll(playlists)
    }

    LaunchedEffect(sortType.value, playlists.size) {
        displayPlaylists.clear()
        displayPlaylists.addAll(
            when (sortType.value) {
                PlaylistSortType.DATE_CREATED -> playlists
                PlaylistSortType.A_Z -> playlists.sortedBy { it.name }
                PlaylistSortType.Z_A -> playlists.sortedByDescending { it.name }
            }
        )
    }

    // --- Mostrar/ocultar los botones inferiores según la dirección del scroll ---
    // Misma lógica que en SongScreen: scroll hacia abajo los oculta, hacia arriba reaparecen.
    val listState = rememberLazyListState()
    var bottomButtonsVisible by remember { mutableStateOf(true) }
    LaunchedEffect(listState) {
        var previousIndex = listState.firstVisibleItemIndex
        var previousOffset = listState.firstVisibleItemScrollOffset
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                val scrollingUp = if (index != previousIndex) index < previousIndex else offset < previousOffset
                val scrollingDown = if (index != previousIndex) index > previousIndex else offset > previousOffset
                if (scrollingDown) bottomButtonsVisible = false
                if (scrollingUp) bottomButtonsVisible = true
                previousIndex = index
                previousOffset = offset
            }
    }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action == "com.music.musicflame.FAVORITES_CHANGED") {
                    favoriteCount.value = favoritesRepo.getAllFavoriteIds().size
                }
            }
        }
        val filter = IntentFilter("com.music.musicflame.FAVORITES_CHANGED")

        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        // Ya vive dentro del Scaffold principal (que reserva status/nav bar). Sin esto
        // reserva la status bar otra vez y deja un hueco vacío arriba de las playlists.
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            PullToRefreshBox(
                isRefreshing = isRefreshing.value,
                onRefresh = {
                    scope.launch {
                        isRefreshing.value = true
                        delay(800)
                        playlists.clear()
                        playlists.addAll(playlistRepo.getPlaylists())
                        favoriteCount.value = favoritesRepo.getAllFavoriteIds().size
                        mostPlayedPlaylist.value = buildMostPlayedPlaylist(context)
                        neverPlayedPlaylist.value = buildNeverPlayedPlaylist(context)
                        displayPlaylists.clear()
                        displayPlaylists.addAll(
                            when (sortType.value) {
                                PlaylistSortType.DATE_CREATED -> playlists
                                PlaylistSortType.A_Z -> playlists.sortedBy { it.name }
                                PlaylistSortType.Z_A -> playlists.sortedByDescending { it.name }
                            }
                        )
                        isRefreshing.value = false
                    }
                },
                state = pullState,
                modifier = Modifier
                    .fillMaxSize(),
                indicator = {
                    PullToRefreshDefaults.Indicator(
                        state = pullState,
                        isRefreshing = isRefreshing.value,
                        color = MaterialTheme.colorScheme.primary,
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
                }
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item { Spacer(modifier = Modifier.height(8.dp)) }

                    item {
                        val favoritesPlaylist = Playlist("favorites", "Favoritos", favoritesRepo.getAllFavoriteIds().toList(), customCoverUri = favoritesRepo.getCoverUri())
                        PlaylistCard(
                            playlist = favoritesPlaylist,
                            songCount = favoriteCount.value,
                            kind = PlaylistKind.FAVORITES,
                            onPlaylistClick = { onPlaylistClick(it, PlaylistKind.FAVORITES) },
                            onChangeCoverClick = { onChangeCoverClick("favorites") },
                            onResetCoverClick = { favoritesRepo.saveCoverUri("") },
                            hasBackgroundImage = hasBackgroundImage,
                            isSelected = selectedPlaylists.contains(favoritesPlaylist),
                            isSelectionMode = isSelectionMode,
                            onToggleSelection = { onToggleSelection(favoritesPlaylist) }
                        )
                    }

                    item {
                        PlaylistCard(
                            playlist = mostPlayedPlaylist.value,
                            songCount = mostPlayedPlaylist.value.songIds.size,
                            kind = PlaylistKind.MOST_PLAYED,
                            onPlaylistClick = { onPlaylistClick(it, PlaylistKind.MOST_PLAYED) },
                            onChangeCoverClick = {},
                            hasBackgroundImage = hasBackgroundImage,
                            isSelected = selectedPlaylists.contains(mostPlayedPlaylist.value),
                            isSelectionMode = isSelectionMode,
                            onToggleSelection = { onToggleSelection(mostPlayedPlaylist.value) }
                        )
                    }

                    item {
                        PlaylistCard(
                            playlist = neverPlayedPlaylist.value,
                            songCount = neverPlayedPlaylist.value.songIds.size,
                            kind = PlaylistKind.NEVER_PLAYED,
                            onPlaylistClick = { onPlaylistClick(it, PlaylistKind.NEVER_PLAYED) },
                            onChangeCoverClick = {},
                            hasBackgroundImage = hasBackgroundImage,
                            isSelected = selectedPlaylists.contains(neverPlayedPlaylist.value),
                            isSelectionMode = isSelectionMode,
                            onToggleSelection = { onToggleSelection(neverPlayedPlaylist.value) }
                        )
                    }

                    if (displayPlaylists.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Filled.MusicNote,
                                        contentDescription = null,
                                        modifier = Modifier.size(64.dp),
                                        tint = LocalAppTextColor.current.copy(alpha = 0.6f)
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = "¡Aún no tienes playlists, llena la app de tus playlists favoritas!",
                                        color = LocalAppTextColor.current,
                                        fontWeight = FontWeight.Medium,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        modifier = Modifier.padding(horizontal = 32.dp)
                                    )
                                }
                            }
                        }
                    }

                    items(displayPlaylists, key = { it.id }) { playlist ->
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            PlaylistCard(
                                playlist = playlist,
                                songCount = playlist.songIds.size,
                                kind = PlaylistKind.REGULAR,
                                onPlaylistClick = { onPlaylistClick(it, PlaylistKind.REGULAR) },
                                onChangeCoverClick = onChangeCoverClick,
                                onResetCoverClick = { playlistRepo.updatePlaylistCover(it, "") },
                                hasBackgroundImage = hasBackgroundImage,
                                isSelected = selectedPlaylists.contains(playlist),
                                isSelectionMode = isSelectionMode,
                                onToggleSelection = { onToggleSelection(playlist) }
                            )
                        }
                    }

                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }

            // --- GRUPO IZQUIERDO: seleccionar (checklist) y ordenar ---
            AnimatedVisibility(
                visible = !isSelectionMode && bottomButtonsVisible,
                modifier = Modifier.align(Alignment.BottomStart),
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FloatingActionButton(
                        onClick = onToggleSelectionModeButton,
                        shape = RoundedCornerShape(fabRadius),
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        elevation = FloatingActionButtonDefaults.elevation()
                    ) {
                        Icon(Icons.Filled.Checklist, contentDescription = "Seleccionar playlists")
                    }

                    Box {
                        FloatingActionButton(
                            onClick = { showSortMenu.value = true },
                            shape = RoundedCornerShape(fabRadius),
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            elevation = FloatingActionButtonDefaults.elevation()
                        ) {
                            Icon(Icons.Filled.Sort, contentDescription = "Ordenar")
                        }

                        DropdownMenu(
                            expanded = showSortMenu.value,
                            onDismissRequest = { showSortMenu.value = false }
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                        RadioButton(selected = sortType.value == PlaylistSortType.DATE_CREATED, onClick = { sortType.value = PlaylistSortType.DATE_CREATED; showSortMenu.value = false })
                                        Spacer(Modifier.width(8.dp))
                                        Text("Fecha creada", color = LocalAppTextColor.current)
                                    }
                                },
                                onClick = { sortType.value = PlaylistSortType.DATE_CREATED; showSortMenu.value = false }
                            )
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                        RadioButton(selected = sortType.value == PlaylistSortType.A_Z, onClick = { sortType.value = PlaylistSortType.A_Z; showSortMenu.value = false })
                                        Spacer(Modifier.width(8.dp))
                                        Text("A - Z", color = LocalAppTextColor.current)
                                    }
                                },
                                onClick = { sortType.value = PlaylistSortType.A_Z; showSortMenu.value = false }
                            )
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                        RadioButton(selected = sortType.value == PlaylistSortType.Z_A, onClick = { sortType.value = PlaylistSortType.Z_A; showSortMenu.value = false })
                                        Spacer(Modifier.width(8.dp))
                                        Text("Z - A", color = LocalAppTextColor.current)
                                    }
                                },
                                onClick = { sortType.value = PlaylistSortType.Z_A; showSortMenu.value = false }
                            )
                        }
                    }
                }
            }

            // --- GRUPO DERECHO: nueva playlist e importar (al mismo nivel que el grupo izquierdo) ---
            AnimatedVisibility(
                visible = !isSelectionMode && bottomButtonsVisible,
                modifier = Modifier.align(Alignment.BottomEnd),
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ExtendedFloatingActionButton(
                        onClick = { showCreateDialog.value = true },
                        icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                        text = { Text("Nueva") },
                        shape = RoundedCornerShape(fabRadius),
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        elevation = FloatingActionButtonDefaults.elevation()
                    )
                    ExtendedFloatingActionButton(
                        onClick = onImportClick,
                        icon = { Icon(Icons.Filled.FileDownload, contentDescription = null) },
                        text = { Text("Importar") },
                        shape = RoundedCornerShape(fabRadius),
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        elevation = FloatingActionButtonDefaults.elevation()
                    )
                }
            }
        }
    }

    if (showCreateDialog.value) {
        val name = remember { mutableStateOf("") }
        AlertDialog(
            shape = RoundedCornerShape(dialogRadius),
            onDismissRequest = { showCreateDialog.value = false },
            title = { Text("Nueva Playlist", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = name.value,
                    onValueChange = { name.value = it },
                    label = { Text("Nombre") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(textFieldRadius)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (name.value.isNotBlank()) {
                            playlistRepo.createPlaylist(name.value.trim())
                            playlists.clear()
                            playlists.addAll(playlistRepo.getPlaylists())
                            displayPlaylists.clear()
                            displayPlaylists.addAll(
                                when (sortType.value) {
                                    PlaylistSortType.DATE_CREATED -> playlists
                                    PlaylistSortType.A_Z -> playlists.sortedBy { it.name }
                                    PlaylistSortType.Z_A -> playlists.sortedByDescending { it.name }
                                }
                            )
                            showCreateDialog.value = false
                        }
                    }
                ) { Text("Crear") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog.value = false }) { Text("Cancelar") }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlaylistCard(
    playlist: Playlist,
    songCount: Int,
    kind: PlaylistKind,
    onPlaylistClick: (Playlist) -> Unit,
    onChangeCoverClick: (String) -> Unit,
    onResetCoverClick: (String) -> Unit = {},
    hasBackgroundImage: Boolean = false,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    onToggleSelection: () -> Unit = {}
) {
    val context = LocalContext.current
    val playlistRepo = remember { PlaylistRepository(context) }
    val showCoverDialog = remember { mutableStateOf(false) }
    // Las playlists inteligentes no tienen carátula personalizable: su ícono
    // (fuego / brújula) es fijo, igual que el corazón de Favoritos.
    val coverIsEditable = kind == PlaylistKind.REGULAR || kind == PlaylistKind.FAVORITES

    val allSongsForDuration = com.music.musicflame.data.SongLibraryHolder.songs
    val totalDurationFormatted = remember(playlist.songIds, allSongsForDuration) {
        val allSongs = allSongsForDuration
        val totalMs = playlist.songIds.sumOf { id ->
            allSongs.find { it.id == id }?.duration ?: 0L
        }
        formatPlaylistDuration(totalMs)
    }

    val isRounded = LocalUseRoundCorners.current
    val albumArtShape = LocalAlbumArtShape.current
    val cardRadius = if (isRounded) 16.dp else 0.dp
    val albumRadius = if (isRounded) 12.dp else 0.dp
    val dialogRadius = if (isRounded) 28.dp else 0.dp

    // <-- CAMBIO APLICADO: Lógica de color de fondo dependiente del tema
    // Rojo vino fijo para Favoritos (no depende de Material You dinámico).
    // Naranja/fuego para "Lo Más Sonado" (a tono con el nombre "MusicFlame") y
    // un azul/teal para "Por Descubrir" (sensación de exploración).
    val favoritesWineRed = Color(0xFF6D1B2A)
    val mostPlayedFlameOrange = Color(0xFF9A4B0C)
    val neverPlayedDiscoveryTeal = Color(0xFF14555C)
    val containerColor = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer
        kind == PlaylistKind.FAVORITES -> if (hasBackgroundImage) favoritesWineRed.copy(alpha = 0.7f) else favoritesWineRed
        kind == PlaylistKind.MOST_PLAYED -> if (hasBackgroundImage) mostPlayedFlameOrange.copy(alpha = 0.7f) else mostPlayedFlameOrange
        kind == PlaylistKind.NEVER_PLAYED -> if (hasBackgroundImage) neverPlayedDiscoveryTeal.copy(alpha = 0.7f) else neverPlayedDiscoveryTeal
        else -> if (hasBackgroundImage) {
            // Si el tema es claro, la tarjeta es blanca; si es oscuro, negra.
            if (MaterialTheme.colorScheme.surface.red > 0.5f) Color.White.copy(alpha = 0.8f)
            else Color.Black.copy(alpha = 0.5f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(cardRadius))
            .combinedClickable(
                onClick = { if (isSelectionMode) onToggleSelection() else onPlaylistClick(playlist) },
                onLongClick = { onToggleSelection() }
            ),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(cardRadius),
        elevation = if (hasBackgroundImage || isSelected) CardDefaults.cardElevation(0.dp) else CardDefaults.cardElevation(4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = if (!isSelected) {
                    Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(albumRadius))
                        .combinedClickable(
                            onClick = { },
                            onLongClick = { if (!isSelectionMode && coverIsEditable) showCoverDialog.value = true }
                        )
                } else {
                    Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(albumRadius))
                },
                contentAlignment = Alignment.Center
            ) {
                if (kind == PlaylistKind.FAVORITES && playlist.customCoverUri == null) {
                    // La tarjeta de Favoritos es vino fijo, no dinámica. Usamos onSurface
                    // (el tono de texto estándar de Material You que usa el resto de la app)
                    // en vez de onTertiaryContainer, que sacaba un matiz raro (verde) del wallpaper.
                    val favoritesIconTint = Color.White
                    Box(modifier = Modifier.size(56.dp).background(Color.Transparent), contentAlignment = Alignment.Center) {
                        Icon(imageVector = Icons.Filled.Favorite, contentDescription = null, modifier = Modifier.size(32.dp), tint = favoritesIconTint)
                    }
                } else if (kind == PlaylistKind.MOST_PLAYED) {
                    Box(modifier = Modifier.size(56.dp).background(Color.Transparent), contentAlignment = Alignment.Center) {
                        Icon(imageVector = Icons.Filled.LocalFireDepartment, contentDescription = null, modifier = Modifier.size(32.dp), tint = Color.White)
                    }
                } else if (kind == PlaylistKind.NEVER_PLAYED) {
                    Box(modifier = Modifier.size(56.dp).background(Color.Transparent), contentAlignment = Alignment.Center) {
                        Icon(imageVector = Icons.Filled.Explore, contentDescription = null, modifier = Modifier.size(32.dp), tint = Color.White)
                    }
                } else if (playlist.customCoverUri != null) {
                    AlbumArt(albumArtUri = playlist.customCoverUri, size = 56.dp, cornerRadius = albumRadius, shape = albumArtShape)
                } else {
                    val firstSongId = playlist.songIds.firstOrNull()
                    if (firstSongId != null) {
                        val allSongs = com.music.musicflame.data.SongLibraryHolder.songs
                        val firstSong = allSongs.find { it.id == firstSongId }
                        AlbumArt(albumArtUri = firstSong?.albumArtUri, size = 56.dp, cornerRadius = albumRadius, shape = albumArtShape)
                    } else {
                        Icon(Icons.Filled.MusicNote, contentDescription = null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }

                // Overlay + check centrado al seleccionar (estilo unificado, igual que al
                // seleccionar una canción manteniendo pulsado): la carátula/ícono se mantiene
                // visible debajo, no se reemplaza por completo.
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(albumRadius))
                            .background(Color.Black.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = "Seleccionada",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                val favoritesTextColor = LocalAppTextColor.current
                Text(
                    text = playlist.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else if (kind != PlaylistKind.REGULAR) favoritesTextColor else LocalAppTextColor.current
                )
                Text(
                    text = "$songCount ${if (songCount == 1) "canción" else "canciones"} • $totalDurationFormatted",
                    fontSize = 13.sp,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f) else if (kind != PlaylistKind.REGULAR) favoritesTextColor.copy(alpha = 0.85f) else LocalAppTextColor.current.copy(alpha = 0.7f)
                )
            }
        }
    }

    if (showCoverDialog.value && coverIsEditable) {
        AlertDialog(
            shape = RoundedCornerShape(dialogRadius),
            onDismissRequest = { showCoverDialog.value = false },
            title = { Text("Carátula de playlist", fontWeight = FontWeight.Bold) },
            text = { Text("Elige una opción para la carátula de \"${playlist.name}\"") },
            confirmButton = {
                Button(onClick = {
                    onChangeCoverClick(playlist.id)
                    showCoverDialog.value = false
                }) { Text("Elegir Imagen/GIF") }
            },
            dismissButton = {
                TextButton(onClick = {
                    onResetCoverClick(playlist.id)
                    showCoverDialog.value = false
                }) { Text("Restablecer") }
            }
        )
    }
}