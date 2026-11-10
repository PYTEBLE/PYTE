package dev.pyte.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class MiniSwipeTest {
    @Test fun `short drags never change tracks and long drags map to one direction`() {
        assertEquals(0, miniSwipeDirection(60f, 300))
        assertEquals(0, miniSwipeDirection(-60f, 300))
        assertEquals(1, miniSwipeDirection(-100f, 300))
        assertEquals(-1, miniSwipeDirection(100f, 300))
    }

    @Test fun `invalid geometry cannot trigger playback`() {
        assertEquals(0, miniSwipeDirection(100f, 0))
        assertEquals(0, miniSwipeDirection(Float.NaN, 300))
    }
}
