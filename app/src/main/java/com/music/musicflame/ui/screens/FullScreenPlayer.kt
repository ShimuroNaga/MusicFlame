package com.music.musicflame.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import com.music.musicflame.LocalUseRoundCorners
import com.music.musicflame.data.MusicPlayerManager
import com.music.musicflame.data.Song
import com.music.musicflame.ui.theme.LocalAppTextColor // <-- IMPORT AÑADIDO
import com.music.musicflame.ui.utils.safeScreenPadding
import kotlinx.coroutines.delay

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FullScreenPlayer(
    song: Song,
    songList: List<Song>,
    playerManager: MusicPlayerManager,
    isPlaying: Boolean,
    isFavorite: Boolean,
    onCollapse: () -> Unit,
    onPlayPause: () -> Unit, // <-- NUEVO: Evento para sincronizar el botón Play/Pause
    onToggleFavorite: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onSendToGemini: () -> Unit,
    hasBackgroundImage: Boolean = false
) {
    val context = LocalContext.current

    val isRounded = LocalUseRoundCorners.current
    val artRadius = if (isRounded) 24.dp else 0.dp

    val currentCycleState = playerManager.cycleMode.value

    val bgColor = if (hasBackgroundImage) Color.Black.copy(alpha = 0.65f) else MaterialTheme.colorScheme.background

    // <-- AQUÍ SUCEDE LA MAGIA: Al cambiar esto a LocalAppTextColor, se propaga automáticamente a todos los textos e íconos de esta pantalla
    val adaptiveContentColor = if (hasBackgroundImage) Color.White else LocalAppTextColor.current

    val initialIndex = songList.indexOf(song).coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = initialIndex, pageCount = { songList.size })

    LaunchedEffect(song) {
        val targetIndex = songList.indexOf(song)
        if (targetIndex in songList.indices && pagerState.currentPage != targetIndex) {
            pagerState.animateScrollToPage(targetIndex, animationSpec = tween(400))
        }
    }

    LaunchedEffect(pagerState.isScrollInProgress) {
        if (!pagerState.isScrollInProgress) {
            val targetSong = songList.getOrNull(pagerState.currentPage)
            if (targetSong != null && targetSong.id != song.id) {
                playerManager.playSong(targetSong, songList)
            }
        }
    }

    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var isDragging by remember { mutableStateOf(false) }

    LaunchedEffect(isPlaying, isDragging, song) {
        if (!isDragging) {
            currentPositionMs = playerManager.currentPosition
        }
        while (isPlaying && !isDragging) {
            delay(500)
            currentPositionMs = playerManager.currentPosition
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            // --- ESCUDO INVISIBLE: Esto intercepta TODOS los toques y evita que pasen a la UI de atrás ---
            .pointerInput(Unit) { detectTapGestures { } }
            // Respeta el notch/status bar arriba y la barra de navegación (gestos o
            // 3 botones) abajo, en cualquier celular. Va primero para que sea lo único
            // que separa el contenido de los bordes reales del sistema.
            .safeScreenPadding()
            // Aire visual extra, chico, para no duplicar el espacio que ya reservó
            // safeScreenPadding() (antes esto sumaba 24dp encima del inset y dejaba
            // un hueco negro enorme abajo).
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            IconButton(
                onClick = onCollapse,
                modifier = Modifier.align(Alignment.CenterStart).offset(x = (-12).dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = "Ocultar reproductor",
                    modifier = Modifier.size(36.dp),
                    tint = adaptiveContentColor
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.align(Alignment.Center)
            ) {
                Text(
                    text = "REPRODUCIENDO DESDE",
                    fontSize = 10.sp,
                    letterSpacing = 1.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = adaptiveContentColor.copy(alpha = 0.5f)
                )
                Text(
                    text = "MusicFlame",
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    color = adaptiveContentColor
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) { page ->
            val pageSong = songList.getOrNull(page)
            if (pageSong != null) {

                val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
                val alphaAnimation by animateFloatAsState(
                    targetValue = (1f - kotlin.math.abs(pageOffset)).coerceIn(0f, 1f),
                    animationSpec = tween(200), label = "AlphaAnim"
                )
                val scaleAnimation by animateFloatAsState(
                    targetValue = (1f - (kotlin.math.abs(pageOffset) * 0.15f)).coerceIn(0.85f, 1f),
                    animationSpec = tween(200), label = "ScaleAnim"
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            alpha = alphaAnimation
                            scaleX = scaleAnimation
                            scaleY = scaleAnimation
                        },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .aspectRatio(1f)
                            .shadow(
                                elevation = if (hasBackgroundImage) 0.dp else 16.dp,
                                shape = RoundedCornerShape(artRadius),
                                ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                            )
                            .clip(RoundedCornerShape(artRadius))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        if (pageSong.albumArtUri != null) {
                            SubcomposeAsyncImage(
                                model = ImageRequest.Builder(context).data(pageSong.albumArtUri).crossfade(true).build(),
                                contentDescription = "Carátula",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                val painterState = painter.state
                                if (painterState is coil.compose.AsyncImagePainter.State.Success) {
                                    // Carátula encontrada: mostramos la imagen real
                                    SubcomposeAsyncImageContent()
                                } else if (painterState is coil.compose.AsyncImagePainter.State.Error) {
                                    // No hay carátula real (o la URI falló): mostramos el ícono
                                    Icon(Icons.Filled.MusicNote, null, modifier = Modifier.size(80.dp), tint = adaptiveContentColor.copy(alpha = 0.3f))
                                }
                                // Mientras carga no mostramos nada: se ve el fondo gris de la Box
                            }
                        } else {
                            Icon(Icons.Filled.MusicNote, null, modifier = Modifier.size(80.dp), tint = adaptiveContentColor.copy(alpha = 0.3f))
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    Text(
                        text = pageSong.title,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = adaptiveContentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = pageSong.artist,
                        fontSize = 18.sp,
                        color = adaptiveContentColor.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        val totalDuration = if (playerManager.duration > 0) playerManager.duration else song.duration
        val progress = (currentPositionMs.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f)

        Column(modifier = Modifier.fillMaxWidth()) {
            Slider(
                value = progress,
                onValueChange = { newValue ->
                    isDragging = true
                    currentPositionMs = (newValue * totalDuration).toLong()
                },
                onValueChangeFinished = {
                    isDragging = false
                    playerManager.seekTo(currentPositionMs)
                },
                colors = SliderDefaults.colors(
                    thumbColor = adaptiveContentColor,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = adaptiveContentColor.copy(alpha = 0.2f)
                ),
                modifier = Modifier.fillMaxWidth().height(24.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatDuration(currentPositionMs),
                    fontSize = 12.sp,
                    color = adaptiveContentColor.copy(alpha = 0.6f),
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = formatDuration(totalDuration),
                    fontSize = 12.sp,
                    color = adaptiveContentColor.copy(alpha = 0.6f),
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { playerManager.toggleCycleMode() }) {
                Icon(
                    painter = painterResource(id = playerManager.cycleIconRes), // ¡Aquí se actualiza solo!
                    contentDescription = "Modo Reproducción",
                    // Pinta de color primario solo si NO es el modo 0 (Normal)
                    tint = if (playerManager.cycleMode.value != 0)
                        MaterialTheme.colorScheme.primary
                    else
                        adaptiveContentColor
                )
            }

            IconButton(onClick = onAddToPlaylist) {
                Icon(Icons.Filled.PlaylistAdd, contentDescription = "Añadir a Playlist", tint = adaptiveContentColor)
            }

            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = "Favorito",
                    tint = if (isFavorite) MaterialTheme.colorScheme.primary else adaptiveContentColor,
                    modifier = Modifier.size(28.dp)
                )
            }

            IconButton(onClick = onSendToGemini) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = "Preguntar a Gemini", tint = adaptiveContentColor)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onSkipPrevious) {
                Icon(Icons.Filled.SkipPrevious, "Anterior", modifier = Modifier.size(44.dp), tint = adaptiveContentColor)
            }

            Surface(
                onClick = onPlayPause, // <-- CORREGIDO: Ahora dispara la acción hacia MainActivity
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(76.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            IconButton(onClick = onSkipNext) {
                Icon(Icons.Filled.SkipNext, "Siguiente", modifier = Modifier.size(44.dp), tint = adaptiveContentColor)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}