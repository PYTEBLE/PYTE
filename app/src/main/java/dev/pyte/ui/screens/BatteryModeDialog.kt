package dev.pyte.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
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
import dev.pyte.R
import androidx.compose.ui.res.stringResource

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
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.battery)) },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.battery_intro))
                if (enabled) Text(stringResource(if (metered) R.string.battery_metered else R.string.battery_unmetered),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                SettingsSwitch(stringResource(R.string.battery_enable), stringResource(R.string.battery_enable_help), enabled, !busy, { enabled = it })
                SettingsSwitch(stringResource(R.string.battery_quality), stringResource(R.string.battery_quality_help), lowData, enabled && !busy, { lowData = it })
                SettingsSwitch(stringResource(R.string.battery_buffer), stringResource(R.string.battery_buffer_help), buffer, enabled && !busy, { buffer = it })
                SettingsSwitch(stringResource(R.string.battery_eq), stringResource(R.string.battery_eq_help), eq, enabled && !busy, { eq = it })
                SettingsSwitch(stringResource(R.string.battery_effects), stringResource(R.string.battery_effects_help), effects, enabled && !busy, { effects = it })
                SettingsSwitch(stringResource(R.string.battery_updates), stringResource(R.string.battery_updates_help), updates, enabled && !busy, { updates = it })
                SettingsSwitch(stringResource(R.string.battery_downloads), stringResource(R.string.battery_downloads_help), downloads, enabled && !busy, { downloads = it })
                SettingsSwitch(stringResource(R.string.battery_prefetch), stringResource(R.string.battery_prefetch_help), prefetch, enabled && !busy, { prefetch = it })
                Text(stringResource(R.string.battery_timer), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.battery_timer_help))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(0, 15, 30, 60).forEach { minutes ->
                        FilterChip(selected = timer == minutes, enabled = enabled && !busy,
                            onClick = { timer = minutes }, label = { Text(if (minutes == 0) stringResource(R.string.off) else stringResource(R.string.minutes, minutes)) })
                    }
                }
                Text(stringResource(R.string.battery_apply_help), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(enabled = !busy, onClick = {
            onSave(BatterySettings(enabled, lowData, buffer, eq, effects, updates, downloads, prefetch, timer)); onDismiss()
        }) { Text(stringResource(R.string.apply)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}
