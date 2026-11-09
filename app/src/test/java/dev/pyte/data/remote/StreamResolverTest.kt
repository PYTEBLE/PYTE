package dev.pyte.data.remote

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.withContext
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import okhttp3.Call
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class StreamResolverTest {
    @Test fun `canceling a speculative audio probe cancels its network call and does not cache it`() = runBlocking {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val call = AtomicReference<Call>()
        var loads = 0
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            call.set(chain.call()); entered.countDown()
            check(release.await(5, TimeUnit.SECONDS))
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(206).message("test").body("x".toResponseBody()).build()
        }.build()
        val resolver = StreamResolver({ _, c -> loads++; player(c) }, client, { 0L })
        val job = launch { resolver.resolve(videoId) }
        try {
            assertTrue(withContext(Dispatchers.IO) { entered.await(5, TimeUnit.SECONDS) })
            job.cancelAndJoin()
            assertTrue(call.get().isCanceled())
            assertEquals(1, loads)
            release.countDown()
            resolver.resolve(videoId)
            assertEquals(2, loads)
        } finally { release.countDown(); job.cancelAndJoin() }
    }
    @Test fun `data saver chooses best under cap or lowest available without changing normal AAC selection`() {
        fun format(itag: Int, bitrate: Int) = Json.parseToJsonElement("""{"itag":$itag,"bitrate":$bitrate}""") as JsonObject
        val formats = listOf(format(140, 128000), format(249, 48000), format(250, 64000), format(251, 160000))
        assertEquals(formats[0], selectAudioFormat(formats, false))
        assertEquals(formats[2], selectAudioFormat(formats, true))
        assertEquals(formats[0], selectAudioFormat(listOf(formats[0], formats[3]), true))
        assertNull(selectAudioFormat(emptyList(), true))
    }

    @Test fun `normal and data saver lookups do not contaminate each others cache and both invalidate`() = runBlocking {
        var loads = 0
        val resolver = StreamResolver({ _, client -> loads++; player(client) }, http { _, _ -> 206 }, { 0L })
        resolver.resolve(videoId); resolver.resolve(videoId, true)
        resolver.resolve(videoId); resolver.resolve(videoId, true)
        assertEquals(2, loads)
        resolver.invalidate(videoId)
        resolver.resolve(videoId); resolver.resolve(videoId, true)
        assertEquals(4, loads)
    }
    private val videoId = "dQw4w9WgXcQ"

    private fun player(client: PlayerClient, format: Int = 140): JsonObject = Json.parseToJsonElement(
        """{"playabilityStatus":{"status":"OK"},"streamingData":{"adaptiveFormats":[
            {"itag":$format,"mimeType":"audio/mp4","bitrate":130000,
             "url":"https://audio.example/${client.name}?expire=9999999999"}
        ]}}""",
    ) as JsonObject

    private fun http(status: (String, String?) -> Int): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request()
            assertEquals("bytes=0-0", request.header("Range"))
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(status(request.url.encodedPath, request.header("User-Agent")))
                .message("test response").body("x".toResponseBody()).build()
        }.build()

    @Test
    fun `HTTP 403 on initial clients falls back to iOS with matching headers`() = runBlocking {
        val loaded = mutableListOf<PlayerClient>()
        val headers = mutableListOf<String?>()
        val resolver = StreamResolver({ _, client -> loaded += client; player(client) }, http { path, ua ->
            headers += ua
            if (path.contains("IOS")) 206 else 403
        }, { 0L })

        val result = resolver.resolve(videoId)

        assertEquals(listOf(PlayerClient.VISIONOS, PlayerClient.ANDROID_VR, PlayerClient.IOS), loaded)
        assertEquals(loaded.map { it.userAgent }, headers)
        assertEquals(PlayerClient.IOS, result.client)
        assertEquals(PlayerClient.IOS.userAgent, result.userAgent)
    }

    @Test
    fun `successful player response does not hide denied audio on all clients`() = runBlocking {
        val resolver = StreamResolver({ _, client -> player(client) }, http { _, _ -> 403 }, { 0L })
        try {
            resolver.resolve(videoId)
            fail("Expected inaccessible audio to be rejected")
        } catch (e: StreamUnavailableException) {
            assertTrue(e.message!!.contains("HTTP 403"))
            assertFalse(e.message!!.contains("https://"))
            assertEquals(listOf("VISIONOS", "ANDROID_VR", "IOS"), e.attempts.map { it.client })
            assertTrue(e.attempts.all { it.stage == "audio-probe" })
            assertTrue(e.attempts.all { it.reason == "HTTP 403" })
        }
    }

    @Test
    fun `player rejection preserves status separately from audio probe errors`() = runBlocking {
        val resolver = StreamResolver({ _, _ ->
            Json.parseToJsonElement("""{"playabilityStatus":{"status":"LOGIN_REQUIRED"}}""") as JsonObject
        }, http { _, _ -> fail("Probe must not run"); 500 }, { 0L })
        try {
            resolver.resolve(videoId)
            fail("Expected player rejection")
        } catch (e: StreamUnavailableException) {
            assertEquals(3, e.attempts.size)
            assertTrue(e.attempts.all { it.stage == "audio-format" && it.reason == "LOGIN_REQUIRED" })
        }
    }

    @Test
    fun `player API HTTP errors retain stage and status`() = runBlocking {
        val resolver = StreamResolver({ _, _ -> throw InnerTubeException("denied", 429) },
            http { _, _ -> 206 }, { 0L })
        try {
            resolver.resolve(videoId)
            fail("Expected API failure")
        } catch (e: StreamUnavailableException) {
            assertTrue(e.attempts.all { it.stage == "player-api" && it.reason == "HTTP 429" })
        }
    }

    @Test
    fun `valid cached URL is reused without another probe`() = runBlocking {
        var probes = 0
        val resolver = StreamResolver({ _, client -> player(client) }, http { _, _ -> probes++; 206 }, { 0L })
        assertEquals(resolver.resolve(videoId), resolver.resolve(videoId))
        assertEquals(1, probes)
    }

    @Test
    fun `late stream denial skips rejected client on retry`() = runBlocking {
        val loaded = mutableListOf<PlayerClient>()
        val resolver = StreamResolver({ _, client -> loaded += client; player(client) }, http { _, _ -> 206 }, { 0L })
        resolver.resolve(videoId)
        resolver.invalidate(videoId, rejectClient = true)
        assertEquals(PlayerClient.ANDROID_VR, resolver.resolve(videoId).client)
        assertEquals(listOf(PlayerClient.VISIONOS, PlayerClient.ANDROID_VR), loaded)
    }

    @Test
    fun `rejected client is eligible again after cooldown`() = runBlocking {
        var now = 0L
        val resolver = StreamResolver({ _, client -> player(client) }, http { _, _ -> 206 }, { now })
        resolver.resolve(videoId)
        resolver.invalidate(videoId, rejectClient = true)
        now = 6 * 60_000L
        assertEquals(PlayerClient.VISIONOS, resolver.resolve(videoId).client)
    }

    @Test
    fun `cancellation does not trigger another client`() = runBlocking {
        var calls = 0
        val resolver = StreamResolver({ _, _ -> calls++; throw CancellationException() }, http { _, _ -> 206 }, { 0L })
        try {
            resolver.resolve(videoId)
            fail("Expected cancellation")
        } catch (_: CancellationException) {
            assertEquals(1, calls)
        }
    }

    @Test
    fun `concurrent resolutions share one validated stream`() = runBlocking {
        var probes = 0
        val resolver = StreamResolver({ _, client -> player(client) }, http { _, _ -> probes++; 206 }, { 0L })
        coroutineScope { List(5) { async { resolver.resolve(videoId) } }.awaitAll() }
        assertEquals(1, probes)
    }

    @Test
    fun `different audio formats cannot share cached bytes`() {
        val a = ResolvedStream("https://audio.example/a", PlayerClient.ANDROID_VR, "251", 1L)
        val b = a.copy(formatId = "140")
        assertNotEquals(a.cacheKey(videoId), b.cacheKey(videoId))
        assertNotEquals(videoId, a.cacheKey(videoId))
    }
}
