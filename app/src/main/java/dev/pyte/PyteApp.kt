package dev.pyte

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import dev.pyte.data.offline.OfflineStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collectLatest
import javax.inject.Inject
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import android.content.Context
import android.util.Log
import okio.Path.Companion.toOkioPath
import kotlinx.coroutines.flow.catch

@HiltAndroidApp
class PyteApp : Application(), SingletonImageLoader.Factory {
    override fun newImageLoader(context: Context): ImageLoader = ImageLoader.Builder(context)
        .memoryCache { MemoryCache.Builder().maxSizeBytes(32L * 1024 * 1024).build() }
        .diskCache { DiskCache.Builder().directory(context.cacheDir.resolve("artwork").toOkioPath())
            .maxSizeBytes(96L * 1024 * 1024).build() }
        .build()
    @Inject lateinit var offline: OfflineStore
    override fun onCreate() {
        super.onCreate()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            offline.wantedTracks.distinctUntilChanged()
                .catch { Log.w("Offline", "Could not observe offline playlists") }.collectLatest {
                try {
                    offline.reconcile()
                    offline.schedule()
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) { Log.w("Offline", "Could not schedule playlist downloads") }
            }
        }
    }
}
