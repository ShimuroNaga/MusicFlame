package com.music.musicflame.data

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Cache en memoria, compartido por toda la app, de la librería completa de
 * canciones del dispositivo (MediaStore).
 *
 * ANTES: cada pantalla (AlbumScreen, SongScreen, PlaylistsScreen, QueueScreen,
 * MixScreen, PlaylistDetailScreen, MainActivity, PlaylistRepository...)
 * llamaba a loadSongsFromDevice(context) directo, muchas veces dentro de
 * remember{} o como val suelto en medio de la composición. Esa función hace
 * un query completo a MediaStore.Audio.Media + otro query aparte para el mapa
 * de géneros + lee JSON de personalizaciones y los filtros de duración desde
 * SharedPreferences — todo de forma SÍNCRONA en el hilo principal. Como se
 * llamaba en más de 15 lugares distintos, cada vez que el usuario entraba a
 * una de esas pantallas la app volvía a escanear TODA la librería del
 * dispositivo desde cero, lo cual se sentía como micro-freezes con librerías
 * grandes.
 *
 * AHORA: el resultado vive en un solo estado compartido (mismo patrón que
 * ProStatusHolder). refresh() hace el trabajo pesado en Dispatchers.IO una
 * sola vez y actualiza [songs]; cualquier composable que lea [songs] se
 * recompone automáticamente cuando cambia, sin volver a tocar MediaStore.
 *
 * Cuándo llamar a refresh(context) (desde una coroutine: LaunchedEffect,
 * scope.launch, etc.):
 *  - Al arrancar la app (MainActivity ya lo hace vía ensureLoaded).
 *  - Después de mandar canciones a la papelera o restaurarlas (TrashScreen,
 *    MainActivity).
 *  - Después de guardar una personalización de carátula/título
 *    (EditSongDialog vía SongCustomizationRepository).
 *  - Después de cambiar los filtros de duración en Ajustes (SettingsScreen,
 *    OnboardingSongsStep).
 */
object SongLibraryHolder {
    private val state = mutableStateOf<List<Song>>(emptyList())
    private var hasLoadedOnce = false

    /** Snapshot actual de la librería. Leerlo dentro de un @Composable te suscribe a cambios. */
    val songs: List<Song>
        get() = state.value

    /** Re-escanea MediaStore en Dispatchers.IO y actualiza [songs]. Llamar tras cualquier cambio real. */
    suspend fun refresh(context: Context) {
        val appContext = context.applicationContext
        val loaded = withContext(Dispatchers.IO) {
            loadSongsFromDevice(appContext)
        }
        state.value = loaded
        hasLoadedOnce = true
    }

    /** Carga solo si nunca se ha cargado en esta sesión de la app; no fuerza un re-escaneo. */
    suspend fun ensureLoaded(context: Context) {
        if (!hasLoadedOnce) refresh(context)
    }
}
