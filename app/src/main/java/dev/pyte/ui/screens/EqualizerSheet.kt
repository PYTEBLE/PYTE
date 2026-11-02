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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerSheet(onDismiss: () -> Unit, viewModel: EqualizerViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val device by viewModel.device.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    DisposableEffect(viewModel) { onDispose { viewModel.cancelPreview() } }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        EqualizerContent(settings?.equalizer, device, error, viewModel::enabled, viewModel::select,
            viewModel::preview, viewModel::curve, viewModel::save, viewModel::delete, viewModel::retry)
    }
}

@Composable
internal fun EqualizerContent(
    settings: EqualizerSettings?, device: EqualizerState, error: String?,
    onEnabled: (Boolean) -> Unit, onSelect: (String) -> Unit,
    onPreview: (List<EqPoint>) -> Unit, onCurve: (List<EqPoint>) -> Unit,
    onSave: (String, List<EqPoint>) -> Unit, onDelete: (String) -> Unit, onRetry: () -> Unit,
) {
    var saveOpen by rememberSaveable { mutableStateOf(false) }
    var profileName by rememberSaveable { mutableStateOf("") }
    var deleteId by rememberSaveable { mutableStateOf<String?>(null) }
    val stored = settings?.points ?: EqualizerCurves.flat
    var draft by remember(stored, device.frequencies) {
        mutableStateOf(device.frequencies.map { EqPoint(it, EqualizerCurves.gainAt(stored, it).coerceIn(device.minMb, device.maxMb)) })
    }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)
        .padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Equalizer", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Your sound. Your profiles.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = settings?.enabled == true, onCheckedChange = onEnabled,
                enabled = settings != null && device.available,
                modifier = Modifier.semantics { contentDescription = "Enable equalizer" })
        }
        if (settings == null) CircularProgressIndicator()
        if (!device.available) {
            Text(device.message, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onRetry) { Text("Retry audio effect") }
        }
        Text("Presets", style = MaterialTheme.typography.titleSmall)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EqualizerCurves.presets.forEach { profile ->
                FilterChip(selected = settings?.selectedId == profile.id, onClick = { onSelect(profile.id) },
                    enabled = settings != null, label = { Text(profile.name) })
            }
        }
        if (settings?.profiles?.isNotEmpty() == true) {
            Text("Saved profiles", style = MaterialTheme.typography.titleSmall)
            settings.profiles.forEach { profile ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(selected = settings.selectedId == profile.id, onClick = { onSelect(profile.id) },
                        label = { Text(profile.name) }, modifier = Modifier.weight(1f))
                    TextButton(onClick = { profileName = profile.name; saveOpen = true }) { Text("Replace") }
                    TextButton(onClick = { deleteId = profile.id }) { Text("Delete") }
                }
            }
        }
        if (device.available) {
            draft.forEachIndexed { index, point ->
                Column {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(frequencyLabel(point.hz), style = MaterialTheme.typography.labelLarge)
                        Text(String.format(Locale.ROOT, "%+.1f dB", point.gainMb / 100f), color = MaterialTheme.colorScheme.primary)
                    }
                    Slider(value = point.gainMb.toFloat(), valueRange = device.minMb.toFloat()..device.maxMb.toFloat(),
                        enabled = settings?.enabled == true,
                        modifier = Modifier.semantics { contentDescription = "Equalizer ${frequencyLabel(point.hz)}" },
                        onValueChange = { gain ->
                            draft = draft.mapIndexed { i, value -> if (i == index) value.copy(gainMb = gain.toInt()) else value }
                            onPreview(draft)
                        }, onValueChangeFinished = { onCurve(draft) })
                }
            }
        }
        Text("Bands follow your audio device. Profiles adapt to other supported outputs. Boosts reserve volume headroom to reduce distortion; lower the gains if you hear clipping.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { onSelect("neutral") }, enabled = settings != null) { Text("Reset to neutral") }
            Button(onClick = { profileName = ""; saveOpen = true }, enabled = settings != null && device.available) { Text("Save profile") }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
    if (saveOpen) AlertDialog(onDismissRequest = { saveOpen = false }, title = { Text("Save sound profile") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(profileName, { profileName = it.take(48) }, label = { Text("Profile name") }, singleLine = true)
            Text("Saving with an existing name replaces that profile with the current slider values.")
        } }, confirmButton = { TextButton(onClick = { onSave(profileName, draft); saveOpen = false }, enabled = profileName.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = { saveOpen = false }) { Text("Cancel") } })
    deleteId?.let { id -> AlertDialog(onDismissRequest = { deleteId = null }, title = { Text("Delete sound profile?") },
        text = { Text("Only this saved profile is removed. Your current sound curve stays unchanged.") },
        confirmButton = { TextButton(onClick = { onDelete(id); deleteId = null }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { deleteId = null }) { Text("Cancel") } }) }
}

private fun frequencyLabel(hz: Int): String = if (hz >= 1000) String.format(Locale.ROOT, "%.1f kHz", hz / 1000f) else "$hz Hz"
