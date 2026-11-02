package dev.pyte.data.offline

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.work.*
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.pyte.data.local.*
import dev.pyte.data.lyrics.LyricsRepository
import dev.pyte.data.remote.ArtworkUrls
import dev.pyte.data.remote.ArtworkQuality
import dev.pyte.data.remote.StreamResolver
import dev.pyte.data.repository.toDomain
import dev.pyte.data.settings.SettingsRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OfflineStore internal constructor(
    private val context: Context,
    private val db: PyteDatabase,
    private val settings: SettingsRepository,
    private val resolver: StreamResolver,
    private val http: OkHttpClient,
    private val lyrics: LyricsRepository,
    private val wifiAvailable: () -> Boolean,
) {
    @Inject constructor(@ApplicationContext context: Context, db: PyteDatabase, settings: SettingsRepository,
        resolver: StreamResolver, http: OkHttpClient, lyrics: LyricsRepository) :
        this(context, db, settings, resolver, http, lyrics, { isWifi(context) })
    private val mutex = Mutex()
    private val dao get() = db.offlineDao()
    val selected = dao.observeSelected()
    val downloads = dao.observeDownloads()
    val wantedTracks = dao.observeWantedTracks()
    private val directory get() = File(context.filesDir, "offline").apply { mkdirs() }
    private fun file(id: String, suffix: String): File {
        require(id.matches(Regex("[A-Za-z0-9_-]{11}")))
        return File(directory, "$id.$suffix")
    }
    fun audio(id: String): File? = file(id, "audio").takeIf { it.isFile && it.length() > 0 }
    fun cover(id: String): File? = file(id, "cover").takeIf { it.isFile && it.length() > 0 }
    suspend fun usedBytes(): Long = withContext(Dispatchers.IO) { directory.listFiles().orEmpty().sumOf { it.length() } }

    suspend fun setSelected(id: String, enabled: Boolean) {
        if (enabled) {
            require(db.playlistDao().getPlaylist(id) != null) { "Playlist no longer exists" }
            dao.select(OfflinePlaylistEntity(id))
        } else dao.deselect(id)
        reconcile()
        schedule()
    }

    /** Runs even without connectivity so disabling/deleting a playlist immediately releases its files. */
    suspend fun reconcile() = withContext(Dispatchers.IO) {
        val wanted = dao.wantedTracks().map { it.videoId }.toSet()
        directory.listFiles().orEmpty().filter { it.name.substringBefore('.') !in wanted }.forEach { it.delete() }
        val records = dao.observeDownloads().first().associateBy { it.videoId }
        records.values.filter { it.videoId !in wanted }.forEach { dao.remove(it.videoId) }
        wanted.forEach { id ->
            val completed = audio(id)
            val record = records[id]
            if (completed != null && record?.state != "Downloaded")
                dao.update(DownloadEntity(id, "Downloaded", completed.length(), completed.length()))
            else if (completed == null && (record == null || record.state == "Downloaded"))
                dao.update(DownloadEntity(id, "Waiting for Wi-Fi", file(id, "part").length()))
        }
    }

    suspend fun schedule() {
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED)
            .setRequiresStorageNotLow(true).build()
        val manager = WorkManager.getInstance(context)
        if (dao.wantedTracks().isEmpty()) {
            manager.cancelUniqueWork("offline-sync")
            manager.cancelUniqueWork("offline-maintenance")
            return
        }
        manager.enqueueUniqueWork("offline-sync", ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequestBuilder<OfflineWorker>().setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build())
        manager.enqueueUniquePeriodicWork("offline-maintenance", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<OfflineWorker>(15, TimeUnit.MINUTES).setConstraints(constraints).build())
    }

    private fun wifi(): Boolean = wifiAvailable()

    suspend fun sync(): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            reconcile()
            if (!wifi() || settings.settings.first().downloadsPaused) return@withLock false
            val started = System.currentTimeMillis()
            var retry = false
            for (track in dao.wantedTracks()) {
                currentCoroutineContext().ensureActive()
                if (System.currentTimeMillis() - started > 7 * 60_000) return@withLock true
                if (!dao.isWanted(track.videoId)) continue
                try {
                    if (audio(track.videoId) == null) download(track, started)
                    if (!dao.isWanted(track.videoId)) { reconcile(); continue }
                    // Ancillary content is optional; audio remains usable if a provider has no lyrics/artwork.
                    try {
                        if (wifi() && !settings.settings.first().downloadsPaused) {
                            downloadCover(track)
                            if (usedBytes() + 1_000_000 < settings.settings.first().downloadLimitMb * 1024 * 1024)
                                lyrics.get(track.toDomain(), pin = true)
                        }
                    } catch (e: CancellationException) { throw e }
                    catch (_: Exception) { /* Retry ancillary content on a future sync. */ }
                    if (!dao.isWanted(track.videoId)) reconcile()
                } catch (e: CancellationException) {
                    withContext(NonCancellable) {
                        if (audio(track.videoId) == null && dao.isWanted(track.videoId))
                            dao.update(DownloadEntity(track.videoId, "Queued", file(track.videoId, "part").length()))
                    }
                    throw e
                }
                catch (e: DownloadPaused) {
                    if (!dao.isWanted(track.videoId)) { reconcile(); continue }
                    dao.update(DownloadEntity(track.videoId, e.message ?: "Paused", file(track.videoId, "part").length()))
                    return@withLock e.retry
                } catch (e: Exception) {
                    resolver.invalidate(track.videoId, rejectClient = true)
                    dao.update(DownloadEntity(track.videoId, "Retry pending", file(track.videoId, "part").length(), error = "Download interrupted. Will retry on Wi-Fi."))
                    retry = true
                }
            }
            reconcile()
            retry
        }
    }

    private suspend fun checkAllowed(id: String, started: Long) {
        currentCoroutineContext().ensureActive()
        if (!dao.isWanted(id)) throw DownloadPaused("Removed", false)
        if (settings.settings.first().downloadsPaused) throw DownloadPaused("Paused", false)
        if (!wifi()) throw DownloadPaused("Waiting for Wi-Fi", true)
        if (System.currentTimeMillis() - started > 7 * 60_000) throw DownloadPaused("Queued", true)
    }

    private suspend fun download(track: TrackEntity, started: Long) {
        val id = track.videoId
        checkAllowed(id, started)
        dao.update(DownloadEntity(id, "Resolving"))
        val stream = resolver.resolve(id)
        checkAllowed(id, started)
        val partial = file(id, "part")
        val signature = file(id, "format")
        val validator = file(id, "validator")
        val key = stream.cacheKey(id)
        if (!signature.exists() || signature.readText() != key || !validator.exists() || validator.readText().isBlank()) {
            partial.delete()
        }
        signature.writeText(key)
        var offset = partial.length()
        val request = Request.Builder().url(stream.url).header("User-Agent", stream.userAgent)
            .header("Accept-Encoding", "identity")
            .apply { if (offset > 0) { header("Range", "bytes=$offset-"); header("If-Range", validator.readText()) } }
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) { if (response.code == 416) partial.delete(); throw IOException("HTTP ${response.code}") }
            val body = response.body ?: throw IOException("Empty audio")
            val remaining = body.contentLength()
            if (remaining <= 0 || remaining > Long.MAX_VALUE - offset) throw IOException("Unknown audio length")
            if (response.code == 206) {
                val range = Regex("bytes (\\d+)-(\\d+)/(\\d+)").matchEntire(response.header("Content-Range").orEmpty())
                    ?: throw IOException("Invalid range")
                if (range.groupValues[1].toLong() != offset) throw IOException("Wrong range")
                val end = range.groupValues[2].toLong()
                val fullLength = range.groupValues[3].toLong()
                if (end != fullLength - 1 || offset + remaining != fullLength)
                    throw IOException("Server returned an incomplete range")
            } else offset = 0
            val total = offset + remaining
            validator.writeText(response.header("ETag")?.takeUnless { it.startsWith("W/") }
                ?: response.header("Last-Modified").orEmpty())
            val limit = settings.settings.first().downloadLimitMb * 1024 * 1024
            if (usedBytes() - partial.length() + total > limit || directory.usableSpace < remaining + 64 * 1024 * 1024)
                throw DownloadPaused("Storage limit reached", false)
            dao.update(DownloadEntity(id, "Downloading", offset, total))
            body.byteStream().use { input ->
                FileOutputStream(partial, offset > 0).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var bytes = offset
                    var lastUpdate = 0L
                    while (true) {
                        checkAllowed(id, started)
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (bytes + count > total) throw IOException("Audio length mismatch")
                        if (usedBytes() + count > settings.settings.first().downloadLimitMb * 1024 * 1024)
                            throw DownloadPaused("Storage limit reached", false)
                        output.write(buffer, 0, count)
                        bytes += count
                        if (bytes - lastUpdate >= 256 * 1024) {
                            dao.update(DownloadEntity(id, "Downloading", bytes, total))
                            lastUpdate = bytes
                        }
                    }
                    if (bytes != total) throw IOException("Incomplete audio")
                    output.fd.sync()
                }
            }
            checkAllowed(id, started)
            if (!partial.renameTo(file(id, "audio"))) throw IOException("Could not commit download")
            signature.delete()
            validator.delete()
            dao.update(DownloadEntity(id, "Downloaded", total, total))
        }
    }

    private suspend fun downloadCover(track: TrackEntity) {
        val existing = cover(track.videoId)
        val oldSize = existing?.let(ArtworkQuality::size)
        if ((oldSize?.shortEdge ?: 0) >= ArtworkQuality.OFFLINE_TARGET) return
        val checked = file(track.videoId, "covercheck")
        if (checked.exists() && System.currentTimeMillis() - checked.lastModified() < 86_400_000) return
        checked.writeText("")
        for (url in listOfNotNull(ArtworkUrls.sized(track.thumbnailUrl), track.thumbnailUrl).distinct()) {
        http.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful || response.header("Content-Type")?.startsWith("image/") != true) return@use
            val body = response.body ?: return
            val temp = file(track.videoId, "cover.tmp")
            try {
                body.byteStream().use { input ->
                    temp.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var bytes = 0L
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            if (!wifi() || !dao.isWanted(track.videoId) || settings.settings.first().downloadsPaused) return
                            val count = input.read(buffer)
                            if (count < 0) break
                            bytes += count
                            if (bytes > 4 * 1024 * 1024 || usedBytes() + count > settings.settings.first().downloadLimitMb * 1024 * 1024) return
                            output.write(buffer, 0, count)
                        }
                    }
                }
                if (ArtworkQuality.shouldReplace(oldSize, ArtworkQuality.size(temp)) && dao.isWanted(track.videoId))
                    temp.renameTo(file(track.videoId, "cover"))
            } finally { temp.delete() }
        }
        if ((cover(track.videoId)?.let(ArtworkQuality::size)?.shortEdge ?: 0) > (oldSize?.shortEdge ?: 0)) return
        }
    }
}

private fun isWifi(context: Context): Boolean {
    val connectivity = context.getSystemService(ConnectivityManager::class.java)
    val caps = connectivity.getNetworkCapabilities(connectivity.activeNetwork) ?: return false
    return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) && !connectivity.isActiveNetworkMetered
}

private class DownloadPaused(message: String, val retry: Boolean) : IOException(message)

@EntryPoint
@InstallIn(SingletonComponent::class)
interface OfflineWorkerEntryPoint { fun offlineStore(): OfflineStore }

class OfflineWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        val store = EntryPointAccessors.fromApplication(applicationContext, OfflineWorkerEntryPoint::class.java).offlineStore()
        if (store.sync()) Result.retry() else Result.success()
    } catch (e: CancellationException) { throw e }
    catch (_: Exception) { Result.retry() }
}
