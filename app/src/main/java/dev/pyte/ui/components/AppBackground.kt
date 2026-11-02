package dev.pyte.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import dev.pyte.ui.theme.LocalLivingUi
import dev.pyte.ui.theme.rememberAmbientMotion
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun AppBackground(isPlaying: Boolean = false, playerExpanded: Boolean = false, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AuroraBackdrop(isPlaying, playerExpanded)
        content()
    }
}

/** Three radial gradients, no bitmap blur or audio analysis. Draw updates are capped at 30 Hz. */
@Composable
internal fun AuroraBackdrop(isPlaying: Boolean, expanded: Boolean, modifier: Modifier = Modifier) {
    val policy = LocalLivingUi.current
    val (phase, amplitude) = rememberAmbientMotion(isPlaying, .17f)
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.tertiary
    val base = MaterialTheme.colorScheme.background
    Canvas(modifier.fillMaxSize()) {
        val movement = amplitude.value * policy.strength
        val strength = policy.strength * if (expanded) 1f else .75f
        repeat(3) { layer ->
            val offset = layer * 2.1f
            val center = Offset(
                size.width * (.2f + layer * .3f + sin(phase.value + offset) * .18f * movement),
                size.height * (.2f + layer * .26f + sin(phase.value * .7f + offset) * .15f * movement),
            )
            drawCircle(brush = Brush.radialGradient(listOf(
                (if (layer == 1) secondary else primary).copy(alpha = .18f * strength), Color.Transparent),
                center = center, radius = size.maxDimension * .75f),
                radius = size.maxDimension * .75f, center = center)
        }
        // The maximum combined glow remains below ~16% after this scrim, even in Intense mode.
        drawRect(base.copy(alpha = .65f))
    }
}
