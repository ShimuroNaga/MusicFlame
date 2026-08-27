package com.music.musicflame.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
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
import com.music.musicflame.ui.components.YoutubeVerifyWebView
import com.music.musicflame.ui.theme.LocalAppTextColor // <-- IMPORT AÑADIDO
import com.music.musicflame.ui.utils.safeScreenPadding
import com.music.musicflame.ui.utils.rememberSafeBottomPadding
import kotlinx.coroutines.delay

// Tamaño del aro del estilo "Círculo pulsante" (EqualizerStyle.PULSE_CIRCLE) cuando
// rodea al botón de Play/Pause (vista normal) o flota solo (vista de Letra). 118dp
// deja ~21dp de aire alrededor del botón real de 76dp para que el aro se note sin
// invadir los botones de Anterior/Siguiente de al lado.
private val PULSE_CIRCLE_RING_SIZE = 118.dp

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
    hasBackgroundImage: Boolean = false,
    // Se llama cada vez que la letra guardada de alguna canción cambia (se borra,
    // se encuentra online, o se inserta a mano), para que la lista de canciones
    // pueda refrescar el icono de "letra disponible" sin tener que reabrir la app.
    onLyricsChanged: () -> Unit = {}
) {
    val context = LocalContext.current

    val isRounded = LocalUseRoundCorners.current
    val artRadius = if (isRounded) 24.dp else 0.dp

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
    // NUEVO: pantalla de Cola editable — solo existe/se abre desde el reproductor
    // a pantalla completa. Vive como overlay independiente (igual que la
    // verificación de YouTube más abajo) para no interferir con el gesto de
    // swipe que ya usa esta pantalla para mostrar la letra.
    var showQueueScreen by remember { mutableStateOf(false) }
    val settingsRepo = remember { com.music.musicflame.data.SettingsRepository(context) }
    val lyricsRepoRef = remember { com.music.musicflame.data.LyricsRepository(context) }
    val lyricsSpeed = remember { settingsRepo.getLyricsSpeed() }
    val lyricsAnimType = remember { settingsRepo.getLyricsAnimationType() }
    val lyricsColorMode = remember { settingsRepo.getLyricsTextColorMode() }
    val lyricsTextColor = com.music.musicflame.ui.components.resolveLyricsTextColor(lyricsColorMode, "")
    // Cantidad de barras del ecualizador gráfico, configurable en Ajustes > Apariencia
    // (6 mínimo, 32 estándar, 64 máximo). Antes se armaba la vista pero nunca se le
    // pasaba este valor a AudioVisualizerBars más abajo, así que siempre quedaba en
    // el default interno del componente sin importar lo que eligieras en el slider.
    val equalizerBarCount = remember { settingsRepo.getEqualizerBarCount() }
    // Estilo visual del ecualizador (barras clásicas, espejado, ondas, círculo
    // pulsante, partículas, barras finas o VU meter retro), configurable en
    // Ajustes > Apariencia > "Estilo de ecualizador gráfico".
    val equalizerStyle = remember { settingsRepo.getEqualizerStyle() }

    // Color de las barras del visualizador. Por defecto ("Adaptativo") sigue
    // igual que antes: blanco o negro según lo que haya detrás de las barras:
    //  - Con imagen/gif de fondo: siempre se pone un overlay oscuro semitransparente
    //    (ver bgColor más abajo), así que blanco es lo que mejor contrasta ahí.
    //  - Sin imagen/gif: se mira el color de fondo real que está usando el tema
    //    (blanco/claro -> barras negras, oscuro -> barras blancas), usando su
    //    luminancia en vez de asumir "modo claro = tema claro" (por AMOLED, Material You, etc).
    // Con "Personalizado" (Ajustes > Apariencia > "Color del ecualizador") se
    // usa el color elegido por el usuario en vez de esta lógica adaptativa.
    val equalizerColorMode = remember { settingsRepo.getEqualizerColorMode() }
    val equalizerCustomColorHex = remember { settingsRepo.getEqualizerCustomColorHex() }
    val equalizerAdaptiveColor = if (hasBackgroundImage) {
        Color.White
    } else {
        if (MaterialTheme.colorScheme.background.luminance() > 0.5f) Color.Black else Color.White
    }
    val equalizerBarsColor = com.music.musicflame.ui.components.resolveEqualizerColor(
        equalizerColorMode, equalizerCustomColorHex, equalizerAdaptiveColor
    )
    val lyricsState = com.music.musicflame.ui.components.rememberLyricsState(song, lyricsRepoRef)
    var showYoutubeVerify by remember { mutableStateOf(false) }

    // Avisa hacia arriba (para refrescar el icono de "letra disponible" en la
    // lista) cada vez que una búsqueda de letra en curso termina, sea la
    // automática al abrir la canción, un "Reintentar" manual, o la disparada
    // por la verificación en YouTube.
    var wasLoadingLyrics by remember { mutableStateOf(false) }
    LaunchedEffect(lyricsState.isLoading) {
        if (wasLoadingLyrics && !lyricsState.isLoading) {
            onLyricsChanged()
        }
        wasLoadingLyrics = lyricsState.isLoading
    }

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
            // --- LETRA Y COLA: cada una se controla con SU PROPIO lado, y ese mismo lado
            // sirve tanto para entrar como para salir (toggle), sin mezclarlos:
            //   - Swipe IZQUIERDA: entra y sale de la Letra.
            //   - Swipe DERECHA: entra y sale de la Cola.
            // Se desactiva por completo mientras la verificación de YouTube está abierta
            // (esa pantalla necesita todos los gestos para su propio WebView).
            // Al vivir en el contenedor exterior (padre del HorizontalPager de carátulas y
            // del Slider, y también padre del overlay de la Cola), cualquier drag que
            // empiece sobre esos controles lo consumen ellos primero y este gesto no se
            // activa ahí; solo reacciona en el resto de la pantalla.
            .pointerInput(showYoutubeVerify) {
                if (showYoutubeVerify) return@pointerInput
                var totalDrag = 0f
                detectHorizontalDragGestures(
                    onDragStart = { totalDrag = 0f },
                    onHorizontalDrag = { _, dragAmount -> totalDrag += dragAmount },
                    onDragEnd = {
                        when {
                            showQueueScreen -> {
                                // Ya estamos en la Cola: swipe derecha vuelve a la vista normal
                                // (mismo lado que la abre, como toggle).
                                if (totalDrag > 120f) showQueueScreen = false
                            }
                            showLyrics -> {
                                // Ya estamos en la Letra: swipe izquierda vuelve a la vista normal
                                // (mismo lado que la abre, como toggle).
                                if (totalDrag < -120f) showLyrics = false
                            }
                            else -> {
                                // Vista normal: izquierda abre Letra, derecha abre Cola.
                                if (totalDrag < -120f) showLyrics = true
                                else if (totalDrag > 120f) showQueueScreen = true
                            }
                        }
                        totalDrag = 0f
                    },
                    onDragCancel = { totalDrag = 0f }
                )
            }
    ) {
        // --- ECUALIZADOR DE FONDO ---
        // Vive DETRÁS de todo (carátula/controles Y letra): un solo Composable
        // persistente, en vez de uno adentro de cada rama del AnimatedContent
        // de abajo. Así:
        //  - Nunca se re-crea (ni pierde su estado del Visualizer) al cambiar
        //    entre la vista normal y la Letra.
        //  - Se ve incluso mientras la Letra está abierta (más tenue, para no
        //    pelear con el texto), en vez de desaparecer.
        // Ocupa todo el ancho y se estira desde el fondo de la pantalla hacia
        // arriba, respetando ya el inset real de la barra de navegación (gestos
        // o 3 botones) porque este Box padre ya tiene .safeScreenPadding().
        // NOTA (círculo pulsante): este estilo NO se dibuja acá. A diferencia del
        // resto de los estilos (que sí funcionan bien como franja ambiental de
        // fondo), el círculo pulsante ahora se dibuja pegado al botón de
        // Play/Pause (vista normal) o flotando solo, sin botón detrás (vista de
        // Letra) — ver PULSE_CIRCLE_RING_SIZE más abajo, donde se usa.
        // NOTA (doble espejado): antes tenía su propio bloque aparte, con las
        // dos filas en cajas independientes pegadas a los bordes reales de
        // arriba y abajo de la pantalla. Se sacó ese tratamiento especial: el
        // usuario pidió que el doble espejado viva en la MISMA caja/posición
        // que el resto de los estilos (la franja de abajo, 26% de alto), así
        // que ahora pasa por acá igual que BARS/WATER_WAVE/etc. — usa
        // MirroredBarsEqualizerCanvas por dentro (ver EqualizerCanvas), que ya
        // dibuja las dos filas con su hueco en el medio dentro de una sola caja.
        if (equalizerStyle != com.music.musicflame.ui.components.EqualizerStyle.PULSE_CIRCLE) {
            com.music.musicflame.ui.components.GraphicEqualizer(
                style = equalizerStyle,
                audioSessionId = playerManager.audioSessionId.value,
                isPlaying = isPlaying,
                hasRecordAudioPermission = hasRecordAudioPermission,
                // Antes: opacidad completa en la vista normal, compitiendo visualmente con
                // los botones de control que quedan por encima. Bajado a 0.55 acá (y sigue
                // en 0.28 con la Letra abierta) para que se sienta "detrás", más ambiente
                // que protagonista — y sumado al degradado de abajo, que lo termina de
                // difuminar del todo justo donde arrancan los botones.
                color = if (showLyrics) equalizerBarsColor.copy(alpha = 0.28f) else equalizerBarsColor.copy(alpha = 0.55f),
                // Cantidad de barras elegida en Ajustes > Apariencia (antes no se pasaba
                // este parámetro, así que el slider no tenía ningún efecto acá).
                barCount = equalizerBarCount,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    // 26% del alto disponible (ya sin status bar/nav bar, por el
                    // .safeScreenPadding() del Box padre): mucho más grande que el
                    // strip fijo de 48dp de antes, pero sin tragarse el slider de
                    // arriba. Es solo un número -> fácil de subir/bajar a gusto.
                    .fillMaxHeight(0.26f)
                    .padding(horizontal = 8.dp)
            )
        }

        // --- CÍRCULO PULSANTE flotando en la vista de Letra ---
        // En la vista normal el aro se dibuja pegado al botón de Play/Pause (ver
        // más abajo, en la Row de controles). Acá, en cambio, no hay ningún botón
        // de Play/Pause (la Letra ocupa toda la pantalla), así que el aro flota
        // solo, anclado abajo al centro, para que la sensación de "el círculo
        // rodea el play/pause" se mantenga aunque el botón no esté visible.
        if (equalizerStyle == com.music.musicflame.ui.components.EqualizerStyle.PULSE_CIRCLE && showLyrics) {
            com.music.musicflame.ui.components.GraphicEqualizer(
                style = com.music.musicflame.ui.components.EqualizerStyle.PULSE_CIRCLE,
                audioSessionId = playerManager.audioSessionId.value,
                isPlaying = isPlaying,
                hasRecordAudioPermission = hasRecordAudioPermission,
                color = equalizerBarsColor.copy(alpha = 0.55f),
                barCount = equalizerBarCount,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
                    .size(PULSE_CIRCLE_RING_SIZE)
            )
        }

        // --- DEGRADADO DE DIFUMINADO (fila de abajo) ---
        // Se dibuja ENCIMA del ecualizador, ocupando la misma franja de abajo. Va de
        // "color de fondo real de la pantalla" (bgColor) arriba del todo — tapando/
        // difuminando las barras justo donde arrancan los botones de control — a
        // totalmente transparente abajo, dejando las barras bien vivas cerca del
        // borde inferior de la pantalla. Así el ecualizador se ve grande y vivo, pero
        // sin pelearle protagonismo visual a los botones que quedan por encima.
        // Con PULSE_CIRCLE no aplica (ya no hay ninguna franja de fondo ahí abajo
        // que difuminar). Con MIRRORED_BARS sí aplica ahora — ya no tiene su
        // propio bloque aparte, así que usa este mismo degradado como el resto.
        if (equalizerStyle != com.music.musicflame.ui.components.EqualizerStyle.PULSE_CIRCLE) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.26f)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(bgColor, Color.Transparent),
                        startY = 0f,
                        endY = Float.POSITIVE_INFINITY
                    )
                )
        )
        }


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
                        // .weight(1f) para que la Column quede acotada al ancho
                        // disponible de la fila; sin esto, el marquee del título no
                        // tiene un límite de ancho contra el cual deslizarse.
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = song.title,
                                fontWeight = FontWeight.Black,
                                fontSize = 16.sp,
                                color = adaptiveContentColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.basicMarquee(velocity = (-30).dp)
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
                        onInsertManual = { raw ->
                            lyricsState.onInsertManual(raw)
                            onLyricsChanged()
                        },
                        onSearchYoutube = { showYoutubeVerify = true },
                        onDeleteLyrics = {
                            lyricsState.onDeleteLyrics()
                            onLyricsChanged()
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

                        // NUEVO: botón de Cola — único punto de entrada a QueueScreen.
                        // "Queue" solo existe dentro del reproductor a pantalla completa.
                        IconButton(
                            onClick = { showQueueScreen = true },
                            modifier = Modifier.align(Alignment.CenterEnd).offset(x = 12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.QueueMusic,
                                contentDescription = "Cola de reproducción",
                                tint = adaptiveContentColor
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
                                    // Marquee hacia la derecha (velocity negativa invierte
                                    // el sentido por defecto, que es hacia la izquierda).
                                    modifier = Modifier
                                        .padding(horizontal = 16.dp)
                                        .basicMarquee(velocity = (-30).dp)
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

                        // El Box de afuera solo crece a PULSE_CIRCLE_RING_SIZE (118dp) cuando el
                        // estilo elegido es el círculo pulsante, para dejarle aire al aro
                        // alrededor; con cualquier otro estilo queda en 76dp como siempre (no
                        // le cambia el tamaño ni el espaciado de la fila a nadie más).
                        val playPauseBoxSize = if (equalizerStyle == com.music.musicflame.ui.components.EqualizerStyle.PULSE_CIRCLE) {
                            PULSE_CIRCLE_RING_SIZE
                        } else {
                            76.dp
                        }
                        Box(
                            modifier = Modifier.size(playPauseBoxSize),
                            contentAlignment = Alignment.Center
                        ) {
                            // Aro del círculo pulsante: SOLO cuando ese es el estilo elegido en
                            // Ajustes > Apariencia. Se dibuja DETRÁS del botón (primer hijo del
                            // Box), late con el audio real igual que en el resto de las pantallas.
                            if (equalizerStyle == com.music.musicflame.ui.components.EqualizerStyle.PULSE_CIRCLE) {
                                com.music.musicflame.ui.components.GraphicEqualizer(
                                    style = com.music.musicflame.ui.components.EqualizerStyle.PULSE_CIRCLE,
                                    audioSessionId = playerManager.audioSessionId.value,
                                    isPlaying = isPlaying,
                                    hasRecordAudioPermission = hasRecordAudioPermission,
                                    color = equalizerBarsColor.copy(alpha = 0.55f),
                                    barCount = equalizerBarCount,
                                    modifier = Modifier.fillMaxSize()
                                )
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
                        }

                        IconButton(onClick = onSkipNext) {
                            Icon(Icons.Filled.SkipNext, "Siguiente", modifier = Modifier.size(44.dp), tint = adaptiveContentColor)
                        }
                    }

                    // Antes había un Spacer + el ecualizador fijo (48dp) aquí. Ahora el
                    // ecualizador vive en la capa de fondo persistente (ver más arriba, antes
                    // del AnimatedContent), así que solo dejamos aire de respiro al final;
                    // el resto del espacio hacia abajo lo llena visualmente el ecualizador
                    // de fondo, que se ve alrededor/detrás de estos controles.
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }

        // Misma animación que la Letra (slide + fade, 280ms): entra deslizando desde la
        // derecha -tal como se abre, con swipe derecha- y sale deslizando hacia la derecha.
        AnimatedVisibility(
            visible = showQueueScreen,
            enter = slideInHorizontally(tween(280)) { it } + fadeIn(tween(280)),
            exit = slideOutHorizontally(tween(280)) { it } + fadeOut(tween(280))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(bgColor)
                    .safeScreenPadding()
                    // Escudo: igual que el resto de la pantalla, no deja pasar toques
                    // hacia lo que esté detrás (mini-reproductor, lista, etc.)
                    .pointerInput(Unit) { detectTapGestures { } }
            ) {
                QueueScreen(
                    playerManager = playerManager,
                    currentSong = song,
                    adaptiveContentColor = adaptiveContentColor,
                    hasBackgroundImage = hasBackgroundImage,
                    onClose = { showQueueScreen = false },
                    onSongClick = { clickedSong ->
                        playerManager.playSong(clickedSong, playerManager.queue)
                    }
                )
            }
        }

        if (showYoutubeVerify) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                YoutubeVerifyWebView(
                    query = "${song.artist} ${song.title}",
                    onTitleExtracted = { extractedTitle ->
                        lyricsState.onYoutubeTitleFound(extractedTitle)
                        showYoutubeVerify = false
                    },
                    onClose = { showYoutubeVerify = false },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}