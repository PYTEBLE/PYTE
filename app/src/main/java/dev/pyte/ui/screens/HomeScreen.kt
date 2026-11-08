package dev.pyte.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import dev.pyte.ui.components.EmptyState
import dev.pyte.ui.components.PlaylistCard
import dev.pyte.ui.components.Artwork
import dev.pyte.ui.components.ScreenHeading
import dev.pyte.ui.components.LibraryWelcome
import dev.pyte.ui.viewmodels.HomeViewModel
import dev.pyte.ui.viewmodels.ImportUiState
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.testTag
import dev.pyte.ui.components.Reveal
import dev.pyte.ui.theme.LocalLivingUi
import androidx.compose.animation.core.tween

@Composable
fun HomeScreen(
    onOpenPlaylist: (String) -> Unit,
    contentPadding: PaddingValues,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val motion = LocalLivingUi.current
    val pendingLink by viewModel.pendingLink.collectAsStateWithLifecycle()
    val recent by viewModel.recentTracks.collectAsStateWithLifecycle()
    var showImport by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(pendingLink) {
        if (pendingLink != null) showImport = true
    }

    Scaffold(
        modifier = Modifier.padding(contentPadding),
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            if (playlists.isNotEmpty() || recent.isNotEmpty()) {
            ExtendedFloatingActionButton(
                onClick = { showImport = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Import", fontWeight = FontWeight.SemiBold) },
            )
            }
        },
    ) { inner ->
        if (playlists.isEmpty() && recent.isEmpty()) {
            LibraryWelcome(
                onImport = { showImport = true },
                modifier = Modifier.padding(inner).statusBarsPadding(),
            )
        } else {
            Column(
                modifier = Modifier
                    .padding(inner)
                    .statusBarsPadding(),
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 120.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    item(key = "library-hero", span = { GridItemSpan(maxLineSpan) }) {
                        Reveal("library-hero") {
                            Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = .20f),
                                MaterialTheme.colorScheme.tertiary.copy(alpha = .06f))), RoundedCornerShape(30.dp)).padding(24.dp)) {
                                Text("YOUR MUSIC. YOUR SPACE.", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary, letterSpacing = 2.sp)
                                Spacer(Modifier.height(16.dp))
                                Text("Find your\nnext feeling.", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(12.dp))
                                Text("${playlists.size} playlists. Endless possibilities.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                TextButton(onClick = { showImport = true }) { Icon(Icons.Default.Add, null); Text("  Grow your collection") }
                            }
                        }
                    }
                    if (recent.isNotEmpty()) {
                        item(key = "recent", span = { GridItemSpan(maxLineSpan) }) {
                            Column {
                                Text("Recently played", style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp))
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp)) {
                                    itemsIndexed(recent, key = { _, track -> track.videoId }) { index, track ->
                                        Column(Modifier.width(156.dp).clickable { viewModel.playRecent(recent, index) }.padding(bottom = 16.dp)) {
                                            Artwork(track.thumbnailUrl, Modifier.fillMaxWidth().aspectRatio(1f),
                                                shape = RoundedCornerShape(24.dp), videoId = track.videoId)
                                            Spacer(Modifier.height(8.dp))
                                            Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                                style = MaterialTheme.typography.bodyMedium)
                                            Text(track.artist, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    item(key = "collections-label", span = { GridItemSpan(maxLineSpan) }) {
                        Text("Your collections", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                    }
                    items(playlists, key = { it.id }) { playlist ->
                        Reveal(playlist.id, modifier = Modifier.animateItem(
                            fadeInSpec = if (motion.animate) tween(motion.duration(220)) else null,
                            placementSpec = if (motion.animate) tween(motion.duration(320)) else null,
                            fadeOutSpec = if (motion.animate) tween(motion.duration(180)) else null,
                        )) {
                            PlaylistCard(playlist = playlist, onClick = { onOpenPlaylist(playlist.id) })
                        }
                    }
                }
            }
        }
    }

    if (showImport) {
        ImportSheet(
            initialLink = pendingLink.orEmpty(),
            onDismiss = {
                showImport = false
                viewModel.dismissImport()
            },
            onImport = { link ->
                viewModel.importPlaylist(link) { id ->
                    showImport = false
                    onOpenPlaylist(id)
                }
            },
            importState = viewModel.importState,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ImportSheet(
    initialLink: String,
    onDismiss: () -> Unit,
    onImport: (String) -> Unit,
    importState: kotlinx.coroutines.flow.StateFlow<ImportUiState>,
) {
    var link by rememberSaveable { mutableStateOf(initialLink) }
    val state by importState.collectAsStateWithLifecycle()
    val loading = state is ImportUiState.Loading

    ModalBottomSheet(onDismissRequest = { if (!loading) onDismiss() }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            Text("Import a playlist", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Paste a public YouTube Music or YouTube playlist link.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = link,
                onValueChange = { link = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !loading,
                placeholder = { Text("https://music.youtube.com/playlist?list=…") },
                leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
                isError = state is ImportUiState.Error,
                supportingText = {
                    val error = state as? ImportUiState.Error
                    if (error != null) Text(error.message, color = MaterialTheme.colorScheme.error)
                },
            )
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss, enabled = !loading) { Text("Cancel") }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { onImport(link) },
                    enabled = link.isNotBlank() && !loading,
                ) {
                    if (loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp).testTag("playlistImportSpinner"),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Importiere …")
                    } else {
                        Text("Import")
                    }
                }
            }
        }
    }
}
