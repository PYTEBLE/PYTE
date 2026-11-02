package dev.pyte.ui.theme

import dev.pyte.data.settings.EffectLevel
import org.junit.Assert.*
import org.junit.Test

class LivingUiPolicyTest {
    @Test fun `system reduced motion battery saver and background override every effect level`() {
        EffectLevel.entries.forEach { level ->
            listOf(LivingUiPolicy(level, foreground = false), LivingUiPolicy(level, systemAnimations = false),
                LivingUiPolicy(level, powerSaver = true)).forEach { policy ->
                assertFalse(policy.animate)
                assertFalse(policy.ambient)
                assertFalse(policy.particles)
                assertEquals(0, policy.duration(700))
            }
        }
    }
    @Test fun `subtle mode has no persistent decorative motion`() {
        val policy = LivingUiPolicy(EffectLevel.SUBTLE)
        assertTrue(policy.animate)
        assertFalse(policy.ambient)
        assertFalse(policy.particles)
        assertTrue(policy.duration(700) < LivingUiPolicy().duration(700))
    }
    @Test fun `unknown stored level falls back without disabling existing settings`() {
        assertEquals(EffectLevel.LIVELY, EffectLevel.fromStored(null))
        assertEquals(EffectLevel.LIVELY, EffectLevel.fromStored("future-mode"))
        EffectLevel.entries.forEach { assertEquals(it, EffectLevel.fromStored(it.name)) }
    }
}
