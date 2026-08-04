package com.music.musicflame.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.music.musicflame.data.Album
import com.music.musicflame.data.Song
import com.music.musicflame.data.groupSongsIntoAlbums
import com.music.musicflame.data.loadSongsFromDevice
import com.music.musicflame.ui.components.AlbumArt

/**
 * Pantalla de Álbumes. Agrupa las canciones del dispositivo por álbum
 * (usando AlbumRepository.kt) y las muestra en una grilla; al tocar un
 * álbum se abre AlbumDetailScreen.kt (reproducir/mezclar/ordenar/seleccionar/exportar).
 *
 * Reemplaza lo que antes era la pantalla de Gemini (que usaba Firebase AI
 * Logic y se quitó por completo).
 */
@Composable
fun AlbumScreen(
    modifier: Modifier = Modifier,
    hasBackgroundImage: Boolean = false,
    onPlaySong: (Song, List<Song>) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val albums = remember { groupSongsIntoAlbums(loadSongsFromDevice(context)) }
    var selectedAlbum by remember { mutableStateOf<Album?>(null) }

    // Selección múltiple dentro del detalle de un álbum (se resetea al cambiar de álbum)
    val selectedSongs = remember(selectedAlbum) { mutableStateOf(setOf<Song>()) }
    var selectionModeActive by remember(selectedAlbum) { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        val album = selectedAlbum
        if (album == null) {
            AlbumGrid(albums = albums, onAlbumClick = { selectedAlbum = it })
        } else {
            AlbumDetailScreen(
                album = album,
                onBack = { selectedAlbum = null },
                onSongClick = { song, list -> onPlaySong(song, list) },
                hasBackgroundImage = hasBackgroundImage,
                selectedSongs = selectedSongs.value.toList(),
                onToggleSelection = { song ->
                    selectedSongs.value = if (selectedSongs.value.contains(song)) {
                        selectedSongs.value - song
                    } else {
                        selectedSongs.value + song
                    }
                },
                selectionModeActive = selectionModeActive,
                onToggleSelectionModeButton = {
                    selectionModeActive = !selectionModeActive
                    if (!selectionModeActive) selectedSongs.value = emptySet()
                }
            )
        }
    }
}

@Composable
private fun AlbumGrid(albums: List<Album>, onAlbumClick: (Album) -> Unit) {
    if (albums.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Filled.Album,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Spacer(Modifier.height(8.dp))
                Text("No se encontraron álbumes", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(albums, key = { it.name + it.artist }) { album ->
            AlbumCard(album = album, onClick = { onAlbumClick(album) })
        }
    }
}

@Composable
private fun AlbumCard(album: Album, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            contentAlignment = Alignment.Center
        ) {
            AlbumArt(
                albumArtUri = album.albumArtUri,
                size = 160.dp,
                cornerRadius = 12.dp
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            album.name,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            "${album.artist} · ${album.songCount} canciones",
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
    }
}
