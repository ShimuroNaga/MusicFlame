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
import androidx.compose.runtime.LaunchedEffect
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
import com.music.musicflame.ui.theme.LocalAppTextColor

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
    onToggleSelectionModeButton: () -> Unit = {},
    // NUEVO: id de la canción sonando ahora, para el icono al lado del título.
    currentPlayingSongId: Long? = null
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { com.music.musicflame.data.SongLibraryHolder.ensureLoaded(context) }
    val allSongs = com.music.musicflame.data.SongLibraryHolder.songs
    val albums = remember(allSongs) { groupSongsIntoAlbums(allSongs) }

    Box(modifier = modifier.fillMaxSize()) {
        if (selectedAlbum == null) {
            AlbumGrid(albums = albums, hasBackgroundImage = hasBackgroundImage, onAlbumClick = onAlbumClick)
        } else {
            AlbumDetailScreen(
                album = selectedAlbum,
                onSongClick = { song, list -> onPlaySong(song, list) },
                hasBackgroundImage = hasBackgroundImage,
                selectedSongs = selectedSongs,
                onToggleSelection = onToggleSelection,
                selectionModeActive = selectionModeActive,
                onToggleSelectionModeButton = onToggleSelectionModeButton,
                currentPlayingSongId = currentPlayingSongId
            )
        }
    }
}

@Composable
private fun AlbumGrid(albums: List<Album>, hasBackgroundImage: Boolean, onAlbumClick: (Album) -> Unit) {
    val columns = LocalAlbumGridColumns.current

    if (albums.isEmpty()) {
        val emptyTextColor = LocalAppTextColor.current.copy(alpha = 0.6f)
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Filled.Album,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = emptyTextColor
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "No se encontraron álbumes",
                    color = LocalAppTextColor.current
                )
            }
        }
        return
    }

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
            AlbumCard(album = album, artSize = itemSize, hasBackgroundImage = hasBackgroundImage, onClick = { onAlbumClick(album) })
        }
    }
}

@Composable
private fun AlbumCard(album: Album, artSize: androidx.compose.ui.unit.Dp, hasBackgroundImage: Boolean, onClick: () -> Unit) {
    val titleColor = LocalAppTextColor.current
    val subtitleColor = LocalAppTextColor.current.copy(alpha = 0.7f)

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
                cornerRadius = 12.dp,
                filePath = album.albumArtSourcePath,
                isCustomCover = album.albumArtIsCustom
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            album.name,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = titleColor
        )
        Text(
            "${album.artist} · ${album.songCount} canciones",
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = subtitleColor,
            style = MaterialTheme.typography.bodySmall
        )
    }
}