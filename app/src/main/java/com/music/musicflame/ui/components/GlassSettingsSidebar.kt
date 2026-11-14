package com.music.musicflame.ui.components

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.music.musicflame.R
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

// ─────────────────────────────────────────────────────────────────────────────
// Modelo de ítem del sidebar
// ─────────────────────────────────────────────────────────────────────────────

data class SidebarItem(
    val key: String,
    val labelRes: Int,
    val icon: ImageVector,
    val badge: Int = 0,
    val showDot: Boolean = false
)

/** Lista de secciones por defecto. Se mantiene sincronizada con los catKey de SettingsScreen. */
val defaultSidebarItems = listOf(
    SidebarItem("Apariencia",       R.string.section_appearance,  Icons.Filled.Palette),
    SidebarItem("Canciones",        R.string.section_songs,       Icons.Filled.MusicNote),
    SidebarItem("Lyrics",           R.string.section_lyrics,      Icons.Filled.Equalizer),
    SidebarItem("IA",               R.string.section_ai,          Icons.Filled.SmartToy),
    SidebarItem("Hogar de Shimuro", R.string.section_shimuro_home,Icons.Filled.AutoAwesome),
    SidebarItem("Cuenta",           R.string.section_account,     Icons.Filled.AccountCircle),
    SidebarItem("Especificaciones", R.string.section_specs,       Icons.Filled.Info)
)

// ─────────────────────────────────────────────────────────────────────────────
// Composable principal
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Panel flotante tipo "Glassy Sidebar V5":
 *  - Se ancla al borde DERECHO de la pantalla.
 *  - Entra/sale con spring de derecha a izquierda.
 *  - Fondo de vidrio esmerilado (blur en Android 12+, translúcido en menores).
 *  - Botón de cerrar con muesca circular en el borde superior izquierdo.
 *  - Footer con tarjeta de cuenta de Google.
 *
 * @param visible        Controla la visibilidad del panel.
 * @param onDismiss      Se llama al cerrar (toque fuera, botón o swipe derecha).
 * @param onNavigate     Se llama con el [SidebarItem.key] cuando el usuario toca una fila.
 * @param onOpenAccount  Se llama al tocar la tarjeta de cuenta.
 * @param userName       Nombre del usuario de Google (null = sin sesión).
 * @param userPhotoUrl   URL de la foto del usuario (null = usar inicial).
 * @param banner         Banner de perfil (Bitmap de ProfileBanner.load); fondo de la tarjeta de cuenta (null = fondo oscuro).
 * @param bottomMargin   Margen extra sobre la barra de navegación del sistema, para alinear con el borde inferior de la barra de la app.
 * @param items          Lista de opciones; por defecto [defaultSidebarItems].
 */
@Composable
fun GlassSettingsSidebar(
    visible: Boolean,
    onDismiss: () -> Unit,
    onNavigate: (String) -> Unit,
    onOpenAccount: () -> Unit,
    userName: String? = null,
    userPhotoUrl: String? = null,
    banner: Any? = null,
    bottomMargin: Dp = 14.dp,
    items: List<SidebarItem> = defaultSidebarItems,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val isDark = colors.surface.luminance() < 0.5f

    val glassAlpha = if (isDark) 0.16f else 0.14f
    val borderAlpha = 0.35f
    val scrimAlpha = 0.30f

    var activeIndex by remember { mutableIntStateOf(-1) }
    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    val SWIPE_THRESHOLD = 80f

    Box(modifier = modifier.fillMaxSize()) {
        // Scrim
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(220)),
            exit  = fadeOut(tween(220))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = scrimAlpha))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    )
            )
        }

        // Panel
        AnimatedVisibility(
            visible = visible,
            enter = slideInHorizontally(
                initialOffsetX = { it },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness    = Spring.StiffnessMediumLow
                )
            ) + fadeIn(tween(180)),
            exit = slideOutHorizontally(
                targetOffsetX = { it },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness    = Spring.StiffnessMediumLow
                )
            ) + fadeOut(tween(180)),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            GlassPanelContent(
                isDark          = isDark,
                glassAlpha      = glassAlpha,
                borderAlpha     = borderAlpha,
                items           = items,
                activeIndex     = activeIndex,
                userName        = userName,
                userPhotoUrl    = userPhotoUrl,
                banner          = banner,
                bottomMargin    = bottomMargin,
                dragOffsetX     = dragOffsetX,
                onItemClick     = { index, key ->
                    activeIndex = index
                    onNavigate(key)
                },
                onDragDelta     = { dx ->
                    dragOffsetX = (dragOffsetX + dx).coerceAtLeast(0f)
                },
                onDragEnd       = {
                    if (dragOffsetX > SWIPE_THRESHOLD) onDismiss()
                    dragOffsetX = 0f
                },
                onCloseClick    = onDismiss,
                onOpenAccount   = onOpenAccount
            )
        }

        LaunchedEffect(visible) {
            if (!visible) dragOffsetX = 0f
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Contenido interno
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun GlassPanelContent(
    isDark: Boolean,
    glassAlpha: Float,
    borderAlpha: Float,
    items: List<SidebarItem>,
    activeIndex: Int,
    userName: String?,
    userPhotoUrl: String?,
    banner: Any?,
    bottomMargin: Dp,
    dragOffsetX: Float,
    onItemClick: (Int, String) -> Unit,
    onDragDelta: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onCloseClick: () -> Unit,
    onOpenAccount: () -> Unit
) {
    val panelWidth        = 216.dp
    val cornerRadius      = 32.dp
    val closeButtonRadius = 20.dp
    val notchRadius       = 24.dp

    Box(
        modifier = Modifier
            .width(panelWidth)
            .fillMaxHeight()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(top = 8.dp, bottom = bottomMargin)
            .offset { IntOffset(dragOffsetX.roundToInt(), 0) }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd    = { onDragEnd() },
                    onDragCancel = { onDragEnd() },
                    onHorizontalDrag = { _, dx -> if (dx > 0f) onDragDelta(dx) }
                )
            }
    ) {
        // Fondo de vidrio
        GlassBackground(
            modifier          = Modifier.fillMaxSize(),
            cornerRadius      = cornerRadius,
            glassAlpha        = glassAlpha,
            borderAlpha       = borderAlpha,
            isDark            = isDark,
            notchRadius       = notchRadius
        )

        // Contenido
        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(Modifier.height(28.dp))
            Text(
                text  = stringResource(R.string.nav_settings),
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Medium,
                    color      = Color.White
                ),
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Spacer(Modifier.height(16.dp))

            LazyColumn(modifier = Modifier.weight(1f)) {
                itemsIndexed(items) { index, item ->
                    SidebarRow(
                        item     = item,
                        isActive = index == activeIndex,
                        delayMs  = index * 30L,
                        onClick  = { onItemClick(index, item.key) }
                    )
                }
                item { Spacer(Modifier.height(16.dp)) }
            }

            AccountCard(
                userName     = userName,
                userPhotoUrl = userPhotoUrl,
                banner       = banner,
                borderAlpha  = borderAlpha,
                onClick      = onOpenAccount,
                modifier     = Modifier.padding(horizontal = 10.dp, vertical = 10.dp)
            )
        }

        // Botón de cerrar (sale medio del borde izquierdo del panel)
        CloseButton(
            radius   = closeButtonRadius,
            isDark   = isDark,
            onClick  = onCloseClick,
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = -closeButtonRadius, y = 36.dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Fondo de vidrio
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun GlassBackground(
    modifier: Modifier,
    cornerRadius: Dp,
    glassAlpha: Float,
    borderAlpha: Float,
    isDark: Boolean,
    notchRadius: Dp
) {
    val density = LocalDensity.current

    Box(modifier = modifier
        .then(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                Modifier.blur(20.dp)
            else Modifier
        )
        .drawBehind {
            val w    = size.width
            val h    = size.height
            val crPx = with(density) { cornerRadius.toPx() }
            val nrPx = with(density) { notchRadius.toPx() }
            val notchY = with(density) { 56.dp.toPx() }

            val basePath = Path().apply {
                addRoundRect(
                    RoundRect(
                        left   = 0f, top = 0f, right = w, bottom = h,
                        topLeftCornerRadius     = CornerRadius(crPx, crPx),
                        bottomLeftCornerRadius  = CornerRadius(crPx, crPx),
                        topRightCornerRadius    = CornerRadius.Zero,
                        bottomRightCornerRadius = CornerRadius.Zero
                    )
                )
            }

            val notchPath = Path().apply {
                addOval(
                    androidx.compose.ui.geometry.Rect(
                        center = Offset(0f, notchY),
                        radius = nrPx
                    )
                )
            }

            val finalPath = Path.combine(PathOperation.Difference, basePath, notchPath)

            // Relleno translúcido
            drawPath(finalPath, Color.White.copy(alpha = glassAlpha))

            // Gradiente vertical tipo reflexión
            drawPath(
                finalPath,
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = if (isDark) 0.18f else 0.40f),
                        Color.White.copy(alpha = if (isDark) 0.04f else 0.10f)
                    )
                )
            )

            // Borde fino
            drawPath(
                finalPath,
                Color.White.copy(alpha = borderAlpha),
                style = Stroke(width = with(density) { 0.8.dp.toPx() })
            )
        }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Fila de opción
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SidebarRow(
    item: SidebarItem,
    isActive: Boolean,
    delayMs: Long,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme

    // Cascada de entrada
    var appeared by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        delay(delayMs)
        appeared = 1f
    }
    val alpha by animateFloatAsState(appeared, tween(200), label = "rowAlpha")

    Box(
        modifier = Modifier
            .graphicsLayer { this.alpha = alpha }
            .padding(horizontal = 10.dp, vertical = 2.dp)
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .then(
                if (isActive) Modifier.drawBehind {
                    drawRoundRect(
                        color        = Color.White.copy(alpha = 0.22f),
                        cornerRadius = CornerRadius(24.dp.toPx())
                    )
                    drawRoundRect(
                        color        = Color.White.copy(alpha = 0.60f),
                        cornerRadius = CornerRadius(24.dp.toPx()),
                        style        = Stroke(width = 1.dp.toPx())
                    )
                } else Modifier
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication        = null,
                onClick           = onClick
            )
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector        = item.icon,
                contentDescription = null,
                tint               = Color.White.copy(alpha = if (isActive) 1f else 0.75f),
                modifier           = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text  = stringResource(item.labelRes),
                color = Color.White.copy(alpha = if (isActive) 1f else 0.85f),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (item.badge > 0) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .background(Color(0xFFFF6600), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text  = item.badge.toString(),
                        color = Color.White,
                        fontSize   = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            if (item.showDot) {
                Box(
                    modifier = Modifier
                        .padding(start = 6.dp)
                        .size(6.dp)
                        .background(colors.primary, CircleShape)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Botón circular de cerrar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CloseButton(
    radius: Dp,
    isDark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(radius * 2)
            .background(Color.White.copy(alpha = if (isDark) 0.90f else 0.95f), CircleShape)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication        = null,
                onClick           = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector        = Icons.Filled.ChevronRight,
            contentDescription = stringResource(R.string.glass_sidebar_close),
            tint               = if (isDark) Color(0xFF1A1A1A) else Color(0xFF333333),
            modifier           = Modifier.size(20.dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Tarjeta de cuenta (footer)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AccountCard(
    userName: String?,
    userPhotoUrl: String?,
    banner: Any?,
    borderAlpha: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(20.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(84.dp)
            .clip(shape)
            .background(Color.Black.copy(alpha = 0.30f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication        = null,
                onClick           = onClick
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        // Banner de perfil como fondo (si hay uno)
        if (banner != null) {
            AsyncImage(
                model              = banner,
                contentDescription = null,
                contentScale       = ContentScale.Crop,
                modifier           = Modifier.matchParentSize()
            )
        }
        // Degradado oscuro para que el texto se lea sobre cualquier banner
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = if (banner == null) 0f else 0.15f),
                            Color.Black.copy(alpha = if (banner == null) 0f else 0.65f)
                        )
                    )
                )
        )

        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar circular
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.20f)),
                contentAlignment = Alignment.Center
            ) {
                if (userPhotoUrl != null) {
                    AsyncImage(
                        model              = userPhotoUrl,
                        contentDescription = null,
                        contentScale       = ContentScale.Crop,
                        modifier           = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        text       = userName?.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                        color      = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize   = 18.sp
                    )
                }
            }

            Spacer(Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text       = userName ?: stringResource(R.string.glass_sidebar_no_account),
                    color      = Color.White,
                    fontWeight = FontWeight.Medium,
                    fontSize   = 14.sp,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis
                )
                Text(
                    text     = stringResource(R.string.glass_sidebar_google_account),
                    color    = Color.White.copy(alpha = 0.75f),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Borde fino por encima de todo
        Box(
            modifier = Modifier
                .matchParentSize()
                .border(0.8.dp, Color.White.copy(alpha = borderAlpha), shape)
        )
    }
}