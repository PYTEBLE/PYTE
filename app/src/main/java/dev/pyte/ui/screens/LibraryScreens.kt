package dev.pyte.ui.screens

import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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

@Composable
fun FavoritesScreen(
    contentPadding: PaddingValues,
    viewModel: LibraryViewModel = hiltViewModel(),
    playerViewModel: PlayerViewModel = hiltViewModel(),
) {
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val favoriteIds by viewModel.favoriteIds.collectAsStateWithLifecycle()
    val playerState by playerViewModel.state.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().padding(contentPadding).statusBarsPadding()) {
        ScreenHeading("Favorites", "${favorites.size} tracks · The ones you come back to")
        if (favorites.isEmpty()) {
            EmptyState(
                title = "No favorites yet",
                body = "Tap the heart on any track to keep it here.",
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
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val favoriteIds by viewModel.favoriteIds.collectAsStateWithLifecycle()
    val playerState by playerViewModel.state.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().padding(contentPadding).statusBarsPadding()) {
        ScreenHeading("Find your sound", "Search songs and artists in your library")
        OutlinedTextField(
            value = query,
            onValueChange = viewModel::setQuery,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            ),
            placeholder = { Text("Songs and artists in your library") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        )
        when {
            query.isBlank() -> EmptyState(
                title = "Search your library",
                body = "Find any song or artist across all your imported playlists.",
            )
            results.isEmpty() -> EmptyState(
                title = "No matches",
                body = "Nothing in your library matches \"$query\".",
            )
            else -> TrackList(
                tracks = results,
                playerState = playerState,
                favoriteIds = favoriteIds,
                onPlay = { index -> viewModel.play(results, index) },
                onToggleFavorite = { track -> viewModel.toggleFavorite(track, track.videoId !in favoriteIds) },
                onPlayNext = viewModel::enqueueNext,
                onAddToQueue = viewModel::enqueueLater,
            )
        }
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
            )
        }
    }
}
