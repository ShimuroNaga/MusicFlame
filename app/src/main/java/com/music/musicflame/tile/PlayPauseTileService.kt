package com.music.musicflame.tile

import android.content.ComponentName
import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.music.musicflame.R
import com.music.musicflame.data.MusicPlaybackService

/**
 * Tile de acceso rápido: Reanudar/Pausar.
 * Se conecta directo a la MediaSession de MusicPlaybackService (Media3),
 * así que funciona aunque la Activity no esté abierta, igual que la
 * notificación de reproducción.
 */
class PlayPauseTileService : TileService() {

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updateTile(isPlaying)
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
    }

    override fun onClick() {
        super.onClick()
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
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
                updateTile(c.isPlaying)
            }
        }, MoreExecutors.directExecutor())
    }

    private fun updateTile(isPlaying: Boolean) {
        val tile = qsTile ?: return
        // Icono fijo (combina play + pause en un solo símbolo); el estado
        // "activo" del tile (resaltado) es lo que indica si está sonando.
        tile.state = if (isPlaying) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.icon = Icon.createWithResource(this, R.drawable.ic_tile_play_pause)
        tile.label = "Reproducir/Pausar"
        tile.updateTile()
    }
}
