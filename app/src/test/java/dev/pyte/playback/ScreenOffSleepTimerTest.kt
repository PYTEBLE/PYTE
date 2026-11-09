package dev.pyte.playback

import android.content.Context
import android.content.Intent
import android.os.Looper
import android.os.PowerManager
import dev.pyte.data.settings.BatterySettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ScreenOffSleepTimerTest {
    @Test fun `screen on resets timer and master off cancels it without pausing`() {
        val context = RuntimeEnvironment.getApplication()
        val power = shadowOf(context.getSystemService(PowerManager::class.java))
        val looper = shadowOf(Looper.getMainLooper())
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        var pauses = 0
        val timer = ScreenOffSleepTimer(context, scope) { pauses++ }
        val enabled = BatterySettings(enabled = true, screenOffTimerMinutes = 15)
        try {
            power.setIsInteractive(false)
            timer.update(enabled, true)
            looper.idleFor(Duration.ofMinutes(10))
            assertEquals(0, pauses)
            power.setIsInteractive(true)
            context.sendBroadcast(Intent(Intent.ACTION_SCREEN_ON)); looper.idle()
            power.setIsInteractive(false)
            context.sendBroadcast(Intent(Intent.ACTION_SCREEN_OFF)); looper.idle()
            looper.idleFor(Duration.ofMinutes(10))
            assertEquals(0, pauses)
            looper.idleFor(Duration.ofMinutes(5))
            assertEquals(1, pauses)
            timer.update(enabled, false)
            timer.update(enabled, true)
            timer.update(enabled.copy(enabled = false), true)
            looper.idleFor(Duration.ofMinutes(20))
            assertEquals(1, pauses)
        } finally { timer.close(); scope.cancel() }
    }
}
