package dev.pyte.ui.components

import dev.pyte.ui.components.TactileIconButton as IconButton

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.pyte.R
import dev.pyte.domain.PlaylistSummary
import dev.pyte.domain.Track
import dev.pyte.ui.theme.LocalLivingUi

private enum class AddStep { CHOOSE, CREATE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistSheet(
    track: Track,
    playlists: List<PlaylistSummary>,
    saving: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onChoose: (String) -> Unit,
    onCreate: (String) -> Unit,
    recentId: String? = null,
) {
    var step by rememberSaveable(track.videoId) { mutableStateOf(AddStep.CHOOSE) }
    var title by rememberSaveable(track.videoId) { mutableStateOf("") }
    var query by rememberSaveable(track.videoId) { mutableStateOf("") }
    val motion = LocalLivingUi.current
    ModalBottomSheet(
        onDismissRequest = { if (!saving) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        BackHandler(enabled = step == AddStep.CREATE && !saving) { step = AddStep.CHOOSE }
        SheetMotion {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.add_to_playlist), style = MaterialTheme.typography.titleLarge)
                SongHeader(track)
                if (saving) LinearProgressIndicator(Modifier.fillMaxWidth())
                AnimatedContent(
                    targetState = step,
                    label = "addToPlaylistStep",
                    transitionSpec = {
                        (fadeIn(tween(motion.duration(200))) +
                            slideInHorizontally(tween(motion.duration(260))) { if (motion.animate) it / 8 else 0 }) togetherWith
                            fadeOut(tween(motion.duration(120)))
                    },
                ) { current ->
                    when (current) {
                        AddStep.CHOOSE -> ChooseStep(
                            playlists = playlists,
                            query = query,
                            onQuery = { query = it },
                            saving = saving,
                            recentId = recentId,
                            onCreateNew = { step = AddStep.CREATE },
                            onChoose = onChoose,
                            onCancel = onDismiss,
                        )
                        AddStep.CREATE -> CreateStep(
                            title = title,
                            onTitle = { title = it },
                            saving = saving,
                            onBack = { step = AddStep.CHOOSE },
                            onCreate = { onCreate(title.trim()) },
                        )
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }
}

@Composable
private fun SongHeader(track: Track) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Artwork(track.thumbnailUrl, Modifier.size(56.dp), videoId = track.videoId)
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(track.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.artist, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ChooseStep(
    playlists: List<PlaylistSummary>,
    query: String,
    onQuery: (String) -> Unit,
    saving: Boolean,
    recentId: String?,
    onCreateNew: () -> Unit,
    onChoose: (String) -> Unit,
    onCancel: () -> Unit,
) {
    val visible = playlistChoices(playlists, query, recentId)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CreateRow(enabled = !saving, onClick = onCreateNew)
        if (playlists.size > 5) {
            OutlinedTextField(query, onQuery, singleLine = true, enabled = !saving,
                label = { Text(stringResource(R.string.playlist_search)) }, modifier = Modifier.fillMaxWidth())
        }
        if (playlists.isEmpty()) {
            Text(stringResource(R.string.playlist_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else if (visible.isEmpty()) {
            Text(stringResource(R.string.playlist_search_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LazyColumn(Modifier.heightIn(max = 340.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(visible, key = { it.id }) { playlist ->
                PlaylistChoice(playlist, playlist.id == recentId, enabled = !saving) { onChoose(playlist.id) }
            }
        }
        Text(stringResource(R.string.playlist_privacy), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = feedbackClick(onCancel), enabled = !saving, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.cancel)) }
    }
}

@Composable
private fun CreateRow(enabled: Boolean, onClick: () -> Unit) {
    Surface(onClick = feedbackClick(onClick), enabled = enabled, shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
            }
            Text(stringResource(R.string.playlist_create_tile), style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
private fun PlaylistChoice(playlist: PlaylistSummary, recent: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Surface(onClick = feedbackClick(onClick), enabled = enabled, shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Artwork(playlist.thumbnailUrl, Modifier.size(52.dp), shape = RoundedCornerShape(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(playlist.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(stringResource(R.string.playlist_count,
                    pluralStringResource(R.plurals.songs_count, playlist.trackCount, playlist.trackCount),
                    stringResource(if (playlist.isLocal) R.string.local_playlist else R.string.local_copy)),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (recent) {
                Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primary.copy(alpha = .14f)) {
                    Text(stringResource(R.string.recent_playlist), Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun CreateStep(title: String, onTitle: (String) -> Unit, saving: Boolean, onBack: () -> Unit, onCreate: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, enabled = !saving) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
            }
            Text(stringResource(R.string.playlist_new_title), style = MaterialTheme.typography.titleMedium)
        }
        OutlinedTextField(value = title, onValueChange = { onTitle(it.take(100)) }, singleLine = true, enabled = !saving,
            label = { Text(stringResource(R.string.new_playlist_name)) }, modifier = Modifier.fillMaxWidth())
        Button(onClick = feedbackClick(onCreate), enabled = title.isNotBlank() && !saving, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.create_and_add))
        }
        Text(stringResource(R.string.playlist_privacy), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

internal fun playlistChoices(playlists: List<PlaylistSummary>, query: String, recentId: String?): List<PlaylistSummary> =
    playlists.filter { it.title.contains(query.trim(), ignoreCase = true) }
        .sortedWith(compareBy<PlaylistSummary> { it.id != recentId }.thenBy { it.title.lowercase(java.util.Locale.GERMAN) })
