package dev.pyte.designreview

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.pyte.R
import dev.pyte.ui.components.SheetMotion
import dev.pyte.ui.theme.ListeningComponents as Components
import dev.pyte.ui.theme.ListeningSpace as Space

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
internal fun ReviewActionsSheet(song: ReviewSong, favorite: Boolean, onDismiss: () -> Unit, onSave: () -> Unit,
    onNext: () -> Unit, onQueue: () -> Unit, onFavorite: () -> Unit, onDiscover: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, shape = Components.sheetShape,
        modifier = Modifier.semantics { testTagsAsResourceId = true },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.surface) {
        SheetMotion {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Space.content).padding(bottom = Space.section),
                verticalArrangement = Arrangement.spacedBy(Space.item)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.review_song_actions), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary)
                    ReviewIcon(Icons.Default.Close, stringResource(R.string.close), onDismiss)
                }
                ReviewSongIdentity(song)
                ReviewPrimary(stringResource(R.string.add_to_playlist), Icons.AutoMirrored.Filled.PlaylistAdd, onSave,
                    Modifier.fillMaxWidth().testTag("review-action-save"))
                ReviewAction(stringResource(R.string.play_next), Icons.Default.SkipNext, onNext)
                ReviewAction(stringResource(R.string.add_to_queue), Icons.AutoMirrored.Filled.QueueMusic, onQueue)
                ReviewAction(stringResource(if (favorite) R.string.favorite_remove else R.string.favorite_add),
                    if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, onFavorite)
                ReviewAction(stringResource(R.string.discover_similar), Icons.Default.AutoAwesome, onDiscover)
                ReviewNotice(stringResource(R.string.review_intro))
            }
        }
    }
}

@Composable
private fun ReviewSongIdentity(song: ReviewSong) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.item)) {
        ReviewCover(song.id, song.cover, Modifier.size(64.dp), lettering = false)
        Column(Modifier.weight(1f)) {
            Text(song.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            ReviewNotice(song.artist)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
internal fun ReviewPicker(song: ReviewSong, playlists: List<ReviewPlaylist>, memberships: Set<String>, onDismiss: () -> Unit,
    onBack: (() -> Unit)?, onChoose: (ReviewPlaylist) -> Unit, onCreate: () -> Unit) {
    var query by rememberSaveable(song.id) { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, shape = Components.sheetShape, containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.semantics { testTagsAsResourceId = true },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        BackHandler(enabled = onBack != null) { onBack?.invoke() }
        SheetMotion {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Space.content).padding(bottom = Space.section),
                verticalArrangement = Arrangement.spacedBy(Space.item)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onBack != null) ReviewIcon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), onBack)
                    Text(stringResource(R.string.add_to_playlist), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    ReviewIcon(Icons.Default.Close, stringResource(R.string.close), onDismiss)
                }
                ReviewSongIdentity(song)
                ReviewNotice(stringResource(R.string.review_picker_help))
                OutlinedTextField(query, { query = it }, label = { Text(stringResource(R.string.playlist_search)) },
                    leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true, shape = Components.buttonShape,
                    modifier = Modifier.fillMaxWidth().testTag("review-playlist-query"))
                playlists.filter { it.title.contains(query, ignoreCase = true) }.forEach { playlist ->
                    Surface(onClick = { onChoose(playlist) }, shape = Components.buttonShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth().testTag("review-pick-${playlist.id}")) {
                        Row(Modifier.padding(Space.item), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Space.item)) {
                            ReviewCover(playlist.artwork, playlist.title, Modifier.size(48.dp), lettering = false)
                            Column(Modifier.weight(1f)) {
                                Text(playlist.title, style = MaterialTheme.typography.titleSmall)
                                ReviewNotice(if (playlist.id in memberships) stringResource(R.string.review_already_saved)
                                    else pluralStringResource(R.plurals.songs_count, playlist.count, playlist.count))
                            }
                            Icon(if (playlist.id in memberships) Icons.Default.CheckCircle else Icons.Default.Add,
                                null, tint = if (playlist.id in memberships) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                ReviewAction(stringResource(R.string.review_new_playlist), Icons.Default.Add, onCreate, "review-create-playlist")
            }
        }
    }
}

@Composable
internal fun ReviewNewPlaylist(addSong: Boolean, onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.review_new_playlist)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(Space.item)) {
            ReviewNotice(stringResource(R.string.review_picker_help))
            OutlinedTextField(title, { title = it.take(100) }, label = { Text(stringResource(R.string.review_playlist_name)) },
                singleLine = true, modifier = Modifier.fillMaxWidth())
        } }, confirmButton = { TextButton(onClick = { onCreate(title.trim()) }, enabled = title.isNotBlank()) {
            Text(stringResource(if (addSong) R.string.create_and_add else R.string.review_new_confirm))
        } }, dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReviewQueue(songs: List<ReviewSong>, index: Int, onDismiss: () -> Unit, onChoose: (Int) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, shape = Components.sheetShape,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Space.content).padding(bottom = Space.section),
            verticalArrangement = Arrangement.spacedBy(Space.item)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.queue), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                ReviewIcon(Icons.Default.Close, stringResource(R.string.close), onDismiss)
            }
            ReviewNotice(stringResource(R.string.review_queue_hint))
            songs.forEachIndexed { position, song ->
                Surface(onClick = { onChoose(position) }, shape = Components.buttonShape,
                    color = if (position == index) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surface) {
                    Row(Modifier.fillMaxWidth().padding(Space.item), horizontalArrangement = Arrangement.spacedBy(Space.item), verticalAlignment = Alignment.CenterVertically) {
                        Text("${position + 1}", color = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f)) { Text(song.title, style = MaterialTheme.typography.titleSmall); ReviewNotice(song.artist) }
                        if (position == index) Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}
