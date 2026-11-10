package dev.pyte.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import dev.pyte.R
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
    recentId: String? = null,
) {
    var title by rememberSaveable(track.videoId) { mutableStateOf("") }
    var query by rememberSaveable(track.videoId) { mutableStateOf("") }
    val visible = playlistChoices(playlists, query, recentId)
    AlertDialog(onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(stringResource(R.string.add_to_playlist)) },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(track.title, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                Text(stringResource(R.string.playlist_privacy),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (saving) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (playlists.isNotEmpty()) {
                    OutlinedTextField(query, { query = it }, singleLine = true, enabled = !saving,
                        label = { Text(stringResource(R.string.playlist_search)) }, modifier = Modifier.fillMaxWidth())
                    if (visible.isEmpty()) Text(stringResource(R.string.playlist_search_empty))
                    LazyColumn(Modifier.heightIn(max = 220.dp)) {
                        items(visible, key = { it.id }) { playlist ->
                            TextButton(onClick = { onChoose(playlist.id) }, enabled = !saving, modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                    Text(playlist.title)
                                    if (playlist.id == recentId) Text(stringResource(R.string.recent_playlist), color = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.labelSmall)
                                    Text(stringResource(R.string.playlist_count, pluralStringResource(R.plurals.songs_count, playlist.trackCount, playlist.trackCount),
                                        stringResource(if (playlist.isLocal) R.string.local_playlist else R.string.local_copy)),
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
                if (playlists.isEmpty()) Text(stringResource(R.string.playlist_empty))
                OutlinedTextField(value = title, onValueChange = { title = it.take(100) }, singleLine = true,
                    enabled = !saving, label = { Text(stringResource(R.string.new_playlist_name)) }, modifier = Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(title.trim()) }, enabled = title.isNotBlank() && !saving) { Text(stringResource(R.string.create_and_add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text(stringResource(R.string.cancel)) } })
}

internal fun playlistChoices(playlists: List<PlaylistSummary>, query: String, recentId: String?): List<PlaylistSummary> =
    playlists.filter { it.title.contains(query.trim(), ignoreCase = true) }
        .sortedWith(compareBy<PlaylistSummary> { it.id != recentId }.thenBy { it.title.lowercase(java.util.Locale.GERMAN) })
