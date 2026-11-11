package dev.pyte.data.remote

import dev.pyte.domain.Track
import javax.inject.Inject
import javax.inject.Singleton

class ImportException(message: String) : Exception(message)

data class ImportedPlaylist(
    val id: String,
    val title: String,
    val owner: String?,
    val thumbnailUrl: String?,
    val tracks: List<Track>,
)

/** Fetches every page of a public playlist without any account. */
@Singleton
class PlaylistImporter @Inject constructor(
    private val innerTube: InnerTubeClient,
) {
    /** Cover URLs contain expiring signatures. Refresh just the header, never playlist contents. */
    suspend fun cover(listId: String): String? = PlaylistParser.parse(innerTube.browse(browseId = "VL$listId")).thumbnailUrl

    suspend fun import(listId: String): ImportedPlaylist {
        val first = PlaylistParser.parse(innerTube.browse(browseId = "VL$listId"))
        if (first.title == null && first.tracks.isEmpty()) {
            throw ImportException("Playlist not found, or it is private")
        }

        val tracks = first.tracks.toMutableList()
        var token = first.continuation
        var pages = 0
        while (token != null && pages < MAX_PAGES) {
            val page = PlaylistParser.parse(innerTube.browse(continuation = token))
            if (page.tracks.isEmpty()) break
            tracks += page.tracks
            token = page.continuation
            pages++
        }

        return ImportedPlaylist(
            id = listId,
            title = first.title ?: "Imported playlist",
            owner = first.owner,
            thumbnailUrl = first.thumbnailUrl ?: tracks.firstNotNullOfOrNull { it.thumbnailUrl },
            tracks = tracks,
        )
    }

    private companion object {
        const val MAX_PAGES = 50
    }
}
