package com.music.musicflame.data

import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Estado global y reactivo de qué ítems del catálogo (PaymentCatalog) están
 * desbloqueados, para que TODA la app (Theme.kt, FullScreenPlayer.kt,
 * SettingsScreen.kt, MusicPlaybackService.kt) se entere al instante cuando
 * cambia, sin necesidad de cerrar y reabrir la app.
 *
 * REEMPLAZA el [isProUnlocked] booleano único de antes: ahora que cada
 * compra en Ko-fi desbloquea solo un subconjunto del catálogo (ver
 * LicenseRepository.isItemUnlocked/unlockedItemIds), un solo booleano ya no
 * alcanza. El valor vive en un solo mutableStateOf<Set<String>> compartido;
 * cualquier composable que lea [isItemUnlocked] se recompone automáticamente
 * en cuanto cambia, sin importar por cuál camino se haya desbloqueado
 * (cuenta dueña, o cualquiera de los productos comprados).
 *
 * Llamar a [refresh] justo después de:
 *  - Iniciar sesión con Google exitosamente (MainActivity, login por correo)
 *  - restoreSilently() al abrir la app (MainActivity)
 *  - Restaurar compras (SettingsScreen, pago)
 *  - Quitar las compras guardadas (SettingsScreen)
 */
object ProStatusHolder {
    private val unlockedIdsState = mutableStateOf<Set<String>>(emptySet())

    // --- Precarga al arrancar (evita el "parpadeo" del tema) -------------------------------------
    // Antes, unlockedIds arrancaba vacío y se llenaba DESPUÉS del primer frame (LaunchedEffect en
    // Theme.kt / MainActivity). Resultado: el primer frame pintaba la fuente y los colores de pago
    // del usuario con el valor gratis de siempre, y un instante después "saltaban" a los suyos.
    // Ahora se calcula en segundo plano desde Application.onCreate y MainActivity lo espera (con
    // tope) antes de dibujar, así el primer frame ya sale con el tema correcto.
    private val preloadStarted = AtomicBoolean(false)
    private val readyLatch = CountDownLatch(1)

    @Volatile
    private var ready = false

    /** Calcula el estado de compras en un hilo aparte. Seguro llamarla varias veces. */
    fun preload(context: Context) {
        if (!preloadStarted.compareAndSet(false, true)) return
        val appContext = context.applicationContext
        Thread({
            try {
                refresh(appContext)
            } catch (e: Exception) {
                Log.w("ProStatusHolder", "No se pudo precargar el estado de compras; se calculará al abrir la UI", e)
            } finally {
                ready = true
                readyLatch.countDown()
            }
        }, "pro-status-preload").start()
    }

    /** Espera (con tope) a que termine la precarga. Normalmente ya terminó. */
    fun awaitReady(maxWaitMs: Long = 1500L) {
        if (ready) return
        try {
            readyLatch.await(maxWaitMs, TimeUnit.MILLISECONDS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

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
