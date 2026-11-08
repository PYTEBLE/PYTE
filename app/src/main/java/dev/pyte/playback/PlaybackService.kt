@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package dev.pyte.playback

import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import dev.pyte.data.offline.OfflineStore
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
import dev.pyte.data.remote.StreamUnavailableException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import dev.pyte.data.repository.LibraryRepository
import dev.pyte.data.settings.SettingsRepository
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
    @Inject lateinit var diagnostics: PlaybackDiagnostics
    @Inject lateinit var library: LibraryRepository
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var offline: OfflineStore
    @Inject lateinit var equalizer: SoftwareEqualizer

    private var session: MediaSession? = null
    private var player: ExoPlayer? = null
    private var retriesForItem = 0
    private val historyScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var lastRecordedMediaId: String? = null

    override fun onCreate() {
        super.onCreate()

        val cachedSource = CacheDataSource.Factory()
            .setCache(streamCache)
            .setUpstreamDataSourceFactory(OkHttpDataSource.Factory(okHttp))
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        val resolvingSource = ResolvingDataSource.Factory(
            DefaultDataSource.Factory(this, cachedSource),
        ) { spec: DataSpec ->
            try {
                resolveAudioSpec(spec, offline::audio) { videoId -> runBlocking { resolver.resolve(videoId) } }
            } catch (e: Exception) {
                throw IOException(e.message ?: "Could not resolve track", e)
            }
        }

        val renderers = EqualizerRenderersFactory(this, equalizer)
        val exo = ExoPlayer.Builder(this, renderers)
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
                lastRecordedMediaId = null
                diagnostics.clear()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    retriesForItem = 0
                    diagnostics.clear()
                    if (exo.isPlaying) recordCurrentTrack(exo)
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) recordCurrentTrack(exo)
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.w(TAG, "Playback error: ${error.errorCodeName}", error)
                recoverFromError(exo, error)
            }
        })

        player = exo
        historyScope.launch(Dispatchers.Main.immediate) {
            settings.settings
                .catch { Log.w(TAG, "Could not read playback settings", it) }
                .collect { exo.skipSilenceEnabled = it.skipSilence; equalizer.apply(it.equalizer) }
        }
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
        historyScope.cancel()
        equalizer.analyzing = false
        equalizer.clearSpectrum()
        session?.run {
            release()
        }
        session = null
        player?.release()
        player = null
        super.onDestroy()
    }

    private fun recordCurrentTrack(exo: ExoPlayer) {
        val item = exo.currentMediaItem ?: return
        if (lastRecordedMediaId == item.mediaId) return
        lastRecordedMediaId = item.mediaId
        historyScope.launch {
            try {
                library.recordPlayed(item.toTrack())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Could not save local playback history", e)
            }
        }
    }

    /**
     * Stream URLs expire or get rejected (HTTP 403). Drop the cached URL and re-prepare
     * at the same position, a bounded number of times so a dead track can't loop forever.
     */
    private fun recoverFromError(exo: ExoPlayer, error: PlaybackException) {
        // The resolver already tried every available client. Repeating the whole chain
        // immediately cannot solve LOGIN_REQUIRED and needlessly increases request pressure.
        val clientsExhausted = generateSequence<Throwable>(error) { it.cause }
            .take(12).any { it is StreamUnavailableException }
        if (clientsExhausted || retriesForItem >= MAX_RETRIES_PER_ITEM) {
            diagnostics.record(error)
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
