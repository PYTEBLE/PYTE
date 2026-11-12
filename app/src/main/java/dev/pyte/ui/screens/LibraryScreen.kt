package dev.pyte.ui.screens

import dev.pyte.ui.components.TactileIconButton as IconButton
import dev.pyte.ui.components.feedbackClick

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pyte.R
import dev.pyte.ui.components.PlaylistCard
import dev.pyte.ui.components.Reveal
import dev.pyte.ui.components.TrackRow
import dev.pyte.ui.components.EmptyState
import dev.pyte.ui.components.LibraryCollectionCard
import dev.pyte.ui.components.playlistCatalogItems
import dev.pyte.ui.theme.LocalLivingUi
import dev.pyte.ui.viewmodels.HomeViewModel
import dev.pyte.ui.viewmodels.LibraryViewModel
import dev.pyte.ui.viewmodels.PlayerViewModel
import androidx.compose.animation.core.tween

/** Bibliothek = Playlists, Favoriten and offline collections in one labeled place. */
@Composable
fun LibraryScreen(
    contentPadding: PaddingValues,
    onOpenPlaylist: (String) -> Unit,
    onSearch: () -> Unit,
    onSettings: (() -> Unit)? = null,
    homeViewModel: HomeViewModel = hiltViewModel(),
    libraryViewModel: LibraryViewModel = hiltViewModel(),
    playerViewModel: PlayerViewModel = hiltViewModel(),
    catalogViewModel: dev.pyte.ui.viewmodels.PlaylistCatalogViewModel = hiltViewModel(),
) {
    val playlists by homeViewModel.playlists.collectAsStateWithLifecycle()
    val offlineIds by homeViewModel.offlinePlaylists.collectAsStateWithLifecycle()
    val favorites by libraryViewModel.favorites.collectAsStateWithLifecycle()
    val favoriteIds by libraryViewModel.favoriteIds.collectAsStateWithLifecycle()
    val playerState by playerViewModel.state.collectAsStateWithLifecycle()
    val created by homeViewModel.createMessage.collectAsStateWithLifecycle()
    val catalog by catalogViewModel.state.collectAsStateWithLifecycle()
    val catalogImport by catalogViewModel.import.collectAsStateWithLifecycle()
    val motion = LocalLivingUi.current
    var filter by rememberSaveable { mutableStateOf("playlists") }
    var alphabetical by rememberSaveable { mutableStateOf(false) }
    var createOpen by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val largeText = LocalDensity.current.fontScale > 1.3f
    LaunchedEffect(created) {
        created?.let { snackbar.showSnackbar(it); homeViewModel.consumeCreateMessage() }
    }
    val visible = playlists.filter { filter != "offline" || it.id in offlineIds }
        .let { list -> if (alphabetical) list.sortedBy { it.title.lowercase(java.util.Locale.GERMAN) } else list }

    Box(Modifier.fillMaxSize().padding(contentPadding).statusBarsPadding()) {
        LazyVerticalGrid(
            columns = if (largeText) GridCells.Fixed(1) else GridCells.Adaptive(160.dp),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "library-head", span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.library), Modifier.weight(1f), style = MaterialTheme.typography.headlineLarge)
                        onSettings?.let { settings ->
                            IconButton(onClick = settings) {
                                Icon(androidx.compose.material.icons.Icons.Default.Settings, stringResource(R.string.settings))
                            }
                        }
                    }
                    Text(stringResource(R.string.library_subtitle_text),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(filter == "playlists", feedbackClick { filter = "playlists" },
                            label = { Text(stringResource(R.string.library_filter_playlists)) })
                        FilterChip(filter == "favorites", feedbackClick { filter = "favorites" },
                            label = { Text(stringResource(R.string.favorites)) })
                        FilterChip(filter == "offline", feedbackClick { filter = "offline" },
                            label = { Text(stringResource(R.string.library_filter_offline)) })
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(if (filter == "favorites") R.string.favorites else R.string.library_collections),
                            Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        if (filter != "favorites") {
                            IconButton(onClick = { alphabetical = !alphabetical }) {
                                Icon(Icons.Default.Sort, stringResource(if (alphabetical) R.string.library_sort_name else R.string.library_sort_recent))
                            }
                            IconButton(onClick = { createOpen = true }) {
                                Icon(Icons.Default.Add, stringResource(R.string.library_new_playlist))
                            }
                        }
                    }
                    if (filter != "favorites") Text(
                        stringResource(if (alphabetical) R.string.library_sort_name else R.string.library_sort_recent),
                        Modifier.padding(bottom = 4.dp), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            when {
                filter == "favorites" && favorites.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(stringResource(R.string.favorites_empty_title), stringResource(R.string.favorites_empty_body),
                        stringResource(R.string.search), onAction = onSearch)
                }
                filter == "favorites" -> items(favorites, key = { "song:${it.videoId}" }, span = { GridItemSpan(maxLineSpan) }) { track ->
                    TrackRow(track, playerState.current?.videoId == track.videoId, track.videoId in favoriteIds,
                        onClick = { libraryViewModel.play(favorites, favorites.indexOf(track)) },
                        onFavoriteClick = { libraryViewModel.toggleFavorite(track, false) },
                        onPlayNext = { libraryViewModel.enqueueNext(track) },
                        onAddToQueue = { libraryViewModel.enqueueLater(track) })
                }
                playlists.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(stringResource(R.string.library_empty), stringResource(R.string.library_empty_body),
                        stringResource(R.string.import_playlist), onAction = { createOpen = true })
                }
                filter == "offline" && visible.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(stringResource(R.string.library_offline_empty), stringResource(R.string.library_offline_empty_body),
                        stringResource(R.string.library_filter_playlists), onAction = { filter = "playlists" })
                }
                else -> items(visible, key = { it.id }, span = { GridItemSpan(if (filter == "playlists" && visible.size == 1) maxLineSpan else 1) }) { playlist ->
                    Reveal(playlist.id, modifier = Modifier.animateItem(
                        fadeInSpec = if (motion.animate) tween(motion.duration(220)) else null,
                        placementSpec = if (motion.animate) tween(motion.duration(320)) else null,
                        fadeOutSpec = if (motion.animate) tween(motion.duration(180)) else null,
                    )) {
                        if (filter == "playlists" && visible.size == 1) {
                            LibraryCollectionCard(playlist, { onOpenPlaylist(playlist.id) }, { homeViewModel.refreshArtwork(playlist.id) })
                        } else PlaylistCard(playlist, { onOpenPlaylist(playlist.id) }, onArtworkError = { homeViewModel.refreshArtwork(playlist.id) })
                    }
                }
            }
            if (filter == "playlists") {
                playlistCatalogItems(catalog, playlists.map { it.id }.toSet(), catalogViewModel::retry) { playlist ->
                    if (playlists.any { it.id == playlist.id }) onOpenPlaylist(playlist.id)
                    else catalogViewModel.select(playlist)
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(horizontal = 20.dp))
    }
    dev.pyte.ui.components.PlaylistImportDialog(catalogImport,
        onConfirm = { catalogViewModel.confirm(onOpenPlaylist) }, onDismiss = catalogViewModel::dismiss)
    if (createOpen) {
        var title by rememberSaveable { mutableStateOf("") }
        AlertDialog(onDismissRequest = { createOpen = false },
            title = { Text(stringResource(R.string.library_new_playlist)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(title, { title = it.take(100) }, singleLine = true,
                        label = { Text(stringResource(R.string.library_new_name)) }, modifier = Modifier.fillMaxWidth())
                    Text(stringResource(R.string.playlist_no_songs_body), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = { TextButton(onClick = feedbackClick { homeViewModel.createPlaylist(title); createOpen = false },
                enabled = title.isNotBlank()) { Text(stringResource(R.string.library_create_confirm)) } },
            dismissButton = { TextButton(onClick = feedbackClick { createOpen = false }) { Text(stringResource(R.string.cancel)) } })
    }
}
