package dev.pyte.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pyte.ui.components.Artwork
import dev.pyte.ui.components.EmptyState
import dev.pyte.ui.components.TrackRow
import dev.pyte.ui.components.BrandButton
import dev.pyte.ui.theme.PyteAccent
import dev.pyte.ui.theme.PyteBrandStart
import dev.pyte.ui.viewmodels.PlaylistUiState
import dev.pyte.ui.viewmodels.PlaylistViewModel

@Composable
fun PlaylistScreen(
    onBack: () -> Unit,
    contentPadding: PaddingValues,
    viewModel: PlaylistViewModel = hiltViewModel(),
) {
    val detail by viewModel.detail.collectAsStateWithLifecycle()
    val action by viewModel.action.collectAsStateWithLifecycle()
    val favoriteIds by viewModel.favoriteIds.collectAsStateWithLifecycle()
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var menuOpen by remember { mutableStateOf(false) }
    var renameOpen by rememberSaveable { mutableStateOf(false) }
    var deleteOpen by rememberSaveable { mutableStateOf(false) }
    var renameText by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(action.message) {
        action.message?.let { snackbar.showSnackbar(it); viewModel.consumeMessage() }
    }

    Scaffold(
        modifier = Modifier.padding(contentPadding),
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner).statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                Text("Playlist", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                if (detail is PlaylistUiState.Loaded) {
                    Box {
                        IconButton(onClick = { menuOpen = true }, enabled = !action.busy) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Playlist options")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(text = { Text("Rename locally") }, onClick = {
                                menuOpen = false
                                renameText = (detail as PlaylistUiState.Loaded).summary.title
                                renameOpen = true
                            })
                            if (!(detail as PlaylistUiState.Loaded).summary.isLocal) DropdownMenuItem(text = { Text("Refresh from YouTube") }, onClick = {
                                menuOpen = false; viewModel.refresh()
                            })
                            DropdownMenuItem(text = { Text("Delete playlist") }, onClick = {
                                menuOpen = false; deleteOpen = true
                            })
                        }
                    }
                }
            }
            if (action.busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            when (val current = detail) {
                PlaylistUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                PlaylistUiState.Missing -> EmptyState("Playlist not found", "It may have been deleted.", "Go back", onBack)
                is PlaylistUiState.Loaded -> {
                    val playlist = current.summary
                    val tracks = current.tracks
                    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                        item(key = "header") {
                            Column(
                                Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(
                                    PyteBrandStart.copy(alpha = 0.30f), MaterialTheme.colorScheme.background,
                                ))).padding(20.dp),
                            ) {
                                Artwork(playlist.thumbnailUrl, Modifier.size(200.dp).align(Alignment.CenterHorizontally), RoundedCornerShape(16.dp))
                                Spacer(Modifier.height(16.dp))
                                Text(playlist.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                                    maxLines = 3, overflow = TextOverflow.Ellipsis)
                                Text(listOfNotNull(playlist.owner, "${playlist.trackCount} tracks").joinToString(" • "),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(16.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    BrandButton("Play", { viewModel.play(tracks, 0) }, enabled = tracks.isNotEmpty(), icon = Icons.Default.PlayArrow)
                                    OutlinedButton(onClick = { viewModel.shuffleAll(tracks) }, enabled = tracks.isNotEmpty(),
                                        modifier = Modifier.height(56.dp), shape = RoundedCornerShape(18.dp)) {
                                        Icon(Icons.Default.Shuffle, null); Spacer(Modifier.width(6.dp)); Text("Shuffle")
                                    }
                                }
                            }
                        }
                        itemsIndexed(tracks, key = { index, track -> "$index:${track.videoId}" }) { index, track ->
                            TrackRow(track, playerState.current?.videoId == track.videoId, track.videoId in favoriteIds,
                                onClick = { viewModel.play(tracks, index) },
                                onFavoriteClick = { viewModel.toggleFavorite(track, track.videoId !in favoriteIds) },
                                onPlayNext = { viewModel.enqueueNext(track) },
                                onAddToQueue = { viewModel.enqueueLater(track) })
                        }
                    }
                }
            }
        }
    }

    if (renameOpen) {
        AlertDialog(onDismissRequest = { renameOpen = false }, title = { Text("Rename playlist") }, text = {
            OutlinedTextField(renameText, { renameText = it.take(100) }, singleLine = true,
                label = { Text("Local name") }, supportingText = { Text("${renameText.length}/100 · Kept when refreshing") })
        }, confirmButton = {
            TextButton(onClick = { viewModel.rename(renameText); renameOpen = false }, enabled = renameText.isNotBlank() && !action.busy) {
                Text("Save")
            }
        }, dismissButton = { TextButton(onClick = { renameOpen = false }) { Text("Cancel") } })
    }
    if (deleteOpen) {
        AlertDialog(onDismissRequest = { deleteOpen = false }, title = { Text("Delete this playlist?") },
            text = { Text("Only the local playlist is removed. Favorites and listening history are kept.") },
            confirmButton = {
                TextButton(onClick = { deleteOpen = false; viewModel.deletePlaylist(onBack) }, enabled = !action.busy) { Text("Delete") }
            }, dismissButton = { TextButton(onClick = { deleteOpen = false }) { Text("Cancel") } })
    }
}
