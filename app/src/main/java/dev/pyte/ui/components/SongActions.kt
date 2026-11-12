package dev.pyte.ui.components

import dev.pyte.ui.components.TactileIconButton as IconButton

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.pyte.R
import dev.pyte.domain.Track

/** One entry point for every song surface. The selected Track is a snapshot, not "current song". */
data class SongActions(val open: (Track) -> Unit, val save: (Track) -> Unit,
    val openFromPlaylist: (Track, String) -> Unit = { track, _ -> open(track) })
val LocalSongActions = staticCompositionLocalOf<SongActions?> { null }
data class SongFeedback(val message: String, val undo: (() -> Unit)?, val error: Boolean = false)
val LocalSongFeedback = staticCompositionLocalOf<SongFeedback?> { null }

/** Modal sheets own a separate window: show the save result there as well as in the main snackbar. */
@Composable
fun SongFeedbackBanner() {
    val feedback = LocalSongFeedback.current ?: return
    Surface(color = if (feedback.error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (feedback.error) Icons.Default.ErrorOutline else Icons.Default.CheckCircle, null,
                tint = if (feedback.error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            Text(feedback.message, Modifier.weight(1f).padding(horizontal = 10.dp), style = MaterialTheme.typography.bodySmall)
            feedback.undo?.let { undo -> TextButton(onClick = feedbackClick(undo)) { Text(stringResource(R.string.undo)) } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongActionsSheet(track: Track, favorite: Boolean, onDismiss: () -> Unit,
    onSave: () -> Unit, onNext: () -> Unit, onQueue: () -> Unit,
    onFavorite: () -> Unit, onDiscover: () -> Unit, onRemoveFromPlaylist: (() -> Unit)? = null) {
    ModalBottomSheet(onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surface) {
        SheetMotion {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Artwork(track.thumbnailUrl, Modifier.size(64.dp), videoId = track.videoId)
                Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                    Text(track.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(track.artist, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, stringResource(R.string.close)) }
            }
            BrandButton(stringResource(R.string.add_to_playlist), onSave, Modifier.fillMaxWidth(), icon = Icons.Default.PlaylistAdd)
            ActionTile(stringResource(R.string.play_next), Icons.Default.SkipNext, onNext)
            ActionTile(stringResource(R.string.add_to_queue), Icons.Default.QueueMusic, onQueue)
            ActionTile(stringResource(if (favorite) R.string.favorite_remove else R.string.favorite_add),
                if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, onFavorite)
            ActionTile(stringResource(R.string.discover_similar), Icons.Default.AutoAwesome, onDiscover)
            onRemoveFromPlaylist?.let { ActionTile(stringResource(R.string.remove_from_playlist), Icons.Default.PlaylistRemove, it) }
            Text(stringResource(R.string.offline_playlist_hint), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        }
    }
}

@Composable
private fun ActionTile(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Surface(onClick = feedbackClick(onClick), shape = MaterialTheme.shapes.large,
        interactionSource = interaction,
        color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth().pressMotion(interaction)
            .clip(MaterialTheme.shapes.large).touchBloom(interaction)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.titleSmall)
        }
    }
}
