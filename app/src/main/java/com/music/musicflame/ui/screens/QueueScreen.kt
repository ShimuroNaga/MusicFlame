package com.music.musicflame.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.musicflame.LocalUseRoundCorners
import com.music.musicflame.LocalAlbumArtShape
import com.music.musicflame.data.MusicPlayerManager
import com.music.musicflame.data.Song
import com.music.musicflame.data.loadSongsFromDevice
import com.music.musicflame.ui.components.AlbumArt

// --- Motor de arrastre/reordenamiento de la lista ---
// Patrón estándar para reorder con LazyColumn en Compose (no hay soporte nativo):
// el ítem que se arrastra se "levanta" visualmente con un translationY, y cuando su
// centro cruza el ítem vecino, se dispara onMove(from, to). draggingItemIndex se
// identifica por el índice que YA conocemos desde itemsIndexed (no hace falta
// adivinarlo tocando cualquier parte de la fila), así el arrastre solo puede
// empezar desde el propio icono de "3 puntitos".
private class QueueDragState(
    private val listState: LazyListState,
    private val onMove: (from: Int, to: Int) -> Unit
) {
    var draggingItemIndex by mutableStateOf<Int?>(null)
        private set
    private var draggedDistance by mutableStateOf(0f)
    private var draggingItemInitialOffset by mutableStateOf(0)

    private val draggingItemLayoutInfo
        get() = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == draggingItemIndex }

    val draggingItemOffset: Float
        get() = draggingItemLayoutInfo?.let { item ->
            draggingItemInitialOffset + draggedDistance - item.offset
        } ?: 0f

    fun onDragStart(index: Int) {
        val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } ?: return
        draggingItemIndex = index
        draggingItemInitialOffset = item.offset
        draggedDistance = 0f
    }

    fun onDrag(delta: Offset) {
        draggedDistance += delta.y
        val draggingItem = draggingItemLayoutInfo ?: return
        val startOffset = draggingItem.offset + draggingItemOffset
        val endOffset = startOffset + draggingItem.size
        val middleOffset = (startOffset + endOffset) / 2f

        val targetItem = listState.layoutInfo.visibleItemsInfo.find { item ->
            middleOffset.toInt() in item.offset..(item.offset + item.size) && item.index != draggingItem.index
        }

        if (targetItem != null) {
            onMove(draggingItem.index, targetItem.index)
            draggingItemIndex = targetItem.index
        }
    }

    fun onDragEnd() {
        draggingItemIndex = null
        draggedDistance = 0f
        draggingItemInitialOffset = 0
    }
}

@Composable
private fun rememberQueueDragState(
    listState: LazyListState,
    onMove: (from: Int, to: Int) -> Unit
) = remember(listState) { QueueDragState(listState, onMove) }

// Botón cuadrado flotante, mismo estilo que el usado en SongScreen para la barra inferior.
@Composable
private fun QueueFab(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    hasBackgroundImage: Boolean,
    highlighted: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(48.dp),
        shape = RoundedCornerShape(14.dp),
        color = when {
            highlighted -> MaterialTheme.colorScheme.primary
            hasBackgroundImage -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.92f)
            else -> MaterialTheme.colorScheme.secondaryContainer
        },
        contentColor = if (highlighted) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
        tonalElevation = 3.dp
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription, modifier = Modifier.size(22.dp))
        }
    }
}

/**
 * Pantalla de COLA editable, accesible solo desde el reproductor a pantalla completa.
 * Muestra el orden REAL de reproducción (respeta el mezclar/mix si está activo) y
 * permite reordenarlo a mano en "modo reordenar".
 */
@Composable
fun QueueScreen(
    playerManager: MusicPlayerManager,
    currentSong: Song?,
    adaptiveContentColor: androidx.compose.ui.graphics.Color,
    hasBackgroundImage: Boolean,
    onClose: () -> Unit,
    onSongClick: (Song) -> Unit,
    modifier: Modifier = Modifier
) {
    val isRounded = LocalUseRoundCorners.current
    val albumArtShape = LocalAlbumArtShape.current
    val cardRadius = if (isRounded) 16.dp else 0.dp
    val albumRadius = if (isRounded) 8.dp else 0.dp

    val queue = playerManager.queue
    val isShuffleOn = playerManager.shuffleEnabledState.value

    var reorderModeActive by remember { mutableStateOf(false) }
    var showAddSongsDialog by remember { mutableStateOf(false) }
    var bottomButtonsVisible by remember { mutableStateOf(true) }

    val listState = rememberLazyListState()

    // Mismo patrón que SongScreen: ocultar la barra inferior al bajar, mostrarla al subir.
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

    val dragState = rememberQueueDragState(listState) { from, to ->
        playerManager.moveQueueItem(from, to)
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // --- Cabecera ---
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Volver",
                        tint = adaptiveContentColor
                    )
                }
                Spacer(Modifier.width(4.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Cola de reproducción",
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp,
                        color = adaptiveContentColor
                    )
                    Text(
                        text = if (isShuffleOn) "${queue.size} canciones · mezcladas" else "${queue.size} canciones",
                        fontSize = 12.sp,
                        color = adaptiveContentColor.copy(alpha = 0.6f)
                    )
                }
                if (isShuffleOn) {
                    Icon(
                        imageVector = Icons.Filled.Shuffle,
                        contentDescription = "Mezclado",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                }
            }

            if (queue.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No hay canciones en la cola",
                        color = adaptiveContentColor.copy(alpha = 0.6f)
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    itemsIndexed(queue, key = { _, song -> song.id }) { index, song ->
                        val isCurrent = currentSong?.id == song.id
                        val isBeingDragged = dragState.draggingItemIndex == index

                        val containerColor = when {
                            isCurrent -> MaterialTheme.colorScheme.primaryContainer
                            hasBackgroundImage -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.70f)
                            else -> MaterialTheme.colorScheme.surfaceContainerHigh
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .graphicsLayer {
                                    translationY = if (isBeingDragged) dragState.draggingItemOffset else 0f
                                    shadowElevation = if (isBeingDragged) 12f else 0f
                                }
                                .clip(RoundedCornerShape(cardRadius))
                                .clickable(enabled = !reorderModeActive) { onSongClick(song) },
                            colors = CardDefaults.cardColors(containerColor = containerColor),
                            elevation = CardDefaults.cardElevation(defaultElevation = if (hasBackgroundImage || isBeingDragged) 0.dp else 4.dp),
                            shape = RoundedCornerShape(cardRadius)
                        ) {
                            Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                // --- Manija de arrastre: los "3 puntitos" a la izquierda de la carátula.
                                // Solo aparece (y solo responde al gesto) en "modo reordenar". ---
                                AnimatedVisibility(visible = reorderModeActive) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .pointerInput(index) {
                                                detectDragGestures(
                                                    onDragStart = { dragState.onDragStart(index) },
                                                    onDrag = { change, dragAmount ->
                                                        change.consume()
                                                        dragState.onDrag(dragAmount)
                                                    },
                                                    onDragEnd = { dragState.onDragEnd() },
                                                    onDragCancel = { dragState.onDragEnd() }
                                                )
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.DragIndicator,
                                            contentDescription = "Arrastrar para reordenar",
                                            tint = adaptiveContentColor.copy(alpha = 0.6f)
                                        )
                                    }
                                }

                                AlbumArt(song.albumArtUri, 50.dp, albumRadius, albumArtShape)

                                Column(modifier = Modifier.padding(start = 16.dp).weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (isCurrent) {
                                            Icon(
                                                imageVector = Icons.Filled.GraphicEq,
                                                contentDescription = "Sonando ahora",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(Modifier.width(6.dp))
                                        }
                                        Text(
                                            text = song.title,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer else adaptiveContentColor
                                        )
                                    }
                                    Text(
                                        text = song.artist,
                                        fontSize = 13.sp,
                                        color = (if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer else adaptiveContentColor).copy(alpha = 0.7f),
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

        AnimatedVisibility(
            visible = bottomButtonsVisible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // El "+" se oculta mientras el modo reordenar está activo (no tiene
                // sentido agregar canciones a mitad de un reordenamiento). Se deja un
                // espacio del mismo tamaño para que el botón de reordenar no se mueva.
                if (reorderModeActive) {
                    Spacer(modifier = Modifier.size(48.dp))
                } else {
                    QueueFab(
                        icon = Icons.Filled.Add,
                        contentDescription = "Agregar canciones a la cola",
                        hasBackgroundImage = hasBackgroundImage,
                        onClick = { showAddSongsDialog = true }
                    )
                }

                QueueFab(
                    icon = Icons.Filled.Reorder,
                    contentDescription = "Reordenar",
                    hasBackgroundImage = hasBackgroundImage,
                    highlighted = reorderModeActive,
                    onClick = { reorderModeActive = !reorderModeActive }
                )
            }
        }
    }

    if (showAddSongsDialog) {
        AddSongsToQueueDialog(
            alreadyInQueue = queue.map { it.id }.toSet(),
            onDismiss = { showAddSongsDialog = false },
            onConfirm = { selected ->
                playerManager.addToQueue(selected)
                showAddSongsDialog = false
            }
        )
    }
}

// Selector simple de canciones del dispositivo para agregar a la cola actual.
@Composable
private fun AddSongsToQueueDialog(
    alreadyInQueue: Set<Long>,
    onDismiss: () -> Unit,
    onConfirm: (List<Song>) -> Unit
) {
    val context = LocalContext.current
    val allSongs = remember { loadSongsFromDevice(context) }
    var query by remember { mutableStateOf("") }
    val selectedIds = remember { mutableStateListOf<Long>() }

    val filtered = remember(query, allSongs) {
        if (query.isBlank()) allSongs
        else allSongs.filter {
            it.title.contains(query, ignoreCase = true) || it.artist.contains(query, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Agregar a la cola", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.heightIn(max = 420.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Buscar canción o artista") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Search, null) }
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                    itemsIndexed(filtered, key = { _, s -> s.id }) { _, song ->
                        val isSelected = selectedIds.contains(song.id)
                        val alreadyQueued = alreadyInQueue.contains(song.id)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !alreadyQueued) {
                                    if (isSelected) selectedIds.remove(song.id) else selectedIds.add(song.id)
                                }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = isSelected || alreadyQueued, onCheckedChange = null, enabled = !alreadyQueued)
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = song.title,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (alreadyQueued) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f) else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (alreadyQueued) "Ya está en la cola" else song.artist,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = selectedIds.isNotEmpty(),
                onClick = {
                    val songsToAdd = allSongs.filter { selectedIds.contains(it.id) }
                    onConfirm(songsToAdd)
                }
            ) { Text("Agregar (${selectedIds.size})") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
