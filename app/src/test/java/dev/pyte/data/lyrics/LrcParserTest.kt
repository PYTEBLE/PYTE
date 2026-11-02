package dev.pyte.data.lyrics

import org.junit.Assert.*
import org.junit.Test

class LrcParserTest {
    @Test fun `timestamps fractions repeated lines and metadata`() {
        val lines = LrcParser.parse("[ar:Artist]\n[00:01.2] First\n[00:02.34][01:02.345] Chorus\n[01:03] \ninvalid")
        assertEquals(listOf(1200L, 2340L, 62345L, 63000L), lines.map { it.timeMs })
        assertEquals(listOf("First", "Chorus", "Chorus", ""), lines.map { it.text })
    }
    @Test fun `offset and out of order lines are normalized`() {
        val lines = LrcParser.parse("[offset:-500]\n[00:03] Later\n[00:00.2] Early")
        assertEquals(listOf(0L, 2500L), lines.map { it.timeMs })
    }
    @Test fun `active line follows exact playback position including backward seek`() {
        val lines = LrcParser.parse("[00:02] A\n[00:04] B")
        assertEquals(-1, LrcParser.activeIndex(lines, 0))
        assertEquals(0, LrcParser.activeIndex(lines, 2000))
        assertEquals(1, LrcParser.activeIndex(lines, 5000))
        assertEquals(0, LrcParser.activeIndex(lines, 3000))
    }
    @Test fun `plain and invalid lrc never invent synchronization`() {
        assertTrue(LrcParser.parse("Plain lyrics\n[00:99] Invalid\n[ti:Title]").isEmpty())
    }
}
