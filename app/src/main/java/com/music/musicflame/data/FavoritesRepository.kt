package com.music.musicflame.data

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import com.music.musicflame.data.db.AppDatabase
import com.music.musicflame.data.db.FavoriteEntity
import com.music.musicflame.data.db.RoomStoreEngine
import org.json.JSONArray
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet

/** Se llama (en el hilo principal) cada vez que cambia el conjunto de favoritos, venga de donde venga. */
fun interface FavoritesChangeListener {
    fun onFavoritesChanged()
}

/** Dato viejo: StringSet "favorite_songs_set" en el archivo de prefs "settings". */
private const val LEGACY_FAVORITES_KEY = "favorite_songs_set"

/** true = marcar como favorita, false = quitar. La última operación por canción gana. */
private class FavoriteOp(val favorite: Boolean)

/**
 * Estado compartido (memoria + Room) de FavoritesRepository. Ver RoomStoreEngine para el patrón de
 * arranque/migración/escritura. Antes: StringSet "favorite_songs_set" en SharedPreferences.
 *
 * A diferencia de los otros repos, el dato viejo NO es un JSON sino un StringSet, por eso se
 * sobreescriben [readLegacy]/[writeLegacy]: el motor lo ve como un JSON de ids.
 */
private object FavoritesStore : RoomStoreEngine<Long, FavoriteOp>(
    tag = "FavoritesStore",
    legacyPrefsName = SettingsStore.FILE_NAME,
    legacyKey = LEGACY_FAVORITES_KEY,
    flagKey = "favorites_room_migration_done"
) {
    val memory: MutableSet<Long> = ConcurrentHashMap.newKeySet()
    private val lock = Any()
    private val listeners = CopyOnWriteArraySet<FavoritesChangeListener>()
    private val mainHandler = Handler(Looper.getMainLooper())

    private fun dao() = AppDatabase.getInstance(appContext).favoriteDao()

    fun addListener(listener: FavoritesChangeListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: FavoritesChangeListener) {
        listeners.remove(listener)
    }

    private fun notifyChanged() {
        mainHandler.post { listeners.forEach { it.onFavoritesChanged() } }
    }

    /** Marca/quita una canción. Solo avisa a los listeners si de verdad cambió algo. */
    fun set(songId: Long, favorite: Boolean) {
        val changed = synchronized(lock) {
            val c = if (favorite) memory.add(songId) else memory.remove(songId)
            if (c) enqueue(songId, FavoriteOp(favorite))
            c
        }
        if (changed) notifyChanged()
    }

    fun toggle(songId: Long) {
        synchronized(lock) {
            val nowFavorite = memory.add(songId) // true = no estaba y ya la agregó
            if (!nowFavorite) memory.remove(songId)
            enqueue(songId, FavoriteOp(nowFavorite))
        }
        notifyChanged()
    }

    private fun parseLegacy(json: String): Set<Long> {
        val arr = JSONArray(json) // JSONException si está corrupto
        val ids = LinkedHashSet<Long>()
        for (i in 0 until arr.length()) arr.optString(i).toLongOrNull()?.let { ids.add(it) }
        return ids
    }

    override fun readLegacy(prefs: SharedPreferences): String? = try {
        prefs.getStringSet(LEGACY_FAVORITES_KEY, null)?.let { JSONArray(it.toList()).toString() }
    } catch (e: ClassCastException) {
        null // la clave existe pero no es un StringSet: no hay nada recuperable
    }

    override fun writeLegacy(editor: SharedPreferences.Editor, serialized: String) {
        editor.putStringSet(LEGACY_FAVORITES_KEY, memory.mapTo(HashSet()) { it.toString() })
    }

    override suspend fun migrateLegacy(json: String): Int {
        val entities = parseLegacy(json).map { FavoriteEntity(it) }
        if (entities.isNotEmpty()) dao().insertAllIgnoringExisting(entities)
        val inRoom = dao().count()
        check(inRoom >= entities.size) { "verificación falló: Room tiene $inRoom filas y el set tenía ${entities.size}" }
        return entities.size
    }

    override suspend fun loadFromRoom() {
        dao().getAll().forEach { memory.add(it.songId) }
    }

    override fun loadFromLegacyJson(json: String) {
        memory.addAll(parseLegacy(json))
    }

    override suspend fun flush(batch: Map<Long, FavoriteOp>) {
        val added = batch.filterValues { it.favorite }.keys.map { FavoriteEntity(it) }
        val removed = batch.filterValues { !it.favorite }.keys.toList()
        dao().applyChanges(removed, added)
    }

    override fun serializeLegacy(): String = JSONArray(memory.map { it.toString() }).toString()

    override fun describeMemory(): String = "${memory.size} favoritos"
}

/**
 * Favoritos del usuario. Almacenamiento: Room (tabla favorites). Interfaz pública idéntica a la
 * versión con SharedPreferences; las lecturas son de memoria (sin disco).
 *
 * La carátula de la carpeta "Favoritos" NO se migró: es un ajuste (va con el resto de Ajustes).
 */
class FavoritesRepository(context: Context) {

    private val prefs: SettingsStore = SettingsStore.get(context)

    init {
        FavoritesStore.start(context)
    }

    companion object {
        /** Arranca la carga/migración en segundo plano (llamar desde MainActivity.onCreate y el servicio). */
        fun preload(context: Context) = FavoritesStore.start(context)

        /**
         * Avisa cuando cambian los favoritos desde cualquier lado (app, notificación, tiles...).
         * Se llama en el hilo principal. Quitar el listener al destruir la pantalla/servicio.
         */
        fun addOnChangeListener(listener: FavoritesChangeListener) = FavoritesStore.addListener(listener)

        fun removeOnChangeListener(listener: FavoritesChangeListener) = FavoritesStore.removeListener(listener)
    }

    fun isFavorite(songId: Long): Boolean {
        FavoritesStore.awaitLoaded()
        return FavoritesStore.memory.contains(songId)
    }

    fun toggleFavorite(songId: Long) {
        FavoritesStore.awaitLoaded()
        FavoritesStore.toggle(songId)
    }

    fun addFavorite(songId: Long) {
        FavoritesStore.awaitLoaded()
        FavoritesStore.set(songId, true)
    }

    fun removeFavorite(songId: Long) {
        FavoritesStore.awaitLoaded()
        FavoritesStore.set(songId, false)
    }

    fun getAllFavoriteIds(): Set<Long> {
        FavoritesStore.awaitLoaded()
        return FavoritesStore.memory.toSet()
    }

    // --- CARÁTULA PERSONALIZADA DE LA CARPETA "FAVORITOS" (imagen o GIF) ---
    fun getCoverUri(): String? = prefs.getString("favorites_cover_uri", null)

    fun saveCoverUri(uri: String) {
        prefs.edit().putString("favorites_cover_uri", if (uri.isBlank()) null else uri).apply()
    }
}
