package dev.pyte.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Candidate primitives. Existing production theme is intentionally unchanged until approval. */
internal object ListeningPalette {
    val ink = Color(0xFF0D0C10)
    val charcoal = Color(0xFF18161D)
    val raisedCharcoal = Color(0xFF24212B)
    val lavender = PyteAccent
    val onLavender = PyteOnAccent
    val paper = PyteTextPrimary
    val muted = PyteTextMuted
    val outline = Color(0xFF514A5D)
    val mint = Color(0xFFB1D9BF)
    val error = Color(0xFFFFB4AB)
    val errorSurface = Color(0xFF432722)
    val violet = Color(0xFF665090)
    val peach = Color(0xFFCE9878)
    val ocean = Color(0xFF457C87)
}

/** Semantic roles, shared by all three review screens. Not a per-screen palette. */
object ListeningDesign {
    val colors = darkColorScheme(
        primary = ListeningPalette.lavender, onPrimary = ListeningPalette.onLavender,
        primaryContainer = ListeningPalette.raisedCharcoal, onPrimaryContainer = ListeningPalette.paper,
        secondary = ListeningPalette.mint, onSecondary = ListeningPalette.ink,
        secondaryContainer = ListeningPalette.raisedCharcoal, onSecondaryContainer = ListeningPalette.paper,
        tertiary = ListeningPalette.peach, onTertiary = ListeningPalette.ink,
        background = ListeningPalette.ink, onBackground = ListeningPalette.paper,
        surface = ListeningPalette.charcoal, onSurface = ListeningPalette.paper,
        surfaceVariant = ListeningPalette.raisedCharcoal, onSurfaceVariant = ListeningPalette.muted,
        surfaceContainerLowest = ListeningPalette.ink, surfaceContainerLow = ListeningPalette.charcoal,
        surfaceContainer = ListeningPalette.charcoal, surfaceContainerHigh = ListeningPalette.raisedCharcoal,
        surfaceContainerHighest = ListeningPalette.raisedCharcoal,
        outline = ListeningPalette.outline, outlineVariant = ListeningPalette.outline,
        error = ListeningPalette.error, onError = ListeningPalette.ink,
        errorContainer = ListeningPalette.errorSurface, onErrorContainer = ListeningPalette.error,
        inverseSurface = ListeningPalette.paper, inverseOnSurface = ListeningPalette.ink,
    )
    val type = Typography(
        headlineLarge = TextStyle(fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp),
        headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.6).sp),
        titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
        titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
        bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
        bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
        bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
        labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
        labelSmall = TextStyle(fontSize = 10.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold, letterSpacing = .8.sp),
    )
}

object ListeningSpace {
    val unit = 4.dp
    val tight = unit * 2
    val item = unit * 3
    val content = unit * 5
    val section = unit * 6
    val major = unit * 8
}

/** Component layer aliases semantic spacing/type/colors instead of inventing screen values. */
object ListeningComponents {
    val touchTarget = ListeningSpace.unit * 12
    val buttonHeight = ListeningSpace.unit * 14
    val songHeight = ListeningSpace.unit * 19
    val cardShape = RoundedCornerShape(ListeningSpace.section)
    val buttonShape = RoundedCornerShape(ListeningSpace.item + ListeningSpace.unit)
    val sheetShape = RoundedCornerShape(topStart = ListeningSpace.major, topEnd = ListeningSpace.major)
    val artworkColors = listOf(ListeningPalette.violet, ListeningPalette.ocean, ListeningPalette.peach)
}

object ListeningMotion {
    const val pressMs = 140
    const val contentMs = 240
    const val expansionMs = 360
}
