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

@Composable
fun SettingsScreen(contentPadding: PaddingValues, viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val loadFailed by viewModel.loadFailed.collectAsStateWithLifecycle()
    val offlineBytes by viewModel.offlineBytes.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var equalizerOpen by rememberSaveable { mutableStateOf(false) }
    if (equalizerOpen) EqualizerSheet(onDismiss = { equalizerOpen = false })
    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); viewModel.dismissMessage() }
    }
    Box(Modifier.fillMaxSize().padding(contentPadding).statusBarsPadding()) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            if (loadFailed) {
                ScreenHeading("Settings", "Could not load settings")
                Text("Please close and reopen the app to try again. No settings were changed.", Modifier.padding(20.dp))
            } else {
                SettingsContent(settings, busy, viewModel::skipSilence, viewModel::recordHistory, viewModel::clearHistory)
                settings?.let { value ->
                    Column(Modifier.padding(20.dp)) {
                        SettingsSection("Sound") {
                            TextButton(onClick = { equalizerOpen = true }, modifier = Modifier.padding(8.dp)) {
                                Text("Equalizer & sound profiles")
                            }
                        }
                        SettingsSection("Appearance") {
                            SettingsSwitch("Dynamic cover theme", "Let your entire app follow the current cover. Without artwork, PYTE stays violet.",
                                value.dynamicCoverTheme, !busy, viewModel::dynamicCoverTheme)
                            Text("Effekte", Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.titleSmall)
                            Column(Modifier.padding(horizontal = 16.dp)) {
                                EffectLevel.entries.forEach { level ->
                                    FilterChip(selected = value.effectLevel == level, enabled = !busy,
                                        onClick = { viewModel.effectLevel(level) }, label = { Text(level.label) })
                                }
                            }
                            Text("System animation settings and battery saver take priority. Decorative effects stop in the background.",
                                Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall)
                            SettingsSwitch("Haptic feedback", "Gentle feedback when favoriting a song. Respects system settings.",
                                value.hapticFeedback, !busy, viewModel::hapticFeedback)
                        }
                        SettingsSection("Offline downloads") {
                            Text("${offlineBytes / (1024 * 1024)} MB used · ${value.downloadLimitMb} MB limit",
                                Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
                            Text("Only playlists you mark for offline use are downloaded, on Wi-Fi only. Existing downloads are never automatically evicted to make room.",
                                Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Separate temporary caches: audio up to 1.5 GB, artwork up to 96 MB, lyrics up to 8 MB.",
                                Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            SettingsSwitch("Pause downloads", "Playback of completed downloads stays available.",
                                value.downloadsPaused, !busy, viewModel::pauseDownloads)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                TextButton(onClick = { viewModel.downloadLimit((value.downloadLimitMb / 2).coerceAtLeast(512)) }, enabled = !busy) { Text("Lower limit") }
                                TextButton(onClick = { viewModel.downloadLimit((value.downloadLimitMb * 2).coerceAtMost(16384)) }, enabled = !busy) { Text("Raise limit") }
                            }
                        }
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
) {
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    ScreenHeading("Settings", "Make this space your own")
    if (settings == null) {
        CircularProgressIndicator(Modifier.padding(24.dp))
        return
    }
    Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SettingsSection("Playback") {
            SettingsSwitch("Skip silence", "Skip quiet gaps in audio. Applies to current playback, too.",
                settings.skipSilence, !busy, onSkipSilence)
        }
        SettingsSection("Listening history") {
            SettingsSwitch("Save listening history", "Show your recently played tracks on this device. Turning this off keeps existing history.",
                settings.recordHistory, !busy, onRecordHistory)
            TextButton(onClick = { confirmClear = true }, enabled = !busy, modifier = Modifier.padding(horizontal = 8.dp)) {
                Text("Clear listening history")
            }
        }
        SettingsSection("Your privacy") {
            Text("Your playlists, favorites, history and settings stay on this device. No Google login. Android cloud backup and device transfer are disabled.",
                Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Online searches, streaming and playlist imports connect to YouTube; artwork may load from its image servers. Lyrics requests send song title, artist and duration to LRCLIB. Available lyrics are cached locally. Only completed playlist downloads guarantee offline playback; the streaming cache does not.",
                Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SettingsSection("About PYTE") {
            Text("PYTE ${BuildConfig.VERSION_NAME}", Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
            Text("Local-first · Android · Violet by design", Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear listening history?") },
            text = { Text("This removes your recently played list. Your playlists, favorites and current queue stay untouched. This cannot be undone.") },
            confirmButton = { TextButton(onClick = { confirmClear = false; onClearHistory() }, enabled = !busy) { Text("Clear history") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
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
private fun SettingsSwitch(title: String, description: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}
