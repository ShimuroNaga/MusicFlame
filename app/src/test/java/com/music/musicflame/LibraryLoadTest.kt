package com.music.musicflame

import com.music.musicflame.data.DebouncedTask
import com.music.musicflame.data.LibraryIndex
import com.music.musicflame.data.Song
import com.music.musicflame.data.matchM3uPaths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * PRUEBAS DE CARGA (JVM puro, sin celular): ejercitan la parte de caché/índice de la biblioteca
 * con bibliotecas sintéticas de 1 000, 10 000 y 50 000 canciones.
 *
 * Correr:  ./gradlew testDebugUnitTest --tests "com.music.musicflame.LibraryLoadTest" -i
 * (-i imprime las tablas de tiempos). Los límites de tiempo son holgados a propósito (cubren CI
 * lento); lo que importa es leer los números impresos y compararlos entre corridas.
 */
class LibraryLoadTest {

    // ------------------------------------------------------------------ datos sintéticos

    private fun synthLibrary(n: Int, seed: Long = 42L): List<Song> {
        val rnd = Random(seed)
        val artists = (n / 12).coerceAtLeast(5)
        val genres = listOf("Rock", "Pop", "Hip-Hop", "Jazz", "Electronic", "Metal", "Lo-Fi", "Reggaeton", "Clásica", "")
        return List(n) { i ->
            val artist = rnd.nextInt(artists)
            val album = rnd.nextInt(4)
            Song(
                id = 1_000L + i,
                title = "Canción ${rnd.nextInt(n * 2)} ${'A' + rnd.nextInt(26)}",
                artist = "Artista $artist",
                album = "Álbum $artist-$album",
                duration = 120_000L + rnd.nextInt(240_000),
                path = "/storage/emulated/0/Music/Artista $artist/Álbum $album/pista_$i.mp3",
                albumArtUri = "content://media/external/audio/albumart/${artist * 10 + album}",
                dateAdded = 1_700_000_000L + i,
                year = 1990 + rnd.nextInt(35),
                genre = genres[rnd.nextInt(genres.size)].takeIf { it.isNotEmpty() }
            )
        }
    }

    private inline fun <T> timed(block: () -> T): Pair<T, Double> {
        val t0 = System.nanoTime()
        val r = block()
        return r to (System.nanoTime() - t0) / 1e6
    }

    // ------------------------------------------------------------------ índice

    @Test
    fun index_build_scales() {
        println("== Construcción del índice (byId + byPath + álbumes + artistas + géneros) ==")
        println("canciones | warmUp ms | álbumes | artistas | géneros")
        for (n in listOf(1_000, 10_000, 50_000)) {
            val songs = synthLibrary(n)
            val (idx, ms) = timed { LibraryIndex(songs).also { it.warmUp() } }
            println("%9d | %9.1f | %7d | %8d | %7d".format(n, ms, idx.albums.size, idx.artists.size, idx.genres.size))
            if (n == 50_000) assertTrue("50k tardó demasiado: $ms ms", ms < 8_000)
        }
    }

    @Test
    fun index_is_consistent_with_source() {
        val songs = synthLibrary(5_000)
        val idx = LibraryIndex(songs)
        assertEquals(songs.size, idx.byId.size)
        assertEquals(songs.size, idx.byPath.size)
        assertEquals(songs.size, idx.albums.sumOf { it.songCount })
        assertEquals(songs.size, idx.artists.sumOf { it.songCount })
        assertEquals(songs.size, idx.genres.sumOf { it.songCount })
        songs.take(200).forEach { s ->
            assertEquals(s, idx.byId[s.id])
            assertEquals(s, idx.byPath[s.path])
        }
        assertNull(idx.byId[-1L])
        assertEquals(songs.map { it.album to it.artist }.distinct().size, idx.albums.size)
    }

    @Test
    fun index_concurrent_first_access_is_safe_and_shared() {
        val idx = LibraryIndex(synthLibrary(20_000))
        val pool = Executors.newFixedThreadPool(16)
        val start = CountDownLatch(1)
        val results = (0 until 16).map {
            pool.submit<List<Any>> {
                start.await()
                listOf(idx.albums, idx.artists, idx.genres, idx.byId)
            }
        }
        start.countDown()
        val all = results.map { it.get(30, TimeUnit.SECONDS) }
        pool.shutdown()
        // Todos los hilos deben ver LAS MISMAS instancias (calculado una sola vez, no 16).
        for (i in 0 until 4) assertTrue(all.all { it[i] === all[0][i] })
    }

    @Test
    fun lookup_by_id_beats_linear_scan() {
        println("== Búsqueda por id: lineal (antes) vs índice (ahora), 20 000 consultas ==")
        println("canciones | lineal ms | índice ms | veces más rápido")
        for (n in listOf(1_000, 10_000, 50_000)) {
            val songs = synthLibrary(n)
            val idx = LibraryIndex(songs).also { it.warmUp() }
            val rnd = Random(7)
            val ids = LongArray(20_000) { songs[rnd.nextInt(n)].id }
            val (linHits, linMs) = timed { ids.count { id -> songs.find { it.id == id } != null } }
            val (idxHits, idxMs) = timed { ids.count { id -> idx.byId[id] != null } }
            assertEquals(linHits, idxHits)
            println("%9d | %9.1f | %9.2f | %12.0fx".format(n, linMs, idxMs, linMs / idxMs.coerceAtLeast(0.01)))
        }
    }

    // ------------------------------------------------------------------ M3U

    private fun naiveM3u(songs: List<Song>, paths: List<String>) =
        songs.filter { song -> paths.any { path -> song.path.endsWith(path.trimStart('/')) || song.path == path } }

    @Test
    fun m3u_matcher_equals_original_algorithm() {
        val songs = synthLibrary(3_000)
        val rnd = Random(3)
        val paths = buildList {
            // rutas absolutas exactas, rutas relativas con subcarpetas, solo nombre y rutas inexistentes
            repeat(300) { add(songs[rnd.nextInt(songs.size)].path) }
            repeat(300) { add(songs[rnd.nextInt(songs.size)].path.removePrefix("/storage/emulated/0/")) }
            repeat(100) { add(songs[rnd.nextInt(songs.size)].path.substringAfterLast('/')) }
            repeat(100) { add("/otra/carpeta/no_existe_$it.mp3") }
        }
        assertEquals(naiveM3u(songs, paths).map { it.id }, matchM3uPaths(songs, paths).map { it.id })
    }

    @Test
    fun m3u_matcher_scales() {
        println("== Importar .m3u: O(canciones×rutas) (antes) vs indexado (ahora) ==")
        println("canciones=rutas | antes ms | ahora ms")
        for (n in listOf(1_000, 5_000, 10_000)) {
            val songs = synthLibrary(n)
            val paths = songs.map { it.path }
            val (_, newMs) = timed { matchM3uPaths(songs, paths) }
            // el algoritmo viejo se mide solo hasta 5 000 (a 10 000 son 100 M de endsWith)
            val oldMs = if (n <= 5_000) timed { naiveM3u(songs, paths) }.second else Double.NaN
            println("%15d | %8.1f | %8.1f".format(n, oldMs, newMs))
            if (n == 10_000) assertTrue("m3u 10k tardó $newMs ms", newMs < 1_500)
        }
    }

    // ------------------------------------------------------------------ escrituras con debounce

    @Test
    fun debounce_coalesces_a_burst_into_one_write() {
        var serializedEntries = 0L
        val task = DebouncedTask(150) { serializedEntries += 5_000 } // "serializar" 5 000 entradas
        repeat(5_000) { task.request() }                              // 5 000 cambios seguidos
        Thread.sleep(600)
        assertEquals("debió escribir UNA sola vez", 1, task.runCount)
        assertEquals(5_000L, serializedEntries)
        println("== Debounce: 5 000 cambios -> ${task.runCount} escritura (antes: 5 000 escrituras completas) ==")
    }

    @Test
    fun debounce_flushNow_runs_immediately_and_cancels_pending() {
        val task = DebouncedTask(10_000) {}
        task.request()
        task.flushNow()
        assertEquals(1, task.runCount)
        Thread.sleep(100)
        assertEquals("lo pendiente debió cancelarse", 1, task.runCount)
    }

    @Test
    fun debounce_survives_concurrent_requests() {
        val task = DebouncedTask(100) {}
        val pool = Executors.newFixedThreadPool(8)
        val done = CountDownLatch(8)
        repeat(8) { pool.execute { repeat(2_000) { task.request() }; done.countDown() } }
        assertTrue(done.await(20, TimeUnit.SECONDS))
        pool.shutdown()
        Thread.sleep(500)
        assertTrue("runCount=${task.runCount}", task.runCount in 1..3)
    }

    @Test
    fun sanity_helpers() {
        assertNotNull(synthLibrary(10).firstOrNull())
    }
}
