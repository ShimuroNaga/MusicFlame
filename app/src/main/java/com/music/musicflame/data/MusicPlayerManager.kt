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
    private var currentPlaylist = listOf<Song>()
    private val playbackHistory = mutableListOf<Int>()

    // --- ESTADOS GLOBALES REACTIVOS ---
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

            // Sincronizar el estado inicial al conectar
            syncCycleModeState(controller)

            // NUEVO: sincroniza la canción actual y si está reproduciendo AHORA MISMO.
            // Es clave cuando el servicio ya venía reproduciendo música en segundo plano
            // (p.ej. cerraste la app desde "recientes" y la vuelves a abrir): sin esto,
            // el mini-reproductor aparecía vacío hasta la siguiente canción.
            syncCurrentSongState(controller)
            _isPlayingState.value = controller.isPlaying

            controller.addListener(object : Player.Listener {
                private var lastIndex = controller.currentMediaItemIndex

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    val currentIndex = controller.currentMediaItemIndex
                    if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                        if (lastIndex != C.INDEX_UNSET) {
                            playbackHistory.add(lastIndex)
                        }
                    }
                    lastIndex = currentIndex

                    syncCurrentSongState(controller)
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _isPlayingState.value = isPlaying
                }

                // NUEVO: Escuchamos cambios desde la notificación para actualizar la UI en vivo
                override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                    syncCycleModeState(controller)
                }

                override fun onRepeatModeChanged(repeatMode: Int) {
                    syncCycleModeState(controller)
                }
            })

        }, MoreExecutors.directExecutor())
    }

    // NUEVO: Método que averigua el estado actual real de Media3 y actualiza el icono
    private fun syncCycleModeState(controller: Player) {
        if (controller.shuffleModeEnabled) {
            _cycleMode.intValue = 1 // Aleatorio
        } else {
            when (controller.repeatMode) {
                Player.REPEAT_MODE_ALL -> _cycleMode.intValue = 2 // Repetir todas
                Player.REPEAT_MODE_ONE -> _cycleMode.intValue = 3 // Repetir una
                else -> _cycleMode.intValue = 0 // Normal (Flecha derecha)
            }
        }
    }

    // NUEVO: Averigua qué canción está sonando ahora en el reproductor y actualiza el estado reactivo.
    // Si el MediaItem no está en nuestra playlist en memoria (por ejemplo, porque la Activity se
    // recreó y perdió la lista al ser cerrada desde "recientes"), reconstruye un Song "ligero" a
    // partir de los metadatos que ya guarda ExoPlayer, para que el mini-reproductor no quede vacío.
    private fun syncCurrentSongState(controller: Player) {
        val mediaItem = controller.currentMediaItem
        if (mediaItem == null) {
            _currentSong.value = null
            return
        }

        val mediaId = mediaItem.mediaId
        val songFromPlaylist = currentPlaylist.find { it.id.toString() == mediaId }
        _currentSong.value = songFromPlaylist ?: buildSongFromMediaItem(mediaItem)
    }

    private fun buildSongFromMediaItem(mediaItem: MediaItem): Song {
        val metadata = mediaItem.mediaMetadata
        return Song(
            id = mediaItem.mediaId.toLongOrNull() ?: 0L,
            title = metadata.title?.toString() ?: "Desconocido",
            artist = metadata.artist?.toString() ?: "",
            duration = 0L, // La UI usa playerManager.duration en vivo, no este campo
            path = mediaItem.localConfiguration?.uri?.toString() ?: "",
            albumArtUri = metadata.artworkUri?.toString()
        )
    }

    fun playSong(song: Song, songList: List<Song>) {
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
            playbackHistory.clear()
            setMediaItems(mediaItems, indexToPlay, 0)
            prepare()
            play()
        }
    }

    fun togglePlayPause() {
        mediaController?.let { if (it.isPlaying) it.pause() else it.play() }
    }

    fun pause() { mediaController?.pause() }

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
        if (currentPos > 3000) {
            controller.seekTo(0)
        } else if (playbackHistory.isNotEmpty()) {
            val previousIndex = playbackHistory.removeAt(playbackHistory.size - 1)
            controller.seekToDefaultPosition(previousIndex)
        } else {
            controller.seekToPreviousMediaItem()
        }
    }

    // El comportamiento desde la UI ahora está sincronizado
    fun toggleCycleMode() {
        val controller = mediaController ?: return
        val nextMode = (_cycleMode.intValue + 1) % 4

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

    fun seekTo(positionMs: Long) { mediaController?.seekTo(positionMs) }
    fun release() { mediaController?.release(); mediaController = null }

    val isPlaying: Boolean get() = mediaController?.isPlaying == true
    val isShuffleEnabled: Boolean get() = mediaController?.shuffleModeEnabled == true
    val currentPosition: Long get() = mediaController?.currentPosition ?: 0L
    val duration: Long get() = mediaController?.duration ?: 0L
}