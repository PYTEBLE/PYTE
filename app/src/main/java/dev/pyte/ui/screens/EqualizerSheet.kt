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
                Text("SOUND STUDIO", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close equalizer") }
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
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)
        .padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Equalizer", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("15 Bänder. Dein Klang.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = settings?.enabled == true, onCheckedChange = onEnabled,
                enabled = settings != null && device.available,
                modifier = Modifier.semantics { contentDescription = "Enable equalizer" })
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Schutzbegrenzer", style = MaterialTheme.typography.titleSmall)
                Text("Stereo-Pegelschutz bei kräftigen Anhebungen", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(settings?.limiter != false, onCheckedChange = onLimiter, enabled = settings != null,
                modifier = Modifier.semantics { contentDescription = "Schutzbegrenzer" })
        }
        if (settings == null) CircularProgressIndicator()
        if (!device.available) {
            Text(device.message, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onRetry) { Text("Retry audio effect") }
        }
        Text("Klangprofile", style = MaterialTheme.typography.titleSmall)
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
                Text("Headroom ${String.format(Locale.ROOT, "%.1f dB", 20 * kotlin.math.log10(device.headroom.coerceAtLeast(.001f)))}",
                    color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
            }
            Text("Kurvenpunkte ziehen oder die Bandregler nutzen · 0,5-dB-Schritte", style = MaterialTheme.typography.bodySmall,
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
        Text("Echte 15-Band-Verarbeitung in PYTE, unabhängig vom Geräte-Equalizer. Bestehende Profile werden übernommen. Hohe Bänder sind bei niedriger Abtastrate nicht verfügbar. Headroom reduziert Übersteuerung; der Schutzbegrenzer fängt verbleibende Spitzen ab.",
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
