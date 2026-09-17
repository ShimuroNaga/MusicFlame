package com.music.musicflame.data

import android.content.Context
import androidx.compose.runtime.mutableStateOf

/**
 * Estado global y reactivo de qué ítems del catálogo (PaymentCatalog) están
 * desbloqueados, para que TODA la app (Theme.kt, FullScreenPlayer.kt,
 * SettingsScreen.kt, MusicPlaybackService.kt) se entere al instante cuando
 * cambia, sin necesidad de cerrar y reabrir la app.
 *
 * REEMPLAZA el [isProUnlocked] booleano único de antes: ahora que cada
 * compra en Lemon Squeezy desbloquea solo un subconjunto del catálogo (ver
 * LicenseRepository.isItemUnlocked/unlockedItemIds), un solo booleano ya no
 * alcanza. El valor vive en un solo mutableStateOf<Set<String>> compartido;
 * cualquier composable que lea [isItemUnlocked] se recompone automáticamente
 * en cuanto cambia, sin importar por cuál camino se haya desbloqueado
 * (cuenta dueña, o cualquiera de las licencias compradas).
 *
 * Llamar a [refresh] justo después de:
 *  - Iniciar sesión con Google exitosamente (MainActivity, login por correo)
 *  - revalidateAllSilently() al abrir la app (MainActivity)
 *  - Validar una license key nueva (SettingsScreen, pago)
 *  - Quitar una o todas las licencias (SettingsScreen)
 */
object ProStatusHolder {
    private val unlockedIdsState = mutableStateOf<Set<String>>(emptySet())

    /** true si el ítem [catalogId] (un id de PaymentCatalog.ITEMS) está desbloqueado ahora mismo. */
    fun isItemUnlocked(catalogId: String): Boolean = catalogId in unlockedIdsState.value

    /** Todos los ids actualmente desbloqueados (para pintar listas completas sin repetir el chequeo ítem por ítem). */
    val unlockedIds: Set<String>
        get() = unlockedIdsState.value

    /**
     * Compatibilidad: true si TODO el catálogo está desbloqueado (dueño de
     * la app, o compró el producto "TODO"). Usar solo donde de verdad se
     * necesita "¿tiene todo?" (ej. mostrar una insignia "Pro completo") —
     * para gatear un selector o botón puntual, usar [isItemUnlocked] con el
     * id específico de ese ítem.
     */
    val isProUnlocked: Boolean
        get() = PaymentCatalog.ITEMS.isNotEmpty() && PaymentCatalog.ITEMS.all { it.id in unlockedIdsState.value }

    fun refresh(context: Context) {
        unlockedIdsState.value = LicenseRepository(context).unlockedItemIds()
    }
}
