package dev.pyte.data.remote

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import javax.inject.Inject
import javax.inject.Singleton

data class CatalogPlaylist(val id: String, val title: String, val subtitle: String, val thumbnailUrl: String?, val charts: Boolean = false) {
    val url: String get() = "https://music.youtube.com/playlist?list=$id"
}

data class PlaylistCatalogBatch(val playlists: List<CatalogPlaylist>, val partial: Boolean)

/** Parse playlist navigation only: album menus also contain playlist IDs and are not suggestions. */
internal fun parseCatalogPlaylists(root: JsonObject, charts: Boolean = false): List<CatalogPlaylist> =
    PlaylistParser.findObjects(root, "musicTwoRowItemRenderer").mapNotNull { item ->
        val endpoint = (item["navigationEndpoint"] as? JsonObject)?.get("browseEndpoint") as? JsonObject ?: return@mapNotNull null
        val browseId = (endpoint["browseId"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
        if (!browseId.startsWith("VL")) return@mapNotNull null
        val id = browseId.removePrefix("VL")
        val url = "https://music.youtube.com/playlist?list=$id"
        if (ListIdParser.parse(url) !is ListIdResult.Valid) return@mapNotNull null
        val title = catalogText(item["title"]).trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        val thumbnail = PlaylistParser.findObjects(item, "musicThumbnailRenderer").firstOrNull()
            ?.get("thumbnail") as? JsonObject
        val images = thumbnail?.get("thumbnails") as? JsonArray
        val image = images?.mapNotNull { it as? JsonObject }?.maxByOrNull {
            (it["width"] as? JsonPrimitive)?.intOrNull ?: 0
        }?.get("url")?.let { (it as? JsonPrimitive)?.contentOrNull }?.takeIf { it.startsWith("https://") }
        CatalogPlaylist(id, title, catalogText(item["subtitle"]), image, charts)
    }.distinctBy { it.id }

private fun catalogText(element: JsonElement?): String {
    val obj = element as? JsonObject ?: return ""
    return (obj["simpleText"] as? JsonPrimitive)?.contentOrNull
        ?: (obj["runs"] as? JsonArray)?.joinToString("") {
            ((it as? JsonObject)?.get("text") as? JsonPrimitive)?.contentOrNull.orEmpty()
        }.orEmpty()
}

@Singleton
class PlaylistCatalog @Inject constructor(private val client: InnerTubeClient) {
    private val lock = Mutex()
    private var cached: Pair<Long, PlaylistCatalogBatch>? = null

    suspend fun load(refresh: Boolean = false): PlaylistCatalogBatch = lock.withLock {
        cached?.takeIf { !refresh && System.currentTimeMillis() - it.first in 0 until 15 * 60_000L }
            ?.let { return@withLock it.second }
        val pages = coroutineScope {
            listOf("FEmusic_charts", "FEmusic_home").map { id -> async {
                try { parseCatalogPlaylists(client.browse(browseId = id, localized = true), charts = id == "FEmusic_charts") }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { emptyList() }
            } }.awaitAll()
        }
        // Sample across the editorial page rather than filling the row with its first genre shelf.
        val selections = pages.map { list ->
            if (list.size <= 8) list else (0..7).map { list[it * list.lastIndex / 7] }
        }
        val mixed = (0 until (selections.maxOfOrNull { it.size } ?: 0)).flatMap { index -> selections.mapNotNull { it.getOrNull(index) } }
            .distinctBy { it.id }.take(12)
        check(mixed.isNotEmpty()) { "No public playlists available" }
        PlaylistCatalogBatch(mixed, pages.any { it.isEmpty() }).also { cached = System.currentTimeMillis() to it }
    }
}
