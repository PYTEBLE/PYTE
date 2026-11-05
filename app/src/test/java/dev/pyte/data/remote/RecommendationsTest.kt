package dev.pyte.data.remote

import dev.pyte.domain.Track
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class RecommendationsTest {
    private fun song(id: String, artist: String = id) = Track(id, "Song $id", artist, 120000, null)
    private val seed = song("YGr2G2fc48U")
    private val other = song("dQw4w9WgXcQ")
    private val radio = """{"contents":[{"playlistPanelVideoRenderer":{
      "videoId":"dQw4w9WgXcQ", "title":{"runs":[{"text":"A new song"}]},
      "longBylineText":{"runs":[{"text":"Artist"},{"text":" • "},{"text":"Album"}]},
      "lengthText":{"simpleText":"3:21"},
      "thumbnail":{"thumbnails":[{"url":"small","width":60},{"url":"large","width":500}]}
    }},{"playlistPanelVideoRenderer":{"videoId":"bad"}},
    {"playlistPanelVideoRenderer":{"videoId":"aaaaaaaaaaa","title":{"simpleText":"Unavailable"},"unplayable":true}}]}"""

    @Test fun `radio parser handles runs duration cover resolution and unavailable entries`() {
        val parsed = RadioParser.parse(Json.parseToJsonElement(radio).jsonObject).single()
        assertEquals("dQw4w9WgXcQ", parsed.videoId)
        assertEquals("A new song", parsed.title)
        assertEquals("Artist", parsed.artist)
        assertEquals(201000L, parsed.durationMs)
        assertEquals("large", parsed.thumbnailUrl)
        assertTrue(RadioParser.parse(Json.parseToJsonElement("{}").jsonObject).isEmpty())
    }

    @Test fun `seed sampling spans the playlist and removes duplicates`() {
        val songs = (0..12).map { song(it.toString().padStart(11, '0')) }
        assertEquals(listOf(songs[0], songs[4], songs[8], songs[12]), recommendationSeeds(songs + songs))
        assertEquals(listOf(seed), recommendationSeeds(listOf(seed, seed)))
        assertTrue(recommendationSeeds(emptyList()).isEmpty())
    }

    @Test fun `ranking combines sources removes existing songs and avoids artist domination`() {
        val common = song("aaaaaaaaaaa", "Shared artist")
        val many = (0..9).map { song(it.toString().padStart(11, '0'), "Same artist") }
        val ranked = rankRecommendations(listOf(seed to (listOf(seed, common) + many), other to listOf(common)), setOf(seed.videoId))
        assertEquals(common.videoId, ranked.first().track.videoId)
        assertFalse(ranked.any { it.track.videoId == seed.videoId })
        assertEquals(1, ranked.count { it.track.videoId == common.videoId })
        assertEquals(4, ranked.count { it.track.artist == "Same artist" })
    }

    @Test fun `requests are anonymous and partial failures retain working sources`() = runBlocking {
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            assertEquals("/youtubei/v1/next", request.url.encodedPath)
            assertNull(request.header("Authorization"))
            assertNull(request.header("Cookie"))
            val body = Json.parseToJsonElement(Buffer().also { request.body!!.writeTo(it) }.readUtf8()).jsonObject
            val id = body.getValue("videoId").jsonPrimitive.content
            assertEquals("RDAMVM$id", body.getValue("playlistId").jsonPrimitive.content)
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(if (id == other.videoId) 503 else 200).message("test").body(radio.toResponseBody()).build()
        }.build()
        val batch = RecommendationsRepository(InnerTubeClient(http, Json)).discover(listOf(seed, other), emptySet())
        assertTrue(batch.partial)
        // The one successful source returned a song already among the seeds: never suggest it.
        assertTrue(batch.songs.isEmpty())
        try {
            RecommendationsRepository(InnerTubeClient(http, Json)).discover(listOf(other), emptySet())
            fail("All failed sources must report an error")
        } catch (_: IllegalStateException) { }
    }

    @Test fun `live anonymous radio yields new recommendations`() = runBlocking {
        assumeTrue(System.getenv("PYTE_LIVE_RECOMMENDATIONS") == "true")
        val batch = RecommendationsRepository(InnerTubeClient(OkHttpClient(), Json)).discover(listOf(seed), emptySet())
        assertTrue(batch.songs.isNotEmpty())
        assertFalse(batch.songs.any { it.track.videoId == seed.videoId })
        assertEquals(batch.songs.size, batch.songs.map { it.track.videoId }.distinct().size)
    }
}
