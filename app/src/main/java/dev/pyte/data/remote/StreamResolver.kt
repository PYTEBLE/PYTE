package dev.pyte.data.remote

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

class StreamUnavailableException(message: String) : Exception(message)

/**
 * Resolves a videoId to a direct, playable audio URL. URLs are IP-bound and expire,
 * so they are cached per videoId until shortly before their `expire` timestamp.
 * Concurrent requests for the same videoId are coalesced into one network call.
 */
@Singleton
class StreamResolver @Inject constructor(
    private val innerTube: InnerTubeClient,
) {
    private data class CachedUrl(val url: String, val expiresAtMs: Long)

    private val cache = ConcurrentHashMap<String, CachedUrl>()
    private val locks = ConcurrentHashMap<String, Mutex>()

    suspend fun resolve(videoId: String): String {
        fresh(videoId)?.let { return it }
        return locks.getOrPut(videoId) { Mutex() }.withLock {
            fresh(videoId) ?: fetch(videoId).url
        }
    }

    fun invalidate(videoId: String) {
        cache.remove(videoId)
    }

    private fun fresh(videoId: String): String? =
        cache[videoId]?.takeIf { it.expiresAtMs - SAFETY_MARGIN_MS > System.currentTimeMillis() }?.url

    private suspend fun fetch(videoId: String): CachedUrl {
        var lastError: Exception? = null
        for (client in listOf(PlayerClient.ANDROID_VR, PlayerClient.IOS)) {
            try {
                val url = pickAudioUrl(innerTube.player(videoId, client))
                    ?: throw StreamUnavailableException("No audio stream from ${client.name}")
                return CachedUrl(url, expiryOf(url)).also { cache[videoId] = it }
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw StreamUnavailableException(lastError?.message ?: "Could not resolve stream for $videoId")
    }

    private fun pickAudioUrl(response: JsonObject): String? {
        val status = response["playabilityStatus"]?.jsonObject?.get("status")?.jsonPrimitive?.contentOrNull
        if (status != null && status != "OK") {
            val reason = response["playabilityStatus"]?.jsonObject?.get("reason")?.jsonPrimitive?.contentOrNull
            throw StreamUnavailableException(reason ?: "Track is not playable ($status)")
        }
        val formats = response["streamingData"]?.jsonObject?.get("adaptiveFormats")?.jsonArray.orEmpty()
        return formats
            .mapNotNull { it as? JsonObject }
            .filter { it["mimeType"]?.jsonPrimitive?.contentOrNull?.startsWith("audio/") == true }
            .mapNotNull { format ->
                val url = format["url"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val bitrate = format["bitrate"]?.jsonPrimitive?.longOrNull ?: 0L
                url to bitrate
            }
            .maxByOrNull { it.second }
            ?.first
    }

    /** googlevideo URLs carry an `expire` query param (unix seconds). Fall back to 30 min. */
    private fun expiryOf(url: String): Long {
        val expire = Regex("""[?&]expire=(\d+)""").find(url)?.groupValues?.get(1)?.toLongOrNull()
        return expire?.times(1000) ?: (System.currentTimeMillis() + 30 * 60 * 1000)
    }

    private companion object {
        const val SAFETY_MARGIN_MS = 60_000L
    }
}
