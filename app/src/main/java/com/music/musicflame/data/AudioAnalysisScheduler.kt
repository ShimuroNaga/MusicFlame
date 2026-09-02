package com.music.musicflame.data

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Analiza en segundo plano el tempo y energía reales de toda la biblioteca
 * (ver AudioFeatureExtractor), cacheando el resultado en
 * AudioFeaturesCacheRepository para no repetir el trabajo pesado.
 *
 * Mismo patrón de singleton "object" que SongLibraryHolder/ProStatusHolder.
 * Se dispara una vez al arrancar (ver MainActivity, justo después de
 * SongLibraryHolder.ensureLoaded), y cualquier pantalla puede leer
 * [analyzedCount]/[totalCount]/[isRunning] para mostrar progreso si se
 * quiere (ej. "Analizando tu música: 340/1200").
 *
 * CONCURRENCIA: procesa hasta MAX_CONCURRENT canciones EN PARALELO (con un
 * Semaphore, no lanzando las 500 de una), en vez de una por una con un delay
 * fijo entre cada una como la primera versión. Con una biblioteca de ~500
 * canciones esto es la diferencia entre ~15-20 minutos y ~3-5 minutos. No se
 * sube más alto que MAX_CONCURRENT porque decodificar audio ya usa varios
 * hilos internos por su cuenta (el decoder de MediaCodec), y algunos
 * dispositivos limitan cuántas instancias de decoder pueden vivir a la vez;
 * 2 es un número conservador que anda bien en la gran mayoría de teléfonos
 * sin saturar CPU mientras el usuario sigue usando la app normalmente.
 *
 * A propósito NO usa WorkManager (el proyecto no lo tenía como dependencia,
 * no hacía falta sumarlo para esto): corre en su propio
 * CoroutineScope(Dispatchers.IO).
 */
object AudioAnalysisScheduler {
    private const val MAX_CONCURRENT = 2

    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _analyzed = mutableIntStateOf(0)
    private val _total = mutableIntStateOf(0)
    private val _running = mutableStateOf(false)

    /** Cuántas canciones ya tienen análisis (cacheadas de antes + analizadas en esta corrida). */
    val analyzedCount: Int get() = _analyzed.intValue

    /** Tamaño total de la biblioteca al momento de arrancar esta corrida. */
    val totalCount: Int get() = _total.intValue

    /** true mientras el scheduler está analizando canciones activamente. */
    val isRunning: Boolean get() = _running.value

    /**
     * Arranca el análisis en segundo plano si no está corriendo ya. Si ya se
     * había corrido antes y no hay canciones nuevas, termina casi al toque
     * (todo sale del caché). Seguro de llamar varias veces (ej. cada vez que
     * se refresca la biblioteca): no vuelve a lanzar un job si ya hay uno activo.
     */
    fun start(context: Context) {
        if (job?.isActive == true) return
        val appContext = context.applicationContext
        job = scope.launch {
            _running.value = true
            try {
                val cache = AudioFeaturesCacheRepository(appContext)
                val songs = SongLibraryHolder.songs
                val pending = songs.filter { !cache.has(it.path) }

                _total.intValue = songs.size
                val doneCounter = AtomicInteger(songs.size - pending.size)
                _analyzed.intValue = doneCounter.get()

                val semaphore = Semaphore(MAX_CONCURRENT)
                pending.map { song ->
                    async {
                        semaphore.withPermit {
                            val features = AudioFeatureExtractor.extract(song.path)
                            // Si falló el análisis, se guarda un resultado "vacío"
                            // igual (bpm=0, energy=0) para no reintentar esta misma
                            // canción en cada arranque de la app — se puede filtrar
                            // por bpm>0 más adelante al armar las mezclas.
                            cache.set(song.path, features ?: AudioFeatures(bpm = 0f, energy = 0f))
                            _analyzed.intValue = doneCounter.incrementAndGet()
                        }
                    }
                }.awaitAll()
            } finally {
                _running.value = false
            }
        }
    }
}
