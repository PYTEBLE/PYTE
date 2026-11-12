package dev.pyte.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.pyte.playback.MixUiState
import dev.pyte.R
import androidx.compose.ui.res.stringResource

@Composable
fun MixAction(
    state: MixUiState,
    isPreview: Boolean,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onRestore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val buttonModifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
    val padding = PaddingValues(horizontal = 12.dp)
    Box(modifier) {
        when {
            state.loading -> FilledTonalButton(onClick = feedbackClick(onCancel), modifier = buttonModifier, shape = MaterialTheme.shapes.medium, contentPadding = padding) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Text(stringResource(R.string.cancel), Modifier.padding(start = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            state.title == null -> FilledTonalButton(onClick = feedbackClick(onStart), enabled = !isPreview, modifier = buttonModifier, shape = MaterialTheme.shapes.medium, contentPadding = padding) {
                Text(stringResource(R.string.mix_start), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            else -> Box(Modifier.fillMaxWidth()) {
                FilledTonalButton(onClick = feedbackClick { menuOpen = true }, modifier = buttonModifier, shape = MaterialTheme.shapes.medium, contentPadding = padding) {
                    Text(stringResource(R.string.mix_active), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.mix_new)) }, enabled = !isPreview,
                        onClick = feedbackClick { menuOpen = false; onStart() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.mix_restore)) },
                        onClick = feedbackClick { menuOpen = false; onRestore() })
                }
            }
        }
    }
}
