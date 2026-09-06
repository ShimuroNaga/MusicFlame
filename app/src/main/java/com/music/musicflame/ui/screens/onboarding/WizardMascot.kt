package com.music.musicflame.ui.screens.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import kotlinx.coroutines.launch

enum class MascotPose {
    NORMAL, POINTING, EXPLAINING, CELEBRATING
}

private data class MascotStepState(
    val restPose: MascotPose,
    val activePose: MascotPose,
    val message: String
)

private fun stateForStep(step: Int): MascotStepState = when (step) {
    1 -> MascotStepState(MascotPose.NORMAL, MascotPose.EXPLAINING, "¡Hola buenas,soy Shimuro tu asistente musical!, Primero necesito algunos permisos para poder funcionar bien.")
    2 -> MascotStepState(MascotPose.POINTING, MascotPose.POINTING, "Elige el estilo y los colores que más te gusten.")
    3 -> MascotStepState(MascotPose.EXPLAINING, MascotPose.EXPLAINING, "Configuremos cómo se ven tus canciones.")
    4 -> MascotStepState(MascotPose.NORMAL, MascotPose.NORMAL, "Si quieres, iniciá sesión para sincronizar tus cosas.")
    5 -> MascotStepState(MascotPose.POINTING, MascotPose.POINTING, "Así se van a ver las letras mientras escuchás música.")
    6 -> MascotStepState(MascotPose.CELEBRATING, MascotPose.CELEBRATING, "¡Listo! Ya terminamos aqui!.")
    else -> MascotStepState(MascotPose.NORMAL, MascotPose.NORMAL, "")
}


@Composable
fun WizardMascot(
    step: Int,
    modifier: Modifier = Modifier
) {
    val stepState = remember(step) { stateForStep(step) }
    var hasGreeted by remember(step) { mutableStateOf(stepState.restPose == stepState.activePose) }
    var bubbleVisible by remember(step) { mutableStateOf(false) }
    val rotation = remember(step) { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    val displayPose = if (hasGreeted) stepState.activePose else stepState.restPose
    val drawableRes = when (displayPose) {
        MascotPose.NORMAL -> R.drawable.mascot_normal
        MascotPose.POINTING -> R.drawable.mascot_pointing
        MascotPose.EXPLAINING -> R.drawable.mascot_explaining
        MascotPose.CELEBRATING -> R.drawable.mascot_celebrating
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

    Box(modifier = modifier) {
        AnimatedContent(
            targetState = drawableRes,
            transitionSpec = {
                (scaleIn(
                    initialScale = 0.7f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                ) + fadeIn(tween(150))) togetherWith
                    (scaleOut(targetScale = 0.7f) + fadeOut(tween(100)))
            },
            label = "mascot_pose_change"
        ) { res ->
            Image(
                painter = painterResource(id = res),
                contentDescription = "Mascota, tocar para más información",
                modifier = Modifier
                    .size(52.dp)
                    .graphicsLayer {
                        scaleX = breatheScale
                        scaleY = breatheScale
                        rotationZ = rotation.value
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        if (!hasGreeted) {
                            scope.launch {
                                rotation.animateTo(
                                    targetValue = 0f,
                                    animationSpec = keyframes {
                                        durationMillis = 500
                                        0f at 0
                                        -14f at 100
                                        14f at 200
                                        -10f at 320
                                        8f at 420
                                        0f at 500
                                    }
                                )
                                hasGreeted = true
                                bubbleVisible = true
                            }
                        } else {
                            bubbleVisible = !bubbleVisible
                        }
                    }
            )
        }

        if (bubbleVisible && stepState.message.isNotBlank()) {
            val offsetY = with(density) { (-118).dp.roundToPx() }
            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(0, offsetY),
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
                        text = stepState.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
