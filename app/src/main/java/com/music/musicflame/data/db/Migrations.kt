package com.music.musicflame.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migraciones de esquema de AppDatabase, una por cada subida de `version`.
 *
 * Reglas para escribir una nueva:
 *  - El SQL debe ser EXACTAMENTE el que Room espera para la entidad (tipos, NOT NULL, PRIMARY KEY,
 *    índices, claves foráneas); si no, Room lanza "Migration didn't properly handle..." al abrir la
 *    base. La referencia exacta sale de `createSql` en app/schemas/.../<version>.json (se genera al
 *    compilar). Los repos con motor (RoomStoreEngine) lo atrapan y siguen con los datos viejos.
 *  - Solo se AGREGAN tablas/columnas; nunca se borra nada del usuario.
 */
object Migrations {

    /** v1 -> v2: StatsRepository (tabla song_stats). */
    val MIGRATION_1_2: Migration = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `song_stats` (`song_id` INTEGER NOT NULL, " +
                    "`play_count` INTEGER NOT NULL, `total_listened_ms` INTEGER NOT NULL, " +
                    "`last_played_at` INTEGER NOT NULL, PRIMARY KEY(`song_id`))"
            )
        }
    }

    /** v2 -> v3: PlaylistRepository (tablas playlists y playlist_songs). */
    val MIGRATION_2_3: Migration = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `playlists` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                    "`is_default` INTEGER NOT NULL, `custom_cover_uri` TEXT, `position` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`id`))"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `playlist_songs` (`playlist_id` TEXT NOT NULL, " +
                    "`position` INTEGER NOT NULL, `song_id` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`playlist_id`, `position`), " +
                    "FOREIGN KEY(`playlist_id`) REFERENCES `playlists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
            )
        }
    }

    /** v3 -> v4: SongCustomizationRepository (tabla song_customizations). */
    val MIGRATION_3_4: Migration = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `song_customizations` (`song_id` INTEGER NOT NULL, " +
                    "`title` TEXT, `cover_uri` TEXT, `artist` TEXT, `album` TEXT, " +
                    "`display_title_formatted` TEXT, PRIMARY KEY(`song_id`))"
            )
        }
    }

    /** v4 -> v5: MomentsRepository (tabla moments + índice por ruta). */
    val MIGRATION_4_5: Migration = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `moments` (`id` TEXT NOT NULL, `path` TEXT NOT NULL, " +
                    "`start_ms` INTEGER NOT NULL, `end_ms` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`id`))"
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_moments_path` ON `moments` (`path`)")
        }
    }

    /** v5 -> v6: FavoritesRepository (tabla favorites). */
    val MIGRATION_5_6: Migration = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `favorites` (`song_id` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`song_id`))"
            )
        }
    }

    /** v6 -> v7: Órbita (tablas orbita_meta y orbita_song). */
    val MIGRATION_6_7: Migration = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `orbita_meta` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`nombre` TEXT NOT NULL, `autor_nombre` TEXT NOT NULL, `es_propia` INTEGER NOT NULL, " +
                    "`fecha_import` INTEGER NOT NULL)"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `orbita_song` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`orbita_id` INTEGER NOT NULL, `song_id` INTEGER, `path` TEXT, `titulo` TEXT NOT NULL, " +
                    "`artista` TEXT NOT NULL, `album` TEXT NOT NULL, `nota` TEXT NOT NULL, " +
                    "`fecha_agregada` INTEGER NOT NULL, `pos_x` REAL NOT NULL, `pos_y` REAL NOT NULL, `caratula` TEXT)"
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_orbita_song_orbita_id` ON `orbita_song` (`orbita_id`)")
        }
    }

    val ALL: Array<Migration> = arrayOf(
        MIGRATION_1_2,
        MIGRATION_2_3,
        MIGRATION_3_4,
        MIGRATION_4_5,
        MIGRATION_5_6,
        MIGRATION_6_7
    )
}
