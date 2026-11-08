package dev.pyte.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.pyte.playback.MixUiState

@Composable
fun MixPanel(
    state: MixUiState,
    isPreview: Boolean,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onRestore: () -> Unit,
    modifier: Modifier = Modifier,
    onSaveSong: (() -> Unit)? = null,
) {
    var optionsOpen by remember { mutableStateOf(false) }
    Surface(modifier, shape = RoundedCornerShape(26.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(8.dp)) {
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 8.dp).semantics { liveRegion = LiveRegionMode.Polite })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (state.title == null || state.loading) Button(onClick = onStart, enabled = !state.loading && !isPreview,
                    modifier = Modifier.weight(1f).heightIn(min = 56.dp), shape = RoundedCornerShape(18.dp)) {
                    if (state.loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Default.AutoAwesome, null, Modifier.size(18.dp))
                    Column(Modifier.padding(start = 10.dp)) {
                        Text(if (state.loading) "Lädt…" else "Mix starten", fontWeight = FontWeight.SemiBold)
                        Text("Neue Songs · ganzer Katalog", style = MaterialTheme.typography.labelSmall)
                    }
                }
                else if (onSaveSong != null) Button(onClick = onSaveSong,
                    modifier = Modifier.weight(1f).heightIn(min = 56.dp), shape = RoundedCornerShape(18.dp)) {
                    Icon(Icons.Default.PlaylistAdd, null, Modifier.size(20.dp))
                    Text("  Song zur Playlist")
                }
                else Text("Dein Mix", Modifier.weight(1f).padding(12.dp), style = MaterialTheme.typography.titleSmall)
                if (state.loading) TextButton(onClick = onCancel) { Text("Abbrechen") }
                if (state.title != null && !state.loading) Box {
                    IconButton(onClick = { optionsOpen = true }) { Icon(Icons.Default.MoreVert, "Mix-Optionen") }
                    DropdownMenu(optionsOpen, onDismissRequest = { optionsOpen = false }) {
                        DropdownMenuItem(text = { Text("Neuen Mix starten") }, enabled = !isPreview,
                            onClick = { optionsOpen = false; onStart() })
                        DropdownMenuItem(text = { Text("Vorherige Warteschlange wiederherstellen") },
                            onClick = { optionsOpen = false; onRestore() })
                    }
                }
            }
            if (isPreview) Text("Mix nach dem Beenden der Vorschau starten.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
