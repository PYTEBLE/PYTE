package dev.pyte.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

data class CoverColors(val accent: Color, val background: Color, val surface: Color, val secondary: Color) {
    val onAccent: Color get() = if (contrast(Color.Black, accent) >= contrast(Color.White, accent)) Color.Black else Color.White
}

internal fun contrast(a: Color, b: Color): Float {
    val x = a.luminance(); val y = b.luminance()
    return (maxOf(x, y) + .05f) / (minOf(x, y) + .05f)
}

/** Quantized dominant color, with saturated pixels favored and deterministic grayscale fallback. */
object CoverColorExtractor {
    fun extract(pixels: IntArray): CoverColors? {
        val bins = mutableMapOf<Int, Int>()
        pixels.forEach { pixel ->
            if ((pixel ushr 24) < 128) return@forEach
            val r = (pixel shr 16) and 255; val g = (pixel shr 8) and 255; val b = pixel and 255
            val high = maxOf(r, g, b); val low = minOf(r, g, b)
            if (high < 24 || low > 235 || high - low < 24) return@forEach
            val key = ((r shr 4) shl 8) or ((g shr 4) shl 4) or (b shr 4)
            bins[key] = (bins[key] ?: 0) + 1
        }
        val dominant = bins.maxByOrNull { (key, count) ->
            val values = listOf((key shr 8) and 15, (key shr 4) and 15, key and 15)
            count * (.5f + (values.max() - values.min()) / 15f)
        }?.key ?: return null
        val source = Color(((dominant shr 8) and 15) / 15f, ((dominant shr 4) and 15) / 15f, (dominant and 15) / 15f)
        val background = lerp(Color(0xFF08090E), source, .10f)
        val surface = lerp(Color(0xFF12131A), source, .18f)
        var accent = source
        repeat(16) { if (contrast(accent, surface) < 4.8f) accent = lerp(accent, Color.White, .12f) }
        return CoverColors(accent, background, surface, lerp(accent, Color.White, .22f))
    }
}

@Composable
fun PlayerCoverTheme(colors: CoverColors?, content: @Composable () -> Unit) {
    val base = MaterialTheme.colorScheme
    val accent by animateColorAsState(colors?.accent ?: base.primary, tween(650), label = "coverAccent")
    val background by animateColorAsState(colors?.background ?: base.background, tween(650), label = "coverBackground")
    val surface by animateColorAsState(colors?.surface ?: base.surface, tween(650), label = "coverSurface")
    val secondary by animateColorAsState(colors?.secondary ?: base.tertiary, tween(650), label = "coverSecondary")
    val onAccent = if (contrast(Color.Black, accent) >= contrast(Color.White, accent)) Color.Black else Color.White
    MaterialTheme(colorScheme = base.copy(primary = accent, onPrimary = onAccent, secondary = accent,
        tertiary = secondary, primaryContainer = surface, background = background, surface = surface,
        surfaceContainerHigh = surface, surfaceVariant = surface, outlineVariant = accent.copy(alpha = .22f)), content = content)
}
