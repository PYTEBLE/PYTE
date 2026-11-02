package dev.pyte.data.remote

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

class StreamUnavailableException(
    message: String,
    cause: Throwable? = null,
    val attempts: List<StreamAttemptFailure> = emptyList(),
) : IOException(message, cause)

data class StreamAttemptFailure(val client: String, val stage: String, val reason: String)

internal class AudioHttpException(val status: Int) : IOException("Audio-Server HTTP $status")

internal class PlayerStatusException(val status: String) : IOException("Player status: $status")

data class ResolvedStream(
    val url: String,
    val client: PlayerClient,
    val formatId: String,
    val expiresAtMs: Long,
) {
    val userAgent: String get() = client.userAgent
    fun cacheKey(videoId: String): String = "audio-v2:$videoId:$formatId"
}

/** A player response is not proof of access: validate the audio URL before caching it. */
@Singleton
class StreamResolver internal constructor(
    private val loadPlayer: suspend (String, PlayerClient) -> JsonObject,
    private val http: OkHttpClient,
    private val now: () -> Long,
) {
    @Inject constructor(innerTube: InnerTubeClient, http: OkHttpClient) :
        this(innerTube::player, http, System::currentTimeMillis)

    private val cache = ConcurrentHashMap<String, ResolvedStream>()
    private val locks = ConcurrentHashMap<String, Mutex>()
    private val rejected = ConcurrentHashMap<Pair<String, PlayerClient>, Long>()

    suspend fun resolve(videoId: String): ResolvedStream {
        fresh(videoId)?.let { return it }
        return locks.getOrPut(videoId) { Mutex() }.withLock {
            fresh(videoId) ?: fetch(videoId)
        }
    }

    /** Reject the client too, not just its URL, when its actual audio request was denied. */
    fun invalidate(videoId: String, rejectClient: Boolean = false) {
        val previous = cache.remove(videoId)
        if (rejectClient && previous != null) {
            rejected[videoId to previous.client] = now() + REJECTION_TTL_MS
        }
    }

    private fun fresh(videoId: String): ResolvedStream? =
        cache[videoId]?.takeIf { it.expiresAtMs - SAFETY_MARGIN_MS > now() }

    private suspend fun fetch(videoId: String): ResolvedStream {
        var lastError: IOException? = null
        val attempts = mutableListOf<StreamAttemptFailure>()
        for (client in listOf(PlayerClient.VISIONOS, PlayerClient.ANDROID_VR, PlayerClient.IOS)) {
            if ((rejected[videoId to client] ?: 0L) > now()) {
                attempts += StreamAttemptFailure(client.name, "client", "COOLDOWN")
                continue
            }
            var stage = "player-api"
            try {
                val response = loadPlayer(videoId, client)
                stage = "audio-format"
                val stream = pickAudio(response, client)
                stage = "audio-probe"
                validateAccess(stream)
                return stream.also { cache[videoId] = it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val reason = when (e) {
                    is AudioHttpException -> "HTTP ${e.status}"
                    is PlayerStatusException -> e.status
                    is InnerTubeException -> e.statusCode?.let { "HTTP $it" } ?: "INVALID_PLAYER_RESPONSE"
                    else -> e.javaClass.simpleName
                }
                attempts += StreamAttemptFailure(client.name, stage, reason)
                lastError = if (e is IOException) e else StreamUnavailableException(
                    "${client.name}: keine abspielbare Audioquelle", e,
                )
            }
        }
        throw StreamUnavailableException(
            "Audio konnte über keinen YouTube-Client geladen werden. ${lastError?.message.orEmpty()}",
            lastError,
            attempts,
        )
    }

    private fun pickAudio(response: JsonObject, client: PlayerClient): ResolvedStream {
        val status = response["playabilityStatus"]?.jsonObject?.get("status")?.jsonPrimitive?.contentOrNull
        if (status != "OK") {
            val safeStatus = status?.takeIf { it.matches(Regex("[A-Z_]{1,50}")) } ?: "UNKNOWN_STATUS"
            throw PlayerStatusException(safeStatus)
        }
        val formats = response["streamingData"]?.jsonObject?.get("adaptiveFormats")?.jsonArray.orEmpty()
            .mapNotNull { it as? JsonObject }
            .filter { it["mimeType"]?.jsonPrimitive?.contentOrNull?.startsWith("audio/") == true }
            .filter { it["url"]?.jsonPrimitive?.contentOrNull != null }
        // Prefer a common AAC format across clients. Format-specific cache keys protect other formats.
        val chosen = formats.firstOrNull { it["itag"]?.jsonPrimitive?.contentOrNull == "140" }
            ?: formats.maxByOrNull { it["bitrate"]?.jsonPrimitive?.longOrNull ?: 0L }
            ?: throw StreamUnavailableException("${client.name}: kein direkter Audiostream")
        val url = chosen.getValue("url").jsonPrimitive.content
        val format = chosen["itag"]?.jsonPrimitive?.contentOrNull
            ?: throw StreamUnavailableException("${client.name}: fehlende Audioformat-ID")
        val expiry = Regex("""[?&]expire=(\d+)""").find(url)?.groupValues?.get(1)?.toLongOrNull()
        return ResolvedStream(url, client, format, expiry?.times(1000) ?: (now() + 30 * 60 * 1000))
    }

    private suspend fun validateAccess(stream: ResolvedStream) = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(stream.url)
            .header("User-Agent", stream.userAgent)
            .header("Range", "bytes=0-0")
            .build()
        http.newCall(request).execute().use { response ->
            if (response.code != 200 && response.code != 206) {
                // Never include signed URLs or response bodies in diagnostics.
                throw AudioHttpException(response.code)
            }
            if (response.body?.source()?.readByteArray(1)?.size != 1) {
                throw StreamUnavailableException("${stream.client.name}: leere Audioantwort")
            }
        }
    }

    private companion object {
        const val SAFETY_MARGIN_MS = 60_000L
        const val REJECTION_TTL_MS = 5 * 60_000L
    }
}
