package com.music.musicflame.data

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import com.music.musicflame.data.db.AppDatabase
import com.music.musicflame.data.db.PlaylistEntity
import com.music.musicflame.data.db.PlaylistSongEntity
import com.music.musicflame.data.db.RoomStoreEngine
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import com.music.musicflame.R

data class Playlist(
    val id: String,
    val name: String,
    val songIds: List<Long>,
    val isDefault: Boolean = false,
    val customCoverUri: String? = null
)

/**
 * Distingue de qué "tipo" es una playlist para la UI y navegación:
 * - REGULAR: una playlist normal, creada a mano por el usuario (vive en PlaylistRepository).
 * - FAVORITES: la playlist virtual de Favoritos (vive en FavoritesRepository).
 * - MOST_PLAYED / NEVER_PLAYED: "playlists inteligentes", no se guardan en ningún lado;
 *   se recalculan cada vez a partir de StatsRepository + la biblioteca actual.
 */
enum class PlaylistKind { REGULAR, FAVORITES, MOST_PLAYED, NEVER_PLAYED }

/** IDs fijos (no cambian) que identifican a cada playlist inteligente en la UI. */
object SmartPlaylistIds {
    const val MOST_PLAYED = "smart_most_played"
    const val NEVER_PLAYED = "smart_never_played"
}

private const val MOST_PLAYED_LIMIT = 30

/**
 * "Lo Más Sonado": las canciones con más reproducciones reales (StatsRepository),
 * limitadas a las que siguen existiendo en el dispositivo. No se guarda songIds en
 * ningún lado: se recalcula cada vez que se pide, así que se mantiene sola sin que
 * el usuario tenga que arrastrar canciones a mano.
 */
fun buildMostPlayedPlaylist(context: Context): Playlist {
    val statsRepo = StatsRepository(context)
    val libraryIds = SongLibraryHolder.songs.map { it.id }.toSet()
    val topIds = statsRepo.getTopPlayed(MOST_PLAYED_LIMIT)
        .filter { (id, stat) -> stat.playCount > 0 && id in libraryIds }
        .map { it.first }
    val coverUri = SettingsRepository(context).getMostPlayedCoverUri()
    return Playlist(id = SmartPlaylistIds.MOST_PLAYED, name = context.getString(R.string.pl_most_played), songIds = topIds, isDefault = true, customCoverUri = coverUri)
}

/**
 * "Por Descubrir": canciones de la biblioteca que nunca se han reproducido
 * (sin registro en StatsRepository, o con playCount 0), ordenadas por fecha de
 * agregado (las más nuevas primero). También se recalcula cada vez, nunca hay
 * que agregar canciones a mano.
 */
fun buildNeverPlayedPlaylist(context: Context): Playlist {
    val statsRepo = StatsRepository(context)
    val playedIds = statsRepo.getAllStats().filterValues { it.playCount > 0 }.keys
    val neverPlayedIds = SongLibraryHolder.songs
        .filter { it.id !in playedIds }
        .sortedByDescending { it.dateAdded }
        .map { it.id }
    val coverUri = SettingsRepository(context).getNeverPlayedCoverUri()
    return Playlist(id = SmartPlaylistIds.NEVER_PLAYED, name = context.getString(R.string.pl_never_played), songIds = neverPlayedIds, isDefault = true, customCoverUri = coverUri)
}

/**
 * Estado compartido (memoria + Room) de PlaylistRepository. Ver RoomStoreEngine para el patrón de
 * arranque/migración/escritura. Antes: SharedPreferences "playlists", clave "playlists" (un JSON con
 * todas las playlists). La lista es chica y siempre se escribía completa, así que cada cambio se
 * guarda como una "foto" de todas las playlists (las ráfagas se fusionan en una sola escritura).
 */
private object PlaylistStore : RoomStoreEngine<String, List<Playlist>>(
    tag = "PlaylistStore",
    legacyPrefsName = "playlists",
    legacyKey = "playlists"
) {
    private const val KEY_ALL = "all"

    val lock = Any()

    @Volatile
    var snapshot: List<Playlist> = emptyList()

    private fun dao() = AppDatabase.getInstance(appContext).playlistDao()

    /** Reemplaza la lista completa en memoria y encola su escritura. Llamar con [lock] tomado. */
    fun commit(playlists: List<Playlist>) {
        snapshot = playlists
        enqueue(KEY_ALL, playlists)
    }

    private fun parseLegacy(json: String): List<Playlist> {
        val array = JSONArray(json) // JSONException si está corrupto
        val result = ArrayList<Playlist>()
        val usedIds = HashSet<String>()
        for (i in 0 until array.length()) {
            try {
                val obj = array.getJSONObject(i)
                val songs = obj.optJSONArray("songIds")
                val songIds = ArrayList<Long>()
                if (songs != null) {
                    for (j in 0 until songs.length()) {
                        songIds.add(songs.getLong(j))
                    }
                }
                var id = obj.getString("id")
                // Dos playlists con el mismo id (creadas en el mismo milisegundo) chocarían en la
                // clave primaria: la segunda recibe un id nuevo para no perder ninguna.
                while (!usedIds.add(id)) id += "_dup"
                result.add(
                    Playlist(
                        id = id,
                        name = obj.getString("name"),
                        songIds = songIds,
                        isDefault = obj.optBoolean("isDefault", false),
                        customCoverUri = if (obj.has("customCoverUri")) obj.getString("customCoverUri") else null
                    )
                )
            } catch (e: Exception) {
                Log.w("PlaylistStore", "Se omite una playlist ilegible del JSON viejo (posición $i)", e)
            }
        }
        return result
    }

    private fun toEntities(playlists: List<Playlist>): Pair<List<PlaylistEntity>, List<PlaylistSongEntity>> {
        val pls = playlists.mapIndexed { index, p -> PlaylistEntity(p.id, p.name, p.isDefault, p.customCoverUri, index) }
        val rows = playlists.flatMap { p -> p.songIds.mapIndexed { index, songId -> PlaylistSongEntity(p.id, index, songId) } }
        return pls to rows
    }

    override suspend fun migrateLegacy(json: String): Int {
        val parsed = parseLegacy(json)
        val (pls, rows) = toEntities(parsed)
        dao().migrateAll(pls, rows)
        val plsInRoom = dao().countPlaylists()
        val rowsInRoom = dao().countSongRows()
        check(plsInRoom >= pls.size && rowsInRoom >= rows.size) {
            "verificación falló: Room tiene $plsInRoom playlists/$rowsInRoom canciones y el JSON tenía ${pls.size}/${rows.size}"
        }
        return pls.size
    }

    override suspend fun loadFromRoom() {
        val songsByPlaylist = dao().getSongRows().groupBy({ it.playlistId }, { it.songId })
        snapshot = dao().getPlaylists().map { e ->
            Playlist(
                id = e.id,
                name = e.name,
                songIds = songsByPlaylist[e.id].orEmpty(),
                isDefault = e.isDefault,
                customCoverUri = e.customCoverUri
            )
        }
    }

    override fun loadFromLegacyJson(json: String) {
        snapshot = parseLegacy(json)
    }

    override suspend fun flush(batch: Map<String, List<Playlist>>) {
        val latest = batch[KEY_ALL] ?: return
        val (pls, rows) = toEntities(latest)
        dao().replaceAll(pls, rows)
    }

    override fun serializeLegacy(): String {
        val array = JSONArray()
        snapshot.forEach { playlist ->
            val obj = JSONObject()
            obj.put("id", playlist.id)
            obj.put("name", playlist.name)
            obj.put("isDefault", playlist.isDefault)
            if (playlist.customCoverUri != null) obj.put("customCoverUri", playlist.customCoverUri)
            val songs = JSONArray()
            playlist.songIds.forEach { songs.put(it) }
            obj.put("songIds", songs)
            array.put(obj)
        }
        return array.toString()
    }

    override fun describeMemory(): String = "${snapshot.size} playlists"
}

class PlaylistRepository(context: Context) {
    private val appContext: Context = context.applicationContext

    init {
        PlaylistStore.start(context)
    }

    companion object {
        /** Arranca la carga/migración en segundo plano (llamar desde MainActivity.onCreate). */
        fun preload(context: Context) = PlaylistStore.start(context)
    }

    fun getPlaylists(): List<Playlist> {
        return getAllPlaylists()
    }

    fun getAllPlaylists(): List<Playlist> {
        PlaylistStore.awaitLoaded()
        return PlaylistStore.snapshot
    }

    /** Aplica un cambio a la lista completa de forma atómica (memoria al instante, Room en segundo plano). */
    private fun update(transform: (List<Playlist>) -> List<Playlist>) {
        PlaylistStore.awaitLoaded()
        synchronized(PlaylistStore.lock) {
            PlaylistStore.commit(transform(PlaylistStore.snapshot))
        }
    }

    fun createPlaylist(name: String) {
        update { playlists ->
            playlists + Playlist(
                id = System.currentTimeMillis().toString(),
                name = name,
                songIds = emptyList()
            )
        }
    }

    /** Crea una playlist ya con canciones (la usa el comando /playlist del chat, tras confirmar el usuario). */
    fun createPlaylistWithSongs(name: String, songIds: List<Long>): Playlist {
        PlaylistStore.awaitLoaded()
        synchronized(PlaylistStore.lock) {
            val playlists = PlaylistStore.snapshot
            var id = System.currentTimeMillis()
            while (playlists.any { it.id == id.toString() }) id++
            val created = Playlist(id = id.toString(), name = name.trim().ifBlank { "MusicFlame IA" }, songIds = songIds.distinct())
            PlaylistStore.commit(playlists + created)
            return created
        }
    }

    fun deletePlaylist(id: String) {
        update { playlists -> playlists.filter { it.id != id && !it.isDefault } }
    }

    // Vuelve a insertar una playlist (usado al restaurar desde la papelera), preservando su id, canciones y carátula
    fun restorePlaylist(playlist: Playlist) {
        update { playlists ->
            if (playlists.none { it.id == playlist.id }) playlists + playlist else playlists
        }
    }

    fun addSongToPlaylist(playlistId: String, songId: Long) {
        update { playlists ->
            playlists.map { playlist ->
                if (playlist.id == playlistId && songId !in playlist.songIds) {
                    playlist.copy(songIds = playlist.songIds + songId)
                } else playlist
            }
        }
    }

    // Quita canciones de una playlist (solo de la lista; no toca el archivo ni la papelera).
    fun removeSongsFromPlaylist(playlistId: String, songIds: Set<Long>) {
        if (songIds.isEmpty()) return
        update { playlists ->
            playlists.map { playlist ->
                if (playlist.id == playlistId) {
                    playlist.copy(songIds = playlist.songIds.filter { it !in songIds })
                } else playlist
            }
        }
    }

    // Renombra una playlist creada por el usuario (REGULAR). No aplica a Favoritos
    // ni a las playlists inteligentes (Lo Más Sonado / Por Descubrir), que no viven aquí.
    fun renamePlaylist(playlistId: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        update { playlists ->
            playlists.map { playlist ->
                if (playlist.id == playlistId) playlist.copy(name = trimmed) else playlist
            }
        }
    }

    fun updatePlaylistCover(playlistId: String, coverUri: String) {
        update { playlists ->
            playlists.map { playlist ->
                if (playlist.id == playlistId) {
                    playlist.copy(customCoverUri = if (coverUri.isBlank()) null else coverUri)
                } else playlist
            }
        }
    }

    fun importFromM3U(context: Context, uri: Uri): Playlist? {
        return try {
            val lines = context.contentResolver.openInputStream(uri)
                ?.bufferedReader()
                ?.readLines() ?: return null

            val name = lines.firstOrNull { it.startsWith("#PLAYLIST:") }
                ?.removePrefix("#PLAYLIST:") ?: appContext.getString(R.string.pl_imported_default_name)

            val paths = lines.filter { !it.startsWith("#") && it.isNotBlank() }

            val allSongs = SongLibraryHolder.songs
            val matchedIds = allSongs
                .filter { song -> paths.any { path -> song.path.endsWith(path.trimStart('/')) || song.path == path } }
                .map { it.id }

            val playlist = Playlist(
                id = System.currentTimeMillis().toString(),
                name = name,
                songIds = matchedIds
            )
            update { playlists -> playlists + playlist }
            playlist
        } catch (e: Exception) {
            null
        }
    }

    fun exportToM3U(context: Context, playlist: Playlist): Boolean {
        return try {
            // 1. Obtener todas las canciones del dispositivo para mapear ID -> Path
            val allSongs = SongLibraryHolder.songs
            val songMap = allSongs.associateBy { it.id }

            // 2. Construir el contenido del archivo M3U
            val stringBuilder = StringBuilder()
            stringBuilder.append("#EXTM3U\n")
            stringBuilder.append("#PLAYLIST:${playlist.name}\n")

            playlist.songIds.forEach { id ->
                val song = songMap[id]
                if (song != null) {
                    // Si el objeto song tiene una propiedad 'title', se puede añadir la metaetiqueta opcional
                    // stringBuilder.append("#EXTINF:-1,${song.title}\n")
                    stringBuilder.append("${song.path}\n")
                }
            }

            // 3. Definir la ruta en la carpeta pública de Descargas (Downloads)
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            // Sanitizar el nombre del archivo para evitar caracteres ilegales
            val safeName = playlist.name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            val file = File(downloadsDir, "$safeName.m3u")

            // 4. Escribir el archivo
            file.writeText(stringBuilder.toString())
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}