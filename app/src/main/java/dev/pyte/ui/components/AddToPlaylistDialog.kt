package dev.pyte.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.pyte.domain.PlaylistSummary
import dev.pyte.domain.Track

@Composable
fun AddToPlaylistDialog(
    track: Track,
    playlists: List<PlaylistSummary>,
    saving: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onChoose: (String) -> Unit,
    onCreate: (String) -> Unit,
) {
    var title by rememberSaveable(track.videoId) { mutableStateOf("") }
    AlertDialog(onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Add to local playlist") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(track.title, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                Text("Only this app changes. Nothing is added to your YouTube account.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (saving) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (playlists.isNotEmpty()) {
                    LazyColumn(Modifier.heightIn(max = 220.dp)) {
                        items(playlists, key = { it.id }) { playlist ->
                            TextButton(onClick = { onChoose(playlist.id) }, enabled = !saving, modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                    Text(playlist.title)
                                    Text("${playlist.trackCount} tracks · ${if (playlist.isLocal) "Local playlist" else "Local copy"}",
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
                OutlinedTextField(value = title, onValueChange = { title = it.take(100) }, singleLine = true,
                    enabled = !saving, label = { Text("New local playlist name") }, modifier = Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(title.trim()) }, enabled = title.isNotBlank() && !saving) { Text("Create & add") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancel") } })
}
