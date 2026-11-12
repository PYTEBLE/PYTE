package dev.pyte.ui.components

import dev.pyte.ui.theme.LivingUiPolicy
import org.junit.Assert.*
import org.junit.Test

class UiFeedbackTest {
    private var policy = LivingUiPolicy(sounds = true)
    private val sounds = mutableListOf<UiSound>()
    private val haptics = mutableListOf<UiHaptic>()
    private val feedback = UiFeedback({ policy }, sounds::add, haptics::add)

    @Test fun oneActionGetsOneClickAndOneHaptic() {
        var actions = 0
        feedback.click({ actions++ })
        assertEquals(1, actions)
        assertEquals(listOf(UiSound.TAP), sounds)
        assertEquals(listOf(UiHaptic.CLICK), haptics)
    }

    @Test fun forwardingWrappedCallbacksDoesNotDoubleClick() {
        var actions = 0
        feedback.click({ feedback.click({ actions++ }) })
        assertEquals(1, actions)
        assertEquals(1, sounds.size)
        assertEquals(1, haptics.size)
    }

    @Test fun dispatcherRecoversAfterAnActionThrows() {
        assertThrows(IllegalStateException::class.java) { feedback.click({ error("action failed") }) }
        feedback.click({})
        assertEquals(2, sounds.size)
    }

    @Test fun soundsAndHapticsCanBeDisabledIndependentlyWithoutDisablingActions() {
        var actions = 0
        policy = policy.copy(sounds = false)
        feedback.click({ actions++ })
        assertTrue(sounds.isEmpty())
        assertEquals(1, haptics.size)
        policy = policy.copy(sounds = true, haptics = false)
        feedback.click({ actions++ })
        assertEquals(1, sounds.size)
        assertEquals(1, haptics.size)
        assertEquals(2, actions)
    }

    @Test fun backgroundAndPowerSaverAreSilentButActionsStillWork() {
        var actions = 0
        policy = policy.copy(foreground = false)
        feedback.click({ actions++ })
        feedback.tick(true)
        policy = policy.copy(foreground = true, powerSaver = true)
        feedback.click({ actions++ })
        feedback.tick(false)
        assertEquals(2, actions)
        assertTrue(sounds.isEmpty())
        assertTrue(haptics.isEmpty())
    }

    @Test fun reducedMotionDoesNotDisableOtherSenses() {
        policy = policy.copy(systemAnimations = false)
        feedback.tick(false)
        feedback.tick(true)
        assertEquals(listOf(UiSound.TICK, UiSound.DETENT), sounds)
        assertEquals(listOf(UiHaptic.TICK, UiHaptic.DETENT), haptics)
    }

    @Test fun togglesUseDryOnAndOffVariants() {
        feedback.click({}, UiSound.ON)
        feedback.click({}, UiSound.OFF)
        assertEquals(listOf(UiSound.ON, UiSound.OFF), sounds)
    }
}
