package dev.pyte.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey val id: String,
    val title: String,
    val owner: String?,
    val thumbnailUrl: String?,
    val sourceUrl: String,
    val trackCount: Int,
    val importedAt: Long,
)

@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey val videoId: String,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val thumbnailUrl: String?,
)

@Entity(
    tableName = "playlist_tracks",
    primaryKeys = ["playlistId", "position"],
    indices = [Index("videoId")],
)
data class PlaylistTrackEntity(
    val playlistId: String,
    @ColumnInfo(name = "position") val position: Int,
    val videoId: String,
)

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val videoId: String,
    val addedAt: Long,
)
