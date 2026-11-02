package com.music.musicflame.data

import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Junta muchos pedidos seguidos en UNA sola ejecución de [action], [delayMs] después del último.
 *
 * Para qué: varios cachés guardaban TODO su JSON en SharedPreferences en cada escritura
 * (VolumeNormalizationCacheRepository: una reescritura completa cada vez que cambia la canción;
 * AnomalyRepository: igual). Con miles de entradas eso son cientos de KB serializados por
 * canción. Con esto, una ráfaga de 500 cambios termina en 1 sola escritura.
 *
 * [flushNow] ejecuta ya (y cancela lo pendiente): usarlo para acciones del usuario que no deben
 * perderse si matan el proceso en la ventana del debounce.
 */
class DebouncedTask(private val delayMs: Long, private val action: () -> Unit) {

    private val executor by lazy {
        Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "debounced-task").apply { isDaemon = true }
        }
    }
    private val lock = Any()
    private var pending: ScheduledFuture<*>? = null
    private val runs = AtomicInteger(0)

    /** Cuántas veces se ejecutó de verdad [action] (útil para pruebas). */
    val runCount: Int get() = runs.get()

    fun request() {
        synchronized(lock) {
            pending?.cancel(false)
            pending = executor.schedule({ runAction() }, delayMs, TimeUnit.MILLISECONDS)
        }
    }

    fun flushNow() {
        synchronized(lock) {
            pending?.cancel(false)
            pending = null
        }
        runAction()
    }

    private fun runAction() {
        try {
            action()
        } finally {
            runs.incrementAndGet()
        }
    }
}
