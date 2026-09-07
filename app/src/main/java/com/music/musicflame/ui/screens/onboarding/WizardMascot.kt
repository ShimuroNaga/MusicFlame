package com.music.musicflame.ui.screens.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

import com.music.musicflame.R

enum class MascotPose {
    NORMAL, POINTING, EXPLAINING, CELEBRATING
}

/** Tamaño del sprite de la mascota (antes 52.dp). Más grande a propósito
 *  para que asome un poco por encima de la bottom bar del wizard. */
private val MASCOT_SIZE = 108.dp
private val BUBBLE_GAP = 10.dp

/**
 * Cada dibujo (normal, señalando, explicando, festejando) viene ya
 * orientado hacia un lado en el propio PNG/vector, y no siempre es el
 * mismo lado entre un sprite y otro. Por eso el volteo se resuelve
 * sprite por sprite y no con un único signo global: así cada pose
 * termina mirando hacia el bubble/contenido sin importar cuál esté
 * activa.
 *
 * Confirmado probando en el celular:
 * - NORMAL: el dibujo mira nativamente hacia el lado equivocado -> se espeja.
 * - POINTING: el dibujo YA mira hacia el lado correcto -> no se espeja.
 * - EXPLAINING: el dibujo YA mira hacia el lado correcto -> no se espeja.
 * CELEBRATING quedó con el mismo valor que NORMAL como punto de partida (no
 * se pudo confirmar en captura); si al probarla se ve mirando para el lado
 * que no es, basta con cambiar `true` por `false` en su línea.
 */
private fun mascotIsMirrored(pose: MascotPose): Boolean = when (pose) {
    MascotPose.NORMAL -> true
    MascotPose.POINTING -> false
    MascotPose.EXPLAINING -> false
    MascotPose.CELEBRATING -> true
}

private data class MascotStepState(
    val restPose: MascotPose,
    val activePose: MascotPose,
    val message: String,
    /** Mensaje alterno usado solo cuando el paso tiene un disparador
     *  especial ajeno al toque (ej: vincular cuenta de Google). Si es
     *  null, se reusa `message`. */
    val celebrateMessage: String? = null
)

private fun stateForStep(step: Int): MascotStepState = when (step) {
    1 -> MascotStepState(
        restPose = MascotPose.NORMAL,
        activePose = MascotPose.EXPLAINING,
        message = "¡Hola, buenas! Soy Shimuro, tu asistente musical. Primero necesito algunos permisos para funcionar bien."
    )
    2 -> MascotStepState(
        restPose = MascotPose.NORMAL,
        activePose = MascotPose.POINTING,
        message = "Elige el estilo y los colores que más te gusten."
    )
    3 -> MascotStepState(
        restPose = MascotPose.NORMAL,
        activePose = MascotPose.EXPLAINING,
        message = "Configuremos cómo se ven tus canciones."
    )
    4 -> MascotStepState(
        restPose = MascotPose.NORMAL,
        activePose = MascotPose.NORMAL,
        message = "Si quieres, inicia sesión para sincronizar tus cosas.",
        celebrateMessage = "¡Genial, ya vinculaste tu cuenta!"
    )
    5 -> MascotStepState(
        restPose = MascotPose.NORMAL,
        activePose = MascotPose.POINTING,
        message = "Así se verán las letras, tanto dentro como fuera de la app."
    )
    6 -> MascotStepState(
        restPose = MascotPose.CELEBRATING,
        activePose = MascotPose.CELEBRATING,
        message = "¡Listo, ya terminamos! Si me necesitas, estaré en Ajustes descansando. ¡Bye!"
    )
    else -> MascotStepState(MascotPose.NORMAL, MascotPose.NORMAL, "")
}

@Composable
fun WizardMascot(
    step: Int,
    modifier: Modifier = Modifier,
    /** true cuando el disparador es ajeno al toque del usuario, por
     *  ejemplo cuando en el paso de cuenta el usuario SÍ vinculó su
     *  cuenta de Google. Mientras sea true la mascota festeja sin
     *  importar si fue tocada o no. */
    forceCelebrate: Boolean = false
) {
    val stepState = remember(step) { stateForStep(step) }
    var hasGreeted by remember(step) { mutableStateOf(stepState.restPose == stepState.activePose) }
    var bubbleVisible by remember(step) { mutableStateOf(false) }

    val basePose = if (hasGreeted) stepState.activePose else stepState.restPose
    val displayPose = if (forceCelebrate) MascotPose.CELEBRATING else basePose
    val displayMessage = if (forceCelebrate) {
        stepState.celebrateMessage ?: stepState.message
    } else {
        stepState.message
    }
    val drawableRes = when (displayPose) {
        MascotPose.NORMAL -> R.drawable.mascot_normal
        MascotPose.POINTING -> R.drawable.mascot_pointing
        MascotPose.EXPLAINING -> R.drawable.mascot_explaining
        MascotPose.CELEBRATING -> R.drawable.mascot_celebrating
    }
    // Se viajan juntos el dibujo y si va espejado o no: así, durante el
    // crossfade, tanto el sprite que sale como el que entra usan CADA
    // UNO su propio volteo y no el de la pose que está llegando.
    val spriteState = drawableRes to mascotIsMirrored(displayPose)

    // Cuando el paso ajeno al toque (ej: login exitoso) se activa,
    // solo se muestra el globo; el cambio de sprite lo resuelve
    // displayPose de arriba (via forceCelebrate) y el AnimatedContent
    // de abajo hace el cambio de imagen.
    LaunchedEffect(forceCelebrate) {
        if (forceCelebrate) {
            bubbleVisible = true
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "mascot_breathe")
    val breatheScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathe_scale"
    )

    Box(modifier = modifier.size(MASCOT_SIZE)) {
        AnimatedContent(
            targetState = spriteState,
            // Solo cambia de sprite a otro (crossfade simple), sin
            // ningún giro/volteo de por medio.
            transitionSpec = {
                fadeIn(tween(120)) togetherWith fadeOut(tween(120))
            },
            label = "mascot_pose_change"
        ) { (res, mirrored) ->
            Image(
                painter = painterResource(id = res),
                contentDescription = "Mascota, tocar para más información",
                modifier = Modifier
                    .size(MASCOT_SIZE)
                    .graphicsLayer {
                        // Espejado horizontal resuelto por pose (ver
                        // mascotIsMirrored arriba), porque cada sprite
                        // viene dibujado mirando hacia un lado distinto.
                        scaleX = if (mirrored) -breatheScale else breatheScale
                        scaleY = breatheScale
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        if (forceCelebrate) {
                            bubbleVisible = !bubbleVisible
                        } else if (bubbleVisible) {
                            // 2do toque (o cualquier toque con el bubble
                            // ya abierto): lo oculta y la mascota vuelve
                            // a su pose normal.
                            bubbleVisible = false
                            hasGreeted = false
                        } else {
                            // 1er toque (o toques siguientes con el
                            // bubble ya oculto): pasa a la pose activa
                            // y muestra el bubble de nuevo.
                            hasGreeted = true
                            bubbleVisible = true
                        }
                    }
            )
        }

        if (bubbleVisible && displayMessage.isNotBlank()) {
            // El globo va al ladito de la mascota (a su derecha),
            // ya no arriba de ella.
            val offsetXPx = with(LocalDensity.current) { (MASCOT_SIZE + BUBBLE_GAP).roundToPx() }
            Popup(
                alignment = Alignment.CenterStart,
                offset = IntOffset(offsetXPx, 0),
                properties = PopupProperties(focusable = false)
            ) {
                Box(
                    modifier = Modifier
                        .shadow(2.dp, RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .widthIn(max = 200.dp)
                ) {
                    Text(
                        text = displayMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
