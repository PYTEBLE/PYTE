package dev.pyte.ui.screens

import dev.pyte.ui.components.TactileIconButton as IconButton
import dev.pyte.ui.components.feedbackClick

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pyte.BuildConfig
import dev.pyte.R
import dev.pyte.data.settings.AppSettings
import dev.pyte.ui.components.ScreenHeading
import dev.pyte.ui.viewmodels.SettingsViewModel

enum class SettingsCategory(val route: String, @StringRes val title: Int, val icon: ImageVector) {
    PLAYBACK("playback", R.string.playback, Icons.Default.PlayArrow),
    SOUND("sound", R.string.sound, Icons.Default.GraphicEq),
    DISCOVER("discover", R.string.discover, Icons.Default.Explore),
    APPEARANCE("appearance", R.string.appearance_title, Icons.Default.Palette),
    BATTERY("battery", R.string.battery, Icons.Default.BatteryChargingFull),
    DOWNLOADS("downloads", R.string.offline_downloads, Icons.Default.Download),
    HISTORY("history", R.string.history, Icons.Default.History),
    DATA("data", R.string.data_title, Icons.Default.Backup),
    PRIVACY("privacy", R.string.privacy_info, Icons.Default.PrivacyTip);

    companion object {
        fun fromRoute(value: String?): SettingsCategory? = entries.firstOrNull { it.route == value }
    }
}

@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    category: SettingsCategory?,
    onOpen: (SettingsCategory) -> Unit,
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val loadFailed by viewModel.loadFailed.collectAsStateWithLifecycle()
    val offlineBytes by viewModel.offlineBytes.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); viewModel.dismissMessage() }
    }
    Box(Modifier.fillMaxSize().padding(contentPadding).statusBarsPadding()) {
        val current = settings
        when {
            loadFailed -> Column(Modifier.fillMaxSize()) {
                SettingsPageBar(stringResource(R.string.settings), onBack)
                ScreenHeading(stringResource(R.string.settings), stringResource(R.string.settings_load_error))
                Text(stringResource(R.string.settings_reopen), Modifier.padding(20.dp))
            }
            current == null -> Column(Modifier.fillMaxSize()) {
                SettingsPageBar(stringResource(R.string.settings), onBack)
                CircularProgressIndicator(Modifier.padding(24.dp))
            }
            category == null -> SettingsOverview(current, offlineBytes, onOpen, onBack)
            else -> SettingsDetailPage(category, current, busy, viewModel, offlineBytes, onBack)
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(horizontal = 20.dp))
    }
}

@Composable
private fun SettingsDetailPage(
    category: SettingsCategory,
    settings: AppSettings,
    busy: Boolean,
    viewModel: SettingsViewModel,
    offlineBytes: Long,
    onBack: () -> Unit,
) {
    when (category) {
        SettingsCategory.PLAYBACK -> PlaybackPage(settings, busy, viewModel::skipSilence, onBack)
        SettingsCategory.SOUND -> SoundPage(onBack)
        SettingsCategory.DISCOVER -> DiscoverPage(settings, busy, viewModel::autoRecommendations, onBack)
        SettingsCategory.APPEARANCE -> AppearancePage(settings, busy, viewModel::dynamicCoverTheme, viewModel::effectLevel,
            viewModel::hapticFeedback, viewModel::uiSounds, onBack)
        SettingsCategory.BATTERY -> BatteryModePage(settings.battery, busy, viewModel::battery, onBack)
        SettingsCategory.DOWNLOADS -> DownloadsPage(settings, busy, offlineBytes, viewModel::downloadLimit,
            viewModel::pauseDownloads, onBack)
        SettingsCategory.HISTORY -> HistoryPage(settings, busy, viewModel::recordHistory, viewModel::clearHistory, onBack)
        SettingsCategory.DATA -> DataPage(busy, viewModel::exportBackup, viewModel::importBackup, onBack)
        SettingsCategory.PRIVACY -> PrivacyPage(onBack)
    }
}

@Composable
private fun SettingsOverview(settings: AppSettings, offlineBytes: Long, onOpen: (SettingsCategory) -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.close))
            }
        }
        ScreenHeading(stringResource(R.string.settings), stringResource(R.string.settings_subtitle))
        Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            CategoryGroup(stringResource(R.string.settings_group_listening), listOf(
                SettingsCategory.PLAYBACK, SettingsCategory.SOUND, SettingsCategory.DISCOVER, SettingsCategory.APPEARANCE,
            ), settings, offlineBytes, onOpen)
            CategoryGroup(stringResource(R.string.settings_group_device), listOf(
                SettingsCategory.BATTERY, SettingsCategory.DOWNLOADS, SettingsCategory.HISTORY,
                SettingsCategory.DATA, SettingsCategory.PRIVACY,
            ), settings, offlineBytes, onOpen)
        }
    }
}

@Composable
private fun CategoryGroup(title: String, categories: List<SettingsCategory>, settings: AppSettings, offlineBytes: Long,
    onOpen: (SettingsCategory) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
            Column {
                categories.forEachIndexed { index, category ->
                    CategoryRow(category, categoryValue(category, settings, offlineBytes)) { onOpen(category) }
                    if (index < categories.lastIndex) {
                        HorizontalDivider(Modifier.padding(start = 76.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryRow(category: SettingsCategory, value: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = feedbackClick(onClick)).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center) {
            Icon(category.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(category.title), style = MaterialTheme.typography.titleMedium)
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun categoryValue(category: SettingsCategory, settings: AppSettings, offlineBytes: Long): String = when (category) {
    SettingsCategory.PLAYBACK -> stringResource(R.string.settings_value_playback, onOff(settings.skipSilence))
    SettingsCategory.SOUND -> stringResource(R.string.settings_value_sound, onOff(settings.equalizer.enabled))
    SettingsCategory.DISCOVER -> stringResource(R.string.settings_value_discover, onOff(settings.autoRecommendations))
    SettingsCategory.APPEARANCE -> stringResource(R.string.settings_value_appearance, settings.effectLevel.label)
    SettingsCategory.BATTERY -> stringResource(R.string.settings_value_battery, onOff(settings.battery.enabled))
    SettingsCategory.DOWNLOADS -> stringResource(R.string.settings_value_downloads,
        stringResource(R.string.size_mb, offlineBytes / (1024 * 1024)), limitLabel(settings.downloadLimitMb))
    SettingsCategory.HISTORY -> stringResource(R.string.settings_value_history, onOff(settings.recordHistory))
    SettingsCategory.DATA -> stringResource(R.string.settings_value_data)
    SettingsCategory.PRIVACY -> stringResource(R.string.settings_value_privacy, BuildConfig.VERSION_NAME)
}

@Composable
private fun onOff(enabled: Boolean) = stringResource(if (enabled) R.string.on else R.string.off)

@Composable
private fun limitLabel(mb: Long) =
    if (mb >= 1024 && mb % 1024 == 0L) stringResource(R.string.size_gb, mb / 1024) else stringResource(R.string.size_mb, mb)
