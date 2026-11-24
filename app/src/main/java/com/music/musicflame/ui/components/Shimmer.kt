package com.music.musicflame.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.isUnspecified
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Skeletons de carga (shimmer) reutilizables en toda la app.
 *
 * Piezas:
 *  - [Modifier.shimmer]: pinta cualquier caja con el brillo animado.
 *  - [ShimmerHost]: opcional, hace que todos los shimmer de adentro compartan UNA sola
 *    animación (más barato y se ven sincronizados en vez de desfasados).
 *  - Skeletons ya armados con las MISMAS medidas que la fila/tarjeta real, para que al
 *    llegar los datos la lista no "salte": [SongRowSkeleton], [SongListSkeleton],
 *    [AlbumGridSkeleton], [MemberRowSkeleton], [PurchaseRowSkeleton], [LyricsSkeleton].
 */

private val LocalShimmerProgress = compositionLocalOf<State<Float>?> { null }

@Composable
private fun rememberShimmerProgress(): State<Float> {
    val transition = rememberInfiniteTransition(label = "shimmer")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1300, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerProgress"
    )
}

/** Comparte una sola animación de shimmer entre todo lo que haya dentro de [content]. */
@Composable
fun ShimmerHost(content: @Composable () -> Unit) {
    val progress = rememberShimmerProgress()
    CompositionLocalProvider(LocalShimmerProgress provides progress, content = content)
}

/**
 * Rellena la caja con un fondo tenue y una franja de brillo que la recorre.
 * Por defecto usa el color de texto del tema (onSurface), así se ve bien en claro/oscuro
 * y también encima de una imagen de fondo. Para zonas con color propio (ej. letras),
 * pasa [baseColor] y [highlightColor].
 */
fun Modifier.shimmer(
    shape: Shape = RoundedCornerShape(8.dp),
    baseColor: Color = Color.Unspecified,
    highlightColor: Color = Color.Unspecified
): Modifier = composed {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val base = if (baseColor.isUnspecified) onSurface.copy(alpha = 0.10f) else baseColor
    val highlight = if (highlightColor.isUnspecified) onSurface.copy(alpha = 0.22f) else highlightColor
    // Si hay un ShimmerHost arriba se reutiliza su animación; si no, esta caja tiene la suya.
    val progress = LocalShimmerProgress.current ?: rememberShimmerProgress()

    this
        .clip(shape)
        .drawBehind {
            // progress.value se lee en la fase de dibujo: animar no recompone nada.
            val width = size.width.coerceAtLeast(1f)
            val band = width * 0.6f
            val startX = -band + (width + band) * progress.value
            drawRect(base)
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color.Transparent, highlight, Color.Transparent),
                    start = Offset(startX, 0f),
                    end = Offset(startX + band, size.height * 0.35f)
                )
            )
        }
}

// ---------------------------------------------------------------------------------------
// Canciones / videos de YouTube (misma fila: tarjeta con carátula de 50dp)
// ---------------------------------------------------------------------------------------

private val TitleFractions = listOf(0.62f, 0.48f, 0.72f, 0.55f, 0.40f, 0.66f, 0.52f)
private val SubtitleFractions = listOf(0.38f, 0.30f, 0.45f, 0.34f, 0.28f, 0.42f)

/** Una fila de canción/video en carga. Mide igual que la tarjeta real (12dp + carátula 50dp). */
@Composable
fun SongRowSkeleton(
    cardRadius: Dp,
    artRadius: Dp,
    hasBackgroundImage: Boolean,
    modifier: Modifier = Modifier,
    titleFraction: Float = 0.6f,
    subtitleFraction: Float = 0.4f
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (hasBackgroundImage) {
                MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.70f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (hasBackgroundImage) 0.dp else 4.dp),
        shape = RoundedCornerShape(cardRadius)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.size(50.dp).shimmer(RoundedCornerShape(artRadius)))
            Column(modifier = Modifier.padding(start = 16.dp).weight(1f)) {
                Spacer(Modifier.fillMaxWidth(titleFraction).height(16.dp).shimmer(RoundedCornerShape(6.dp)))
                Spacer(Modifier.height(8.dp))
                Spacer(Modifier.fillMaxWidth(subtitleFraction).height(12.dp).shimmer(RoundedCornerShape(6.dp)))
            }
        }
    }
}

/** Lista completa de filas en carga: calcula cuántas caben en la pantalla. No hace scroll. */
@Composable
fun SongListSkeleton(
    cardRadius: Dp,
    artRadius: Dp,
    hasBackgroundImage: Boolean,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize().clipToBounds()) {
        // 74dp de la tarjeta + 6dp de separación; una de más para que nunca quede hueco abajo.
        val rows = (maxHeight / 80.dp).toInt() + 1
        ShimmerHost {
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp).padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                repeat(rows) { i ->
                    SongRowSkeleton(
                        cardRadius = cardRadius,
                        artRadius = artRadius,
                        hasBackgroundImage = hasBackgroundImage,
                        titleFraction = TitleFractions[i % TitleFractions.size],
                        subtitleFraction = SubtitleFractions[i % SubtitleFractions.size]
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------
// Álbumes / artistas (grilla con carátula cuadrada o circular)
// ---------------------------------------------------------------------------------------

/** Grilla de álbumes (o artistas con [circular] = true) en carga. Mismo padding que la real. */
@Composable
fun AlbumGridSkeleton(
    columns: Int,
    modifier: Modifier = Modifier,
    circular: Boolean = false
) {
    val artShape: Shape = if (circular) CircleShape else RoundedCornerShape(12.dp)
    ShimmerHost {
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns.coerceAtLeast(1)),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            userScrollEnabled = false,
            modifier = modifier.fillMaxSize()
        ) {
            items(columns.coerceAtLeast(1) * 6) { i ->
                Column(modifier = Modifier.fillMaxWidth()) {
                    Spacer(Modifier.fillMaxWidth().aspectRatio(1f).shimmer(artShape))
                    Spacer(Modifier.height(8.dp))
                    Spacer(Modifier.fillMaxWidth(TitleFractions[i % TitleFractions.size]).height(14.dp).shimmer(RoundedCornerShape(6.dp)))
                    Spacer(Modifier.height(6.dp))
                    Spacer(Modifier.fillMaxWidth(SubtitleFractions[i % SubtitleFractions.size] + 0.1f).height(10.dp).shimmer(RoundedCornerShape(6.dp)))
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------
// En compañía (Together): miembros de la sala mientras se conecta
// ---------------------------------------------------------------------------------------

/** Tarjeta de miembro en carga (avatar de 42dp + nombre + estado). */
@Composable
fun MemberRowSkeleton(modifier: Modifier = Modifier, nameFraction: Float = 0.5f) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Spacer(Modifier.size(42.dp).shimmer(CircleShape))
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Spacer(Modifier.fillMaxWidth(nameFraction).height(14.dp).shimmer(RoundedCornerShape(6.dp)))
                Spacer(Modifier.height(8.dp))
                Spacer(Modifier.fillMaxWidth(nameFraction * 0.6f).height(10.dp).shimmer(RoundedCornerShape(6.dp)))
            }
        }
    }
}

// ---------------------------------------------------------------------------------------
// Pagos: fila de compra mientras se restaura
// ---------------------------------------------------------------------------------------

/** Fila de "compra reconocida" en carga (icono de 18dp + nombre del producto). */
@Composable
fun PurchaseRowSkeleton(modifier: Modifier = Modifier, nameFraction: Float = 0.55f) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().padding(vertical = 6.dp)
    ) {
        Spacer(Modifier.size(18.dp).shimmer(CircleShape))
        Spacer(Modifier.width(8.dp))
        Spacer(Modifier.fillMaxWidth(nameFraction).height(13.dp).shimmer(RoundedCornerShape(6.dp)))
    }
}

// ---------------------------------------------------------------------------------------
// Letras
// ---------------------------------------------------------------------------------------

private val LyricsBarFractions = listOf(0.70f, 0.50f, 0.85f, 0.60f, 0.78f, 0.45f, 0.66f)

/**
 * Barras de letra en carga, centradas como las líneas reales. [color] es el color de texto de
 * la letra, así el skeleton siempre contrasta con el fondo del reproductor.
 */
@Composable
fun LyricsSkeleton(color: Color, modifier: Modifier = Modifier) {
    ShimmerHost {
        Column(
            modifier = modifier.fillMaxWidth().padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            LyricsBarFractions.forEach { fraction ->
                Spacer(
                    Modifier
                        .fillMaxWidth(fraction)
                        .height(16.dp)
                        .shimmer(
                            shape = RoundedCornerShape(8.dp),
                            baseColor = color.copy(alpha = 0.12f),
                            highlightColor = color.copy(alpha = 0.30f)
                        )
                )
            }
        }
    }
}
