@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package dev.pyte.playback

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.pyte.domain.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import dev.pyte.data.remote.RecommendationsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException

data class PlayerUiState(
    val current: Track? = null,
    val queue: List<Track> = emptyList(),
    val currentIndex: Int = -1,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffle: Boolean = false,
    val repeat: Int = Player.REPEAT_MODE_OFF,
    val errorMessage: String? = null,
    val queueEntryIds: List<String> = emptyList(),
    val playlistOrigin: PlaylistPlaybackOrigin? = null,
    val isPreview: Boolean = false,
    val previewTrack: Track? = null,
    val previewPlaying: Boolean = false,
    val previewPositionMs: Long = 0L,
    val previewDurationMs: Long = 0L,
) {
    val hasItems: Boolean get() = queue.isNotEmpty()
}

/**
 * UI-side handle to [PlaybackService]. The UI never owns the player; it only sends
 * commands through a MediaController and mirrors the state it reports.
 *
 * Song previews use a separate in-process player that never touches the main queue or
 * the media session. The main playback pauses while a preview plays and resumes afterwards.
 */
@Singleton
class PlayerController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val recommendations: RecommendationsRepository,
    private val settings: dev.pyte.data.settings.SettingsRepository,
    private val streams: StreamSources,
    private val equalizer: SoftwareEqualizer,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private var controller: MediaController? = null
    private var pendingAction: ((MediaController) -> Unit)? = null
    private var previewExo: ExoPlayer? = null
    private var previewTrack: Track? = null
    private var resumeMainAfterPreview = false
    private val mix = MixPlayback()
    private val _mixState = MutableStateFlow(MixUiState())
    val mixState = _mixState.asStateFlow()
    private var mixJob: Job? = null
    private var mixSeedEntry: String? = null
    private var mixRequest = 0L

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (mixSeedEntry != null && (previewTrack != null ||
                    player.currentMediaItem?.mediaMetadata?.extras?.getString("queueEntryId") != mixSeedEntry)) cancelMixLoading()
            // Main playback started elsewhere (headset, notification) must not run alongside a preview.
            if (previewTrack != null && player.isPlaying) discardPreview()
            refresh(player)
        }

        override fun onPlayerError(error: PlaybackException) {
            _state.value = _state.value.copy(
                errorMessage = playbackErrorMessage(error.errorCodeName, error.cause),
            )
        }
    }

    private val previewListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            controller?.let { refresh(it) }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) controller?.let { c ->
                if (stopPreview()) c.play()
                refresh(c)
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            _state.value = _state.value.copy(
                errorMessage = playbackErrorMessage(error.errorCodeName, error.cause),
            )
        }
    }

    init {
        var battery = dev.pyte.data.settings.BatterySettings()
        scope.launch { settings.settings.collect { battery = it.battery } }
        val power = context.getSystemService(android.os.PowerManager::class.java)
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            try {
                val c = future.get()
                controller = c
                c.addListener(listener)
                pendingAction?.invoke(c)
                pendingAction = null
                refresh(c)
            } catch (e: Exception) {
                pendingAction = null
                _state.value = _state.value.copy(
                    errorMessage = "Player konnte nicht verbunden werden. Bitte die App neu öffnen.",
                )
            }
        }, ContextCompat.getMainExecutor(context))

        scope.launch {
            while (true) {
                val screenOff = battery.active(battery.reduceBackgroundUpdates) && !power.isInteractive
                delay(if (screenOff) 2_000L else POSITION_TICK_MS)
                controller?.takeIf { !screenOff && it.isPlaying }?.let { c ->
                    _state.value = _state.value.copy(positionMs = c.currentPosition, durationMs = c.duration.coerceAtLeast(0))
                }
                previewExo?.takeIf { previewTrack != null && it.isPlaying && !screenOff }?.let { p ->
                    _state.value = _state.value.copy(previewPositionMs = p.currentPosition, previewDurationMs = p.duration.coerceAtLeast(0))
                }
            }
        }
    }

    fun playQueue(tracks: List<Track>, startIndex: Int = 0, shuffle: Boolean = false, sourcePlaylistId: String? = null) {
        if (tracks.isEmpty()) return
        act { c ->
            cancelMixLoading()
            mix.discard()
            _mixState.value = MixUiState()
            discardPreview()
            val items = tracks.map { it.toMediaItem() }
            val origin = sourcePlaylistId?.let {
                PlaylistPlaybackOrigin(it, java.util.UUID.randomUUID().toString(),
                    checkNotNull(items.last().mediaMetadata.extras?.getString("queueEntryId")))
            }
            c.setMediaItems(items.map { it.withPlaylistOrigin(origin) }, startIndex.coerceIn(0, tracks.lastIndex), 0L)
            c.shuffleModeEnabled = shuffle
            c.prepare()
            c.play()
        }
    }

    fun preview(track: Track) = act { c ->
        cancelMixLoading()
        if (previewTrack == null) resumeMainAfterPreview = c.isPlaying
        c.pause()
        previewTrack = track
        ensurePreviewPlayer().apply {
            setMediaItem(track.toMediaItem())
            prepare()
            play()
        }
        refresh(c)
    }

    fun togglePreview() = act { c ->
        previewExo?.takeIf { previewTrack != null }?.let { p -> if (p.isPlaying) p.pause() else p.play() }
        refresh(c)
    }

    fun finishPreview() = act { c ->
        if (stopPreview()) c.play()
        refresh(c)
    }

    fun cancelMixLoading() {
        mixRequest++
        mixJob?.cancel()
        mixJob = null
        mixSeedEntry = null
        _mixState.value = _mixState.value.copy(loading = false)
    }

    fun startMix() {
        val c = controller ?: return
        if (previewTrack != null || _mixState.value.loading) return
        val seed = c.currentMediaItem?.toTrack() ?: return
        val entry = c.currentMediaItem?.mediaMetadata?.extras?.getString("queueEntryId") ?: return
        val request = ++mixRequest
        mixSeedEntry = entry
        _mixState.value = _mixState.value.copy(loading = true, error = null)
        mixJob = scope.launch {
            try {
                val songs = recommendations.discover(listOf(seed), setOf(seed.videoId)).songs.map { it.track }
                if (request != mixRequest || mixSeedEntry != entry || previewTrack != null) return@launch
                // Clear the loading guard before timeline events are dispatched.
                mixSeedEntry = null
                if (mix.start(c, entry, songs)) {
                    discardPreview()
                    _mixState.value = MixUiState(title = seed.title)
                    refresh(c)
                } else _mixState.value = _mixState.value.copy(loading = false, error = "Keine passenden Titel verfügbar. Bitte erneut versuchen.")
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                if (request == mixRequest) _mixState.value = _mixState.value.copy(loading = false, error = "Mix konnte nicht geladen werden. Prüfe deine Verbindung.")
            } finally { if (request == mixRequest) mixSeedEntry = null }
        }
    }

    fun restoreMixQueue() = act { c ->
        cancelMixLoading()
        discardPreview()
        mix.restore(c)
        _mixState.value = MixUiState()
        refresh(c)
    }

    fun togglePlayPause() = act { c ->
        discardPreview()
        if (c.isPlaying) c.pause() else {
            if (c.playbackState == Player.STATE_ENDED) c.seekToDefaultPosition()
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            c.play()
        }
    }

    fun next() = act { cancelMixLoading(); discardPreview(); it.seekToNext() }
    fun previous() = act { cancelMixLoading(); discardPreview(); it.seekToPrevious() }
    fun seekTo(positionMs: Long) = act { it.seekTo(positionMs) }
    fun playIndex(index: Int) = act {
        cancelMixLoading()
        discardPreview()
        if (index in 0 until it.mediaItemCount) { it.seekToDefaultPosition(index); it.play() }
    }
    fun enqueueNext(track: Track) = act { cancelMixLoading(); it.enqueue(track.toMediaItem(), next = true) }
    fun enqueueLater(track: Track) = act { cancelMixLoading(); it.enqueue(track.toMediaItem(), next = false) }
    fun moveQueueItem(from: Int, to: Int) = act { cancelMixLoading(); it.moveQueuedItem(from, to) }
    fun removeQueueItem(index: Int) = act { cancelMixLoading(); it.removeQueuedItem(index) }

    fun toggleShuffle() = act { c -> c.shuffleModeEnabled = !c.shuffleModeEnabled }

    fun cycleRepeat() = act { c ->
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    private fun act(block: (MediaController) -> Unit) {
        _state.value = _state.value.copy(errorMessage = null)
        val c = controller
        if (c != null) block(c) else pendingAction = block
    }

    private fun ensurePreviewPlayer(): ExoPlayer = previewExo ?: ExoPlayer.Builder(context, EqualizerRenderersFactory(context, equalizer))
        .setMediaSourceFactory(DefaultMediaSourceFactory(context).setDataSourceFactory(streams.resolvingFactory()))
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            /* handleAudioFocus = */ true,
        )
        .setHandleAudioBecomingNoisy(true)
        .build()
        .also {
            it.addListener(previewListener)
            previewExo = it
        }

    /** Ends the preview and reports whether the main playback was playing before it started. */
    private fun stopPreview(): Boolean {
        if (previewTrack == null) return false
        val resume = resumeMainAfterPreview
        discardPreview()
        return resume
    }

    /** Drops the preview without resuming the main playback, for user actions that take over playback. */
    private fun discardPreview() {
        previewTrack = null
        resumeMainAfterPreview = false
        previewExo?.run {
            stop()
            clearMediaItems()
        }
    }

    private fun refresh(player: Player) {
        val items = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).toTrack() }
        val index = player.currentMediaItemIndex
        val preview = previewExo?.takeIf { previewTrack != null }
        _state.value = PlayerUiState(
            current = items.getOrNull(index),
            queue = items,
            currentIndex = index,
            isPlaying = player.isPlaying,
            isBuffering = player.playbackState == Player.STATE_BUFFERING,
            positionMs = player.currentPosition.coerceAtLeast(0),
            durationMs = player.duration.coerceAtLeast(0),
            shuffle = player.shuffleModeEnabled,
            repeat = player.repeatMode,
            errorMessage = _state.value.errorMessage,
            queueEntryIds = (0 until player.mediaItemCount).map { index ->
                player.getMediaItemAt(index).mediaMetadata.extras?.getString("queueEntryId") ?: "external:$index"
            },
            playlistOrigin = if (mix.active) null else player.currentMediaItem?.playlistOrigin(),
            isPreview = previewTrack != null,
            previewTrack = previewTrack,
            previewPlaying = preview?.isPlaying == true,
            previewPositionMs = preview?.currentPosition?.coerceAtLeast(0) ?: 0L,
            previewDurationMs = preview?.duration?.coerceAtLeast(0) ?: 0L,
        )
    }

    private companion object {
        const val POSITION_TICK_MS = 200L
    }
}

private const val STREAM_SCHEME = "pyte://stream/"

internal fun Track.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(videoId)
    .setUri("$STREAM_SCHEME$videoId")
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setExtras(Bundle().apply {
                putLong("durationMs", durationMs)
                putString("queueEntryId", java.util.UUID.randomUUID().toString())
            })
            .setArtworkUri(thumbnailUrl?.let(android.net.Uri::parse))
            .build(),
    )
    .build()

internal fun MediaItem.toTrack(): Track = Track(
    videoId = mediaId,
    title = mediaMetadata.title?.toString().orEmpty(),
    artist = mediaMetadata.artist?.toString().orEmpty(),
    durationMs = mediaMetadata.extras?.getLong("durationMs", 0L) ?: 0L,
    thumbnailUrl = mediaMetadata.artworkUri?.toString(),
)

internal fun MediaItem.withPlaylistOrigin(origin: PlaylistPlaybackOrigin?): MediaItem {
    if (origin == null) return this
    val extras = Bundle(mediaMetadata.extras ?: Bundle()).apply {
        putString("sourcePlaylistId", origin.playlistId)
        putString("playbackRunId", origin.runId)
        putString("playlistLastEntryId", origin.lastEntryId)
    }
    return buildUpon().setMediaMetadata(mediaMetadata.buildUpon().setExtras(extras).build()).build()
}

private fun MediaItem.playlistOrigin(): PlaylistPlaybackOrigin? {
    val extras = mediaMetadata.extras ?: return null
    return PlaylistPlaybackOrigin(extras.getString("sourcePlaylistId") ?: return null,
        extras.getString("playbackRunId") ?: return null, extras.getString("playlistLastEntryId") ?: return null)
}
