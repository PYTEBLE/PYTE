package dev.pyte.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.FilterChip
import dev.pyte.data.settings.EffectLevel
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pyte.BuildConfig
import dev.pyte.data.settings.AppSettings
import dev.pyte.ui.components.ScreenHeading
import dev.pyte.ui.viewmodels.SettingsViewModel
import dev.pyte.R
import androidx.compose.ui.res.stringResource

@Composable
fun SettingsScreen(contentPadding: PaddingValues, viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val loadFailed by viewModel.loadFailed.collectAsStateWithLifecycle()
    val offlineBytes by viewModel.offlineBytes.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var equalizerOpen by rememberSaveable { mutableStateOf(false) }
    var batteryOpen by rememberSaveable { mutableStateOf(false) }
    if (batteryOpen) settings?.let { value ->
        BatteryModeDialog(value.battery, busy, viewModel::battery, { batteryOpen = false })
    }
    if (equalizerOpen) EqualizerSheet(onDismiss = { equalizerOpen = false })
    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); viewModel.dismissMessage() }
    }
    Box(Modifier.fillMaxSize().padding(contentPadding).statusBarsPadding()) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            if (loadFailed) {
                ScreenHeading(stringResource(R.string.settings), stringResource(R.string.settings_load_error))
                Text(stringResource(R.string.settings_reopen), Modifier.padding(20.dp))
            } else {
                SettingsContent(settings, busy, viewModel::skipSilence, viewModel::recordHistory, viewModel::clearHistory, showInfo = false)
                settings?.let { value ->
                    Column(Modifier.padding(20.dp)) {
                        SettingsSection(stringResource(R.string.battery)) {
                            TextButton(onClick = { batteryOpen = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.battery_configure, stringResource(if (value.battery.enabled) R.string.on else R.string.off)))
                            }
                        }
                        SettingsSection(stringResource(R.string.sound)) {
                            TextButton(onClick = { equalizerOpen = true }, modifier = Modifier.padding(8.dp)) {
                                Text(stringResource(R.string.sound_studio))
                            }
                        }
                        SettingsSection(stringResource(R.string.discover)) {
                            SettingsSwitch(stringResource(R.string.recommendations), stringResource(R.string.recommendations_help),
                                value.autoRecommendations, !busy, viewModel::autoRecommendations)
                        }
                        SettingsSection(stringResource(R.string.appearance)) {
                            SettingsSwitch(stringResource(R.string.cover_theme), stringResource(R.string.cover_theme_help),
                                value.dynamicCoverTheme, !busy, viewModel::dynamicCoverTheme)
                            Text(stringResource(R.string.effects), Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.titleSmall)
                            Column(Modifier.padding(horizontal = 16.dp)) {
                                EffectLevel.entries.forEach { level ->
                                    FilterChip(selected = value.effectLevel == level, enabled = !busy,
                                        onClick = { viewModel.effectLevel(level) }, label = { Text(level.label) })
                                }
                            }
                            Text(stringResource(R.string.effects_help),
                                Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall)
                            SettingsSwitch(stringResource(R.string.haptics), stringResource(R.string.haptics_help),
                                value.hapticFeedback, !busy, viewModel::hapticFeedback)
                        }
                        SettingsSection(stringResource(R.string.offline_downloads)) {
                            Text(stringResource(R.string.storage_used, offlineBytes / (1024 * 1024), value.downloadLimitMb),
                                Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.downloads_help),
                                Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(stringResource(R.string.cache_help),
                                Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            SettingsSwitch(stringResource(R.string.pause_downloads), stringResource(R.string.pause_downloads_help),
                                value.downloadsPaused, !busy, viewModel::pauseDownloads)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                TextButton(onClick = { viewModel.downloadLimit((value.downloadLimitMb / 2).coerceAtLeast(512)) }, enabled = !busy) { Text(stringResource(R.string.lower_limit)) }
                                TextButton(onClick = { viewModel.downloadLimit((value.downloadLimitMb * 2).coerceAtMost(16384)) }, enabled = !busy) { Text(stringResource(R.string.raise_limit)) }
                            }
                        }
                        SettingsInformation()
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(horizontal = 20.dp))
    }
}

@Composable
internal fun SettingsContent(
    settings: AppSettings?,
    busy: Boolean,
    onSkipSilence: (Boolean) -> Unit,
    onRecordHistory: (Boolean) -> Unit,
    onClearHistory: () -> Unit,
    showInfo: Boolean = true,
) {
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    ScreenHeading(stringResource(R.string.settings), stringResource(R.string.settings_subtitle))
    if (settings == null) {
        CircularProgressIndicator(Modifier.padding(24.dp))
        return
    }
    Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SettingsSection(stringResource(R.string.playback)) {
            SettingsSwitch(stringResource(R.string.skip_silence), stringResource(R.string.skip_silence_help),
                settings.skipSilence, !busy, onSkipSilence)
        }
        SettingsSection(stringResource(R.string.history)) {
            SettingsSwitch(stringResource(R.string.save_history), stringResource(R.string.save_history_help),
                settings.recordHistory, !busy, onRecordHistory)
            TextButton(onClick = { confirmClear = true }, enabled = !busy, modifier = Modifier.padding(horizontal = 8.dp)) {
                Text(stringResource(R.string.clear_history))
            }
        }
        if (showInfo) SettingsInformation()
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.clear_history_title)) },
            text = { Text(stringResource(R.string.clear_history_body)) },
            confirmButton = { TextButton(onClick = { confirmClear = false; onClearHistory() }, enabled = !busy) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun SettingsInformation() {
    SettingsSection(stringResource(R.string.privacy)) {
        Text(stringResource(R.string.privacy_local), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.privacy_network), Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    SettingsSection(stringResource(R.string.about)) {
        Text("PYTE ${BuildConfig.VERSION_NAME}", Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.about_tagline), Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
            Column { content() }
        }
    }
}

@Composable
internal fun SettingsSwitch(title: String, description: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}
