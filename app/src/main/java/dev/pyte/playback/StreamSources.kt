@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package dev.pyte.playback

import android.content.Context
import android.net.ConnectivityManager
import android.os.SystemClock
import android.util.Log
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.pyte.data.offline.OfflineStore
import dev.pyte.data.remote.StreamResolver
import dev.pyte.data.settings.BatterySettings
import dev.pyte.data.settings.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** One resolution path for every player: the main session and the separate preview player. */
@Singleton
class StreamSources @Inject constructor(
    @ApplicationContext private val context: Context,
    private val streamCache: SimpleCache,
    private val resolver: StreamResolver,
    private val okHttp: OkHttpClient,
    private val settings: SettingsRepository,
    private val offline: OfflineStore,
) {
    fun lowData(value: BatterySettings): Boolean = value.active(value.lowData) &&
        context.getSystemService(ConnectivityManager::class.java).isActiveNetworkMetered

    fun resolvingFactory(): ResolvingDataSource.Factory {
        val cachedSource = CacheDataSource.Factory()
            .setCache(streamCache)
            .setUpstreamDataSourceFactory(OkHttpDataSource.Factory(okHttp))
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        return ResolvingDataSource.Factory(
            DefaultDataSource.Factory(context, cachedSource),
        ) { spec: DataSpec ->
            try {
                resolveAudioSpec(spec, offline::audio) { videoId -> runBlocking {
                    // Ensure persisted battery preferences are loaded even on a cold service start.
                    val preferences = settings.settings.first().battery
                    val start = SystemClock.elapsedRealtime()
                    resolver.resolve(videoId, lowData(preferences)).also {
                        Log.d(TAG, "Stream resolve: ${SystemClock.elapsedRealtime() - start} ms")
                    }
                } }
            } catch (e: Exception) {
                throw IOException(e.message ?: "Could not resolve track", e)
            }
        }
    }

    private companion object {
        const val TAG = "StreamSources"
    }
}
