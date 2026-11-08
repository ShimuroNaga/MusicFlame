package com.music.musicflame.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Una Órbita (la propia o una importada desde un archivo .orbita). */
@Entity(tableName = "orbita_meta")
data class OrbitaMetaEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id") val id: Long = 0,
    @ColumnInfo(name = "nombre") val nombre: String,
    @ColumnInfo(name = "autor_nombre") val autorNombre: String,
    /** true = editable (la del usuario); false = importada, solo lectura. */
    @ColumnInfo(name = "es_propia") val esPropia: Boolean,
    @ColumnInfo(name = "fecha_import") val fechaImport: Long
)

/**
 * Un nodo de una Órbita.
 *
 * Además de `song_id` guarda una "foto" de título/artista/álbum/ruta: así la órbita se puede
 * exportar sin buscar nada, sobrevive si MediaStore cambia los ids, y las órbitas IMPORTADAS
 * (que no traen `song_id` de este dispositivo) funcionan con la misma tabla.
 * `caratula` (base64 JPEG 100x100) solo se llena en órbitas importadas; en las propias la
 * carátula se lee de la canción real.
 */
@Entity(tableName = "orbita_song", indices = [Index(value = ["orbita_id"])])
data class OrbitaSongEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id") val id: Long = 0,
    @ColumnInfo(name = "orbita_id") val orbitaId: Long,
    @ColumnInfo(name = "song_id") val songId: Long?,
    @ColumnInfo(name = "path") val path: String?,
    @ColumnInfo(name = "titulo") val titulo: String,
    @ColumnInfo(name = "artista") val artista: String,
    @ColumnInfo(name = "album") val album: String,
    @ColumnInfo(name = "nota") val nota: String,
    @ColumnInfo(name = "fecha_agregada") val fechaAgregada: Long,
    @ColumnInfo(name = "pos_x") val posX: Float,
    @ColumnInfo(name = "pos_y") val posY: Float,
    @ColumnInfo(name = "caratula") val caratula: String?
)

/** Resultado de la lista de selección: la órbita + cuántas canciones tiene. */
data class OrbitaMetaWithCount(
    val id: Long,
    val nombre: String,
    @ColumnInfo(name = "autor_nombre") val autorNombre: String,
    @ColumnInfo(name = "es_propia") val esPropia: Boolean,
    @ColumnInfo(name = "fecha_import") val fechaImport: Long,
    val cantidad: Int
)
