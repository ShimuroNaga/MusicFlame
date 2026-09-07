package com.music.musicflame.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.musicflame.R
import java.time.LocalTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Card única "Hogar de Shimuro". Vive en su propia sección de SettingsScreen
 * (se abre igual que "Cuenta", "Apariencia", etc. — no está fija en el listado
 * principal, hay que tocar la entrada "Hogar de Shimuro" para entrar).
 *
 * Assets reales en res/drawable/ (7 en total):
 * - mascot_room_day        -> cuarto de día, Shimuro SENTADO, boca cerrada (estado inicial)
 * - mascot_room_day_alone  -> el mismo cuarto SIN personaje (silla vacía) — FONDO constante
 *                             detrás del personaje una vez que se para
 * - mascot_day             -> sticker parado, brazos cruzados, boca cerrada
 * - mascot_smile_day       -> sticker parado, sonriendo con dientes
 * - mascot_talk_day        -> sticker parado, boca abierta hablando
 * - mascot_night           -> retrato cercano de noche, boca cerrada
 * - mascot_talk_night      -> retrato cercano de noche, boca abierta
 *
 * DÍA: al primer toque se dispara una secuencia única de "se levanta de la silla":
 *   sentado (foto plana) -> silla sola (foto plana) ->
 *   -> de pie/sonríe/habla (sticker compuesto SOBRE el cuarto vacío de fondo)
 * Los toques siguientes (ya de pie) solo alternan calma/hablando, con el cuarto
 * vacío siempre de fondo.
 *
 * NOCHE: no hay secuencia, es directo crossfade cerrado/abierto (mascot_night / mascot_talk_night).
 */

private enum class ShimuroTimeOfDay { DAY, NIGHT }

private enum class DayVisual { SITTING, ALONE, STANDING_CALM, STANDING_SMILE, STANDING_TALK }

/** Rango de día: 6:00 a 18:59. Ajustable acá si se quiere otro corte. */
private fun currentShimuroTimeOfDay(): ShimuroTimeOfDay {
    val hour = LocalTime.now().hour
    return if (hour in 6..18) ShimuroTimeOfDay.DAY else ShimuroTimeOfDay.NIGHT
}

/** Sticker (fondo transparente/blanco) que va compuesto ENCIMA del cuarto vacío, una vez de pie. */
private fun standingStickerFor(visual: DayVisual): Int? = when (visual) {
    DayVisual.STANDING_CALM -> R.drawable.mascot_day
    DayVisual.STANDING_SMILE -> R.drawable.mascot_smile_day
    DayVisual.STANDING_TALK -> R.drawable.mascot_talk_day
    else -> null
}

private val shimuroPhrases = listOf(
    "¿Qué querés escuchar hoy?",
    "La música suena mejor con buena compañía.",
    "¿Ya probaste el modo Arcoíris del ecualizador?",
    "Este es mi rincón, hacé como en tu casa.",
    "¿Seguimos armando la biblioteca?"
)

private data class ShimuroFaq(val question: String, val answer: String)

// Respuestas en la voz de Shimuro, en base a lo que contó el desarrollador.
// Para agregar/editar una pregunta, solo se toca esta lista.
private val shimuroFaq = listOf(
    ShimuroFaq(
        "¿Para qué sirve MusicFlame?",
        "Más que solo reproducir canciones, esta app es el proyecto personal de mi creador: " +
                "la usa para \"entrenarse\", aprender, y analizar cómo se le ocurren y cómo desarrolla " +
                "sus ideas. Literal, yo soy parte de ese experimento."
    ),
    ShimuroFaq(
        "¿Cómo se hizo la app?",
        "Se armó con ayuda de IA, aprendiendo sobre la marcha, y fijándose en cosas que le " +
                "llamaron la atención de otros reproductores de música."
    ),
    ShimuroFaq(
        "¿Qué lo inspiró a crearla?",
        "El tiempo libre, y las ganas de siempre mejorar y hacer algo propio y único desde cero. " +
                "Nada más ni nada menos :3"
    ),
    ShimuroFaq(
        "¿Quién es mi creador?",
        "Es un universitario que recién está entrando a la universidad, de unos 18 años. Se " +
                "describe como hiperactivo (jeje), con muchísimas ideas dando vueltas... y cuando algo " +
                "le llama la atención, no lo suelta hasta exprimirle la última gota."
    ),
    ShimuroFaq(
        "¿Quién soy yo (Shimuro)?",
        "Soy el personaje que mi creador usa como su \"representante\" a partir de esta " +
                "actualización. Tengo una historia bastante larga detrás... ¡para otro día!"
    ),
    ShimuroFaq(
        "¿La app va a tener más funciones?",
        "Siempre que se le ocurran o se lo sugieran. De hecho pensó en algo con QR para " +
                "transferir mp3, pero lo descartó por otras ideas que le gustaron más."
    ),
    ShimuroFaq(
        "¿Puedo confiar en que mis canciones están seguras?",
        "Sí, tranquilo. Mi creador usa esta misma app todos los días para sus propias canciones, " +
                "y hasta ahora no ha visto ningún problema de corrupción de archivos ni nada por el estilo."
    ),
    ShimuroFaq(
        "¿Cómo lo contacto?",
        "Por Discord (buscalo en la sección \"Especificaciones\"), pronto también por Facebook, " +
                "y siempre por GitHub o por correo."
    )
)


@Composable
fun ShimuroHomeCard(modifier: Modifier = Modifier) {
    // Se recalcula cada vez que la card entra en composición (al abrir la sección),
    // no hay timer corriendo en 2do plano.
    val timeOfDay = remember { currentShimuroTimeOfDay() }

    // --- Estado de NOCHE (simple, cerrado/abierto) ---
    var nightTalking by remember { mutableStateOf(false) }

    // --- Estado de DÍA (secuencia de "se levanta" + toggle posterior) ---
    var dayVisual by remember { mutableStateOf(DayVisual.SITTING) }
    var hasStoodUp by remember { mutableStateOf(false) }
    var isAnimatingStandUp by remember { mutableStateOf(false) }

    var bubbleVisible by remember { mutableStateOf(false) }
    val phrase = remember { shimuroPhrases.random() }

    val scope = rememberCoroutineScope()
    val popScale = remember { Animatable(1f) }

    fun onTap() {
        when (timeOfDay) {
            ShimuroTimeOfDay.NIGHT -> {
                nightTalking = !nightTalking
                bubbleVisible = nightTalking
            }
            ShimuroTimeOfDay.DAY -> {
                if (isAnimatingStandUp) return
                if (!hasStoodUp) {
                    // Secuencia única: se levanta de la silla, se pone de pie, sonríe y habla.
                    isAnimatingStandUp = true
                    bubbleVisible = false
                    scope.launch {
                        dayVisual = DayVisual.ALONE
                        delay(380)
                        dayVisual = DayVisual.STANDING_CALM
                        delay(380)
                        dayVisual = DayVisual.STANDING_SMILE
                        delay(320)
                        dayVisual = DayVisual.STANDING_TALK
                        bubbleVisible = true
                        hasStoodUp = true
                        isAnimatingStandUp = false
                    }
                } else {
                    // Ya de pie: solo alterna calma <-> hablando.
                    dayVisual = if (dayVisual == DayVisual.STANDING_TALK) DayVisual.STANDING_CALM else DayVisual.STANDING_TALK
                    bubbleVisible = (dayVisual == DayVisual.STANDING_TALK)
                }
                scope.launch {
                    popScale.animateTo(1.03f, animationSpec = tween(90))
                    popScale.animateTo(1f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                }
            }
        }
    }

    // Edge-to-edge: sin margen lateral en la card ni padding alrededor de la
    // imagen, para que el cuarto de Shimuro ocupe todo el ancho disponible.
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column {
            // Sin título propio acá: SettingsScreen ya muestra "Hogar de Shimuro"
            // como encabezado de la sección (sectionHeader), se veía duplicado.

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    // Volvió a 3:2 (casi idéntico a la proporción real de las
                    // fotos, 1541x1020 ≈ 1.51:1) para no recortar de más el
                    // cuarto al hacer Crop.
                    .aspectRatio(if (timeOfDay == ShimuroTimeOfDay.DAY) 3f / 2f else 16f / 9f)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .graphicsLayer {
                        scaleX = popScale.value
                        scaleY = popScale.value
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onTap() },
                contentAlignment = Alignment.BottomStart
            ) {
                when (timeOfDay) {
                    ShimuroTimeOfDay.DAY -> {
                        Crossfade(
                            targetState = dayVisual,
                            animationSpec = tween(220),
                            label = "shimuro_home_day_visual"
                        ) { visual ->
                            when (visual) {
                                DayVisual.SITTING -> {
                                    // Foto plana: Shimuro ya sentado en el cuarto.
                                    Image(
                                        painter = painterResource(id = R.drawable.mascot_room_day),
                                        contentDescription = "Shimuro sentado, tocar para interactuar",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                DayVisual.ALONE -> {
                                    // Foto plana: la silla sola, paso intermedio de la animación.
                                    Image(
                                        painter = painterResource(id = R.drawable.mascot_room_day_alone),
                                        contentDescription = "Shimuro se levanta de la silla",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                else -> {
                                    // De pie: el cuarto vacío queda FIJO de fondo, y el sticker de
                                    // Shimuro (calma/sonríe/habla) se compone encima, parado, tocando
                                    // el borde superior e inferior del recuadro (ver nota de scale abajo).
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RectangleShape)
                                    ) {
                                        Image(
                                            painter = painterResource(id = R.drawable.mascot_room_day_alone),
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        val stickerRes = standingStickerFor(visual)
                                        if (stickerRes != null) {
                                            // Box propio con contentAlignment explícito: más confiable
                                            // para centrar que encadenar .align() con otros modifiers.
                                            Box(
                                                modifier = Modifier.fillMaxSize(),
                                                contentAlignment = Alignment.BottomCenter
                                            ) {
                                                Image(
                                                    painter = painterResource(id = stickerRes),
                                                    contentDescription = when (visual) {
                                                        DayVisual.STANDING_SMILE -> "Shimuro sonriendo"
                                                        DayVisual.STANDING_TALK -> "Shimuro hablando"
                                                        else -> "Shimuro de pie"
                                                    },
                                                    contentScale = ContentScale.Fit,
                                                    modifier = Modifier
                                                        .fillMaxHeight(1f)
                                                        .graphicsLayer {
                                                            // Los 3 PNG (mascot_day/smile_day/talk_day)
                                                            // traen ~3% de margen blanco horneado arriba
                                                            // y ~2.5% abajo. Con origen en el CENTRO (no
                                                            // abajo: eso empujaba el margen inferior hacia
                                                            // ADENTRO en vez de sacarlo), este zoom empuja
                                                            // ambos márgenes fuera del recuadro parejo, y
                                                            // el .clip de arriba los recorta.
                                                            scaleX = 1.07f
                                                            scaleY = 1.07f
                                                        }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    ShimuroTimeOfDay.NIGHT -> {
                        Crossfade(
                            targetState = nightTalking,
                            animationSpec = tween(220),
                            label = "shimuro_home_talking_night"
                        ) { isTalking ->
                            Image(
                                painter = painterResource(
                                    id = if (isTalking) R.drawable.mascot_talk_night else R.drawable.mascot_night
                                ),
                                contentDescription = if (isTalking) "Shimuro hablando" else "Shimuro, tocar para interactuar",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }

                if (bubbleVisible) {
                    Box(
                        modifier = Modifier
                            .padding(12.dp)
                            .background(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                                RoundedCornerShape(10.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            phrase,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // --- Preguntas frecuentes, en la voz de Shimuro ---
            var expandedFaqIndex by remember { mutableStateOf<Int?>(null) }

            Text(
                "Preguntas frecuentes",
                fontWeight = FontWeight.Black,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 16.dp, top = 16.dp, bottom = 4.dp)
            )

            shimuroFaq.forEachIndexed { index, faq ->
                val isExpanded = expandedFaqIndex == index
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            expandedFaqIndex = if (isExpanded) null else index
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            faq.question,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            imageVector = if (isExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                            contentDescription = if (isExpanded) "Contraer" else "Expandir",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    AnimatedVisibility(
                        visible = isExpanded,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Text(
                            faq.answer,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
                if (index != shimuroFaq.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                }
            }
            Spacer(Modifier.padding(bottom = 4.dp))
        }
    }
}