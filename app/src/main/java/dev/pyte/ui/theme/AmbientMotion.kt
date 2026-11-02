package dev.pyte.ui.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import kotlin.math.PI

/** Finite settling after pause, then no frame callbacks; retained phase prevents pause/resume jumps. */
@Composable
fun rememberAmbientMotion(playing: Boolean, speed: Float): Pair<State<Float>, State<Float>> {
    val policy = LocalLivingUi.current
    val amplitude = animateFloatAsState(if (playing && policy.ambient) 1f else 0f,
        tween(policy.duration(900)), label = "ambientSettle")
    val phase = remember { mutableFloatStateOf(0f) }
    val running = policy.ambient && (playing || amplitude.value > .005f)
    LaunchedEffect(running, speed) {
        if (running) {
            var last = 0L
            while (true) withFrameNanos { now ->
                if (last == 0L) last = now
                if (now - last >= 33_333_333L) {
                    phase.floatValue = (phase.floatValue + (now - last).coerceAtMost(100_000_000L) /
                        1_000_000_000f * speed) % (2 * PI).toFloat()
                    last = now
                }
            }
        }
    }
    return phase to amplitude
}
