package com.music.musicflame.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

/**
 * Base de datos Room ÚNICA y compartida de la app. Cada repositorio que se migre a Room solo
 * agrega aquí su entidad (en `entities`) y su DAO (como `abstract fun xxxDao()`).
 *
 * Cómo agregar una entidad/tabla nueva en el futuro (ej. StatsRepository):
 *  1. Crear la @Entity y su @Dao.
 *  2. Agregarla a `entities` y exponer el DAO aquí.
 *  3. Subir `version`.
 *  4. Escribir el `Migration(n, n+1)` con el CREATE TABLE en Migrations.kt y agregarlo a `Migrations.ALL`.
 *     (El SQL exacto sale de `createSql` en app/schemas/.../<version>.json, que Room genera al compilar.)
 *
 * NUNCA se usa fallbackToDestructiveMigration(): si falta una migración, es mejor que falle
 * ruidosamente en desarrollo que borrar datos de usuarios en silencio.
 */
@Database(
    entities = [
        ArtworkCacheEntity::class,
        SongStatEntity::class,
        PlaylistEntity::class,
        PlaylistSongEntity::class,
        SongCustomizationEntity::class,
        MomentEntity::class
    ],
    version = 5,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun artworkCacheDao(): ArtworkCacheDao
    abstract fun songStatDao(): SongStatDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun songCustomizationDao(): SongCustomizationDao
    abstract fun momentDao(): MomentDao

    companion object {
        const val DB_NAME = "musicflame.db"

        /** Migraciones de esquema, en orden (ver Migrations.kt). */
        val ALL_MIGRATIONS: Array<Migration> = Migrations.ALL

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DB_NAME
                )
                    .addMigrations(*ALL_MIGRATIONS)
                    .build()
                    .also { instance = it }
            }
    }
}
