package dev.pyte.playback

import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.Player
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import kotlin.random.Random
import dev.pyte.domain.Track
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class QueueActionsTest {
    private fun item(id: String) = MediaItem.Builder().setMediaId(id).setUri("https://example.com/$id.mp3").build()
    private fun withPlayer(block: (ExoPlayer) -> Unit) {
        val player = ExoPlayer.Builder(RuntimeEnvironment.getApplication()).build()
        try { player.setMediaItems(listOf(item("a"), item("b"), item("c"))); block(player) }
        finally { player.release() }
    }
    private fun ExoPlayer.ids() = (0 until mediaItemCount).map { getMediaItemAt(it).mediaId }

    @Test fun `play next preserves current item and disables shuffle for predictable order`() = withPlayer { player ->
        player.seekTo(1, 1200L)
        player.shuffleModeEnabled = true
        player.enqueue(item("next"), next = true)
        assertEquals(listOf("a", "b", "next", "c"), player.ids())
        assertEquals("b", player.currentMediaItem!!.mediaId)
        assertEquals(1200L, player.currentPosition)
        assertFalse(player.shuffleModeEnabled)
    }

    @Test fun `append and move preserve current playback identity and position`() = withPlayer { player ->
        player.seekTo(1, 2000L)
        player.enqueue(item("later"), next = false)
        player.moveQueuedItem(0, 3)
        assertEquals(listOf("b", "c", "later", "a"), player.ids())
        assertEquals("b", player.currentMediaItem!!.mediaId)
        assertEquals(2000L, player.currentPosition)
    }

    @Test fun `stale invalid queue indexes do not modify queue`() = withPlayer { player ->
        player.moveQueuedItem(-1, 1)
        player.moveQueuedItem(0, 9)
        player.removeQueuedItem(8)
        assertEquals(listOf("a", "b", "c"), player.ids())
    }

    @Test fun `removing an upcoming item keeps the current track`() = withPlayer { player ->
        player.removeQueuedItem(1)
        assertEquals(listOf("a", "c"), player.ids())
        assertEquals("a", player.currentMediaItem!!.mediaId)
    }

    @Test fun `track duration survives media controller metadata round trip`() {
        val track = Track("YGr2G2fc48U", "Song", "Artist", 155000L, null)
        assertEquals(track, track.toMediaItem().toTrack())
    }

    @Test fun `shuffle visits every entry from every possible random start without repeat`() = withPlayer { player ->
        player.setMediaItems((0 until 20).map { item("song$it") })
        player.shuffleModeEnabled = true
        for (start in 0 until 20) {
            player.seekTo(start, 1234L)
            player.shuffleFromCurrent(Random(start))
            assertEquals(1234L, player.currentPosition)
            assertEquals(Player.REPEAT_MODE_OFF, player.repeatMode)
            val timeline = player.currentTimeline
            val visited = mutableListOf(start)
            var next = timeline.getNextWindowIndex(start, Player.REPEAT_MODE_OFF, true)
            while (next != androidx.media3.common.C.INDEX_UNSET) {
                visited += next
                assertTrue("Traversal must terminate", visited.size <= 20)
                next = timeline.getNextWindowIndex(next, Player.REPEAT_MODE_OFF, true)
            }
            assertEquals((0 until 20).toSet(), visited.toSet())
            assertEquals(20, visited.size)
        }
    }

    @Test fun `unanchored random start reproduces a short shuffle suffix`() = withPlayer { player ->
        player.setShuffleOrder(DefaultShuffleOrder(intArrayOf(0, 1, 2), 0L))
        player.shuffleModeEnabled = true
        player.seekTo(2, 0L)
        assertFalse(player.hasNextMediaItem())
        player.shuffleFromCurrent(Random(1))
        assertTrue(player.hasNextMediaItem())
    }
}
