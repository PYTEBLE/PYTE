package dev.pyte.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import android.content.Context

@Database(
    entities = [
        PlaylistEntity::class,
        TrackEntity::class,
        PlaylistTrackEntity::class,
        FavoriteEntity::class,
        HistoryEntity::class,
        TrackFtsEntity::class,
        OfflinePlaylistEntity::class,
        DownloadEntity::class,
    ],
    version = 4,
    exportSchema = false,
)
abstract class PyteDatabase : RoomDatabase() {
    abstract fun playlistDao(): PlaylistDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun trackDao(): TrackDao
    abstract fun offlineDao(): OfflineDao

    companion object {
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS offline_playlists (playlistId TEXT NOT NULL PRIMARY KEY)")
                db.execSQL("CREATE TABLE IF NOT EXISTS downloads (videoId TEXT NOT NULL PRIMARY KEY, state TEXT NOT NULL, bytes INTEGER NOT NULL, total INTEGER NOT NULL, error TEXT)")
            }
        }
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE playlist_tracks ADD COLUMN locallyAdded INTEGER NOT NULL DEFAULT 0")
            }
        }
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE playlists ADD COLUMN localTitle TEXT")
                db.execSQL("CREATE TABLE IF NOT EXISTS history (videoId TEXT NOT NULL PRIMARY KEY, lastPlayedAt INTEGER NOT NULL)")
                db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS tracks_fts USING FTS4(videoId TEXT NOT NULL, title TEXT NOT NULL, artist TEXT NOT NULL, notindexed=`videoId`, tokenize=unicode61)")
                db.execSQL("INSERT INTO tracks_fts(rowid, videoId, title, artist) SELECT rowid, videoId, title, artist FROM tracks")
                installSearchTriggers(db)
            }
        }

        fun open(context: Context, name: String = "pyte.db"): PyteDatabase =
            Room.databaseBuilder(context, PyteDatabase::class.java, name)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) = installSearchTriggers(db)
                })
                .build()

        private fun installSearchTriggers(db: SupportSQLiteDatabase) {
            db.execSQL("""CREATE TRIGGER IF NOT EXISTS tracks_search_insert AFTER INSERT ON tracks BEGIN
                INSERT INTO tracks_fts(rowid, videoId, title, artist) VALUES (new.rowid, new.videoId, new.title, new.artist); END""")
            db.execSQL("""CREATE TRIGGER IF NOT EXISTS tracks_search_update AFTER UPDATE ON tracks BEGIN
                DELETE FROM tracks_fts WHERE rowid = old.rowid;
                INSERT INTO tracks_fts(rowid, videoId, title, artist) VALUES (new.rowid, new.videoId, new.title, new.artist); END""")
            db.execSQL("""CREATE TRIGGER IF NOT EXISTS tracks_search_delete AFTER DELETE ON tracks BEGIN
                DELETE FROM tracks_fts WHERE rowid = old.rowid; END""")
        }
    }
}
