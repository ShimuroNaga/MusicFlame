package com.music.musicflame.ui.screens

import android.provider.MediaStore
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.musicflame.LocalAlbumArtShape
import com.music.musicflame.LocalUseRoundCorners
import com.music.musicflame.data.Playlist
import com.music.musicflame.data.PlaylistRepository
import com.music.musicflame.data.Song
import com.music.musicflame.data.TrashRepository
import com.music.musicflame.data.TrashedPlaylist
import com.music.musicflame.data.TrashedSong
import com.music.musicflame.ui.components.AlbumArt
import com.music.musicflame.ui.theme.LocalAppTextColor
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TrashScreen(
    modifier: Modifier = Modifier,
    onSongClick: (Song, List<Song>) -> Unit = { _, _ -> },
    hasBackgroundImage: Boolean = false,
    // --- NUEVOS PARÁMETROS PARA LA SELECCIÓN ---
    selectedSongs: List<Song> = emptyList(),
    onToggleSelection: (Song) -> Unit = {}
) {
    val context = LocalContext.current
    val trashRepo = remember { TrashRepository(context) }
    val playlistRepo = remember { PlaylistRepository(context) }
    val trashedItems = remember { mutableStateListOf<TrashedSong>() }
    val trashedPlaylists = remember { mutableStateListOf<TrashedPlaylist>() }
    val showClearAllDialog = remember { mutableStateOf(false) }

    val isRounded = LocalUseRoundCorners.current
    val albumArtShape = LocalAlbumArtShape.current
    val cardRadius = if (isRounded) 12.dp else 0.dp
    val isSelectionMode = selectedSongs.isNotEmpty()

    // Cargamos los datos de forma segura
    LaunchedEffect(Unit) {
        trashedItems.clear()
        trashedItems.addAll(trashRepo.getTrash())
        trashedPlaylists.clear()
        trashedPlaylists.addAll(trashRepo.getTrashedPlaylists())
        trashRepo.purgeExpired()
    }

    val isEmpty = trashedItems.isEmpty() && trashedPlaylists.isEmpty()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        floatingActionButton = {
            // Ocultamos el botón de vaciar si estamos en modo selección para evitar líos
            if (!isEmpty && !isSelectionMode) {
                ExtendedFloatingActionButton(
                    onClick = { showClearAllDialog.value = true },
                    icon = { Icon(Icons.Filled.DeleteSweep, contentDescription = null) },
                    text = { Text("Vaciar papelera") },
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    ) { padding ->
        if (isEmpty) {
            EmptyTrashView()
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { Spacer(Modifier.height(8.dp)) }

                if (trashedPlaylists.isNotEmpty()) {
                    item {
                        Text(
                            "Playlists",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = LocalAppTextColor.current.copy(alpha = 0.7f)
                        )
                    }

                    items(trashedPlaylists, key = { "playlist_${it.playlist.id}" }) { item ->
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            TrashedPlaylistCard(
                                trashedPlaylist = item,
                                onRestore = {
                                    playlistRepo.restorePlaylist(item.playlist)
                                    trashRepo.restorePlaylistFromTrash(item.playlist.id)
                                    trashedPlaylists.remove(item)
                                },
                                hasBackgroundImage = hasBackgroundImage,
                                cardRadius = cardRadius,
                                albumArtShape = albumArtShape
                            )
                        }
                    }

                    if (trashedItems.isNotEmpty()) {
                        item { Spacer(Modifier.height(8.dp)) }
                        item {
                            Text(
                                "Canciones",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = LocalAppTextColor.current.copy(alpha = 0.7f)
                            )
                        }
                    }
                }

                items(trashedItems, key = { "song_${it.song.id}" }) { item ->
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        TrashItemCard(
                            trashedSong = item,
                            trashRepo = trashRepo,
                            onRestore = {
                                trashRepo.restoreSong(item.song.id)
                                trashedItems.remove(item)
                            },
                            onPlay = {
                                onSongClick(item.song, trashedItems.map { it.song })
                            },
                            hasBackgroundImage = hasBackgroundImage,
                            cardRadius = cardRadius,
                            albumArtShape = albumArtShape,
                            // --- INTEGRACIÓN SELECCIÓN ---
                            isSelected = selectedSongs.contains(item.song),
                            isSelectionMode = isSelectionMode,
                            onToggleSelection = { onToggleSelection(item.song) }
                        )
                    }
                }

                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }

    // DIÁLOGO PARA VACIAR LA PAPELERA
    if (showClearAllDialog.value) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog.value = false },
            icon = {
                Icon(
                    Icons.Filled.DeleteForever,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(48.dp)
                )
            },
            title = { Text("¿Vaciar papelera?", fontWeight = FontWeight.Bold) },
            text = {
                val songCount = trashedItems.size
                val playlistCount = trashedPlaylists.size
                val parts = mutableListOf<String>()
                if (songCount > 0) parts.add("$songCount ${if (songCount == 1) "canción" else "canciones"}")
                if (playlistCount > 0) parts.add("$playlistCount ${if (playlistCount == 1) "playlist" else "playlists"}")
                Text("Se eliminarán permanentemente ${parts.joinToString(" y ")} del dispositivo. Las playlists solo pierden el contenedor, no las canciones. Esta acción no se puede deshacer.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        trashedItems.forEach { item ->
                            // 1. Intento de borrado normal
                            val file = File(item.song.path)
                            if (file.exists()) {
                                file.delete()
                            }

                            // 2. Borrado permanente vía MediaStore (Soluciona el problema en Android 10+)
                            try {
                                val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                                context.contentResolver.delete(
                                    uri,
                                    "${MediaStore.Audio.Media.DATA} = ?",
                                    arrayOf(item.song.path)
                                )
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }

                            // 3. Borrar de la base de datos de la app
                            trashRepo.deleteFromTrash(item.song.id)
                        }
                        trashedItems.clear()

                        // Las playlists en papelera solo se eliminan como contenedor (las canciones no se tocan)
                        trashedPlaylists.forEach { trashRepo.deletePlaylistPermanently(it.playlist.id) }
                        trashedPlaylists.clear()

                        showClearAllDialog.value = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Eliminar todo")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog.value = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrashItemCard(
    trashedSong: TrashedSong,
    trashRepo: TrashRepository,
    onRestore: () -> Unit,
    onPlay: () -> Unit,
    hasBackgroundImage: Boolean = false,
    cardRadius: androidx.compose.ui.unit.Dp = 12.dp,
    albumArtShape: com.music.musicflame.AlbumArtShapeType = com.music.musicflame.AlbumArtShapeType.SQUARE,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    onToggleSelection: () -> Unit = {}
) {
    val daysLeft = trashRepo.daysRemaining(trashedSong.deletedAt)

    // Color de fondo dinámico si está seleccionado
    val containerColor = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
        hasBackgroundImage -> Color.Black.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }

    // Color de texto normal: sigue la preferencia global elegida en Ajustes ("Color de texto").
    // Cuando la card está seleccionada, el fondo pasa a ser primaryContainer (un color sólido),
    // así que ahí seguimos usando onPrimaryContainer para garantizar contraste correcto.
    val normalTextColor = LocalAppTextColor.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(cardRadius))
            .combinedClickable(
                onClick = { if (isSelectionMode) onToggleSelection() else onPlay() },
                onLongClick = { onToggleSelection() }
            ),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(cardRadius)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // INDICADOR DE SELECCIÓN O CARÁTULA
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = "Seleccionado", tint = MaterialTheme.colorScheme.onPrimary)
                }
            } else {
                AlbumArt(trashedSong.song.albumArtUri, 50.dp, 8.dp, albumArtShape)
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                Text(
                    text = trashedSong.song.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else normalTextColor
                )
                Text(
                    text = trashedSong.song.artist,
                    fontSize = 13.sp,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else normalTextColor.copy(alpha = 0.7f),
                    maxLines = 1
                )

                Spacer(Modifier.height(2.dp))

                Text(
                    text = if (daysLeft > 1) "Se elimina en $daysLeft días" else "Se elimina hoy",
                    fontSize = 11.sp,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f) else (if (daysLeft <= 7) MaterialTheme.colorScheme.error else normalTextColor.copy(alpha = 0.7f)),
                    fontWeight = if (daysLeft <= 7) FontWeight.Bold else FontWeight.Normal
                )
            }

            // Ocultamos el botón de restaurar si estamos en modo selección para evitar líos
            if (!isSelectionMode) {
                IconButton(onClick = onRestore, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Filled.Restore, contentDescription = "Restaurar", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

// Tarjeta para una playlist movida a la papelera. Restaurarla solo trae de vuelta el
// contenedor (nombre, orden, carátula) — las canciones nunca se tocaron ni se borraron.
@Composable
fun TrashedPlaylistCard(
    trashedPlaylist: TrashedPlaylist,
    onRestore: () -> Unit,
    hasBackgroundImage: Boolean = false,
    cardRadius: androidx.compose.ui.unit.Dp = 12.dp,
    albumArtShape: com.music.musicflame.AlbumArtShapeType = com.music.musicflame.AlbumArtShapeType.SQUARE
) {
    val context = LocalContext.current
    val trashRepo = remember { TrashRepository(context) }
    val daysLeft = trashRepo.daysRemaining(trashedPlaylist.deletedAt)
    val normalTextColor = LocalAppTextColor.current

    val containerColor = if (hasBackgroundImage) Color.Black.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceContainerHigh

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(cardRadius)),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(cardRadius)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (trashedPlaylist.playlist.customCoverUri != null) {
                AlbumArt(trashedPlaylist.playlist.customCoverUri, 50.dp, 8.dp, albumArtShape)
            } else {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.QueueMusic, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                Text(
                    text = trashedPlaylist.playlist.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = normalTextColor
                )
                Text(
                    text = "${trashedPlaylist.playlist.songIds.size} canciones (a salvo)",
                    fontSize = 13.sp,
                    color = normalTextColor.copy(alpha = 0.7f),
                    maxLines = 1
                )

                Spacer(Modifier.height(2.dp))

                Text(
                    text = if (daysLeft > 1) "Se elimina en $daysLeft días" else "Se elimina hoy",
                    fontSize = 11.sp,
                    color = if (daysLeft <= 7) MaterialTheme.colorScheme.error else normalTextColor.copy(alpha = 0.7f),
                    fontWeight = if (daysLeft <= 7) FontWeight.Bold else FontWeight.Normal
                )
            }

            IconButton(onClick = onRestore, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.Restore, contentDescription = "Restaurar playlist", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
fun EmptyTrashView() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Filled.Delete,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Papelera vacía",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = LocalAppTextColor.current
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Las canciones y playlists eliminadas aparecerán aquí",
            fontSize = 14.sp,
            color = LocalAppTextColor.current.copy(alpha = 0.7f)
        )
    }
}