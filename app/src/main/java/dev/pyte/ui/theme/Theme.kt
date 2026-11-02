package dev.pyte.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val PyteAccent = Color(0xFFFF3B5C)
val PyteBackground = Color(0xFF0B0B0F)
val PyteSurface = Color(0xFF141418)
val PyteSurfaceHigh = Color(0xFF1E1E24)
val PyteTextPrimary = Color(0xFFF2F2F5)
val PyteTextMuted = Color(0xFFA0A0AB)

private val PyteDarkColors = darkColorScheme(
    primary = PyteAccent,
    onPrimary = Color.White,
    secondary = PyteAccent,
    background = PyteBackground,
    onBackground = PyteTextPrimary,
    surface = PyteSurface,
    onSurface = PyteTextPrimary,
    surfaceVariant = PyteSurfaceHigh,
    onSurfaceVariant = PyteTextMuted,
    surfaceContainer = PyteSurface,
    surfaceContainerHigh = PyteSurfaceHigh,
    outlineVariant = Color(0x14FFFFFF),
)

/** Dark-only by design: the product direction is a Spotify / YouTube Music style player. */
@Composable
fun PyteTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PyteDarkColors,
        content = content,
    )
}
