package com.music.musicflame.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Una fila por canción: qué fuente de carátula "de fábrica" funcionó la última vez
 * (ver ArtworkSource en ArtworkCacheRepository.kt).
 *
 * - filePath: ruta física del archivo de audio (es lo único que AlbumArt() siempre recibe).
 * - source: el NOMBRE del enum ArtworkSource ("EMBEDDED", "ALBUM_URI" o "NONE"). Se guarda como
 *   texto a propósito (sin TypeConverter): si un día se agrega un valor al enum, la fila vieja
 *   sigue siendo legible y un valor desconocido simplemente se ignora al leer.
 */
@Entity(tableName = "artwork_cache")
data class ArtworkCacheEntity(
    @PrimaryKey
    @ColumnInfo(name = "file_path") val filePath: String,
    @ColumnInfo(name = "source") val source: String
)
