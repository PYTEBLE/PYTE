package dev.pyte.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.dp
import dev.pyte.ui.theme.LocalLivingUi
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState

/** Modal windows take focus from the Activity; keep their own interaction effects eligible. */
@Composable
internal fun SheetMotion(content: @Composable () -> Unit) {
    val inherited = LocalLivingUi.current
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val power = LocalContext.current.getSystemService(android.os.PowerManager::class.java)
    val policy = inherited.copy(foreground = LocalWindowInfo.current.isWindowFocused &&
        lifecycle.isAtLeast(Lifecycle.State.RESUMED) && power.isInteractive)
    CompositionLocalProvider(LocalLivingUi provides policy) { content() }
}

/** Finite, input-driven light bloom; no idle render loop and no synthetic audio animation. */
@Composable
internal fun Modifier.touchBloom(source: MutableInteractionSource): Modifier {
    val policy = LocalLivingUi.current
    val progress = remember { Animatable(1f) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val color = MaterialTheme.colorScheme.primary
    LaunchedEffect(source, policy.animate, policy.particles) {
        progress.snapTo(1f)
        if (policy.particles) source.interactions.filterIsInstance<PressInteraction.Press>().collectLatest {
            origin = it.pressPosition.takeIf { p -> p.x.isFinite() && p.y.isFinite() } ?: Offset.Zero
            progress.snapTo(0f)
            progress.animateTo(1f, tween(policy.duration(520)))
        }
    }
    return drawWithContent {
        drawContent()
        if (policy.particles && progress.value < 1f) {
            val radius = size.maxDimension * (.12f + progress.value)
            // Keep light on the lower rim, not over labels: feedback must not wash out text.
            clipRect(top = (size.height - 6.dp.toPx()).coerceAtLeast(0f)) {
                drawRect(Brush.radialGradient(listOf(color.copy(alpha = (1 - progress.value) * .6f * policy.strength), Color.Transparent),
                    Offset(origin.x, size.height), radius.coerceAtLeast(1f)))
            }
        }
    }
}

/** A real position indicator, not an audio visualizer. Draw-only updates while playing. */
@Composable
internal fun CoverProgress(progress: Float, modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.tertiary
    val position = rememberUpdatedState(progress)
    Box(modifier.drawWithCache {
        val inset = 3.dp.toPx()
        val right = (size.width - inset).coerceAtLeast(inset)
        val bottom = (size.height - inset).coerceAtLeast(inset)
        // The stroke sits 5 dp outside the 30 dp cover corners, so both curves are concentric.
        val radius = minOf(35.dp.toPx(), (right - inset) / 2, (bottom - inset) / 2)
        val outline = Path().apply {
            moveTo(size.width / 2, inset)
            lineTo(right - radius, inset)
            arcTo(Rect(right - radius * 2, inset, right, inset + radius * 2), -90f, 90f, false)
            lineTo(right, bottom - radius)
            arcTo(Rect(right - radius * 2, bottom - radius * 2, right, bottom), 0f, 90f, false)
            lineTo(inset + radius, bottom)
            arcTo(Rect(inset, bottom - radius * 2, inset + radius * 2, bottom), 90f, 90f, false)
            lineTo(inset, inset + radius)
            arcTo(Rect(inset, inset, inset + radius * 2, inset + radius * 2), 180f, 90f, false)
            close()
        }
        val measure = PathMeasure().apply { setPath(outline, false) }
        val segment = Path()
        val brush = Brush.linearGradient(listOf(secondary, accent))
        val trackStroke = Stroke(1.dp.toPx())
        val progressStroke = Stroke(3.dp.toPx(), cap = StrokeCap.Round)
        onDrawBehind {
            drawPath(outline, accent.copy(alpha = .12f), style = trackStroke)
            val fraction = position.value.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
            segment.reset()
            if (fraction >= 1f) {
                drawPath(outline, brush, style = progressStroke)
            } else if (fraction > 0f) {
                measure.getSegment(0f, measure.length * fraction, segment, true)
                drawPath(segment, brush, style = progressStroke)
            }
        }
    })
}

@Composable
internal fun TrackListSkeleton() {
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        repeat(5) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                androidx.compose.material3.Surface(Modifier.size(52.dp), shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh) { ShimmerPlaceholder() }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.Surface(Modifier.fillMaxWidth(.8f).height(18.dp), shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh) { ShimmerPlaceholder() }
                    androidx.compose.material3.Surface(Modifier.fillMaxWidth(.5f).height(14.dp), shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh) { ShimmerPlaceholder() }
                }
            }
        }
    }
}
