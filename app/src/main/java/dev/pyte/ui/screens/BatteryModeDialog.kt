package dev.pyte.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.pyte.data.settings.BatterySettings
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import androidx.compose.ui.platform.LocalContext

@Composable
internal fun BatteryModeDialog(settings: BatterySettings, busy: Boolean,
    onSave: (BatterySettings) -> Unit, onDismiss: () -> Unit) {
    // Draft controls deliberately don't change playback until Apply is pressed.
    var enabled by rememberSaveable { mutableStateOf(settings.enabled) }
    var lowData by rememberSaveable { mutableStateOf(settings.lowData) }
    var buffer by rememberSaveable { mutableStateOf(settings.burstBuffer) }
    var eq by rememberSaveable { mutableStateOf(settings.bypassDisabledEq) }
    var effects by rememberSaveable { mutableStateOf(settings.reduceEffects) }
    var updates by rememberSaveable { mutableStateOf(settings.reduceBackgroundUpdates) }
    var downloads by rememberSaveable { mutableStateOf(settings.pauseDownloads) }
    var prefetch by rememberSaveable { mutableStateOf(settings.disablePrefetch) }
    var timer by rememberSaveable { mutableStateOf(settings.screenOffTimerMinutes) }
    val context = LocalContext.current
    val network = remember(context) { context.getSystemService(ConnectivityManager::class.java) }
    var metered by remember { mutableStateOf(network.isActiveNetworkMetered) }
    DisposableEffect(network) {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(n: Network, capabilities: NetworkCapabilities) { metered = network.isActiveNetworkMetered }
            override fun onLost(n: Network) { metered = network.isActiveNetworkMetered }
        }
        network.registerDefaultNetworkCallback(callback, Handler(Looper.getMainLooper()))
        onDispose { network.unregisterNetworkCallback(callback) }
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Battery mode") },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                Text("Choose your trade-offs. Changes apply only while battery mode is enabled. Savings depend on signal strength and your device.")
                if (enabled) Text(if (metered) "Metered network: mobile-data quality saving can apply." else "Unmetered network: normal streaming quality.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                SettingsSwitch("Enable battery mode", "Off restores normal behavior; your choices stay saved.", enabled, !busy, { enabled = it })
                SettingsSwitch("Lower mobile-data quality", "Prefer audio up to 64 kbps on metered networks. Applies to newly loaded tracks; existing audio is not interrupted.", lowData, enabled && !busy, { lowData = it })
                SettingsSwitch("Buffer in larger bursts", "Load up to 90 seconds before resting. Skipping can waste more data. Does not increase the startup threshold.", buffer, enabled && !busy, { buffer = it })
                SettingsSwitch("Fast bypass for disabled EQ", "Skip per-sample processing when EQ and spectrum analysis are off. Enabled EQ stays intact.", eq, enabled && !busy, { eq = it })
                SettingsSwitch("Reduce visual effects", "Pause decorative motion and spectrum analysis. Your normal effect level stays saved.", effects, enabled && !busy, { effects = it })
                SettingsSwitch("Reduce background updates", "Stop polling playback position while the screen is off.", updates, enabled && !busy, { updates = it })
                SettingsSwitch("Pause offline downloads", "Defer new download work. Completed downloads remain playable.", downloads, enabled && !busy, { downloads = it })
                SettingsSwitch("Disable next-song lookahead", "Avoid speculative stream lookups; transitions may take longer.", prefetch, enabled && !busy, { prefetch = it })
                Text("Screen-off sleep timer", style = MaterialTheme.typography.titleSmall)
                Text("Optional: pause after continuous screen-off listening. Turning the screen on resets it; this is not needed for ordinary battery savings.")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(0, 15, 30, 60).forEach { minutes ->
                        FilterChip(selected = timer == minutes, enabled = enabled && !busy,
                            onClick = { timer = minutes }, label = { Text(if (minutes == 0) "Off" else "${minutes}m") })
                    }
                }
                Text("Network/quality changes apply at the next load. Playback is never restarted just to save battery.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(enabled = !busy, onClick = {
            onSave(BatterySettings(enabled, lowData, buffer, eq, effects, updates, downloads, prefetch, timer)); onDismiss()
        }) { Text("Apply") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}
