package dev.pyte.data.remote

import dev.pyte.domain.Track
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class PlaylistPage(
    val title: String?,
    val owner: String?,
    val thumbnailUrl: String?,
    val tracks: List<Track>,
    val continuation: String?,
)

/** Parses a WEB_REMIX `browse` response for a playlist (first page or continuation). */
object PlaylistParser {
    private val durationPattern = Regex("""^(\d{1,2}:)?\d{1,2}:\d{2}$""")

    fun parse(root: JsonObject): PlaylistPage {
        val header = findObjects(root, "musicResponsiveHeaderRenderer").firstOrNull()
            ?: findObjects(root, "musicEditablePlaylistDetailHeaderRenderer").firstOrNull()?.get("header")
                ?.asObject()?.get("musicResponsiveHeaderRenderer")?.asObject()

        val title = header?.get("title")?.let(::runsText)?.takeIf { it.isNotBlank() }
        val owner = header?.get("straplineTextOne")?.let(::runsText)?.takeIf { it.isNotBlank() }
        val thumbnail = header?.let { thumbnailUrl(it) }

        val tracks = findObjects(root, "musicResponsiveListItemRenderer").mapNotNull { parseTrack(it) }

        val continuation = findObjects(root, "continuationItemRenderer")
            .firstNotNullOfOrNull { renderer ->
                renderer["continuationEndpoint"]?.asObject()?.get("continuationCommand")?.asObject()
                    ?.get("token")?.asString()
            }
            ?: findObjects(root, "nextContinuationData").firstNotNullOfOrNull { it["continuation"]?.asString() }

        return PlaylistPage(title, owner, thumbnail, tracks, continuation)
    }

    internal fun parseTrack(item: JsonObject, searchResult: Boolean = false): Track? {
        val flex = item["flexColumns"]?.asArray().orEmpty()
        val titleText = flex.getOrNull(0)?.asObject()?.get("musicResponsiveListItemFlexColumnRenderer")
            ?.asObject()?.get("text")?.asObject()
        val searchVideoId = if (searchResult) titleText?.get("runs")?.asArray()?.firstOrNull()
            ?.asObject()?.get("navigationEndpoint")?.asObject()?.get("watchEndpoint")
            ?.asObject()?.get("videoId")?.asString() else null
        val videoId = item["playlistItemData"]?.asObject()?.get("videoId")?.asString()
            ?: searchVideoId ?: return null
        if (!Regex("[A-Za-z0-9_-]{11}").matches(videoId)) return null

        val titleRuns = flex.getOrNull(0)?.columnText()
        val title = titleRuns?.let { runs -> runs.joinToString("") { it.text } }?.trim().orEmpty()

        val artistRuns = flex.getOrNull(1)?.columnText().orEmpty()
            .takeWhile { it.text.trim() != "•" }
        val artist = artistRuns.joinToString("") { it.text }.trim().ifBlank { "Unknown artist" }

        val durationText = (flex.drop(1) + item["fixedColumns"]?.asArray().orEmpty())
            .asSequence()
            .flatMap { it.columnText().orEmpty().asSequence() }
            .map { it.text.trim() }
            .firstOrNull { durationPattern.matches(it) }

        return Track(
            videoId = videoId,
            title = title.ifBlank { "Untitled" },
            artist = artist,
            durationMs = durationText?.let(::parseDurationMs) ?: 0L,
            thumbnailUrl = thumbnailUrl(item),
        )
    }

    private fun JsonElement.columnText(): List<Run>? {
        val text = asObject()
            ?.get("musicResponsiveListItemFlexColumnRenderer")?.asObject()
            ?.get("text")?.asObject() ?: asObject()
            ?.get("musicResponsiveListItemFixedColumnRenderer")?.asObject()
            ?.get("text")?.asObject()
            ?: return null
        return text["runs"]?.asArray()?.mapNotNull { run ->
            run.asObject()?.get("text")?.asString()?.let { Run(it) }
        } ?: text["simpleText"]?.asString()?.let { listOf(Run(it)) }
    }

    private data class Run(val text: String)

    private fun runsText(element: JsonElement): String? {
        val obj = element.asObject() ?: return null
        obj["simpleText"]?.asString()?.let { return it }
        return obj["runs"]?.asArray()?.joinToString("") { it.asObject()?.get("text")?.asString().orEmpty() }
    }

    private fun thumbnailUrl(obj: JsonObject): String? =
        findObjects(obj, "musicThumbnailRenderer").firstOrNull()
            ?.get("thumbnail")?.asObject()
            ?.get("thumbnails")?.asArray()
            ?.lastOrNull()?.asObject()
            ?.get("url")?.asString()

    private fun parseDurationMs(text: String): Long {
        val parts = text.split(":").map { it.toLongOrNull() ?: 0L }
        val seconds = parts.fold(0L) { acc, part -> acc * 60 + part }
        return seconds * 1000
    }

    /** Depth-first search for every object stored under [key], in document order. */
    internal fun findObjects(root: JsonElement, key: String): List<JsonObject> {
        val out = mutableListOf<JsonObject>()
        fun visit(element: JsonElement) {
            when (element) {
                is JsonObject -> element.forEach { (k, v) ->
                    if (k == key && v is JsonObject) out += v
                    visit(v)
                }
                is JsonArray -> element.forEach(::visit)
                else -> Unit
            }
        }
        visit(root)
        return out
    }

    private fun JsonElement.asObject(): JsonObject? = this as? JsonObject
    private fun JsonElement.asArray(): JsonArray? = this as? JsonArray
    private fun JsonElement.asString(): String? = (this as? JsonPrimitive)?.contentOrNull
}
