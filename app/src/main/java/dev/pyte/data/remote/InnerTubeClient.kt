package dev.pyte.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
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

    suspend fun player(videoId: String, client: PlayerClient): JsonObject {
        val body = buildJsonObject {
            putJsonObject("context") { putClient(client) }
            put("videoId", videoId)
            put("contentCheckOk", true)
            put("racyCheckOk", true)
        }
        return post(
            url = "${client.host}/youtubei/v1/player?prettyPrint=false&key=${client.apiKey}",
            userAgent = client.userAgent,
            body = body,
        )
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

    private suspend fun post(url: String, userAgent: String, body: JsonObject): JsonObject =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .header("Content-Type", "application/json")
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

    private fun kotlinx.serialization.json.JsonObjectBuilder.putClient(client: PlayerClient) {
        putJsonObject("client") {
            put("clientName", client.clientName)
            put("clientVersion", client.clientVersion)
            put("hl", "en")
            put("gl", "US")
            put("utcOffsetMinutes", 0)
            put("androidSdkVersion", 32)
            put("osName", if (client == PlayerClient.IOS) "iPhone" else "Android")
            put("osVersion", if (client == PlayerClient.IOS) "18.3.2.22D82" else "12L")
            put("deviceMake", if (client == PlayerClient.IOS) "Apple" else "Oculus")
            put("deviceModel", if (client == PlayerClient.IOS) "iPhone16,2" else "Quest 3")
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
