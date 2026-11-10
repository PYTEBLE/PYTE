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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.material3.Switch
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import dev.pyte.ui.theme.LocalLivingUi
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
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
import dev.pyte.ui.viewmodels.PlaylistUiState
import dev.pyte.ui.viewmodels.PlaylistViewModel
import dev.pyte.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip

@Composable
fun PlaylistScreen(
    onBack: () -> Unit,
    contentPadding: PaddingValues,
    viewModel: PlaylistViewModel = hiltViewModel(),
    onDiscover: (String, String) -> Unit = { _, _ -> },
    onSearch: () -> Unit = {},
) {
    val detail by viewModel.detail.collectAsStateWithLifecycle()
    val action by viewModel.action.collectAsStateWithLifecycle()
    val favoriteIds by viewModel.favoriteIds.collectAsStateWithLifecycle()
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val selected by viewModel.offlinePlaylists.collectAsStateWithLifecycle()
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var menuOpen by remember { mutableStateOf(false) }
    var renameOpen by rememberSaveable { mutableStateOf(false) }
    var deleteOpen by rememberSaveable { mutableStateOf(false) }
    var renameText by rememberSaveable { mutableStateOf("") }
    val progressLabel = stringResource(R.string.download_progress)
    val offlineLabel = stringResource(R.string.offline_available)
    val listState = rememberLazyListState()
    val motion = LocalLivingUi.current

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
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) }
                Text("Playlist", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                if (detail is PlaylistUiState.Loaded) {
                    Box {
                        IconButton(onClick = { menuOpen = true }, enabled = !action.busy) {
                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.playlist_options))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.playlist_rename)) }, onClick = {
                                menuOpen = false
                                renameText = (detail as PlaylistUiState.Loaded).summary.title
                                renameOpen = true
                            })
                            if (!(detail as PlaylistUiState.Loaded).summary.isLocal) DropdownMenuItem(text = { Text(stringResource(R.string.playlist_refresh)) }, onClick = {
                                menuOpen = false; viewModel.refresh()
                            })
                            DropdownMenuItem(text = { Text(stringResource(R.string.playlist_delete)) }, onClick = {
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
                PlaylistUiState.Missing -> EmptyState(stringResource(R.string.playlist_missing), stringResource(R.string.playlist_missing_body), stringResource(R.string.back), onBack)
                is PlaylistUiState.Loaded -> {
                    val playlist = current.summary
                    val tracks = current.tracks
                    LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 24.dp)) {
                        item(key = "header") {
                            Column(
                                Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(
                                    MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.background,
                                ))).padding(20.dp),
                            ) {
                                Box(Modifier.size(200.dp).align(Alignment.CenterHorizontally).clip(MaterialTheme.shapes.large)) {
                                Artwork(playlist.thumbnailUrl, Modifier.fillMaxSize().graphicsLayer {
                                    val scroll = if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset.toFloat() else 0f
                                    translationY = if (motion.animate) (scroll * .23f * motion.strength).coerceAtMost(48.dp.toPx()) else 0f
                                    val scale = if (motion.animate) 1f - (scroll / 6000f).coerceIn(0f, .08f) else 1f
                                    scaleX = scale; scaleY = scale
                                }, MaterialTheme.shapes.large)
                                }
                                Spacer(Modifier.height(16.dp))
                                Text(playlist.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                                    maxLines = 3, overflow = TextOverflow.Ellipsis)
                                Text(listOfNotNull(playlist.owner?.let { if (it == "On this device") stringResource(R.string.on_device) else it }, pluralStringResource(R.plurals.songs_count, playlist.trackCount, playlist.trackCount)).joinToString(" • "),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                 Spacer(Modifier.height(16.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    BrandButton(stringResource(R.string.play), { viewModel.play(tracks, 0) },
                                        modifier = Modifier.weight(1f), enabled = tracks.isNotEmpty(), icon = Icons.Default.PlayArrow)
                                    FilledTonalButton(onClick = { viewModel.shuffleAll(tracks) }, enabled = tracks.isNotEmpty(),
                                        modifier = Modifier.height(56.dp), shape = MaterialTheme.shapes.medium) {
                                        Icon(Icons.Default.Shuffle, stringResource(R.string.shuffle))
                                    }
                                }
                                FilledTonalButton(onClick = { onDiscover(playlist.id, playlist.title) },
                                    enabled = tracks.isNotEmpty(), modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                                    Icon(Icons.Default.AutoAwesome, null, Modifier.size(20.dp))
                                    Text(stringResource(R.string.discover_similar), Modifier.padding(start = 8.dp))
                                }
                                val enabled = playlist.id in selected
                                val relevant = downloads.filter { d -> tracks.any { it.videoId == d.videoId } }
                                val complete = tracks.count { track -> relevant.any { it.videoId == track.videoId && it.state == "Downloaded" } }
                                val fraction = if (tracks.isEmpty()) 0f else tracks.sumOf { track ->
                                    val record = relevant.firstOrNull { it.videoId == track.videoId }
                                    when {
                                        record?.state == "Downloaded" -> 1.0
                                        record != null && record.total > 0 -> (record.bytes.toDouble() / record.total).coerceIn(0.0, 1.0)
                                        else -> 0.0
                                    }
                                }.toFloat() / tracks.size
                                val ring by animateFloatAsState(fraction, tween(LocalLivingUi.current.duration(280)), label = "offlineProgress")
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(offlineLabel, style = MaterialTheme.typography.titleMedium)
                                        Text(if (enabled) stringResource(R.string.download_count, complete, tracks.size) else stringResource(R.string.download_playlist_only),
                                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (enabled) CircularProgressIndicator(progress = { ring }, strokeWidth = 3.dp,
                                        modifier = Modifier.padding(horizontal = 8.dp).size(32.dp)
                                            .semantics { contentDescription = progressLabel })
                                    Switch(checked = enabled, onCheckedChange = viewModel::offlineEnabled, enabled = !action.busy,
                                        modifier = Modifier.semantics { contentDescription = offlineLabel })
                                }
                                if (enabled) {
                                    relevant.firstOrNull { it.state != "Downloaded" }?.let { download ->
                                        Text(stringResource(if (download.error != null) R.string.download_interrupted else downloadLabel(download.state)), style = MaterialTheme.typography.bodySmall,
                                            color = if (download.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                                        if (download.total > 0) LinearProgressIndicator(
                                            progress = { (download.bytes.toFloat() / download.total).coerceIn(0f, 1f) },
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                                    }
                                    if (complete < tracks.size) TextButton(onClick = viewModel::retryDownloads) { Text(stringResource(R.string.download_retry)) }
                                }
                                Text(stringResource(R.string.actions_hint), Modifier.padding(top = 12.dp),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (tracks.isEmpty()) item(key = "empty-playlist") {
                            Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(stringResource(R.string.playlist_no_songs), style = MaterialTheme.typography.titleLarge)
                                Text(stringResource(R.string.playlist_no_songs_body), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                BrandButton(stringResource(R.string.search), onSearch)
                            }
                        }
                        itemsIndexed(tracks, key = { index, track -> "$index:${track.videoId}" }) { index, track ->
                            TrackRow(track, playerState.current?.videoId == track.videoId, track.videoId in favoriteIds,
                                onClick = { viewModel.play(tracks, index) },
                                onFavoriteClick = { viewModel.toggleFavorite(track, track.videoId !in favoriteIds) },
                                onPlayNext = { viewModel.enqueueNext(track) },
                                onAddToQueue = { viewModel.enqueueLater(track) },
                                isDownloaded = downloads.any { it.videoId == track.videoId && it.state == "Downloaded" })
                        }
                    }
                }
            }
        }
    }

    if (renameOpen) {
        AlertDialog(onDismissRequest = { renameOpen = false }, title = { Text(stringResource(R.string.playlist_rename)) }, text = {
            OutlinedTextField(renameText, { renameText = it.take(100) }, singleLine = true,
                label = { Text(stringResource(R.string.playlist_name)) }, supportingText = { Text(stringResource(R.string.playlist_name_hint, renameText.length)) })
        }, confirmButton = {
            TextButton(onClick = { viewModel.rename(renameText); renameOpen = false }, enabled = renameText.isNotBlank() && !action.busy) {
                Text(stringResource(R.string.save))
            }
        }, dismissButton = { TextButton(onClick = { renameOpen = false }) { Text(stringResource(R.string.cancel)) } })
    }
    if (deleteOpen) {
        AlertDialog(onDismissRequest = { deleteOpen = false }, title = { Text(stringResource(R.string.playlist_delete_title)) },
            text = { Text(stringResource(R.string.playlist_delete_body)) },
            confirmButton = {
                TextButton(onClick = { deleteOpen = false; viewModel.deletePlaylist(onBack) }, enabled = !action.busy) { Text(stringResource(R.string.delete)) }
            }, dismissButton = { TextButton(onClick = { deleteOpen = false }) { Text(stringResource(R.string.cancel)) } })
    }
}

private fun downloadLabel(state: String) = when (state) {
    "Downloaded" -> R.string.offline_available
    "Downloading" -> R.string.download_loading
    "Paused" -> R.string.download_paused
    "Waiting for Wi-Fi" -> R.string.download_wait_wifi
    "Storage limit reached" -> R.string.download_storage
    "Retry pending" -> R.string.download_retry_pending
    else -> R.string.download_wait
}
