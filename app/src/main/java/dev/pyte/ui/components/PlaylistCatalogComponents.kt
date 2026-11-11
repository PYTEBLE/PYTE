package dev.pyte.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.pyte.R
import dev.pyte.data.remote.CatalogPlaylist
import dev.pyte.ui.viewmodels.CatalogImportState
import dev.pyte.ui.viewmodels.CatalogState

@Composable
fun PlaylistCatalogSection(state: CatalogState, savedIds: Set<String>, onRetry: () -> Unit, onSelect: (CatalogPlaylist) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(stringResource(R.string.catalog_heading), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            TextButton(onClick = onRetry, enabled = !state.loading) { Text(stringResource(R.string.discovery_refresh)) }
        }
        Text(stringResource(R.string.catalog_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        when {
            state.loading -> {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(stringResource(R.string.catalog_loading), style = MaterialTheme.typography.bodyMedium)
            }
            state.error -> DiscoveryNotice(stringResource(R.string.catalog_error), onRetry)
        }
        if (state.partial) DiscoveryNotice(stringResource(R.string.discovery_partial), onRetry)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 8.dp)) {
            items(state.playlists, key = { it.id }) { playlist ->
                val label = stringResource(R.string.catalog_open, playlist.title)
                ElevatedCard(onClick = { onSelect(playlist) }, shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    modifier = Modifier.width(188.dp).semantics { contentDescription = label }) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Artwork(playlist.thumbnailUrl, Modifier.fillMaxWidth().aspectRatio(1f), shape = RoundedCornerShape(14.dp))
                        Text(stringResource(if (playlist.id in savedIds) R.string.catalog_saved else if (playlist.charts) R.string.catalog_charts else R.string.catalog_editorial),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Text(playlist.title, style = MaterialTheme.typography.titleSmall, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(playlist.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
fun PlaylistImportDialog(state: CatalogImportState, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val playlist = state.selected ?: return
    AlertDialog(onDismissRequest = { if (!state.importing) onDismiss() },
        title = { Text(stringResource(R.string.catalog_import_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.catalog_import_body, playlist.title))
                if (state.importing) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(stringResource(R.string.catalog_import_loading))
                }
                if (state.error) Text(stringResource(R.string.catalog_import_error), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { TextButton(onClick = onConfirm, enabled = !state.importing) { Text(stringResource(R.string.catalog_import_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !state.importing) { Text(stringResource(R.string.cancel)) } },
    )
}
