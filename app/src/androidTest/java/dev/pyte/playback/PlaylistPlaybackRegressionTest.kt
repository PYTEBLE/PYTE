@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package dev.pyte.playback

import android.content.ComponentName
import android.content.Intent
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.pyte.MainActivity
import dev.pyte.data.local.PyteDatabase
import dev.pyte.domain.Track
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Real service/decoder/transitions with short silent local fixtures, never user's playlist songs. */
@RunWith(AndroidJUnit4::class)
class PlaylistPlaybackRegressionTest {
    @Test fun shuffleCompletesTwoFullRunsAndRemovalAdvancesWithoutRestartingTheQueue() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val tracks = (0 until 8).map { Track("pyteTest%03d".format(it), "Playback fixture $it", "PYTE test", 600L, null) }
        val files = tracks.map { File(context.filesDir, "offline/${it.videoId}.audio") }
        assertTrue("Never overwrite existing audio", files.none { it.exists() })
        files.forEach { it.parentFile!!.mkdirs(); it.writeBytes(wave()) }
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val controller = MediaController.Builder(context, SessionToken(context, ComponentName(context, PlaybackService::class.java)))
            .buildAsync().get(30, TimeUnit.SECONDS)
        val observed = Collections.synchronizedList(mutableListOf<String>())
        var ended = CountDownLatch(1)
        val listener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                mediaItem?.mediaMetadata?.extras?.getString("queueEntryId")?.let(observed::add)
            }
            override fun onPlaybackStateChanged(state: Int) { if (state == Player.STATE_ENDED) ended.countDown() }
        }
        try {
            instrumentation.runOnMainSync { controller.addListener(listener) }
            repeat(2) { run ->
                instrumentation.runOnMainSync {
                    observed.clear()
                    ended = CountDownLatch(1)
                    controller.repeatMode = Player.REPEAT_MODE_OFF
                    controller.shuffleModeEnabled = false
                    controller.setMediaItems(tracks.map { it.toMediaItem() }, if (run == 0) 3 else 6, 0L)
                    controller.shuffleModeEnabled = true
                    controller.prepare()
                    controller.play()
                }
                assertTrue("Shuffle run $run did not finish", ended.await(45, TimeUnit.SECONDS))
                assertEquals("Every queue entry must play exactly once in run $run", 8, observed.size)
                assertEquals(8, observed.toSet().size)
            }
            val origin = PlaylistPlaybackOrigin("local:device-playback-fixture", "test-run", "last")
            instrumentation.runOnMainSync {
                controller.pause()
                controller.shuffleModeEnabled = false
                controller.setMediaItems(tracks.map { it.toMediaItem().withPlaylistOrigin(origin) }, 2, 100L)
                controller.prepare()
                controller.removePlaylistTrack(origin.playlistId, tracks[6].videoId)
                assertEquals(tracks[2].videoId, controller.currentMediaItem?.mediaId)
                assertEquals(100L, controller.currentPosition)
                controller.removePlaylistTrack(origin.playlistId, tracks[2].videoId)
                assertEquals(tracks[3].videoId, controller.currentMediaItem?.mediaId)
                assertEquals(6, controller.mediaItemCount)
                assertFalse(controller.playWhenReady)
            }
        } finally {
            instrumentation.runOnMainSync {
                controller.removeListener(listener)
                controller.stop()
                controller.clearMediaItems()
                controller.release()
            }
            // Only fixture IDs are removed; no user's history or playlists are cleared.
            PyteDatabase.open(context).let { db ->
                try {
                    tracks.forEach { db.openHelper.writableDatabase.execSQL("DELETE FROM history WHERE videoId = ?", arrayOf(it.videoId)) }
                } finally { db.close() }
            }
            files.forEach { it.delete() }
        }
    }

    private fun wave(): ByteArray {
        val samples = 4_800 // 600 ms at 8 kHz, PCM16 mono silence
        return ByteBuffer.allocate(44 + samples * 2).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + samples * 2); put("WAVEfmt ".toByteArray())
            putInt(16); putShort(1); putShort(1); putInt(8_000); putInt(16_000); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(samples * 2)
            repeat(samples) { putShort(0) }
        }.array()
    }
}
