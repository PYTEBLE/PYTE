package dev.pyte.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import android.net.Uri
import dev.pyte.BuildConfig
import dev.pyte.R
import dev.pyte.data.settings.AppSettings
import dev.pyte.data.settings.EffectLevel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val downloadLimitSteps = listOf(512L, 1024L, 2048L, 4096L, 8192L, 16384L)

@Composable
internal fun PlaybackPage(settings: AppSettings, busy: Boolean, onSkipSilence: (Boolean) -> Unit, onBack: () -> Unit) {
    SettingsDetail(stringResource(R.string.playback), onBack) {
        SettingsSection {
            SettingsSwitch(stringResource(R.string.skip_silence), stringResource(R.string.skip_silence_help),
                settings.skipSilence, !busy, onSkipSilence)
        }
    }
}

@Composable
internal fun SoundPage(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        SettingsPageBar(stringResource(R.string.sound), onBack)
        EqualizerPanel(Modifier.weight(1f))
    }
}

@Composable
internal fun DiscoverPage(settings: AppSettings, busy: Boolean, onRecommendations: (Boolean) -> Unit, onBack: () -> Unit) {
    SettingsDetail(stringResource(R.string.discover), onBack) {
        SettingsSection {
            SettingsSwitch(stringResource(R.string.recommendations), stringResource(R.string.recommendations_help),
                settings.autoRecommendations, !busy, onRecommendations)
        }
    }
}

@Composable
internal fun AppearancePage(
    settings: AppSettings,
    busy: Boolean,
    onCoverTheme: (Boolean) -> Unit,
    onEffectLevel: (EffectLevel) -> Unit,
    onHaptics: (Boolean) -> Unit,
    onUiSounds: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    SettingsDetail(stringResource(R.string.appearance_title), onBack) {
        SettingsSection {
            SettingsSwitch(stringResource(R.string.cover_theme), stringResource(R.string.cover_theme_help),
                settings.dynamicCoverTheme, !busy, onCoverTheme)
        }
        SettingsSection(stringResource(R.string.effects)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                EffectLevelPicker(settings.effectLevel, !busy, onEffectLevel)
                Text(stringResource(R.string.effects_help), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        SettingsSection {
            SettingsSwitch(stringResource(R.string.haptics), stringResource(R.string.haptics_help),
                settings.hapticFeedback, !busy, onHaptics)
            SettingsSwitch(stringResource(R.string.ui_sounds), stringResource(R.string.ui_sounds_help),
                settings.uiSounds, !busy, onUiSounds)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EffectLevelPicker(selected: EffectLevel, enabled: Boolean, onSelect: (EffectLevel) -> Unit) {
    val levels = EffectLevel.entries
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        levels.forEachIndexed { index, level ->
            SegmentedButton(
                selected = selected == level,
                onClick = { onSelect(level) },
                enabled = enabled,
                shape = SegmentedButtonDefaults.itemShape(index, levels.size),
                label = { Text(level.label) },
            )
        }
    }
}

@Composable
internal fun DownloadsPage(settings: AppSettings, busy: Boolean, usedBytes: Long, onLimit: (Long) -> Unit,
    onPause: (Boolean) -> Unit, onBack: () -> Unit) {
    val usedMb = usedBytes / (1024 * 1024)
    val limit = settings.downloadLimitMb
    val index = downloadLimitSteps.indexOfFirst { it >= limit }.let { if (it < 0) downloadLimitSteps.lastIndex else it }
    SettingsDetail(stringResource(R.string.offline_downloads), onBack) {
        SettingsSection {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.storage_used, usedMb, limit), style = MaterialTheme.typography.titleMedium)
                LinearProgressIndicator(progress = { (usedMb.toFloat() / limit).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.downloads_help), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        SettingsSection(stringResource(R.string.download_limit)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                IconButton(onClick = { onLimit(downloadLimitSteps[index - 1]) }, enabled = !busy && index > 0) {
                    Icon(Icons.Default.Remove, contentDescription = stringResource(R.string.download_limit_decrease))
                }
                Text(sizeLabel(limit), style = MaterialTheme.typography.headlineSmall)
                IconButton(onClick = { onLimit(downloadLimitSteps[index + 1]) }, enabled = !busy && index < downloadLimitSteps.lastIndex) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.download_limit_increase))
                }
            }
        }
        SettingsSection {
            SettingsSwitch(stringResource(R.string.pause_downloads), stringResource(R.string.pause_downloads_help),
                settings.downloadsPaused, !busy, onPause)
        }
        Text(stringResource(R.string.cache_help), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun sizeLabel(mb: Long): String =
    if (mb >= 1024 && mb % 1024 == 0L) stringResource(R.string.size_gb, mb / 1024) else stringResource(R.string.size_mb, mb)

@Composable
internal fun HistoryPage(settings: AppSettings, busy: Boolean, onRecordHistory: (Boolean) -> Unit,
    onClearHistory: () -> Unit, onBack: () -> Unit) {
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    SettingsDetail(stringResource(R.string.history), onBack) {
        SettingsSection {
            SettingsSwitch(stringResource(R.string.save_history), stringResource(R.string.save_history_help),
                settings.recordHistory, !busy, onRecordHistory)
        }
        SettingsSection {
            TextButton(onClick = { confirmClear = true }, enabled = !busy, modifier = Modifier.fillMaxWidth().padding(8.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                Text(stringResource(R.string.clear_history))
            }
        }
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
internal fun DataPage(busy: Boolean, onExport: (Uri) -> Unit, onImport: (Uri) -> Unit, onBack: () -> Unit) {
    var pendingImport by rememberSaveable { mutableStateOf<Uri?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(onExport)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> pendingImport = uri }
    SettingsDetail(stringResource(R.string.data_title), onBack) {
        Text(stringResource(R.string.data_intro), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        SettingsSection(stringResource(R.string.data_export_title)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.data_export_help), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = { exportLauncher.launch(backupFileName()) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.data_export_action))
                }
            }
        }
        SettingsSection(stringResource(R.string.data_import_title)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.data_import_help), color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) },
                    enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.data_import_action))
                }
            }
        }
        Text(stringResource(R.string.data_not_included), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    pendingImport?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text(stringResource(R.string.data_import_confirm_title)) },
            text = { Text(stringResource(R.string.data_import_confirm_body)) },
            confirmButton = { TextButton(onClick = { pendingImport = null; onImport(uri) }, enabled = !busy) { Text(stringResource(R.string.data_import_confirm_action)) } },
            dismissButton = { TextButton(onClick = { pendingImport = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

private fun backupFileName() = "pyte-backup-" + SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date()) + ".json"

@Composable
internal fun PrivacyPage(onBack: () -> Unit) {
    SettingsDetail(stringResource(R.string.privacy_info), onBack) {
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
}
