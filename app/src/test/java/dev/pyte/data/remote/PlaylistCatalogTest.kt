package dev.pyte.data.remote

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class PlaylistCatalogTest {
    private fun item(id: String, title: String = id) = """{"musicTwoRowItemRenderer":{
      "title":{"runs":[{"text":"$title"}]},"subtitle":{"simpleText":"YouTube Music"},
      "navigationEndpoint":{"browseEndpoint":{"browseId":"$id"}},
      "thumbnailRenderer":{"musicThumbnailRenderer":{"thumbnail":{"thumbnails":[
        {"url":"https://example.com/small.jpg","width":60},{"url":"https://example.com/large.jpg","width":544}]}}}
    }}"""
    private fun page(vararg ids: String) = """{"contents":[${ids.joinToString(",") { item(it) }}]}"""

    @Test fun `parser selects real playlist links not albums or endless radios`() {
        val root = Json.parseToJsonElement(page("VLPLcharts", "VLRDCLAKcurated", "MPREalbum", "VLRDAMVMradio", "VLPLcharts")).jsonObject
        val parsed = parseCatalogPlaylists(root, charts = true)
        assertEquals(listOf("PLcharts", "RDCLAKcurated"), parsed.map { it.id })
        assertTrue(parsed.all { it.charts && it.thumbnailUrl == "https://example.com/large.jpg" })
        assertEquals("https://music.youtube.com/playlist?list=PLcharts", parsed.first().url)
        assertTrue(parseCatalogPlaylists(Json.parseToJsonElement("{}").jsonObject).isEmpty())
    }

    @Test fun `catalog interleaves charts and editorial caches requests and refreshes explicitly`() = runBlocking {
        val calls = AtomicInteger()
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            calls.incrementAndGet()
            val request = chain.request()
            assertNull(request.header("Authorization"))
            assertNull(request.header("Cookie"))
            val body = Json.parseToJsonElement(Buffer().also { request.body!!.writeTo(it) }.readUtf8()).jsonObject
            val client = body.getValue("context").jsonObject.getValue("client").jsonObject
            assertEquals("de", client.getValue("hl").jsonPrimitive.content)
            val charts = body.getValue("browseId").jsonPrimitive.content == "FEmusic_charts"
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("test")
                .body((if (charts) page("VLPLchart1", "VLPLchart2") else page("VLRDCLAKeditorial1", "VLRDCLAKeditorial2")).toResponseBody()).build()
        }.build()
        val catalog = PlaylistCatalog(InnerTubeClient(http, Json))
        val first = catalog.load()
        assertFalse(first.partial)
        assertEquals(listOf("PLchart1", "RDCLAKeditorial1", "PLchart2", "RDCLAKeditorial2"), first.playlists.map { it.id })
        assertEquals(first, catalog.load())
        assertEquals(2, calls.get())
        catalog.load(refresh = true)
        assertEquals(4, calls.get())
    }

    @Test fun `partial catalog failure preserves usable playlists while total failure reports an error`() = runBlocking {
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val charts = Buffer().also { request.body!!.writeTo(it) }.readUtf8().contains("FEmusic_charts")
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(if (charts) 200 else 503).message("test")
                .body(page("VLPLchart").toResponseBody()).build()
        }.build()
        val result = PlaylistCatalog(InnerTubeClient(http, Json)).load()
        assertTrue(result.partial)
        assertEquals("PLchart", result.playlists.single().id)
        val empty = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("test").body("{}".toResponseBody()).build()
        }.build()
        try { PlaylistCatalog(InnerTubeClient(empty, Json)).load(); fail("Empty catalog must not pretend to be populated") }
        catch (_: IllegalStateException) { }
    }

    @Test fun `live catalog returns importable public playlists`() = runBlocking {
        assumeTrue(System.getenv("PYTE_LIVE_CATALOG") == "true")
        val client = InnerTubeClient(OkHttpClient(), Json)
        val catalog = PlaylistCatalog(client).load()
        assertTrue(catalog.playlists.isNotEmpty())
        assertTrue(catalog.playlists.all { ListIdParser.parse(it.url) is ListIdResult.Valid })
        val editorial = catalog.playlists.firstOrNull { it.id.startsWith("RDCLAK") } ?: catalog.playlists.first()
        val imported = PlaylistImporter(client).import(editorial.id)
        assertTrue(imported.tracks.isNotEmpty())
        assertEquals(editorial.id, imported.id)
    }
}
