package dev.pyte.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.*
import org.junit.Test

class ListeningDesignTest {
    private fun contrast(a: Color, b: Color): Float {
        val x = a.luminance(); val y = b.luminance()
        return (maxOf(x, y) + .05f) / (minOf(x, y) + .05f)
    }

    @Test fun `candidate surfaces share readable foreground roles`() {
        val c = ListeningDesign.colors
        for (surface in listOf(c.background, c.surface, c.surfaceContainerHigh)) {
            assertTrue(contrast(c.onSurface, surface) >= 4.5f)
            assertTrue(contrast(c.onSurfaceVariant, surface) >= 4.5f)
            assertTrue(contrast(c.primary, surface) >= 4.5f)
            assertTrue(contrast(c.secondary, surface) >= 3f)
        }
        assertTrue(contrast(c.onPrimary, c.primary) >= 4.5f)
        assertTrue(contrast(c.onErrorContainer, c.errorContainer) >= 4.5f)
    }

    @Test fun `component sizes meet Android touch and spacing rules`() {
        assertTrue(ListeningComponents.touchTarget.value >= 48)
        assertTrue(ListeningComponents.buttonHeight >= ListeningComponents.touchTarget)
        assertEquals(0f, ListeningSpace.content.value % ListeningSpace.unit.value, 0f)
        assertEquals(0f, ListeningSpace.section.value % ListeningSpace.unit.value, 0f)
        assertTrue(ListeningMotion.pressMs < ListeningMotion.expansionMs)
    }
}
