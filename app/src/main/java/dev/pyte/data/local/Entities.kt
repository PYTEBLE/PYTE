package dev.pyte.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Fts4
import androidx.room.FtsOptions

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey val id: String,
    val title: String,
    val owner: String?,
    val thumbnailUrl: String?,
    val sourceUrl: String,
    val trackCount: Int,
    val importedAt: Long,
    val localTitle: String? = null,
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
    @ColumnInfo(defaultValue = "0") val locallyAdded: Boolean = false,
)

/** Local exclusions survive source refreshes; no changes are sent to YouTube. */
@Entity(tableName = "playlist_exclusions", primaryKeys = ["playlistId", "videoId"])
data class PlaylistExclusionEntity(val playlistId: String, val videoId: String)

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val videoId: String,
    val addedAt: Long,
)

@Entity(tableName = "history")
data class HistoryEntity(@PrimaryKey val videoId: String, val lastPlayedAt: Long)

@Fts4(tokenizer = FtsOptions.TOKENIZER_UNICODE61, notIndexed = ["videoId"])
@Entity(tableName = "tracks_fts")
data class TrackFtsEntity(val videoId: String, val title: String, val artist: String)
