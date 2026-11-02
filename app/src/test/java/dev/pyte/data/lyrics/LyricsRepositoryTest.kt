package dev.pyte.data.lyrics

import android.app.Application
import dev.pyte.domain.Track
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class LyricsRepositoryTest {
    private val track = Track("dQw4w9WgXcQ", "A title", "An artist", 123_000, null)
    private val context get() = RuntimeEnvironment.getApplication()
    private fun http(code: Int, text: String, onRequest: (Request) -> Unit = {}) = OkHttpClient.Builder()
        .addInterceptor { chain ->
            onRequest(chain.request())
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("Test")
                .header("Retry-After", "120").body(text.toResponseBody()).build()
        }.build()

    private fun clearFixture() {
        File(context.cacheDir, "lyrics/${track.videoId}.json").delete()
        File(context.cacheDir, "lyrics/${track.videoId}.miss").delete()
        File(context.filesDir, "offline/${track.videoId}.lyrics").delete()
    }

    @Test fun `lyrics are matched by title artist duration and pinned for offline use`() = runBlocking {
        clearFixture()
        try {
            var calls = 0
            val repository = LyricsRepository(context, http(200,
                """{"syncedLyrics":"[00:01.00] Hello","plainLyrics":"Hello","instrumental":false}""") { request ->
                calls++
                assertEquals("A title", request.url.queryParameter("track_name"))
                assertEquals("An artist", request.url.queryParameter("artist_name"))
                assertEquals("123.0", request.url.queryParameter("duration"))
                assertTrue(request.header("User-Agent")!!.startsWith("PYTE/"))
            }, Json)
            assertEquals("Hello", repository.get(track, pin = true)!!.lines.single().text)
            File(context.cacheDir, "lyrics/${track.videoId}.json").delete()
            val offline = LyricsRepository(context, http(500, "") { error("Pinned lyrics must not use network") }, Json)
            assertEquals(1000L, offline.get(track)!!.lines.single().timeMs)
            assertEquals(1, calls)
        } finally { clearFixture() }
    }

    @Test fun `missing lyrics are cached without making up text`() = runBlocking {
        clearFixture()
        try {
            var calls = 0
            val repository = LyricsRepository(context, http(404, "") { calls++ }, Json)
            assertNull(repository.get(track))
            assertNull(repository.get(track))
            assertEquals(1, calls)
        } finally { clearFixture() }
    }

    @Test fun `rate limit honors retry after without repeated requests`() = runBlocking {
        clearFixture()
        try {
            var calls = 0
            val repository = LyricsRepository(context, http(429, "") { calls++ }, Json)
            repeat(2) {
                try { repository.get(track); fail("Rate-limited lyrics should be unavailable") }
                catch (_: IOException) { }
            }
            assertEquals(1, calls)
        } finally { clearFixture() }
    }
}
