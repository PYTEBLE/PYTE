package dev.pyte.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.pyte.R
import dev.pyte.data.remote.CatalogPlaylist
import dev.pyte.domain.PlaylistSummary
import dev.pyte.ui.viewmodels.CatalogImportState
import dev.pyte.ui.viewmodels.CatalogState

@Composable
fun PlaylistCatalogSection(state: CatalogState, savedIds: Set<String>, onRetry: () -> Unit, onSelect: (CatalogPlaylist) -> Unit) {
    LazyVerticalGrid(
        columns = if (LocalDensity.current.fontScale > 1.3f) GridCells.Fixed(1) else GridCells.Adaptive(160.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) { playlistCatalogItems(state, savedIds, onRetry, onSelect) }
}

/** Add each card to the screen's grid: one vertical scroll surface, not a nested shelf. */
fun LazyGridScope.playlistCatalogItems(state: CatalogState, savedIds: Set<String>, onRetry: () -> Unit, onSelect: (CatalogPlaylist) -> Unit) {
    val charts = state.playlists.filter { it.charts }
    val tips = state.playlists.filterNot { it.charts }
    item(key = "catalog-heading", span = { GridItemSpan(maxLineSpan) }) {
        CatalogHeading(
            title = stringResource(if (charts.isNotEmpty() || state.loading) R.string.catalog_charts_heading else R.string.catalog_tips_heading),
            subtitle = stringResource(if (charts.isNotEmpty() || state.loading) R.string.catalog_charts_body else R.string.catalog_tips_body),
            onRetry = onRetry, loading = state.loading,
        )
    }
    if (state.loading) item(key = "catalog-loading", span = { GridItemSpan(maxLineSpan) }) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(stringResource(R.string.catalog_loading), style = MaterialTheme.typography.bodyMedium)
        }
    }
    if (state.error || state.partial) item(key = "catalog-status", span = { GridItemSpan(maxLineSpan) }) {
        DiscoveryNotice(stringResource(if (state.error) R.string.catalog_error else R.string.discovery_partial), onRetry)
    }
    charts.firstOrNull()?.let { playlist ->
        item(key = "catalog:${playlist.id}", span = { GridItemSpan(maxLineSpan) }) {
            FeaturedChartCard(playlist, playlist.id in savedIds) { onSelect(playlist) }
        }
    }
    items(charts.drop(1), key = { "catalog:${it.id}" }) { playlist ->
        CatalogGridCard(playlist, playlist.id in savedIds) { onSelect(playlist) }
    }
    if (tips.isNotEmpty()) {
        if (charts.isNotEmpty()) item(key = "catalog-tips-heading", span = { GridItemSpan(maxLineSpan) }) {
            CatalogHeading(stringResource(R.string.catalog_tips_heading), stringResource(R.string.catalog_tips_body))
        }
        items(tips, key = { "catalog:${it.id}" }) { playlist ->
            CatalogGridCard(playlist, playlist.id in savedIds) { onSelect(playlist) }
        }
    }
}

@Composable
private fun CatalogHeading(title: String, subtitle: String, onRetry: (() -> Unit)? = null, loading: Boolean = false) {
    Column(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.catalog_source), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary)
            onRetry?.let { TextButton(onClick = feedbackClick(it), enabled = !loading) { Text(stringResource(R.string.discovery_refresh)) } }
        }
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun FeaturedChartCard(playlist: CatalogPlaylist, saved: Boolean, onClick: () -> Unit) {
    val label = stringResource(R.string.catalog_open, playlist.title)
    ElevatedCard(onClick = feedbackClick(onClick), modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
        shape = RoundedCornerShape(28.dp), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Box(Modifier.fillMaxWidth().heightIn(min = 228.dp)) {
            Artwork(playlist.thumbnailUrl, Modifier.matchParentSize(), shape = RoundedCornerShape(28.dp))
            Box(Modifier.matchParentSize().background(Brush.horizontalGradient(listOf(
                Color.Black.copy(alpha = .92f), Color.Black.copy(alpha = .78f), Color.Black.copy(alpha = .60f),
            ))))
            Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(if (saved) R.string.catalog_saved else R.string.catalog_charts),
                    style = MaterialTheme.typography.labelLarge, color = Color.White)
                Text(playlist.title, Modifier.padding(end = 24.dp), style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold, color = Color.White, maxLines = 4, overflow = TextOverflow.Ellipsis)
                Text(playlist.subtitle, style = MaterialTheme.typography.bodyMedium, color = Color.White,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.catalog_explore), style = MaterialTheme.typography.labelLarge, color = Color.White)
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(20.dp), tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun CatalogGridCard(playlist: CatalogPlaylist, saved: Boolean, onClick: () -> Unit) {
    val label = stringResource(R.string.catalog_open, playlist.title)
    ElevatedCard(onClick = feedbackClick(onClick), modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
        shape = RoundedCornerShape(22.dp), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Artwork(playlist.thumbnailUrl, Modifier.fillMaxWidth().aspectRatio(1f), shape = RoundedCornerShape(0.dp))
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(if (saved) R.string.catalog_saved else if (playlist.charts) R.string.catalog_charts else R.string.catalog_editorial),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Text(playlist.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                minLines = 2, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text(playlist.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.catalog_explore), Modifier.weight(1f), style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary)
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
fun LibraryCollectionCard(playlist: PlaylistSummary, onClick: () -> Unit, onArtworkError: () -> Unit) {
    ElevatedCard(onClick = feedbackClick(onClick), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Artwork(playlist.thumbnailUrl, Modifier.size(104.dp), shape = RoundedCornerShape(16.dp), onArtworkError = onArtworkError)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(playlist.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                    maxLines = 3, overflow = TextOverflow.Ellipsis)
                Text(pluralStringResource(R.plurals.songs_count, playlist.trackCount, playlist.trackCount),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
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
        confirmButton = { TextButton(onClick = feedbackClick(onConfirm), enabled = !state.importing) { Text(stringResource(R.string.catalog_import_confirm)) } },
        dismissButton = { TextButton(onClick = feedbackClick(onDismiss), enabled = !state.importing) { Text(stringResource(R.string.cancel)) } },
    )
}
