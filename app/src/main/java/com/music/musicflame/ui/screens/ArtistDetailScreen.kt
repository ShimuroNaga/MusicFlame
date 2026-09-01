package com.music.musicflame.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.music.musicflame.AlbumArtShapeType
import com.music.musicflame.LocalAlbumArtShape
import com.music.musicflame.LocalUseRoundCorners
import com.music.musicflame.data.Artist
import com.music.musicflame.data.Song
import com.music.musicflame.ui.components.AlbumArt
import com.music.musicflame.ui.theme.LocalAppTextColor

// Clon de AlbumDetailScreen.kt adaptado a Artist. Diferencias a propósito:
// - El "avatar" del artista siempre se pinta CIRCLE, sin importar la forma de
//   carátula que el usuario haya elegido en Ajustes (LocalAlbumArtShape), para
//   distinguir visualmente artista de álbum de un vistazo.
// - La lista de canciones es del artista completo (todas sus canciones, de
//   todos sus álbumes), ordenadas A-Z por defecto en vez de "orden original
//   de álbum" (un artista no tiene un único orden natural).
// - El subtítulo muestra cantidad de álbumes + canciones en vez de solo canciones.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistDetailScreen(
    artist: Artist,
    onSongClick: (Song, List<Song>) -> Unit,
    hasBackgroundImage: Boolean = false,
    selectedSongs: List<Song> = emptyList(),
    onToggleSelection: (Song) -> Unit = {},
    selectionModeActive: Boolean = false,
    onToggleSelectionModeButton: () -> Unit = {},
    currentPlayingSongId: Long? = null
) {
    val isRounded = LocalUseRoundCorners.current
    val albumArtShape = LocalAlbumArtShape.current
    val itemRadius = if (isRounded) 12.dp else 0.dp
    val buttonRadius = if (isRounded) 24.dp else 0.dp

    val isSelectionMode = selectedSongs.isNotEmpty() || selectionModeActive

    val displaySongs = remember { mutableStateListOf<Song>() }
    val sortType = remember { mutableStateOf(SortType.A_Z) }
    val showSortMenu = remember { mutableStateOf(false) }

    LaunchedEffect(artist) {
        displaySongs.clear()
        displaySongs.addAll(artist.songs.sortedBy { it.title })
    }

    LaunchedEffect(sortType.value, artist.songs) {
        displaySongs.clear()
        displaySongs.addAll(
            when (sortType.value) {
                SortType.DATE_CREATED -> artist.songs.sortedByDescending { it.dateAdded }
                SortType.A_Z -> artist.songs.sortedBy { it.title }
                SortType.Z_A -> artist.songs.sortedByDescending { it.title }
            }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // --- Avatar + info del artista ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AlbumArt(
                    albumArtUri = artist.albumArtUri,
                    size = 96.dp,
                    cornerRadius = 48.dp,
                    shape = AlbumArtShapeType.CIRCLE,
                    filePath = artist.albumArtSourcePath,
                    isCustomCover = artist.albumArtIsCustom
                )
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        artist.name,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge,
                        color = LocalAppTextColor.current
                    )
                    Text(
                        "${artist.albumCount} álbumes · ${artist.songCount} canciones",
                        color = LocalAppTextColor.current.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // --- Fila de acciones: Reproducir / Mezclar / Ordenar / Seleccionar ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        if (displaySongs.isNotEmpty()) onSongClick(displaySongs.first(), displaySongs)
                    },
                    modifier = Modifier.weight(1f).height(50.dp),
                    enabled = displaySongs.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(buttonRadius)
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Reproducir", fontWeight = FontWeight.Bold)
                }

                AnimatedActionButton(
                    icon = Icons.Filled.Shuffle,
                    enabled = displaySongs.isNotEmpty(),
                    onClick = {
                        val shuffled = artist.songs.shuffled()
                        displaySongs.clear()
                        displaySongs.addAll(shuffled)
                        if (shuffled.isNotEmpty()) onSongClick(shuffled.first(), shuffled)
                    },
                    hasBackgroundImage = hasBackgroundImage,
                    radius = buttonRadius
                )

                Box {
                    AnimatedActionButton(
                        icon = Icons.Filled.Sort,
                        enabled = displaySongs.isNotEmpty(),
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
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(
                                        selected = sortType.value == SortType.A_Z,
                                        onClick = { sortType.value = SortType.A_Z; showSortMenu.value = false }
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text("A - Z")
                                }
                            },
                            onClick = { sortType.value = SortType.A_Z; showSortMenu.value = false }
                        )
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(
                                        selected = sortType.value == SortType.Z_A,
                                        onClick = { sortType.value = SortType.Z_A; showSortMenu.value = false }
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text("Z - A")
                                }
                            },
                            onClick = { sortType.value = SortType.Z_A; showSortMenu.value = false }
                        )
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(
                                        selected = sortType.value == SortType.DATE_CREATED,
                                        onClick = { sortType.value = SortType.DATE_CREATED; showSortMenu.value = false }
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text("Agregadas recientemente")
                                }
                            },
                            onClick = { sortType.value = SortType.DATE_CREATED; showSortMenu.value = false }
                        )
                    }
                }

                AnimatedActionButton(
                    icon = Icons.Filled.Checklist,
                    enabled = displaySongs.isNotEmpty(),
                    onClick = onToggleSelectionModeButton,
                    hasBackgroundImage = hasBackgroundImage,
                    radius = buttonRadius
                )
            }

            // --- Lista de canciones (todos los álbumes del artista) ---
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(displaySongs, key = { it.id }) { song ->
                    SongItemCard(
                        song = song,
                        onClick = { onSongClick(song, displaySongs) },
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
