package com.music.musicflame.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.core.content.ContextCompat
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import com.music.musicflame.LocalUseRoundCorners
import com.music.musicflame.data.MusicPlayerManager
import com.music.musicflame.data.Song
import com.music.musicflame.ui.components.AudioVisualizerBars
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
    hasBackgroundImage: Boolean = false
) {
    val context = LocalContext.current

    val isRounded = LocalUseRoundCorners.current
    val artRadius = if (isRounded) 24.dp else 0.dp

    val currentCycleState = playerManager.cycleMode.value

    val bgColor = if (hasBackgroundImage) Color.Black.copy(alpha = 0.65f) else MaterialTheme.colorScheme.background

    // Ahora SIEMPRE respeta el color que el usuario eligió en Ajustes, haya o no
    // imagen/gif de fondo. Antes se forzaba a blanco encima de fondos, ignorando su elección.
    val adaptiveContentColor = LocalAppTextColor.current

    // --- PERMISO DEL VISUALIZADOR (RECORD_AUDIO) ---
    // Se pide AQUÍ, contextual, solo la primera vez que el usuario abre esta pantalla,
    // y con un diálogo propio explicando el porqué ANTES del diálogo del sistema. Así no
    // se ve como una app rara pidiendo micrófono al abrir sin explicación.
    //
    // hasRecordAudioPermission es un State (no un simple Boolean calculado una vez): así,
    // en cuanto el usuario acepta el permiso, el visualizador reacciona AL TOQUE, sin
    // necesitar salir de esta pantalla y volver a entrar.
    var hasRecordAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var showVisualizerPermissionDialog by remember { mutableStateOf(false) }
    val recordAudioLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasRecordAudioPermission = isGranted
    }

    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val alreadyAsked = prefs.getBoolean("visualizer_permission_asked", false)

        if (!hasRecordAudioPermission && !alreadyAsked) {
            showVisualizerPermissionDialog = true
        }
    }

    if (showVisualizerPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showVisualizerPermissionDialog = false },
            title = { Text("Visualizador de audio") },
            text = {
                Text(
                    "Para animar las barras con el ritmo de tu música, Android exige el " +
                            "permiso de \"grabar audio\", aunque MusicFlame no graba ni guarda nada. " +
                            "Solo se usa para leer el sonido que ya está sonando y dibujar el " +
                            "ecualizador en tiempo real."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showVisualizerPermissionDialog = false
                    context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                        .edit().putBoolean("visualizer_permission_asked", true).apply()
                    recordAudioLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }) { Text("Permitir") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showVisualizerPermissionDialog = false
                    context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                        .edit().putBoolean("visualizer_permission_asked", true).apply()
                }) { Text("Ahora no") }
            }
        )
    }

    // DEFENSA: si songList todavía no llegó (o no contiene la canción actual, por ejemplo
    // justo al reabrir la app mientras se restaura la librería en segundo plano), usamos una
    // lista mínima con solo la canción actual. Así el pager SIEMPRE tiene al menos 1 página
    // válida: nunca queda con pageCount=0 (carátula invisible) ni desincronizado al saltar
    // de canción (lo que provocaba el cierre de la app).
    val effectiveSongList = if (songList.contains(song)) songList else listOf(song)

    val initialIndex = effectiveSongList.indexOf(song).coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = initialIndex, pageCount = { effectiveSongList.size })

    LaunchedEffect(song, effectiveSongList) {
        val targetIndex = effectiveSongList.indexOf(song)
        if (targetIndex in effectiveSongList.indices && pagerState.currentPage != targetIndex) {
            pagerState.animateScrollToPage(targetIndex, animationSpec = tween(400))
        }
    }

    LaunchedEffect(pagerState.isScrollInProgress) {
        if (!pagerState.isScrollInProgress) {
            val targetSong = effectiveSongList.getOrNull(pagerState.currentPage)
            if (targetSong != null && targetSong.id != song.id) {
                playerManager.playSong(targetSong, effectiveSongList)
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

    var showLyrics by remember { mutableStateOf(false) }
    val settingsRepo = remember { com.music.musicflame.data.SettingsRepository(context) }
    val lyricsRepoRef = remember { com.music.musicflame.data.LyricsRepository(context) }
    val lyricsSpeed = remember { settingsRepo.getLyricsSpeed() }
    val lyricsAnimType = remember { settingsRepo.getLyricsAnimationType() }
    val lyricsColorMode = remember { settingsRepo.getLyricsTextColorMode() }
    val lyricsCustomHex = remember { settingsRepo.getLyricsCustomColorHex() }
    val lyricsTextColor = com.music.musicflame.ui.components.resolveLyricsTextColor(lyricsColorMode, lyricsCustomHex)
    val lyricsState = com.music.musicflame.ui.components.rememberLyricsState(song, lyricsRepoRef)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            // --- ESCUDO INVISIBLE: Esto intercepta TODOS los toques y evita que pasen a la UI de atrás ---
            .pointerInput(Unit) { detectTapGestures { } }
            // Respeta el notch/status bar arriba y la barra de navegación (gestos o
            // 3 botones) abajo, en cualquier celular. Va primero para que sea lo único
            // que separa el contenido de los bordes reales del sistema.
            .safeScreenPadding()
            // --- LYRICS: deslizar la PANTALLA (no la carátula) hacia la derecha muestra
            // la letra sincronizada de la canción actual; deslizar a la izquierda vuelve.
            // Al vivir en el contenedor exterior (padre del HorizontalPager de carátulas y
            // del Slider), cualquier drag que empiece sobre esos controles lo consumen ellos
            // primero y este gesto no se activa ahí; solo reacciona en el resto de la pantalla.
            .pointerInput(Unit) {
                var totalDrag = 0f
                detectHorizontalDragGestures(
                    onDragStart = { totalDrag = 0f },
                    onHorizontalDrag = { _, dragAmount -> totalDrag += dragAmount },
                    onDragEnd = {
                        if (!showLyrics && totalDrag > 120f) showLyrics = true
                        else if (showLyrics && totalDrag < -120f) showLyrics = false
                        totalDrag = 0f
                    },
                    onDragCancel = { totalDrag = 0f }
                )
            }
    ) {
        androidx.compose.animation.AnimatedContent(
            targetState = showLyrics,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            transitionSpec = {
                if (targetState) {
                    (androidx.compose.animation.slideInHorizontally(tween(280)) { it } + androidx.compose.animation.fadeIn(tween(280))) togetherWith
                        (androidx.compose.animation.slideOutHorizontally(tween(280)) { -it } + androidx.compose.animation.fadeOut(tween(280)))
                } else {
                    (androidx.compose.animation.slideInHorizontally(tween(280)) { -it } + androidx.compose.animation.fadeIn(tween(280))) togetherWith
                        (androidx.compose.animation.slideOutHorizontally(tween(280)) { it } + androidx.compose.animation.fadeOut(tween(280)))
                }
            },
            label = "fullscreen_lyrics_toggle"
        ) { lyricsVisible ->
            if (lyricsVisible) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { showLyrics = false }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Volver",
                                tint = adaptiveContentColor
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Column {
                            Text(
                                text = song.title,
                                fontWeight = FontWeight.Black,
                                fontSize = 16.sp,
                                color = adaptiveContentColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = song.artist,
                                fontSize = 12.sp,
                                color = adaptiveContentColor.copy(alpha = 0.7f),
                                maxLines = 1
                            )
                        }
                    }
                    com.music.musicflame.ui.components.LyricsView(
                        song = song,
                        positionMs = currentPositionMs,
                        lyrics = lyricsState.lyrics,
                        speed = lyricsSpeed,
                        animationType = lyricsAnimType,
                        textColor = lyricsTextColor,
                        isLoading = lyricsState.isLoading,
                        searchFailed = lyricsState.searchFailed,
                        onSearchOnline = lyricsState.onSearchOnline,
                        onInsertManual = lyricsState.onInsertManual,
                        onSearchYoutube = {
                            val query = android.net.Uri.encode("${song.artist} ${song.title}")
                            val intent = android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse("https://www.youtube.com/results?search_query=$query")
                            )
                            context.startActivity(intent)
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
    Column(
        modifier = Modifier.fillMaxSize(),
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
            val pageSong = effectiveSongList.getOrNull(page)
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
        // FIX: si ni el MediaController ni el Song tienen una duración válida todavía
        // (típicamente justo tras reconectar/saltar de canción, antes de que ExoPlayer
        // termine de preparar el nuevo MediaItem), totalDuration puede ser 0. Dividir
        // entre 0 en floats da NaN, y coerceIn NO limpia el NaN (cualquier comparación
        // con NaN da false, así que pasa de largo). Ese NaN llegaba al Slider, que
        // truena al intentar redondearlo para accesibilidad ("Cannot round NaN value").
        val progress = if (totalDuration > 0) {
            (currentPositionMs.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }

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
                // Material You: los 3 colores salen de MaterialTheme.colorScheme.primary, que en
                // Android 12+ ya se genera dinámicamente desde el fondo de pantalla del sistema
                // (ver Theme.kt: dynamicLightColorScheme / dynamicDarkColorScheme). Antes el thumb
                // y el track inactivo usaban el color de texto elegido en Ajustes, fijo y sin
                // relación con la paleta dinámica.
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
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
                onClick = onPlayPause,
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

        Spacer(modifier = Modifier.height(20.dp))

        AudioVisualizerBars(
            audioSessionId = playerManager.audioSessionId.value,
            isPlaying = isPlaying,
            hasRecordAudioPermission = hasRecordAudioPermission,
            color = adaptiveContentColor,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 8.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
            }
        }
    }
}