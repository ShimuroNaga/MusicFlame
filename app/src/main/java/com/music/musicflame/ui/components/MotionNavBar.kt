package com.music.musicflame.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.musicflame.navigation.Screen
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Barra inferior con animaciones de movimiento (Ajustes > Apariencia). Estilos:
 *  - SLIDING_PILL: una píldora se desliza hasta la pestaña tocada y se estira mientras viaja.
 *  - LIQUID_BULGE: la barra se abulta como líquido bajo la pestaña activa y el bulto fluye a la nueva.
 *  - ICON_FILL: el ícono activo se rellena desde el centro, da un "pop" y deja un puntito.
 *
 * Igual que [GlassNavBar]: se ven 5 pestañas a la vez (NAV_VISIBLE_ITEMS), siempre con su nombre, y la lista se desliza
 * con [listState] (el mismo de MainActivity, que sigue al pager). Todos los colores salen de
 * MaterialTheme.colorScheme, o sea del tema de la app (Material You cuando el color es "Adaptativo").
 */
@Composable
fun MotionNavBar(
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
    val isPill = style == NavBarStyle.SLIDING_PILL
    val isBulge = style == NavBarStyle.LIQUID_BULGE
    val isFill = style == NavBarStyle.ICON_FILL

    val barHeight = 64.dp
    val rise = if (isBulge) 22.dp else 0.dp

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(barHeight + rise)
    ) {
        val itemWidth = maxWidth / NAV_VISIBLE_ITEMS.toFloat()
        val widthPx = with(density) { maxWidth.toPx() }
        val itemWidthPx = widthPx / NAV_VISIBLE_ITEMS
        val risePx = with(density) { rise.toPx() }
        val barHeightPx = with(density) { barHeight.toPx() }
        val pillHalf = itemWidthPx / 2f - with(density) { 5.dp.toPx() }

        // Centro (px) de la pestaña activa dentro de la barra, aunque la lista esté desplazada.
        val targetCx by remember(itemWidthPx, selectedIndex) {
            derivedStateOf {
                (selectedIndex - listState.firstVisibleItemIndex) * itemWidthPx -
                        listState.firstVisibleItemScrollOffset + itemWidthPx / 2f
            }
        }

        // Dirección del movimiento: el borde que va adelante corre rápido y el de atrás se queda
        // rezagado, así la píldora se estira al viajar.
        var prevTarget by remember { mutableFloatStateOf(targetCx) }
        var movingRight by remember { mutableStateOf(true) }
        LaunchedEffect(targetCx) {
            if (targetCx != prevTarget) {
                movingRight = targetCx > prevTarget
                prevTarget = targetCx
            }
        }
        val leftEdge by animateFloatAsState(
            targetValue = targetCx - pillHalf,
            animationSpec = spring(dampingRatio = 0.75f, stiffness = if (movingRight) 180f else 650f),
            label = "motionPillLeft"
        )
        val rightEdge by animateFloatAsState(
            targetValue = targetCx + pillHalf,
            animationSpec = spring(dampingRatio = 0.75f, stiffness = if (movingRight) 650f else 180f),
            label = "motionPillRight"
        )
        // Centro del bulto líquido (resorte suave: al viajar queda un poco atrás de la pestaña).
        val bulgeCx by animateFloatAsState(
            targetValue = targetCx,
            animationSpec = spring(dampingRatio = 0.6f, stiffness = 260f),
            label = "motionBulgeCx"
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            // Recorte a los lados: lo que sale de la barra (pestaña activa fuera de vista) no se dibuja.
            clipRect(left = 0f, top = -12.dp.toPx(), right = size.width, bottom = size.height) {
                val w = size.width
                val y0 = risePx
                val r = if (roundCorners) 28.dp.toPx() else 0f
                val margin = 40.dp.toPx()

                val base = Path().apply {
                    addRoundRect(RoundRect(Rect(0f, y0, w, y0 + barHeightPx), CornerRadius(r, r)))
                }

                var bar: Path = base
                if (isBulge) {
                    val cx = bulgeCx
                    if (cx > -margin && cx < w + margin) {
                        // v = lo que le falta al bulto para llegar a su pestaña; define cuánto se estira.
                        val v = (targetCx - cx).coerceIn(-itemWidthPx, itemWidthPx)
                        val stretch = abs(v) / itemWidthPx
                        val bump = risePx * (1f - 0.3f * stretch)
                        val bw = 54.dp.toPx() * (1f + 0.45f * stretch)
                        val minX = 6.dp.toPx()
                        val minHalf = 8.dp.toPx()
                        val half = minOf(bw, cx - minX, (w - cx) - minX)
                        if (half >= minHalf) {
                            val xl = cx - half
                            val xr = cx + half
                            // La cima se adelanta hacia donde va el líquido.
                            val peak = (cx + v * 0.22f).coerceIn(xl + minHalf * 0.5f, xr - minHalf * 0.5f)
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
                            val yl = y0 + drop(xl)
                            val yr = y0 + drop(w - xr)
                            val kl = (peak - xl) * 0.4f
                            val kr = (xr - peak) * 0.4f
                            val inside = 24.dp.toPx()
                            val bumpPath = Path().apply {
                                moveTo(xl, yl + inside)
                                lineTo(xl, yl)
                                cubicTo(xl + kl, yl - slope(xl) * kl, peak - (peak - xl) * 0.5f, y0 - bump, peak, y0 - bump)
                                cubicTo(peak + (xr - peak) * 0.5f, y0 - bump, xr - kr, yr - slope(w - xr) * kr, xr, yr)
                                lineTo(xr, yr + inside)
                                close()
                            }
                            bar = Path.combine(PathOperation.Union, base, bumpPath)
                        }
                    }
                }

                if (isBulge) {
                    // Barra "líquida": degradado de los contenedores del tema (Material You).
                    drawPath(
                        bar,
                        Brush.horizontalGradient(listOf(colors.primaryContainer, colors.tertiaryContainer)),
                        alpha = if (hasBackgroundImage) 0.88f else 1f
                    )
                    drawPath(
                        bar,
                        Brush.verticalGradient(
                            listOf(Color.White.copy(alpha = if (isDark) 0.14f else 0.45f), Color.Transparent)
                        )
                    )
                    drawPath(
                        bar,
                        Brush.verticalGradient(
                            listOf(Color.White.copy(alpha = if (isDark) 0.40f else 0.80f), Color.White.copy(alpha = 0.06f))
                        ),
                        style = Stroke(width = 1.dp.toPx())
                    )
                } else {
                    // Vidrio: tinte translúcido + reflejo superior + borde fino (igual que GlassNavBar).
                    drawPath(bar, colors.surfaceContainer.copy(alpha = if (hasBackgroundImage) 0.42f else 0.78f))
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
                            listOf(Color.White.copy(alpha = if (isDark) 0.50f else 0.90f), edge.copy(alpha = 0.10f))
                        ),
                        style = Stroke(width = 1.dp.toPx())
                    )
                }

                if (isPill) {
                    val l = leftEdge
                    val pw = (rightEdge - leftEdge).coerceAtLeast(pillHalf)
                    if (l + pw > 0f && l < w) {
                        val top = y0 + 8.dp.toPx()
                        val ph = barHeightPx - 16.dp.toPx()
                        val pad = 3.dp.toPx()
                        val brush = Brush.horizontalGradient(
                            listOf(colors.primary, colors.tertiary),
                            startX = l,
                            endX = l + pw
                        )
                        // Resplandor suave, píldora y reflejo.
                        drawRoundRect(
                            brush = brush,
                            topLeft = Offset(l - pad, top - pad),
                            size = Size(pw + pad * 2, ph + pad * 2),
                            cornerRadius = CornerRadius(ph / 2f + pad),
                            alpha = 0.28f
                        )
                        drawRoundRect(
                            brush = brush,
                            topLeft = Offset(l, top),
                            size = Size(pw, ph),
                            cornerRadius = CornerRadius(ph / 2f)
                        )
                        drawRoundRect(
                            brush = Brush.verticalGradient(
                                listOf(Color.White.copy(alpha = 0.28f), Color.Transparent),
                                startY = top,
                                endY = top + ph
                            ),
                            topLeft = Offset(l, top),
                            size = Size(pw, ph),
                            cornerRadius = CornerRadius(ph / 2f)
                        )
                    }
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
            itemsIndexed(items, key = { _, screen -> screen.route }, contentType = { _, _ -> "tab" }) { index, screen ->
                val selected = index == selectedIndex
                val label = stringResource(screen.labelRes)

                val contentColor by animateColorAsState(
                    when {
                        isPill -> if (selected) colors.onPrimary else colors.onSurfaceVariant
                        isBulge -> if (selected) colors.onPrimaryContainer else colors.onPrimaryContainer.copy(alpha = 0.68f)
                        else -> if (selected) colors.primary else colors.onSurfaceVariant
                    },
                    label = "motionNavTint"
                )
                // Bulto líquido: el ícono activo sube al bulto.
                val lift by animateDpAsState(
                    targetValue = if (isBulge && selected) (-17).dp else 0.dp,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                    label = "motionNavLift"
                )
                // Icon Fill: progreso del relleno, "pop" del ícono y largo del puntito.
                val fill by animateFloatAsState(
                    targetValue = if (isFill && selected) 1f else 0f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
                    label = "motionNavFill"
                )
                val pop by animateFloatAsState(
                    targetValue = if (isFill && selected) 1.12f else 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                    label = "motionNavPop"
                )
                val dotWidth by animateDpAsState(
                    targetValue = if (isFill && selected) 16.dp else 0.dp,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                    label = "motionNavDot"
                )

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
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (isFill) {
                            FillIcon(
                                icon = screen.icon,
                                progress = fill,
                                scale = pop,
                                baseTint = colors.onSurfaceVariant,
                                colorA = colors.primary,
                                colorB = colors.tertiary
                            )
                        } else {
                            Icon(
                                imageVector = screen.icon,
                                contentDescription = label,
                                tint = contentColor,
                                modifier = Modifier
                                    .offset(y = lift)
                                    .size(if (isBulge && selected) 26.dp else 24.dp)
                            )
                        }
                        Text(
                            text = label,
                            fontSize = 10.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = contentColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (isFill) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 5.dp)
                                .size(width = dotWidth, height = 3.dp)
                                .clip(CircleShape)
                                .background(Brush.horizontalGradient(listOf(colors.primary, colors.tertiary)))
                        )
                    }
                }
            }
        }
    }
}

/**
 * Ícono que se rellena con un degradado desde su centro. Los íconos de la barra son todos
 * "Filled", así que el ícono base va atenuado y el degradado crece encima con un círculo.
 */
@Composable
private fun FillIcon(
    icon: ImageVector,
    progress: Float,
    scale: Float,
    baseTint: Color,
    colorA: Color,
    colorB: Color
) {
    val brush = remember(colorA, colorB) { Brush.linearGradient(listOf(colorA, colorB)) }
    Box(
        modifier = Modifier
            .size(24.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = baseTint.copy(alpha = 0.8f),
            modifier = Modifier.fillMaxSize()
        )
        if (progress > 0.01f) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        val radius = size.minDimension * 0.75f * progress
                        val circle = Path().apply { addOval(Rect(center, radius)) }
                        clipPath(circle) {
                            this@drawWithContent.drawContent()
                            drawRect(brush = brush, blendMode = BlendMode.SrcIn)
                        }
                    }
            )
        }
    }
}
