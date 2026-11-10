package dev.pyte.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape

/** Dark-only by design. Colors, type and shapes come from the approved Listening Space tokens. */
@Composable
fun PyteTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ListeningDesign.colors,
        typography = ListeningDesign.type,
        shapes = Shapes(
            small = RoundedCornerShape(ListeningSpace.item),
            medium = RoundedCornerShape(ListeningSpace.item + ListeningSpace.unit),
            large = ListeningComponents.cardShape,
            extraLarge = RoundedCornerShape(ListeningSpace.major),
        ),
        content = content,
    )
}
