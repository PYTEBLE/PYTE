package dev.pyte.playback

import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DataSpec
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

        val upstream = ResolvingDataSource.Factory(
            OkHttpDataSource.Factory(okHttp).setUserAgent(STREAM_USER_AGENT),
        ) { spec: DataSpec ->
            val videoId = spec.uri.lastPathSegment ?: throw IllegalArgumentException("Bad stream uri")
            val url = runBlocking { resolver.resolve(videoId) }
            spec.buildUpon().setUri(url).build()
        }

        val cachedSource = CacheDataSource.Factory()
            .setCache(streamCache)
            .setCacheKeyFactory { spec -> spec.uri.lastPathSegment ?: spec.uri.toString() }
            .setUpstreamDataSourceFactory(upstream)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        val exo = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(this).setDataSourceFactory(cachedSource))
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
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) retriesForItem = 0
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.w(TAG, "Playback error: ${error.errorCodeName}", error)
                recoverFromError(exo)
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
    private fun recoverFromError(exo: ExoPlayer) {
        if (retriesForItem >= MAX_RETRIES_PER_ITEM) {
            retriesForItem = 0
            exo.seekToNext()
            exo.prepare()
            return
        }
        retriesForItem++
        exo.currentMediaItem?.mediaId?.let(resolver::invalidate)
        val index = exo.currentMediaItemIndex
        val position = exo.currentPosition
        exo.seekTo(index, position)
        exo.prepare()
        exo.play()
    }

    private companion object {
        const val TAG = "PlaybackService"
        const val MAX_RETRIES_PER_ITEM = 2
        const val STREAM_USER_AGENT =
            "com.google.android.apps.youtube.vr.oculus/1.65.10 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip"
    }
}
