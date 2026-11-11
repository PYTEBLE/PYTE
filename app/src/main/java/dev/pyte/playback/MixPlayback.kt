package dev.pyte.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import dev.pyte.domain.Track

data class MixUiState(val loading: Boolean = false, val title: String? = null, val error: String? = null)

/** Only edits upcoming items, leaving the decoder, current item and position untouched. */
internal class MixPlayback {
    private data class Snapshot(
        val items: List<MediaItem>, val index: Int, val position: Long,
        val shuffle: Boolean, val repeat: Int, val playing: Boolean,
    )
    private var snapshot: Snapshot? = null
    val active: Boolean get() = snapshot != null

    fun start(player: Player, expectedEntryId: String, songs: List<Track>): Boolean {
        if (player.currentMediaItem?.mediaMetadata?.extras?.getString("queueEntryId") != expectedEntryId) return false
        val candidates = songs.distinctBy { it.videoId }.filter { it.videoId != player.currentMediaItem?.mediaId }
        if (candidates.isEmpty()) return false
        if (snapshot == null) snapshot = Snapshot(
            (0 until player.mediaItemCount).map(player::getMediaItemAt), player.currentMediaItemIndex,
            player.currentPosition, player.shuffleModeEnabled, player.repeatMode, player.playWhenReady,
        )
        player.shuffleModeEnabled = false
        player.repeatMode = Player.REPEAT_MODE_OFF
        val next = player.currentMediaItemIndex + 1
        player.removeMediaItems(next, player.mediaItemCount)
        player.addMediaItems(candidates.map { it.toMediaItem() })
        return true
    }

    fun restore(player: Player) {
        val saved = snapshot ?: return
        snapshot = null
        if (saved.items.isEmpty()) {
            player.clearMediaItems()
            player.shuffleModeEnabled = saved.shuffle
            player.repeatMode = saved.repeat
            return
        }
        player.setMediaItems(saved.items, saved.index, saved.position)
        player.shuffleModeEnabled = saved.shuffle
        player.repeatMode = saved.repeat
        player.prepare()
        player.playWhenReady = saved.playing
    }

    fun discard() { snapshot = null }

    fun removePlaylistTrack(playlistId: String, videoId: String) {
        val saved = snapshot ?: return
        val remaining = saved.items.filterNot { it.belongsToPlaylist(playlistId, videoId) }
        val current = saved.items.getOrNull(saved.index)
        val index = remaining.indexOf(current)
        val fallback = saved.items.take(saved.index).count { !it.belongsToPlaylist(playlistId, videoId) }
        snapshot = saved.copy(items = remaining, index = if (index >= 0) index else fallback.coerceAtMost(remaining.lastIndex),
            position = if (index >= 0) saved.position else 0L)
    }
}
