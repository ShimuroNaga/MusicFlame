package com.music.musicflame.data

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.coroutineContext

/**
 * Motor de "Búsqueda de anomalías" (Ajustes > Canciones): recorre la
 * biblioteca ya cargada (SongLibraryHolder.songs) y clasifica cada canción
 * según los 5 problemas que sabe detectar (ver AnomalyType).
 *
 * SIEMPRE se dispara a mano desde el botón "Buscar anomalías" de
 * AnomalyScanDialog — nunca solo, nunca en segundo plano — y corre entero en
 * Dispatchers.IO porque decodificar carátulas embebidas y leer tags con
 * jaudiotagger es trabajo de disco (mismo patrón que SongLibraryHolder.refresh).
 *
 * Reaprovecha AnomalyRepository: si el archivo de una canción no cambió de
 * tamaño/fecha de modificación NI de título/artista efectivos desde el
 * último análisis guardado, no se vuelve a tocar el disco para ella — se
 * reutiliza directo el resultado anterior. (Se compara también título/artista
 * y no solo el archivo físico porque una edición hecha SOLO dentro de la app,
 * vía SongCustomizationRepository, sin "Guardar etiquetas reales" activado,
 * cambia lo que ve el usuario sin tocar el archivo en disco.)
 *
 * Los "Posibles duplicados" son la excepción: se recalculan SIEMPRE sobre la
 * biblioteca completa recién armada, porque dependen de comparar unas
 * canciones con otras (no de un archivo aislado) y es una operación en
 * memoria barata, no de IO.
 */
object AnomalyScanner {

    // Canciones analizadas a la vez. 3 equilibra velocidad y memoria/batería en celulares medios.
    private const val SCAN_PARALLELISM = 3

    // Para validar la carátula no hace falta decodificarla a tamaño completo (una de 3000x3000 son
    // ~36 MB y con varias en paralelo reventaría la memoria): se decodifica reducida.
    private const val ART_CHECK_MAX_DIM = 256

    // Marcadores típicos de "sin artista real" que distintos taggers/apps
    // dejan cuando no hay dato — no pretende ser una lista exhaustiva.
    private val UNKNOWN_ARTIST_MARKERS = setOf(
        "unknown", "unknown artist", "artista desconocido", "<unknown>", "desconocido"
    )

    /**
     * Escanea [songs] (normalmente SongLibraryHolder.songs). [onProgress] se
     * llama por cada canción procesada con (analizadas, total) — ya sea que
     * se haya re-analizado de verdad o que se haya reusado del cache — para
     * alimentar la barra de progreso 0-100% ("Analizando 340/1200").
     */
    suspend fun scan(
        songs: List<Song>,
        repository: AnomalyRepository,
        onProgress: (done: Int, total: Int) -> Unit
    ): List<AnomalyRecord> = withContext(Dispatchers.IO) {
        val total = songs.size
        val cache = repository.getAll()
        val now = System.currentTimeMillis()
        val results = arrayOfNulls<AnomalyRecord>(total)

        // Hasta SCAN_PARALLELISM canciones a la vez (antes: una por una). El trabajo es IO
        // (stat del archivo + MediaMetadataRetriever), así que paralelizar acota el tiempo total
        // sin saturar el celular. El orden de salida se conserva (results[index]).
        val gate = Semaphore(SCAN_PARALLELISM)
        val doneCount = AtomicInteger(0)
        val progressLock = Any()

        coroutineScope {
            songs.forEachIndexed { index, song ->
                launch {
                    gate.withPermit {
                        coroutineContext.ensureActive() // permite cancelar el escaneo si se cierra el diálogo a medias

                        val file = File(song.path)
                        val exists = file.exists()
                        val currentSize = if (exists) file.length() else -1L
                        val currentModified = if (exists) file.lastModified() else -1L
                        val cached = cache[song.path]

                        val unchanged = cached != null &&
                            cached.songId == song.id &&
                            cached.fileSize == currentSize &&
                            cached.fileLastModified == currentModified &&
                            cached.title == song.title &&
                            cached.artist == song.artist

                        results[index] = if (unchanged) {
                            cached!!
                        } else {
                            AnomalyRecord(
                                songId = song.id,
                                path = song.path,
                                title = song.title,
                                artist = song.artist,
                                types = detectFileLevelAnomalies(song, file, exists),
                                ignoredTypes = cached?.ignoredTypes ?: emptySet(),
                                fileSize = currentSize,
                                fileLastModified = currentModified,
                                lastScannedAt = now
                            )
                        }
                    }
                    // Callbacks serializados y con contador creciente, aunque terminen desordenadas.
                    synchronized(progressLock) { onProgress(doneCount.incrementAndGet(), total) }
                }
            }
        }

        val fresh = ArrayList<AnomalyRecord>(total)
        for (r in results) if (r != null) fresh.add(r)
        withDuplicatesMarked(fresh, songs)
    }

    /** Los 4 problemas que dependen solo del propio archivo/canción (no de compararla con otras). */
    private fun detectFileLevelAnomalies(song: Song, file: File, fileExists: Boolean): Set<AnomalyType> {
        val types = mutableSetOf<AnomalyType>()

        // 3. Formato no soportado por el tagger actual (jaudiotagger) — ej. .aac crudo.
        // Reutiliza la misma lista que ya usa RealTagWriter para escribir tags reales.
        if (!RealTagWriter.isSupportedFile(song.path)) {
            types.add(AnomalyType.UNSUPPORTED_FORMAT)
        }

        // 4. Duración = 0 o archivo posiblemente truncado/corrupto.
        val fileMissingOrEmpty = !fileExists || file.length() <= 0L
        if (song.duration <= 0L || fileMissingOrEmpty) {
            types.add(AnomalyType.ZERO_OR_TRUNCATED_DURATION)
        }

        // 2. Metadata vacía o sospechosa: título = nombre de archivo, artista
        // "desconocido"/vacío, o sin año (MediaStore no distingue "año = 0"
        // de "sin dato de año": SongRepository ya normaliza ambos a null).
        val filenameStem = file.name.substringBeforeLast('.', file.name)
        val titleLooksLikeFilename = song.title.isNotBlank() &&
            song.title.trim().equals(filenameStem.trim(), ignoreCase = true)
        val artistLooksMissing = song.artist.isBlank() ||
            song.artist.trim().lowercase() in UNKNOWN_ARTIST_MARKERS
        val yearMissing = song.year == null
        if (titleLooksLikeFilename || artistLooksMissing || yearMissing) {
            types.add(AnomalyType.SUSPICIOUS_METADATA)
        }

        // 1. Carátula corrupta o ilegible: solo cuenta si el archivo SÍ trae
        // bytes de carátula embebida pero esos bytes no se pueden decodificar
        // como imagen — si simplemente no trae ninguna carátula, no es una
        // anomalía (mismo método de extracción que SongArtLoader.extractEmbeddedArt).
        if (!fileMissingOrEmpty) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(song.path)
                val embedded = retriever.embeddedPicture
                if (embedded != null) {
                    if (!isArtworkDecodable(embedded)) types.add(AnomalyType.CORRUPT_ARTWORK)
                }
            } catch (e: Exception) {
                // No se pudo ni siquiera abrir el archivo para leer sus metadatos:
                // ya queda cubierto arriba por ZERO_OR_TRUNCATED_DURATION.
            } finally {
                try {
                    retriever.release()
                } catch (e: Exception) {
                    // no-op
                }
            }
        }

        return types
    }

    /** true si los bytes se pueden decodificar como imagen (decodificando reducida, barato en memoria). */
    private fun isArtworkDecodable(bytes: ByteArray): Boolean {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= ART_CHECK_MAX_DIM) sample *= 2
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
        }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        val ok = decoded != null
        decoded?.recycle()
        return ok
    }

    /**
     * 5. Posibles duplicados: mismo título+artista (normalizados, ignorando
     * mayúsculas/espacios) pero distinta ruta física. Se recalcula siempre
     * desde cero sobre [songs] (nunca se toma del cache), incluyendo también
     * quitar la marca de canciones que dejaron de ser duplicadas desde el
     * último análisis (ej. porque se borró la copia repetida).
     */
    private fun withDuplicatesMarked(records: List<AnomalyRecord>, songs: List<Song>): List<AnomalyRecord> {
        fun key(title: String, artist: String) = "${title.trim().lowercase()}|${artist.trim().lowercase()}"

        val duplicatePaths = songs
            .groupBy { key(it.title, it.artist) }
            .filterValues { group -> group.map { it.path }.distinct().size > 1 }
            .values
            .flatten()
            .map { it.path }
            .toSet()

        return records.map { record ->
            val withoutStaleDuplicateFlag = record.types - AnomalyType.POSSIBLE_DUPLICATE
            val finalTypes = if (record.path in duplicatePaths) {
                withoutStaleDuplicateFlag + AnomalyType.POSSIBLE_DUPLICATE
            } else {
                withoutStaleDuplicateFlag
            }
            if (finalTypes == record.types) record else record.copy(types = finalTypes)
        }
    }
}
