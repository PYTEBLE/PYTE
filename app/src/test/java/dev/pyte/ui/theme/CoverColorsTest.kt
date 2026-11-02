package dev.pyte.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import org.junit.Assert.*
import org.junit.Test

class CoverColorsTest {
    @Test fun `intermediate theme colors and worst case aurora remain readable`() {
        val a = CoverColorExtractor.extract(IntArray(20) { 0xFFFF2222.toInt() })!!
        val b = CoverColorExtractor.extract(IntArray(20) { 0xFF2233FF.toInt() })!!
        for (step in 0..20) {
            val f = step / 20f
            val surface = androidx.compose.ui.graphics.lerp(a.surface, b.surface, f)
            val accent = readableAccent(androidx.compose.ui.graphics.lerp(a.accent, b.accent, f), surface)
            assertTrue(contrast(accent, surface) >= 4.5f)
        }
        for (colors in listOf(a, b)) {
            var background = colors.background
            repeat(3) { background = colors.accent.copy(alpha = .18f).compositeOver(background) }
            background = colors.background.copy(alpha = .65f).compositeOver(background)
            assertTrue(contrast(PyteTextMuted, background) >= 4.5f)
            assertTrue(contrast(colors.accent, background) >= 4.5f)
        }
    }
    @Test fun `different covers produce different accents`() {
        val red = CoverColorExtractor.extract(IntArray(200) { 0xFFFF2222.toInt() })!!
        val blue = CoverColorExtractor.extract(IntArray(200) { 0xFF2244FF.toInt() })!!
        assertNotEquals(red.accent, blue.accent)
        assertTrue(red.accent.red > red.accent.blue)
        assertTrue(blue.accent.blue > blue.accent.red)
    }
    @Test fun `all generated schemes preserve text contrast`() {
        listOf(0xFF080828, 0xFF002200, 0xFFFFFF00, 0xFFEE0044, 0xFF2233FF, 0xFF00CCAA).forEach { pixel ->
            val colors = CoverColorExtractor.extract(IntArray(256) { pixel.toInt() })!!
            assertTrue(contrast(colors.accent, colors.surface) >= 4.5f)
            assertTrue(contrast(colors.onAccent, colors.accent) >= 4.5f)
            assertTrue(contrast(Color.White, colors.background) >= 7f)
        }
    }
    @Test fun `transparent grayscale and empty images use violet fallback`() {
        assertNull(CoverColorExtractor.extract(intArrayOf()))
        assertNull(CoverColorExtractor.extract(IntArray(100) { 0x0088FF22 }))
        assertNull(CoverColorExtractor.extract(IntArray(100) { 0xFF888888.toInt() }))
    }
}
