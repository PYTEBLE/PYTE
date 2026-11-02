package dev.pyte.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/** Clients used by the InnerTube API. Player clients return direct audio URLs without signature ciphers. */
enum class PlayerClient(
    val apiKey: String,
    val clientName: String,
    val clientVersion: String,
    val userAgent: String,
    val host: String,
) {
    VISIONOS(
        apiKey = "",
        clientName = "VISIONOS",
        clientVersion = "1.04",
        userAgent = "com.google.visionos.youtube/1.04(RealityDevice17,1; U; CPU visionOS 26_6_0 like Mac OS X; US)",
        host = "https://youtubei.googleapis.com",
    ),
    ANDROID_VR(
        apiKey = "AIzaSyA8eiZmM1FaDVjRy-df2KTyQ_vz_yYM39w",
        clientName = "ANDROID_VR",
        clientVersion = "1.65.10",
        userAgent = "com.google.android.apps.youtube.vr.oculus/1.65.10 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip",
        host = "https://www.youtube.com",
    ),
    IOS(
        apiKey = "AIzaSyB-63vPrdThhKuerbB2N_l7Kwwcxj6yUAc",
        clientName = "IOS",
        clientVersion = "20.10.4",
        userAgent = "com.google.ios.youtube/20.10.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)",
        host = "https://www.youtube.com",
    ),
}

private const val WEB_REMIX_KEY = "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30"
private const val WEB_REMIX_VERSION = "1.20250101.01.00"
private const val WEB_REMIX_HOST = "https://music.youtube.com"
private const val WEB_UA =
    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0 Mobile Safari/537.36"

@Singleton
class InnerTubeClient @Inject constructor(
    private val http: OkHttpClient,
    private val json: Json,
) {
    private val jsonMedia = "application/json".toMediaType()
    private val visitorLock = Mutex()
    private var visitorSession: Pair<String, Long>? = null
    private val random = SecureRandom()

    suspend fun player(videoId: String, client: PlayerClient): JsonObject {
        val visitor = if (client == PlayerClient.VISIONOS) visionVisitor() else null
        val nonce = if (client == PlayerClient.VISIONOS) randomNonce(16) else null
        val body = buildJsonObject {
            putJsonObject("context") { putClient(client, visitor) }
            put("videoId", videoId)
            nonce?.let { put("cpn", it) }
            put("contentCheckOk", true)
            put("racyCheckOk", true)
        }
        val url = if (client == PlayerClient.VISIONOS) {
            "${client.host}/youtubei/v1/player".toHttpUrl().newBuilder()
                .addQueryParameter("prettyPrint", "false")
                .addQueryParameter("t", randomNonce(12))
                .addQueryParameter("id", videoId).build().toString()
        } else {
            "${client.host}/youtubei/v1/player?prettyPrint=false&key=${client.apiKey}"
        }
        val response = post(
            url = url,
            userAgent = client.userAgent,
            body = body,
            apiFormatV2 = client == PlayerClient.VISIONOS,
        )
        return if (nonce != null) withPlaybackNonce(response, nonce) else response
    }

    /** Anonymous session state, kept in memory only. No account cookies or login are used. */
    private suspend fun visionVisitor(): String = visitorLock.withLock {
        visitorSession?.takeIf { it.second > System.currentTimeMillis() }?.let { return@withLock it.first }
        val body = buildJsonObject { putJsonObject("context") { putClient(PlayerClient.VISIONOS) } }
        val response = post(
            "https://www.youtube.com/youtubei/v1/visitor_id?prettyPrint=false",
            PlayerClient.VISIONOS.userAgent,
            body,
            apiFormatV2 = true,
        )
        val visitor = response["responseContext"]?.jsonObject?.get("visitorData")?.jsonPrimitive?.contentOrNull
            ?.takeIf { it.isNotBlank() } ?: throw InnerTubeException("Missing anonymous visitor session")
        visitorSession = visitor to (System.currentTimeMillis() + 30 * 60_000L)
        visitor
    }

    private fun randomNonce(length: Int): String {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
        return CharArray(length) { alphabet[random.nextInt(alphabet.length)] }.concatToString()
    }

    /** The same playback nonce accompanies both the player request and its media URLs. */
    private fun withPlaybackNonce(response: JsonObject, nonce: String): JsonObject {
        val streaming = response["streamingData"] as? JsonObject ?: return response
        val updated = streaming.toMutableMap()
        for (key in listOf("adaptiveFormats", "formats")) {
            val formats = streaming[key] as? JsonArray ?: continue
            updated[key] = JsonArray(formats.map { element ->
                val format = element as? JsonObject ?: return@map element
                val url = format["url"]?.jsonPrimitive?.contentOrNull ?: return@map element
                JsonObject(format.toMutableMap().apply {
                    put("url", JsonPrimitive(url.toHttpUrl().newBuilder().setQueryParameter("cpn", nonce).build().toString()))
                })
            })
        }
        return JsonObject(response.toMutableMap().apply { put("streamingData", JsonObject(updated)) })
    }

    suspend fun browse(browseId: String? = null, continuation: String? = null): JsonObject {
        val body = buildJsonObject {
            putJsonObject("context") { putWebRemix() }
            browseId?.let { put("browseId", it) }
            continuation?.let { put("continuation", it) }
        }
        return post(
            url = "$WEB_REMIX_HOST/youtubei/v1/browse?prettyPrint=false&key=$WEB_REMIX_KEY",
            userAgent = WEB_UA,
            body = body,
        )
    }

    private suspend fun post(
        url: String,
        userAgent: String,
        body: JsonObject,
        apiFormatV2: Boolean = false,
    ): JsonObject =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .header("Content-Type", "application/json")
                .apply { if (apiFormatV2) header("X-Goog-Api-Format-Version", "2") }
                .post(body.toString().toRequestBody(jsonMedia))
                .build()
            http.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw InnerTubeException("HTTP ${response.code} from InnerTube", response.code)
                }
                json.parseToJsonElement(text) as? JsonObject
                    ?: throw InnerTubeException("Unexpected InnerTube response")
            }
        }

    private fun kotlinx.serialization.json.JsonObjectBuilder.putClient(client: PlayerClient, visitor: String? = null) {
        putJsonObject("client") {
            put("clientName", client.clientName)
            put("clientVersion", client.clientVersion)
            put("hl", "en")
            put("gl", "US")
            put("utcOffsetMinutes", 0)
            visitor?.let { put("visitorData", it) }
            when (client) {
                PlayerClient.VISIONOS -> {
                    put("clientScreen", "WATCH")
                    put("platform", "MOBILE")
                    put("deviceMake", "Apple")
                    put("deviceModel", "RealityDevice17,1")
                    put("osName", "visionOS")
                    put("osVersion", "26.6.0.23O770")
                }
                PlayerClient.IOS -> {
                    put("deviceMake", "Apple")
                    put("deviceModel", "iPhone16,2")
                    put("osName", "iPhone")
                    put("osVersion", "18.3.2.22D82")
                }
                PlayerClient.ANDROID_VR -> {
                    put("androidSdkVersion", 32)
                    put("deviceMake", "Oculus")
                    put("deviceModel", "Quest 3")
                    put("osName", "Android")
                    put("osVersion", "12L")
                }
            }
        }
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.putWebRemix() {
        putJsonObject("client") {
            put("clientName", "WEB_REMIX")
            put("clientVersion", WEB_REMIX_VERSION)
            put("hl", "en")
            put("gl", "US")
        }
    }
}

class InnerTubeException(message: String, val statusCode: Int? = null) : Exception(message)
