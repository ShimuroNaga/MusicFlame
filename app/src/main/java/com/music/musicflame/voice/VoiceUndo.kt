package com.music.musicflame.voice

import android.content.Context
import android.media.AudioManager
import com.music.musicflame.audio.EqVoice
import com.music.musicflame.data.FavoritesRepository
import com.music.musicflame.data.JarvisStep
import com.music.musicflame.data.JarvisTool
import com.music.musicflame.data.MusicPlayerManager
import com.music.musicflame.data.PlaylistRepository
import com.music.musicflame.data.SongCustomizationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * "Deshaz eso" para la ÚLTIMA acción del asistente. Se captura el estado ANTES de ejecutar y solo se
 * guardan operaciones seguras de revertir. Crear una playlist no se deshace por voz (no hay borrado por voz).
 */
class VoiceUndo(private val ops: List<suspend (MusicPlayerManager?) -> Unit>) {
    val isEmpty: Boolean get() = ops.isEmpty()
    /** [player] = el reproductor del asistente actual (el de la sesión en que se capturó ya pudo cerrarse). */
    suspend fun run(player: MusicPlayerManager?) { ops.asReversed().forEach { it(player) } }

    companion object {
        /** Llamar ANTES de [com.music.musicflame.data.JarvisBrain.execute]. */
        suspend fun capture(ctx: Context, steps: List<JarvisStep>, player: MusicPlayerManager?): VoiceUndo = withContext(Dispatchers.IO) {
            val ops = ArrayList<suspend (MusicPlayerManager?) -> Unit>()
            val favs = FavoritesRepository(ctx)
            val lists = PlaylistRepository(ctx)
            for (s in steps) {
                if (s.problem != null) continue
                when (s.call.tool) {
                    JarvisTool.FAVORITE -> {
                        val changed = s.songs.filter { !favs.isFavorite(it.id) }.map { it.id }
                        if (changed.isNotEmpty()) ops += { _ -> withContext(Dispatchers.IO) { changed.forEach { favs.removeFavorite(it) } } }
                    }
                    JarvisTool.UNFAVORITE -> {
                        val changed = s.songs.filter { favs.isFavorite(it.id) }.map { it.id }
                        if (changed.isNotEmpty()) ops += { _ -> withContext(Dispatchers.IO) { changed.forEach { favs.addFavorite(it) } } }
                    }
                    JarvisTool.ADD_TO_PLAYLIST -> {
                        val pid = s.playlistId ?: continue
                        val has = lists.getAllPlaylists().find { it.id == pid }?.songIds?.toSet() ?: continue
                        val added = s.songs.map { it.id }.filter { it !in has }.toSet()
                        if (added.isNotEmpty()) ops += { _ -> withContext(Dispatchers.IO) { PlaylistRepository(ctx).removeSongsFromPlaylist(pid, added) } }
                    }
                    JarvisTool.REMOVE_FROM_PLAYLIST -> {
                        val pid = s.playlistId ?: continue
                        val has = lists.getAllPlaylists().find { it.id == pid }?.songIds?.toSet() ?: continue
                        val removed = s.songs.map { it.id }.filter { it in has }
                        if (removed.isNotEmpty()) ops += { _ -> withContext(Dispatchers.IO) { removed.forEach { PlaylistRepository(ctx).addSongToPlaylist(pid, it) } } }
                    }
                    JarvisTool.RENAME_PLAYLIST -> {
                        val pid = s.playlistId ?: continue
                        val old = lists.getAllPlaylists().find { it.id == pid }?.name ?: continue
                        ops += { _ -> withContext(Dispatchers.IO) { PlaylistRepository(ctx).renamePlaylist(pid, old) } }
                    }
                    JarvisTool.VOLUME -> {
                        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                        val before = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                        ops += { _ -> am.setStreamVolume(AudioManager.STREAM_MUSIC, before, 0) }
                    }
                    JarvisTool.EQ_PRESET, JarvisTool.EQ_BAND -> {
                        val before = EqVoice.snapshot(ctx)
                        ops += { _ -> withContext(Dispatchers.IO) { EqVoice.apply(ctx, before) } }
                    }
                    JarvisTool.SONG_TITLE, JarvisTool.SONG_COVER -> {
                        val song = s.songs.firstOrNull() ?: continue
                        val prev = SongCustomizationRepository(ctx).getCustomization(song.id)
                        val isTitle = s.call.tool == JarvisTool.SONG_TITLE
                        ops += { _ ->
                            withContext(Dispatchers.IO) {
                                val repo = SongCustomizationRepository(ctx)
                                if (isTitle) repo.setCustomization(song.id, title = prev?.title, clearTitle = prev?.title == null)
                                else repo.setCustomization(song.id, coverUri = prev?.coverUri, clearCover = prev?.coverUri == null)
                            }
                        }
                    }
                    JarvisTool.SLEEP_TIMER -> ops += { _ -> VoiceSleepTimer.cancel() }
                    JarvisTool.REPEAT, JarvisTool.SHUFFLE_MODE -> {
                        val before = player?.let { p -> withContext(Dispatchers.Main) { p.cycleMode.value } }
                        if (before != null) ops += { pl -> pl?.let { p -> withContext(Dispatchers.Main) { p.setCycleMode(before) } } ; Unit }
                    }
                    else -> {}
                }
            }
            VoiceUndo(ops)
        }
    }
}
