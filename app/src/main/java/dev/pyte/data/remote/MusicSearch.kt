package dev.pyte.data.remote

import dev.pyte.domain.Track
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import javax.inject.Inject
import javax.inject.Singleton

data class MusicSearchPage(val tracks: List<Track>, val continuation: String?)

object MusicSearchParser {
    fun parse(root: JsonObject): MusicSearchPage {
        val tracks = PlaylistParser.findObjects(root, "musicResponsiveListItemRenderer")
            .mapNotNull { PlaylistParser.parseTrack(it, searchResult = true) }.distinctBy { it.videoId }
        val continuation = PlaylistParser.findObjects(root, "nextContinuationData")
            .firstNotNullOfOrNull { (it["continuation"] as? JsonPrimitive)?.contentOrNull }
            ?: PlaylistParser.findObjects(root, "continuationCommand")
                .firstNotNullOfOrNull { (it["token"] as? JsonPrimitive)?.contentOrNull }
        return MusicSearchPage(tracks, continuation)
    }
}

@Singleton
class MusicSearch @Inject constructor(private val client: InnerTubeClient) {
    suspend fun search(query: String, continuation: String? = null): MusicSearchPage {
        require(query.isNotBlank() && query.length <= 200) { "Use a search between 1 and 200 characters" }
        return MusicSearchParser.parse(client.search(query.trim(), continuation))
    }
}
