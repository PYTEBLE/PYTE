package dev.pyte.ui.components

import android.os.Build
import android.os.SystemClock
import android.os.PowerManager
import android.view.HapticFeedbackConstants
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.pyte.ui.theme.LivingUiPolicy
import kotlin.math.roundToInt

internal enum class UiHaptic { CLICK, TICK, DETENT }

/** One semantic action, even when reusable controls forward already-wrapped callbacks. */
internal class UiFeedback(
    private val policy: () -> LivingUiPolicy = { LivingUiPolicy() },
    private val sound: (UiSound) -> Unit = {},
    private val haptic: (UiHaptic) -> Unit = {},
) {
    private var dispatching = false

    fun click(action: () -> Unit, sound: UiSound = UiSound.TAP) {
        if (dispatching) { action(); return }
        dispatching = true
        try {
            emit(sound, UiHaptic.CLICK)
            action()
        } finally {
            dispatching = false
        }
    }

    fun tick(detent: Boolean) = emit(
        if (detent) UiSound.DETENT else UiSound.TICK,
        if (detent) UiHaptic.DETENT else UiHaptic.TICK,
    )

    private fun emit(effect: UiSound, touch: UiHaptic) {
        val current = policy()
        if (!current.foreground) return
        if (current.sounds && !current.powerSaver) sound(effect)
        if (current.haptics && !current.powerSaver) haptic(touch)
    }
}

internal val LocalUiFeedback = staticCompositionLocalOf { UiFeedback() }

@Composable
internal fun rememberUiFeedback(policy: LivingUiPolicy, playSound: (UiSound) -> Unit): UiFeedback {
    val latest by rememberUpdatedState(policy)
    val latestSound by rememberUpdatedState(playSound)
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val power = remember(context) { context.getSystemService(PowerManager::class.java) }
    return remember(view, lifecycleOwner, power) {
        // Own dialogs/sheets take focus away from the root window, but are still foreground UI.
        // Check the activity lifecycle at event time, not the root window's animation policy.
        UiFeedback({ latest.copy(foreground = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            && view.isAttachedToWindow && power.isInteractive) }, { latestSound(it) }) { kind ->
            val effect = when (kind) {
                UiHaptic.CLICK -> HapticFeedbackConstants.KEYBOARD_TAP
                UiHaptic.TICK -> if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.SEGMENT_FREQUENT_TICK
                    else HapticFeedbackConstants.CLOCK_TICK
                UiHaptic.DETENT -> if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.SEGMENT_TICK
                    else HapticFeedbackConstants.CLOCK_TICK
            }
            // No IGNORE_* flags: Android's haptic preference remains authoritative.
            view.performHapticFeedback(effect)
        }
    }
}

@Composable
internal fun feedbackClick(action: () -> Unit): () -> Unit {
    val feedback = LocalUiFeedback.current
    return { feedback.click(action) }
}

@Composable
internal fun feedbackToggle(action: (Boolean) -> Unit): (Boolean) -> Unit {
    val feedback = LocalUiFeedback.current
    return { value -> feedback.click({ action(value) }, if (value) UiSound.ON else UiSound.OFF) }
}

/** A continuous control with tactile notches; never changes or rounds the actual value. */
internal class SliderFeedback(
    private val steps: Int,
    private val detents: Set<Int> = setOf(0, steps),
    private val clock: () -> Long = SystemClock::uptimeMillis,
    private val emit: (Boolean) -> Unit,
) {
    private var bucket: Int? = null
    private var lastTick: Long? = null

    fun start(fraction: Float) {
        bucket = index(fraction)
        lastTick = null
    }

    fun move(fraction: Float) {
        val next = index(fraction)
        val previous = bucket
        bucket = next
        if (previous == null || previous == next) return
        val now = clock()
        val detent = detents.any { it in minOf(previous, next)..maxOf(previous, next) && it != previous }
        // A crossed landmark may sound sooner, but never produces a burst of skipped ticks.
        if (lastTick?.let { now - it < if (detent) 25L else 45L } == true) return
        lastTick = now
        emit(detent)
    }

    private fun index(fraction: Float): Int = (fraction.coerceIn(0f, 1f) * steps).roundToInt()
}

@Composable
internal fun rememberSliderFeedback(steps: Int, detents: Set<Int> = setOf(0, steps)): SliderFeedback {
    val feedback = LocalUiFeedback.current
    return remember(feedback, steps, detents) { SliderFeedback(steps, detents, emit = feedback::tick) }
}
