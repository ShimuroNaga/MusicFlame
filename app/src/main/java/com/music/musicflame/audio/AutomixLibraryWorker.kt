package com.music.musicflame.audio

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.music.musicflame.data.AutomixCacheRepository
import com.music.musicflame.data.AutomixData
import com.music.musicflame.data.SettingsStore
import com.music.musicflame.data.SongLibraryHolder
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * AUTOMIX: pre-analiza (BPM / tonalidad / rejilla de beats) las canciones de la biblioteca que aún no
 * están en el caché, SOLO mientras el celular carga y la batería no está baja (ver [AutomixLibraryScheduler]).
 *
 * Usa exactamente la misma función que la reproducción y el botón de Ajustes ([AutomixAnalyzer.analyze]),
 * así que comparte su Mutex (de a una canción), su prioridad de fondo y el mismo caché. Cada resultado
 * se guarda al terminar la canción: si WorkManager detiene el trabajo (se desenchufa, el sistema lo
 * pausa o se acaba el tiempo) no se pierde nada y la siguiente ejecución continúa donde quedó.
 */
class AutomixLibraryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private companion object {
        const val TAG = "AutomixLibraryWorker"

        /** JobScheduler corta cada ejecución a los 10 min: se termina antes y se reencola con retry(). */
        const val MAX_RUN_MS = 8 * 60 * 1000L

        /** Sin modelo y sin red: tras estos intentos se espera al siguiente periodo en vez de reintentar sin fin. */
        const val MAX_MODEL_ATTEMPTS = 4
    }

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        if (!SettingsStore.get(ctx).getBoolean("automix_enabled", false)) return Result.success()
        // Si el usuario ya lanzó "Analizar biblioteca completa" a mano, ese proceso hace el mismo trabajo.
        if (AutomixLibraryAnalysis.running.value) return Result.success()

        try {
            SongLibraryHolder.refreshIfPermitted(ctx) // sin permiso de audio no hace nada: la lista queda vacía
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Log.w(TAG, "No se pudo cargar la biblioteca", e)
            return Result.success()
        }

        val repo = AutomixCacheRepository(ctx)
        val todo = SongLibraryHolder.songs.map { it.path }.filter { it.isNotEmpty() && !repo.has(it) }
        if (todo.isEmpty()) return Result.success()

        if (!AutomixModelManager.isReady(ctx) && AutomixModelManager.ensureModel(ctx) == null) {
            return if (runAttemptCount >= MAX_MODEL_ATTEMPTS) Result.success() else Result.retry()
        }

        val startedAt = SystemClock.elapsedRealtime()
        AutomixStatus.set("Pre-análisis en segundo plano: ${todo.size} canciones por analizar")
        for ((i, path) in todo.withIndex()) {
            if (SystemClock.elapsedRealtime() - startedAt > MAX_RUN_MS) {
                AutomixStatus.set("Pre-análisis en pausa: faltan ${todo.size - i} canciones (continúa solo)")
                return Result.retry() // quedan canciones: WorkManager la reencola
            }
            val data = AutomixAnalyzer.analyze(path, ctx)
            currentCoroutineContext().ensureActive() // si se detuvo el trabajo, la canción NO se marca como fallida
            when {
                data != null -> repo.set(path, data)
                AutomixModelManager.isReady(ctx) -> repo.set(path, AutomixData.UNUSABLE)
                else -> return Result.retry()
            }
        }
        AutomixStatus.set("Pre-análisis en segundo plano terminado")
        return Result.success()
    }
}

/**
 * Programa (o cancela) el pre-análisis. Es idempotente: con KEEP, llamarlo varias veces no reinicia el
 * periodo. Solo se ejecuta cargando y con batería no baja; no pide red (los archivos son locales; el
 * modelo se baja una vez y, si falta, el propio worker lo intenta).
 */
object AutomixLibraryScheduler {
    private const val WORK_NAME = "automix_library_preanalysis"

    fun update(context: Context, enabled: Boolean) {
        val wm = WorkManager.getInstance(context.applicationContext)
        if (!enabled) {
            wm.cancelUniqueWork(WORK_NAME)
            return
        }
        val constraints = Constraints.Builder()
            .setRequiresCharging(true)
            .setRequiresBatteryNotLow(true)
            .build()
        val request = PeriodicWorkRequestBuilder<AutomixLibraryWorker>(12, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 1, TimeUnit.MINUTES)
            .build()
        wm.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}
