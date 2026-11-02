package dev.pyte.data.remote

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
import org.junit.Test
import org.junit.Assume.assumeTrue

class MusicSearchTest {
    @Test fun `live search finds the reported song and paginates`() = runBlocking {
        assumeTrue(System.getenv("PYTE_LIVE_SEARCH") == "true")
        val search = MusicSearch(InnerTubeClient(OkHttpClient(), Json))
        val page = search.search("schönhauser Gustav")
        assertTrue(page.tracks.any { it.videoId == "YGr2G2fc48U" })
        assertTrue(page.tracks.all { it.title.isNotBlank() && it.artist.isNotBlank() })
        val next = search.search("schönhauser Gustav", requireNotNull(page.continuation))
        assertTrue(next.tracks.isNotEmpty())
    }

    private val song = """{"musicResponsiveListItemRenderer":{
      "flexColumns":[
        {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"schönhauser", "navigationEndpoint":{"watchEndpoint":{"videoId":"YGr2G2fc48U"}}}]}}},
        {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Zartmann"},{"text":" • "},{"text":"Album"},{"text":" • "},{"text":"2:35"}]}}}
      ],
      "thumbnail":{"musicThumbnailRenderer":{"thumbnail":{"thumbnails":[{"url":"https://image.example/song.jpg"}]}}}
    }}"""

    @Test fun `search parses title endpoint duration artist and continuation without duplicates`() {
        val root = Json.parseToJsonElement("""{"contents":[$song,$song,{"musicResponsiveListItemRenderer":{"flexColumns":[]}}],"continuations":[{"nextContinuationData":{"continuation":"page-two"}}]}""").jsonObject
        val page = MusicSearchParser.parse(root)
        assertEquals("page-two", page.continuation)
        val track = page.tracks.single()
        assertEquals("YGr2G2fc48U", track.videoId)
        assertEquals("schönhauser", track.title)
        assertEquals("Zartmann", track.artist)
        assertEquals(155000L, track.durationMs)
        assertEquals("https://image.example/song.jpg", track.thumbnailUrl)
    }

    @Test fun `empty results and command style continuation parse safely`() {
        assertTrue(MusicSearchParser.parse(Json.parseToJsonElement("{}").jsonObject).tracks.isEmpty())
        assertEquals("next", MusicSearchParser.parse(Json.parseToJsonElement("""{"continuationItemRenderer":{"continuationEndpoint":{"continuationCommand":{"token":"next"}}}}""").jsonObject).continuation)
    }

    @Test fun `search is anonymous filtered to songs and supports paginated requests`() = runBlocking {
        var calls = 0
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            assertEquals("music.youtube.com", request.url.host)
            assertEquals("/youtubei/v1/search", request.url.encodedPath)
            assertNull(request.header("Authorization"))
            assertNull(request.header("Cookie"))
            val body = Json.parseToJsonElement(Buffer().also { request.body!!.writeTo(it) }.readUtf8()).jsonObject
            assertEquals("WEB_REMIX", body.getValue("context").jsonObject.getValue("client").jsonObject.getValue("clientName").jsonPrimitive.content)
            if (calls++ == 0) {
                assertEquals("Gustav", body.getValue("query").jsonPrimitive.content)
                assertTrue(body.containsKey("params"))
            } else {
                assertEquals("page-two", body.getValue("continuation").jsonPrimitive.content)
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("""{"contents":[$song]}""".toResponseBody()).build()
        }.build()
        val search = MusicSearch(InnerTubeClient(http, Json))
        assertEquals(1, search.search(" Gustav ").tracks.size)
        assertEquals(1, search.search("Gustav", "page-two").tracks.size)
        assertEquals(2, calls)
    }
}
