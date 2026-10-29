package com.music.musicflame.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Colores compartidos de Ajustes. SettingsScreen los calcula una vez y se los
 * pasa a cada sección que vive en su propio archivo (Settings*.kt).
 *
 * @param groupColor  color de fondo de las tarjetas de grupo (filas de una sección).
 * @param translucent true cuando hay fondo/GIF propio: las tarjetas se vuelven un poco
 *                    translúcidas pero siguen siendo legibles sobre la imagen.
 */
@Immutable
class SettingsStyle(
    val highEmphasis: Color,
    val mediumEmphasis: Color,
    val trailingColor: Color,
    val listItemColors: ListItemColors,
    val dividerColor: Color,
    val groupColor: Color,
    val translucent: Boolean
)

/** Encabezado de sección: pequeño, en mayúsculas y con el color primario del tema. */
@Composable
internal fun SettingsSectionHeader(style: SettingsStyle, text: String) {
    Text(
        text = text.uppercase(),
        modifier = Modifier.padding(start = 32.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.8.sp,
        color = style.trailingColor
    )
}

// ---------------------------------------------------------------------------------------------
//  Filas agrupadas en tarjeta
// ---------------------------------------------------------------------------------------------

/** Posición de una fila dentro de su grupo (define qué esquinas se redondean). */
internal enum class SettingsRowPos { Only, First, Middle, Last }

/**
 * Una fila de un grupo. Cada fila vive en su propio `item {}` del LazyColumn (se mantiene la
 * carga perezosa), pero las filas consecutivas se ven como UNA sola tarjeta redondeada.
 */
@Composable
internal fun SettingsRow(
    style: SettingsStyle,
    pos: SettingsRowPos,
    content: @Composable ColumnScope.() -> Unit
) {
    val r = 20.dp
    val shape = when (pos) {
        SettingsRowPos.Only -> RoundedCornerShape(r)
        SettingsRowPos.First -> RoundedCornerShape(topStart = r, topEnd = r)
        SettingsRowPos.Middle -> RectangleShape
        SettingsRowPos.Last -> RoundedCornerShape(bottomStart = r, bottomEnd = r)
    }
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = shape,
        color = style.groupColor
    ) { Column(content = content) }
}

/** Divisor con sangría para usar entre filas de un mismo grupo. */
@Composable
internal fun SettingsDivider(style: SettingsStyle) {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = style.dividerColor
    )
}

// ---------------------------------------------------------------------------------------------
//  Colores distintivos por categoría (siguen Material You porque salen del colorScheme)
// ---------------------------------------------------------------------------------------------

@Immutable
class SettingsAccent(val container: Color, val onContainer: Color)

/**
 * Cada categoría tiene su propio color tonal, como la tarjeta de Lyrics. Se construyen a partir de
 * los contenedores primary / secondary / tertiary / error del tema (y mezclas entre ellos), así que
 * con Material You se adaptan solos al fondo de pantalla; en los temas fijos usan su paleta.
 */
@Composable
internal fun settingsAccentFor(key: String): SettingsAccent {
    val cs = MaterialTheme.colorScheme
    val p = SettingsAccent(cs.primaryContainer, cs.onPrimaryContainer)
    val s = SettingsAccent(cs.secondaryContainer, cs.onSecondaryContainer)
    val t = SettingsAccent(cs.tertiaryContainer, cs.onTertiaryContainer)
    val e = SettingsAccent(cs.errorContainer, cs.onErrorContainer)
    fun mix(a: SettingsAccent, b: SettingsAccent, f: Float) =
        SettingsAccent(lerp(a.container, b.container, f), lerp(a.onContainer, b.onContainer, f))
    return when (key) {
        "Apariencia" -> p
        "Lyrics" -> t
        "Hogar de Shimuro" -> mix(p, t, 0.5f)
        "Canciones" -> s
        "IA" -> mix(t, s, 0.5f)
        "Cuenta" -> mix(s, p, 0.5f)
        "Copia de seguridad" -> mix(p, t, 0.25f)
        "Pagos (opcional)" -> mix(e, t, 0.6f)
        "Especificaciones" -> mix(p, s, 0.3f)
        "Aviso de Uso" -> e
        "Licencias de terceros" -> mix(p, t, 0.75f)
        else -> p
    }
}

internal fun settingsSectionIcon(key: String): ImageVector = when (key) {
    "Apariencia" -> Icons.Filled.Palette
    "Lyrics" -> Icons.Filled.Subtitles
    "Hogar de Shimuro" -> Icons.Filled.Home
    "Canciones" -> Icons.Filled.LibraryMusic
    "IA" -> Icons.Filled.AutoAwesome
    "Cuenta" -> Icons.Filled.AccountCircle
    "Copia de seguridad" -> Icons.Filled.Backup
    "Pagos (opcional)" -> Icons.Filled.Favorite
    "Especificaciones" -> Icons.Filled.Info
    "Aviso de Uso" -> Icons.Filled.Warning
    "Licencias de terceros" -> Icons.Filled.Description
    else -> Icons.Filled.Info
}

/** Cuadrito con el ícono: círculo/rect suave del color "on" del contenedor, para dar profundidad. */
@Composable
internal fun SettingsIconBadge(icon: ImageVector, accent: SettingsAccent) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(accent.onContainer.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = accent.onContainer, modifier = Modifier.size(22.dp))
    }
}

/** Tarjeta de categoría de la pantalla principal de Ajustes, con su color distintivo. */
@Composable
internal fun SettingsCategoryCard(
    style: SettingsStyle,
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: SettingsAccent,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(20.dp),
        color = accent.container.copy(alpha = if (style.translucent) 0.92f else 1f)
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            SettingsIconBadge(icon, accent)
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = accent.onContainer)
                Text(subtitle, fontSize = 12.sp, color = accent.onContainer.copy(alpha = 0.75f))
            }
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = accent.onContainer.copy(alpha = 0.7f)
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
//  Previews: sirven para revisar que categorías vecinas se distingan en distintos esquemas.
//  (Android Studio → abrir este archivo → panel Split/Design.)
// ---------------------------------------------------------------------------------------------

@Composable
private fun SettingsAccentsPreviewBody() {
    val style = SettingsStyle(
        highEmphasis = MaterialTheme.colorScheme.onSurface,
        mediumEmphasis = MaterialTheme.colorScheme.onSurfaceVariant,
        trailingColor = MaterialTheme.colorScheme.primary,
        listItemColors = ListItemDefaults.colors(containerColor = Color.Transparent),
        dividerColor = MaterialTheme.colorScheme.outlineVariant,
        groupColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        translucent = false
    )
    val groups = listOf(
        "Personalización" to listOf("Apariencia", "Lyrics", "Hogar de Shimuro"),
        "Reproducción" to listOf("Canciones", "IA"),
        "Cuenta y datos" to listOf("Cuenta", "Copia de seguridad"),
        "Apoyo" to listOf("Pagos (opcional)"),
        "Acerca de" to listOf("Especificaciones", "Aviso de Uso", "Licencias de terceros")
    )
    Column(modifier = Modifier.background(MaterialTheme.colorScheme.background).padding(bottom = 16.dp)) {
        groups.forEach { (title, keys) ->
            SettingsSectionHeader(style, title)
            keys.forEach { key ->
                SettingsCategoryCard(
                    style = style,
                    title = key,
                    subtitle = "Subtítulo de ejemplo",
                    icon = settingsSectionIcon(key),
                    accent = settingsAccentFor(key),
                    onClick = {}
                )
            }
        }
    }
}

@Preview(name = "Claro (base)", widthDp = 380, heightDp = 1000)
@Composable
private fun SettingsAccentsLightPreview() =
    MaterialTheme(colorScheme = lightColorScheme()) { SettingsAccentsPreviewBody() }

@Preview(name = "Oscuro (base)", widthDp = 380, heightDp = 1000)
@Composable
private fun SettingsAccentsDarkPreview() =
    MaterialTheme(colorScheme = darkColorScheme()) { SettingsAccentsPreviewBody() }

/** Peor caso: un wallpaper "apagado" donde primary/secondary/tertiary quedan muy parecidos. */
@Preview(name = "Material You apagado (verde)", widthDp = 380, heightDp = 1000)
@Composable
private fun SettingsAccentsMutedPreview() = MaterialTheme(
    colorScheme = lightColorScheme(
        primaryContainer = Color(0xFFCDE8D2), onPrimaryContainer = Color(0xFF0B2A14),
        secondaryContainer = Color(0xFFD3E4D6), onSecondaryContainer = Color(0xFF0E1F13),
        tertiaryContainer = Color(0xFFCFE6E4), onTertiaryContainer = Color(0xFF0A2120),
        errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002)
    )
) { SettingsAccentsPreviewBody() }
