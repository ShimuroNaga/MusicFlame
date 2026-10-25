package com.music.musicflame.data

import android.content.Context
import com.music.musicflame.data.db.AppDatabase
import com.music.musicflame.data.db.RoomStoreEngine
import com.music.musicflame.data.db.SongCustomizationEntity
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Personalización guardada para una canción: nombre, artista, álbum y/o carátula
 * (imagen o GIF) elegidos por el usuario, que sobreescriben los metadatos
 * originales del archivo.
 */
data class SongCustomization(
    val title: String? = null,
    val coverUri: String? = null,
    // --- NUEVO: editor de etiquetas/metadata ---
    val artist: String? = null,
    val album: String? = null,
    // --- NUEVO: título de visualización con códigos § (colores/glitch/etc, ver
    // FormatCodeText.kt). A DIFERENCIA de `title`, este campo es PURAMENTE
    // visual: nunca se pasa a RealTagWriter ni se escribe al tag físico del
    // mp3. Úsalo con FormatCodeText/resolveDisplayTitle para pintar el título
    // en la UI (SongItemCard, FullScreenPlayer, MiniPlayer, etc).
    val displayTitleFormatted: String? = null
)

/**
 * Estado compartido (memoria + Room) de SongCustomizationRepository. Ver RoomStoreEngine para el
 * patrón de arranque/migración/escritura. Antes: SharedPreferences "song_customizations", clave
 * "customizations_map" (JSON). La operación en cola es la personalización nueva de esa canción, o
 * null si hay que borrar su fila.
 */
private class CustomizationOp(val value: SongCustomization?)

private object CustomizationStore : RoomStoreEngine<Long, CustomizationOp>(
    tag = "CustomizationStore",
    legacyPrefsName = "song_customizations",
    legacyKey = "customizations_map"
) {
    val memory = ConcurrentHashMap<Long, SongCustomization>()
    val lock = Any()

    private fun dao() = AppDatabase.getInstance(appContext).songCustomizationDao()

    private fun isEmpty(c: SongCustomization) =
        c.title == null && c.coverUri == null && c.artist == null && c.album == null && c.displayTitleFormatted == null

    /** Guarda (o borra, si ya no queda nada personalizado) la personalización de una canción. Llamar con [lock] tomado. */
    fun commit(songId: Long, value: SongCustomization?) {
        if (value == null || isEmpty(value)) {
            memory.remove(songId)
            enqueue(songId, CustomizationOp(null))
        } else {
            memory[songId] = value
            enqueue(songId, CustomizationOp(value))
        }
    }

    private fun optStr(entry: JSONObject, name: String): String? =
        if (entry.has(name) && !entry.isNull(name)) entry.getString(name) else null

    private fun parseLegacy(json: String): Map<Long, SongCustomization> {
        val obj = JSONObject(json) // JSONException si está corrupto
        val result = LinkedHashMap<Long, SongCustomization>()
        obj.keys().forEach { key ->
            val id = key.toLongOrNull() ?: return@forEach
            val entry = obj.optJSONObject(key) ?: return@forEach
            val custom = SongCustomization(
                title = optStr(entry, "title"),
                coverUri = optStr(entry, "coverUri"),
                artist = optStr(entry, "artist"),
                album = optStr(entry, "album"),
                displayTitleFormatted = optStr(entry, "displayTitleFormatted")
            )
            if (!isEmpty(custom)) result[id] = custom
        }
        return result
    }

    private fun toEntity(id: Long, c: SongCustomization) =
        SongCustomizationEntity(id, c.title, c.coverUri, c.artist, c.album, c.displayTitleFormatted)

    override suspend fun migrateLegacy(json: String): Int {
        val parsed = parseLegacy(json)
        if (parsed.isNotEmpty()) dao().insertAllIgnoringExisting(parsed.map { (id, c) -> toEntity(id, c) })
        val inRoom = dao().count()
        check(inRoom >= parsed.size) { "verificación falló: Room tiene $inRoom filas y el JSON tenía ${parsed.size}" }
        return parsed.size
    }

    override suspend fun loadFromRoom() {
        dao().getAll().forEach { e ->
            memory[e.songId] = SongCustomization(
                title = e.title,
                coverUri = e.coverUri,
                artist = e.artist,
                album = e.album,
                displayTitleFormatted = e.displayTitleFormatted
            )
        }
    }

    override fun loadFromLegacyJson(json: String) {
        memory.putAll(parseLegacy(json))
    }

    override suspend fun flush(batch: Map<Long, CustomizationOp>) {
        val puts = batch.mapNotNull { (id, op) -> op.value?.let { toEntity(id, it) } }
        val deletes = batch.filterValues { it.value == null }.keys.toList()
        if (puts.isNotEmpty()) dao().upsertAll(puts)
        deletes.chunked(500).forEach { dao().deleteAll(it) }
    }

    override fun serializeLegacy(): String {
        val obj = JSONObject()
        memory.forEach { (id, custom) ->
            if (isEmpty(custom)) return@forEach
            val entry = JSONObject()
            if (custom.title != null) entry.put("title", custom.title)
            if (custom.coverUri != null) entry.put("coverUri", custom.coverUri)
            if (custom.artist != null) entry.put("artist", custom.artist)
            if (custom.album != null) entry.put("album", custom.album)
            if (custom.displayTitleFormatted != null) entry.put("displayTitleFormatted", custom.displayTitleFormatted)
            obj.put(id.toString(), entry)
        }
        return obj.toString()
    }

    override fun describeMemory(): String = "${memory.size} personalizaciones"
}

/**
 * Guarda y lee las personalizaciones de nombre/carátula por canción. Se indexa por el ID de
 * MediaStore de cada canción. Almacenamiento: Room (tabla song_customizations). Interfaz pública
 * idéntica a la versión con SharedPreferences; las lecturas son de memoria (sin disco).
 */
class SongCustomizationRepository(context: Context) {

    init {
        CustomizationStore.start(context)
    }

    companion object {
        /** Arranca la carga/migración en segundo plano (llamar desde MainActivity.onCreate). */
        fun preload(context: Context) = CustomizationStore.start(context)
    }

    /** Devuelve la personalización guardada para una canción, o null si no tiene ninguna. */
    fun getCustomization(songId: Long): SongCustomization? {
        CustomizationStore.awaitLoaded()
        return CustomizationStore.memory[songId]
    }

    /** Devuelve todas las personalizaciones guardadas, indexadas por ID de canción. */
    fun getAll(): Map<Long, SongCustomization> {
        CustomizationStore.awaitLoaded()
        return CustomizationStore.memory.toMap()
    }

    /**
     * Guarda nombre, artista, álbum y/o carátula personalizados para una canción.
     * Pasar `null` en cualquiera deja ese campo sin cambios respecto a lo que ya
     * hubiera guardado (usar los flags clear* para borrarlo puntualmente).
     */
    fun setCustomization(
        songId: Long,
        title: String? = null,
        coverUri: String? = null,
        artist: String? = null,
        album: String? = null,
        displayTitleFormatted: String? = null,
        clearTitle: Boolean = false,
        clearCover: Boolean = false,
        clearArtist: Boolean = false,
        clearAlbum: Boolean = false,
        clearDisplayTitleFormatted: Boolean = false
    ) {
        CustomizationStore.awaitLoaded()
        synchronized(CustomizationStore.lock) {
            val current = CustomizationStore.memory[songId] ?: SongCustomization()

            val newTitle = when {
                clearTitle -> null
                title != null -> title.trim().takeIf { it.isNotBlank() }
                else -> current.title
            }
            val newCover = when {
                clearCover -> null
                coverUri != null -> coverUri.takeIf { it.isNotBlank() }
                else -> current.coverUri
            }
            val newArtist = when {
                clearArtist -> null
                artist != null -> artist.trim().takeIf { it.isNotBlank() }
                else -> current.artist
            }
            val newAlbum = when {
                clearAlbum -> null
                album != null -> album.trim().takeIf { it.isNotBlank() }
                else -> current.album
            }
            val newDisplayTitleFormatted = when {
                clearDisplayTitleFormatted -> null
                displayTitleFormatted != null -> displayTitleFormatted.takeIf { it.isNotBlank() }
                else -> current.displayTitleFormatted
            }

            CustomizationStore.commit(
                songId,
                SongCustomization(title = newTitle, coverUri = newCover, artist = newArtist, album = newAlbum, displayTitleFormatted = newDisplayTitleFormatted)
            )
        }
    }

    /** Devuelve el título con códigos § de una canción, o null si no tiene uno guardado. */
    fun getDisplayTitleFormatted(songId: Long): String? = getCustomization(songId)?.displayTitleFormatted

    /** Aplica la misma carátula personalizada a varias canciones a la vez. */
    fun setCoverForSongs(songIds: List<Long>, coverUri: String?, clearCover: Boolean = false) {
        CustomizationStore.awaitLoaded()
        synchronized(CustomizationStore.lock) {
            songIds.forEach { id ->
                val current = CustomizationStore.memory[id] ?: SongCustomization()
                val newCover = if (clearCover) null else coverUri
                val updated = current.copy(coverUri = newCover)
                // Mismo criterio que antes (se conserva tal cual): con título y carátula vacíos se borra la entrada.
                if (updated.title == null && updated.coverUri == null) {
                    CustomizationStore.commit(id, null)
                } else {
                    CustomizationStore.commit(id, updated)
                }
            }
        }
    }

    /**
     * Aplica el mismo nombre de álbum a varias canciones a la vez (para juntarlas
     * en un solo álbum desde selección múltiple, sin tener que editarlas una por una).
     * No toca la carátula de cada canción; combínalo con [setCoverForSongs] si además
     * quieres que todas compartan una misma carátula para representar el álbum.
     */
    fun setAlbumForSongs(songIds: List<Long>, album: String?, clearAlbum: Boolean = false) {
        CustomizationStore.awaitLoaded()
        synchronized(CustomizationStore.lock) {
            val newAlbum = if (clearAlbum) null else album?.trim()?.takeIf { it.isNotBlank() }
            songIds.forEach { id ->
                val current = CustomizationStore.memory[id] ?: SongCustomization()
                val updated = current.copy(album = newAlbum)
                if (updated.title == null && updated.coverUri == null && updated.artist == null && updated.album == null) {
                    CustomizationStore.commit(id, null)
                } else {
                    CustomizationStore.commit(id, updated)
                }
            }
        }
    }

    /** Elimina toda personalización (nombre y carátula) de una canción. */
    fun clearCustomization(songId: Long) {
        CustomizationStore.awaitLoaded()
        synchronized(CustomizationStore.lock) {
            CustomizationStore.commit(songId, null)
        }
    }
}
