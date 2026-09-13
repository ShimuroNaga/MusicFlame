package com.music.musicflame.tile

import android.content.ComponentName
import android.graphics.drawable.Icon
import android.net.Uri
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.music.musicflame.R
import com.music.musicflame.data.FavoritesRepository
import com.music.musicflame.data.MusicPlaybackService
import com.music.musicflame.data.PlaybackContextTracker
import com.music.musicflame.data.PlaylistKind
import com.music.musicflame.data.PlaylistRepository
import com.music.musicflame.data.Song
import com.music.musicflame.data.SongArtLoader
import com.music.musicflame.data.SongLibraryHolder
import com.music.musicflame.data.buildMostPlayedPlaylist
import com.music.musicflame.data.buildNeverPlayedPlaylist

/**
 * Tile de acceso rápido: Mezclar.
 *
 * Comportamiento:
 * - Si el usuario NO está reproduciendo desde una playlist (canción suelta,
 *   álbum, artista, etc.): se comporta como el botón "Aleatorio" normal,
 *   activando/desactivando shuffleModeEnabled de Media3 (afecta el orden de
 *   las próximas canciones en la cola actual).
 * - Si el usuario SÍ está reproduciendo desde una playlist (detectado vía
 *   PlaybackContextTracker, que MusicPlayerManager actualiza en cada
 *   playSong()): en vez de solo activar el modo aleatorio, remezcla de
 *   verdad esa playlist completa y arranca a reproducirla desde el inicio
 *   mezclado — el mismo efecto que el botón "Mezclar" dentro de
 *   PlaylistDetailScreen, pero disparado desde el panel de ajustes rápidos.
 */
class ShuffleTileService : TileService() {

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null

    // Si el usuario toca el tile antes de que el MediaController termine de
    // conectarse (muy común: recién se abrió el panel de ajustes rápidos),
    // el toque se perdía en silencio -> parecía que el tile "no respondía" y
    // había que tocarlo 2-3 veces hasta que la conexión ya estuviera lista.
    // Ahora se guarda y se ejecuta apenas el controller esté listo.
    private var pendingClick = false

    private val playerListener = object : Player.Listener {
        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            updateTile(shuffleModeEnabled)
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        connectController()
    }

    override fun onStopListening() {
        super.onStopListening()
        controller?.removeListener(playerListener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controller = null
        controllerFuture = null
        pendingClick = false
    }

    override fun onClick() {
        super.onClick()
        val c = controller
        if (c == null) {
            // Todavía conectando al MediaController: no perder el toque.
            pendingClick = true
            return
        }
        performToggle(c)
    }

    // Ejecuta la acción real del tile (reshuffle de playlist activa, o toggle
    // de shuffle suelto) y pinta el tile al instante -> ya no se espera el
    // viaje de ida y vuelta del listener de Media3 para dar feedback visual,
    // por eso ahora responde al primer toque en vez de sentirse "atascado".
    private fun performToggle(c: MediaController) {
        val activePlaylist = PlaybackContextTracker.getActivePlaylist(applicationContext)
        if (activePlaylist != null) {
            val (playlistId, kind) = activePlaylist
            reshufflePlaylist(c, playlistId, kind)
            updateTile(true)
        } else {
            val newShuffleState = !c.shuffleModeEnabled
            c.shuffleModeEnabled = newShuffleState
            updateTile(newShuffleState)
        }
    }

    private fun connectController() {
        val sessionToken = SessionToken(
            applicationContext,
            ComponentName(applicationContext, MusicPlaybackService::class.java)
        )
        val future = MediaController.Builder(applicationContext, sessionToken).buildAsync()
        controllerFuture = future
        future.addListener({
            controller = future.get().also { c ->
                c.addListener(playerListener)
                updateTile(c.shuffleModeEnabled)
                if (pendingClick) {
                    pendingClick = false
                    performToggle(c)
                }
            }
        }, MoreExecutors.directExecutor())
    }

    // Resuelve los songIds reales de la playlist activa según su tipo (normal,
    // favoritos o inteligente), los cruza contra la biblioteca en memoria,
    // los mezcla y reemplaza la cola completa del reproductor con ese orden.
    private fun reshufflePlaylist(controller: MediaController, playlistId: String, kind: PlaylistKind) {
        val songIds: List<Long> = when (kind) {
            PlaylistKind.REGULAR ->
                PlaylistRepository(applicationContext).getAllPlaylists()
                    .find { it.id == playlistId }?.songIds ?: emptyList()
            PlaylistKind.FAVORITES ->
                FavoritesRepository(applicationContext).getAllFavoriteIds().toList()
            PlaylistKind.MOST_PLAYED ->
                buildMostPlayedPlaylist(applicationContext).songIds
            PlaylistKind.NEVER_PLAYED ->
                buildNeverPlayedPlaylist(applicationContext).songIds
        }
        if (songIds.isEmpty()) return

        val idSet = songIds.toSet()
        val songs = SongLibraryHolder.songs.filter { it.id in idSet }
        if (songs.isEmpty()) return

        val shuffled = songs.shuffled()
        val mediaItems = shuffled.map { s -> buildMediaItem(s) }

        controller.setMediaItems(mediaItems, 0, 0)
        controller.prepare()
        controller.play()
    }

    // Réplica liviana de MusicPlayerManager.buildMediaItem() (privado ahí, no
    // accesible desde acá), para no romper la firma de esa clase solo por esto.
    private fun buildMediaItem(s: Song): MediaItem {
        val artUriString = s.albumArtUri?.toString()
        val finalArtworkUri: Uri = when {
            s.hasCustomCover && !artUriString.isNullOrEmpty() -> Uri.parse(artUriString)
            s.path.isNotEmpty() -> SongArtLoader.embeddedArtUri(s.path, artUriString)
            !artUriString.isNullOrEmpty() -> Uri.parse(artUriString)
            else -> Uri.parse("android.resource://$packageName/${R.mipmap.ic_launcher}")
        }

        val metadata = MediaMetadata.Builder()
            .setTitle(s.title)
            .setArtist(s.artist)
            .setArtworkUri(finalArtworkUri)
            .build()

        return MediaItem.Builder()
            .setMediaId(s.id.toString())
            .setUri(s.path)
            .setMediaMetadata(metadata)
            .build()
    }

    private fun updateTile(shuffleModeEnabled: Boolean) {
        val tile = qsTile ?: return
        tile.state = if (shuffleModeEnabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.icon = Icon.createWithResource(this, R.drawable.ic_tile_shuffle)
        tile.label = "Mezclar"
        tile.updateTile()
    }
}