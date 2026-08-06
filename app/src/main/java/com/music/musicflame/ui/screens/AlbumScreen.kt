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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.music.musicflame.LocalAlbumGridColumns
import com.music.musicflame.data.Album
import com.music.musicflame.data.Song
import com.music.musicflame.data.groupSongsIntoAlbums
import com.music.musicflame.data.loadSongsFromDevice
import com.music.musicflame.ui.components.AlbumArt

/**
 * Pantalla de Álbumes. Agrupa las canciones del dispositivo por álbum
 * (usando AlbumRepository.kt) y las muestra en una grilla; al tocar un
 * álbum se abre AlbumDetailScreen.kt.
 *
 * El álbum seleccionado, igual que la playlist seleccionada, vive en
 * MainActivity para que la barra superior "de afuera" pueda mostrar el
 * nombre del álbum + botón de regresar + exportar, en vez de que esta
 * pantalla dibuje su propia barra encima.
 */
@Composable
fun AlbumScreen(
    modifier: Modifier = Modifier,
    hasBackgroundImage: Boolean = false,
    selectedAlbum: Album? = null,
    onAlbumClick: (Album) -> Unit = {},
    onPlaySong: (Song, List<Song>) -> Unit = { _, _ -> },
    selectedSongs: List<Song> = emptyList(),
    onToggleSelection: (Song) -> Unit = {},
    selectionModeActive: Boolean = false,
    onToggleSelectionModeButton: () -> Unit = {}
) {
    val context = LocalContext.current
    val albums = remember { groupSongsIntoAlbums(loadSongsFromDevice(context)) }

    Box(modifier = modifier.fillMaxSize()) {
        if (selectedAlbum == null) {
            AlbumGrid(albums = albums, onAlbumClick = onAlbumClick)
        } else {
            AlbumDetailScreen(
                album = selectedAlbum,
                onSongClick = { song, list -> onPlaySong(song, list) },
                hasBackgroundImage = hasBackgroundImage,
                selectedSongs = selectedSongs,
                onToggleSelection = onToggleSelection,
                selectionModeActive = selectionModeActive,
                onToggleSelectionModeButton = onToggleSelectionModeButton
            )
        }
    }
}

@Composable
private fun AlbumGrid(albums: List<Album>, onAlbumClick: (Album) -> Unit) {
    val columns = LocalAlbumGridColumns.current

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

    // AlbumArt pinta a un tamaño fijo (no responsivo), así que calculamos
    // manualmente cuánto le toca a cada carátula según cuántas columnas
    // eligió el usuario en Ajustes > Apariencia.
    val configuration = LocalConfiguration.current
    val gridPadding = 16.dp
    val itemSpacing = 16.dp
    val itemSize = (configuration.screenWidthDp.dp - gridPadding * 2 - itemSpacing * (columns - 1)) / columns

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        contentPadding = PaddingValues(gridPadding),
        horizontalArrangement = Arrangement.spacedBy(itemSpacing),
        verticalArrangement = Arrangement.spacedBy(itemSpacing),
        modifier = Modifier.fillMaxSize()
    ) {
        items(albums, key = { it.name + it.artist }) { album ->
            AlbumCard(album = album, artSize = itemSize, onClick = { onAlbumClick(album) })
        }
    }
}

@Composable
private fun AlbumCard(album: Album, artSize: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
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
                size = artSize,
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
