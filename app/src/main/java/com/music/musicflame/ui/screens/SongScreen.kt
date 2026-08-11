package com.music.musicflame.ui.screens

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.musicflame.LocalUseRoundCorners
import com.music.musicflame.LocalAlbumArtShape
import com.music.musicflame.SearchMode
import com.music.musicflame.data.*
import com.music.musicflame.ui.components.AlbumArt
import com.music.musicflame.ui.theme.LocalAppTextColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// Botón cuadrado, con elevación, para la barra flotante de selección/orden.
@Composable
private fun MFIconButton(
    icon: ImageVector,
    contentDescription: String,
    hasBackgroundImage: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(48.dp),
        shape = RoundedCornerShape(14.dp),
        color = if (hasBackgroundImage) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.92f) else MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        tonalElevation = 3.dp
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription, modifier = Modifier.size(22.dp))
        }
    }
}

// Barrita de scroll simple, tipo "scrollbar" de escritorio, que indica en qué parte
// de la lista estás. Solo se muestra si hay más canciones de las que caben en pantalla.
@Composable
private fun ListScrollbar(listState: LazyListState, modifier: Modifier = Modifier) {
    val layoutInfo = listState.layoutInfo
    val totalItems = layoutInfo.totalItemsCount
    val visibleItems = layoutInfo.visibleItemsInfo.size

    if (totalItems == 0 || visibleItems == 0 || visibleItems >= totalItems) return

    BoxWithConstraints(modifier = modifier.width(4.dp)) {
        val density = LocalDensity.current
        val trackHeightPx = with(density) { maxHeight.toPx() }
        val thumbRatio = (visibleItems.toFloat() / totalItems.toFloat()).coerceIn(0.08f, 1f)
        val thumbHeightPx = trackHeightPx * thumbRatio
        val maxScrollableItems = (totalItems - visibleItems).coerceAtLeast(1)
        val scrollProgress = (listState.firstVisibleItemIndex.toFloat() / maxScrollableItems.toFloat()).coerceIn(0f, 1f)
        val offsetPx = (trackHeightPx - thumbHeightPx) * scrollProgress

        Box(
            modifier = Modifier
                .offset { IntOffset(0, offsetPx.roundToInt()) }
                .width(4.dp)
                .height(with(density) { thumbHeightPx.toDp() })
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f))
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SongsScreen(
    modifier: Modifier = Modifier,
    onSongClick: (Song, List<Song>) -> Unit = { _, _ -> },
    hasBackgroundImage: Boolean = false,
    searchQuery: String = "",
    searchMode: SearchMode = SearchMode.LOCAL,
    selectedSongs: List<Song> = emptyList(),
    onToggleSelection: (Song) -> Unit = {},
    youtubeRecommendedSongs: List<Song> = emptyList(),
    isYoutubeLoggedIn: Boolean = false,
    // --- NUEVOS PARÁMETROS ---
    favoriteIds: Set<Long> = emptySet(),
    onToggleFavorite: (Song) -> Unit = {},
    syncedFileNames: Set<String> = emptySet(),
    // --- MODO DE SELECCIÓN POR TAP (sin necesidad de mantener presionado) ---
    selectionModeActive: Boolean = false,
    onToggleSelectionModeButton: () -> Unit = {},
    // --- Aplica cambios de carátula/nombre AL INSTANTE sobre la lista ya cargada,
    // sin releer todo el dispositivo. patchTrigger debe cambiar (ej. un contador
    // incremental) cada vez que hay nuevos pendingPatches que aplicar. ---
    patchTrigger: Int = 0,
    pendingPatches: List<SongEditPatch> = emptyList(),
    // Cambia (contador incremental) cada vez que la letra guardada de alguna
    // canción se modifica desde otra pantalla (ej. al borrarla o encontrarla
    // desde el reproductor a pantalla completa), para refrescar el icono de
    // "letra disponible" sin tener que recargar toda la lista.
    lyricsRefreshTrigger: Int = 0
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val trashRepo = remember { TrashRepository(context) }

    val songs = remember { mutableStateListOf<Song>() }
    val displaySongs = remember { mutableStateListOf<Song>() }

    var errorMessage by remember { mutableStateOf<String?>(null) }

    val isRefreshing = remember { mutableStateOf(false) }
    val pullState = rememberPullToRefreshState()
    val sortType = remember { mutableStateOf(SortType.DATE_CREATED) }
    val showSortMenu = remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // --- Mostrar/ocultar los botones inferiores según la dirección del scroll ---
    // Scroll down (hacia abajo) = se ocultan. Scroll up (hacia arriba) = reaparecen.
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

    val isSelectionMode = selectedSongs.isNotEmpty() || selectionModeActive

    val isRounded = LocalUseRoundCorners.current
    val albumArtShape = LocalAlbumArtShape.current
    val cardRadius = if (isRounded) 16.dp else 0.dp
    val albumRadius = if (isRounded) 8.dp else 0.dp

    // Color de texto normal para toda la pantalla, controlado desde Ajustes > Color de texto.
    val normalTextColor = LocalAppTextColor.current

    val refreshSongs = {
        val trashedIds = trashRepo.getTrash().map { it.song.id }
        val loaded = loadSongsFromDevice(context)
        songs.clear()
        songs.addAll(loaded.filter { it.id !in trashedIds })
    }

    val lyricsRepo = remember { LyricsRepository(context) }
    // IDs de canciones que sabemos tienen letra disponible (guardada de antes o
    // encontrada por el escaneo automático). Vive en memoria para no releer el
    // JSON guardado en cada recomposición de cada fila de la lista.
    val lyricsAvailableIds = remember { mutableStateOf(setOf<Long>()) }

    LaunchedEffect(Unit) {
        refreshSongs()
        lyricsAvailableIds.value = songs.filter { lyricsRepo.hasLyrics(it.id) }.map { it.id }.toSet()
        // Busca en segundo plano (sin bloquear la UI ni pedir nada al usuario)
        // qué canciones de la biblioteca tienen letra disponible. Se guía por
        // el nombre de la canción aunque el mp3 no tenga el artista en sus
        // etiquetas, y no repite canciones ya revisadas en una corrida anterior.
        scope.launch {
            lyricsRepo.scanLibrary(songs.toList(), onFound = { found ->
                lyricsAvailableIds.value = lyricsAvailableIds.value + found.id
            })
        }
    }

    // Aplica los cambios de carátula/nombre directo sobre la lista en memoria (instantáneo,
    // sin volver a consultar MediaStore). Se dispara solo cuando patchTrigger cambia.
    LaunchedEffect(patchTrigger) {
        if (patchTrigger > 0 && pendingPatches.isNotEmpty()) {
            pendingPatches.forEach { patch ->
                val idx = songs.indexOfFirst { it.id == patch.songId }
                if (idx != -1) {
                    var updated = songs[idx]
                    if (patch.newTitle != null) updated = updated.copy(title = patch.newTitle)
                    if (patch.newCoverUri != null) updated = updated.copy(albumArtUri = patch.newCoverUri)
                    songs[idx] = updated
                }
            }
        }
    }

    // Vuelve a leer qué canciones tienen letra guardada cuando algo cambió desde
    // otra pantalla (letra borrada, encontrada, o insertada a mano en el reproductor
    // a pantalla completa). No repite el escaneo online, solo relee lo ya guardado.
    LaunchedEffect(lyricsRefreshTrigger) {
        if (lyricsRefreshTrigger > 0) {
            lyricsAvailableIds.value = songs.filter { lyricsRepo.hasLyrics(it.id) }.map { it.id }.toSet()
        }
    }

    // Reacciona cuando cambia la búsqueda, el modo, los recomendados de YouTube, o se aplica un patch
    LaunchedEffect(sortType.value, songs.size, searchQuery, searchMode, youtubeRecommendedSongs, patchTrigger) {
        if (searchMode == SearchMode.LOCAL) {
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
        } else {
            // --- BÚSQUEDA EN YOUTUBE ---
            if (searchQuery.isNotBlank()) {
                delay(500) // Debounce para no saturar la API
                try {
                    errorMessage = null
                    val response = com.music.musicflame.api.RetrofitClient.instance.searchVideos(query = searchQuery)

                    if (isActive) {
                        displaySongs.clear()

                        val youtubeSongs = response.items.mapNotNull { item ->
                            val realVideoId = item.id?.videoId ?: return@mapNotNull null
                            Song(
                                id = realVideoId.hashCode().toLong(),
                                title = item.snippet?.title ?: "Sin título",
                                artist = item.snippet?.channelTitle ?: "Desconocido",
                                albumArtUri = item.snippet?.thumbnails?.high?.url ?: "",
                                path = "",
                                dateAdded = 0L,
                                duration = 0L,
                                youtubeVideoId = realVideoId
                            )
                        }
                        displaySongs.addAll(youtubeSongs)
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e

                    errorMessage = "Error tipo: " + e.javaClass.simpleName
                    android.util.Log.e("YOUTUBE_DEBUG", "Error: ", e)
                }
            } else {
                // Si la barra de búsqueda está vacía, mostramos los videos base (likes o tendencias)
                displaySongs.clear()
                displaySongs.addAll(youtubeRecommendedSongs)
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(), containerColor = Color.Transparent,
        // Este Scaffold vive DENTRO del Scaffold principal (que ya reserva status bar
        // y nav bar). Sin esto, por defecto reserva la status bar OTRA VEZ y deja un
        // hueco vacío arriba de las cards.
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {

            if (searchMode == SearchMode.YOUTUBE) {
                Box(modifier = Modifier.fillMaxSize()) {

                    // La "TV" de fondo solo aparece si NO hay videos que mostrar (o hay un error visual)
                    if (displaySongs.isEmpty() || errorMessage != null) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Filled.OndemandVideo,
                                    contentDescription = "YouTube Mode",
                                    modifier = Modifier.size(64.dp),
                                    tint = if (hasBackgroundImage) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(16.dp))

                                if (errorMessage != null) {
                                    Text(
                                        text = errorMessage!!,
                                        color = Color.Red,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(horizontal = 16.dp)
                                    )
                                } else if (searchQuery.isBlank()) {
                                    Text(
                                        // El texto cambia dependiendo de si el usuario vinculó su cuenta o no
                                        text = if (isYoutubeLoggedIn) "Tus videos favoritos y búsqueda" else "Tendencias y búsqueda en YouTube",
                                        color = normalTextColor,
                                        fontWeight = FontWeight.Medium
                                    )
                                } else {
                                    Text(
                                        text = "Buscando: \"$searchQuery\"...",
                                        color = normalTextColor,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    CircularProgressIndicator(
                                        color = if (hasBackgroundImage) Color.White else MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }

                    // Lista de resultados de YouTube
                    if (displaySongs.isNotEmpty()) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            item { Spacer(modifier = Modifier.height(8.dp)) }
                            items(displaySongs, key = { it.id }) { song ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(cardRadius))
                                        .combinedClickable(
                                            onClick = { onSongClick(song, displaySongs) },
                                            onLongClick = {}
                                        ),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (hasBackgroundImage) MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.70f) else MaterialTheme.colorScheme.surfaceContainerHigh
                                    ),
                                    shape = RoundedCornerShape(cardRadius)
                                ) {
                                    Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        AlbumArt(song.albumArtUri, 50.dp, albumRadius, albumArtShape)
                                        Column(modifier = Modifier.padding(start = 16.dp).weight(1f)) {
                                            Text(
                                                text = song.title,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 16.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                color = normalTextColor
                                            )
                                            Text(
                                                text = song.artist,
                                                fontSize = 13.sp,
                                                color = normalTextColor.copy(alpha = 0.7f),
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
            } else {
                // INTERFAZ LOCAL ORIGINAL
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
                    modifier = Modifier.fillMaxSize(),
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
                    if (displaySongs.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Filled.MusicNote,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = normalTextColor.copy(alpha = 0.6f)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "¡Aún no tienes canciones, llena la app de sinfonía!",
                                    color = normalTextColor,
                                    fontWeight = FontWeight.Medium,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 32.dp)
                                )
                            }
                        }
                    }
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
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
                                        AlbumArt(song.albumArtUri, 50.dp, albumRadius, albumArtShape)
                                    }

                                    // --- COLUMNA ACTUALIZADA CON ÍCONO DE DRIVE ---
                                    Column(modifier = Modifier.padding(start = 16.dp).weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = song.title,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 16.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else normalTextColor,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )

                                            // Muestra la nube si el nombre de la canción está en Drive
                                            if (syncedFileNames.contains(song.title) || syncedFileNames.contains("${song.title}.mp3")) {
                                                Spacer(Modifier.width(8.dp))
                                                Icon(
                                                    imageVector = Icons.Filled.CloudDone,
                                                    contentDescription = "Sincronizado con Drive",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = song.artist,
                                                fontSize = 13.sp,
                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else normalTextColor.copy(alpha = 0.7f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            // Letra disponible (encontrada por el escaneo automático o insertada a mano).
                                            if (lyricsAvailableIds.value.contains(song.id)) {
                                                Spacer(Modifier.width(4.dp))
                                                Icon(
                                                    imageVector = Icons.Filled.Subject,
                                                    contentDescription = "Letra disponible",
                                                    tint = (if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else normalTextColor).copy(alpha = 0.5f),
                                                    modifier = Modifier.size(13.dp)
                                                )
                                            }
                                        }
                                    }

                                    // --- NUEVO BOTÓN DE FAVORITOS ---
                                    IconButton(onClick = { onToggleFavorite(song) }) {
                                        Icon(
                                            imageVector = if (favoriteIds.contains(song.id)) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                            contentDescription = "Favorito",
                                            tint = if (favoriteIds.contains(song.id)) Color(0xFFE91E63) else normalTextColor.copy(alpha = 0.5f)
                                        )
                                    }
                                }
                            }
                        }
                        item { Spacer(modifier = Modifier.height(80.dp)) }
                    }
                    ListScrollbar(
                        listState = listState,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .padding(vertical = 8.dp, horizontal = 2.dp)
                    )
                }
            }

            AnimatedVisibility(
                visible = !isSelectionMode && searchMode == SearchMode.LOCAL && bottomButtonsVisible,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MFIconButton(
                        icon = Icons.Filled.Checklist,
                        contentDescription = "Seleccionar canciones",
                        hasBackgroundImage = hasBackgroundImage,
                        onClick = onToggleSelectionModeButton
                    )

                    Box {
                        MFIconButton(
                            icon = Icons.Filled.Sort,
                            contentDescription = "Ordenar",
                            hasBackgroundImage = hasBackgroundImage,
                            onClick = { showSortMenu.value = true }
                        )
                        DropdownMenu(expanded = showSortMenu.value, onDismissRequest = { showSortMenu.value = false }) {
                            listOf("Fecha creada" to SortType.DATE_CREATED, "A - Z" to SortType.A_Z, "Z - A" to SortType.Z_A).forEach { (label, type) -> DropdownMenuItem(text = { Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = sortType.value == type, onClick = null); Spacer(Modifier.width(8.dp)); Text(label) } }, onClick = { sortType.value = type; showSortMenu.value = false }) }
                        }
                    }
                }
            }
        }
    }
}