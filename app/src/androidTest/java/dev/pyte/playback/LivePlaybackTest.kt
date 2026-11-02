package dev.pyte.playback

import android.content.ComponentName
import android.content.Intent
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.pyte.MainActivity
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/** Opt-in, real network test. Normal test runs do not depend on YouTube availability. */
@RunWith(AndroidJUnit4::class)
class LivePlaybackTest {
    @Test
    fun playsPublicTrackAndContinuesBehindHomeScreen() {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(arguments.getString("livePlayback") == "true")
        val videoId = arguments.getString("videoId") ?: "dQw4w9WgXcQ"
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        val controller = future.get(60, TimeUnit.SECONDS)
        val playing = CountDownLatch(1)
        val errors = mutableListOf<String>()
        try {
            instrumentation.runOnMainSync {
                controller.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        if (isPlaying) playing.countDown()
                    }
                    override fun onPlayerError(error: PlaybackException) {
                        errors += error.errorCodeName
                    }
                })
                controller.setMediaItem(MediaItem.Builder().setMediaId(videoId).build())
                controller.prepare()
                controller.play()
            }
            val started = playing.await(120, TimeUnit.SECONDS)
            val initial = AtomicLong()
            var errorSummary = ""
            instrumentation.runOnMainSync {
                initial.set(controller.currentPosition)
                errorSummary = errors.joinToString()
            }
            assertTrue("No real playback started. Controller errors: $errorSummary", started)
            context.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            // Deliberately measure playback progress with the activity out of focus.
            Thread.sleep(5_000)
            val after = AtomicLong()
            var stillPlaying = false
            instrumentation.runOnMainSync {
                after.set(controller.currentPosition)
                stillPlaying = controller.isPlaying
            }
            assertTrue("Playback stopped in the background", stillPlaying)
            assertTrue("Audio position did not advance", after.get() > initial.get() + 1_000)
        } finally {
            instrumentation.runOnMainSync { controller.stop(); controller.release() }
        }
    }
}
