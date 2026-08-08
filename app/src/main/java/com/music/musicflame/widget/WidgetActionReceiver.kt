package com.music.musicflame.widget

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.music.musicflame.data.MusicPlaybackService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Resuelve los toques de los botones del widget de home screen.
 * Todas las acciones son de 1 toque, respuesta inmediata (sin trucos de doble toque).
 */
@OptIn(UnstableApi::class)
class WidgetActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_PLAY_PAUSE = "com.music.musicflame.widget.ACTION_PLAY_PAUSE"
        const val ACTION_NEXT_ONLY = "com.music.musicflame.widget.ACTION_NEXT_ONLY"
        const val ACTION_PREVIOUS = "com.music.musicflame.widget.ACTION_PREVIOUS"

        const val REQUEST_CODE_PLAY_PAUSE = 9001
        const val REQUEST_CODE_NEXT_ONLY = 9003
        const val REQUEST_CODE_PREVIOUS = 9004

        private val mainHandler = Handler(Looper.getMainLooper())
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

        private suspend fun connectController(context: Context): MediaController {
            val sessionToken = SessionToken(
                context.applicationContext,
                ComponentName(context.applicationContext, MusicPlaybackService::class.java)
            )
            val controllerFuture = MediaController.Builder(context.applicationContext, sessionToken).buildAsync()
            return suspendCancellableCoroutine { continuation ->
                controllerFuture.addListener(
                    {
                        continuation.resume(controllerFuture.get())
                    },
                    { runnable -> mainHandler.post(runnable) }
                )
            }
        }

        private fun persistStateAfterAction(context: Context, controller: MediaController) {
            val mediaItem = controller.currentMediaItem
            val hasSong = mediaItem != null
            val metadata = mediaItem?.mediaMetadata

            WidgetPrefs.save(
                context = context,
                hasSong = hasSong,
                title = metadata?.title?.toString() ?: "",
                artist = metadata?.artist?.toString() ?: "",
                albumArtUri = metadata?.artworkUri?.toString(),
                isPlaying = controller.isPlaying,
                mediaId = mediaItem?.mediaId
            )
            MusicFlameWidgetProvider.refreshAllWidgets(context)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_PLAY_PAUSE -> handlePlayPause(context)
            ACTION_NEXT_ONLY -> handleSimpleTransportAction(context, goToNext = true)
            ACTION_PREVIOUS -> handleSimpleTransportAction(context, goToNext = false)
        }
    }

    private fun handleSimpleTransportAction(context: Context, goToNext: Boolean) {
        val appContext = context.applicationContext
        val pendingResult = goAsync()
        scope.launch {
            val controller = connectController(appContext)
            try {
                if (goToNext) controller.seekToNext() else controller.seekToPrevious()
                persistStateAfterAction(appContext, controller)
            } finally {
                controller.release()
                pendingResult.finish()
            }
        }
    }

    private fun handlePlayPause(context: Context) {
        val pendingResult = goAsync()
        scope.launch {
            val controller = connectController(context)
            try {
                if (controller.isPlaying) controller.pause() else controller.play()

                // Respuesta visual instantánea del ícono, sin esperar el estado "oficial"
                MusicFlameWidgetProvider.refreshPlayPauseOnly(context, controller.isPlaying)
                persistStateAfterAction(context, controller)
            } finally {
                controller.release()
                pendingResult.finish()
            }
        }
    }
}
