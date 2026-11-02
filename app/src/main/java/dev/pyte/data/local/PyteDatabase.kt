package dev.pyte.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        PlaylistEntity::class,
        TrackEntity::class,
        PlaylistTrackEntity::class,
        FavoriteEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class PyteDatabase : RoomDatabase() {
    abstract fun playlistDao(): PlaylistDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun trackDao(): TrackDao
}
