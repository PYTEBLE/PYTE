package dev.pyte.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.ui.draw.blur
import dev.pyte.domain.Track
import androidx.compose.runtime.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import dev.pyte.ui.theme.LocalLivingUi
import dev.pyte.ui.theme.rememberAmbientMotion
import kotlin.math.sin

/** Draw-only cover lighting: no bitmap blur and no synthetic beat detection. */
@Composable
fun CoverLight(playing: Boolean, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val policy = LocalLivingUi.current
    val (phase, amplitude) = rememberAmbientMotion(playing, .45f)
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.tertiary
    Box(modifier) {
        Canvas(Modifier.matchParentSize()) {
            val drift = sin(phase.value) * amplitude.value * .18f * policy.strength
            listOf(primary, secondary).forEachIndexed { index, color ->
                val center = Offset(size.width * (if (index == 0) .25f + drift else .75f - drift), size.height * .55f)
                drawRect(Brush.radialGradient(listOf(color.copy(alpha = .48f * policy.strength), Color.Transparent),
                    center, size.maxDimension * .7f))
            }
        }
        content()
    }
}

/** Artwork is the scene, not just a small square. Slow drift is decorative, never beat-synced. */
@Composable
fun PlayerAtmosphere(track: Track?, playing: Boolean) {
    val policy = LocalLivingUi.current
    val (phase, amplitude) = rememberAmbientMotion(playing, .25f)
    val background = MaterialTheme.colorScheme.background
    val accent = MaterialTheme.colorScheme.primary
    Box(Modifier.fillMaxSize()) {
        if (track != null) SongSwap(track, 1, Modifier.fillMaxSize().graphicsLayer {
            alpha = .48f
            scaleX = 1.25f; scaleY = 1.25f
            translationX = sin(phase.value) * 28.dp.toPx() * amplitude.value * policy.strength
            translationY = kotlin.math.cos(phase.value) * 18.dp.toPx() * amplitude.value * policy.strength
        }.blur(40.dp)) { shown ->
            Artwork(shown.thumbnailUrl, Modifier.fillMaxSize(), videoId = shown.videoId, requestedPixels = 1200)
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
            background.copy(alpha = .50f), background.copy(alpha = .60f), background.copy(alpha = .98f)))))
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.radialGradient(listOf(accent.copy(alpha = .20f * policy.strength), Color.Transparent),
                Offset(size.width * .6f, size.height * .25f), size.maxDimension * .65f))
        }
    }
}

@Composable
fun Reveal(key: String, order: Int = 0, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val policy = LocalLivingUi.current
    val progress = remember(key) { Animatable(if (policy.animate) 0f else 1f) }
    LaunchedEffect(key, policy.animate) {
        progress.animateTo(1f, tween(policy.duration(420), delayMillis = policy.duration(order * 65)))
    }
    Box(modifier.graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 24.dp.toPx()
        scaleX = .97f + .03f * progress.value
        scaleY = scaleX
    }) { content() }
}
