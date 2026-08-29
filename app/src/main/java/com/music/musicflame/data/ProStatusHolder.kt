package com.music.musicflame.data

import android.content.Context
import androidx.compose.runtime.mutableStateOf

/**
 * Estado global y reactivo de "¿está desbloqueado el Pro?", para que TODA la
 * app (Theme.kt, FullScreenPlayer.kt, SettingsScreen.kt) se entere al
 * instante cuando cambia, sin necesidad de cerrar y reabrir la app.
 *
 * ANTES: cada pantalla llamaba a LicenseRepository(context).isProUnlocked()
 * envuelto en remember { ... } sin claves, así que el resultado quedaba
 * "congelado" desde el momento en que esa pantalla entraba en composición.
 * Aunque el usuario acabara de iniciar sesión con la cuenta dueña (correo,
 * ver isOwnerAccount()) o de activar una license key (pago), el Arcoíris y
 * el resto de personalizaciones de pago seguían viéndose "bloqueados" hasta
 * reiniciar la app por completo — eso era el "tarda en activarse".
 *
 * AHORA: el valor vive en un solo mutableStateOf compartido. Cualquier
 * composable que lea [isProUnlocked] se recompone automáticamente en cuanto
 * cambia, sin importar por cuál de los dos caminos (correo o pago) se haya
 * desbloqueado.
 *
 * Llamar a [refresh] justo después de:
 *  - Iniciar sesión con Google exitosamente (MainActivity, login por correo)
 *  - revalidateSilently() al abrir la app (MainActivity)
 *  - Validar una license key nueva (SettingsScreen, pago)
 *  - Quitar la licencia (SettingsScreen)
 */
object ProStatusHolder {
    private val state = mutableStateOf(false)

    val isProUnlocked: Boolean
        get() = state.value

    fun refresh(context: Context) {
        state.value = LicenseRepository(context).isProUnlocked()
    }
}
