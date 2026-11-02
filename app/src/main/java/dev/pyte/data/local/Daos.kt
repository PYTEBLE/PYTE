package dev.pyte.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY importedAt DESC")
    fun observePlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    fun observePlaylist(id: String): Flow<PlaylistEntity?>

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun getPlaylist(id: String): PlaylistEntity?

    @Query("UPDATE playlists SET localTitle = :title WHERE id = :id")
    suspend fun renamePlaylist(id: String, title: String)

    @Query(
        """
        SELECT t.* FROM tracks t
        INNER JOIN playlist_tracks pt ON pt.videoId = t.videoId
        WHERE pt.playlistId = :playlistId
        ORDER BY pt.position
        """
    )
    fun observePlaylistTracks(playlistId: String): Flow<List<TrackEntity>>

    @Upsert
    suspend fun upsertPlaylist(playlist: PlaylistEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistTracks(links: List<PlaylistTrackEntity>)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun clearPlaylistTracks(playlistId: String)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: String)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun deletePlaylistLinks(playlistId: String)
}

@Dao
interface FavoriteDao {
    @Query(
        """
        SELECT t.* FROM tracks t
        INNER JOIN favorites f ON f.videoId = t.videoId
        ORDER BY f.addedAt DESC
        """
    )
    fun observeFavoriteTracks(): Flow<List<TrackEntity>>

    @Query("SELECT videoId FROM favorites")
    fun observeFavoriteIds(): Flow<List<String>>

    @Upsert
    suspend fun add(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE videoId = :videoId")
    suspend fun remove(videoId: String)
}

@Dao
interface TrackDao {
    @Query("SELECT * FROM tracks WHERE videoId = :videoId")
    suspend fun getTrack(videoId: String): TrackEntity?

    @Upsert
    suspend fun upsertTracks(tracks: List<TrackEntity>)

    @Query(
        """
        SELECT t.* FROM tracks t JOIN tracks_fts ON tracks_fts.videoId = t.videoId
        WHERE tracks_fts MATCH :query AND (
            EXISTS (SELECT 1 FROM playlist_tracks p WHERE p.videoId = t.videoId) OR
            EXISTS (SELECT 1 FROM favorites f WHERE f.videoId = t.videoId) OR
            EXISTS (SELECT 1 FROM history h WHERE h.videoId = t.videoId))
        ORDER BY t.title COLLATE NOCASE
        LIMIT 200
        """
    )
    fun search(query: String): Flow<List<TrackEntity>>

    @Upsert
    suspend fun recordHistory(history: HistoryEntity)

    @Query("SELECT t.* FROM tracks t JOIN history h ON h.videoId = t.videoId ORDER BY h.lastPlayedAt DESC LIMIT 20")
    fun observeHistory(): Flow<List<TrackEntity>>

    @Query("DELETE FROM history")
    suspend fun clearHistory()
}
