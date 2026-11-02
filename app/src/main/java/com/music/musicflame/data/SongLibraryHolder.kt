package com.music.musicflame.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    // Un solo estado con lista + índices derivados (ver LibraryIndex). Leer [songs] o [index]
    // dentro de un @Composable te suscribe a cambios, igual que antes.
    private val state = mutableStateOf(LibraryIndex(emptyList()))
    @Volatile private var hasLoadedOnce = false

    // Generación de MediaStore (Android 11+) del último escaneo. Si no cambió, los refresh
    // "automáticos" (onResume, ContentObserver) se saltan el query completo. -1 = desconocida.
    @Volatile private var lastGeneration = -1L

    // Evita escaneos duplicados: al arrancar, MainActivity, SongScreen, etc. piden
    // la librería casi a la vez; con el mutex solo el primero escanea de verdad y
    // los demás (ensureLoaded) ven hasLoadedOnce=true y salen sin tocar MediaStore.
    private val scanMutex = Mutex()

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingAutoRefresh: Runnable? = null
    private var observerRegistered = false

    /** Snapshot actual de la librería. Leerlo dentro de un @Composable te suscribe a cambios. */
    val songs: List<Song>
        get() = state.value.songs

    /** Índices ya calculados (byId, byPath, álbumes, artistas, géneros). */
    val index: LibraryIndex
        get() = state.value

    /** Búsqueda O(1) por id (antes: songs.find { it.id == ... } lineal). */
    fun findById(id: Long): Song? = state.value.byId[id]

    /** Búsqueda O(1) por ruta de archivo. */
    fun findByPath(path: String): Song? = state.value.byPath[path]

    /** Re-escanea MediaStore en Dispatchers.IO y actualiza [songs]. Llamar tras cualquier cambio real. */
    suspend fun refresh(context: Context) {
        val appContext = context.applicationContext
        scanMutex.withLock { scan(appContext) }
    }

    // Refresh "barato": si MediaStore no registró ningún cambio desde el último escaneo, no hace
    // nada. Los cambios hechos DENTRO de la app (filtros, personalizaciones, papelera) siguen
    // usando refresh() que siempre re-escanea.
    private suspend fun refreshIfMediaStoreChanged(context: Context) {
        val appContext = context.applicationContext
        scanMutex.withLock {
            val gen = currentMediaStoreGeneration(appContext)
            if (gen != -1L && gen == lastGeneration) return@withLock
            scan(appContext)
        }
    }

    private fun currentMediaStoreGeneration(context: Context): Long = try {
        MediaStore.getGeneration(context, MediaStore.VOLUME_EXTERNAL_PRIMARY)
    } catch (e: Throwable) {
        -1L
    }

    /** Carga solo si nunca se ha cargado en esta sesión de la app; no fuerza un re-escaneo. */
    suspend fun ensureLoaded(context: Context) {
        if (hasLoadedOnce) return
        val appContext = context.applicationContext
        scanMutex.withLock { if (!hasLoadedOnce) scan(appContext) }
    }

    /**
     * Para onResume de la Activity: si todavía no se cargó nada, carga; si ya
     * había una carga, la refresca (cubre canciones agregadas con la app en
     * segundo plano y el caso de recién concedido el permiso de audio).
     * No hace nada si el permiso de audio aún no está concedido.
     */
    suspend fun refreshIfPermitted(context: Context) {
        if (!hasAudioPermission(context)) return
        if (hasLoadedOnce) refreshIfMediaStoreChanged(context) else ensureLoaded(context)
    }

    /**
     * Registra (una sola vez) un ContentObserver sobre MediaStore.Audio: cuando
     * Android detecta una canción nueva/borrada/modificada, la librería se
     * refresca sola (con un pequeño debounce porque MediaStore dispara varias
     * notificaciones seguidas al copiar un archivo).
     */
    fun start(context: Context) {
        if (observerRegistered) return
        val appContext = context.applicationContext
        appContext.contentResolver.registerContentObserver(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            true,
            object : ContentObserver(mainHandler) {
                override fun onChange(selfChange: Boolean) {
                    scheduleAutoRefresh(appContext)
                }
            }
        )
        observerRegistered = true
    }

    private fun scheduleAutoRefresh(appContext: Context) {
        pendingAutoRefresh?.let { mainHandler.removeCallbacks(it) }
        val r = Runnable {
            appScope.launch { refreshIfPermitted(appContext) }
        }
        pendingAutoRefresh = r
        mainHandler.postDelayed(r, 600L)
    }

    private fun hasAudioPermission(context: Context): Boolean {
        val perm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE
        return ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
    }

    // Debe llamarse con scanMutex tomado.
    private suspend fun scan(appContext: Context) {
        // Se lee ANTES del query: si MediaStore cambia mientras escaneamos, la próxima
        // comparación verá otra generación y volverá a escanear (nunca nos saltamos un cambio).
        val generation = currentMediaStoreGeneration(appContext)

        // UNA sola pasada a MediaStore (género incluido, ver SongRepository), en IO.
        val loaded = withContext(Dispatchers.IO) {
            loadSongsFromDevice(appContext, includeGenres = true)
        }
        lastGeneration = generation
        hasLoadedOnce = true

        // Si el contenido es idéntico no se publica nada: ninguna pantalla se recompone y los
        // índices ya calculados se reutilizan.
        if (loaded == state.value.songs) return

        val idx = LibraryIndex(loaded)
        state.value = idx

        // Calcula byId/byPath/álbumes/artistas/géneros fuera del hilo principal.
        appScope.launch(Dispatchers.Default) { idx.warmUp() }
    }
}
