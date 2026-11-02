package dev.pyte.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlaybackMediaItemsTest {
    @Test
    fun `controller item without local configuration becomes playable`() {
        val metadata = MediaMetadata.Builder().setTitle("Test song").setArtist("Test artist").build()
        val requested = MediaItem.Builder().setMediaId("dQw4w9WgXcQ").setMediaMetadata(metadata).build()
        assertNull(requested.localConfiguration)

        val resolved = resolvePlaybackItem(requested)

        assertEquals("pyte://stream/dQw4w9WgXcQ", resolved.localConfiguration?.uri.toString())
        assertEquals(requested.mediaId, resolved.mediaId)
        assertEquals(metadata, resolved.mediaMetadata)
    }

    @Test
    fun `service does not trust a controller supplied stream URL`() {
        val requested = MediaItem.Builder().setMediaId("dQw4w9WgXcQ")
            .setUri("https://example.com/untrusted.mp3").build()
        assertEquals("pyte://stream/dQw4w9WgXcQ", resolvePlaybackItem(requested).localConfiguration?.uri.toString())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `malformed track IDs are rejected`() {
        resolvePlaybackItem(MediaItem.Builder().setMediaId("../../invalid").build())
    }
}
