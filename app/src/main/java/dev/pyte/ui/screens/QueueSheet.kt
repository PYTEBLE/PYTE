package dev.pyte.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.tween
import dev.pyte.ui.theme.LocalLivingUi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.pyte.playback.PlayerUiState
import dev.pyte.ui.components.Artwork
import dev.pyte.domain.Track
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlaylistAdd

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(
    state: PlayerUiState,
    onDismiss: () -> Unit,
    onPlay: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onRemove: (Int) -> Unit,
    onAddToPlaylist: ((Track) -> Unit)? = null,
) {
    val motion = LocalLivingUi.current
    val keys = queueAnimationKeys(state)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text("Queue · ${state.queue.size} tracks", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (state.shuffle) {
                Text("Shuffle is on. Moving tracks or choosing Play next switches it off.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp))
            }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 520.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                itemsIndexed(state.queue, key = { index, _ -> keys[index] }) { index, track ->
                    Column(Modifier.animateItem(
                        fadeInSpec = if (motion.animate) tween(motion.duration(180)) else null,
                        placementSpec = if (motion.animate) tween(motion.duration(260)) else null,
                        fadeOutSpec = if (motion.animate) tween(motion.duration(160)) else null,
                    ).fillMaxWidth().padding(vertical = 6.dp)) {
                        Row(Modifier.fillMaxWidth().clickable { onPlay(index) }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Artwork(track.thumbnailUrl, Modifier.size(44.dp), videoId = track.videoId)
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    color = if (index == state.currentIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                                Text(if (index == state.currentIndex) "Now playing · ${track.artist}" else track.artist,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text("${index + 1}", style = MaterialTheme.typography.labelSmall)
                            onAddToPlaylist?.let { add ->
                                IconButton(onClick = { add(track) }) {
                                    Icon(Icons.Default.PlaylistAdd, "${track.title} zur lokalen Playlist hinzufügen")
                                }
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { onMove(index, index - 1) }, enabled = index > 0) { Text("Move up") }
                            TextButton(onClick = { onMove(index, index + 1) }, enabled = index < state.queue.lastIndex) { Text("Move down") }
                            TextButton(onClick = { onRemove(index) }) { Text("Remove") }
                        }
                    }
                }
            }
        }
    }
}

internal fun queueAnimationKeys(state: PlayerUiState): List<String> {
    if (state.queueEntryIds.size == state.queue.size && state.queueEntryIds.distinct().size == state.queue.size)
        return state.queueEntryIds
    val occurrences = mutableMapOf<String, Int>()
    return state.queue.map { track ->
        val occurrence = occurrences.getOrDefault(track.videoId, 0)
        occurrences[track.videoId] = occurrence + 1
        "${track.videoId}:$occurrence"
    }
}
