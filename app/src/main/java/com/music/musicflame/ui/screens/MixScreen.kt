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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.musicflame.LocalUseRoundCorners
import com.music.musicflame.data.*
// --- TARJETA UNIVERSAL DE CANCIONES ---
import com.music.musicflame.ui.components.SongItemCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MixScreen(
    modifier: Modifier = Modifier,
    onSongClick: (Song, List<Song>) -> Unit = { _, _ -> },
    hasBackgroundImage: Boolean = false,
    // --- PARÁMETROS PARA LA SELECCIÓN GLOBAL ---
    selectedSongs: List<Song> = emptyList(),
    onToggleSelection: (Song) -> Unit = {}
) {
    val context = LocalContext.current
    val settingsRepo = remember { SettingsRepository(context) }
    val playlistRepo = remember { PlaylistRepository(context) }
    val scope = rememberCoroutineScope()

    val mixSongs = remember { mutableStateListOf<Song>() }
    val isGenerating = remember { mutableStateOf(false) }
    var canGenerate by remember { mutableStateOf(true) }
    val showSaveDialog = remember { mutableStateOf(false) }

    val todayFormatted = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date()) }
    val isRounded = LocalUseRoundCorners.current
    val headerRadius = if (isRounded) 20.dp else 0.dp
    val buttonRadius = if (isRounded) 12.dp else 0.dp
    val itemRadius = if (isRounded) 12.dp else 0.dp

    val infiniteTransition = rememberInfiniteTransition(label = "rotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(2000, easing = LinearEasing), repeatMode = RepeatMode.Restart),
        label = "rotation"
    )

    // Modo Selección Global activo si hay elementos seleccionados
    val isSelectionMode = selectedSongs.isNotEmpty()

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

    Scaffold(modifier = modifier.fillMaxSize(), containerColor = Color.Transparent) { padding ->
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
                            Box(modifier = Modifier.size(80.dp).rotate(if (isGenerating.value) rotation else 0f).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.MusicNote, null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onPrimary)
                            }
                            Spacer(Modifier.height(16.dp))

                            val textColor = if (hasBackgroundImage) Color.White else MaterialTheme.colorScheme.onPrimaryContainer

                            Text("Tu Mix Diario", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = textColor)
                            Text("Hoy ($todayFormatted)", fontSize = 14.sp, color = textColor.copy(alpha = 0.8f))
                            if (mixSongs.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                Text("${mixSongs.size} canciones", fontSize = 13.sp, color = textColor.copy(alpha = 0.8f))
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
                        isSelected = selectedSongs.contains(song),
                        isSelectionMode = isSelectionMode,
                        onToggleSelection = { onToggleSelection(song) }
                    )
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
                                Toast.makeText(context, "Vuelve mañana para generar un nuevo mix", Toast.LENGTH_SHORT).show()
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = if (canGenerate) Icons.Filled.Shuffle else Icons.Filled.Lock,
                                contentDescription = null
                            )
                        },
                        text = { Text(if (canGenerate) "Generar Mix" else "Mix Listo") },
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