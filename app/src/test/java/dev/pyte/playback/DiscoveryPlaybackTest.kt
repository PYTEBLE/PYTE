package dev.pyte.playback

import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dev.pyte.domain.Track
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DiscoveryPlaybackTest {
    private val first = Track("YGr2G2fc48U", "First", "Artist", 120000, null)
    private val last = first.copy(videoId = "dQw4w9WgXcQ", title = "Last")
    private val origin = PlaylistPlaybackOrigin("local:test", "run1", "entry2")
    private fun finalSong() = PlayerUiState(last, listOf(first, last), 1, isPlaying = true,
        queueEntryIds = listOf("entry1", "entry2"), playlistOrigin = origin)

    @Test fun `last song prompts only once per playlist playback run`() {
        val gate = DiscoveryPromptGate()
        assertTrue(gate.shouldOpen(finalSong(), true, true, true))
        assertFalse(gate.shouldOpen(finalSong().copy(positionMs = 200), true, true, true))
        assertTrue(gate.shouldOpen(finalSong().copy(playlistOrigin = origin.copy(runId = "run2")), true, true, true))
    }

    @Test fun `repeat shuffle background disabled and obstructed runs cannot produce delayed popups`() {
        for ((state, flags) in listOf(
            finalSong().copy(shuffle = true) to Triple(true, true, true),
            finalSong().copy(repeat = Player.REPEAT_MODE_ONE) to Triple(true, true, true),
            finalSong().copy(repeat = Player.REPEAT_MODE_ALL) to Triple(true, true, true),
            finalSong() to Triple(false, true, true), finalSong() to Triple(true, false, true),
            finalSong() to Triple(true, true, false))) {
            val gate = DiscoveryPromptGate()
            assertFalse(gate.shouldOpen(state, flags.first, flags.second, flags.third))
            assertFalse(gate.shouldOpen(finalSong(), true, true, true))
        }
    }

    @Test fun `paused previews non-playlist queues and non-final entries do not trigger`() {
        for (state in listOf(finalSong().copy(isPlaying = false), finalSong().copy(isPreview = true),
            finalSong().copy(playlistOrigin = null), finalSong().copy(currentIndex = 0),
            finalSong().copy(queueEntryIds = listOf("entry1", "different")),
            finalSong().copy(queue = listOf(first, last, first)))) {
            assertFalse(DiscoveryPromptGate().shouldOpen(state, true, true, true))
        }
    }

}
