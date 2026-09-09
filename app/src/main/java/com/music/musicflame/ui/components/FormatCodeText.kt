package com.music.musicflame.ui.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import com.music.musicflame.ui.theme.rainbowColorAt
import com.music.musicflame.ui.theme.rememberRainbowPhase
import kotlinx.coroutines.delay

/**
 * Códigos de formato estilo Minecraft Bedrock.
 * Uso: "§bMi Canción§r" -> "Mi Canción" en color cian.
 * Estos códigos SOLO afectan el "displayTitleFormatted" guardado en
 * SongCustomizationRepository, nunca el tag real del mp3 (RealTagWriter no
 * se toca).
 */
object FormatCodes {

    // Colores (0-9, a-f) - paleta estándar de Minecraft
    val COLOR_CODES: Map<Char, Color> = mapOf(
        '0' to Color(0xFF000000), // negro
        '1' to Color(0xFF0000AA), // azul oscuro
        '2' to Color(0xFF00AA00), // verde oscuro
        '3' to Color(0xFF00AAAA), // aqua oscuro
        '4' to Color(0xFFAA0000), // rojo oscuro
        '5' to Color(0xFFAA00AA), // púrpura oscuro
        '6' to Color(0xFFFFAA00), // dorado
        '7' to Color(0xFFAAAAAA), // gris
        '8' to Color(0xFF555555), // gris oscuro
        '9' to Color(0xFF5555FF), // azul
        'a' to Color(0xFF55FF55), // verde
        'b' to Color(0xFF55FFFF), // aqua / cian
        'c' to Color(0xFFFF5555), // rojo
        'd' to Color(0xFFFF55FF), // rosa / magenta
        'e' to Color(0xFFFFFF55), // amarillo
        'f' to Color(0xFFFFFFFF), // blanco
    )

    // Modificadores de estilo (no son colores)
    const val OBFUSCATED = 'k'   // texto glitcheando
    const val BOLD = 'l'
    const val STRIKETHROUGH = 'm'
    const val UNDERLINE = 'n'
    const val ITALIC = 'o'
    const val RESET = 'r'
    const val RAINBOW = 'x'       // NUEVO: reusa el modo Arcoíris ya existente (RainbowColor.kt)

    const val PREFIX = '§'

    /** Lista de ejemplos para mostrar en el desplegable de ayuda. */
    data class CodeExample(val code: String, val label: String, val previewColor: Color? = null)

    fun examples(): List<CodeExample> = buildList {
        COLOR_CODES.forEach { (char, color) ->
            add(CodeExample("$PREFIX$char", "Color", color))
        }
        add(CodeExample("$PREFIX$RAINBOW", "Arcoíris (mismo modo del ecualizador)"))
        add(CodeExample("$PREFIX$OBFUSCATED", "Obfuscado (glitch)"))
        add(CodeExample("$PREFIX$BOLD", "Negrita"))
        add(CodeExample("$PREFIX$ITALIC", "Cursiva"))
        add(CodeExample("$PREFIX$UNDERLINE", "Subrayado"))
        add(CodeExample("$PREFIX$STRIKETHROUGH", "Tachado"))
        add(CodeExample("$PREFIX$RESET", "Reset (quita formato)"))
    }
}

/** Un tramo de texto ya resuelto con su estilo. */
private data class FormatSegment(
    val text: String,
    val style: SpanStyle,
    val obfuscated: Boolean,
    val rainbow: Boolean,
)

private val obfuscatedPool = ('!'..'~').toList() // rango ASCII imprimible

/**
 * Parsea un string crudo con códigos § en una lista de segmentos con estilo.
 * No resuelve la animación de §k aquí; eso lo hace el Composable de abajo
 * en cada "tick" para que el texto cambie con el tiempo.
 */
private fun parseFormatCodes(raw: String): List<FormatSegment> {
    val segments = mutableListOf<FormatSegment>()
    var currentColor: Color? = null
    var bold = false
    var italic = false
    var underline = false
    var strikethrough = false
    var obfuscated = false
    var rainbow = false

    val sb = StringBuilder()

    fun flush() {
        if (sb.isNotEmpty()) {
            var decoration: TextDecoration? = null
            if (underline && strikethrough) decoration = TextDecoration.combine(
                listOf(TextDecoration.Underline, TextDecoration.LineThrough)
            )
            else if (underline) decoration = TextDecoration.Underline
            else if (strikethrough) decoration = TextDecoration.LineThrough

            segments.add(
                FormatSegment(
                    text = sb.toString(),
                    style = SpanStyle(
                        // si rainbow está activo, el color lo decide rainbowColorAt()
                        // por letra al renderizar, no el color plano de §0-§f
                        color = if (rainbow) Color.Unspecified else (currentColor ?: Color.Unspecified),
                        fontWeight = if (bold) FontWeight.Bold else null,
                        fontStyle = if (italic) FontStyle.Italic else null,
                        textDecoration = decoration,
                    ),
                    obfuscated = obfuscated,
                    rainbow = rainbow,
                )
            )
            sb.clear()
        }
    }

    var i = 0
    while (i < raw.length) {
        val c = raw[i]
        if (c == FormatCodes.PREFIX && i + 1 < raw.length) {
            val code = raw[i + 1].lowercaseChar()
            when {
                FormatCodes.COLOR_CODES.containsKey(code) -> {
                    flush()
                    currentColor = FormatCodes.COLOR_CODES[code]
                }
                code == FormatCodes.OBFUSCATED -> { flush(); obfuscated = true }
                code == FormatCodes.RAINBOW -> { flush(); rainbow = true }
                code == FormatCodes.BOLD -> { flush(); bold = true }
                code == FormatCodes.ITALIC -> { flush(); italic = true }
                code == FormatCodes.UNDERLINE -> { flush(); underline = true }
                code == FormatCodes.STRIKETHROUGH -> { flush(); strikethrough = true }
                code == FormatCodes.RESET -> {
                    flush()
                    currentColor = null; bold = false; italic = false
                    underline = false; strikethrough = false; obfuscated = false; rainbow = false
                }
                else -> { sb.append(c) } // código desconocido: se muestra literal
            }
            if (FormatCodes.COLOR_CODES.containsKey(code) ||
                code in listOf(
                    FormatCodes.OBFUSCATED, FormatCodes.RAINBOW, FormatCodes.BOLD, FormatCodes.ITALIC,
                    FormatCodes.UNDERLINE, FormatCodes.STRIKETHROUGH, FormatCodes.RESET
                )
            ) {
                i += 2
                continue
            }
        } else {
            sb.append(c)
        }
        i++
    }
    flush()
    return segments
}

/**
 * Muestra [rawTitle] interpretando los códigos §.
 * Si no quieres animación (ej. en una lista larga donde no vale la pena
 * el timer), pasa [animateObfuscated] = false y §k simplemente se ignora
 * (se muestra el texto tal cual, sin glitch). §x (arcoíris) siempre se
 * anima porque reusa directamente rememberRainbowPhase(), que ya está
 * pensado para correr barato (18 updates/seg).
 */
@Composable
fun FormatCodeText(
    rawTitle: String,
    style: TextStyle,
    animateObfuscated: Boolean = true,
    tickMillis: Long = 80L,
) {
    var tick by remember { mutableIntStateOf(0) }

    val hasObfuscated = remember(rawTitle) { rawTitle.contains("${FormatCodes.PREFIX}${FormatCodes.OBFUSCATED}") }

    if (animateObfuscated && hasObfuscated) {
        LaunchedEffect(rawTitle) {
            while (true) {
                delay(tickMillis)
                tick++
            }
        }
    }

    // misma fase global que usan ecualizador/Now Playing/Lyrics cuando están
    // en modo Arcoíris, así §x se ve sincronizado con el resto de la UI
    val rainbowPhase by rememberRainbowPhase()

    val annotated = remember(rawTitle, tick, rainbowPhase) {
        buildAnnotatedString(rawTitle, animate = animateObfuscated, seed = tick, rainbowPhaseDeg = rainbowPhase)
    }

    androidx.compose.material3.Text(text = annotated, style = style)
}

private fun buildAnnotatedString(raw: String, animate: Boolean, seed: Int, rainbowPhaseDeg: Float = 0f): AnnotatedString {
    val segments = parseFormatCodes(raw)
    return androidx.compose.ui.text.buildAnnotatedString {
        segments.forEach { seg ->
            when {
                seg.rainbow -> {
                    // cada letra toma un punto distinto del espectro (spreadFraction
                    // por índice), igual que hace el ecualizador con sus barras
                    seg.text.forEachIndexed { idx, ch ->
                        val color = rainbowColorAt(
                            phaseDeg = rainbowPhaseDeg,
                            spreadFraction = (idx % 12) / 12f,
                        )
                        withStyle(seg.style.copy(color = color)) {
                            val displayChar = if (seg.obfuscated && animate && !ch.isWhitespace()) {
                                obfuscatedPool[(ch.code + seed * 31 + idx * 17) % obfuscatedPool.size]
                            } else ch
                            append(displayChar)
                        }
                    }
                }
                seg.obfuscated && animate -> {
                    withStyle(seg.style) {
                        // cada char se sustituye por uno random del pool;
                        // el "seed" (tick) hace que cambie con el tiempo
                        val scrambled = seg.text.mapIndexed { idx, ch ->
                            if (ch.isWhitespace()) ch
                            else obfuscatedPool[(ch.code + seed * 31 + idx * 17) % obfuscatedPool.size]
                        }.joinToString("")
                        append(scrambled)
                    }
                }
                else -> withStyle(seg.style) { append(seg.text) }
            }
        }
    }
}

/** Versión sin animación, útil si solo necesitas el AnnotatedString (ej. para un Preview o log). §x se renderiza como una foto fija (fase 0) ya que no hay composable corriendo detrás. */
fun formatCodesToPlainAnnotated(raw: String): AnnotatedString = buildAnnotatedString(raw, animate = false, seed = 0, rainbowPhaseDeg = 0f)

/**
 * Devuelve el título a mostrar para una canción: el "displayTitleFormatted"
 * guardado en SongCustomizationRepository (con códigos §) si existe, o el
 * título real como fallback.
 */
fun resolveDisplayTitle(
    songId: Long,
    realTitle: String,
    customizationRepo: com.music.musicflame.data.SongCustomizationRepository,
): String = customizationRepo.getDisplayTitleFormatted(songId) ?: realTitle
