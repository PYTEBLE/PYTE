package dev.pyte.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
            val drift = sin(phase.value) * amplitude.value * .08f * policy.strength
            listOf(primary, secondary).forEachIndexed { index, color ->
                val center = Offset(size.width * (if (index == 0) .25f + drift else .75f - drift), size.height * .55f)
                drawRect(Brush.radialGradient(listOf(color.copy(alpha = .24f * policy.strength), Color.Transparent),
                    center, size.maxDimension * .7f))
            }
        }
        content()
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
