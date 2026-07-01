package com.music.musicflame.data

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.music.musicflame.R

@OptIn(UnstableApi::class)
class MusicPlayerManager(private val context: Context) {

    private var mediaController: MediaController? = null

    // --- MEMORIA INTERNA ---
    // Guardamos la lista actual para poder buscar la canción cuando Media3 avise del cambio
    private var currentPlaylist = listOf<Song>()
    private val playbackHistory = mutableListOf<Int>()

    // --- ESTADOS GLOBALES REACTIVOS (LA FUENTE DE LA VERDAD PARA COMPOSE) ---
    private val _currentSong = mutableStateOf<Song?>(null)
    val currentSong: State<Song?> = _currentSong

    private val _isPlayingState = mutableStateOf(false)
    val isPlayingState: State<Boolean> = _isPlayingState

    private val _cycleMode = mutableIntStateOf(0)
    val cycleMode: State<Int> = _cycleMode

    val cycleIconRes: Int
        get() = when (_cycleMode.intValue) {
            0 -> R.drawable.ic_straight_arrow
            1 -> R.drawable.ic_shuffle
            2 -> R.drawable.ic_autorenew
            3 -> R.drawable.ic_autoplay
            else -> R.drawable.ic_straight_arrow
        }

    init {
        val sessionToken = SessionToken(
            context,
            ComponentName(context, MusicPlaybackService::class.java)
        )

        val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture.addListener({
            val controller = controllerFuture.get()
            mediaController = controller

            // --- EL ESPÍA DE TRANSICIONES Y ESTADOS ---
            controller.addListener(object : Player.Listener {
                private var lastIndex = controller.currentMediaItemIndex

                // ESTA FUNCIÓN BLINDA LA SINCRONIZACIÓN DE LA CANCIÓN
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    val currentIndex = controller.currentMediaItemIndex

                    // Guardar historial si la transición fue automática
                    if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                        if (lastIndex != C.INDEX_UNSET) {
                            playbackHistory.add(lastIndex)
                        }
                    }
                    lastIndex = currentIndex

                    // ACTUALIZAMOS LA UI BASADO EN LO QUE MEDIA3 CARGÓ REALMENTE
                    val currentMediaId = mediaItem?.mediaId
                    _currentSong.value = currentPlaylist.find { it.id.toString() == currentMediaId }
                }

                // ESTA FUNCIÓN BLINDA EL BOTÓN DE PLAY/PAUSA
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _isPlayingState.value = isPlaying
                }
            })

        }, MoreExecutors.directExecutor())
    }

    fun playSong(song: Song, songList: List<Song>) {
        // Guardamos la playlist actual en la memoria del manager
        currentPlaylist = songList

        val mediaItems = songList.map { s ->
            val artUriString = s.albumArtUri?.toString()
            val finalArtworkUri: Uri = if (!artUriString.isNullOrEmpty()) {
                Uri.parse(artUriString)
            } else {
                Uri.parse("android.resource://${context.packageName}/${R.mipmap.ic_launcher}")
            }

            val metadata = MediaMetadata.Builder()
                .setTitle(s.title)
                .setArtist(s.artist)
                .setArtworkUri(finalArtworkUri)
                .build()

            MediaItem.Builder()
                .setMediaId(s.id.toString())
                .setUri(s.path)
                .setMediaMetadata(metadata)
                .build()
        }

        val startIndex = songList.indexOf(song)

        mediaController?.apply {
            val indexToPlay = if (startIndex >= 0) startIndex else 0

            // Borramos el historial porque el usuario eligió una nueva canción manualmente
            playbackHistory.clear()

            setMediaItems(mediaItems, indexToPlay, 0)
            prepare()
            play()
        }
    }

    fun togglePlayPause() {
        mediaController?.let {
            if (it.isPlaying) it.pause() else it.play()
        }
    }

    fun pause() {
        mediaController?.pause()
    }

    // --- AHORA SOLO MANDAN ÓRDENES AL REPRODUCTOR ---
    fun skipNext() {
        mediaController?.let { controller ->
            val currentIndex = controller.currentMediaItemIndex
            if (currentIndex != C.INDEX_UNSET) {
                playbackHistory.add(currentIndex)
            }
            controller.seekToNextMediaItem()
        }
    }

    fun skipPrevious() {
        val controller = mediaController ?: return
        val currentPos = controller.currentPosition

        // REGLA 1: Si pasaron más de 3 segundos, se reinicia la canción
        if (currentPos > 3000) {
            controller.seekTo(0)
        }
        // REGLA 2: Si hay historial, saca la última canción real (Ideal para aleatorio)
        else if (playbackHistory.isNotEmpty()) {
            val previousIndex = playbackHistory.removeAt(playbackHistory.size - 1)
            controller.seekToDefaultPosition(previousIndex)
        }
        // REGLA 3: Reversa natural
        else {
            controller.seekToPreviousMediaItem()
        }
    }

    fun toggleCycleMode() {
        val controller = mediaController ?: return
        val nextMode = (_cycleMode.intValue + 1) % 4
        _cycleMode.intValue = nextMode

        when (nextMode) {
            0 -> {
                controller.shuffleModeEnabled = false
                controller.repeatMode = Player.REPEAT_MODE_OFF
            }
            1 -> {
                controller.shuffleModeEnabled = true
                controller.repeatMode = Player.REPEAT_MODE_OFF
            }
            2 -> {
                controller.shuffleModeEnabled = false
                controller.repeatMode = Player.REPEAT_MODE_ALL
            }
            3 -> {
                controller.shuffleModeEnabled = false
                controller.repeatMode = Player.REPEAT_MODE_ONE
            }
        }
    }

    fun seekTo(positionMs: Long) {
        mediaController?.seekTo(positionMs)
    }

    fun release() {
        mediaController?.release()
        mediaController = null
    }

    val isPlaying: Boolean
        get() = mediaController?.isPlaying == true

    val isShuffleEnabled: Boolean
        get() = mediaController?.shuffleModeEnabled == true

    val currentPosition: Long
        get() = mediaController?.currentPosition ?: 0L

    val duration: Long
        get() = mediaController?.duration ?: 0L
}