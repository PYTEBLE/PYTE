package dev.pyte.data.remote

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test

class AnonymousPlayerSessionTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun requestJson(request: Request) = json.parseToJsonElement(
        Buffer().also { request.body!!.writeTo(it) }.readUtf8(),
    ).jsonObject

    @Test
    fun `anonymous visitor is acquired before playback and reused without cookies`() = runBlocking {
        val requests = mutableListOf<Request>()
        val nonces = mutableListOf<String>()
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            requests += request
            assertNull(request.header("Cookie"))
            assertNull(request.header("Authorization"))
            assertEquals(PlayerClient.VISIONOS.userAgent, request.header("User-Agent"))
            assertEquals("2", request.header("X-Goog-Api-Format-Version"))
            val data = requestJson(request)
            val client = data.getValue("context").jsonObject.getValue("client").jsonObject
            assertEquals("VISIONOS", client.getValue("clientName").jsonPrimitive.content)
            val response = if (request.url.encodedPath.endsWith("visitor_id")) {
                assertEquals("www.youtube.com", request.url.host)
                assertFalse(client.containsKey("visitorData"))
                """{"responseContext":{"visitorData":"anonymous-test-session"}}"""
            } else {
                assertEquals("youtubei.googleapis.com", request.url.host)
                assertEquals("anonymous-test-session", client.getValue("visitorData").jsonPrimitive.content)
                assertEquals(data.getValue("videoId").jsonPrimitive.content, request.url.queryParameter("id"))
                assertTrue(request.url.queryParameter("t")!!.matches(Regex("[A-Za-z0-9_-]{12}")))
                nonces += data.getValue("cpn").jsonPrimitive.content
                """{"playabilityStatus":{"status":"OK"},"streamingData":{"adaptiveFormats":[
                    {"itag":140,"mimeType":"audio/mp4","url":"https://audio.example/media?sig=keep-me"}
                ]}}"""
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200)
                .message("OK").body(response.toResponseBody()).build()
        }.build()
        val client = InnerTubeClient(http, json)
        val first = client.player("YGr2G2fc48U", PlayerClient.VISIONOS)
        client.player("dQw4w9WgXcQ", PlayerClient.VISIONOS)

        assertEquals(3, requests.size)
        assertNotEquals(nonces[0], nonces[1])
        val streamUrl = first.getValue("streamingData").jsonObject.getValue("adaptiveFormats")
            .jsonArray[0].jsonObject.getValue("url").jsonPrimitive.content
        assertTrue(streamUrl.contains("cpn=${nonces.first()}"))
        assertTrue(streamUrl.contains("sig=keep-me"))
    }

    @Test
    fun `missing visitor data fails before sending a player request`() = runBlocking {
        var calls = 0
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            calls++
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200)
                .message("OK").body("{}".toResponseBody()).build()
        }.build()
        try {
            InnerTubeClient(http, json).player("YGr2G2fc48U", PlayerClient.VISIONOS)
            fail("Expected missing visitor session to fail")
        } catch (_: InnerTubeException) {
            assertEquals(1, calls)
        }
    }
}
