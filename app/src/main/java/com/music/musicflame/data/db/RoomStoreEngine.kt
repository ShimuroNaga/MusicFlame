package com.music.musicflame.data.db

import android.content.Context
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Motor común de los repositorios que se migraron de "JSON en SharedPreferences" a Room
 * (StatsRepository, PlaylistRepository, SongCustomizationRepository, MomentsRepository).
 * Cada repositorio tiene un `object` privado que hereda de esta clase y solo implementa los
 * ganchos específicos de sus datos; todo lo delicado (orden, migración, fallback, lotes) vive aquí
 * UNA sola vez.
 *
 * Patrón: los datos viven en memoria (los repos leen de ahí, sin tocar disco) y Room es la copia
 * persistente. Las escrituras actualizan memoria al instante y se encolan; un único consumidor en
 * Dispatchers.IO las aplica en lotes, en orden (la última operación por clave gana).
 *
 * ARRANQUE (siempre en Dispatchers.IO, nunca en el hilo principal):
 *  1. Si el flag de migración no está puesto: lee el JSON viejo, lo inserta en Room (IGNORE,
 *     idempotente) y VERIFICA el conteo. Solo si todo salió bien marca el flag y borra el JSON viejo.
 *  2. Carga todo Room a memoria.
 *  Si algo falla (y la migración no estaba hecha): no se marca el flag, no se borra nada, se
 *  trabaja con el JSON viejo (memoria cargada de ahí y escrituras nuevas al mismo JSON) y se
 *  reintenta en el próximo arranque. Nunca crashea.
 *
 * RED DE SEGURIDAD: [awaitLoaded] espera (máx. 3 s) a que termine la carga inicial. Las funciones
 * públicas de los repos la llaman antes de leer/escribir para NO operar sobre datos a medio cargar
 * (por ejemplo, sumar una reproducción sobre un contador que todavía no se cargó lo pisaría).
 * Normalmente la carga ya terminó (se dispara con preload() desde MainActivity.onCreate); si alguien
 * llega antes (servicio, widget, tile en un proceso en frío) espera esos milisegundos. Si pasa en el
 * hilo principal queda un Log.w para poder detectarlo.
 */
abstract class RoomStoreEngine<K : Any, O : Any>(
    private val tag: String,
    private val legacyPrefsName: String,
    private val legacyKey: String,
    private val flagKey: String = "room_migration_done"
) {
    protected lateinit var appContext: Context
        private set

    private val started = AtomicBoolean(false)
    private val loadedLatch = CountDownLatch(1)
    private val ops = Channel<Pair<K, O>>(Channel.UNLIMITED)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    protected var roomAvailable = false
        private set

    @Volatile
    private var warnedMainThread = false

    /** Parsea el JSON viejo, lo inserta en Room (IGNORE) y verifica el conteo. Devuelve cuántas entradas migró. Lanza excepción si algo falla. JSONException = JSON corrupto. */
    protected abstract suspend fun migrateLegacy(json: String): Int

    /** Llena la memoria con todo lo que hay en Room. */
    protected abstract suspend fun loadFromRoom()

    /** Llena la memoria desde el JSON viejo (modo "datos viejos"). */
    protected abstract fun loadFromLegacyJson(json: String)

    /** Aplica un lote de operaciones (ya fusionadas por clave) a Room. */
    protected abstract suspend fun flush(batch: Map<K, O>)

    /** Serializa la memoria al formato del JSON viejo (modo "datos viejos"). */
    protected abstract fun serializeLegacy(): String

    /** Texto corto para el log ("12 canciones", etc.). */
    protected abstract fun describeMemory(): String

    fun start(context: Context) {
        if (!started.compareAndSet(false, true)) return
        appContext = context.applicationContext
        scope.launch {
            try {
                initialize()
            } finally {
                loadedLatch.countDown()
            }
            processOps()
        }
    }

    /** Espera a que termine la carga inicial (casi siempre ya terminó y vuelve al instante). */
    fun awaitLoaded() {
        if (loadedLatch.count == 0L) return
        if (!warnedMainThread && Looper.myLooper() == Looper.getMainLooper()) {
            warnedMainThread = true
            Log.w(tag, "Se pidió un dato en el hilo principal antes de terminar la carga inicial; espero (máx. 3 s)")
        }
        val ok = try {
            loadedLatch.await(3, TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
        if (!ok) Log.e(tag, "La carga inicial tardó más de 3 s; sigo con lo que haya en memoria")
    }

    fun enqueue(key: K, op: O) {
        ops.trySend(key to op)
    }

    // ------------------------------------------------------------------ arranque

    private suspend fun initialize() {
        val prefs = appContext.getSharedPreferences(legacyPrefsName, Context.MODE_PRIVATE)
        val alreadyMigrated = prefs.getBoolean(flagKey, false)
        try {
            if (!alreadyMigrated) migrate(prefs)
            loadFromRoom()
            roomAvailable = true
            Log.i(tag, "Room listo: ${describeMemory()} cargados en memoria")
        } catch (e: Exception) {
            roomAvailable = false
            Log.e(tag, "Room/migración falló; sigo con los datos viejos y reintento en el próximo arranque", e)
            if (!alreadyMigrated) {
                val json = prefs.getString(legacyKey, null)
                if (json != null) {
                    try {
                        loadFromLegacyJson(json)
                    } catch (parseError: Exception) {
                        Log.w(tag, "El JSON viejo no se pudo leer; arranco vacío", parseError)
                    }
                }
            }
        }
    }

    private suspend fun migrate(prefs: android.content.SharedPreferences) {
        val json = prefs.getString(legacyKey, null)
        if (json == null) {
            prefs.edit().putBoolean(flagKey, true).commit()
            Log.i(tag, "Migración: no había JSON viejo; flag marcado")
            return
        }
        val migrated = try {
            migrateLegacy(json)
        } catch (e: JSONException) {
            // JSON corrupto: no hay nada recuperable. Se marca como hecha (reintentar no serviría)
            // pero NO se borra el JSON viejo.
            Log.w(tag, "Migración: el JSON viejo está corrupto; se omite (no se borra)", e)
            prefs.edit().putBoolean(flagKey, true).commit()
            return
        }
        // migrateLegacy ya verificó el conteo (si no, lanzó excepción). Recién ahora se marca y se borra lo viejo.
        val saved = prefs.edit().putBoolean(flagKey, true).remove(legacyKey).commit()
        Log.i(tag, "Migración completada: $migrated entradas del JSON viejo -> Room. Flag guardado=$saved")
    }

    // ------------------------------------------------------------------ escritura

    private suspend fun processOps() {
        while (true) {
            val first = ops.receiveCatching().getOrNull() ?: return
            delay(BATCH_WINDOW_MS) // junta ráfagas en una sola escritura
            val batch = LinkedHashMap<K, O>()
            batch[first.first] = first.second
            while (true) {
                val next = ops.tryReceive().getOrNull() ?: break
                batch.remove(next.first) // reinsertar para que el orden refleje la última operación
                batch[next.first] = next.second
            }
            try {
                if (roomAvailable) flush(batch) else persistLegacyIfNeeded()
            } catch (e: Exception) {
                Log.w(tag, "No se pudo guardar un lote de ${batch.size} cambios", e)
            }
        }
    }

    /**
     * Modo "datos viejos" (Room no disponible y migración pendiente): las escrituras nuevas se siguen
     * guardando en el JSON viejo para no perderlas y que el reintento las migre. Si la migración ya
     * estaba hecha (flag), no se toca el JSON: nadie lo leería.
     */
    private fun persistLegacyIfNeeded() {
        val prefs = appContext.getSharedPreferences(legacyPrefsName, Context.MODE_PRIVATE)
        if (prefs.getBoolean(flagKey, false)) return
        prefs.edit().putString(legacyKey, serializeLegacy()).apply()
    }

    private companion object {
        const val BATCH_WINDOW_MS = 100L
    }
}
