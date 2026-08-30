package com.music.musicflame.ui.screens

import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.musicflame.LocalUseRoundCorners
import com.music.musicflame.LocalAlbumArtShape
import com.music.musicflame.data.*
import com.music.musicflame.ui.components.AlbumArt
import com.music.musicflame.ui.theme.LocalAppTextColor // <-- Import agregado
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

fun exportToM3U(context: Context, playlist: Playlist): Boolean {
    return try {
        val allSongs = SongLibraryHolder.songs
        val playlistSongs = allSongs.filter { it.id in playlist.songIds }

        val m3uContent = buildString {
            appendLine("#EXTM3U")
            appendLine("#PLAYLIST:${playlist.name}")
            playlistSongs.forEach { song ->
                appendLine("#EXTINF:${song.duration / 1000},${song.artist} - ${song.title}")
                appendLine(song.path)
            }
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val contentValues = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, "${playlist.name}.m3u")
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "audio/x-mpegurl")
                put(
                    android.provider.MediaStore.MediaColumns.RELATIVE_PATH,
                    android.os.Environment.DIRECTORY_DOWNLOADS
                )
            }
            val uri = resolver.insert(
                android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                contentValues
            )
            uri?.let {
                resolver.openOutputStream(it)?.use { outputStream ->
                    outputStream.write(m3uContent.toByteArray())
                }
            }
        } else {
            @Suppress("DEPRECATION")
            val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_DOWNLOADS
            )
            val file = java.io.File(downloadsDir, "${playlist.name}.m3u")
            file.writeText(m3uContent)
        }

        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    playlist: Playlist,
    kind: PlaylistKind,
    onBack: () -> Unit,
    onSongClick: (Song, List<Song>) -> Unit,
    hasBackgroundImage: Boolean = false,
    selectedSongs: List<Song> = emptyList(),
    onToggleSelection: (Song) -> Unit = {},
    // --- MODO DE SELECCIÓN POR TAP (sin necesidad de mantener presionado) ---
    selectionModeActive: Boolean = false,
    onToggleSelectionModeButton: () -> Unit = {},
    // NUEVO: id de la canción que está sonando ahora mismo (playerManager.currentSong?.id),
    // para pintar el icono de "sonando" al lado de su título en la lista.
    currentPlayingSongId: Long? = null
) {
    val context = LocalContext.current
    val favoritesRepo = remember { FavoritesRepository(context) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val isRounded = LocalUseRoundCorners.current
    val albumArtShape = LocalAlbumArtShape.current
    val itemRadius = if (isRounded) 12.dp else 0.dp
    val buttonRadius = if (isRounded) 24.dp else 0.dp // Aumentado para un look más "píldora"
    val fabRadius = if (isRounded) 16.dp else 0.dp

    val songs = remember { mutableStateListOf<Song>() }
    val displaySongs = remember { mutableStateListOf<Song>() }
    val sortType = remember { mutableStateOf(SortType.DATE_CREATED) }
    val showSortMenu = remember { mutableStateOf(false) }

    val isSelectionMode = selectedSongs.isNotEmpty() || selectionModeActive

    // SOLUCIÓN AL ORDENAMIENTO: Guardamos el orden original explícitamente.
    val originalOrder = remember { mutableListOf<Long>() }

    LaunchedEffect(Unit) {
        SongLibraryHolder.ensureLoaded(context)
        val allSongs = SongLibraryHolder.songs
        val songIds = when (kind) {
            PlaylistKind.FAVORITES -> favoritesRepo.getAllFavoriteIds()
            // Se recalculan aquí también (no solo en PlaylistsScreen) para que si el
            // usuario entra al detalle justo después de escuchar algo, vea el dato fresco.
            PlaylistKind.MOST_PLAYED -> buildMostPlayedPlaylist(context).songIds
            PlaylistKind.NEVER_PLAYED -> buildNeverPlayedPlaylist(context).songIds
            PlaylistKind.REGULAR -> playlist.songIds
        }

        // Mantener el orden exacto en el que fueron guardados
        val orderedSongs = songIds.mapNotNull { id -> allSongs.find { it.id == id } }

        originalOrder.clear()
        originalOrder.addAll(songIds)

        songs.clear()
        songs.addAll(orderedSongs)

        displaySongs.clear()
        displaySongs.addAll(orderedSongs)
    }

    LaunchedEffect(sortType.value, songs.size) {
        displaySongs.clear()
        displaySongs.addAll(
            when (sortType.value) {
                // Ahora fuerza el re-ordenamiento según los IDs originales
                SortType.DATE_CREATED -> songs.sortedBy { originalOrder.indexOf(it.id) }
                SortType.A_Z -> songs.sortedBy { it.title }
                SortType.Z_A -> songs.sortedByDescending { it.title }
            }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        // Ya vive dentro del Scaffold principal (que reserva status/nav bar); sin esto
        // reserva la status bar otra vez y deja un hueco vacío arriba de la lista.
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // NUEVO LOOK: Menú superior limpio y moderno
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Botón de Play Principal (Destacado)
                    Button(
                        onClick = {
                            if (displaySongs.isNotEmpty()) {
                                onSongClick(displaySongs.first(), displaySongs)
                            }
                        },
                        modifier = Modifier.weight(1f).height(50.dp),
                        enabled = songs.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(buttonRadius)
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Reproducir", fontWeight = FontWeight.Bold) // No se toca por estar en botón primario
                    }

                    // Botón Mezclar (Secundario)
                    AnimatedActionButton(
                        icon = Icons.Filled.Shuffle,
                        enabled = songs.isNotEmpty(),
                        onClick = {
                            val shuffled = songs.shuffled()
                            displaySongs.clear()
                            displaySongs.addAll(shuffled)
                            if (shuffled.isNotEmpty()) {
                                onSongClick(shuffled.first(), shuffled)
                            }
                        },
                        hasBackgroundImage = hasBackgroundImage,
                        radius = buttonRadius
                    )

                    // Botón Ordenar (Secundario) - antes era un FAB flotante, ahora vive en esta fila
                    Box {
                        AnimatedActionButton(
                            icon = Icons.Filled.Sort,
                            enabled = songs.isNotEmpty(),
                            onClick = { showSortMenu.value = true },
                            hasBackgroundImage = hasBackgroundImage,
                            radius = buttonRadius
                        )

                        DropdownMenu(
                            expanded = showSortMenu.value,
                            onDismissRequest = { showSortMenu.value = false }
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        RadioButton(
                                            selected = sortType.value == SortType.DATE_CREATED,
                                            onClick = {
                                                sortType.value = SortType.DATE_CREATED
                                                showSortMenu.value = false
                                            }
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text("Orden original", color = LocalAppTextColor.current)
                                    }
                                },
                                onClick = {
                                    sortType.value = SortType.DATE_CREATED
                                    showSortMenu.value = false
                                }
                            )

                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        RadioButton(
                                            selected = sortType.value == SortType.A_Z,
                                            onClick = {
                                                sortType.value = SortType.A_Z
                                                showSortMenu.value = false
                                            }
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text("A - Z", color = LocalAppTextColor.current)
                                    }
                                },
                                onClick = {
                                    sortType.value = SortType.A_Z
                                    showSortMenu.value = false
                                }
                            )

                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        RadioButton(
                                            selected = sortType.value == SortType.Z_A,
                                            onClick = {
                                                sortType.value = SortType.Z_A
                                                showSortMenu.value = false
                                            }
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text("Z - A", color = LocalAppTextColor.current)
                                    }
                                },
                                onClick = {
                                    sortType.value = SortType.Z_A
                                    showSortMenu.value = false
                                }
                            )
                        }
                    }

                    // Botón Seleccionar (Secundario) - antes era un FAB flotante, ahora vive en esta fila
                    AnimatedActionButton(
                        icon = Icons.Filled.Checklist,
                        enabled = songs.isNotEmpty(),
                        onClick = onToggleSelectionModeButton,
                        hasBackgroundImage = hasBackgroundImage,
                        radius = buttonRadius
                    )
                }
            }

            if (songs.isEmpty() && kind == PlaylistKind.MOST_PLAYED) {
                Box(modifier = Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Todavía no tienes canciones reproducidas.\nEscucha algo y vuelve por aquí.",
                        color = LocalAppTextColor.current.copy(alpha = 0.6f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
            } else if (songs.isEmpty() && kind == PlaylistKind.NEVER_PLAYED) {
                Box(modifier = Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = "¡Ya le diste play a todo tu catálogo!",
                        color = LocalAppTextColor.current.copy(alpha = 0.6f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { Spacer(Modifier.height(4.dp)) }

                items(displaySongs, key = { it.id }) { song ->
                    SongItemCard(
                        song = song,
                        onClick = { onSongClick(song, displaySongs) },
                        onDelete = if (kind == PlaylistKind.FAVORITES) {
                            {
                                favoritesRepo.removeFavorite(song.id)
                                songs.remove(song)
                                displaySongs.remove(song)
                                originalOrder.remove(song.id)
                            }
                        } else null,
                        hasBackgroundImage = hasBackgroundImage,
                        radius = itemRadius,
                        albumArtShape = albumArtShape,
                        isSelected = selectedSongs.contains(song),
                        isSelectionMode = isSelectionMode,
                        onToggleSelection = { onToggleSelection(song) },
                        isCurrentlyPlaying = currentPlayingSongId != null && currentPlayingSongId == song.id
                    )
                }

                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

// NUEVO LOOK PARA BOTONES SECUNDARIOS (REDONDOS Y COMPACTOS)
@Composable
fun AnimatedActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    hasBackgroundImage: Boolean = false,
    radius: androidx.compose.ui.unit.Dp = 24.dp
) {
    var pressed by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.85f else 1f,
        animationSpec = tween(100),
        label = "scale"
    )

    val containerColor by animateColorAsState(
        targetValue = if (enabled) {
            if (hasBackgroundImage) MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.8f)
            else MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        },
        animationSpec = tween(200),
        label = "color"
    )

    Box(
        modifier = modifier
            .size(50.dp)
            .scale(scale)
            .clip(RoundedCornerShape(radius))
            .background(containerColor)
            .clickable(enabled = enabled) {
                pressed = true
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        LaunchedEffect(pressed) {
            if (pressed) {
                delay(150)
                pressed = false
            }
        }

        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongItemCard(
    song: Song,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null,
    hasBackgroundImage: Boolean = false,
    radius: androidx.compose.ui.unit.Dp = 12.dp,
    albumArtShape: com.music.musicflame.AlbumArtShapeType = com.music.musicflame.AlbumArtShapeType.SQUARE,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    onToggleSelection: (() -> Unit)? = null,
    // NUEVO: true cuando esta es la canción que está sonando ahora mismo.
    isCurrentlyPlaying: Boolean = false
) {
    // Animación suave del color de fondo al ser seleccionado
    // <-- CAMBIO APLICADO: Lógica de color de fondo dependiente del tema
    val backgroundColor by animateColorAsState(
        targetValue = when {
            isSelected -> MaterialTheme.colorScheme.primaryContainer
            hasBackgroundImage -> {
                if (MaterialTheme.colorScheme.surface.red > 0.5f) Color.White.copy(alpha = 0.8f)
                else Color.Black.copy(alpha = 0.5f)
            }
            else -> MaterialTheme.colorScheme.surfaceContainerHigh
        },
        label = "selectionColor"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(radius))
            .combinedClickable(
                onClick = {
                    if (isSelectionMode && onToggleSelection != null) {
                        onToggleSelection()
                    } else {
                        onClick()
                    }
                },
                onLongClick = {
                    if (onToggleSelection != null) {
                        onToggleSelection()
                    }
                }
            ),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        ),
        shape = RoundedCornerShape(radius),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (hasBackgroundImage || isSelected) 0.dp else 4.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Reemplazamos momentáneamente el AlbumArt si está seleccionado por un Check
            Box(contentAlignment = Alignment.Center) {
                AlbumArt(
                    albumArtUri = song.albumArtUri,
                    size = 48.dp,
                    cornerRadius = if (radius > 0.dp) 8.dp else 0.dp,
                    shape = albumArtShape,
                    filePath = song.path,
                    isCustomCover = song.hasCustomCover
                )
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(if (radius > 0.dp) 8.dp else 0.dp))
                            .background(Color.Black.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = "Seleccionado",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isCurrentlyPlaying) {
                        com.music.musicflame.ui.components.NowPlayingIndicator(
                            modifier = Modifier.height(14.dp),
                            color = com.music.musicflame.ui.theme.LocalNowPlayingIndicatorColor.current
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        text = song.title,
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        // <-- Aplicando LocalAppTextColor
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else LocalAppTextColor.current
                    )
                }
                Text(
                    text = song.artist,
                    fontSize = 13.sp,
                    // <-- Aplicando LocalAppTextColor con transparencia para el texto secundario
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else LocalAppTextColor.current.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Ocultamos el botón de borrar si estamos en modo selección para evitar accidentes
            if (onDelete != null && !isSelectionMode) {
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Eliminar",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}