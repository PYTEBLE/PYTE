package dev.pyte.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Semantic tokens shared by Compose, not per-screen accent guesses.
val PyteAccent = Color(0xFFC4A0FF)
val PyteOnAccent = Color(0xFF241039)
val PyteBackground = Color(0xFF09070F)
val PyteBackgroundScrim = PyteBackground.copy(alpha = 0.80f)
val PyteSurface = Color(0xFF14101E)
val PyteSurfaceHigh = Color(0xFF21182F)
val PyteTextPrimary = Color(0xFFF4EFFB)
val PyteTextMuted = Color(0xFFB7ACC8)
val PyteOutline = Color(0xFF3B2C4E)
val PyteBrandStart = Color(0xFF7423D5)
val PyteBrandEnd = Color(0xFFA02ADC)
val PyteOnBrand = Color(0xFFFFFFFF)
val PyteBrandGradient: Brush get() = Brush.linearGradient(listOf(PyteBrandStart, PyteBrandEnd))
