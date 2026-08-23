package com.music.musicflame.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue

@Composable
fun NowPlayingIndicator(
    modifier: Modifier = Modifier,
    // FIX: antes usaba siempre el color primario del tema (que puede ser cualquier
    // tono con Material You dinámico y a veces se pierde contra el fondo). Ahora,
    // por defecto, es blanco o negro puro según la luminancia REAL del fondo activo:
    // fondo oscuro -> barras blancas, fondo claro -> barras negras. Sigue siendo
    // sobreescribible si alguna pantalla necesita otro color a propósito.
    color: Color = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color.White else Color.Black,
    barWidth: Dp = 3.dp,
    height: Dp = 14.dp,
    spacing: Dp = 2.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "nowPlayingBars")

    val bar1 by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(420, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar1"
    )
    val bar2 by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar2"
    )
    val bar3 by infiniteTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(360, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar3"
    )

    Row(
        modifier = modifier
            .height(height)
            .semantics { contentDescription = "Sonando ahora" },
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(spacing)
    ) {
        listOf(bar1, bar2, bar3).forEach { level ->
            Box(
                modifier = Modifier
                    .width(barWidth)
                    .fillMaxHeight(level.coerceIn(0.15f, 1f))
                    .background(color, RoundedCornerShape(1.dp))
            )
        }
    }
}