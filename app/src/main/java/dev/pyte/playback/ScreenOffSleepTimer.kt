package dev.pyte.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import androidx.core.content.ContextCompat
import dev.pyte.data.settings.BatterySettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Service-owned timer: no Activity reference, no alarms or extra wake lock. */
internal class ScreenOffSleepTimer(private val context: Context, private val scope: CoroutineScope,
    private val pause: () -> Unit) {
    private var battery = BatterySettings()
    private var playing = false
    private var job: Job? = null
    private var activeMinutes = 0
    private val power = context.getSystemService(PowerManager::class.java)
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // Reset even when ON/OFF happen before another UI/settings update.
            job?.cancel(); job = null; activeMinutes = 0
            refresh()
        }
    }
    init {
        ContextCompat.registerReceiver(context, receiver, IntentFilter(Intent.ACTION_SCREEN_ON).apply {
            addAction(Intent.ACTION_SCREEN_OFF)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
    }
    fun update(value: BatterySettings, isPlaying: Boolean) {
        battery = value; playing = isPlaying; refresh()
    }
    private fun refresh() {
        val minutes = if (battery.enabled && playing && !power.isInteractive) battery.screenOffTimerMinutes else 0
        if (minutes == activeMinutes) return
        job?.cancel(); job = null; activeMinutes = minutes
        if (minutes > 0) job = scope.launch {
            delay(minutes * 60_000L)
            if (battery.enabled && playing && !power.isInteractive) pause()
        }
    }
    fun close() { job?.cancel(); context.unregisterReceiver(receiver) }
}
