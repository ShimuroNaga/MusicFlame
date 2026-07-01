package com.music.musicflame.ui.screens

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.musicflame.LocalUseRoundCorners
import com.music.musicflame.data.*
import com.music.musicflame.ui.components.AlbumArt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SongsScreen(
    modifier: Modifier = Modifier,
    onSongClick: (Song, List<Song>) -> Unit = { _, _ -> },
    hasBackgroundImage: Boolean = false,
    searchQuery: String = "",
    // --- PARÁMETROS PARA LA SELECCIÓN ---
    selectedSongs: List<Song> = emptyList(),
    onToggleSelection: (Song) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val trashRepo = remember { TrashRepository(context) }

    val songs = remember { mutableStateListOf<Song>() }
    val displaySongs = remember { mutableStateListOf<Song>() }

    val isRefreshing = remember { mutableStateOf(false) }
    val pullState = rememberPullToRefreshState()
    val sortType = remember { mutableStateOf(SortType.DATE_CREATED) }
    val showSortMenu = remember { mutableStateOf(false) }

    // Modo Selección Global
    val isSelectionMode = selectedSongs.isNotEmpty()

    val isRounded = LocalUseRoundCorners.current
    val cardRadius = if (isRounded) 16.dp else 0.dp
    val albumRadius = if (isRounded) 8.dp else 0.dp

    val refreshSongs = {
        val trashedIds = trashRepo.getTrash().map { it.song.id }
        val loaded = loadSongsFromDevice(context)
        songs.clear()
        songs.addAll(loaded.filter { it.id !in trashedIds })
    }

    LaunchedEffect(Unit) { refreshSongs() }

    LaunchedEffect(sortType.value, songs.size, searchQuery) {
        val sortedBase = when (sortType.value) {
            SortType.DATE_CREATED -> songs.sortedByDescending { it.dateAdded }
            SortType.A_Z -> songs.sortedBy { it.title.lowercase() }
            SortType.Z_A -> songs.sortedByDescending { it.title.lowercase() }
        }
        displaySongs.clear()
        if (searchQuery.isBlank()) {
            displaySongs.addAll(sortedBase)
        } else {
            displaySongs.addAll(sortedBase.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                        it.artist.contains(searchQuery, ignoreCase = true)
            })
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(), containerColor = Color.Transparent,
        floatingActionButton = {
            if (!isSelectionMode) { // Ocultar al seleccionar
                Box {
                    FloatingActionButton(onClick = { showSortMenu.value = true }, containerColor = if (hasBackgroundImage) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f) else MaterialTheme.colorScheme.secondaryContainer) { Icon(Icons.Filled.Sort, "Ordenar") }
                    DropdownMenu(expanded = showSortMenu.value, onDismissRequest = { showSortMenu.value = false }) {
                        listOf("Fecha creada" to SortType.DATE_CREATED, "A - Z" to SortType.A_Z, "Z - A" to SortType.Z_A).forEach { (label, type) -> DropdownMenuItem(text = { Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = sortType.value == type, onClick = null); Spacer(Modifier.width(8.dp)); Text(label) } }, onClick = { sortType.value = type; showSortMenu.value = false }) }
                    }
                }
            }
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing.value,
            onRefresh = {
                scope.launch {
                    isRefreshing.value = true
                    delay(800)
                    refreshSongs()
                    isRefreshing.value = false
                }
            },
            state = pullState,
            modifier = Modifier.fillMaxSize().padding(padding),
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
            LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { Spacer(modifier = Modifier.height(8.dp)) }

                items(displaySongs, key = { it.id }) { song ->
                    val isSelected = selectedSongs.contains(song)

                    val containerColor = when {
                        isSelected -> MaterialTheme.colorScheme.primaryContainer
                        hasBackgroundImage -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.70f)
                        else -> MaterialTheme.colorScheme.surfaceContainerHigh
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(cardRadius))
                            .combinedClickable(
                                onClick = {
                                    if (isSelectionMode) onToggleSelection(song)
                                    else onSongClick(song, displaySongs)
                                },
                                onLongClick = {
                                    onToggleSelection(song)
                                }
                            ),
                        colors = CardDefaults.cardColors(containerColor = containerColor),
                        elevation = CardDefaults.cardElevation(defaultElevation = if (hasBackgroundImage || isSelected) 0.dp else 4.dp),
                        shape = RoundedCornerShape(cardRadius)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(50.dp)
                                        .clip(RoundedCornerShape(albumRadius))
                                        .background(MaterialTheme.colorScheme.primary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Filled.Check, "Seleccionada", tint = MaterialTheme.colorScheme.onPrimary)
                                }
                            } else {
                                AlbumArt(song.albumArtUri, 50.dp, albumRadius)
                            }

                            Column(modifier = Modifier.padding(start = 16.dp).weight(1f)) {
                                Text(
                                    text = song.title,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else if (hasBackgroundImage) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = song.artist,
                                    fontSize = 13.sp,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }
}