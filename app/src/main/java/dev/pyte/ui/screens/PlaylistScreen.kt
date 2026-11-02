package dev.pyte.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import dev.pyte.ui.theme.PyteAccent
import dev.pyte.ui.viewmodels.PlaylistViewModel

@Composable
fun PlaylistScreen(
    onBack: () -> Unit,
    contentPadding: PaddingValues,
    viewModel: PlaylistViewModel = hiltViewModel(),
) {
    val detail by viewModel.detail.collectAsStateWithLifecycle()
    val favoriteIds by viewModel.favoriteIds.collectAsStateWithLifecycle()
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()

    val current = detail
    if (current == null) {
        EmptyState(
            title = "Playlist not found",
            body = "It may have been deleted.",
            actionLabel = "Go back",
            onAction = onBack,
            modifier = Modifier.padding(contentPadding),
        )
        return
    }
    val (playlist, tracks) = current

    Column(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
        Box {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(PyteAccent.copy(alpha = 0.55f), MaterialTheme.colorScheme.background),
                        ),
                    ),
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { viewModel.deletePlaylist(onBack) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete playlist")
                    }
                }
                Spacer(Modifier.height(8.dp))
                Artwork(
                    url = playlist.thumbnailUrl,
                    modifier = Modifier.size(200.dp).align(Alignment.CenterHorizontally),
                    shape = RoundedCornerShape(16.dp),
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = playlist.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = listOfNotNull(playlist.owner, "${playlist.trackCount} tracks").joinToString(" • "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { viewModel.play(tracks, 0) },
                        enabled = tracks.isNotEmpty(),
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Play")
                    }
                    OutlinedButton(
                        onClick = { viewModel.shuffleAll(tracks) },
                        enabled = tracks.isNotEmpty(),
                    ) {
                        Icon(Icons.Default.Shuffle, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Shuffle")
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }

        if (tracks.isEmpty()) {
            EmptyState(
                title = "No tracks",
                body = "This playlist is empty or couldn't be loaded.",
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 120.dp)) {
                itemsIndexed(tracks, key = { _, t -> t.videoId }) { index, track ->
                    TrackRow(
                        track = track,
                        isCurrent = playerState.current?.videoId == track.videoId,
                        isFavorite = track.videoId in favoriteIds,
                        onClick = { viewModel.play(tracks, index) },
                        onFavoriteClick = {
                            viewModel.toggleFavorite(track, track.videoId !in favoriteIds)
                        },
                    )
                }
            }
        }
    }
}
