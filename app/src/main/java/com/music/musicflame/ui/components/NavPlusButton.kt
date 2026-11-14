package com.music.musicflame.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.music.musicflame.R

/** Cuántas pestañas se ven a la vez en la píldora de la barra inferior (el resto se alcanza deslizando). */
const val NAV_VISIBLE_ITEMS = 4

private val NAV_PLUS_SIZE = 52.dp

/**
 * Botón "+" fijo a la derecha de la barra inferior. Por ahora es un "Próximamente":
 * al tocarlo solo avisa. Usa el color primario del tema (Material You cuando el color es "Adaptativo").
 * [style] solo sirve para alinearlo verticalmente con el cuerpo de la barra elegida.
 */
@Composable
fun NavPlusButton(style: NavBarStyle, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val message = stringResource(R.string.nav_plus_coming_soon)
    val bodyHeight: Dp = when (style) {
        NavBarStyle.CLASSIC -> 80.dp
        NavBarStyle.GLASS_BUBBLE -> 58.dp
        else -> 64.dp
    }
    // Centra el botón en el cuerpo de la barra (la clásica lleva 4dp extra abajo).
    val bottomPad = (bodyHeight - NAV_PLUS_SIZE) / 2 + if (style == NavBarStyle.CLASSIC) 4.dp else 0.dp

    Box(
        modifier = modifier
            .padding(start = 10.dp, bottom = bottomPad)
            .size(NAV_PLUS_SIZE)
            .alpha(0.9f)
            .shadow(8.dp, CircleShape, ambientColor = colors.primary, spotColor = colors.primary)
            .clip(CircleShape)
            .background(colors.primary)
            .border(1.dp, Color.White.copy(alpha = 0.30f), CircleShape)
            .clickable(role = Role.Button) {
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = message,
            tint = colors.onPrimary,
            modifier = Modifier.size(26.dp)
        )
    }
}
