package dev.pyte.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "offline_playlists")
data class OfflinePlaylistEntity(@PrimaryKey val playlistId: String)

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val videoId: String,
    val state: String = "Queued",
    val bytes: Long = 0,
    val total: Long = 0,
    val error: String? = null,
)

@Dao
interface OfflineDao {
    @Query("SELECT playlistId FROM offline_playlists")
    fun observeSelected(): Flow<List<String>>
    @Query("SELECT * FROM downloads")
    fun observeDownloads(): Flow<List<DownloadEntity>>
    @Query("SELECT DISTINCT t.* FROM tracks t JOIN playlist_tracks pt ON pt.videoId=t.videoId JOIN offline_playlists o ON o.playlistId=pt.playlistId JOIN playlists p ON p.id=o.playlistId")
    suspend fun wantedTracks(): List<TrackEntity>
    @Query("SELECT DISTINCT t.* FROM tracks t JOIN playlist_tracks pt ON pt.videoId=t.videoId JOIN offline_playlists o ON o.playlistId=pt.playlistId JOIN playlists p ON p.id=o.playlistId ORDER BY t.videoId")
    fun observeWantedTracks(): Flow<List<TrackEntity>>
    @Query("SELECT EXISTS(SELECT 1 FROM playlist_tracks pt JOIN offline_playlists o ON o.playlistId=pt.playlistId JOIN playlists p ON p.id=o.playlistId WHERE pt.videoId=:id)")
    suspend fun isWanted(id: String): Boolean
    @Upsert suspend fun select(value: OfflinePlaylistEntity)
    @Query("DELETE FROM offline_playlists WHERE playlistId=:id") suspend fun deselect(id: String)
    @Upsert suspend fun update(value: DownloadEntity)
    @Query("DELETE FROM downloads WHERE videoId=:id") suspend fun remove(id: String)
}
