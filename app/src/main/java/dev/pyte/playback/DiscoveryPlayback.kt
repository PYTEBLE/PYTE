package dev.pyte.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import dev.pyte.domain.Track

data class PlaylistPlaybackOrigin(val playlistId: String, val runId: String, val lastEntryId: String)

/** An auditable one-shot gate: foregrounding or changing repeat must never resurrect an old popup. */
class DiscoveryPromptGate {
    private val visited = linkedSetOf<String>()

    fun shouldOpen(state: PlayerUiState, enabled: Boolean, foreground: Boolean, unobstructed: Boolean): Boolean {
        val origin = state.playlistOrigin ?: return false
        if (state.isPreview || !state.isPlaying || state.currentIndex != state.queue.lastIndex ||
            state.queueEntryIds.getOrNull(state.currentIndex) != origin.lastEntryId) return false
        if (!visited.add(origin.runId)) return false
        if (visited.size > 64) visited.remove(visited.first())
        return enabled && foreground && unobstructed && !state.shuffle && state.repeat == Player.REPEAT_MODE_OFF
    }
}

/** Uses the existing media session: no second audio player or competing audio focus. */
internal class PreviewPlayback {
    private data class Snapshot(
        val items: List<MediaItem>, val index: Int, val position: Long,
        val shuffle: Boolean, val repeat: Int, val playWhenReady: Boolean,
    )
    private var snapshot: Snapshot? = null
    val active: Boolean get() = snapshot != null

    fun play(player: Player, track: Track) {
        if (snapshot == null) snapshot = Snapshot(
            (0 until player.mediaItemCount).map(player::getMediaItemAt), player.currentMediaItemIndex,
            player.currentPosition.coerceAtLeast(0), player.shuffleModeEnabled, player.repeatMode, player.playWhenReady)
        player.shuffleModeEnabled = false
        player.repeatMode = Player.REPEAT_MODE_OFF
        player.setMediaItem(track.toMediaItem())
        player.prepare()
        player.play()
    }

    fun restore(player: Player) {
        val original = snapshot ?: return
        snapshot = null // Clear before events fire while restoring the original timeline.
        if (original.items.isEmpty()) {
            player.stop()
            player.clearMediaItems()
        } else {
            player.setMediaItems(original.items, original.index.coerceIn(original.items.indices), original.position)
            player.shuffleModeEnabled = original.shuffle
            player.repeatMode = original.repeat
            player.prepare()
            player.playWhenReady = original.playWhenReady
        }
    }

    fun discard() { snapshot = null }
}
