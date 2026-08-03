package com.music.musicflame.ui.utils

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * === SafeInsets ===
 *
 * Archivo "universal" para que TODAS las pantallas/sub-pantallas de MusicFlame
 * se adapten al tamaño real de cualquier celular (gestos, 3 botones tipo Honor/EMUI,
 * notch, cámara perforada, etc.) sin cortarse ni dejar espacios vacíos de más.
 *
 * La app apunta a targetSdk 36, que fuerza edge-to-edge: el contenido dibuja
 * detrás de las barras del sistema SIEMPRE. Si no le decimos con qué padding
 * respetarlas, el contenido queda tapado (3 botones) o con aire de más/menos
 * (gestos) según el teléfono.
 *
 * En vez de padding fijo (`.padding(bottom = 16.dp)`) para separarnos del borde,
 * usamos estas funciones: leen el inset REAL que reporta el sistema operativo
 * en cada celular, en tiempo de ejecución. Se adapta solo, siempre.
 *
 * Uso típico:
 *   Modifier.fillMaxWidth().safeBottomPadding()              -> barra inferior de nav
 *   Modifier.fillMaxSize().safeScreenPadding()                -> overlay a pantalla completa
 *   Modifier.fillMaxWidth().safeInputBarPadding()              -> input de chat con teclado
 */

/** Alto mínimo de "aire" que dejamos aunque el inset del sistema sea 0 (gestos). */
private val MIN_SAFE_PADDING = 8.dp

/** Alto real de la barra de navegación (gestos o 3 botones), con mínimo garantizado. */
@Composable
fun rememberSafeBottomPadding(minPadding: Dp = MIN_SAFE_PADDING): Dp {
    val navBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    return if (navBarInset > minPadding) navBarInset else minPadding
}

/** Alto real del status bar (para no meter contenido debajo del reloj/notch). */
@Composable
fun rememberSafeTopPadding(): Dp {
    return WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
}

/**
 * Padding inferior "seguro": usa el inset real de la barra de navegación del
 * sistema (se adapta a gestos, 3 botones, cualquier fabricante), con un mínimo
 * garantizado para que nunca quede pegado al borde en pantallas por gestos.
 *
 * Úsalo en cualquier elemento pegado al fondo de la pantalla: la barra de
 * navegación inferior, controles del reproductor, botones flotantes, etc.
 */
fun Modifier.safeBottomPadding(minPadding: Dp = MIN_SAFE_PADDING): Modifier = composed {
    val padding = rememberSafeBottomPadding(minPadding)
    this.padding(bottom = padding)
}

/**
 * Padding "seguro" completo para pantallas tipo overlay que dibujan a pantalla
 * completa por fuera de un Scaffold (ej. FullScreenPlayer): respeta status bar
 * arriba y navigation bar abajo, cualquiera sea la marca/skin del teléfono.
 */
fun Modifier.safeScreenPadding(): Modifier = this
    .statusBarsPadding()
    .navigationBarsPadding()

/**
 * Para inputs pegados al fondo (ej. chat de Gemini, cajas de búsqueda flotantes):
 * respeta el teclado (IME) cuando está abierto, y la nav bar cuando está cerrado.
 */
fun Modifier.safeInputBarPadding(): Modifier = this
    .navigationBarsPadding()
    .imePadding()

/**
 * Solo la barra de navegación inferior (gestos o botones), sin mínimo extra.
 * Útil cuando el propio contenido ya trae su padding visual y solo se
 * necesita evitar que quede tapado por los botones físicos/virtuales.
 */
fun Modifier.navBarOnlyPadding(): Modifier = this.navigationBarsPadding()
