@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package dev.pyte.playback

import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.LoadControl

/** Normal startup/rebuffer thresholds remain untouched, even with a larger steady-state buffer. */
internal class BatteryLoadControl(private val burstEnabled: () -> Boolean) : DefaultLoadControl() {
    private var filling = true

    override fun shouldContinueLoading(parameters: LoadControl.Parameters): Boolean {
        if (!burstEnabled()) {
            filling = true
            return super.shouldContinueLoading(parameters)
        }
        val seconds = parameters.bufferedDurationUs / 1_000_000.0 / parameters.playbackSpeed.coerceAtLeast(.1f)
        // Bound memory independently of time, particularly for unusual high-bitrate sources.
        if (seconds >= 90 || getAllocator(parameters.playerId).totalBytesAllocated >= 8 * 1024 * 1024) filling = false
        else if (seconds < 30) filling = true
        // Never deadlock playback under the byte cap when little playable media is available.
        return filling || seconds < 1
    }
}
