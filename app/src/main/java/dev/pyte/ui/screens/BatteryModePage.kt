package dev.pyte.ui.screens

import dev.pyte.ui.components.feedbackClick

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.pyte.R
import dev.pyte.data.settings.BatterySettings

@Composable
internal fun BatteryModePage(settings: BatterySettings, busy: Boolean, onSave: (BatterySettings) -> Unit, onBack: () -> Unit) {
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
    val methods = enabled && !busy
    SettingsDetail(stringResource(R.string.battery), onBack) {
        Text(stringResource(R.string.battery_intro), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (enabled) Text(stringResource(if (metered) R.string.battery_metered else R.string.battery_unmetered),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        SettingsSection {
            SettingsSwitch(stringResource(R.string.battery_enable), stringResource(R.string.battery_enable_help), enabled, !busy, { enabled = it })
        }
        SettingsSection(stringResource(R.string.battery_methods)) {
            SettingsSwitch(stringResource(R.string.battery_quality), stringResource(R.string.battery_quality_help), lowData, methods, { lowData = it })
            SettingsSwitch(stringResource(R.string.battery_buffer), stringResource(R.string.battery_buffer_help), buffer, methods, { buffer = it })
            SettingsSwitch(stringResource(R.string.battery_eq), stringResource(R.string.battery_eq_help), eq, methods, { eq = it })
            SettingsSwitch(stringResource(R.string.battery_effects), stringResource(R.string.battery_effects_help), effects, methods, { effects = it })
            SettingsSwitch(stringResource(R.string.battery_updates), stringResource(R.string.battery_updates_help), updates, methods, { updates = it })
            SettingsSwitch(stringResource(R.string.battery_downloads), stringResource(R.string.battery_downloads_help), downloads, methods, { downloads = it })
            SettingsSwitch(stringResource(R.string.battery_prefetch), stringResource(R.string.battery_prefetch_help), prefetch, methods, { prefetch = it })
        }
        SettingsSection(stringResource(R.string.battery_timer_title)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.battery_timer), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.battery_timer_help), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                TimerPicker(timer, methods, { timer = it })
            }
        }
        Text(stringResource(R.string.battery_apply_help), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = feedbackClick(onBack), modifier = Modifier.weight(1f)) { Text(stringResource(R.string.cancel)) }
            Button(enabled = !busy, modifier = Modifier.weight(1f), onClick = feedbackClick {
                onSave(BatterySettings(enabled, lowData, buffer, eq, effects, updates, downloads, prefetch, timer))
                onBack()
            }) { Text(stringResource(R.string.apply)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimerPicker(minutes: Int, enabled: Boolean, onSelect: (Int) -> Unit) {
    val options = listOf(0, 15, 30, 60)
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, value ->
            SegmentedButton(
                selected = minutes == value,
                onClick = feedbackClick { onSelect(value) },
                enabled = enabled,
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                label = { Text(if (value == 0) stringResource(R.string.off) else stringResource(R.string.minutes, value)) },
            )
        }
    }
}
