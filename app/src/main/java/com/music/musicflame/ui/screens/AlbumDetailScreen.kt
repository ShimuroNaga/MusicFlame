package com.music.musicflame.ui.screens

import android.content.Context
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.music.musicflame.LocalAlbumArtShape
import com.music.musicflame.LocalUseRoundCorners
import com.music.musicflame.ui.theme.LocalAppTextColor
import com.music.musicflame.data.Album
import com.music.musicflame.data.Song
import com.music.musicflame.ui.components.AlbumArt

fun exportAlbumToM3U(context: Context, albumName: String, songs: List<Song>): Boolean {
    return try {
        val m3uContent = buildString {
            appendLine("#EXTM3U")
            appendLine("#PLAYLIST:$albumName")
            songs.forEach { song ->
                appendLine("#EXTINF:${song.duration / 1000},${song.artist} - ${song.title}")
                appendLine(song.path)
            }
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val contentValues = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, "$albumName.m3u")
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
            val file = java.io.File(downloadsDir, "$albumName.m3u")
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
fun AlbumDetailScreen(
    album: Album,
    onSongClick: (Song, List<Song>) -> Unit,
    hasBackgroundImage: Boolean = false,
    selectedSongs: List<Song> = emptyList(),
    onToggleSelection: (Song) -> Unit = {},
    selectionModeActive: Boolean = false,
    onToggleSelectionModeButton: () -> Unit = {}
) {
    val isRounded = LocalUseRoundCorners.current
    val albumArtShape = LocalAlbumArtShape.current
    val itemRadius = if (isRounded) 12.dp else 0.dp
    val buttonRadius = if (isRounded) 24.dp else 0.dp

    val isSelectionMode = selectedSongs.isNotEmpty() || selectionModeActive

    val displaySongs = remember { mutableStateListOf<Song>() }
    val sortType = remember { mutableStateOf(SortType.DATE_CREATED) }
    val showSortMenu = remember { mutableStateOf(false) }

    LaunchedEffect(album) {
        displaySongs.clear()
        displaySongs.addAll(album.songs)
    }

    LaunchedEffect(sortType.value, album.songs) {
        displaySongs.clear()
        displaySongs.addAll(
            when (sortType.value) {
                SortType.DATE_CREATED -> album.songs // orden original del álbum (por título, ver AlbumRepository)
                SortType.A_Z -> album.songs.sortedBy { it.title }
                SortType.Z_A -> album.songs.sortedByDescending { it.title }
            }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        // Ya vive dentro del Scaffold principal (que reserva status/nav bar).
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // --- Portada + info del álbum ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AlbumArt(
                    albumArtUri = album.albumArtUri,
                    size = 96.dp,
                    cornerRadius = 12.dp,
                    shape = albumArtShape
                )
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        album.name,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge,
                        color = LocalAppTextColor.current
                    )
                    Text(album.artist, color = LocalAppTextColor.current.copy(alpha = 0.7f))
                    Text(
                        "${album.songCount} canciones",
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
                        val shuffled = album.songs.shuffled()
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
                                        selected = sortType.value == SortType.DATE_CREATED,
                                        onClick = { sortType.value = SortType.DATE_CREATED; showSortMenu.value = false }
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text("Orden original")
                                }
                            },
                            onClick = { sortType.value = SortType.DATE_CREATED; showSortMenu.value = false }
                        )
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

            // --- Lista de canciones ---
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
                        onToggleSelection = { onToggleSelection(song) }
                    )
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}
