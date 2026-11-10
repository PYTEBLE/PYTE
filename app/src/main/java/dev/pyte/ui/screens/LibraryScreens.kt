package dev.pyte.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.FilterChip
import androidx.compose.material3.TextButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pyte.domain.Track
import dev.pyte.ui.components.EmptyState
import dev.pyte.ui.components.TrackRow
import dev.pyte.ui.components.ScreenHeading
import dev.pyte.ui.viewmodels.LibraryViewModel
import dev.pyte.playback.PlayerUiState
import dev.pyte.ui.viewmodels.PlayerViewModel
import dev.pyte.ui.viewmodels.MusicSearchViewModel
import dev.pyte.ui.components.AddToPlaylistDialog
import dev.pyte.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import dev.pyte.ui.components.TrackListSkeleton

@Composable
fun FavoritesScreen(
    contentPadding: PaddingValues,
    viewModel: LibraryViewModel = hiltViewModel(),
    playerViewModel: PlayerViewModel = hiltViewModel(),
    onSearch: () -> Unit = {},
) {
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val favoriteIds by viewModel.favoriteIds.collectAsStateWithLifecycle()
    val playerState by playerViewModel.state.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().padding(contentPadding).statusBarsPadding()) {
        ScreenHeading(stringResource(R.string.favorites), stringResource(R.string.favorites_subtitle, pluralStringResource(R.plurals.songs_count, favorites.size, favorites.size)))
        if (favorites.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.favorites_empty_title),
                body = stringResource(R.string.favorites_empty_body),
                actionLabel = stringResource(R.string.search), onAction = onSearch,
            )
        } else {
            TrackList(
                tracks = favorites,
                playerState = playerState,
                favoriteIds = favoriteIds,
                onPlay = { index -> viewModel.play(favorites, index) },
                onToggleFavorite = { track -> viewModel.toggleFavorite(track, false) },
                onPlayNext = viewModel::enqueueNext,
                onAddToQueue = viewModel::enqueueLater,
            )
        }
    }
}

@Composable
fun SearchScreen(
    contentPadding: PaddingValues,
    viewModel: LibraryViewModel = hiltViewModel(),
    playerViewModel: PlayerViewModel = hiltViewModel(),
    musicViewModel: MusicSearchViewModel = hiltViewModel(),
    onSettings: (() -> Unit)? = null,
) {
    val query by musicViewModel.query.collectAsStateWithLifecycle()
    val localResults by viewModel.results.collectAsStateWithLifecycle()
    val online by musicViewModel.online.collectAsStateWithLifecycle()
    val favoriteIds by viewModel.favoriteIds.collectAsStateWithLifecycle()
    val playerState by playerViewModel.state.collectAsStateWithLifecycle()
    val playlists by musicViewModel.playlists.collectAsStateWithLifecycle()
    val selected by musicViewModel.selected.collectAsStateWithLifecycle()
    val saving by musicViewModel.saving.collectAsStateWithLifecycle()
    val message by musicViewModel.message.collectAsStateWithLifecycle()
    val saveError by musicViewModel.saveError.collectAsStateWithLifecycle()
    var onlineMode by rememberSaveable { mutableStateOf(true) }
    val results = if (onlineMode) online.tracks else localResults
    val snackbar = remember { SnackbarHostState() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(query) { viewModel.setQuery(query) }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); musicViewModel.consumeMessage() } }

    Box(Modifier.fillMaxSize().padding(contentPadding).statusBarsPadding()) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeading(stringResource(R.string.search_title), stringResource(if (onlineMode) R.string.search_online_hint else R.string.search_local_hint),
            onSettings = onSettings)
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = onlineMode, onClick = { onlineMode = true }, label = { Text("YouTube Music") })
            FilterChip(selected = !onlineMode, onClick = { onlineMode = false }, label = { Text(stringResource(R.string.library)) })
        }
        OutlinedTextField(
            value = query,
            onValueChange = musicViewModel::setQuery,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            ),
            placeholder = { Text(stringResource(if (onlineMode) R.string.search_online_placeholder else R.string.search_local_placeholder)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (onlineMode) IconButton(onClick = { keyboard?.hide(); musicViewModel.submit() }, enabled = query.isNotBlank() && !online.loading) {
                    Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search_online_action))
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide(); if (onlineMode) musicViewModel.submit() }),
        )
        if (onlineMode && online.loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
        if (onlineMode && online.error != null) {
            Text(online.error!!, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
            TextButton(onClick = { musicViewModel.submit(loadMore = online.tracks.isNotEmpty()) }) { Text(stringResource(R.string.retry)) }
        }
        when {
            onlineMode && online.loading && results.isEmpty() -> TrackListSkeleton()
            query.isBlank() || (onlineMode && !online.searched) -> EmptyState(
                title = stringResource(if (onlineMode) R.string.search_empty_title else R.string.search_local_placeholder),
                body = stringResource(if (onlineMode) R.string.search_empty_body else R.string.search_local_empty_body),
            )
            results.isEmpty() && !(onlineMode && (online.loading || online.error != null)) -> EmptyState(
                title = stringResource(R.string.search_no_results),
                body = if (onlineMode) stringResource(R.string.search_no_online) else stringResource(R.string.search_no_local, query),
            )
            results.isNotEmpty() -> TrackList(
                tracks = results,
                playerState = playerState,
                favoriteIds = favoriteIds,
                onPlay = { index -> viewModel.play(results, index) },
                onToggleFavorite = { track -> viewModel.toggleFavorite(track, track.videoId !in favoriteIds) },
                onPlayNext = viewModel::enqueueNext,
                onAddToQueue = viewModel::enqueueLater,
                onAddToPlaylist = musicViewModel::select,
                onLoadMore = if (onlineMode && online.continuation != null) ({ musicViewModel.submit(loadMore = true) }) else null,
                loading = onlineMode && online.loading,
            )
        }
    }
    SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }
    selected?.let { track ->
        AddToPlaylistDialog(track, playlists, saving, saveError, musicViewModel::dismissSelection,
            onChoose = { musicViewModel.addToPlaylist(id = it) },
            onCreate = { musicViewModel.addToPlaylist(newTitle = it) })
    }
}

@Composable
private fun TrackList(
    tracks: List<Track>,
    playerState: PlayerUiState,
    favoriteIds: Set<String>,
    onPlay: (Int) -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onPlayNext: (Track) -> Unit,
    onAddToQueue: (Track) -> Unit,
    onAddToPlaylist: ((Track) -> Unit)? = null,
    onLoadMore: (() -> Unit)? = null,
    loading: Boolean = false,
) {
    LazyColumn(contentPadding = PaddingValues(bottom = 120.dp)) {
        itemsIndexed(tracks, key = { _, t -> t.videoId }) { index, track ->
            TrackRow(
                track = track,
                isCurrent = playerState.current?.videoId == track.videoId,
                isFavorite = track.videoId in favoriteIds,
                onClick = { onPlay(index) },
                onFavoriteClick = { onToggleFavorite(track) },
                onPlayNext = { onPlayNext(track) },
                onAddToQueue = { onAddToQueue(track) },
                onAddToPlaylist = onAddToPlaylist?.let { action -> { action(track) } },
            )
        }
        if (onLoadMore != null) item(key = "load-more") {
            TextButton(onClick = onLoadMore, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (loading) R.string.loading else R.string.load_more))
            }
        }
    }
}
