package dev.pyte.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pyte.playback.EqPoint
import dev.pyte.playback.EqualizerCurves
import dev.pyte.playback.EqualizerSettings
import dev.pyte.playback.EqualizerState
import dev.pyte.ui.viewmodels.EqualizerViewModel
import java.util.Locale
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import dev.pyte.ui.components.EqualizerGraph
import dev.pyte.ui.components.BandFader
import dev.pyte.ui.theme.LocalLivingUi
import dev.pyte.ui.theme.rememberLivingUiPolicy
import dev.pyte.data.settings.AppSettings
import dev.pyte.R
import androidx.compose.ui.res.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerSheet(onDismiss: () -> Unit, viewModel: EqualizerViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val device by viewModel.device.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val spectrum by viewModel.spectrum.collectAsStateWithLifecycle()
    val playing by viewModel.playing.collectAsStateWithLifecycle()
    DisposableEffect(viewModel) { onDispose { viewModel.analyzing(false); viewModel.cancelPreview() } }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val motion = rememberLivingUiPolicy(settings ?: AppSettings())
        LaunchedEffect(motion.foreground, motion.ambient, playing) { viewModel.analyzing(motion.foreground && motion.ambient && playing) }
        CompositionLocalProvider(LocalLivingUi provides motion) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.sound_studio), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, stringResource(R.string.close)) }
            }
        EqualizerContent(settings?.equalizer, device, error, viewModel::enabled, viewModel::select,
            viewModel::preview, viewModel::curve, viewModel::save, viewModel::delete, viewModel::retry,
            spectrum, viewModel::limiter)
        }
        }
        }
    }
}

@Composable
fun EqualizerPanel(modifier: Modifier = Modifier, viewModel: EqualizerViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val device by viewModel.device.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val spectrum by viewModel.spectrum.collectAsStateWithLifecycle()
    val playing by viewModel.playing.collectAsStateWithLifecycle()
    val motion = LocalLivingUi.current
    DisposableEffect(viewModel) { onDispose { viewModel.analyzing(false); viewModel.cancelPreview() } }
    LaunchedEffect(motion.foreground, motion.ambient, playing) { viewModel.analyzing(motion.foreground && motion.ambient && playing) }
    Box(modifier) {
        EqualizerContent(settings?.equalizer, device, error, viewModel::enabled, viewModel::select,
            viewModel::preview, viewModel::curve, viewModel::save, viewModel::delete, viewModel::retry,
            spectrum, viewModel::limiter)
    }
}

@Composable
internal fun EqualizerContent(
    settings: EqualizerSettings?, device: EqualizerState, error: String?,
    onEnabled: (Boolean) -> Unit, onSelect: (String) -> Unit,
    onPreview: (List<EqPoint>) -> Unit, onCurve: (List<EqPoint>) -> Unit,
    onSave: (String, List<EqPoint>) -> Unit, onDelete: (String) -> Unit, onRetry: () -> Unit,
    spectrum: List<Float> = emptyList(), onLimiter: (Boolean) -> Unit = {},
) {
    var saveOpen by rememberSaveable { mutableStateOf(false) }
    var profileName by rememberSaveable { mutableStateOf("") }
    var deleteId by rememberSaveable { mutableStateOf<String?>(null) }
    val stored = settings?.points ?: EqualizerCurves.flat
    var draft by remember(stored) {
        mutableStateOf(EqualizerCurves.frequencies.map { EqPoint(it, EqualizerCurves.gainAt(stored, it)) })
    }
    val enableLabel = stringResource(R.string.eq_enable)
    val limiterLabel = stringResource(R.string.eq_limiter)
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)
        .padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.eq_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.eq_subtitle), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = settings?.enabled == true, onCheckedChange = onEnabled,
                enabled = settings != null && device.available,
                modifier = Modifier.semantics { contentDescription = enableLabel })
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(limiterLabel, style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.eq_limiter_help), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(settings?.limiter != false, onCheckedChange = onLimiter, enabled = settings != null,
                modifier = Modifier.semantics { contentDescription = limiterLabel })
        }
        if (settings == null) CircularProgressIndicator()
        if (!device.available) {
            Text(device.message, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
        }
        Text(stringResource(R.string.eq_presets), style = MaterialTheme.typography.titleSmall)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EqualizerCurves.presets.forEach { profile ->
                FilterChip(selected = settings?.selectedId == profile.id, onClick = { onSelect(profile.id) },
                    enabled = settings != null, label = { Text(profile.name) })
            }
        }
        if (device.available) {
            EqualizerGraph(draft.filter { it.hz in device.frequencies }, spectrum,
                enabled = settings?.enabled == true, onPreview = { changed ->
                    draft = draft.map { p -> changed.firstOrNull { it.hz == p.hz } ?: p }; onPreview(draft)
                }, onCommit = { changed ->
                    draft = draft.map { p -> changed.firstOrNull { it.hz == p.hz } ?: p }; onCurve(draft)
                })
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("25 Hz — 16 kHz", style = MaterialTheme.typography.labelMedium)
                Text(stringResource(R.string.eq_headroom, String.format(Locale.GERMAN, "%.1f dB", 20 * kotlin.math.log10(device.headroom.coerceAtLeast(.001f)))),
                    color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
            }
            Text(stringResource(R.string.eq_edit_hint), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                draft.forEachIndexed { index, point ->
                    BandFader(point, settings?.enabled == true && point.hz in device.frequencies,
                        onPreview = { gain ->
                            draft = draft.mapIndexed { i, value -> if (i == index) value.copy(gainMb = gain) else value }
                            onPreview(draft)
                        }, onCommit = { onCurve(draft) })
                }
            }
        }
        if (settings?.profiles?.isNotEmpty() == true) {
            Text(stringResource(R.string.eq_saved_profiles), style = MaterialTheme.typography.titleSmall)
            settings.profiles.forEach { profile ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(selected = settings.selectedId == profile.id, onClick = { onSelect(profile.id) },
                        label = { Text(profile.name) }, modifier = Modifier.weight(1f))
                    TextButton(onClick = { profileName = profile.name; saveOpen = true }) { Text(stringResource(R.string.eq_replace)) }
                    TextButton(onClick = { deleteId = profile.id }) { Text(stringResource(R.string.delete)) }
                }
            }
        }
        Text(stringResource(R.string.eq_help),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { onSelect("neutral") }, enabled = settings != null) { Text(stringResource(R.string.eq_reset)) }
            Button(onClick = { profileName = ""; saveOpen = true }, enabled = settings != null && device.available) { Text(stringResource(R.string.eq_save_profile)) }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
    if (saveOpen) AlertDialog(onDismissRequest = { saveOpen = false }, title = { Text(stringResource(R.string.eq_save_profile)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(profileName, { profileName = it.take(48) }, label = { Text(stringResource(R.string.eq_profile_name)) }, singleLine = true)
            Text(stringResource(R.string.eq_replace_hint))
        } }, confirmButton = { TextButton(onClick = { onSave(profileName, draft); saveOpen = false }, enabled = profileName.isNotBlank()) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = { saveOpen = false }) { Text(stringResource(R.string.cancel)) } })
    deleteId?.let { id -> AlertDialog(onDismissRequest = { deleteId = null }, title = { Text(stringResource(R.string.eq_delete_title)) },
        text = { Text(stringResource(R.string.eq_delete_body)) },
        confirmButton = { TextButton(onClick = { onDelete(id); deleteId = null }) { Text(stringResource(R.string.delete)) } },
        dismissButton = { TextButton(onClick = { deleteId = null }) { Text(stringResource(R.string.cancel)) } }) }
}

private fun frequencyLabel(hz: Int): String = if (hz >= 1000) String.format(Locale.ROOT, "%.1f kHz", hz / 1000f) else "$hz Hz"
