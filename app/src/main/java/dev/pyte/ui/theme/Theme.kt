package dev.pyte.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp

private val PyteDarkColors = darkColorScheme(
    primary = PyteAccent,
    onPrimary = PyteOnAccent,
    primaryContainer = PyteSurfaceHigh,
    onPrimaryContainer = PyteAccent,
    secondary = PyteAccent,
    onSecondary = PyteOnAccent,
    secondaryContainer = PyteSurfaceHigh,
    onSecondaryContainer = PyteAccent,
    background = PyteBackground,
    onBackground = PyteTextPrimary,
    surface = PyteSurface,
    onSurface = PyteTextPrimary,
    surfaceVariant = PyteSurfaceHigh,
    onSurfaceVariant = PyteTextMuted,
    surfaceContainer = PyteSurface,
    surfaceContainerHigh = PyteSurfaceHigh,
    surfaceContainerHighest = PyteSurfaceHigh,
    surfaceContainerLow = PyteSurface,
    surfaceContainerLowest = PyteBackground,
    surfaceTint = PyteAccent,
    outline = PyteOutline,
    outlineVariant = PyteOutline.copy(alpha = 0.65f),
    inverseSurface = PyteSurfaceHigh,
    inverseOnSurface = PyteTextPrimary,
)

private val PyteTypography = Typography(
    headlineLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-1).sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 29.sp, lineHeight = 36.sp, letterSpacing = (-0.7).sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 25.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 24.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
)

/** Dark-only by design: the product direction is a Spotify / YouTube Music style player. */
@Composable
fun PyteTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PyteDarkColors,
        typography = PyteTypography,
        shapes = Shapes(
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(18.dp),
            large = RoundedCornerShape(24.dp),
            extraLarge = RoundedCornerShape(30.dp),
        ),
        content = content,
    )
}
