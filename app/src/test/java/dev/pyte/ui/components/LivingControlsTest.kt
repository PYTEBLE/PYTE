package dev.pyte.ui.components

import dev.pyte.domain.Track
import dev.pyte.playback.PlayerUiState
import dev.pyte.ui.screens.queueAnimationKeys
import org.junit.Assert.*
import org.junit.Test

class LivingControlsTest {
    private val queue = listOf("a", "b", "c").map { Track(it, it, "Artist", 1000, null) }
    @Test fun `song direction handles next previous wraparound and shuffle`() {
        assertEquals(1, songDirection("a", 1, queue, false))
        assertEquals(-1, songDirection("c", 1, queue, false))
        assertEquals(1, songDirection("c", 0, queue, false))
        assertEquals(-1, songDirection("a", 2, queue, false))
        assertEquals(1, songDirection("c", 0, queue, true))
        assertEquals(1, songDirection("missing", 0, queue, false))
    }
    @Test fun `duplicate songs keep distinct queue entry identities when moving`() {
        val songs = listOf(queue[0], queue[0], queue[1])
        val state = PlayerUiState(queue = songs, queueEntryIds = listOf("first", "second", "third"))
        assertEquals(listOf("first", "second", "third"), queueAnimationKeys(state))
        val moved = state.copy(queue = listOf(songs[1], songs[2], songs[0]), queueEntryIds = listOf("second", "third", "first"))
        assertEquals(listOf("second", "third", "first"), queueAnimationKeys(moved))
        assertEquals(3, queueAnimationKeys(state.copy(queueEntryIds = emptyList())).distinct().size)
        assertEquals(3, queueAnimationKeys(state.copy(queueEntryIds = listOf("x", "x", "x"))).distinct().size)
    }
}
