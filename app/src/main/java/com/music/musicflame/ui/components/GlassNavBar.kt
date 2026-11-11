package com.music.musicflame.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.musicflame.data.SettingsRepository
import com.music.musicflame.navigation.Screen
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Estilos de la barra inferior (Ajustes > Apariencia).
 *  - CLASSIC: la barra de siempre (NavigationBarItem).
 *  - GLASS_NOTCH: barra de vidrio con una "joroba" animada sobre la pestaña activa y borde brillante.
 *  - GLASS_BUBBLE: barra de vidrio con una muesca donde sube una burbuja con el ícono activo.
 */
enum class NavBarStyle {
    CLASSIC, GLASS_NOTCH, GLASS_BUBBLE;

    companion object {
        fun fromName(name: String?): NavBarStyle = values().firstOrNull { it.name == name } ?: GLASS_NOTCH
    }
}

/** Estado compartido (Ajustes lo escribe, MainActivity lo lee) para que el cambio se vea al instante. */
object NavBarStyleHolder {
    private var holder: MutableState<NavBarStyle>? = null

    fun state(repo: SettingsRepository): MutableState<NavBarStyle> {
        val current = holder
        if (current != null) return current
        return mutableStateOf(repo.getNavBarStyle()).also { holder = it }
    }
}

/**
 * Barra inferior de vidrio con animación. Mantiene el comportamiento de la barra clásica:
 * se ven 5 pestañas a la vez y la lista se desliza ([listState] es el mismo de MainActivity).
 * [style] debe ser GLASS_NOTCH o GLASS_BUBBLE.
 */
@Composable
fun GlassNavBar(
    style: NavBarStyle,
    items: List<Screen>,
    selectedIndex: Int,
    listState: LazyListState,
    roundCorners: Boolean,
    hasBackgroundImage: Boolean,
    onItemClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val colors = MaterialTheme.colorScheme
    val isDark = colors.surface.luminance() < 0.5f
    val isBubble = style == NavBarStyle.GLASS_BUBBLE
    val primary = colors.primary

    val barHeight = if (isBubble) 58.dp else 64.dp
    val rise = if (isBubble) 24.dp else 14.dp

    // Rebote de la burbuja cada vez que cambias de pestaña.
    val bubbleScale = remember { Animatable(1f) }
    LaunchedEffect(selectedIndex, isBubble) {
        if (isBubble) {
            bubbleScale.snapTo(0.78f)
            bubbleScale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(barHeight + rise)
        // Sin clipToBounds: antes recortaba la cima y el brillo de la joroba (se veía aplastada).
    ) {
        val itemWidth = maxWidth / 5f
        val widthPx = with(density) { maxWidth.toPx() }
        val itemWidthPx = widthPx / 5f
        val risePx = with(density) { rise.toPx() }
        val barHeightPx = with(density) { barHeight.toPx() }

        // Centro (en px) de la pestaña activa dentro de la barra, aunque la lista esté desplazada.
        // Todas las pestañas miden lo mismo, así que se calcula sin buscar el item visible.
        val targetCx by remember(itemWidthPx, selectedIndex) {
            derivedStateOf {
                (selectedIndex - listState.firstVisibleItemIndex) * itemWidthPx -
                        listState.firstVisibleItemScrollOffset + itemWidthPx / 2f
            }
        }
        val cxAnim = animateFloatAsState(
            targetValue = targetCx,
            animationSpec = spring(dampingRatio = 0.65f, stiffness = 420f),
            label = "navGlassCx"
        )

        val bubbleRadiusPx = with(density) { 24.dp.toPx() }
        val notchDepthPx = with(density) { 28.dp.toPx() }
        val bubbleCenterY = risePx + notchDepthPx - bubbleRadiusPx - with(density) { 3.dp.toPx() }

        Canvas(modifier = Modifier.fillMaxSize()) {
            // Recorte solo a los lados (la cima/brillo de la joroba sí pueden sobresalir por arriba).
            // Así lo que queda fuera de la barra (pestaña activa desplazada fuera de vista) no se dibuja sobre la pantalla.
            clipRect(left = 0f, top = -12.dp.toPx(), right = size.width, bottom = size.height) {
                val cx = cxAnim.value
                val w = size.width
                val y0 = risePx
                val r = if (roundCorners) 28.dp.toPx() else 0f
                val margin = 40.dp.toPx()

                val base = Path().apply {
                    addRoundRect(RoundRect(Rect(0f, y0, w, y0 + barHeightPx), CornerRadius(r, r)))
                }

                val inRange = cx > -margin && cx < w + margin
                val bar: Path = when {
                    !inRange -> base
                    isBubble -> {
                        // Muesca cóncava donde se aloja la burbuja.
                        val nw = 46.dp.toPx()
                        val up = 4.dp.toPx()
                        val notch = Path().apply {
                            moveTo(cx - nw, y0 - up)
                            lineTo(cx - nw, y0)
                            cubicTo(cx - nw * 0.45f, y0, cx - nw * 0.55f, y0 + notchDepthPx, cx, y0 + notchDepthPx)
                            cubicTo(cx + nw * 0.55f, y0 + notchDepthPx, cx + nw * 0.45f, y0, cx + nw, y0)
                            lineTo(cx + nw, y0 - up)
                            close()
                        }
                        Path.combine(PathOperation.Difference, base, notch)
                    }
                    else -> {
                        // Joroba convexa sobre la pestaña activa. Sus flancos arrancan sobre el borde de la
                        // barra con la misma tangente (también en las esquinas redondeadas), así que no
                        // queda ninguna esquina cuadrada ni punta cuando la pestaña está en un extremo.
                        val bump = rise.toPx()
                        val bw = 50.dp.toPx()
                        val minX = 6.dp.toPx()
                        val minHalf = 8.dp.toPx()
                        // Cerca de un extremo la joroba se angosta IGUAL por los dos lados (simétrica),
                        // en vez de recortar solo el lado de afuera (eso la deformaba).
                        val half = minOf(bw, cx - minX, (w - cx) - minX)
                        val xl = cx - half
                        val xr = cx + half
                        fun drop(x: Float): Float {
                            if (r <= 0f) return 0f
                            val d = (r - x).coerceAtLeast(0f)
                            return r - sqrt((r * r - d * d).coerceAtLeast(1f))
                        }
                        fun slope(x: Float): Float {
                            if (r <= 0f) return 0f
                            val d = (r - x).coerceAtLeast(0f)
                            return d / sqrt((r * r - d * d).coerceAtLeast(1f))
                        }
                        if (half < minHalf) {
                            base
                        } else {
                            val yl = y0 + drop(xl)
                            val yr = y0 + drop(w - xr)
                            val kl = (cx - xl) * 0.4f
                            val kr = (xr - cx) * 0.4f
                            val inside = 24.dp.toPx()
                            val bumpPath = Path().apply {
                                moveTo(xl, yl + inside)
                                lineTo(xl, yl)
                                cubicTo(xl + kl, yl - slope(xl) * kl, cx - (cx - xl) * 0.5f, y0 - bump, cx, y0 - bump)
                                cubicTo(cx + (xr - cx) * 0.5f, y0 - bump, xr - kr, yr - slope(w - xr) * kr, xr, yr)
                                lineTo(xr, yr + inside)
                                close()
                            }
                            Path.combine(PathOperation.Union, base, bumpPath)
                        }
                    }
                }

                // Vidrio: tinte translúcido + reflejo superior + borde fino.
                val tint = colors.surfaceContainer.copy(alpha = if (hasBackgroundImage) 0.42f else 0.78f)
                drawPath(bar, tint)
                drawPath(
                    bar,
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = if (isDark) 0.16f else 0.55f),
                            Color.White.copy(alpha = if (isDark) 0.04f else 0.12f)
                        )
                    )
                )
                val edge = if (isDark) Color.White else colors.onSurface
                drawPath(
                    bar,
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = if (isDark) 0.50f else 0.90f),
                            edge.copy(alpha = 0.10f)
                        )
                    ),
                    style = Stroke(width = 1.dp.toPx())
                )

                if (inRange && !isBubble) {
                    // Borde turquesa brillante que se desvanece lejos de la pestaña activa.
                    val glow = Brush.horizontalGradient(
                        listOf(Color.Transparent, primary, Color.Transparent),
                        startX = cx - 90.dp.toPx(),
                        endX = cx + 90.dp.toPx()
                    )
                    drawPath(bar, glow, alpha = 0.28f, style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round))
                    drawPath(bar, glow, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
                }

                if (inRange && isBubble) {
                    val radius = bubbleRadiusPx * bubbleScale.value
                    val center = Offset(cx, bubbleCenterY)
                    drawCircle(primary.copy(alpha = 0.92f), radius, center)
                    drawCircle(
                        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.35f), Color.Transparent)),
                        radius,
                        center
                    )
                    drawCircle(Color.White.copy(alpha = 0.5f), radius, center, style = Stroke(width = 1.dp.toPx()))
                }
            }
        }

        LazyRow(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = rise)
                .height(barHeight)
        ) {
            itemsIndexed(items) { index, screen ->
                val selected = index == selectedIndex
                val lift by animateDpAsState(if (selected && !isBubble) (-5).dp else 0.dp, label = "navGlassLift")
                val tint by animateColorAsState(
                    if (selected) primary else colors.onSurfaceVariant,
                    label = "navGlassTint"
                )
                val label = stringResource(screen.labelRes)
                Box(
                    modifier = Modifier
                        .width(itemWidth)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onItemClick(index) },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.offset(y = lift)
                    ) {
                        Icon(
                            imageVector = screen.icon,
                            contentDescription = label,
                            // En el estilo burbuja el ícono activo lo dibuja la burbuja.
                            tint = if (isBubble && selected) Color.Transparent else tint,
                            modifier = Modifier.size(if (selected && !isBubble) 26.dp else 24.dp)
                        )
                        if (!isBubble) {
                            Text(
                                text = label,
                                fontSize = 10.sp,
                                color = tint,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        if (isBubble) {
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (cxAnim.value - bubbleRadiusPx).roundToInt(),
                            (bubbleCenterY - bubbleRadiusPx).roundToInt()
                        )
                    }
                    .size(48.dp)
                    .graphicsLayer {
                        scaleX = bubbleScale.value
                        scaleY = bubbleScale.value
                        // El ícono se desvanece al salir por los lados (ya no hay clipToBounds general).
                        alpha = (minOf(cxAnim.value, widthPx - cxAnim.value) / 12.dp.toPx()).coerceIn(0f, 1f)
                    },
                contentAlignment = Alignment.Center
            ) {
                Crossfade(targetState = selectedIndex, label = "navGlassBubbleIcon") { i ->
                    val screen = items.getOrNull(i)
                    if (screen != null) {
                        Icon(
                            imageVector = screen.icon,
                            contentDescription = stringResource(screen.labelRes),
                            tint = colors.onPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}