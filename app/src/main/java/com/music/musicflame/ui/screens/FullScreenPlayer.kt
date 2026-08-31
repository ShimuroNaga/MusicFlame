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
import androidx.compose.ui.zIndex
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
import com.music.musicflame.data.ArtworkCacheRepository
import com.music.musicflame.data.ArtworkSource
import android.net.Uri
import com.music.musicflame.ui.components.YoutubeVerifyWebView
import com.music.musicflame.ui.components.embeddedArtUriFor
import com.music.musicflame.ui.components.SharedAlbumArtImageLoader
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

    // OPTIMIZACIÓN DE RENDIMIENTO: antes esto era "var currentPositionMs by remember {...}",
    // que se LEÍA directamente acá abajo (en la barra de progreso y en LyricsView). Cada
    // lectura de un state directamente en el cuerpo de FullScreenPlayer suscribe a TODO ese
    // scope de recomposición a los cambios de esa variable — y como se actualiza cada 500ms
    // mientras suena música, toda la pantalla (carátula, pager, gestos, botones) se estaba
    // recomponiendo 2 veces por segundo solo para mover la barra de progreso.
    // Ahora guardamos el OBJETO State (currentPositionMsState) sin leer su .longValue acá.
    // Ese objeto se pasa tal cual (por referencia) a PlaybackSeekBar y a LyricsWithLivePosition
    // más abajo, que son composables chicos y separados: son ELLOS los que leen .longValue,
    // así que son los ÚNICOS que se recomponen cada tick, no FullScreenPlayer entero.
    val currentPositionMsState = remember { mutableLongStateOf(0L) }
    var isDragging by remember { mutableStateOf(false) }

    LaunchedEffect(isPlaying, isDragging, song) {
        if (!isDragging) {
            currentPositionMsState.longValue = playerManager.currentPosition
        }
        while (isPlaying && !isDragging) {
            delay(500)
            currentPositionMsState.longValue = playerManager.currentPosition
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
    // Blinda el RENDERIZADO (no solo los selectores de Ajustes): si el valor
    // guardado es una opción de pago y el usuario no está desbloqueado (p.ej.
    // quedó guardado de cuando el flag de pruebas dejaba elegir cualquier
    // cosa), se ignora y se usa el default gratis, en vez de seguir
    // mostrando la personalización sin haber pagado.
    // Reactivo (ver ProStatusHolder): antes quedaba "congelado" con el valor
    // que tenía cuando se abría el reproductor a pantalla completa, así que
    // comprar o iniciar sesión con la cuenta dueña no se reflejaba acá hasta
    // reiniciar la app. Ahora se lee del estado global compartido.
    val isProUnlocked = com.music.musicflame.data.ProStatusHolder.isProUnlocked
    val lyricsSpeed = remember { settingsRepo.getLyricsSpeed() }
    val lyricsAnimType = remember { settingsRepo.getLyricsAnimationType() }
    // remember(isProUnlocked): se recalcula solo cuando cambia el desbloqueo
    // (no en cada tick de posición), pero ya no queda pegado al valor viejo.
    val lyricsColorMode = remember(isProUnlocked) {
        val saved = settingsRepo.getLyricsTextColorMode()
        val locked = saved == "Personalizado" || saved == com.music.musicflame.ui.theme.COLOR_MODE_RAINBOW
        if (!isProUnlocked && locked) "Adaptativo" else saved
    }
    // Hex del color personalizado (catálogo, punto 2). Antes se pasaba "" a
    // resolveLyricsTextColor y por eso "Personalizado" nunca pintaba nada
    // distinto de negro (fallback de parseCustomTextColor ante texto vacío).
    val lyricsCustomColorHex = remember { settingsRepo.getLyricsCustomColorHex() }
    val lyricsTextColor = com.music.musicflame.ui.components.resolveLyricsTextColor(lyricsColorMode, lyricsCustomColorHex)
    // Cantidad de barras del ecualizador gráfico, configurable en Ajustes > Apariencia
    // (6 mínimo, 32 estándar, 64 máximo). Antes se armaba la vista pero nunca se le
    // pasaba este valor a AudioVisualizerBars más abajo, así que siempre quedaba en
    // el default interno del componente sin importar lo que eligieras en el slider.
    val equalizerBarCount = remember { settingsRepo.getEqualizerBarCount() }
    // Estilo visual del ecualizador (barras clásicas, espejado, ondas, círculo
    // pulsante, partículas, barras finas o VU meter retro), configurable en
    // Ajustes > Apariencia > "Estilo de ecualizador gráfico".
    val equalizerStyle = remember(isProUnlocked) {
        val saved = settingsRepo.getEqualizerStyle()
        if (!isProUnlocked && saved != com.music.musicflame.ui.components.EqualizerStyle.BARS) {
            com.music.musicflame.ui.components.EqualizerStyle.BARS
        } else saved
    }

    // Color de las barras del visualizador. Por defecto ("Adaptativo") sigue
    // igual que antes: blanco o negro según lo que haya detrás de las barras:
    //  - Con imagen/gif de fondo: siempre se pone un overlay oscuro semitransparente
    //    (ver bgColor más abajo), así que blanco es lo que mejor contrasta ahí.
    //  - Sin imagen/gif: se mira el color de fondo real que está usando el tema
    //    (blanco/claro -> barras negras, oscuro -> barras blancas), usando su
    //    luminancia en vez de asumir "modo claro = tema claro" (por AMOLED, Material You, etc).
    // Con "Personalizado" (Ajustes > Apariencia > "Color del ecualizador") se
    // usa el color elegido por el usuario en vez de esta lógica adaptativa.
    // Los 3 modos (Adaptativo/Personalizado/Arcoíris) son de pago para este
    // selector en particular, así que sin desbloquear cae siempre a "" (que
    // resolveEqualizerColor no reconoce y por lo tanto usa adaptiveColor,
    // el comportamiento de siempre).
    val equalizerColorMode = remember(isProUnlocked) {
        val saved = settingsRepo.getEqualizerColorMode()
        if (!isProUnlocked) "" else saved
    }
    val equalizerCustomColorHex = remember { settingsRepo.getEqualizerCustomColorHex() }
    val equalizerAdaptiveColor = if (hasBackgroundImage) {
        Color.White
    } else {
        if (MaterialTheme.colorScheme.background.luminance() > 0.5f) Color.Black else Color.White
    }
    val equalizerBarsColor = com.music.musicflame.ui.components.resolveEqualizerColor(
        equalizerColorMode, equalizerCustomColorHex, equalizerAdaptiveColor
    )

    // --- ESPECTRO COMPARTIDO PARA EL DOBLE ESPEJADO ---
    // Solo se calcula cuando el estilo elegido es MIRRORED_BARS. Se hoistea acá
    // (en vez de dejar que cada canvas llame a GraphicEqualizer por su cuenta)
    // porque el doble espejado necesita DOS canvases en DOS posiciones reales
    // de la pantalla (fila de abajo a nivel de los botones, fila de arriba
    // pegada al borde real de arriba, fuera del Box con safeScreenPadding —
    // ver más abajo). Si cada fila creara su propio android.media.audiofx.Visualizer
    // para el mismo audioSessionId, se duplicaría la captura FFT en cada frame
    // sin necesidad; con un solo EqualizerLevelsState compartido, ambas filas
    // dibujan a partir de los mismos niveles ya calculados una sola vez.
    val mirroredEqualizerSpectrum = if (equalizerStyle == com.music.musicflame.ui.components.EqualizerStyle.MIRRORED_BARS) {
        com.music.musicflame.ui.components.rememberAudioSpectrum(
            audioSessionId = playerManager.audioSessionId.value,
            isPlaying = isPlaying,
            hasRecordAudioPermission = hasRecordAudioPermission,
            barCount = equalizerBarCount
        )
    } else null

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

    // --- CONTENEDOR EXTERIOR SIN INSETS ---
    // Envuelve a TODO lo de abajo (contenido normal + overlays de Cola/YouTube),
    // que sí respeta status bar/nav bar vía .safeScreenPadding(). Este Box de
    // afuera existe solo para poder dibujar la fila de ARRIBA del doble
    // espejado (justo abajo) tocando el borde REAL de la pantalla — ella sí
    // necesita vivir fuera de cualquier padding de status bar.
    //
    // FIX: el fondo (bgColor) se pinta ACÁ, en el Box exterior, y NO en el
    // interior. Antes vivía en el Box interior (siguiente hijo) y al ser un
    // fondo OPACO tapaba por completo la fila de arriba, que quedaba
    // "detrás del fondo" en vez de "detrás de la carátula/letras" — se veía
    // como si el ecualizador hubiera desaparecido. Pintando el fondo acá
    // (antes que la fila de arriba) y dejando el Box interior transparente,
    // el orden de capas queda: 1) fondo sólido, 2) fila de arriba del
    // espejado, 3) todo el contenido (carátula, letras, botones) — visible,
    // detrás del contenido, como se pidió. Cubre exactamente la misma área
    // que antes (pantalla completa): no cambia nada visualmente para el
    // resto de los estilos ni para ningún celular.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
    ) {

        // --- DOBLE ESPEJADO: fila de ARRIBA, pegada al borde REAL de la pantalla ---
        // Se declara ACÁ (primer hijo del Box exterior, justo después del
        // fondo) a propósito: en Compose, el primer hijo de un Box se dibuja
        // PRIMERO, o sea queda DETRÁS de los hijos siguientes. Queda encima
        // del fondo sólido (se ve) pero debajo de la carátula/letras/botones
        // (no los tapa).
        if (equalizerStyle == com.music.musicflame.ui.components.EqualizerStyle.MIRRORED_BARS && mirroredEqualizerSpectrum != null) {
            // FIX (cuadro negro que tapaba la mitad derecha de la fila de arriba):
            // antes el Canvas se dimensionaba con .fillMaxWidth().fillMaxHeight(0.16f)
            // directamente sobre sí mismo, dentro del Box exterior. En ciertos casos
            // (recomposición al cambiar de canción, AnimatedContent corriendo al
            // mismo tiempo, etc.) esa resolución de medidas quedaba corta y el
            // Canvas terminaba con un ancho real menor al de la pantalla — las
            // barras se dibujan igual (matemáticamente ocupan TODO el ancho del
            // Canvas), pero como el Canvas mismo no llegaba al borde derecho, se
            // veía como un bloque negro sólido tapando esa zona.
            // Ahora: un Box exterior fija el área exacta (ancho completo, 16% del
            // alto) y el Canvas usa .matchParentSize(), que copia el tamaño YA
            // RESUELTO de ese Box en vez de resolver su propio tamaño — así queda
            // garantizado que siempre llega de punta a punta, sin importar qué esté
            // pasando alrededor. zIndex explícito para dejar bien firme el orden:
            // detrás de las letras/carátula (que se declaran después, más adelante
            // en este mismo árbol) pero por delante del fondo sólido.
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    // Alto chico a propósito (16% de la pantalla COMPLETA, no solo
                    // del área segura): es una franja angosta pegada arriba, no un
                    // segundo protagonista — el protagonista sigue siendo la fila de
                    // abajo, igual que en el resto de los estilos.
                    .fillMaxHeight(0.16f)
                    .zIndex(1f)
            ) {
                com.music.musicflame.ui.components.MirroredBarsTopRowCanvas(
                    spectrum = mirroredEqualizerSpectrum,
                    color = if (showLyrics) equalizerBarsColor.copy(alpha = 0.28f) else equalizerBarsColor.copy(alpha = 0.55f),
                    modifier = Modifier
                        .matchParentSize()
                        .padding(horizontal = 8.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
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
            if (equalizerStyle == com.music.musicflame.ui.components.EqualizerStyle.MIRRORED_BARS) {
                // DOBLE ESPEJADO — fila de ABAJO: vuelve a vivir en su propia caja
                // independiente (como al principio), pero en la MISMA posición que
                // el resto de los estilos usa (26% de alto, pegada al borde de
                // abajo del área segura) — o sea, a nivel de los 3 botones de
                // control, que es "donde debe" ir. Dibuja con BarsEqualizerCanvas
                // (el mismo trazo que el estilo clásico), a partir del espectro
                // compartido calculado arriba.
                com.music.musicflame.ui.components.BarsEqualizerCanvas(
                    spectrum = mirroredEqualizerSpectrum!!,
                    color = if (showLyrics) equalizerBarsColor.copy(alpha = 0.28f) else equalizerBarsColor.copy(alpha = 0.55f),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .fillMaxHeight(0.26f)
                        .padding(horizontal = 8.dp)
                )
            } else if (equalizerStyle != com.music.musicflame.ui.components.EqualizerStyle.PULSE_CIRCLE) {
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
                        // Ver comentario en currentPositionMsState más arriba: usamos el wrapper
                        // LyricsWithLivePosition (definido al final del archivo) en vez de leer
                        // currentPositionMsState.longValue acá directamente, para que el tick de
                        // posición solo recomponga ese wrapper chico y no toda esta rama.
                        LyricsWithLivePosition(
                            positionState = currentPositionMsState,
                            song = song,
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
                                        // ANTES: acá se cargaba pageSong.albumArtUri directo con Coil.
                                        // Eso es la Uri "de fábrica" por ÁLBUM (no por canción); cuando
                                        // varias canciones se agrupan en el mismo álbum, Android les
                                        // asigna el mismo ALBUM_ID y esa Uri devuelve UN SOLO bitmap
                                        // "representante" para todas ellas (la carga no falla, solo trae
                                        // la imagen equivocada) -> se veía la misma carátula repetida
                                        // para las N canciones del álbum en vez de la real de cada una.
                                        // AHORA: replicamos la misma resolución con fallback que ya usa
                                        // el componente compartido AlbumArt() (carátula personalizada
                                        // primero si existe, si no la embebida real de ESE archivo
                                        // puntual, y solo como último recurso la Uri genérica del álbum).
                                        var useEmbeddedFallback by remember(pageSong.id) { mutableStateOf(false) }
                                        var useAlbumUriFallback by remember(pageSong.id) { mutableStateOf(false) }
                                        // Igual que en AlbumArt.kt: si el ÚLTIMO escalón de fallback también
                                        // falla, forzamos el modelo a null para caer siempre al ícono, en vez
                                        // de depender de que el `when` no repita la misma rama fallida.
                                        var exhaustedAllFallbacks by remember(pageSong.id) { mutableStateOf(false) }

                                        val effectiveArtModel: Any? = when {
                                            exhaustedAllFallbacks -> null
                                            pageSong.hasCustomCover && pageSong.albumArtUri != null && !useEmbeddedFallback -> pageSong.albumArtUri
                                            pageSong.path.isNotEmpty() && !useAlbumUriFallback -> embeddedArtUriFor(pageSong.path)
                                            !pageSong.hasCustomCover && pageSong.albumArtUri != null -> pageSong.albumArtUri
                                            else -> null
                                        }

                                        if (effectiveArtModel != null) {
                                            SubcomposeAsyncImage(
                                                model = ImageRequest.Builder(context)
                                                    .data(effectiveArtModel)
                                                    .crossfade(true)
                                                    .allowHardware(false)
                                                    .listener(
                                                        // FIX: este pager encuentra la carátula embebida real de
                                                        // cada canción por su cuenta, pero antes nunca avisaba al
                                                        // caché persistente que usa AlbumArt() en las listas
                                                        // (ArtworkCacheRepository). Si ese caché había quedado con
                                                        // NONE guardado por un intento fallido anterior (ej. la
                                                        // primera vez que MediaStore escaneó el archivo, antes de
                                                        // que estuviera del todo accesible), la lista se quedaba
                                                        // mostrando el ícono genérico para siempre aunque acá sí
                                                        // se viera la carátula real — porque nada corregía ese
                                                        // NONE viejo. Ahora, cada vez que este pager resuelve una
                                                        // carátula (con éxito o agotando los intentos), lo guarda
                                                        // en el mismo caché que usan las listas, así se autocorrige.
                                                        onSuccess = { _, _ ->
                                                            if (pageSong.path.isNotEmpty()) {
                                                                val source = if (effectiveArtModel is Uri && effectiveArtModel.scheme == "musicflame-embedded") {
                                                                    ArtworkSource.EMBEDDED
                                                                } else {
                                                                    ArtworkSource.ALBUM_URI
                                                                }
                                                                ArtworkCacheRepository(context).set(pageSong.path, source)
                                                            }
                                                        },
                                                        onError = { _, _ ->
                                                            when {
                                                                pageSong.hasCustomCover && !useEmbeddedFallback -> useEmbeddedFallback = true
                                                                !useAlbumUriFallback -> useAlbumUriFallback = true
                                                                else -> {
                                                                    exhaustedAllFallbacks = true
                                                                    if (!pageSong.hasCustomCover && pageSong.path.isNotEmpty()) {
                                                                        ArtworkCacheRepository(context).set(pageSong.path, ArtworkSource.NONE)
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    )
                                                    .build(),
                                                imageLoader = SharedAlbumArtImageLoader.get(context),
                                                contentDescription = "Carátula",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            ) {
                                                val painterState = painter.state
                                                if (painterState is coil.compose.AsyncImagePainter.State.Success) {
                                                    // Carátula encontrada: mostramos la imagen real
                                                    SubcomposeAsyncImageContent()
                                                } else if (painterState is coil.compose.AsyncImagePainter.State.Error) {
                                                    // No hay carátula real (ni personalizada, ni embebida, ni de álbum): ícono
                                                    Icon(Icons.Filled.MusicNote, null, modifier = Modifier.size(80.dp), tint = adaptiveContentColor.copy(alpha = 0.3f))
                                                } else {
                                                    // Mientras carga (Loading): mismo ícono y mismo tono que el
                                                    // estado Error de arriba (antes 0.15f vs 0.3f, se veía
                                                    // "apagado" mientras cargaba y luego saltaba a más marcado).
                                                    Icon(Icons.Filled.MusicNote, null, modifier = Modifier.size(80.dp), tint = adaptiveContentColor.copy(alpha = 0.3f))
                                                }
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

                        // Ver comentario en currentPositionMsState más arriba: PlaybackSeekBar
                        // (definido al final del archivo) es quien lee currentPositionMsState.longValue,
                        // así que es el único que se recompone cada 500ms, no toda esta rama de la pantalla.
                        PlaybackSeekBar(
                            positionState = currentPositionMsState,
                            totalDuration = totalDuration,
                            adaptiveContentColor = adaptiveContentColor,
                            onDragStart = { isDragging = true },
                            onDragChange = { newPositionMs -> currentPositionMsState.longValue = newPositionMs },
                            onDragEnd = { finalPositionMs ->
                                isDragging = false
                                playerManager.seekTo(finalPositionMs)
                            }
                        )

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
}

// ---------------------------------------------------------------------------------
// OPTIMIZACIÓN DE RENDIMIENTO (barra de progreso a pantalla completa)
// ---------------------------------------------------------------------------------
// Los dos composables de acá abajo existen SOLO para aislar la lectura del tick de
// posición (currentPositionMsState, que cambia cada 500ms mientras suena música) en
// el scope de recomposición más chico posible. Reciben el OBJETO State<Long> (no un
// Long ya leído) y son ELLOS los que hacen ".longValue" — así, cuando la posición
// cambia, Compose solo vuelve a ejecutar a estos dos composables chicos, no toda la
// rama de FullScreenPlayer (carátula, pager, gestos, botones) que los rodea. Antes
// currentPositionMs se leía directo ahí arriba, y esa lectura hacía que toda esa
// rama se recompusiera 2 veces por segundo.

/** Barra de progreso + textos de tiempo transcurrido/total, para la vista normal (no letras). */
@Composable
private fun PlaybackSeekBar(
    positionState: State<Long>,
    totalDuration: Long,
    adaptiveContentColor: Color,
    onDragStart: () -> Unit,
    onDragChange: (Long) -> Unit,
    onDragEnd: (Long) -> Unit
) {
    val currentPositionMs = positionState.value

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
                onDragStart()
                onDragChange((newValue * totalDuration).toLong())
            },
            onValueChangeFinished = {
                onDragEnd(positionState.value)
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
}

/** Wrapper que solo existe para leer positionState.value acá adentro (ver comentario arriba) antes de pasárselo a LyricsView. */
@Composable
private fun LyricsWithLivePosition(
    positionState: State<Long>,
    song: Song,
    lyrics: com.music.musicflame.data.ParsedLyrics,
    speed: Float,
    animationType: String,
    textColor: Color,
    isLoading: Boolean,
    searchFailed: Boolean,
    onSearchOnline: () -> Unit,
    onInsertManual: (String) -> Unit,
    onSearchYoutube: () -> Unit,
    onDeleteLyrics: () -> Unit,
    modifier: Modifier = Modifier
) {
    com.music.musicflame.ui.components.LyricsView(
        song = song,
        positionMs = positionState.value,
        lyrics = lyrics,
        speed = speed,
        animationType = animationType,
        textColor = textColor,
        isLoading = isLoading,
        searchFailed = searchFailed,
        onSearchOnline = onSearchOnline,
        onInsertManual = onInsertManual,
        onSearchYoutube = onSearchYoutube,
        onDeleteLyrics = onDeleteLyrics,
        modifier = modifier
    )
}