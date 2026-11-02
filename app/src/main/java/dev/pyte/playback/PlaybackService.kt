@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package dev.pyte.playback

import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dagger.hilt.android.AndroidEntryPoint
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dev.pyte.data.remote.StreamResolver
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import javax.inject.Inject
import java.io.IOException

/**
 * Owns the ExoPlayer and the media session. Runs as a foreground service so playback
 * continues when the UI is backgrounded, the screen is off, or another app is in front.
 */
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    @Inject lateinit var streamCache: SimpleCache
    @Inject lateinit var resolver: StreamResolver
    @Inject lateinit var okHttp: OkHttpClient

    private var session: MediaSession? = null
    private var player: ExoPlayer? = null
    private var retriesForItem = 0

    override fun onCreate() {
        super.onCreate()

        val cachedSource = CacheDataSource.Factory()
            .setCache(streamCache)
            .setUpstreamDataSourceFactory(OkHttpDataSource.Factory(okHttp))
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        val resolvingSource = ResolvingDataSource.Factory(
            cachedSource,
        ) { spec: DataSpec ->
            val videoId = spec.uri.lastPathSegment ?: throw IllegalArgumentException("Bad stream uri")
            val stream = try {
                runBlocking { resolver.resolve(videoId) }
            } catch (e: Exception) {
                throw IOException(e.message ?: "Could not resolve track $videoId", e)
            }
            spec.buildUpon()
                .setUri(stream.url)
                .setKey(stream.cacheKey(videoId))
                .setHttpRequestHeaders(spec.httpRequestHeaders + ("User-Agent" to stream.userAgent))
                .build()
        }

        val exo = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(this).setDataSourceFactory(resolvingSource))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        exo.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                retriesForItem = 0
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) retriesForItem = 0
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.w(TAG, "Playback error: ${error.errorCodeName}", error)
                recoverFromError(exo, error)
            }
        })

        player = exo
        session = MediaSession.Builder(this, exo)
            .setCallback(object : MediaSession.Callback {
                override fun onAddMediaItems(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    mediaItems: List<MediaItem>,
                ): ListenableFuture<List<MediaItem>> = try {
                    Futures.immediateFuture(mediaItems.map(::resolvePlaybackItem))
                } catch (e: IllegalArgumentException) {
                    Futures.immediateFailedFuture(e)
                }
            })
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        session?.run {
            release()
        }
        session = null
        player?.release()
        player = null
        super.onDestroy()
    }

    /**
     * Stream URLs expire or get rejected (HTTP 403). Drop the cached URL and re-prepare
     * at the same position, a bounded number of times so a dead track can't loop forever.
     */
    private fun recoverFromError(exo: ExoPlayer, error: PlaybackException) {
        if (retriesForItem >= MAX_RETRIES_PER_ITEM) {
            retriesForItem = 0
            exo.pause()
            return
        }
        retriesForItem++
        val status = generateSequence<Throwable>(error) { it.cause }
            .filterIsInstance<HttpDataSource.InvalidResponseCodeException>()
            .firstOrNull()?.responseCode
        exo.currentMediaItem?.mediaId?.let {
            resolver.invalidate(it, rejectClient = status == 403 || status == 410)
        }
        val index = exo.currentMediaItemIndex
        val position = exo.currentPosition
        exo.seekTo(index, position)
        exo.prepare()
        exo.play()
    }

    private companion object {
        const val TAG = "PlaybackService"
        const val MAX_RETRIES_PER_ITEM = 2
    }
}
