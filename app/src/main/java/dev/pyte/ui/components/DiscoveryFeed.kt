package dev.pyte.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.pyte.R
import dev.pyte.data.remote.SongRecommendation
import dev.pyte.domain.Track
import dev.pyte.playback.PlayerUiState
import dev.pyte.ui.theme.CoverColors
import dev.pyte.ui.theme.LocalLivingUi
import dev.pyte.ui.viewmodels.SearchSuggestionsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember

@Composable
fun SearchSuggestionsContent(
    state: SearchSuggestionsState,
    player: PlayerUiState,
    favoriteIds: Set<String>,
    onRetry: () -> Unit,
    onPlay: (List<Track>, Int) -> Unit,
    onFavorite: (Track) -> Unit,
    onNext: (Track) -> Unit,
    onQueue: (Track) -> Unit,
    featuredColors: CoverColors? = null,
) {
    val songs = state.songs.map { it.track }
    LazyColumn(contentPadding = PaddingValues(top = 16.dp, bottom = 120.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item(key = "suggestion-heading") {
            Surface(Modifier.padding(horizontal = 20.dp), shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(Modifier.background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha = .13f),
                    MaterialTheme.colorScheme.surfaceContainerHigh))).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.AutoAwesome, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.search_for_you), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.search_for_you_body), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (state.songs.isNotEmpty()) {
            item(key = "suggestion-featured") {
                FeaturedSuggestion(state.songs.first(), featuredColors) { onPlay(songs, 0) }
            }
        }
        if (state.recent.isNotEmpty()) {
            item(key = "recent-heading") {
                Text(stringResource(R.string.search_recent_heading), Modifier.padding(start = 20.dp, top = 20.dp, bottom = 8.dp),
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            item(key = "recent-cards") {
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    itemsIndexed(state.recent.take(6), key = { _, track -> track.videoId }) { index, track ->
                        Surface(onClick = { onPlay(state.recent, index) }, shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.width(156.dp)) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Artwork(track.thumbnailUrl, Modifier.size(64.dp), videoId = track.videoId)
                                Text(track.title, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                                Text(track.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
        when {
            state.loading -> item(key = "suggestion-loading") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.padding(start = 20.dp, top = 12.dp, end = 20.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text(stringResource(R.string.search_suggestions_loading), style = MaterialTheme.typography.bodyMedium)
                    }
                    TrackListSkeleton()
                }
            }
            state.error -> item(key = "suggestion-error") { DiscoveryNotice(stringResource(R.string.search_suggestions_error), onRetry) }
            state.songs.isEmpty() -> item(key = "suggestion-empty") {
                DiscoveryNotice(stringResource(when {
                    !state.historyEnabled -> R.string.search_suggestions_private
                    state.recent.isEmpty() -> R.string.search_suggestions_empty
                    else -> R.string.search_suggestions_no_matches
                }), if (state.historyEnabled && state.recent.isNotEmpty()) onRetry else null)
            }
        }
        if (state.songs.isNotEmpty()) {
            item(key = "suggestion-refresh") {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.discover_similar), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.discovery_refresh)) }
                }
            }
            if (state.partial) item(key = "suggestion-partial") { DiscoveryNotice(stringResource(R.string.discovery_partial), onRetry) }
            itemsIndexed(state.songs.drop(1), key = { _, song -> song.track.videoId }) { offset, song ->
                val index = offset + 1
                Column {
                    TrackRow(song.track, player.current?.videoId == song.track.videoId, song.track.videoId in favoriteIds,
                        onClick = { onPlay(songs, index) }, onFavoriteClick = { onFavorite(song.track) },
                        onPlayNext = { onNext(song.track) }, onAddToQueue = { onQueue(song.track) })
                    Text(stringResource(R.string.search_seed_reason, song.seedTitle),
                        Modifier.padding(start = 76.dp, end = 20.dp, bottom = 10.dp),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun FeaturedSuggestion(featured: SongRecommendation, colors: CoverColors?, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val policy = LocalLivingUi.current
    val sounds = LocalUiSounds.current
    val accent = colors?.accent ?: MaterialTheme.colorScheme.primary
    Surface(onClick = {
            if (policy.sounds) sounds?.play(UiSound.TAP)
            onClick()
        }, shape = RoundedCornerShape(28.dp), interactionSource = interaction,
        color = colors?.background ?: MaterialTheme.colorScheme.primaryContainer,
        contentColor = if (colors != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).pressMotion(interaction).touchBloom(interaction)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Artwork(featured.track.thumbnailUrl, Modifier.size(96.dp), shape = RoundedCornerShape(20.dp), videoId = featured.track.videoId)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.search_featured), style = MaterialTheme.typography.labelSmall,
                    color = accent, letterSpacing = 2.sp)
                Text(featured.track.title, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(featured.track.artist, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.search_seed_reason, featured.seedTitle), maxLines = 2, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun DiscoveryNotice(message: String, onRetry: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        onRetry?.let { TextButton(onClick = it) { Text(stringResource(R.string.retry)) } }
    }
}
