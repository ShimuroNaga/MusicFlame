package com.music.musicflame.ui.screens

import android.widget.Toast
// --- IMPORTACIONES DE ANIMACIÓN UNIFICADAS Y CORREGIDAS ---
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.musicflame.LocalUseRoundCorners
import com.music.musicflame.LocalAlbumArtShape
import com.music.musicflame.data.*
// --- TARJETA UNIVERSAL DE CANCIONES ---
import com.music.musicflame.ui.components.SongItemCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import com.music.musicflame.R

/** Canciones que trae el Mix Diario al generarse. */
private const val DAILY_MIX_SIZE = 40

// --- ESTADÍSTICAS: fila combinada de canción + su estadística + si es favorita ---
private data class SongStatRow(val song: Song, val stat: SongStat, val isFavorite: Boolean)

private fun formatListenedTime(ms: Long): String {
    val totalMinutes = ms / 60000L
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}h ${minutes}min" else "${minutes}min"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MixScreen(
    modifier: Modifier = Modifier,
    onSongClick: (Song, List<Song>) -> Unit = { _, _ -> },
    // Mezcla de Momentos: (canciones en orden, clip (inicio,fin) por id de canción, índice inicial).
    onPlayMoments: (List<Song>, Map<Long, Pair<Long, Long>>, Int) -> Unit = { _, _, _ -> },
    hasBackgroundImage: Boolean = false,
    // --- PARÁMETROS PARA LA SELECCIÓN GLOBAL ---
    selectedSongs: List<Song> = emptyList(),
    onToggleSelection: (Song) -> Unit = {},
    // NUEVO: id de la canción sonando ahora, para el icono al lado del título.
    currentPlayingSongId: Long? = null
) {
    val context = LocalContext.current
    val settingsRepo = remember { SettingsRepository(context) }
    val playlistRepo = remember { PlaylistRepository(context) }
    val scope = rememberCoroutineScope()

    val mixSongs = remember { mutableStateListOf<Song>() }
    val isGenerating = remember { mutableStateOf(false) }
    var canGenerate by remember { mutableStateOf(true) }
    val showSaveDialog = remember { mutableStateOf(false) }

    // --- ESTADÍSTICAS: top 20 canciones más escuchadas, en vivo ---
    val statsRows = remember { mutableStateListOf<SongStatRow>() }
    val showStats = remember { mutableStateOf(false) }
    // NUEVO: distinguimos "no hay NADA registrado todavía" de "hay canciones
    // sonando pero ninguna llegó aún a las 10 reproducciones mínimas" — antes
    // ambos casos mostraban el mismo mensaje ("Reproduce algo para empezar"),
    // lo cual era engañoso si ya llevabas horas escuchando música.
    val hasAnyStatsRecorded = remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val statsRepo = StatsRepository(context)
        val favoritesRepo = FavoritesRepository(context)
        // La lista de canciones del dispositivo no cambia a cada tick, así que la
        // cargamos una sola vez y solo refrescamos estadísticas/favoritos en el loop.
        com.music.musicflame.data.SongLibraryHolder.ensureLoaded(context)
        val songsById = com.music.musicflame.data.SongLibraryHolder.songs.associateBy { it.id }
        while (true) {
            hasAnyStatsRecorded.value = statsRepo.getAllStats().isNotEmpty()
            val top = statsRepo.getTopPlayed(20)
            val rows = top.mapNotNull { (id, stat) ->
                val song = songsById[id] ?: return@mapNotNull null
                SongStatRow(song = song, stat = stat, isFavorite = favoritesRepo.isFavorite(id))
            }
            statsRows.clear()
            statsRows.addAll(rows)
            delay(2000)
        }
    }

    val todayFormatted = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date()) }
    val isRounded = LocalUseRoundCorners.current
    val albumArtShape = LocalAlbumArtShape.current
    val headerRadius = if (isRounded) 20.dp else 0.dp
    val buttonRadius = if (isRounded) 12.dp else 0.dp
    val itemRadius = if (isRounded) 12.dp else 0.dp

    val infiniteTransition = rememberInfiniteTransition(label = "rotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(2000, easing = LinearEasing), repeatMode = RepeatMode.Restart),
        label = "rotation"
    )
    // Rotación lenta constante en reposo, para que el ícono se sienta "vivo" (igual que en GeminiScreen)
    val idleRotation by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(16000, easing = LinearEasing), repeatMode = RepeatMode.Restart),
        label = "idleRotation"
    )
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.96f, targetValue = 1.04f,
        animationSpec = infiniteRepeatable(animation = tween(1400), repeatMode = RepeatMode.Reverse),
        label = "pulse"
    )

    // Modo Selección Global activo si hay elementos seleccionados
    val isSelectionMode = selectedSongs.isNotEmpty()

    // --- Duración total del mix, formateada en horas y minutos ---
    val totalDurationFormatted by remember {
        derivedStateOf {
            val totalMs = mixSongs.sumOf { it.duration }
            val totalMinutes = totalMs / 60000
            val hours = totalMinutes / 60
            val minutes = totalMinutes % 60
            if (hours > 0) "$hours h $minutes min" else "$minutes min"
        }
    }

    // --- Horas restantes hasta la medianoche, para saber cuándo se desbloquea el próximo mix ---
    val hoursUntilNextMix = remember(canGenerate) {
        if (canGenerate) 0 else {
            val now = java.util.Calendar.getInstance()
            val midnight = java.util.Calendar.getInstance().apply {
                add(java.util.Calendar.DAY_OF_YEAR, 1)
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }
            val diffMs = midnight.timeInMillis - now.timeInMillis
            (diffMs / (1000 * 60 * 60)).toInt().coerceAtLeast(0)
        }
    }

    LaunchedEffect(Unit) {
        com.music.musicflame.data.SongLibraryHolder.ensureLoaded(context)
        val allSongs = com.music.musicflame.data.SongLibraryHolder.songs
        val savedIds = settingsRepo.getMixSongs()
        val lastGeneratedDate = try { settingsRepo.getLastMixDate() } catch (e: Exception) { "" }

        if (lastGeneratedDate == todayFormatted && savedIds.isNotEmpty()) {
            canGenerate = false
            mixSongs.clear()
            mixSongs.addAll(allSongs.filter { it.id in savedIds })
        }
    }

    // --- PAGER CIRCULAR de 2 páginas: 0 = Mix Diario, 1 = Mezcla de Momentos. ---
    // Al ser circular, el swipe funciona hacia la izquierda Y hacia la derecha desde
    // cualquier página (con 2 páginas normales, un lado quedaba muerto).
    // El estado de la mezcla vive ACÁ (no dentro de la página): en un pager circular cada
    // vuelta crea una página nueva, y si el estado viviera adentro la mezcla se reordenaría
    // cada vez que cambias de página.
    val momentEntries = remember { mutableStateListOf<MomentMixEntry>() }
    val momentsSignature = remember { mutableStateOf("") }
    var momentsShuffleSeed by remember { mutableStateOf(0) }
    val loopCount = 10_000
    val pagerState = rememberPagerState(initialPage = loopCount / 2) { loopCount }
    val onMomentsPage = pagerState.currentPage % 2 == 1

    Column(modifier = modifier.fillMaxSize()) {
        // Indicador: dos puntos con su nombre; tocar uno también cambia de página.
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf("Mix Diario", "Momentos").forEachIndexed { idx, label ->
                val selected = (pagerState.currentPage % 2) == idx
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            if (!selected) scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                        }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(if (selected) 8.dp else 6.dp)
                            .clip(CircleShape)
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary
                                else (if (hasBackgroundImage) Color.White else MaterialTheme.colorScheme.onSurface).copy(alpha = 0.35f)
                            )
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        label,
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = (if (hasBackgroundImage) Color.White else MaterialTheme.colorScheme.onSurface)
                            .copy(alpha = if (selected) 1f else 0.6f)
                    )
                }
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth().weight(1f),
            userScrollEnabled = !isSelectionMode,
            beyondViewportPageCount = 1
        ) { page ->
            if (page % 2 == 1) {
                MomentsMixPage(
                    entries = momentEntries,
                    signatureState = momentsSignature,
                    shuffleSeed = momentsShuffleSeed,
                    onShuffle = { momentsShuffleSeed++ },
                    isVisible = onMomentsPage,
                    hasBackgroundImage = hasBackgroundImage,
                    currentPlayingSongId = currentPlayingSongId,
                    onPlayMoments = onPlayMoments
                )
            } else {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = Color.Transparent,
                    // Ya vive dentro del Scaffold principal (que reserva status/nav bar); sin esto
                    // reserva la status bar otra vez y deja un hueco vacío arriba.
                    contentWindowInsets = WindowInsets(0, 0, 0, 0)
                ) { padding ->
                    Box(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(padding)
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            item { Spacer(Modifier.height(8.dp)) }

                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (hasBackgroundImage) Color.Black.copy(alpha = 0.5f) else MaterialTheme.colorScheme.primaryContainer
                                    ),
                                    shape = RoundedCornerShape(headerRadius)
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Box(
                                            modifier = Modifier
                                                .size(80.dp)
                                                .scale(if (isGenerating.value) pulse else 1f)
                                                .rotate(if (isGenerating.value) rotation else idleRotation)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Filled.MusicNote,
                                                null,
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .rotate(if (isGenerating.value) -rotation else -idleRotation),
                                                tint = MaterialTheme.colorScheme.onPrimary
                                            )
                                        }
                                        Spacer(Modifier.height(16.dp))

                                        val textColor = if (hasBackgroundImage) Color.White else MaterialTheme.colorScheme.onPrimaryContainer

                                        Text(stringResource(R.string.mix_daily_title), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = textColor)
                                        Text(stringResource(R.string.mix_today, todayFormatted), fontSize = 14.sp, color = textColor.copy(alpha = 0.8f))
                                        if (mixSongs.isNotEmpty()) {
                                            Spacer(Modifier.height(8.dp))
                                            Text(stringResource(R.string.mix_songs_duration, mixSongs.size, totalDurationFormatted), fontSize = 13.sp, color = textColor.copy(alpha = 0.8f))
                                        }
                                    }
                                }
                            }

                            if (mixSongs.isNotEmpty()) {
                                item {
                                    Button(
                                        onClick = {
                                            // Si no estamos en modo selección, reproducimos la primera
                                            if (!isSelectionMode) onSongClick(mixSongs.first(), mixSongs)
                                        },
                                        modifier = Modifier.fillMaxWidth().height(48.dp),
                                        shape = RoundedCornerShape(buttonRadius),
                                        enabled = !isSelectionMode // Deshabilitamos reproducir todo si estamos seleccionando
                                    ) {
                                        Icon(Icons.Filled.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.mix_play))
                                    }
                                }
                            }

                            items(mixSongs, key = { it.id }) { song ->
                                // USAMOS LA NUEVA TARJETA UNIVERSAL
                                SongItemCard(
                                    song = song,
                                    onClick = { onSongClick(song, mixSongs) },
                                    hasBackgroundImage = hasBackgroundImage,
                                    radius = if (isRounded) 16.dp else 0.dp,
                                    albumArtShape = albumArtShape,
                                    matchSongScreenStyle = true,
                                    isSelected = selectedSongs.contains(song),
                                    isSelectionMode = isSelectionMode,
                                    onToggleSelection = { onToggleSelection(song) },
                                    isCurrentlyPlaying = currentPlayingSongId != null && currentPlayingSongId == song.id
                                )
                            }

                            // --- ESTADÍSTICAS: top 20 canciones más escuchadas, en vivo ---
                            item { Spacer(Modifier.height(8.dp)) }

                            item {
                                val statsTextColor = if (hasBackgroundImage) Color.White else MaterialTheme.colorScheme.onSecondaryContainer
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(headerRadius))
                                        .clickable { showStats.value = !showStats.value },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (hasBackgroundImage) Color.Black.copy(alpha = 0.5f) else MaterialTheme.colorScheme.secondaryContainer
                                    ),
                                    shape = RoundedCornerShape(headerRadius)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Filled.BarChart, null, tint = statsTextColor)
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(stringResource(R.string.mix_stats), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = statsTextColor)
                                            val topSongLabel = statsRows.firstOrNull()?.song?.title
                                            Text(
                                                if (topSongLabel != null) stringResource(R.string.mix_most_played, topSongLabel) else stringResource(R.string.mix_top20),
                                                fontSize = 12.sp,
                                                color = statsTextColor.copy(alpha = 0.8f)
                                            )
                                        }
                                        Icon(
                                            if (showStats.value) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                            null,
                                            tint = statsTextColor
                                        )
                                    }
                                }
                            }

                            if (showStats.value) {
                                if (statsRows.isEmpty()) {
                                    item {
                                        Text(
                                            // NUEVO: mensaje distinto según el caso real. Antes decía
                                            // siempre "reproduce algo para empezar", incluso si ya
                                            // llevabas horas escuchando música pero ninguna canción
                                            // había llegado todavía a las 10 reproducciones mínimas
                                            // para aparecer en este Top 20 — lo que parecía un bug
                                            // (como si nada se estuviera registrando) sin serlo.
                                            if (hasAnyStatsRecorded.value)
                                                stringResource(R.string.mix_stats_empty_threshold)
                                            else
                                                stringResource(R.string.mix_stats_empty),
                                            fontSize = 13.sp,
                                            color = if (hasBackgroundImage) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
                                        )
                                    }
                                } else {
                                    itemsIndexed(statsRows, key = { _, row -> "stat_${row.song.id}" }) { index, row ->
                                        val rowTextColor = if (hasBackgroundImage) Color.White else MaterialTheme.colorScheme.onSurface
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (hasBackgroundImage) Color.Black.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceContainerHigh
                                            ),
                                            shape = RoundedCornerShape(itemRadius)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    "${index + 1}",
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = rowTextColor.copy(alpha = 0.6f),
                                                    modifier = Modifier.width(24.dp)
                                                )
                                                Column(Modifier.weight(1f).padding(end = 8.dp)) {
                                                    Text(
                                                        row.song.title,
                                                        fontSize = 14.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = rowTextColor,
                                                        maxLines = 1,
                                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        row.song.artist,
                                                        fontSize = 12.sp,
                                                        color = rowTextColor.copy(alpha = 0.7f),
                                                        maxLines = 1,
                                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                                    )
                                                }
                                                if (row.isFavorite) {
                                                    Icon(
                                                        Icons.Filled.Favorite,
                                                        contentDescription = stringResource(R.string.mix_favorite),
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(Modifier.width(10.dp))
                                                }
                                                Column(horizontalAlignment = Alignment.End) {
                                                    Text(
                                                        "${row.stat.playCount}x",
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = rowTextColor
                                                    )
                                                    Text(
                                                        formatListenedTime(row.stat.totalListenedMs),
                                                        fontSize = 11.sp,
                                                        color = rowTextColor.copy(alpha = 0.7f)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Espacio extra para que la lista no quede oculta detrás de los botones flotantes
                            item { Spacer(Modifier.height(140.dp)) }
                        }

                        // Ocultamos los botones de generar/guardar si estamos en modo de selección para evitar toques por error
                        androidx.compose.animation.AnimatedVisibility(
                            visible = !isSelectionMode,
                            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                            exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
                            modifier = Modifier.align(Alignment.BottomCenter)
                        ) {
                            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (mixSongs.isNotEmpty()) {
                                    ExtendedFloatingActionButton(
                                        onClick = { showSaveDialog.value = true },
                                        icon = { Icon(Icons.Filled.Save, null) },
                                        text = { Text(stringResource(R.string.mix_save)) },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(buttonRadius)
                                    )
                                }

                                ExtendedFloatingActionButton(
                                    onClick = {
                                        if (canGenerate) {
                                            scope.launch {
                                                isGenerating.value = true
                                                delay(1000)
                                                val allSongs = com.music.musicflame.data.SongLibraryHolder.songs
                                                val newMix = allSongs.shuffled().take(DAILY_MIX_SIZE)
                                                mixSongs.clear()
                                                mixSongs.addAll(newMix)
                                                settingsRepo.saveMixSongs(newMix.map { it.id })
                                                settingsRepo.saveLastMixDate(todayFormatted)
                                                canGenerate = false
                                                isGenerating.value = false
                                                Toast.makeText(context, context.getString(R.string.mix_generated, newMix.size), Toast.LENGTH_SHORT).show()
                                            }
                                        } else {
                                            Toast.makeText(context, context.getString(R.string.mix_next_available, hoursUntilNextMix), Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    icon = {
                                        Icon(
                                            imageVector = if (canGenerate) Icons.Filled.Shuffle else Icons.Filled.Lock,
                                            contentDescription = null
                                        )
                                    },
                                    text = { Text(if (canGenerate) stringResource(R.string.mix_generate) else stringResource(R.string.mix_ready_in, hoursUntilNextMix)) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(buttonRadius),
                                    containerColor = FloatingActionButtonDefaults.containerColor,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    elevation = FloatingActionButtonDefaults.elevation()
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSaveDialog.value) {
        val playlistName = remember { mutableStateOf("Mix Diario $todayFormatted") }
        AlertDialog(
            onDismissRequest = { showSaveDialog.value = false },
            title = { Text(stringResource(R.string.mix_save_as_playlist)) },
            text = { OutlinedTextField(value = playlistName.value, onValueChange = { playlistName.value = it }, label = { Text(stringResource(R.string.mix_playlist_name)) }) },
            confirmButton = {
                TextButton(onClick = {
                    playlistRepo.createPlaylist(playlistName.value)
                    val newPlaylist = playlistRepo.getPlaylists().last()
                    mixSongs.forEach { playlistRepo.addSongToPlaylist(newPlaylist.id, it.id) }
                    Toast.makeText(context, context.getString(R.string.mix_playlist_saved), Toast.LENGTH_SHORT).show()
                    showSaveDialog.value = false
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = { TextButton(onClick = { showSaveDialog.value = false }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }
}

/** Un renglón de la Mezcla de Momentos: la canción y el fragmento que sonará de ella. */
private data class MomentMixEntry(val song: Song, val moments: List<Moment>, val chosen: Moment)

/**
 * Página 2 del Mix: "Mezcla de Momentos". Arma una cola con UN momento (elegido al azar) de
 * cada canción que tenga momentos, en orden aleatorio, y cada canción suena solo ese fragmento.
 * - "Reproducir mezcla": suena todo desde el principio.
 * - Tocar una canción: arranca la mezcla desde esa canción.
 * - "Mezclar de nuevo": reordena y vuelve a elegir al azar qué momento suena de cada una.
 */
@Composable
private fun MomentsMixPage(
    entries: androidx.compose.runtime.snapshots.SnapshotStateList<MomentMixEntry>,
    signatureState: MutableState<String>,
    shuffleSeed: Int,
    onShuffle: () -> Unit,
    isVisible: Boolean,
    hasBackgroundImage: Boolean,
    currentPlayingSongId: Long?,
    onPlayMoments: (List<Song>, Map<Long, Pair<Long, Long>>, Int) -> Unit
) {
    val context = LocalContext.current
    val momentsRepo = remember { MomentsRepository(context) }
    val isRounded = LocalUseRoundCorners.current
    val headerRadius = if (isRounded) 20.dp else 0.dp
    val buttonRadius = if (isRounded) 12.dp else 0.dp
    val itemRadius = if (isRounded) 12.dp else 0.dp
    val textColor = if (hasBackgroundImage) Color.White else MaterialTheme.colorScheme.onPrimaryContainer

    var lastSeed by remember { mutableStateOf(shuffleSeed) }

    // Se recarga al entrar a la página SOLO si cambiaron tus momentos (marcaste o borraste
    // alguno en el reproductor) o si pides "Mezclar"; si no, conserva el orden que ya tenías.
    LaunchedEffect(isVisible, shuffleSeed) {
        if (!isVisible && entries.isNotEmpty()) return@LaunchedEffect
        com.music.musicflame.data.SongLibraryHolder.ensureLoaded(context)
        val all = momentsRepo.getAll()
        val signature = all.entries
            .sortedBy { it.key }
            .joinToString("|") { (path, list) -> path + ":" + list.joinToString(",") { it.id } }
        val forced = shuffleSeed != lastSeed
        if (!forced && entries.isNotEmpty() && signature == signatureState.value) return@LaunchedEffect
        lastSeed = shuffleSeed
        signatureState.value = signature
        val trashedIds = try { TrashRepository(context).getTrash().map { it.song.id }.toSet() } catch (e: Exception) { emptySet() }
        val built = com.music.musicflame.data.SongLibraryHolder.songs
            .filter { it.id !in trashedIds && all[it.path].orEmpty().isNotEmpty() }
            .map { song ->
                val list = all[song.path].orEmpty().sortedBy { it.startMs }
                MomentMixEntry(song, list, list.random())
            }
            .shuffled()
        entries.clear()
        entries.addAll(built)
    }

    val totalClipMs by remember { derivedStateOf { entries.sumOf { it.chosen.durationMs } } }
    val totalMomentsCount by remember { derivedStateOf { entries.sumOf { it.moments.size } } }

    fun clipsMap(): Map<Long, Pair<Long, Long>> =
        entries.associate { it.song.id to (it.chosen.startMs to it.chosen.endMs) }

    fun playFrom(index: Int) {
        if (entries.isEmpty()) return
        onPlayMoments(entries.map { it.song }, clipsMap(), index)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            item { Spacer(Modifier.height(8.dp)) }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (hasBackgroundImage) Color.Black.copy(alpha = 0.5f) else MaterialTheme.colorScheme.tertiaryContainer
                    ),
                    shape = RoundedCornerShape(headerRadius)
                ) {
                    val headerText = if (hasBackgroundImage) Color.White else MaterialTheme.colorScheme.onTertiaryContainer
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier.size(80.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.AutoAwesome, null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onTertiary)
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(stringResource(R.string.mix_moments_title), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = headerText)
                        Text(stringResource(R.string.mix_moments_subtitle), fontSize = 14.sp, color = headerText.copy(alpha = 0.8f))
                        if (entries.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                stringResource(R.string.mix_moments_summary, entries.size, totalMomentsCount, formatListenedTime(totalClipMs)),
                                fontSize = 13.sp,
                                color = headerText.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }

            if (entries.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.mix_moments_empty),
                        fontSize = 13.sp,
                        color = if (hasBackgroundImage) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp)
                    )
                }
            } else {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { playFrom(0) },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(buttonRadius)
                        ) {
                            Icon(Icons.Filled.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.mix_play_mix_lower))
                        }
                        FilledTonalButton(
                            onClick = { onShuffle() },
                            modifier = Modifier.height(48.dp),
                            shape = RoundedCornerShape(buttonRadius)
                        ) {
                            Icon(Icons.Filled.Shuffle, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.mix_shuffle))
                        }
                    }
                }

                itemsIndexed(entries, key = { _, e -> "mm_${e.song.id}" }) { index, entry ->
                    val rowText = if (hasBackgroundImage) Color.White else MaterialTheme.colorScheme.onSurface
                    val playing = currentPlayingSongId != null && currentPlayingSongId == entry.song.id
                    Card(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(itemRadius)).clickable { playFrom(index) },
                        colors = CardDefaults.cardColors(
                            containerColor = when {
                                playing -> MaterialTheme.colorScheme.tertiaryContainer
                                hasBackgroundImage -> Color.Black.copy(alpha = 0.35f)
                                else -> MaterialTheme.colorScheme.surfaceContainerHigh
                            }
                        ),
                        shape = RoundedCornerShape(itemRadius)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            com.music.musicflame.ui.components.AlbumArt(
                                entry.song.albumArtUri, 50.dp, if (isRounded) 12.dp else 0.dp,
                                LocalAlbumArtShape.current,
                                filePath = entry.song.path, isCustomCover = entry.song.hasCustomCover
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    entry.song.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = rowText,
                                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                                Text(
                                    entry.song.artist, fontSize = 12.sp, color = rowText.copy(alpha = 0.7f),
                                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    "${formatClock(entry.chosen.startMs)}–${formatClock(entry.chosen.endMs)}",
                                    fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    if (entry.moments.size > 1) stringResource(R.string.mix_moments_many, entry.moments.size) else stringResource(R.string.mix_moments_one),
                                    fontSize = 11.sp, color = rowText.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(100.dp)) }
        }
    }
}

private fun formatClock(ms: Long): String {
    val totalSeconds = ms / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}