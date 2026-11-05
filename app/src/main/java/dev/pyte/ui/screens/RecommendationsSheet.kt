package dev.pyte.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.pyte.domain.Track
import dev.pyte.playback.PlayerUiState
import dev.pyte.data.remote.SongRecommendation
import dev.pyte.ui.components.Artwork
import dev.pyte.ui.theme.LocalLivingUi
import dev.pyte.ui.theme.LivingUiPolicy
import dev.pyte.ui.viewmodels.DiscoveryState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecommendationsSheet(
    state: DiscoveryState,
    playback: PlayerUiState,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onListen: (Track) -> Unit,
    onReturn: () -> Unit,
    onAdd: (Track) -> Unit,
) {
    val context = state.context ?: return
    val saving by rememberUpdatedState(state.savingId != null)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden || !saving }),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        DiscoveryMotion { policy ->
        Column(Modifier.fillMaxWidth().fillMaxHeight(.92f)) {
            Column(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(
                MaterialTheme.colorScheme.primary.copy(alpha = .14f), MaterialTheme.colorScheme.surfaceContainerLow))).padding(horizontal = 20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Similar songs", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(if (context.automatic) "Your playlist, a little further" else "A new direction for your sound",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onRetry, enabled = !state.loading && state.savingId == null) { Icon(Icons.Default.Refresh, "Refresh suggestions") }
                    IconButton(onClick = onDismiss, enabled = state.savingId == null) { Icon(Icons.Default.Close, "Close suggestions") }
                }
                Text("Inspired by ${context.title}", Modifier.padding(top = 14.dp, bottom = 16.dp),
                    style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            AnimatedVisibility(playback.isPreview, enter = fadeIn(tween(policy.duration(180))), exit = fadeOut(tween(policy.duration(120)))) {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Listening preview · ${playback.current?.title.orEmpty()}", maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelLarge)
                        TextButton(onClick = onReturn) { Text("Return to playback") }
                        playback.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
            if (!playback.isPreview) Text("Listen without losing your queue. Closing restores your original playback.",
                Modifier.padding(horizontal = 20.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            val feedback = state.saveError ?: state.status
            AnimatedVisibility(feedback != null, enter = fadeIn(tween(policy.duration(180))), exit = fadeOut(tween(policy.duration(120)))) {
                Text(feedback.orEmpty(), Modifier.padding(horizontal = 20.dp, vertical = 8.dp).semantics { liveRegion = LiveRegionMode.Polite },
                    color = if (state.saveError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium)
            }
            if (state.partial) Text("Some sources were unavailable. These suggestions are ready to explore.",
                Modifier.padding(horizontal = 20.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall)
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (state.error != null) {
                Column(Modifier.padding(20.dp)) {
                    Text(state.error, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = onRetry, enabled = !state.loading) { Text("Try again") }
                }
            }
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (state.loading && state.songs.isEmpty()) items(4) {
                    Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth().height(110.dp)) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(Modifier.size(56.dp), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.outlineVariant) {}
                            Column(Modifier.weight(1f).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Surface(Modifier.fillMaxWidth().height(12.dp), color = MaterialTheme.colorScheme.outlineVariant) {}
                                Surface(Modifier.fillMaxWidth(.6f).height(10.dp), color = MaterialTheme.colorScheme.outlineVariant) {}
                            }
                        }
                    }
                }
                if (!state.loading && state.error == null && state.songs.isEmpty()) item {
                    Column(Modifier.fillMaxWidth().padding(24.dp)) {
                        Text("No new matches this time", style = MaterialTheme.typography.titleMedium)
                        Text("The available suggestions may already be in your playlist. Try another song or refresh later.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                items(state.songs, key = { it.track.videoId }) { song ->
                    val preview = playback.isPreview && playback.current?.videoId == song.track.videoId
                    RecommendationCard(song, preview, preview && playback.isPlaying,
                        song.track.videoId in state.added, song.track.videoId == state.savingId, state.savingId == null,
                        onListen = { onListen(song.track) }, onAdd = { onAdd(song.track) },
                        modifier = if (policy.animate) Modifier.animateItem(fadeInSpec = tween(policy.duration(180)),
                            placementSpec = tween(policy.duration(220)), fadeOutSpec = tween(policy.duration(120))) else Modifier)
                }
            }
        }
        }
    }
}

/** The dialog owns focus while the underlying activity's decorative effects correctly sleep. */
@Composable
private fun DiscoveryMotion(content: @Composable (LivingUiPolicy) -> Unit) {
    val inherited = LocalLivingUi.current
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val motion = inherited.copy(foreground = LocalWindowInfo.current.isWindowFocused && lifecycle.isAtLeast(Lifecycle.State.RESUMED))
    CompositionLocalProvider(LocalLivingUi provides motion) { content(motion) }
}

@Composable
private fun RecommendationCard(
    recommendation: SongRecommendation, preview: Boolean, playing: Boolean,
    added: Boolean, saving: Boolean, canAdd: Boolean, onListen: () -> Unit, onAdd: () -> Unit, modifier: Modifier,
) {
    val song = recommendation.track
    val policy = LocalLivingUi.current
    val scale by animateFloatAsState(if (added && policy.animate) 1.04f else 1f, tween(policy.duration(180)), label = "recommendationAdded")
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp),
        color = if (preview) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, if (preview) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Artwork(song.thumbnailUrl, Modifier.size(58.dp), videoId = song.videoId)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(song.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(song.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Text("Because of ${recommendation.seedTitle}", Modifier.padding(top = 10.dp, bottom = 4.dp),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onListen, modifier = Modifier.semantics {
                    contentDescription = if (playing) "Pause preview of ${song.title}" else "Listen to ${song.title}"
                }) {
                    Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (playing) "Pause preview" else "Listen")
                }
                FilledTonalButton(onClick = onAdd, enabled = canAdd && !added,
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary),
                    modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale }.semantics {
                        contentDescription = if (added) "${song.title} added" else "Add ${song.title} to a local playlist"
                    }) {
                    AnimatedContent(targetState = added, transitionSpec = {
                        fadeIn(tween(policy.duration(180))) togetherWith fadeOut(tween(policy.duration(100)))
                    }, label = "recommendationCheck") { checked ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            else Icon(if (checked) Icons.Default.Check else Icons.Default.Add, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (checked) "Added" else "Add")
                        }
                    }
                }
            }
        }
    }
}
