package com.music.musicflame.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.musicflame.AlbumArtShapeType
import com.music.musicflame.LocalUseRoundCorners
import com.music.musicflame.data.Song
import com.music.musicflame.ui.theme.LocalAppTextColor
import com.music.musicflame.ui.utils.TransparentCardDefaults
import kotlinx.coroutines.launch

/**
 * Widget compacto 1x3 de MusicFlame.
 *
 * Distribución:
 *  [ Carátula ]   [ Nombre de la canción ]   [ Play/Pause ]
 *
 * La carátula + el nombre forman una sola zona swipeable:
 *  - swipe hacia la IZQUIERDA -> siguiente canción
 *  - swipe hacia la DERECHA   -> canción anterior
 *
 * Nota: este componente es para uso DENTRO de la app (Compose).
 * Un widget de pantalla de inicio de Android (RemoteViews) no soporta
 * gestos de swipe, solo taps — ver aclaración en el chat.
 */
@Composable
fun CompactSongWidget(
    currentSong: Song?,
    isPlaying: Boolean,
    hasBackgroundImage: Boolean,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit
) {
    val isRounded = LocalUseRoundCorners.current
    val cornerRadius = if (isRounded) 20.dp else 0.dp
    val scope = rememberCoroutineScope()

    // Desplazamiento visual de la carátula+nombre para dar feedback del swipe
    val offsetX = remember { Animatable(0f) }
    var dragAccumulator by remember { mutableFloatStateOf(0f) }
    val skipThresholdPx = with(LocalDensity.current) { 56.dp.toPx() }
    val maxOffsetPx = with(LocalDensity.current) { 24.dp.toPx() }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .height(64.dp),
        shape = RoundedCornerShape(cornerRadius),
        colors = TransparentCardDefaults.surfaceContainer(hasBackgroundImage),
        elevation = CardDefaults.cardElevation(defaultElevation = if (hasBackgroundImage) 0.dp else 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // --- ZONA SWIPEABLE: carátula + nombre ---
            Row(
                modifier = Modifier
                    .weight(1f)
                    .pointerInput(currentSong) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                when {
                                    currentSong == null -> {}
                                    dragAccumulator <= -skipThresholdPx -> onSkipNext()
                                    dragAccumulator >= skipThresholdPx -> onSkipPrevious()
                                }
                                dragAccumulator = 0f
                                scope.launch {
                                    offsetX.animateTo(0f, animationSpec = spring())
                                }
                            },
                            onDragCancel = {
                                dragAccumulator = 0f
                                scope.launch { offsetX.animateTo(0f, animationSpec = spring()) }
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                if (currentSong != null) {
                                    dragAccumulator += dragAmount
                                    scope.launch {
                                        offsetX.snapTo(
                                            (offsetX.value + dragAmount).coerceIn(-maxOffsetPx, maxOffsetPx)
                                        )
                                    }
                                    change.consume()
                                }
                            }
                        )
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                // Carátula (siempre cuadrada en este widget)
                AlbumArt(
                    albumArtUri = currentSong?.albumArtUri,
                    size = 44.dp,
                    cornerRadius = 8.dp,
                    shape = AlbumArtShapeType.SQUARE,
                    filePath = currentSong?.path,
                    isCustomCover = currentSong?.hasCustomCover ?: false
                )

                // Nombre de la canción, con transición al cambiar (feedback del swipe)
                AnimatedContent(
                    targetState = currentSong?.title ?: "Sin reproducción",
                    transitionSpec = {
                        slideInHorizontally(initialOffsetX = { it / 3 }, animationSpec = tween(300)) + fadeIn() togetherWith
                                slideOutHorizontally(targetOffsetX = { -it / 3 }, animationSpec = tween(300)) + fadeOut()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp)
                        .offset { IntOffset(offsetX.value.toInt(), 0) },
                    label = "widget_song_title"
                ) { title ->
                    Column {
                        Text(
                            text = title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = LocalAppTextColor.current
                        )
                        currentSong?.artist?.let { artist ->
                            Text(
                                text = artist,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = LocalAppTextColor.current.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }

            // --- BOTÓN PLAY/PAUSE (lado derecho, fuera de la zona de swipe) ---
            IconButton(
                onClick = onPlayPause,
                enabled = currentSong != null,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(30.dp)
                )
            }
        }
    }
}