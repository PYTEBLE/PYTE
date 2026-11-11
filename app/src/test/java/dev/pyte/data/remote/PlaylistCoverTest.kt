package dev.pyte.data.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

class PlaylistCoverTest {
    private val cover = """{"musicThumbnailRenderer":{"thumbnail":{"thumbnails":[
      {"url":"https://example.com/playlist-small.jpg","width":60,"height":60},
      {"url":"https://example.com/playlist-cover.jpg","width":1200,"height":1200}]}}}"""
    private val avatar = """{"musicThumbnailRenderer":{"thumbnail":{"thumbnails":[
      {"url":"https://example.com/avatar.jpg","width":1200,"height":1200}]}}}"""
    private fun parse(header: String) = PlaylistParser.parse(Json.parseToJsonElement("""{"header":$header}""").jsonObject)

    @Test fun `legacy playlist detail headers keep the playlist photo and title`() {
        val result = parse("""{"musicDetailHeaderRenderer":{"title":{"runs":[{"text":"Meine Playlist"}]},"thumbnail":$cover}}""")
        assertEquals("Meine Playlist", result.title)
        assertEquals("https://example.com/playlist-cover.jpg", result.thumbnailUrl)
    }

    @Test fun `editable legacy wrapper preserves the custom photo`() {
        val result = parse("""{"musicEditablePlaylistDetailHeaderRenderer":{"header":{"musicDetailHeaderRenderer":{
            "title":{"simpleText":"Eigene Playlist"},"thumbnail":$cover}}}}""")
        assertEquals("Eigene Playlist", result.title)
        assertEquals("https://example.com/playlist-cover.jpg", result.thumbnailUrl)
    }

    @Test fun `owner avatar never replaces the playlist cover regardless of field order`() {
        val result = parse("""{"musicResponsiveHeaderRenderer":{"straplineThumbnail":$avatar,
            "title":{"simpleText":"Playlist"},"thumbnail":$cover}}""")
        assertEquals("https://example.com/playlist-cover.jpg", result.thumbnailUrl)
    }

    @Test fun `cropped square custom thumbnails and direct thumbnail lists are supported`() {
        for (thumbnail in listOf(
            """{"croppedSquareThumbnailRenderer":{"thumbnail":{"thumbnails":[{"url":"https://example.com/custom.jpg","width":1200,"height":1200}]}}}""",
            """{"thumbnails":[{"url":"https://example.com/custom.jpg","width":1200,"height":1200}]}""",
        )) {
            val result = parse("""{"musicResponsiveHeaderRenderer":{"title":{"simpleText":"Playlist"},"thumbnail":$thumbnail}}""")
            assertEquals("https://example.com/custom.jpg", result.thumbnailUrl)
        }
    }

    @Test fun `missing primary cover never promotes an owner avatar to cover`() {
        val result = parse("""{"musicResponsiveHeaderRenderer":{"title":{"simpleText":"Playlist"},"straplineThumbnail":$avatar}}""")
        assertNull(result.thumbnailUrl)
    }

    @Test fun `importer keeps playlist photo separate from first song artwork`() = runBlocking {
        val response = """{"header":{"musicDetailHeaderRenderer":{"title":{"simpleText":"My photo"},"thumbnail":$cover}},
          "contents":[{"musicResponsiveListItemRenderer":{"playlistItemData":{"videoId":"dQw4w9WgXcQ"},
            "flexColumns":[{"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Song"}]}}}],
            "thumbnail":${cover.replace("playlist-cover.jpg", "song-cover.jpg")}}}]}"""
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("test").body(response.toResponseBody()).build()
        }.build()
        val result = PlaylistImporter(InnerTubeClient(http, Json)).import("PLfixture")
        assertEquals("My photo", result.title)
        assertEquals("https://example.com/playlist-cover.jpg", result.thumbnailUrl)
        assertEquals("https://example.com/song-cover.jpg", result.tracks.single().thumbnailUrl)
    }
}
