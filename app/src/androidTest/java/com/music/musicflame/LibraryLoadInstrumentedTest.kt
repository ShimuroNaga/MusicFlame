package com.music.musicflame

import android.Manifest
import android.os.Build
import android.os.Debug
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.music.musicflame.data.AnomalyRepository
import com.music.musicflame.data.AnomalyScanner
import com.music.musicflame.data.SongArtLoader
import com.music.musicflame.data.SongLibraryHolder
import com.music.musicflame.data.loadSongsFromDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * PRUEBA DE CARGA EN EL CELULAR (usa TU biblioteca real de MediaStore).
 *
 * Correr con el celular conectado:
 *   ./gradlew connectedDebugAndroidTest --tests "com.music.musicflame.LibraryLoadInstrumentedTest"
 * y ver los números con:  adb logcat -s MFLoad
 *
 * Mide: query completo a MediaStore (frío y tibio), refresh concurrente (50 llamadas a la vez
 * deben escanear una vez a la vez sin romper nada), el salto de refresh "barato" por generación
 * de MediaStore, y memoria Java antes/después.
 */
@RunWith(AndroidJUnit4::class)
class LibraryLoadInstrumentedTest {

    private val tag = "MFLoad"
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun grantAudioPermission() {
        val perm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .grantRuntimePermission(ctx.packageName, perm)
    }

    private fun usedJavaHeapMb(): Long {
        System.gc()
        val rt = Runtime.getRuntime()
        return (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024)
    }

    @Test
    fun mediaStoreQuery_coldAndWarm() {
        val times = (1..5).map {
            val t0 = System.nanoTime()
            val songs = loadSongsFromDevice(ctx, includeGenres = true)
            val ms = (System.nanoTime() - t0) / 1e6
            Log.i(tag, "query #$it: ${songs.size} canciones en %.1f ms".format(ms))
            ms
        }
        Log.i(tag, "query: frío=%.1f ms, mediana tibia=%.1f ms".format(times.first(), times.drop(1).sorted()[1]))
        assertTrue(times.all { it < 30_000 })
    }

    @Test
    fun concurrentRefresh_isSerializedAndConsistent() = runBlocking {
        SongLibraryHolder.refresh(ctx)
        val expected = SongLibraryHolder.songs.size
        val t0 = System.nanoTime()
        (1..50).map { async(Dispatchers.Default) { SongLibraryHolder.refresh(ctx) } }.awaitAll()
        val ms = (System.nanoTime() - t0) / 1e6
        Log.i(tag, "50 refresh concurrentes: %.1f ms, canciones=%d".format(ms, SongLibraryHolder.songs.size))
        assertEquals(expected, SongLibraryHolder.songs.size)
        assertEquals(expected, SongLibraryHolder.index.byId.size)
    }

    @Test
    fun cheapRefresh_skipsWhenMediaStoreUnchanged() = runBlocking {
        SongLibraryHolder.refresh(ctx)
        val before = SongLibraryHolder.index
        val t0 = System.nanoTime()
        repeat(20) { SongLibraryHolder.refreshIfPermitted(ctx) }
        val ms = (System.nanoTime() - t0) / 1e6
        Log.i(tag, "20 refreshIfPermitted sin cambios: %.1f ms (misma instancia de índice: %b)"
            .format(ms, before === SongLibraryHolder.index))
        assertTrue("el índice no debió reconstruirse", before === SongLibraryHolder.index)
    }

    @Test
    fun memoryFootprint() = runBlocking {
        val base = usedJavaHeapMb()
        SongLibraryHolder.refresh(ctx)
        SongLibraryHolder.index.warmUp()
        val after = usedJavaHeapMb()
        Log.i(tag, "heap Java: antes=%d MB, después=%d MB (+%d MB) con %d canciones; nativo=%d MB"
            .format(base, after, after - base, SongLibraryHolder.songs.size, Debug.getNativeHeapAllocatedSize() / (1024 * 1024)))
        assertTrue(after - base < 256)
    }

    @Test
    fun anomalyScan_firstRunVsCachedRun() = runBlocking {
        SongLibraryHolder.refresh(ctx)
        val songs = SongLibraryHolder.songs
        val repo = AnomalyRepository(ctx)
        val t0 = System.nanoTime()
        val first = AnomalyScanner.scan(songs, repo) { _, _ -> }
        val firstMs = (System.nanoTime() - t0) / 1e6
        repo.saveAll(first)
        val t1 = System.nanoTime()
        val second = AnomalyScanner.scan(songs, repo) { _, _ -> }
        val secondMs = (System.nanoTime() - t1) / 1e6
        Log.i(tag, "anomalías (%d canciones): 1ª pasada=%.0f ms, 2ª (con caché)=%.0f ms".format(songs.size, firstMs, secondMs))
        assertEquals(first.size, second.size)
        assertEquals(first.map { it.types }, second.map { it.types })
    }

    @Test
    fun artLoader_secondPassIsServedFromCache() {
        runBlocking { SongLibraryHolder.refresh(ctx) }
        val songs = SongLibraryHolder.songs.take(60)
        SongArtLoader.clearCache()
        fun pass(): Double {
            val t = System.nanoTime()
            songs.forEach { SongArtLoader.loadBitmap(ctx, SongArtLoader.embeddedArtUri(it.path, it.albumArtUri)) }
            return (System.nanoTime() - t) / 1e6
        }
        val cold = pass()
        val warm = pass()
        Log.i(tag, "carátulas (%d canciones): fría=%.1f ms, tibia=%.1f ms".format(songs.size, cold, warm))
        assertTrue("la pasada con caché debería ser más rápida", songs.isEmpty() || warm <= cold)
    }
}
