package com.music.musicflame.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.music.musicflame.LocalUseRoundCorners
import com.music.musicflame.data.MusicPlayerManager
import com.music.musicflame.data.Song
import com.music.musicflame.ui.theme.LocalAppTextColor // <-- IMPORT AÑADIDO
import com.music.musicflame.ui.utils.TransparentCardDefaults
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

fun formatDuration(ms: Long): String {
    if (ms < 0) return "0:00"
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
    return "%d:%02d".format(minutes, seconds)
}

@Composable
fun MiniPlayer(
    currentSong: Song?,
    isPlaying: Boolean,
    playerManager: MusicPlayerManager,
    hasBackgroundImage: Boolean,
    onExpand: () -> Unit,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit
) {
    val progress = remember { mutableFloatStateOf(0f) }
    val currentMs = remember { mutableLongStateOf(0L) }
    var isDragging by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current

    val isRounded = LocalUseRoundCorners.current
    val cornerRadius = if (isRounded) 24.dp else 0.dp

    // Obtener duración total de forma segura
    val totalDuration = if (currentSong != null) {
        if (playerManager.duration > 0) playerManager.duration else currentSong.duration
    } else 0L

    // 1. Sincronización al regresar a la app (ON_RESUME)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && !isDragging) {
                val duration = playerManager.duration
                val position = playerManager.currentPosition
                if (duration > 0) {
                    progress.floatValue = position.toFloat() / duration.toFloat()
                    currentMs.longValue = position
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 2. Bucle de progreso optimizado
    LaunchedEffect(isPlaying, currentSong, isDragging) {
        if (currentSong != null && !isDragging) {
            while (isPlaying) {
                val duration = playerManager.duration
                val position = playerManager.currentPosition

                if (duration > 0) {
                    progress.floatValue = (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                    currentMs.longValue = position
                }
                delay(500) // Actualiza la UI cada medio segundo
            }

            // Si se pausa, hacemos una última actualización para asegurar que se quede en el punto exacto
            if (!isPlaying) {
                val duration = playerManager.duration
                val position = playerManager.currentPosition
                if (duration > 0) {
                    progress.floatValue = (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                    currentMs.longValue = position
                }
            }
        } else if (currentSong == null) {
            // Resetea todo si no hay canción
            progress.floatValue = 0f
            currentMs.longValue = 0L
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp) // Margen horizontal conservado para estética limpia
            .clickable(enabled = currentSong != null) { onExpand() },
        shape = RoundedCornerShape(cornerRadius),
        colors = TransparentCardDefaults.surfaceContainer(hasBackgroundImage),
        elevation = CardDefaults.cardElevation(defaultElevation = if (hasBackgroundImage) 0.dp else 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            // --- SLIDER COMPACTO ---
            Slider(
                value = if (currentSong != null) progress.floatValue else 0f,
                onValueChange = { newValue ->
                    if (currentSong != null) {
                        isDragging = true
                        progress.floatValue = newValue
                        currentMs.longValue = (newValue * totalDuration).toLong()
                    }
                },
                onValueChangeFinished = {
                    if (currentSong != null) {
                        isDragging = false
                        playerManager.seekTo(currentMs.longValue)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                )
            )

            // --- TIEMPOS ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (currentSong != null) formatDuration(currentMs.longValue) else "0:00",
                    fontSize = 10.sp,
                    // <-- APLICANDO COLOR GLOBAL SECUNDARIO
                    color = LocalAppTextColor.current.copy(alpha = 0.7f)
                )
                Text(
                    text = if (currentSong != null) formatDuration(totalDuration) else "0:00",
                    fontSize = 10.sp,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.End,
                    // <-- APLICANDO COLOR GLOBAL SECUNDARIO
                    color = LocalAppTextColor.current.copy(alpha = 0.7f)
                )
            }

            // --- INFO Y CONTROLES CON ANIMACIÓN ---
            AnimatedContent(
                targetState = currentSong?.title ?: "Sin reproducción",
                transitionSpec = {
                    slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(400)) + fadeIn() togetherWith
                            slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(400)) + fadeOut()
                },
                label = "song_title_animation"
            ) { title ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Info de la canción
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            text = title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            // <-- APLICANDO COLOR GLOBAL PRINCIPAL
                            color = LocalAppTextColor.current
                        )
                        Text(
                            text = currentSong?.artist ?: "Selecciona una canción",
                            fontSize = 12.sp,
                            // <-- APLICANDO COLOR GLOBAL SECUNDARIO
                            color = LocalAppTextColor.current.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Controles de audio (Los colores de los íconos se mantienen intactos)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Botón Cíclico
                        IconButton(
                            onClick = { playerManager.toggleCycleMode() },
                            enabled = currentSong != null,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = playerManager.cycleIconRes),
                                contentDescription = "Modo Reproducción",
                                tint = if (playerManager.cycleMode.value != 0)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Botón Atrasar
                        IconButton(
                            onClick = onSkipPrevious,
                            enabled = currentSong != null,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.SkipPrevious,
                                contentDescription = "Anterior",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // Botón Play/Pause
                        IconButton(
                            onClick = onPlayPause,
                            enabled = currentSong != null,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        // Botón Adelantar
                        IconButton(
                            onClick = onSkipNext,
                            enabled = currentSong != null,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.SkipNext,
                                contentDescription = "Siguiente",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}