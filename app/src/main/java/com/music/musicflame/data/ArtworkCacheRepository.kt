package com.music.musicflame.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.music.musicflame.data.db.AppDatabase
import com.music.musicflame.data.db.ArtworkCacheDao
import com.music.musicflame.data.db.ArtworkCacheEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Qué fuente de carátula terminó funcionando (o si ninguna funcionó) la ÚLTIMA
 * vez que se intentó resolver la carátula "de fábrica" (no personalizada) de
 * una canción:
 * - EMBEDDED: la carátula embebida en el propio archivo de audio (tag ID3/APIC).
 * - ALBUM_URI: la Uri genérica de MediaStore por álbum (content://.../albumart/{id}).
 * - NONE: ni una ni la otra tienen carátula real; se debe mostrar el ícono
 *   genérico directamente, sin intentar ninguna carga.
 */
enum class ArtworkSource { EMBEDDED, ALBUM_URI, NONE }

/** Operación de escritura pendiente hacia Room (se encolan y se aplican en lote, en IO). */
private sealed class ArtworkOp {
    abstract val path: String
    class Put(override val path: String, val source: ArtworkSource) : ArtworkOp()
    class Delete(override val path: String) : ArtworkOp()
}

/**
 * Caché persistente de qué fuente de carátula "de fábrica" funcionó por última vez para cada
 * canción (indexada por su ruta física de archivo, ya que es lo único que AlbumArt() siempre
 * recibe). Evita repetir en cada apertura de la lista intentos que ya sabemos que fallan
 * (carátula embebida -> Uri de MediaStore -> ícono genérico).
 *
 * ALMACENAMIENTO (nuevo): Room (tabla `artwork_cache` en AppDatabase). Antes era un JSON completo
 * en SharedPreferences "artwork_cache" / clave "artwork_source_map".
 *
 * INTERFAZ PÚBLICA: idéntica a la anterior (constructor con Context, get / set / invalidate, mismos
 * tipos). Las pantallas no cambian.
 *
 * CÓMO LEE SIN BLOQUEAR EL HILO PRINCIPAL: todo el caché vive en un mapa en memoria compartido por
 * el proceso (companion object). get() es un lookup en memoria, nunca toca disco. El mapa se llena
 * UNA vez, en Dispatchers.IO, al arrancar (MainActivity llama a [preload] en onCreate; si alguien
 * crea el repositorio antes, el constructor también lo dispara). Si get() se llama antes de que
 * termine esa carga (milisegundos), devuelve null = "no sé", que es exactamente el comportamiento
 * normal de un caché vacío: AlbumArt() resuelve la carátula como siempre y vuelve a guardar el
 * resultado. No se rompe nada, solo se pierde el atajo esa primera vez.
 *
 * CÓMO ESCRIBE: set()/invalidate() actualizan el mapa en memoria al instante y encolan la escritura
 * a Room, que un solo consumidor en IO aplica en lotes (en orden, sin carreras).
 *
 * MIGRACIÓN DESDE EL JSON VIEJO (una sola vez, en IO, con flag `room_migration_done`):
 *  1. Lee el JSON viejo e inserta todo en Room con IGNORE (idempotente: si se interrumpe y se
 *     repite, no duplica ni pisa datos nuevos).
 *  2. Verifica que Room tenga al menos tantas filas como entradas válidas tenía el JSON.
 *  3. Solo si la verificación pasa: marca el flag y borra el JSON viejo.
 *  Si CUALQUIER paso falla: no se marca el flag, no se borra nada, la app sigue funcionando con
 *  los datos viejos (se cargan del JSON a memoria y las escrituras nuevas se siguen guardando en
 *  el JSON) y se reintenta en el próximo arranque. Nunca crashea.
 *
 * Logs (filtrar con `adb logcat -s ArtworkCache`): ver los Log.i/Log.w/Log.e de este archivo.
 */
class ArtworkCacheRepository(context: Context) {

    init {
        ensureStarted(context.applicationContext)
    }

    /** Devuelve la fuente que funcionó la última vez para este archivo, o null si nunca se resolvió. */
    fun get(filePath: String): ArtworkSource? {
        if (filePath.isEmpty()) return null
        return memory[filePath]
    }

    /** Guarda cuál fuente funcionó (o NONE si ninguna) para este archivo. */
    fun set(filePath: String, source: ArtworkSource) {
        if (filePath.isEmpty()) return
        val previous = memory.put(filePath, source)
        if (previous == source) return // ya estaba guardado igual, no reescribas por gusto
        invalidatedBeforeLoad.remove(filePath)
        ops.trySend(ArtworkOp.Put(filePath, source))
    }

    /** Borra la entrada de un archivo puntual (ej. si el usuario le agregó/quitó una carátula por fuera de la app). */
    fun invalidate(filePath: String) {
        if (filePath.isEmpty()) return
        val removed = memory.remove(filePath) != null
        if (!loaded) invalidatedBeforeLoad.add(filePath) // que la carga inicial no lo reviva
        if (removed || !loaded) ops.trySend(ArtworkOp.Delete(filePath))
    }

    companion object {
        private const val TAG = "ArtworkCache"

        // Almacenamiento viejo (SharedPreferences + JSON) y flag de migración.
        private const val LEGACY_PREFS = "artwork_cache"
        private const val LEGACY_KEY_MAP = "artwork_source_map"
        private const val KEY_MIGRATED = "room_migration_done"

        private const val BATCH_WINDOW_MS = 100L
        private const val DELETE_CHUNK = 500

        // Estado compartido por TODAS las instancias (se crean muchas: una por AlbumArt, etc.).
        private val memory = ConcurrentHashMap<String, ArtworkSource>()
        private val invalidatedBeforeLoad: MutableSet<String> = ConcurrentHashMap.newKeySet()
        private val ops = Channel<ArtworkOp>(Channel.UNLIMITED)
        private val started = AtomicBoolean(false)
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        @Volatile private var loaded = false
        @Volatile private var roomAvailable = false

        /**
         * Arranca la carga/migración en segundo plano lo antes posible. Seguro de llamar muchas
         * veces (solo la primera hace algo). Llamarlo desde MainActivity.onCreate.
         */
        fun preload(context: Context) {
            ensureStarted(context.applicationContext)
        }

        private fun ensureStarted(appContext: Context) {
            if (!started.compareAndSet(false, true)) return
            scope.launch {
                initialize(appContext)
                processOps(appContext)
            }
        }

        // ---------------------------------------------------------------- arranque

        private suspend fun initialize(appContext: Context) {
            val legacyPrefs = appContext.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
            val alreadyMigrated = legacyPrefs.getBoolean(KEY_MIGRATED, false)
            try {
                val dao = AppDatabase.getInstance(appContext).artworkCacheDao()
                if (!alreadyMigrated) migrateFromLegacy(legacyPrefs, dao)
                mergeIntoMemory(dao.getAll())
                roomAvailable = true
                Log.i(TAG, "Room listo: ${memory.size} entradas cargadas en memoria")
            } catch (e: Exception) {
                roomAvailable = false
                Log.e(TAG, "Room/migración falló; sigo con los datos viejos y reintento en el próximo arranque", e)
                if (!alreadyMigrated) {
                    legacyPrefs.getString(LEGACY_KEY_MAP, null)?.let { json ->
                        try {
                            parseLegacy(json).forEach { (path, source) ->
                                if (path !in invalidatedBeforeLoad) memory.putIfAbsent(path, source)
                            }
                        } catch (parseError: Exception) {
                            Log.w(TAG, "El JSON viejo no se pudo leer; arranco con el caché vacío", parseError)
                        }
                    }
                }
            } finally {
                loaded = true
            }
        }

        /** Migra el JSON viejo a Room. Lanza excepción si algo sale mal (el llamador cae al modo "datos viejos"). */
        private suspend fun migrateFromLegacy(legacyPrefs: SharedPreferences, dao: ArtworkCacheDao) {
            val json = legacyPrefs.getString(LEGACY_KEY_MAP, null)
            if (json == null) {
                legacyPrefs.edit().putBoolean(KEY_MIGRATED, true).commit()
                Log.i(TAG, "Migración: no había JSON viejo; flag marcado")
                return
            }

            val parsed = try {
                parseLegacy(json)
            } catch (e: Exception) {
                // JSON corrupto: no hay nada recuperable. Se marca como hecha (reintentar no
                // serviría de nada) pero NO se borra el JSON viejo.
                Log.w(TAG, "Migración: el JSON viejo está corrupto; se omite (no se borra)", e)
                legacyPrefs.edit().putBoolean(KEY_MIGRATED, true).commit()
                return
            }

            if (parsed.isNotEmpty()) {
                dao.insertAllIgnoringExisting(parsed.map { (path, source) -> ArtworkCacheEntity(path, source.name) })
            }
            val inRoom = dao.count()
            check(inRoom >= parsed.size) { "verificación falló: Room tiene $inRoom filas y el JSON tenía ${parsed.size}" }

            // Verificado: recién ahora se marca el flag y se borra lo viejo (una sola edición atómica).
            val saved = legacyPrefs.edit().putBoolean(KEY_MIGRATED, true).remove(LEGACY_KEY_MAP).commit()
            Log.i(TAG, "Migración completada: ${parsed.size} entradas del JSON viejo -> Room ($inRoom filas en total). Flag guardado=$saved")
        }

        private fun parseLegacy(json: String): Map<String, ArtworkSource> {
            val obj = JSONObject(json) // lanza JSONException si está corrupto
            val result = HashMap<String, ArtworkSource>()
            obj.keys().forEach { key ->
                val source = try {
                    ArtworkSource.valueOf(obj.optString(key))
                } catch (e: Exception) {
                    null // valor desconocido: igual que antes, equivale a "sin dato"
                }
                if (source != null) result[key] = source
            }
            return result
        }

        private fun mergeIntoMemory(rows: List<ArtworkCacheEntity>) {
            for (row in rows) {
                if (row.filePath in invalidatedBeforeLoad) continue
                val source = try {
                    ArtworkSource.valueOf(row.source)
                } catch (e: Exception) {
                    continue
                }
                // putIfAbsent: lo que se haya escrito con set() mientras cargaba es más nuevo que la base.
                memory.putIfAbsent(row.filePath, source)
            }
        }

        // ---------------------------------------------------------------- escritura

        private suspend fun processOps(appContext: Context) {
            val dao = if (roomAvailable) AppDatabase.getInstance(appContext).artworkCacheDao() else null
            while (true) {
                val first = ops.receiveCatching().getOrNull() ?: return
                delay(BATCH_WINDOW_MS) // junta ráfagas (ej. 500 canciones sin carátula) en una sola escritura
                val batch = LinkedHashMap<String, ArtworkOp>()
                batch[first.path] = first
                while (true) {
                    val next = ops.tryReceive().getOrNull() ?: break
                    batch[next.path] = next // la última operación por ruta gana
                }
                try {
                    if (dao != null) flushToRoom(dao, batch.values) else persistLegacyIfNeeded(appContext)
                } catch (e: Exception) {
                    // Es un caché regenerable: si una escritura falla, solo se vuelve a resolver después.
                    Log.w(TAG, "No se pudo guardar un lote de ${batch.size} entradas", e)
                }
            }
        }

        private suspend fun flushToRoom(dao: ArtworkCacheDao, batch: Collection<ArtworkOp>) {
            val puts = batch.filterIsInstance<ArtworkOp.Put>().map { ArtworkCacheEntity(it.path, it.source.name) }
            val deletes = batch.filterIsInstance<ArtworkOp.Delete>().map { it.path }
            if (puts.isNotEmpty()) dao.upsertAll(puts)
            deletes.chunked(DELETE_CHUNK).forEach { dao.deleteAll(it) }
        }

        /**
         * Modo "datos viejos" (Room no disponible y migración pendiente): las escrituras nuevas se
         * siguen guardando en el JSON viejo para no perderlas y que el reintento las migre.
         * Si la migración ya estaba hecha (flag), no se toca el JSON: nadie lo leería.
         */
        private fun persistLegacyIfNeeded(appContext: Context) {
            val prefs = appContext.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
            if (prefs.getBoolean(KEY_MIGRATED, false)) return
            val obj = JSONObject()
            memory.forEach { (path, source) -> obj.put(path, source.name) }
            prefs.edit().putString(LEGACY_KEY_MAP, obj.toString()).apply()
        }
    }
}
