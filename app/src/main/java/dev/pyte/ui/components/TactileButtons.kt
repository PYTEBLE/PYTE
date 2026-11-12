package dev.pyte.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

/** Keep Material semantics/ripples and add the same interruptible press response everywhere. */
@Composable
internal fun TactileIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    androidx.compose.material3.IconButton(onClick = feedbackClick(onClick), modifier = modifier.pressMotion(source),
        enabled = enabled, colors = colors, interactionSource = source, content = content)
}

@Composable
internal fun TactileFilledIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: IconButtonColors = IconButtonDefaults.filledIconButtonColors(),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    androidx.compose.material3.FilledIconButton(onClick = feedbackClick(onClick), modifier = modifier.pressMotion(source),
        enabled = enabled, colors = colors, interactionSource = source, content = content)
}
