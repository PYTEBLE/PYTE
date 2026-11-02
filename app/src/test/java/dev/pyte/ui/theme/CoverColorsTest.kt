package dev.pyte.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.*
import org.junit.Test

class CoverColorsTest {
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
