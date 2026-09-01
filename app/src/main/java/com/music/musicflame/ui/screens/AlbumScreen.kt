package com.music.musicflame.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.music.musicflame.AlbumArtShapeType
import com.music.musicflame.LocalAlbumGridColumns
import com.music.musicflame.data.Album
import com.music.musicflame.data.Artist
import com.music.musicflame.data.Genre
import com.music.musicflame.data.Song
import com.music.musicflame.data.SongLibraryHolder
import com.music.musicflame.data.groupSongsIntoAlbums
import com.music.musicflame.data.groupSongsIntoArtists
import com.music.musicflame.data.groupSongsIntoGenres
import com.music.musicflame.ui.components.AlbumArt
import com.music.musicflame.ui.theme.LocalAppTextColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// NUEVO: pestañas de biblioteca. Antes esta pantalla SOLO mostraba álbumes;
// ahora es el contenedor de las 3 formas de navegar la librería (Álbumes /
// Artistas / Géneros), elegidas con un SegmentedButton arriba de la grilla.
// A propósito viven las 3 en esta misma pantalla/tab (en vez de 2 pestañas
// nuevas en la barra inferior) para no saturar la bottom nav, que ya tenía 5
// ítems.
private enum class LibraryTab { ALBUMS, ARTISTS, GENRES }

@Composable
fun AlbumScreen(
    modifier: Modifier = Modifier,
    hasBackgroundImage: Boolean = false,
    selectedAlbum: Album? = null,
    onAlbumClick: (Album) -> Unit = {},
    // NUEVO: mismo patrón que selectedAlbum/onAlbumClick, para Artist y Genre.
    selectedArtist: Artist? = null,
    onArtistClick: (Artist) -> Unit = {},
    selectedGenre: Genre? = null,
    onGenreClick: (Genre) -> Unit = {},
    onPlaySong: (Song, List<Song>) -> Unit = { _, _ -> },
    selectedSongs: List<Song> = emptyList(),
    onToggleSelection: (Song) -> Unit = {},
    selectionModeActive: Boolean = false,
    onToggleSelectionModeButton: () -> Unit = {},
    // NUEVO: id de la canción sonando ahora, para el icono al lado del título.
    currentPlayingSongId: Long? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { SongLibraryHolder.ensureLoaded(context) }
    val allSongs = SongLibraryHolder.songs
    val albums = remember(allSongs) { groupSongsIntoAlbums(allSongs) }
    val artists = remember(allSongs) { groupSongsIntoArtists(allSongs) }
    val genres = remember(allSongs) { groupSongsIntoGenres(allSongs) }

    var libraryTab by remember { mutableStateOf(LibraryTab.ALBUMS) }

    // Pull-to-refresh: mismo patrón ya usado acá (SongLibraryHolder.refresh
    // re-escanea MediaStore de verdad); sirve para las 3 pestañas porque las
    // 3 se recalculan solas vía remember(allSongs) apenas cambia la librería.
    val isRefreshing = remember { mutableStateOf(false) }
    val pullState = rememberPullToRefreshState()

    Box(modifier = modifier.fillMaxSize()) {
        when {
            selectedAlbum != null -> AlbumDetailScreen(
                album = selectedAlbum,
                onSongClick = { song, list -> onPlaySong(song, list) },
                hasBackgroundImage = hasBackgroundImage,
                selectedSongs = selectedSongs,
                onToggleSelection = onToggleSelection,
                selectionModeActive = selectionModeActive,
                onToggleSelectionModeButton = onToggleSelectionModeButton,
                currentPlayingSongId = currentPlayingSongId
            )
            selectedArtist != null -> ArtistDetailScreen(
                artist = selectedArtist,
                onSongClick = { song, list -> onPlaySong(song, list) },
                hasBackgroundImage = hasBackgroundImage,
                selectedSongs = selectedSongs,
                onToggleSelection = onToggleSelection,
                selectionModeActive = selectionModeActive,
                onToggleSelectionModeButton = onToggleSelectionModeButton,
                currentPlayingSongId = currentPlayingSongId
            )
            selectedGenre != null -> GenreDetailScreen(
                genre = selectedGenre,
                onSongClick = { song, list -> onPlaySong(song, list) },
                hasBackgroundImage = hasBackgroundImage,
                selectedSongs = selectedSongs,
                onToggleSelection = onToggleSelection,
                selectionModeActive = selectionModeActive,
                onToggleSelectionModeButton = onToggleSelectionModeButton,
                currentPlayingSongId = currentPlayingSongId
            )
            else -> Column(modifier = Modifier.fillMaxSize()) {
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    SegmentedButton(
                        selected = libraryTab == LibraryTab.ALBUMS,
                        onClick = { libraryTab = LibraryTab.ALBUMS },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                        icon = {}
                    ) { Text("Álbumes") }
                    SegmentedButton(
                        selected = libraryTab == LibraryTab.ARTISTS,
                        onClick = { libraryTab = LibraryTab.ARTISTS },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                        icon = {}
                    ) { Text("Artistas") }
                    SegmentedButton(
                        selected = libraryTab == LibraryTab.GENRES,
                        onClick = { libraryTab = LibraryTab.GENRES },
                        shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                        icon = {}
                    ) { Text("Géneros") }
                }

                PullToRefreshBox(
                    isRefreshing = isRefreshing.value,
                    onRefresh = {
                        scope.launch {
                            isRefreshing.value = true
                            delay(800)
                            SongLibraryHolder.refresh(context)
                            isRefreshing.value = false
                        }
                    },
                    state = pullState,
                    modifier = Modifier.fillMaxSize(),
                    indicator = {
                        PullToRefreshDefaults.Indicator(
                            state = pullState,
                            isRefreshing = isRefreshing.value,
                            color = MaterialTheme.colorScheme.primary,
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.align(Alignment.TopCenter)
                        )
                    }
                ) {
                    when (libraryTab) {
                        LibraryTab.ALBUMS -> AlbumGrid(albums = albums, hasBackgroundImage = hasBackgroundImage, onAlbumClick = onAlbumClick)
                        LibraryTab.ARTISTS -> ArtistGrid(artists = artists, onArtistClick = onArtistClick)
                        LibraryTab.GENRES -> GenreList(genres = genres, onGenreClick = onGenreClick)
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumGrid(albums: List<Album>, hasBackgroundImage: Boolean, onAlbumClick: (Album) -> Unit) {
    val columns = LocalAlbumGridColumns.current

    if (albums.isEmpty()) {
        EmptyLibraryState(icon = Icons.Filled.Album, message = "No se encontraron álbumes")
        return
    }

    val configuration = LocalConfiguration.current
    val gridPadding = 16.dp
    val itemSpacing = 16.dp
    val itemSize = (configuration.screenWidthDp.dp - gridPadding * 2 - itemSpacing * (columns - 1)) / columns

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        contentPadding = PaddingValues(gridPadding),
        horizontalArrangement = Arrangement.spacedBy(itemSpacing),
        verticalArrangement = Arrangement.spacedBy(itemSpacing),
        modifier = Modifier.fillMaxSize()
    ) {
        items(albums, key = { it.name + it.artist }) { album ->
            AlbumCard(album = album, artSize = itemSize, hasBackgroundImage = hasBackgroundImage, onClick = { onAlbumClick(album) })
        }
    }
}

@Composable
private fun AlbumCard(album: Album, artSize: androidx.compose.ui.unit.Dp, hasBackgroundImage: Boolean, onClick: () -> Unit) {
    val titleColor = LocalAppTextColor.current
    val subtitleColor = LocalAppTextColor.current.copy(alpha = 0.7f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            contentAlignment = Alignment.Center
        ) {
            AlbumArt(
                albumArtUri = album.albumArtUri,
                size = artSize,
                cornerRadius = 12.dp,
                filePath = album.albumArtSourcePath,
                isCustomCover = album.albumArtIsCustom
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            album.name,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = titleColor
        )
        Text(
            "${album.artist} · ${album.songCount} canciones",
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = subtitleColor,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

// NUEVO: grilla de artistas, mismo layout que AlbumGrid/AlbumCard pero con
// carátula circular (para diferenciar "persona" de "álbum" de un vistazo) y
// subtítulo de álbumes+canciones en vez de artista+canciones.
@Composable
private fun ArtistGrid(artists: List<Artist>, onArtistClick: (Artist) -> Unit) {
    val columns = LocalAlbumGridColumns.current

    if (artists.isEmpty()) {
        EmptyLibraryState(icon = Icons.Filled.Person, message = "No se encontraron artistas")
        return
    }

    val configuration = LocalConfiguration.current
    val gridPadding = 16.dp
    val itemSpacing = 16.dp
    val itemSize = (configuration.screenWidthDp.dp - gridPadding * 2 - itemSpacing * (columns - 1)) / columns

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        contentPadding = PaddingValues(gridPadding),
        horizontalArrangement = Arrangement.spacedBy(itemSpacing),
        verticalArrangement = Arrangement.spacedBy(itemSpacing),
        modifier = Modifier.fillMaxSize()
    ) {
        items(artists, key = { it.name }) { artist ->
            ArtistCard(artist = artist, artSize = itemSize, onClick = { onArtistClick(artist) })
        }
    }
}

@Composable
private fun ArtistCard(artist: Artist, artSize: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    val titleColor = LocalAppTextColor.current
    val subtitleColor = LocalAppTextColor.current.copy(alpha = 0.7f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            contentAlignment = Alignment.Center
        ) {
            AlbumArt(
                albumArtUri = artist.albumArtUri,
                size = artSize,
                cornerRadius = artSize / 2,
                shape = AlbumArtShapeType.CIRCLE,
                filePath = artist.albumArtSourcePath,
                isCustomCover = artist.albumArtIsCustom
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            artist.name,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = titleColor,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Text(
            "${artist.albumCount} álbumes · ${artist.songCount} canciones",
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = subtitleColor,
            style = MaterialTheme.typography.bodySmall,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

// NUEVO: los géneros se muestran como lista (no grilla), igual que en
// Musicolet — no tienen una carátula representativa natural y normalmente
// son pocos, así que una lista compacta con contador de canciones alcanza.
@Composable
private fun GenreList(genres: List<Genre>, onGenreClick: (Genre) -> Unit) {
    if (genres.isEmpty()) {
        EmptyLibraryState(icon = Icons.Filled.Category, message = "No se encontraron géneros")
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(genres, key = { it.name }) { genre ->
            GenreRow(genre = genre, onClick = { onGenreClick(genre) })
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun GenreRow(genre: Genre, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.Category,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp)
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                genre.name,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = LocalAppTextColor.current
            )
            Text(
                "${genre.songCount} canciones",
                color = LocalAppTextColor.current.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodySmall
            )
        }
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = LocalAppTextColor.current.copy(alpha = 0.4f)
        )
    }
}

@Composable
private fun EmptyLibraryState(icon: androidx.compose.ui.graphics.vector.ImageVector, message: String) {
    val emptyTextColor = LocalAppTextColor.current.copy(alpha = 0.6f)
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = emptyTextColor
            )
            Spacer(Modifier.height(8.dp))
            Text(message, color = LocalAppTextColor.current)
        }
    }
}