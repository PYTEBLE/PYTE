package dev.pyte.designreview

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.pyte.R
import dev.pyte.ui.components.ShimmerPlaceholder
import dev.pyte.ui.components.formatDuration
import dev.pyte.ui.theme.ListeningComponents as Components
import dev.pyte.ui.theme.ListeningSpace as Space
import dev.pyte.ui.theme.ListeningMotion
import dev.pyte.ui.theme.LocalLivingUi

@Composable
internal fun ReviewLibrary(playlists: List<ReviewPlaylist>, favorites: Set<Int>, state: String, onRetry: () -> Unit,
    onSearch: () -> Unit, onNew: () -> Unit, onOpen: (ReviewPlaylist) -> Unit, onPlay: (ReviewSong) -> Unit,
    onSave: (ReviewSong) -> Unit, onMore: (ReviewSong) -> Unit, modifier: Modifier) {
    var filter by remember { mutableStateOf("playlists") }
    var alphabetical by remember { mutableStateOf(false) }
    val large = LocalDensity.current.fontScale > 1.3f
    val visible = playlists.filter { filter != "offline" || it.offline }.let { if (alphabetical) it.sortedBy { p -> p.title } else it }
    LazyVerticalGrid(columns = if (large) GridCells.Fixed(1) else GridCells.Adaptive(152.dp), modifier = modifier.testTag("review-library"),
        contentPadding = PaddingValues(start = Space.content, end = Space.content, bottom = Space.section),
        verticalArrangement = Arrangement.spacedBy(Space.item), horizontalArrangement = Arrangement.spacedBy(Space.item)) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(Space.tight)) {
                Text(stringResource(R.string.library), style = MaterialTheme.typography.headlineLarge)
                ReviewNotice(stringResource(R.string.review_library_subtitle))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.tight)) {
                    listOf("playlists" to R.string.review_collections, "favorites" to R.string.favorites, "offline" to R.string.review_offline).forEach { (key, label) ->
                        FilterChip(filter == key, { filter = key }, label = { Text(stringResource(label)) }, modifier = Modifier.testTag("review-filter-$key"))
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(if (filter == "favorites") R.string.favorites else R.string.review_your_collections),
                        Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    ReviewIcon(Icons.Default.Sort, stringResource(R.string.review_sort), { alphabetical = !alphabetical })
                    ReviewIcon(Icons.Default.Add, stringResource(R.string.review_new_playlist), onNew, Modifier.testTag("review-new"))
                }
                ReviewNotice(stringResource(if (alphabetical) R.string.review_sort_title else R.string.review_sort_recent))
                if (state == "loading") ReviewNotice(stringResource(R.string.loading))
            }
        }
        when (state) {
            "loading" -> items(4) {
                Surface(Modifier.fillMaxWidth().height(196.dp), shape = Components.cardShape,
                    color = MaterialTheme.colorScheme.surface) { ShimmerPlaceholder(Modifier.fillMaxSize()) }
            }
            "empty", "error" -> item(span = { GridItemSpan(maxLineSpan) }) {
                ReviewState(state, if (state == "error") onRetry else onSearch)
            }
            else -> if (filter == "favorites") {
                if (demoSongs.none { it.id in favorites }) item(span = { GridItemSpan(maxLineSpan) }) { ReviewState("empty", onSearch) }
                items(demoSongs.filter { it.id in favorites }, key = { "song:${it.id}" }, span = { GridItemSpan(maxLineSpan) }) { song ->
                    ReviewSongRow(song, { onPlay(song) }, { onSave(song) }, { onMore(song) })
                }
            } else items(visible, key = { it.id }) { playlist -> ReviewCollection(playlist) { onOpen(playlist) } }
        }
        item(span = { GridItemSpan(maxLineSpan) }) { ReviewNotice(stringResource(R.string.review_intro)) }
    }
}

@Composable
private fun ReviewState(state: String, onAction: () -> Unit) {
    val error = state == "error"
    Surface(color = MaterialTheme.colorScheme.surface, shape = Components.cardShape, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Space.section), verticalArrangement = Arrangement.spacedBy(Space.item)) {
            Icon(if (error) Icons.Default.CloudOff else Icons.Default.LibraryMusic, null,
                tint = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            Text(stringResource(if (error) R.string.review_error_title else R.string.review_empty_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(if (error) R.string.review_error_body else R.string.review_empty_body),
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            ReviewPrimary(stringResource(if (error) R.string.retry else R.string.search),
                if (error) Icons.Default.Refresh else Icons.Default.Search, onAction)
        }
    }
}

@Composable
internal fun ReviewSearch(query: String, onQuery: (String) -> Unit, songs: List<ReviewSong>, onPlay: (ReviewSong) -> Unit,
    onSave: (ReviewSong) -> Unit, onMore: (ReviewSong) -> Unit, modifier: Modifier) {
    val visible = songs.filter { "${it.title} ${it.artist}".contains(query, ignoreCase = true) }
    LazyColumn(modifier.testTag("review-search"), contentPadding = PaddingValues(horizontal = Space.content, vertical = Space.item),
        verticalArrangement = Arrangement.spacedBy(Space.item)) {
        item { Text(stringResource(R.string.search), style = MaterialTheme.typography.headlineLarge) }
        item { ReviewNotice(stringResource(R.string.review_search_hint)) }
        item { OutlinedTextField(query, onQuery, label = { Text(stringResource(R.string.search_online_placeholder)) },
            leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true, shape = Components.buttonShape, modifier = Modifier.fillMaxWidth()) }
        if (visible.isEmpty()) item { Text(stringResource(R.string.search_no_results), style = MaterialTheme.typography.titleLarge) }
        items(visible, key = { it.id }) { song ->
            ReviewSongRow(song, { onPlay(song) }, { onSave(song) }, { onMore(song) })
        }
        item { ReviewNotice(stringResource(R.string.review_intro)) }
    }
}

@Composable
internal fun ReviewStart(song: ReviewSong, onContinue: () -> Unit, onSearch: () -> Unit, onSave: (ReviewSong) -> Unit,
    onMore: (ReviewSong) -> Unit, onPlay: (ReviewSong) -> Unit, modifier: Modifier) {
    LazyColumn(modifier, contentPadding = PaddingValues(Space.content), verticalArrangement = Arrangement.spacedBy(Space.section)) {
        item { Text(stringResource(R.string.review_start_title), style = MaterialTheme.typography.headlineLarge) }
        item { Surface(shape = Components.cardShape, color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(Space.content), verticalArrangement = Arrangement.spacedBy(Space.item)) {
                Text(stringResource(R.string.review_continue), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.item)) {
                    ReviewCover(song.id, song.cover, Modifier.size(80.dp), lettering = false)
                    Column(Modifier.weight(1f)) {
                        Text(song.title, style = MaterialTheme.typography.titleLarge)
                        ReviewNotice(song.artist)
                    }
                }
                ReviewPrimary(stringResource(R.string.review_continue), Icons.Default.PlayArrow, onContinue, Modifier.fillMaxWidth())
            }
        } }
        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.review_start_hint), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            ReviewIcon(Icons.Default.Search, stringResource(R.string.search), onSearch)
        } }
        items(demoSongs, key = { it.id }) { item -> ReviewSongRow(item, { onPlay(item) }, { onSave(item) }, { onMore(item) }) }
        item { ReviewNotice(stringResource(R.string.review_intro)) }
    }
}

@Composable
internal fun ReviewMiniPlayer(song: ReviewSong, playing: Boolean, progress: Float, onOpen: () -> Unit, onToggle: () -> Unit,
    onSave: () -> Unit, onNext: () -> Unit) {
    Surface(shape = Components.buttonShape, color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.padding(horizontal = Space.item, vertical = Space.tight).testTag("review-mini")) {
        Column {
            LinearProgressIndicator(progress = { progress }, Modifier.fillMaxWidth().height(2.dp),
                color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.surface)
            Row(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(start = Space.item, end = Space.unit, top = Space.tight, bottom = Space.tight),
                verticalAlignment = Alignment.CenterVertically) {
                ReviewCover(song.id, song.cover, Modifier.size(48.dp), lettering = false)
                Column(Modifier.weight(1f).padding(horizontal = Space.item)) {
                    Text(song.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    ReviewNotice(song.artist)
                }
                ReviewIcon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                    stringResource(if (playing) R.string.pause else R.string.play), onToggle)
                ReviewIcon(Icons.AutoMirrored.Filled.PlaylistAdd, stringResource(R.string.add_to_playlist), onSave, tint = MaterialTheme.colorScheme.primary)
                ReviewIcon(Icons.Default.SkipNext, stringResource(R.string.review_next), onNext)
            }
        }
    }
}

@Composable
internal fun ReviewPlayer(song: ReviewSong, sourceTitle: String, playing: Boolean, progress: Float, onToggle: () -> Unit, onSeek: (Float) -> Unit,
    onPrevious: () -> Unit, onNext: () -> Unit, onSave: () -> Unit, onMore: () -> Unit, onQueue: () -> Unit, modifier: Modifier) {
    val motion = LocalLivingUi.current
    val seekLabel = stringResource(R.string.playback_position)
    BoxWithConstraints(modifier.testTag("review-player")) {
        val compact = maxHeight < 620.dp || LocalDensity.current.fontScale > 1.3f
        Column(Modifier.fillMaxSize().padding(horizontal = Space.content).navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.review_player_badge), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    ReviewNotice(stringResource(R.string.review_player_source, sourceTitle))
                }
                ReviewIcon(Icons.Default.MoreVert, stringResource(R.string.song_options, song.title), onMore, Modifier.testTag("review-more"))
            }
            Column(Modifier.weight(1f).verticalScroll(androidx.compose.foundation.rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(Space.item))
                AnimatedContent(song, transitionSpec = {
                    fadeIn(tween(motion.duration(ListeningMotion.contentMs))) togetherWith fadeOut(tween(motion.duration(ListeningMotion.pressMs)))
                }, label = "reviewArtwork") { shown ->
                    ReviewCover(shown.id, shown.cover, Modifier.widthIn(max = if (compact) 200.dp else 300.dp).fillMaxWidth().aspectRatio(1f))
                }
                Spacer(Modifier.height(Space.section))
                Text(song.title, Modifier.fillMaxWidth(), style = MaterialTheme.typography.headlineMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Text(song.artist, Modifier.fillMaxWidth().padding(top = Space.unit), style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                ReviewNotice(stringResource(R.string.review_no_audio), Modifier.fillMaxWidth().padding(top = Space.item))
                Spacer(Modifier.height(Space.item))
            }
            Slider(value = progress, onValueChange = onSeek, modifier = Modifier.fillMaxWidth().semantics { contentDescription = seekLabel })
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                ReviewNotice(formatDuration((progress * 252000).toLong()))
                ReviewNotice("4:12")
            }
            Row(Modifier.fillMaxWidth().padding(vertical = Space.tight), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                ReviewIcon(Icons.Default.SkipPrevious, stringResource(R.string.review_previous), onPrevious)
                FilledIconButton(onClick = onToggle, modifier = Modifier.size(if (compact) 56.dp else 72.dp).testTag("review-play"), shape = CircleShape) {
                    Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                        stringResource(if (playing) R.string.pause else R.string.play), Modifier.size(32.dp))
                }
                ReviewIcon(Icons.Default.SkipNext, stringResource(R.string.review_next), onNext)
            }
            Row(Modifier.fillMaxWidth().padding(bottom = Space.item), horizontalArrangement = Arrangement.spacedBy(Space.tight), verticalAlignment = Alignment.CenterVertically) {
                ReviewPrimary(stringResource(R.string.add_to_playlist), Icons.AutoMirrored.Filled.PlaylistAdd, onSave,
                    Modifier.weight(1f).testTag("review-save"))
                OutlinedIconButton(onClick = onQueue, modifier = Modifier.size(Components.buttonHeight), shape = Components.buttonShape,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
                    Icon(Icons.AutoMirrored.Filled.QueueMusic, stringResource(R.string.queue))
                }
            }
        }
    }
}

@Composable
internal fun ReviewPlaylistScreen(playlist: ReviewPlaylist, onPlay: (ReviewSong) -> Unit, onSave: (ReviewSong) -> Unit,
    onMore: (ReviewSong) -> Unit, onSearch: () -> Unit, modifier: Modifier) {
    LazyColumn(modifier, contentPadding = PaddingValues(Space.content), verticalArrangement = Arrangement.spacedBy(Space.item)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(Space.item)) {
                ReviewCover(playlist.artwork, coverLettering(playlist.title),
                    Modifier.widthIn(max = 240.dp).fillMaxWidth().aspectRatio(1f))
                Text(playlist.title, style = MaterialTheme.typography.headlineLarge)
                ReviewNotice(stringResource(R.string.review_playlist_sample))
                if (playlist.count > 0) ReviewPrimary(stringResource(R.string.play), Icons.Default.PlayArrow,
                    { onPlay(demoSongs[playlist.artwork]) }, Modifier.fillMaxWidth())
            }
        }
        if (playlist.count == 0) item { ReviewState("empty", onSearch) }
        else items(demoSongs, key = { it.id }) { song -> ReviewSongRow(song, { onPlay(song) }, { onSave(song) }, { onMore(song) }) }
        item { ReviewNotice(stringResource(R.string.review_intro)) }
    }
}
