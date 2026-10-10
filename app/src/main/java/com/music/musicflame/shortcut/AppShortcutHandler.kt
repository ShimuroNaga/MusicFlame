package com.music.musicflame.shortcut

import android.content.Context
import com.music.musicflame.R
import android.widget.Toast
import com.music.musicflame.data.FavoritesRepository
import com.music.musicflame.data.MusicPlayerManager
import com.music.musicflame.data.PlaylistKind
import com.music.musicflame.data.SongLibraryHolder
import com.music.musicflame.data.StatsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Acciones que lanzan los atajos de app (res/xml/shortcuts.xml). */
object AppShortcutActions {
    const val RESUME_LAST = "com.music.musicflame.action.SHORTCUT_RESUME_LAST"
    const val SHUFFLE_ALL = "com.music.musicflame.action.SHORTCUT_SHUFFLE_ALL"
    const val PLAY_FAVORITES = "com.music.musicflame.action.SHORTCUT_PLAY_FAVORITES"

    fun isShortcutAction(action: String?): Boolean =
        action == RESUME_LAST || action == SHUFFLE_ALL || action == PLAY_FAVORITES
}

/**
 * Ejecuta lo que pide un atajo de app (menú de pulsación larga del ícono).
 * Se llama desde MainActivity (onCreate / onNewIntent) dentro de lifecycleScope,
 * así que corre en el hilo principal (los Toast y playSong lo necesitan).
 */
object AppShortcutHandler {

    suspend fun handle(context: Context, action: String, playerManager: MusicPlayerManager) {
        // Si la app estaba cerrada, la biblioteca todavía no está cargada.
        SongLibraryHolder.ensureLoaded(context)
        val library = SongLibraryHolder.songs
        if (library.isEmpty()) {
            toast(context, context.getString(R.string.shortcut_no_songs))
            return
        }

        when (action) {
            AppShortcutActions.RESUME_LAST -> {
                val stats = withContext(Dispatchers.IO) { StatsRepository(context).getAllStats() }
                val byId = library.associateBy { it.id }
                val last = stats.entries
                    .filter { it.value.lastPlayedAt > 0L && byId.containsKey(it.key) }
                    .maxByOrNull { it.value.lastPlayedAt }
                    ?.let { byId[it.key] }
                if (last == null) {
                    toast(context, context.getString(R.string.shortcut_none_played))
                } else {
                    playerManager.whenReady { playerManager.playSong(last, library) }
                }
            }

            AppShortcutActions.SHUFFLE_ALL -> {
                val shuffled = library.shuffled()
                playerManager.whenReady { playerManager.playSong(shuffled.first(), shuffled) }
            }

            AppShortcutActions.PLAY_FAVORITES -> {
                val ids = withContext(Dispatchers.IO) { FavoritesRepository(context).getAllFavoriteIds() }
                val favorites = library.filter { it.id in ids }
                if (favorites.isEmpty()) {
                    toast(context, context.getString(R.string.shortcut_no_favorites))
                } else {
                    // "favorites" + FAVORITES = mismo id que usa la pantalla de Playlists, para que
                    // el tile de Mezclar sepa que se está reproduciendo esa playlist.
                    playerManager.whenReady {
                        playerManager.playSong(favorites.first(), favorites, "favorites", PlaylistKind.FAVORITES)
                    }
                }
            }
        }
    }

    private fun toast(context: Context, msg: String) =
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
}
