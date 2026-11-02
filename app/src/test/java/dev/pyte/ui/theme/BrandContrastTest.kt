package dev.pyte.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.compositeOver
import java.io.File
import javax.imageio.ImageIO
import org.junit.Assert.assertTrue
import org.junit.Test

class BrandContrastTest {
    @Test fun `background artwork remains readable behind the shared scrim`() {
        val image = ImageIO.read(File("src/main/res/drawable-nodpi/pyte_background.png"))
        var brightest = PyteBackground
        for (y in 0 until image.height) for (x in 0 until image.width) {
            val background = PyteBackgroundScrim.compositeOver(Color(image.getRGB(x, y)))
            if (background.luminance() > brightest.luminance()) brightest = background
        }
        for (text in listOf(PyteTextPrimary, PyteTextMuted, PyteAccent)) {
            assertTrue("Artwork text contrast must be >= 4.5:1", contrast(text, brightest) >= 4.5f)
        }
    }

    private fun contrast(a: Color, b: Color): Float {
        val light = maxOf(a.luminance(), b.luminance())
        val dark = minOf(a.luminance(), b.luminance())
        return (light + 0.05f) / (dark + 0.05f)
    }

    @Test fun `primary muted and selected text meet normal text contrast on all app surfaces`() {
        for (surface in listOf(PyteBackground, PyteSurface, PyteSurfaceHigh)) {
            for (text in listOf(PyteTextPrimary, PyteTextMuted, PyteAccent)) {
                assertTrue("Text contrast must be >= 4.5:1", contrast(text, surface) >= 4.5f)
            }
        }
    }

    @Test fun `button labels meet contrast on gradient endpoints and solid controls`() {
        assertTrue(contrast(PyteOnBrand, PyteBrandStart) >= 4.5f)
        assertTrue(contrast(PyteOnBrand, PyteBrandEnd) >= 4.5f)
        assertTrue(contrast(PyteOnAccent, PyteAccent) >= 4.5f)
    }
}
