package dev.pyte.ui.screens

import dev.pyte.ui.components.feedbackClick

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pyte.R
import dev.pyte.data.lyrics.LrcParser
import dev.pyte.domain.Track
import dev.pyte.playback.PlayerUiState
import dev.pyte.ui.theme.LocalLivingUi
import dev.pyte.ui.viewmodels.PlayerViewModel

@Composable
internal fun LyricsPanel(viewModel: PlayerViewModel, track: Track, state: PlayerUiState, modifier: Modifier = Modifier) {
    val lyricsState by viewModel.lyrics.collectAsStateWithLifecycle()
    val motion = LocalLivingUi.current.animate
    val accent = MaterialTheme.colorScheme.primary
    var request by remember(track.videoId) { mutableIntStateOf(0) }
    LaunchedEffect(track.videoId, request) { viewModel.loadLyrics(track) }

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val lyrics = lyricsState.lyrics.takeIf { lyricsState.trackId == track.videoId }
            when {
                lyricsState.loading || lyricsState.trackId != track.videoId -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = accent)
                lyrics?.instrumental == true -> Text(stringResource(R.string.lyrics_instrumental), Modifier.align(Alignment.Center), color = Color.White.copy(alpha = .7f))
                lyrics?.synced == true -> key(track.videoId) {
                    val listState = rememberLazyListState()
                    val active = LrcParser.activeIndex(lyrics.lines, state.positionMs)
                    var follow by remember { mutableStateOf(true) }
                    val nested = remember {
                        object : NestedScrollConnection {
                            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                                if (source == NestedScrollSource.UserInput && available.y != 0f) follow = false
                                return Offset.Zero
                            }
                        }
                    }
                    val padding = (maxHeight / 2 - 45.dp).coerceAtLeast(24.dp)
                    LaunchedEffect(active, follow) {
                        if (follow && active >= 0) {
                            if (motion) listState.animateScrollToItem(active) else listState.scrollToItem(active)
                        }
                    }
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize().nestedScroll(nested),
                        contentPadding = PaddingValues(top = padding, bottom = padding),
                        verticalArrangement = Arrangement.spacedBy(28.dp)) {
                        itemsIndexed(lyrics.lines) { index, line ->
                            val selected = index == active
                            val color by animateColorAsState(
                                if (selected) Color.White else Color.White.copy(alpha = if (index < active) .35f else .5f),
                                tween(if (motion) 350 else 0), label = "lyricColor")
                            val scale by animateFloatAsState(if (selected) 1f else .96f,
                                tween(if (motion) 350 else 0), label = "lyricScale")
                            Row(Modifier.fillMaxWidth().clickable(onClick = feedbackClick {
                                viewModel.seekTo(line.timeMs); follow = true
                            }).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.width(4.dp).height(56.dp).clip(CircleShape)
                                    .background(if (selected) accent else Color.Transparent))
                                Text(line.text.ifBlank { "♪" }, Modifier.padding(start = 16.dp, end = 4.dp)
                                    .graphicsLayer { scaleX = scale; scaleY = scale; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, .5f) },
                                    color = color, fontSize = 27.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    if (!follow) FilledTonalButton(onClick = feedbackClick { follow = true }, modifier = Modifier.align(Alignment.BottomCenter)) {
                        Text(stringResource(R.string.lyrics_follow))
                    }
                }
                !lyrics?.plain.isNullOrBlank() -> {
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 28.dp)) {
                        item { Text(stringResource(R.string.lyrics_unsynced), color = accent,
                            style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(bottom = 20.dp)) }
                        item { Text(lyrics!!.plain!!, fontSize = 25.sp, lineHeight = 36.sp,
                            fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = .85f)) }
                    }
                }
                else -> Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(lyricsState.message ?: stringResource(R.string.lyrics_missing), color = Color.White.copy(alpha = .75f))
                    TextButton(onClick = feedbackClick { request++ }) { Text(stringResource(R.string.retry)) }
                }
            }
        }
        Text(stringResource(R.string.lyrics_credit), color = Color.White.copy(alpha = .45f),
            style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(vertical = 4.dp))
    }
}
