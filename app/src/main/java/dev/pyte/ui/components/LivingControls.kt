package dev.pyte.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.pyte.domain.Track
import dev.pyte.ui.theme.LocalLivingUi
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import dev.pyte.R
import androidx.compose.ui.res.stringResource

@Composable
internal fun Modifier.pressMotion(source: MutableInteractionSource): Modifier {
    val pressed by source.collectIsPressedAsState()
    val policy = LocalLivingUi.current
    val scale = animateFloatAsState(if (pressed && policy.animate) .94f else 1f,
        if (policy.animate) spring(dampingRatio = .68f, stiffness = 900f) else snap(), label = "pressScale")
    return graphicsLayer { scaleX = scale.value; scaleY = scale.value }
}

@Composable
fun LivingPlayButton(isPlaying: Boolean, onClick: () -> Unit, size: Dp = 80.dp) {
    val interaction = remember { MutableInteractionSource() }
    FilledIconButton(onClick = feedbackClick(onClick), interactionSource = interaction,
        modifier = Modifier.size(size).pressMotion(interaction).background(Brush.linearGradient(listOf(
            MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)), CircleShape),
        colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.Transparent)) {
        PlayPauseGlyph(isPlaying, Modifier.size(size * .48f))
    }
}

@Composable
private fun PlayPauseGlyph(isPlaying: Boolean, modifier: Modifier) {
    val policy = LocalLivingUi.current
    val progress = animateFloatAsState(if (isPlaying) 1f else 0f, tween(policy.duration(240)), label = "playPauseMorph")
    val color = MaterialTheme.colorScheme.onPrimary
    val label = stringResource(if (isPlaying) R.string.pause else R.string.play)
    Canvas(modifier.semantics { contentDescription = label }) {
        val play = listOf(listOf(.25f to .14f, .25f to .86f, .52f to .68f, .52f to .32f),
            listOf(.52f to .32f, .52f to .68f, .82f to .5f, .82f to .5f))
        val pause = listOf(listOf(.24f to .17f, .24f to .83f, .43f to .83f, .43f to .17f),
            listOf(.57f to .17f, .57f to .83f, .76f to .83f, .76f to .17f))
        repeat(2) { part ->
            val path = Path()
            repeat(4) { point ->
                val (ax, ay) = play[part][point]; val (bx, by) = pause[part][point]
                val x = (ax + (bx - ax) * progress.value) * size.width
                val y = (ay + (by - ay) * progress.value) * size.height
                if (point == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close(); drawPath(path, color)
        }
    }
}

@Composable
fun FavoriteButton(isFavorite: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, key: String = "favorite") {
    val policy = LocalLivingUi.current
    val interaction = remember { MutableInteractionSource() }
    val burst = remember(key) { Animatable(1f) }
    var event by remember(key) { mutableIntStateOf(0) }
    var adding by remember(key) { mutableStateOf(false) }
    LaunchedEffect(event, policy.animate) {
        if (event > 0 && adding && policy.animate) {
            burst.snapTo(0f); burst.animateTo(1f, tween(policy.duration(460)))
        } else burst.snapTo(1f)
    }
    val accent = MaterialTheme.colorScheme.primary
    Box(modifier) {
        IconButton(onClick = feedbackClick {
            adding = !isFavorite; event++
            onClick()
        }, interactionSource = interaction, modifier = Modifier.pressMotion(interaction)) {
            Icon(if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                stringResource(if (isFavorite) R.string.favorite_remove else R.string.favorite_add),
                tint = if (isFavorite) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.graphicsLayer {
                    val scale = 1f + sin(burst.value * PI).toFloat() * .24f
                    scaleX = scale; scaleY = scale
                })
        }
        if (policy.particles) Canvas(Modifier.matchParentSize()) {
            if (burst.value < 1f) repeat(7) { index ->
                val angle = index / 7.0 * PI * 2
                val radius = (12 + 20 * burst.value).dp.toPx()
                drawCircle(accent.copy(alpha = (1 - burst.value) * .8f), (2.5f * (1 - burst.value)).dp.toPx(),
                    center + Offset(cos(angle).toFloat() * radius, sin(angle).toFloat() * radius))
            }
        }
    }
}

/** Interruptible transitions: no animation queue, and no player commands inside this UI helper. */
@Composable
internal fun SongSwap(track: Track, direction: Int, modifier: Modifier = Modifier, content: @Composable (Track) -> Unit) {
    val policy = LocalLivingUi.current
    val duration = if (LocalPlayerMorph.current?.hiddenId != null) 0 else policy.duration(330)
    AnimatedContent(track, modifier = modifier, contentKey = { it.videoId }, label = "songSwap",
        transitionSpec = {
            (fadeIn(tween(duration)) + scaleIn(tween(duration), initialScale = .94f) +
                slideInHorizontally(tween(duration)) { it / 5 * direction }) togetherWith
                (fadeOut(tween(duration)) + scaleOut(tween(duration), targetScale = 1.04f) +
                    slideOutHorizontally(tween(duration)) { -it / 5 * direction })
        }) { content(it) }
}

internal fun songDirection(previousId: String?, currentIndex: Int, queue: List<Track>, shuffle: Boolean): Int {
    val previous = queue.indexOfFirst { it.videoId == previousId }
    if (shuffle || previous < 0 || queue.size < 2) return 1
    if (currentIndex == (previous + 1) % queue.size) return 1
    if (currentIndex == (previous - 1 + queue.size) % queue.size) return -1
    return if (currentIndex >= previous) 1 else -1
}

@Composable
internal fun SongMetadata(track: Track, direction: Int, modifier: Modifier = Modifier, compact: Boolean = false) {
    SongSwap(track, direction, modifier) { shown ->
        Column {
            Text(shown.title, style = if (compact) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold, maxLines = if (compact) 1 else 2, overflow = TextOverflow.Ellipsis)
            Text(shown.artist, style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
internal fun ShimmerPlaceholder(modifier: Modifier = Modifier) {
    val policy = LocalLivingUi.current
    var phase by remember { mutableFloatStateOf(-1f) }
    LaunchedEffect(policy.ambient) {
        if (policy.ambient) {
            var last = 0L
            while (true) withFrameNanos { now ->
                if (last == 0L) last = now
                if (now - last >= 33_333_333L) {
                    phase = if (phase >= 2f) -1f else phase + (now - last).coerceAtMost(100_000_000L) / 1_000_000_000f
                    last = now
                }
            }
        }
    }
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        val start = size.width * phase
        drawRect(Brush.linearGradient(listOf(Color.Transparent, color.copy(alpha = .10f), Color.Transparent),
            start = Offset(start, 0f), end = Offset(start + size.width * .6f, size.height)))
    }
}
