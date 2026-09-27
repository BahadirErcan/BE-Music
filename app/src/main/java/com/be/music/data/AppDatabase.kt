package com.be.music.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Song::class,
        Artist::class,
        Album::class,
        Playlist::class,
        FilterSettings::class,
        SongOverride::class
    ],
    version = 9,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun artistDao(): ArtistDao
    abstract fun albumDao(): AlbumDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun filterSettingsDao(): FilterSettingsDao
    abstract fun songOverrideDao(): SongOverrideDao


    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `filter_settings_new` (`id` INTEGER NOT NULL, `filterMode` TEXT NOT NULL, `durationThresholdSec` INTEGER NOT NULL, `folders` TEXT NOT NULL, `themeMode` TEXT NOT NULL, `language` TEXT NOT NULL, `themeColor` TEXT NOT NULL, `downloadLyricsEnabled` INTEGER NOT NULL, `preferredVideoQuality` TEXT NOT NULL, `sleepTimerMinutes` INTEGER NOT NULL, `parallelDownloadCount` INTEGER NOT NULL, `autoPlaylistName` TEXT NOT NULL, `autoPlaylistNumberPosition` TEXT NOT NULL, `autoPlaylistMultiSong` INTEGER NOT NULL, `skipSilenceEnabled` INTEGER NOT NULL, `songSortOrder` TEXT NOT NULL, `songSortReverse` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("INSERT INTO `filter_settings_new` (`id`, `filterMode`, `durationThresholdSec`, `folders`, `themeMode`, `language`, `themeColor`, `downloadLyricsEnabled`, `preferredVideoQuality`, `sleepTimerMinutes`, `parallelDownloadCount`, `autoPlaylistName`, `autoPlaylistNumberPosition`, `autoPlaylistMultiSong`, `skipSilenceEnabled`, `songSortOrder`, `songSortReverse`) SELECT `id`, `filterMode`, `durationThresholdSec`, `folders`, `themeMode`, `language`, `themeColor`, `downloadLyricsEnabled`, `preferredVideoQuality`, `sleepTimerMinutes`, `parallelDownloadCount`, `autoPlaylistName`, `autoPlaylistNumberPosition`, `autoPlaylistMultiSong`, `skipSilenceEnabled`, 'DATE', 0 FROM `filter_settings`")
                db.execSQL("DROP TABLE `filter_settings`")
                db.execSQL("ALTER TABLE `filter_settings_new` RENAME TO `filter_settings`")

                db.execSQL("CREATE TABLE IF NOT EXISTS `playlists_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `songIds` TEXT NOT NULL, `filePath` TEXT, `sortMode` TEXT NOT NULL, `sortReverse` INTEGER NOT NULL)")
                db.execSQL("INSERT INTO `playlists_new` (`id`, `name`, `songIds`, `filePath`, `sortMode`, `sortReverse`) SELECT `id`, `name`, `songIds`, `filePath`, 'ADDED', 0 FROM `playlists`")
                db.execSQL("DROP TABLE `playlists`")
                db.execSQL("ALTER TABLE `playlists_new` RENAME TO `playlists`")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `song_overrides` (`songId` INTEGER NOT NULL, `title` TEXT NOT NULL, `artist` TEXT NOT NULL, `album` TEXT NOT NULL, `path` TEXT, `dateModified` INTEGER NOT NULL, PRIMARY KEY(`songId`))")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "be_music_database"
                )
                .addMigrations(MIGRATION_7_8, MIGRATION_8_9)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
