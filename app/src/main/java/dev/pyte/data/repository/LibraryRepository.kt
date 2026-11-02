package dev.pyte.data.repository

import androidx.room.withTransaction
import dev.pyte.data.local.FavoriteEntity
import dev.pyte.data.local.PlaylistEntity
import dev.pyte.data.local.PlaylistTrackEntity
import dev.pyte.data.local.PyteDatabase
import dev.pyte.data.local.TrackEntity
import dev.pyte.data.local.HistoryEntity
import dev.pyte.data.local.searchQuery
import dev.pyte.data.remote.ImportException
import dev.pyte.data.remote.ListIdParser
import dev.pyte.data.remote.ListIdResult
import dev.pyte.data.remote.PlaylistImporter
import dev.pyte.data.remote.ImportedPlaylist
import dev.pyte.domain.PlaylistSummary
import dev.pyte.domain.Track
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibraryRepository @Inject constructor(
    private val db: PyteDatabase,
    private val importer: PlaylistImporter,
) {
    private val playlists = db.playlistDao()
    private val favorites = db.favoriteDao()
    private val tracks = db.trackDao()

    fun observePlaylists(): Flow<List<PlaylistSummary>> =
        playlists.observePlaylists().map { list -> list.map { it.toSummary() } }

    fun observePlaylist(id: String): Flow<Pair<PlaylistSummary, List<Track>>?> =
        combine(playlists.observePlaylist(id), playlists.observePlaylistTracks(id)) { playlist, rows ->
            playlist?.let { it.toSummary() to rows.map(TrackEntity::toDomain) }
        }

    fun observeFavorites(): Flow<List<Track>> =
        favorites.observeFavoriteTracks().map { rows -> rows.map(TrackEntity::toDomain) }

    fun observeFavoriteIds(): Flow<Set<String>> =
        favorites.observeFavoriteIds().map { it.toSet() }

    fun search(query: String): Flow<List<Track>> {
        val match = searchQuery(query) ?: return flowOf(emptyList())
        return tracks.search(match).map { rows -> rows.map(TrackEntity::toDomain) }
    }

    fun observeHistory(): Flow<List<Track>> =
        tracks.observeHistory().map { rows -> rows.map(TrackEntity::toDomain) }

    suspend fun recordPlayed(track: Track) {
        db.withTransaction {
            if (tracks.getTrack(track.videoId) == null) tracks.upsertTracks(listOf(track.toEntity()))
            tracks.recordHistory(HistoryEntity(track.videoId, System.currentTimeMillis()))
        }
    }

    suspend fun renamePlaylist(id: String, title: String) {
        val trimmed = title.trim()
        require(trimmed.isNotEmpty() && trimmed.length <= 100) { "Use a name between 1 and 100 characters" }
        playlists.renamePlaylist(id, trimmed)
    }

    suspend fun refreshPlaylist(id: String) {
        saveImported(importer.import(id), requireExisting = true)
    }

    suspend fun setFavorite(track: Track, favorite: Boolean) {
        db.withTransaction {
            if (favorite) {
                tracks.upsertTracks(listOf(track.toEntity()))
                favorites.add(FavoriteEntity(track.videoId, System.currentTimeMillis()))
            } else {
                favorites.remove(track.videoId)
            }
        }
    }

    suspend fun deletePlaylist(id: String) {
        db.withTransaction {
            playlists.deletePlaylistLinks(id)
            playlists.deletePlaylist(id)
        }
    }

    /** Imports (or refreshes) a playlist from any supported link. Returns the playlist id. */
    suspend fun importPlaylist(input: String): String {
        val listId = when (val parsed = ListIdParser.parse(input)) {
            is ListIdResult.Valid -> parsed.listId
            is ListIdResult.Invalid -> throw ImportException(parsed.reason)
        }
        val imported = importer.import(listId)
        saveImported(imported)
        return imported.id
    }

    internal suspend fun saveImported(imported: ImportedPlaylist, requireExisting: Boolean = false) {
        if (imported.tracks.isEmpty()) throw ImportException("This playlist has no tracks")

        db.withTransaction {
            val previous = playlists.getPlaylist(imported.id)
            if (requireExisting && previous == null) throw ImportException("Playlist was deleted")
            playlists.upsertPlaylist(
                PlaylistEntity(
                    id = imported.id,
                    title = imported.title,
                    owner = imported.owner,
                    thumbnailUrl = imported.thumbnailUrl,
                    sourceUrl = "https://music.youtube.com/playlist?list=${imported.id}",
                    trackCount = imported.tracks.size,
                    importedAt = previous?.importedAt ?: System.currentTimeMillis(),
                    localTitle = previous?.localTitle,
                ),
            )
            tracks.upsertTracks(imported.tracks.map { it.toEntity() }.distinctBy { it.videoId })
            playlists.clearPlaylistTracks(imported.id)
            playlists.insertPlaylistTracks(
                imported.tracks.mapIndexed { index, track ->
                    PlaylistTrackEntity(imported.id, index, track.videoId)
                },
            )
        }
    }

    private fun PlaylistEntity.toSummary() = PlaylistSummary(
        id = id,
        title = localTitle ?: title,
        owner = owner,
        thumbnailUrl = thumbnailUrl,
        trackCount = trackCount,
        sourceUrl = sourceUrl,
        importedAt = importedAt,
    )
}

internal fun TrackEntity.toDomain() = Track(videoId, title, artist, durationMs, thumbnailUrl)

internal fun Track.toEntity() = TrackEntity(videoId, title, artist, durationMs, thumbnailUrl)
