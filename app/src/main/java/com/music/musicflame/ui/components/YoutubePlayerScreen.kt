package com.music.musicflame.ui.components

import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize

private fun findWebView(view: View): WebView? {
    if (view is WebView) return view
    if (view is ViewGroup) {
        for (i in 0 until view.childCount) {
            val found = findWebView(view.getChildAt(i))
            if (found != null) return found
        }
    }
    return null
}
@Composable
fun YoutubePlayerScreen(videoId: String, modifier: Modifier = Modifier) {
    val lifecycleOwner = LocalLifecycleOwner.current

    // Guardamos referencias para usarlas en LaunchedEffect y DisposableEffect
    var playerViewRef by remember { mutableStateOf<YouTubePlayerView?>(null) }
    var youtubePlayerRef by remember { mutableStateOf<YouTubePlayer?>(null) }
    var playerError by remember { mutableStateOf<String?>(null) }

    // 1. Este efecto reacciona cuando cambia el videoId.
    // En lugar de destruir la vista, simplemente le decimos al reproductor que cargue el nuevo video.
    LaunchedEffect(videoId) {
        playerError = null
        youtubePlayerRef?.loadVideo(videoId, 0f)
    }

    Box(
        // IMPORTANTE: Le damos un tamaño definido. El aspect ratio 16:9 es el estándar de YouTube.
        // Esto evita que el WebView colapse a altura 0 y se quede negro.
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                YouTubePlayerView(ctx).apply {
                    playerViewRef = this
                    lifecycleOwner.lifecycle.addObserver(this)

                    findWebView(this)?.setBackgroundColor(android.graphics.Color.TRANSPARENT)

                    addYouTubePlayerListener(object : AbstractYouTubePlayerListener() {
                        override fun onReady(youTubePlayer: YouTubePlayer) {
                            youtubePlayerRef = youTubePlayer
                            // Arrancamos el primer video
                            youTubePlayer.loadVideo(videoId, 0f)
                        }

                        override fun onError(
                            youTubePlayer: YouTubePlayer,
                            error: PlayerConstants.PlayerError
                        ) {
                            playerError = "videoId='$videoId' -> $error"
                        }
                    })
                }
            }
        )

        // Mostrar el error EN PANTALLA
        if (playerError != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "Error del player: $playerError", color = Color.Red)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            playerViewRef?.let { view ->
                lifecycleOwner.lifecycle.removeObserver(view)
                view.release()
            }
            playerViewRef = null
            youtubePlayerRef = null
        }
    }
}