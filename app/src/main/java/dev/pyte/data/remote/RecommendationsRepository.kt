package dev.pyte.data.remote

import dev.pyte.domain.Track
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.*
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class SongRecommendation(val track: Track, val seedTitle: String)
data class RecommendationBatch(val songs: List<SongRecommendation>, val partial: Boolean)

object RadioParser {
    fun parse(root: JsonObject): List<Track> = PlaylistParser.findObjects(root, "playlistPanelVideoRenderer")
        .mapNotNull { item ->
            if ((item["unplayable"] as? JsonPrimitive)?.booleanOrNull == true) return@mapNotNull null
            val id = (item["videoId"] as? JsonPrimitive)?.contentOrNull
                ?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{11}")) } ?: return@mapNotNull null
            val title = text(item["title"]).trim().takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val byline = text(item["longBylineText"]).ifBlank { text(item["shortBylineText"]) }
            val duration = text(item["lengthText"]).split(":")
                .takeIf { it.size in 2..3 && it.all { part -> part.toLongOrNull() != null } }
                ?.fold(0L) { acc, part -> acc * 60 + part.toLong() }?.times(1000) ?: 0L
            val images = (item["thumbnail"] as? JsonObject)?.get("thumbnails") as? JsonArray
            val artwork = images?.mapNotNull { it as? JsonObject }?.maxByOrNull {
                (it["width"] as? JsonPrimitive)?.intOrNull ?: 0
            }?.get("url")?.let { (it as? JsonPrimitive)?.contentOrNull }
            Track(id, title, byline.substringBefore(" • ").ifBlank { "Unknown artist" }, duration, artwork)
        }.distinctBy { it.videoId }

    private fun text(element: JsonElement?): String {
        val obj = element as? JsonObject ?: return ""
        return (obj["simpleText"] as? JsonPrimitive)?.contentOrNull
            ?: (obj["runs"] as? JsonArray)?.joinToString("") {
                ((it as? JsonObject)?.get("text") as? JsonPrimitive)?.contentOrNull.orEmpty()
            }.orEmpty()
    }
}

/** A few evenly spaced seeds represent the whole playlist, rather than just its final song. */
internal fun recommendationSeeds(tracks: List<Track>): List<Track> {
    val unique = tracks.distinctBy { it.videoId }
    if (unique.size <= 4) return unique
    return (0..3).map { unique[it * unique.lastIndex / 3] }
}

internal fun rankRecommendations(radios: List<Pair<Track, List<Track>>>, excluded: Set<String>): List<SongRecommendation> {
    data class Candidate(val track: Track, var score: Double, val sources: MutableSet<String>)
    val candidates = linkedMapOf<String, Candidate>()
    radios.forEach { (seed, songs) ->
        songs.distinctBy { it.videoId }.forEachIndexed { index, track ->
            if (track.videoId !in excluded) {
                val score = 1.0 / (index + 5)
                val existing = candidates[track.videoId]
                if (existing == null) candidates[track.videoId] = Candidate(track, score, mutableSetOf(seed.videoId))
                else { existing.score += score; existing.sources += seed.videoId }
            }
        }
    }
    val artists = mutableMapOf<String, Int>()
    val versions = mutableSetOf<String>()
    val ranked = candidates.values.sortedByDescending { it.score }
    val used = mutableSetOf<String>()
    val result = mutableListOf<SongRecommendation>()
    fun eligible(track: Track): Boolean {
        val artist = track.artist.lowercase(Locale.ROOT).trim()
        val version = "$artist:${track.title.lowercase(Locale.ROOT).trim()}"
        return track.videoId !in used && (artists[artist] ?: 0) < 4 && version !in versions
    }
    // Relevance within each radio, round-robin between radios. Overlapping tracks can be
    // attributed to either seed, but are emitted only once across the entire result.
    do {
        val before = result.size
        for ((seed, _) in radios) {
            val available = ranked.filter { seed.videoId in it.sources && eligible(it.track) }
            val previousArtist = result.lastOrNull()?.track?.artist?.lowercase(Locale.ROOT)?.trim()
            val candidate = available.firstOrNull { it.track.artist.lowercase(Locale.ROOT).trim() != previousArtist }
                ?: available.firstOrNull() ?: continue
            val track = candidate.track
            val artist = track.artist.lowercase(Locale.ROOT).trim()
            used += track.videoId
            versions += "$artist:${track.title.lowercase(Locale.ROOT).trim()}"
            artists[artist] = (artists[artist] ?: 0) + 1
            result += SongRecommendation(track, seed.title)
            if (result.size == 30) return result
        }
    } while (result.size > before)
    return result
}

@Singleton
class RecommendationsRepository @Inject constructor(private val client: InnerTubeClient) {
    private val requests = Semaphore(2)

    suspend fun discover(tracks: List<Track>, excluded: Set<String>): RecommendationBatch = coroutineScope {
        val seeds = recommendationSeeds(tracks)
        require(seeds.isNotEmpty()) { "Add a song to this playlist first" }
        val radios = seeds.map { seed ->
            async {
                try {
                    seed to requests.withPermit { RadioParser.parse(client.songRadio(seed.videoId)) }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    seed to null
                }
            }
        }.awaitAll()
        val successful = radios.mapNotNull { (seed, songs) -> songs?.let { seed to it } }
        check(successful.isNotEmpty()) { "Could not load suggestions. Check your connection and try again." }
        RecommendationBatch(rankRecommendations(successful, excluded + tracks.map { it.videoId }), successful.size < seeds.size)
    }
}
