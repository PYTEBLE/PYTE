package dev.pyte.data.lyrics

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.pyte.BuildConfig
import dev.pyte.domain.Track
import dev.pyte.data.remote.readBounded
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

data class LyricLine(val timeMs: Long, val text: String)
data class Lyrics(val lines: List<LyricLine>, val plain: String?, val instrumental: Boolean) {
    val synced: Boolean get() = lines.isNotEmpty()
}

object LrcParser {
    private val stamp = Regex("\\[(\\d{1,3}):([0-5]\\d)(?:[.:](\\d{1,3}))?]")
    fun parse(text: String): List<LyricLine> {
        val offset = Regex("\\[offset:([+-]?\\d+)]", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.get(1)?.toLongOrNull() ?: 0
        return text.lineSequence().flatMap { line ->
            val matches = stamp.findAll(line).toList()
            val words = line.substring(matches.lastOrNull()?.range?.last?.plus(1) ?: line.length).trim()
            matches.map { match ->
                val fraction = match.groupValues[3].padEnd(3, '0').take(3).toLongOrNull() ?: 0
                LyricLine((match.groupValues[1].toLong() * 60_000 +
                    match.groupValues[2].toLong() * 1000 + fraction + offset).coerceAtLeast(0), words)
            }.asSequence()
        }.sortedBy { it.timeMs }.toList()
    }
    fun activeIndex(lines: List<LyricLine>, positionMs: Long): Int =
        lines.indexOfLast { it.timeMs <= positionMs }
}

@Singleton
class LyricsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val http: OkHttpClient,
    private val json: Json,
) {
    private val mutex = Mutex()
    private var nextRequestAt = 0L
    private val cache get() = File(context.cacheDir, "lyrics").apply { mkdirs() }
    private fun pinned(id: String) = File(context.filesDir, "offline/$id.lyrics")

    suspend fun get(track: Track, pin: Boolean = false): Lyrics? = withContext(Dispatchers.IO) {
        require(track.videoId.matches(Regex("[A-Za-z0-9_-]{11}")))
        mutex.withLock {
            val cached = File(cache, "${track.videoId}.json")
            val stored = pinned(track.videoId).takeIf { it.isFile } ?: cached.takeIf { it.isFile }
            if (stored != null) {
                val parsed = runCatching { decode(stored.readText()) }.getOrNull()
                if (parsed != null) {
                    stored.setLastModified(System.currentTimeMillis())
                    if (pin && stored != pinned(track.videoId)) copyPinned(stored, track.videoId)
                    return@withLock parsed
                }
                stored.delete()
            }
            // Cache misses briefly too; do not repeatedly query unavailable songs.
            val miss = File(cache, "${track.videoId}.miss")
            if (miss.exists() && System.currentTimeMillis() - miss.lastModified() < 86_400_000) return@withLock null
            val wait = nextRequestAt - System.currentTimeMillis()
            if (wait > 1500) throw IOException("Lyrics service busy. Please try again later.")
            if (wait > 0) delay(wait)
            val url = "https://lrclib.net/api/get".toHttpUrl().newBuilder()
                .addQueryParameter("track_name", track.title)
                .addQueryParameter("artist_name", track.artist)
                .apply { if (track.durationMs in 1000..3_600_000) addQueryParameter("duration", (track.durationMs / 1000.0).toString()) }
                .build()
            try {
                http.newCall(Request.Builder().url(url)
                    .header("User-Agent", "PYTE/${BuildConfig.VERSION_NAME} (https://github.com/PYTEBLE/PYTE)")
                    .build()).execute().use { response ->
                    if (response.code == 429 || response.code == 503) {
                        val header = response.header("Retry-After")
                        val now = System.currentTimeMillis()
                        val deadline = header?.toLongOrNull()?.coerceIn(1, 31_536_000)?.let { now + it * 1000 }
                            ?: runCatching { java.time.ZonedDateTime.parse(header, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }.getOrNull()
                            ?: (now + 60_000)
                        nextRequestAt = maxOf(now + 1000, deadline) + (100..1000).random()
                        throw IOException("Lyrics service busy. Please try again later.")
                    }
                    if (response.code == 404) {
                        miss.writeText("")
                        trim()
                        return@withLock null
                    }
                    if (!response.isSuccessful) throw IOException("Lyrics unavailable (HTTP ${response.code})")
                    val bytes = response.body?.byteStream()?.readBounded(1_000_000) ?: throw IOException("Empty lyrics")
                    val text = bytes.toString(Charsets.UTF_8)
                    val result = decode(text)
                    cached.writeText(text)
                    if (pin) copyPinned(cached, track.videoId)
                    trim()
                    result
                }
            } finally {
                nextRequestAt = maxOf(nextRequestAt, System.currentTimeMillis() + 350)
            }
        }
    }

    private fun copyPinned(source: File, id: String) {
        val target = pinned(id)
        target.parentFile?.mkdirs()
        val temp = File(target.path + ".tmp")
        source.copyTo(temp, overwrite = true)
        if (!temp.renameTo(target)) throw IOException("Could not store offline lyrics")
    }

    private fun decode(text: String): Lyrics {
        val obj = json.parseToJsonElement(text).jsonObject
        return Lyrics(LrcParser.parse(obj["syncedLyrics"]?.jsonPrimitive?.contentOrNull.orEmpty()),
            obj["plainLyrics"]?.jsonPrimitive?.contentOrNull,
            obj["instrumental"]?.jsonPrimitive?.booleanOrNull ?: false)
    }

    private fun trim() {
        var bytes = 0L
        cache.listFiles().orEmpty().sortedByDescending { it.lastModified() }.forEachIndexed { index, file ->
            bytes += file.length()
            if (bytes > 8 * 1024 * 1024 || index >= 300) file.delete()
        }
    }
}
