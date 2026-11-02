package dev.pyte.playback

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
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
) {
    val hasItems: Boolean get() = queue.isNotEmpty()
}

/**
 * UI-side handle to [PlaybackService]. The UI never owns the player; it only sends
 * commands through a MediaController and mirrors the state it reports.
 */
@Singleton
class PlayerController @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private var controller: MediaController? = null
    private var pendingAction: ((MediaController) -> Unit)? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            refresh(player)
        }

        override fun onPlayerError(error: PlaybackException) {
            _state.value = _state.value.copy(
                errorMessage = playbackErrorMessage(error.errorCodeName, error.cause),
            )
        }
    }

    init {
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
                delay(POSITION_TICK_MS)
                controller?.takeIf { it.isPlaying }?.let { c ->
                    _state.value = _state.value.copy(positionMs = c.currentPosition, durationMs = c.duration.coerceAtLeast(0))
                }
            }
        }
    }

    fun playQueue(tracks: List<Track>, startIndex: Int = 0, shuffle: Boolean = false) {
        if (tracks.isEmpty()) return
        act { c ->
            c.setMediaItems(tracks.map { it.toMediaItem() }, startIndex.coerceIn(0, tracks.lastIndex), 0L)
            c.shuffleModeEnabled = shuffle
            c.prepare()
            c.play()
        }
    }

    fun togglePlayPause() = act { c ->
        if (c.isPlaying) c.pause() else {
            if (c.playbackState == Player.STATE_ENDED) c.seekToDefaultPosition()
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            c.play()
        }
    }

    fun next() = act { it.seekToNext() }
    fun previous() = act { it.seekToPrevious() }
    fun seekTo(positionMs: Long) = act { it.seekTo(positionMs) }
    fun playIndex(index: Int) = act {
        if (index in 0 until it.mediaItemCount) { it.seekToDefaultPosition(index); it.play() }
    }
    fun enqueueNext(track: Track) = act { it.enqueue(track.toMediaItem(), next = true) }
    fun enqueueLater(track: Track) = act { it.enqueue(track.toMediaItem(), next = false) }
    fun moveQueueItem(from: Int, to: Int) = act { it.moveQueuedItem(from, to) }
    fun removeQueueItem(index: Int) = act { it.removeQueuedItem(index) }

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

    private fun refresh(player: Player) {
        val items = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).toTrack() }
        val index = player.currentMediaItemIndex
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
