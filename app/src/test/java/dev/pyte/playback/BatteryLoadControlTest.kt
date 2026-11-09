@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package dev.pyte.playback

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.source.SinglePeriodTimeline
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.source.MediaSource
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BatteryLoadControlTest {
    private val timeline = SinglePeriodTimeline(300_000_000L, true, false, false, null,
        MediaItem.fromUri("https://audio.example/test.m4a"))
    private fun parameters(seconds: Int, speed: Float = 1f) = LoadControl.Parameters(
        PlayerId.UNSET, timeline, MediaSource.MediaPeriodId(timeline.getUidOfPeriod(0)), 0L,
        seconds * 1_000_000L, speed, true, false, C.TIME_UNSET, C.TIME_UNSET)

    @Test fun `bursts have hysteresis and account for playback speed`() {
        val control = BatteryLoadControl { true }
        control.onPrepared(PlayerId.UNSET)
        assertTrue(control.shouldContinueLoading(parameters(0)))
        assertTrue(control.shouldContinueLoading(parameters(60)))
        assertFalse(control.shouldContinueLoading(parameters(90)))
        assertFalse(control.shouldContinueLoading(parameters(45)))
        assertTrue(control.shouldContinueLoading(parameters(20)))
        assertTrue(control.shouldContinueLoading(parameters(90, 2f)))
        control.onReleased(PlayerId.UNSET)
    }

    @Test fun `mode off uses normal load control and startup threshold is unchanged`() {
        var burst = false
        val control = BatteryLoadControl { burst }
        val normal = DefaultLoadControl()
        control.onPrepared(PlayerId.UNSET); normal.onPrepared(PlayerId.UNSET)
        for (seconds in listOf(0, 1, 5, 30, 50, 90)) {
            assertEquals(normal.shouldContinueLoading(parameters(seconds)), control.shouldContinueLoading(parameters(seconds)))
            burst = true
            assertEquals(normal.shouldStartPlayback(parameters(seconds)), control.shouldStartPlayback(parameters(seconds)))
            burst = false
        }
        control.onReleased(PlayerId.UNSET); normal.onReleased(PlayerId.UNSET)
    }

    @Test fun `byte cap pauses loading without preventing recovery near empty`() {
        val control = BatteryLoadControl { true }
        control.onPrepared(PlayerId.UNSET)
        val allocator = control.getAllocator(PlayerId.UNSET)
        val allocations = List(140) { allocator.allocate() }
        assertFalse(control.shouldContinueLoading(parameters(60)))
        assertTrue(control.shouldContinueLoading(parameters(0)))
        allocations.forEach { allocator.release(it) }
        control.onReleased(PlayerId.UNSET)
    }
}
