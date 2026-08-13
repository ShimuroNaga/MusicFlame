package com.music.musicflame.ui.screens

import android.widget.Toast
// --- IMPORTACIONES DE ANIMACIÓN UNIFICADAS Y CORREGIDAS ---
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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

    LaunchedEffect(Unit) {
        val statsRepo = StatsRepository(context)
        val favoritesRepo = FavoritesRepository(context)
        // La lista de canciones del dispositivo no cambia a cada tick, así que la
        // cargamos una sola vez y solo refrescamos estadísticas/favoritos en el loop.
        val songsById = loadSongsFromDevice(context).associateBy { it.id }
        while (true) {
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
        val allSongs = loadSongsFromDevice(context)
        val savedIds = settingsRepo.getMixSongs()
        val lastGeneratedDate = try { settingsRepo.getLastMixDate() } catch (e: Exception) { "" }

        if (lastGeneratedDate == todayFormatted && savedIds.isNotEmpty()) {
            canGenerate = false
            mixSongs.clear()
            mixSongs.addAll(allSongs.filter { it.id in savedIds })
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
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
                verticalArrangement = Arrangement.spacedBy(12.dp)
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

                            Text("Tu Mix Diario", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = textColor)
                            Text("Hoy ($todayFormatted)", fontSize = 14.sp, color = textColor.copy(alpha = 0.8f))
                            if (mixSongs.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                Text("${mixSongs.size} canciones • $totalDurationFormatted", fontSize = 13.sp, color = textColor.copy(alpha = 0.8f))
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
                            Icon(Icons.Filled.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text("Reproducir Mix")
                        }
                    }
                }

                items(mixSongs, key = { it.id }) { song ->
                    // USAMOS LA NUEVA TARJETA UNIVERSAL
                    SongItemCard(
                        song = song,
                        onClick = { onSongClick(song, mixSongs) },
                        hasBackgroundImage = hasBackgroundImage,
                        radius = itemRadius,
                        albumArtShape = albumArtShape,
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
                                Text("Estadísticas", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = statsTextColor)
                                val topSongLabel = statsRows.firstOrNull()?.song?.title
                                Text(
                                    if (topSongLabel != null) "Más escuchada: $topSongLabel" else "Top 20 canciones más escuchadas",
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
                                "Aún no hay estadísticas. ¡Reproduce alguna canción para empezar a registrar!",
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
                                            contentDescription = "Favorita",
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
            AnimatedVisibility(
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
                            text = { Text("Guardar Mix") },
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
                                    val allSongs = loadSongsFromDevice(context)
                                    val newMix = allSongs.shuffled().take(30)
                                    mixSongs.clear()
                                    mixSongs.addAll(newMix)
                                    settingsRepo.saveMixSongs(newMix.map { it.id })
                                    settingsRepo.saveLastMixDate(todayFormatted)
                                    canGenerate = false
                                    isGenerating.value = false
                                    Toast.makeText(context, "¡30 canciones generadas!", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(context, "Nuevo mix disponible en $hoursUntilNextMix horas", Toast.LENGTH_SHORT).show()
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = if (canGenerate) Icons.Filled.Shuffle else Icons.Filled.Lock,
                                contentDescription = null
                            )
                        },
                        text = { Text(if (canGenerate) "Generar Mix" else "Listo (${hoursUntilNextMix}h)") },
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

    if (showSaveDialog.value) {
        val playlistName = remember { mutableStateOf("Mix Diario $todayFormatted") }
        AlertDialog(
            onDismissRequest = { showSaveDialog.value = false },
            title = { Text("Guardar como Playlist") },
            text = { OutlinedTextField(value = playlistName.value, onValueChange = { playlistName.value = it }, label = { Text("Nombre de la playlist") }) },
            confirmButton = {
                TextButton(onClick = {
                    playlistRepo.createPlaylist(playlistName.value)
                    val newPlaylist = playlistRepo.getPlaylists().last()
                    mixSongs.forEach { playlistRepo.addSongToPlaylist(newPlaylist.id, it.id) }
                    Toast.makeText(context, "Playlist guardada", Toast.LENGTH_SHORT).show()
                    showSaveDialog.value = false
                }) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { showSaveDialog.value = false }) { Text("Cancelar") } }
        )
    }
}