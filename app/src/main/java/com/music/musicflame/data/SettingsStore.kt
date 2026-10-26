package com.music.musicflame.data

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * ÚNICA puerta de acceso a los ajustes de la app (antes: archivo SharedPreferences "settings").
 *
 * FASE B: los ajustes viven ahora en DataStore (Preferences), con la migración automática desde el
 * archivo viejo "settings" (SharedPreferencesMigration). La API pública sigue imitando a
 * SharedPreferences (lecturas SÍNCRONAS, edit().putX().apply()) para que ninguno de los ~26 sitios
 * que la usan haya tenido que cambiar.
 *
 * Cómo funciona:
 *  - Al iniciar el proceso (MusicFlameApp.onCreate) se carga DataStore en segundo plano (Dispatchers.IO)
 *    y se deja una copia en memoria. TODAS las lecturas síncronas (widgets, servicio, Theme.kt...)
 *    salen de esa copia: nunca tocan disco.
 *  - Las escrituras actualizan la copia en memoria al instante y se guardan en DataStore en segundo
 *    plano, en orden.
 *  - Los listeners (OnChangeListener) siguen funcionando igual (Theme.kt, AiChatScreen, servicio...).
 *  - Si DataStore NO se puede inicializar (error de disco, migración fallida...), la app NO crashea:
 *    sigue usando el archivo viejo SharedPreferences (que no se borra hasta que la copia termina bien)
 *    y reintenta en el siguiente arranque.
 *
 * Quedan en SharedPreferences a propósito (ver SP_ONLY_KEYS): los favoritos (dato, no ajuste) y el
 * estado del widget / diagnóstico, que el servicio escribe muy seguido.
 *
 * Reglas:
 *  - NADA fuera de esta clase debe tocar el archivo "settings" ni el DataStore de ajustes.
 *  - "license", "ai_online_secure" y el idioma NO pasan por aquí (se quedan como están).
 */
class SettingsStore private constructor(private val appContext: Context) {

    private val sp: SharedPreferences =
        appContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    // ---------------------------------------------------------------- estado de DataStore

    /** Copia en memoria de los ajustes que viven en DataStore (fuente de las lecturas síncronas). */
    private val mem = ConcurrentHashMap<String, Any>()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val started = AtomicBoolean(false)
    private val loadedLatch = CountDownLatch(1)

    @Volatile
    private var loaded = false

    /** true solo si DataStore se inicializó bien; si es false, TODO va al archivo viejo. */
    @Volatile
    private var dsReady = false

    private val writeQueue = Channel<List<WriteOp>>(Channel.UNLIMITED)

    private val listeners = CopyOnWriteArraySet<OnChangeListener>()
    private val mainHandler = Handler(Looper.getMainLooper())

    // ---------------------------------------------------------------- carga inicial

    /** Arranca la carga en segundo plano. Es seguro llamarla muchas veces; solo la primera hace algo. */
    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch { initialize() }
    }

    /**
     * Espera (con tope) a que termine la carga inicial. Normalmente ya terminó cuando alguien lo pide,
     * porque MusicFlameApp la lanza en Application.onCreate. Solo bloquea de verdad si una lectura llega
     * en los primeros milisegundos de un proceso en frío (widget, tile, servicio).
     */
    fun awaitLoaded() {
        if (loaded) return
        start()
        val onMain = Looper.myLooper() == Looper.getMainLooper()
        val t0 = SystemClock.elapsedRealtime()
        val finished = try {
            loadedLatch.await(MAX_WAIT_MS, TimeUnit.MILLISECONDS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
        val waited = SystemClock.elapsedRealtime() - t0
        if (!finished) {
            Log.e(TAG, "La carga de ajustes no terminó en ${MAX_WAIT_MS}ms; se usa el archivo viejo por ahora")
        } else if (onMain && waited > 30) {
            Log.w(TAG, "Lectura de ajustes en el hilo principal esperó ${waited}ms a la carga inicial")
        }
    }

    private suspend fun initialize() {
        try {
            // Solo se migran las claves que el archivo viejo todavía tiene y que corresponden a DataStore.
            // (sp.all espera a que SharedPreferences cargue el archivo: estamos en Dispatchers.IO.)
            val pending = sp.all.keys.filterTo(HashSet<String>()) { isDsKey(it) }
            val ds: DataStore<Preferences> = PreferenceDataStoreFactory.create(
                migrations = listOf(
                    // No sobrescribe claves que ya existan en DataStore, y SOLO borra del archivo viejo
                    // las claves migradas DESPUÉS de haberlas guardado bien en DataStore. Si algo falla,
                    // no se borra nada y se reintenta en el siguiente arranque.
                    SharedPreferencesMigration(appContext, FILE_NAME, pending)
                ),
                scope = scope,
                produceFile = { File(appContext.filesDir, "datastore/$DATASTORE_NAME.preferences_pb") }
            )
            val snapshot = ds.data.first() // aquí corre la migración (si hace falta)
            for ((key, value) in snapshot.asMap()) {
                mem[key.name] = normalize(value)
            }
            dsReady = true
            scope.launch { writerLoop(ds) }
            val leftover = sp.all.keys.count { isDsKey(it) }
            Log.i(
                TAG,
                "DataStore listo: ${mem.size} ajustes en memoria. " +
                    "Pendientes de migrar al abrir: ${pending.size}; quedan en el archivo viejo: $leftover"
            )
            if (leftover > 0) {
                Log.w(TAG, "Quedaron $leftover claves duplicadas en el archivo viejo (se ignoran; DataStore manda)")
            }
        } catch (e: Exception) {
            dsReady = false
            Log.e(TAG, "DataStore/migración falló; sigo con el archivo viejo y reintento en el próximo arranque", e)
        } finally {
            loaded = true
            loadedLatch.countDown()
        }
    }

    /** Guarda en DataStore, en orden y en lotes, lo que los Editor van encolando. */
    private suspend fun writerLoop(ds: DataStore<Preferences>) {
        for (first in writeQueue) {
            val ops = ArrayList<WriteOp>(first)
            while (true) {
                val more = writeQueue.tryReceive().getOrNull() ?: break
                ops.addAll(more)
            }
            try {
                ds.edit { prefs -> ops.forEach { applyOp(prefs, it) } }
            } catch (e: Exception) {
                Log.e(TAG, "No se pudo guardar un lote de ajustes en DataStore (${ops.size} cambios)", e)
            }
        }
    }

    // ---------------------------------------------------------------- enrutado de claves

    /** ¿Esta clave corresponde a DataStore (en teoría)? Todo menos SP_ONLY_KEYS. */
    private fun isDsKey(key: String): Boolean = key !in SP_ONLY_KEYS

    /** ¿Hay que usar DataStore AHORA para esta clave? (espera a la carga inicial si hace falta) */
    private fun useDs(key: String): Boolean {
        if (!isDsKey(key)) return false
        awaitLoaded()
        return dsReady
    }

    // ---------------------------------------------------------------- lecturas (síncronas)

    fun getBoolean(key: String, defValue: Boolean): Boolean =
        if (useDs(key)) (mem[key] as? Boolean) ?: defValue else sp.getBoolean(key, defValue)

    fun getInt(key: String, defValue: Int): Int =
        if (useDs(key)) (mem[key] as? Int) ?: defValue else sp.getInt(key, defValue)

    fun getLong(key: String, defValue: Long): Long =
        if (useDs(key)) (mem[key] as? Long) ?: defValue else sp.getLong(key, defValue)

    fun getFloat(key: String, defValue: Float): Float =
        if (useDs(key)) (mem[key] as? Float) ?: defValue else sp.getFloat(key, defValue)

    fun getString(key: String, defValue: String?): String? =
        if (useDs(key)) (mem[key] as? String) ?: defValue else sp.getString(key, defValue)

    @Suppress("UNCHECKED_CAST")
    fun getStringSet(key: String, defValues: Set<String>?): Set<String>? =
        if (useDs(key)) (mem[key] as? Set<String>) ?: defValues else sp.getStringSet(key, defValues)

    fun contains(key: String): Boolean =
        if (useDs(key)) mem.containsKey(key) else sp.contains(key)

    /** Todas las claves/valores (lo usa ConfigExportRepository para exportar la configuración). */
    val all: Map<String, Any?>
        get() {
            awaitLoaded()
            val result = HashMap<String, Any?>()
            for ((key, value) in sp.all) {
                // Con DataStore activo, las claves de DataStore salen de la copia en memoria.
                if (!dsReady || !isDsKey(key)) result[key] = value
            }
            if (dsReady) result.putAll(mem)
            return result
        }

    // ---------------------------------------------------------------- escrituras

    fun edit(): Editor = Editor(this)

    /** Mismo contrato que SharedPreferences.Editor: encadenable; apply() asíncrono. */
    class Editor internal constructor(private val store: SettingsStore) {
        private val ops = ArrayList<EditorOp>()

        fun putBoolean(key: String, value: Boolean): Editor {
            ops.add(EditorOp.Put(key, value))
            return this
        }

        fun putInt(key: String, value: Int): Editor {
            ops.add(EditorOp.Put(key, value))
            return this
        }

        fun putLong(key: String, value: Long): Editor {
            ops.add(EditorOp.Put(key, value))
            return this
        }

        fun putFloat(key: String, value: Float): Editor {
            ops.add(EditorOp.Put(key, value))
            return this
        }

        /** Como en SharedPreferences, un valor null equivale a borrar la clave. */
        fun putString(key: String, value: String?): Editor {
            ops.add(if (value == null) EditorOp.Remove(key) else EditorOp.Put(key, value))
            return this
        }

        fun putStringSet(key: String, value: Set<String>?): Editor {
            ops.add(if (value == null) EditorOp.Remove(key) else EditorOp.Put(key, HashSet(value)))
            return this
        }

        fun remove(key: String): Editor {
            ops.add(EditorOp.Remove(key))
            return this
        }

        fun clear(): Editor {
            ops.add(EditorOp.Clear)
            return this
        }

        /** Memoria al instante; disco en segundo plano. */
        fun apply() {
            store.applyOps(ops, commit = false)
        }

        /**
         * Para claves en SharedPreferences espera a que termine; para claves en DataStore se guarda en
         * segundo plano igual que apply() (nadie usa commit() hoy). Devuelve true si no hubo error.
         */
        fun commit(): Boolean = store.applyOps(ops, commit = true)
    }

    internal sealed interface EditorOp {
        data class Put(val key: String, val value: Any) : EditorOp
        data class Remove(val key: String) : EditorOp
        data object Clear : EditorOp
    }

    private sealed interface WriteOp {
        data class Put(val key: String, val value: Any) : WriteOp
        data class Remove(val key: String) : WriteOp
        data object Clear : WriteOp
    }

    private fun applyOps(ops: List<EditorOp>, commit: Boolean): Boolean {
        if (ops.isEmpty()) return true
        val spEditor = sp.edit()
        var spTouched = false
        val dsOps = ArrayList<WriteOp>()
        val changed = LinkedHashSet<String>()

        // Igual que SharedPreferences: clear() se aplica primero, sin importar el orden.
        val ordered = ops.filterIsInstance<EditorOp.Clear>() + ops.filter { it !is EditorOp.Clear }
        for (op in ordered) {
            when (op) {
                is EditorOp.Clear -> {
                    spEditor.clear()
                    spTouched = true
                    awaitLoaded()
                    if (dsReady) {
                        for (k in mem.keys.toList()) {
                            if (mem.remove(k) != null) changed.add(k)
                        }
                        dsOps.add(WriteOp.Clear)
                    }
                }
                is EditorOp.Put -> {
                    if (useDs(op.key)) {
                        val value = normalize(op.value)
                        if (mem.put(op.key, value) != value) changed.add(op.key)
                        dsOps.add(WriteOp.Put(op.key, value))
                    } else {
                        putInSharedPrefs(spEditor, op.key, op.value)
                        spTouched = true
                    }
                }
                is EditorOp.Remove -> {
                    if (useDs(op.key)) {
                        if (mem.remove(op.key) != null) changed.add(op.key)
                        dsOps.add(WriteOp.Remove(op.key))
                    } else {
                        spEditor.remove(op.key)
                        spTouched = true
                    }
                }
            }
        }

        if (dsOps.isNotEmpty()) writeQueue.trySend(dsOps)
        var ok = true
        if (spTouched) {
            if (commit) ok = spEditor.commit() else spEditor.apply()
        }
        notifyChanged(changed)
        return ok
    }

    @Suppress("UNCHECKED_CAST")
    private fun putInSharedPrefs(editor: SharedPreferences.Editor, key: String, value: Any) {
        when (value) {
            is Boolean -> editor.putBoolean(key, value)
            is Int -> editor.putInt(key, value)
            is Long -> editor.putLong(key, value)
            is Float -> editor.putFloat(key, value)
            is String -> editor.putString(key, value)
            is Set<*> -> editor.putStringSet(key, value as Set<String>)
            else -> Log.w(TAG, "Tipo no soportado para la clave $key: ${value.javaClass.name}")
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun applyOp(prefs: MutablePreferences, op: WriteOp) {
        when (op) {
            is WriteOp.Clear -> prefs.clear()
            is WriteOp.Remove -> removeEveryType(prefs, op.key)
            is WriteOp.Put -> {
                removeEveryType(prefs, op.key) // por si la clave existía con otro tipo
                when (val v = op.value) {
                    is Boolean -> prefs[booleanPreferencesKey(op.key)] = v
                    is Int -> prefs[intPreferencesKey(op.key)] = v
                    is Long -> prefs[longPreferencesKey(op.key)] = v
                    is Float -> prefs[floatPreferencesKey(op.key)] = v
                    is String -> prefs[stringPreferencesKey(op.key)] = v
                    is Set<*> -> prefs[stringSetPreferencesKey(op.key)] = v as Set<String>
                    else -> Log.w(TAG, "Tipo no soportado para la clave ${op.key}: ${v.javaClass.name}")
                }
            }
        }
    }

    private fun removeEveryType(prefs: MutablePreferences, name: String) {
        prefs.remove(booleanPreferencesKey(name))
        prefs.remove(intPreferencesKey(name))
        prefs.remove(longPreferencesKey(name))
        prefs.remove(floatPreferencesKey(name))
        prefs.remove(stringPreferencesKey(name))
        prefs.remove(stringSetPreferencesKey(name))
    }

    /** Los Set se guardan como copia inmutable para que nadie los modifique por fuera. */
    private fun normalize(value: Any): Any =
        if (value is Set<*>) Collections.unmodifiableSet(HashSet(value)) else value

    // ---------------------------------------------------------------- cambios en vivo

    /** Equivalente a SharedPreferences.OnSharedPreferenceChangeListener (key puede ser null). */
    fun interface OnChangeListener {
        fun onChanged(key: String?)
    }

    // SharedPreferences guarda sus listeners con referencia DÉBIL: este mapa mantiene el puente vivo
    // mientras el listener esté registrado. El puente cubre las claves que siguen en SharedPreferences;
    // las de DataStore se avisan desde notifyChanged().
    private val bridges =
        ConcurrentHashMap<OnChangeListener, SharedPreferences.OnSharedPreferenceChangeListener>()

    fun registerOnChangeListener(listener: OnChangeListener) {
        listeners.add(listener)
        val bridge = bridges.getOrPut(listener) {
            SharedPreferences.OnSharedPreferenceChangeListener { _, key -> listener.onChanged(key) }
        }
        sp.registerOnSharedPreferenceChangeListener(bridge)
    }

    fun unregisterOnChangeListener(listener: OnChangeListener) {
        listeners.remove(listener)
        bridges.remove(listener)?.let { sp.unregisterOnSharedPreferenceChangeListener(it) }
    }

    /** Avisa en el hilo principal (como hace SharedPreferences) de las claves de DataStore que cambiaron. */
    private fun notifyChanged(keys: Collection<String>) {
        if (keys.isEmpty() || listeners.isEmpty()) return
        val keysCopy = ArrayList(keys)
        val run = Runnable {
            for (key in keysCopy) {
                for (listener in listeners) listener.onChanged(key)
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) run.run() else mainHandler.post(run)
    }

    companion object {
        private const val TAG = "SettingsStore"

        /** Archivo viejo de ajustes (SharedPreferences). NO cambiar: ahí están los datos de los usuarios. */
        const val FILE_NAME = "settings"

        /** Archivo nuevo en DataStore: files/datastore/settings_store.preferences_pb */
        const val DATASTORE_NAME = "settings_store"

        /** Tope de espera si alguien lee antes de que termine la carga inicial (no debería pasar). */
        private const val MAX_WAIT_MS = 4000L

        /**
         * Claves que se QUEDAN en SharedPreferences (no se migran):
         *  - favorite_songs_set: es un dato (lista de favoritos), no un ajuste, y el servicio lo escribe.
         *  - volume_normalization_status: texto de diagnóstico que escribe el servicio.
         *  - widget_*: estado del widget (canción, carátula, letras) que el servicio reescribe muy seguido.
         *    OJO: "widget_bg_opacity" NO está aquí: es un ajuste del usuario y sí va a DataStore.
         */
        private val SP_ONLY_KEYS: Set<String> = setOf(
            "favorite_songs_set",
            "volume_normalization_status",
            "widget_has_song",
            "widget_song_title",
            "widget_song_artist",
            "widget_album_art_uri",
            "widget_is_playing",
            "widget_media_id",
            "widget_lyrics_lines",
            "widget_lyrics_media_id"
        )

        @Volatile
        private var instance: SettingsStore? = null

        fun get(context: Context): SettingsStore =
            instance ?: synchronized(this) {
                instance ?: SettingsStore(context.applicationContext).also { instance = it }
            }

        /** Lanza la carga en segundo plano. Se llama desde Application.onCreate. */
        fun preload(context: Context) {
            get(context).start()
        }
    }
}
