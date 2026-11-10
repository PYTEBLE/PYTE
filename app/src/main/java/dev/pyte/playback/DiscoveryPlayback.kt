package dev.pyte.playback

import androidx.media3.common.Player

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

