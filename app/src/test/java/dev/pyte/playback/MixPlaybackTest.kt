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
class MixPlaybackTest {
    private val seed = Track("YGr2G2fc48U", "Seed", "Artist", 120000, null)
    private val other = seed.copy(videoId = "dQw4w9WgXcQ", title = "New discovery")

    @Test fun `mix keeps current item position and pause intent then restores original queue and modes`() = withPlayer { player ->
        val items = listOf(seed.toMediaItem(), seed.toMediaItem(), other.toMediaItem())
        player.setMediaItems(items, 1, 42000)
        player.shuffleModeEnabled = true
        player.repeatMode = Player.REPEAT_MODE_ALL
        val mix = MixPlayback()
        assertTrue(mix.start(player, entry(player), listOf(seed, other, other)))
        assertEquals(items[1], player.currentMediaItem)
        assertEquals(42000L, player.currentPosition)
        assertFalse(player.playWhenReady)
        assertEquals(3, player.mediaItemCount)
        assertFalse(player.shuffleModeEnabled)
        assertEquals(Player.REPEAT_MODE_OFF, player.repeatMode)
        mix.restore(player)
        assertFalse(mix.active)
        assertEquals(items, (0 until player.mediaItemCount).map(player::getMediaItemAt))
        assertEquals(42000L, player.currentPosition)
        assertTrue(player.shuffleModeEnabled)
        assertEquals(Player.REPEAT_MODE_ALL, player.repeatMode)
    }

    @Test fun `empty results and stale duplicate queue entries never mutate playback`() = withPlayer { player ->
        val items = listOf(seed.toMediaItem(), seed.toMediaItem())
        player.setMediaItems(items, 0, 8000)
        val stale = entry(player)
        val mix = MixPlayback()
        assertFalse(mix.start(player, stale, emptyList()))
        assertFalse(mix.start(player, stale, listOf(seed)))
        player.seekTo(1, 9000)
        assertFalse(mix.start(player, stale, listOf(other)))
        assertFalse(mix.active)
        assertEquals(items, (0 until player.mediaItemCount).map(player::getMediaItemAt))
        assertEquals(9000L, player.currentPosition)
    }

    @Test fun `repeated mixes retain the first original queue`() = withPlayer { player ->
        val items = listOf(seed.toMediaItem(), seed.toMediaItem())
        player.setMediaItems(items, 0, 12000)
        val mix = MixPlayback()
        assertTrue(mix.start(player, entry(player), listOf(other)))
        assertEquals(other.videoId, player.getMediaItemAt(1).mediaId)
        assertTrue(mix.start(player, entry(player), listOf(other)))
        mix.restore(player)
        assertEquals(items, (0 until player.mediaItemCount).map(player::getMediaItemAt))
    }

    @Test fun `discard prevents an old mix from restoring over a new user queue`() = withPlayer { player ->
        player.setMediaItem(seed.toMediaItem())
        val mix = MixPlayback()
        assertTrue(mix.start(player, entry(player), listOf(other)))
        mix.discard()
        player.setMediaItem(other.toMediaItem())
        mix.restore(player)
        assertEquals(other.videoId, player.currentMediaItem!!.mediaId)
    }

    @Test fun `empty retry leaves an active mix and its playing intent untouched`() = withPlayer { player ->
        val original = listOf(seed.toMediaItem(), seed.toMediaItem())
        player.setMediaItems(original, 0, 15000)
        player.playWhenReady = true
        val mix = MixPlayback()
        assertTrue(mix.start(player, entry(player), listOf(other)))
        val mixed = (0 until player.mediaItemCount).map(player::getMediaItemAt)
        assertFalse(mix.start(player, entry(player), emptyList()))
        assertTrue(mix.active)
        assertTrue(player.playWhenReady)
        assertEquals(mixed, (0 until player.mediaItemCount).map(player::getMediaItemAt))
        assertEquals(15000L, player.currentPosition)
        mix.restore(player)
        assertTrue(player.playWhenReady)
        assertEquals(original, (0 until player.mediaItemCount).map(player::getMediaItemAt))
    }

    private fun entry(player: Player) = player.currentMediaItem!!.mediaMetadata.extras!!.getString("queueEntryId")!!
    private fun withPlayer(block: (ExoPlayer) -> Unit) {
        val player = ExoPlayer.Builder(RuntimeEnvironment.getApplication()).build()
        try { block(player) } finally { player.release() }
    }
}
