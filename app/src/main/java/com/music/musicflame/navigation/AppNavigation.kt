package com.music.musicflame.navigation

import androidx.annotation.StringRes
import com.music.musicflame.R
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.filled.Settings

sealed class Screen(val route: String, @StringRes val labelRes: Int, val icon: ImageVector) {
    object Songs     : Screen("songs",     R.string.nav_songs, Icons.Filled.Home)
    object Playlists : Screen("playlists", R.string.nav_playlists, Icons.Filled.List)
    object Album     : Screen("album",     R.string.nav_albums,    Icons.Filled.Album)
    object Mix       : Screen("mix",       R.string.nav_mix,    Icons.Filled.Favorite)
    object Trash     : Screen("trash",     R.string.nav_trash,  Icons.Filled.Delete)

    object Settings : Screen("settings", R.string.nav_settings, Icons.Filled.Settings)
}

val bottomNavItems = listOf(
    Screen.Songs,
    Screen.Playlists,
    Screen.Album,
    Screen.Mix,
    Screen.Trash
)