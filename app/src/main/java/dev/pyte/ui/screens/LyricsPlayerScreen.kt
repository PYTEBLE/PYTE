package dev.pyte.ui.screens

import android.animation.ValueAnimator
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import dev.pyte.data.lyrics.LrcParser
import dev.pyte.ui.components.Artwork
import dev.pyte.ui.viewmodels.PlayerViewModel
import kotlin.math.PI
import kotlin.math.sin

@Composable
internal fun LyricsPlayerScreen(viewModel: PlayerViewModel, onClose: () -> Unit, onCover: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lyricsState by viewModel.lyrics.collectAsStateWithLifecycle()
    val favorites by viewModel.favoriteIds.collectAsStateWithLifecycle()
    val track = state.current ?: return
    var queue by rememberSaveable { mutableStateOf(false) }
    var equalizerOpen by rememberSaveable { mutableStateOf(false) }
    if (equalizerOpen) EqualizerSheet(onDismiss = { equalizerOpen = false })
    var request by remember(track.videoId) { mutableIntStateOf(0) }
    val motion = ValueAnimator.areAnimatorsEnabled()
    val accent = MaterialTheme.colorScheme.primary
    LaunchedEffect(track.videoId, request) { viewModel.loadLyrics(track) }
    if (queue) QueueSheet(state, { queue = false }, viewModel::playIndex,
        viewModel::moveQueueItem, viewModel::removeQueueItem)

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Artwork(track.thumbnailUrl, Modifier.fillMaxSize().graphicsLayer { scaleX = 1.15f; scaleY = 1.15f }
            .blur(36.dp), RoundedCornerShape(0.dp), videoId = track.videoId)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = .82f),
            MaterialTheme.colorScheme.background.copy(alpha = .86f), MaterialTheme.colorScheme.background.copy(alpha = .98f)))))
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp)) {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Default.KeyboardArrowDown, "Close player") }
                Artwork(track.thumbnailUrl, Modifier.size(48.dp), videoId = track.videoId)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(track.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(track.artist, color = Color.White.copy(alpha = .65f), maxLines = 1,
                        overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                }
                val favorite = track.videoId in favorites
                IconButton(onClick = { equalizerOpen = true }) { Icon(Icons.Default.Equalizer, "Open equalizer") }
                IconButton(onClick = { viewModel.toggleFavorite(track, !favorite) }) {
                    Icon(if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        if (favorite) "Remove from favorites" else "Add to favorites", tint = accent)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onCover) { Text("Cover", color = Color.White.copy(alpha = .65f)) }
                Surface(shape = CircleShape, color = accent.copy(alpha = .18f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = .7f))) {
                    Text("Lyrics", Modifier.padding(horizontal = 28.dp, vertical = 8.dp), fontWeight = FontWeight.SemiBold)
                }
                TextButton(onClick = { queue = true }) { Text("Queue", color = Color.White.copy(alpha = .65f)) }
            }

            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val lyrics = lyricsState.lyrics.takeIf { lyricsState.trackId == track.videoId }
                when {
                    lyricsState.loading || lyricsState.trackId != track.videoId -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = accent)
                    lyrics?.instrumental == true -> Text("Instrumental · no lyrics", Modifier.align(Alignment.Center), color = Color.White.copy(alpha = .7f))
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
                                Row(Modifier.fillMaxWidth().clickable {
                                    viewModel.seekTo(line.timeMs); follow = true
                                }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.width(4.dp).height(56.dp).clip(CircleShape)
                                        .background(if (selected) accent else Color.Transparent))
                                    Text(line.text.ifBlank { "♪" }, Modifier.padding(start = 16.dp, end = 4.dp)
                                        .graphicsLayer { scaleX = scale; scaleY = scale; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, .5f) },
                                        color = color, fontSize = 27.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        if (!follow) FilledTonalButton(onClick = { follow = true }, modifier = Modifier.align(Alignment.BottomCenter)) {
                            Text("Follow lyrics")
                        }
                    }
                    !lyrics?.plain.isNullOrBlank() -> {
                        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 28.dp)) {
                            item { Text("No synchronized timing available", color = accent,
                                style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(bottom = 20.dp)) }
                            item { Text(lyrics!!.plain!!, fontSize = 25.sp, lineHeight = 36.sp,
                                fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = .85f)) }
                        }
                    }
                    else -> Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(lyricsState.message ?: "No lyrics available for this song.", color = Color.White.copy(alpha = .75f))
                        TextButton(onClick = { request++ }) { Text("Try again") }
                    }
                }
            }
            Text("Lyrics by LRCLIB", color = Color.White.copy(alpha = .45f),
                style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.CenterHorizontally))
            LyricWave(state.isPlaying && motion, Modifier.fillMaxWidth().height(42.dp))
            SeekBar(state.positionMs, state.durationMs, viewModel::seekTo)
            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = viewModel::toggleShuffle) {
                    Icon(Icons.Default.Shuffle, "Shuffle", tint = if (state.shuffle) accent else Color.White.copy(alpha = .6f))
                }
                IconButton(onClick = viewModel::previous) { Icon(Icons.Default.SkipPrevious, "Previous", Modifier.size(34.dp)) }
                FilledIconButton(onClick = viewModel::togglePlayPause,
                    modifier = Modifier.size(72.dp).background(Brush.linearGradient(listOf(accent, MaterialTheme.colorScheme.tertiary)), CircleShape),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.Transparent)) {
                    Icon(if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        if (state.isPlaying) "Pause" else "Play", Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onPrimary)
                }
                IconButton(onClick = viewModel::next) { Icon(Icons.Default.SkipNext, "Next", Modifier.size(34.dp)) }
                IconButton(onClick = viewModel::cycleRepeat) {
                    Icon(if (state.repeat == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                        "Repeat", tint = if (state.repeat != Player.REPEAT_MODE_OFF) accent else Color.White.copy(alpha = .6f))
                }
            }
        }
    }
}

@Composable
private fun LyricWave(animate: Boolean, modifier: Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    val phase = if (animate) {
        val transition = rememberInfiniteTransition(label = "lyricWave")
        transition.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(6000, easing = LinearEasing)), label = "phase").value
    } else 0f
    Canvas(modifier) {
        repeat(3) { layer ->
            val path = Path()
            for (step in 0..100) {
                val x = size.width * step / 100
                val y = size.height / 2 + sin(step / 100f * PI * 3 + phase + layer * .65).toFloat() * size.height * .26f
                if (step == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, accent.copy(alpha = .07f / (layer + 1)),
                style = androidx.compose.ui.graphics.drawscope.Stroke(12.dp.toPx()))
            drawPath(path, accent.copy(alpha = .15f / (layer + 1)),
                style = androidx.compose.ui.graphics.drawscope.Stroke(5.dp.toPx()))
            drawPath(path, accent.copy(alpha = .75f / (layer + 1)),
                style = androidx.compose.ui.graphics.drawscope.Stroke((2f - layer * .4f).dp.toPx()))
        }
    }
}
