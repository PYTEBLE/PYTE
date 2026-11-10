package dev.pyte.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import dev.pyte.ui.components.Artwork
import dev.pyte.ui.components.HeroCoverFrame
import dev.pyte.ui.components.SongSwap
import dev.pyte.ui.components.SongMetadata
import dev.pyte.ui.components.songDirection
import dev.pyte.ui.components.FavoriteButton
import dev.pyte.ui.components.LivingPlayButton
import androidx.compose.runtime.SideEffect
import dev.pyte.ui.components.formatDuration
import dev.pyte.ui.viewmodels.PlayerViewModel
import androidx.compose.foundation.layout.widthIn
import dev.pyte.ui.components.CoverLight
import dev.pyte.ui.components.Reveal
import dev.pyte.ui.components.MixPanel
import dev.pyte.ui.components.AddToPlaylistDialog
import androidx.compose.ui.platform.LocalConfiguration
import dev.pyte.playback.PlayerUiState
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Surface
import androidx.compose.foundation.BorderStroke
import dev.pyte.ui.components.PlayerAtmosphere
import dev.pyte.ui.components.LocalSongActions
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.FilledTonalButton
import androidx.compose.ui.res.stringResource
import dev.pyte.R
import dev.pyte.ui.components.CoverProgress
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription

@Composable
fun NowPlayingScreen(
    onClose: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
    onDiscover: (dev.pyte.domain.Track) -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val mix by viewModel.mixState.collectAsStateWithLifecycle()
    val favoriteIds by viewModel.favoriteIds.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val save by viewModel.playlistSave.collectAsStateWithLifecycle()
    val track = state.current
    val configuration = LocalConfiguration.current
    val compact = configuration.screenHeightDp < 600 || configuration.fontScale > 1.3f
    val coverSize = if (compact) 260.dp else (configuration.screenHeightDp - 560).coerceIn(220, 360).dp
    var previousId by remember { mutableStateOf(track?.videoId) }
    val direction = remember(track?.videoId) { songDirection(previousId, state.currentIndex, state.queue, state.shuffle) }
    SideEffect { previousId = track?.videoId }
    var queueOpen by rememberSaveable { mutableStateOf(false) }
    var lyricsOpen by rememberSaveable { mutableStateOf(false) }
    var equalizerOpen by rememberSaveable { mutableStateOf(false) }
    val songActions = LocalSongActions.current
    if (equalizerOpen) EqualizerSheet(onDismiss = { equalizerOpen = false })

    if (lyricsOpen && track != null) {
        LyricsPlayerScreen(viewModel, onClose, onCover = { lyricsOpen = false })
        return
    }

    if (queueOpen) {
        QueueSheet(state, onDismiss = { queueOpen = false }, onPlay = viewModel::playIndex,
            onMove = viewModel::moveQueueItem, onRemove = viewModel::removeQueueItem,
            onAddToPlaylist = viewModel::selectForPlaylist)
    }
    if (songActions == null) save.track?.let { selected ->
        AddToPlaylistDialog(selected, playlists, save.saving, save.error, viewModel::dismissPlaylistSave,
            onChoose = { viewModel.saveToPlaylist(id = it) }, onCreate = { viewModel.saveToPlaylist(newTitle = it) })
    }

    Box(Modifier.fillMaxSize()) {
    PlayerAtmosphere(track, state.isPlaying)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = stringResource(R.string.close_player))
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (mix.title != null) "DEIN MIX" else "DEIN MOMENT", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary, letterSpacing = 3.sp)
                Text(mix.title ?: stringResource(R.string.now_playing), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelMedium)
            }
            IconButton(onClick = { queueOpen = true }) {
                Icon(Icons.Default.QueueMusic, contentDescription = stringResource(R.string.open_queue))
            }
        }

        if (track == null) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.nothing_playing), style = MaterialTheme.typography.titleLarge)
            }
            return@Column
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        CoverLight(state.isPlaying, Modifier.fillMaxWidth()) {
        Box(Modifier.align(Alignment.Center).widthIn(max = coverSize + 16.dp).fillMaxWidth().aspectRatio(1f)) {
            CoverProgress(if (state.durationMs > 0) state.positionMs.toFloat() / state.durationMs else 0f, Modifier.fillMaxSize())
        }
        HeroCoverFrame(track.videoId, Modifier.align(Alignment.Center).padding(horizontal = 12.dp, vertical = 24.dp)
                .widthIn(max = coverSize)
                .fillMaxWidth()
                .aspectRatio(1f)
                .shadow(32.dp, RoundedCornerShape(30.dp), ambientColor = MaterialTheme.colorScheme.primary, spotColor = MaterialTheme.colorScheme.primary)
                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .22f), RoundedCornerShape(30.dp))) { artworkModifier ->
            SongSwap(track, direction, artworkModifier) { shown ->
                Artwork(shown.thumbnailUrl, Modifier.fillMaxSize(), RoundedCornerShape(30.dp), shown.videoId, requestedPixels = 1200)
            }
        }
        }

        Spacer(Modifier.height(8.dp))

        Reveal(track.videoId, order = 1) {
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            SongMetadata(track, direction, Modifier.weight(1f))
            val isFav = track.videoId in favoriteIds
            FavoriteButton(isFav, { viewModel.toggleFavorite(track, !isFav) }, key = track.videoId)
        }
        }

        Spacer(Modifier.height(20.dp))

        if (compact) PlaybackDeck(state, viewModel)

        Reveal("player-actions", order = 2) {
            FlowRow(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.Center) {
                TextButton(onClick = { lyricsOpen = true }) { Text(stringResource(R.string.lyrics)) }
                TextButton(onClick = { equalizerOpen = true }) { Icon(Icons.Default.Equalizer, null, Modifier.size(18.dp)); Text(stringResource(R.string.sound_studio), Modifier.padding(start = 6.dp)) }
                TextButton(onClick = { onDiscover(track) }) { Text(stringResource(R.string.discover_similar)) }
            }
        }
        }

        if (!compact) PlaybackDeck(state, viewModel)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { viewModel.selectForPlaylist(track) }, modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                shape = MaterialTheme.shapes.medium) {
                Icon(Icons.Default.PlaylistAdd, null, Modifier.size(20.dp))
                Text(stringResource(R.string.add_to_playlist), Modifier.padding(start = 8.dp))
            }
            songActions?.let { actions -> IconButton(onClick = { actions.open(track) }) {
                Icon(Icons.Default.MoreVert, stringResource(R.string.song_options, track.title))
            } }
        }
        // Outside the scrolling content: reachable even on small screens.
        MixPanel(mix, state.isPreview, viewModel::startMix, viewModel::cancelMixLoading, viewModel::restoreMixQueue,
            Modifier.fillMaxWidth().padding(bottom = 12.dp),
            onSaveSong = null)
    }
    }
}

@Composable
private fun PlaybackDeck(state: PlayerUiState, viewModel: PlayerViewModel) {
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        SeekBar(state.positionMs, state.durationMs, viewModel::seekTo)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = viewModel::toggleShuffle) {
                Icon(
                    Icons.Default.Shuffle,
                    contentDescription = stringResource(R.string.shuffle),
                    tint = if (state.shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = viewModel::previous, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Default.SkipPrevious, contentDescription = stringResource(R.string.previous), modifier = Modifier.size(36.dp))
            }
            LivingPlayButton(state.isPlaying, viewModel::togglePlayPause)
            IconButton(onClick = viewModel::next, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Default.SkipNext, contentDescription = stringResource(R.string.next), modifier = Modifier.size(36.dp))
            }
            IconButton(onClick = viewModel::cycleRepeat) {
                val repeatIcon = if (state.repeat == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat
                Icon(
                    repeatIcon,
                    contentDescription = stringResource(R.string.repeat),
                    tint = if (state.repeat == Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                )
            }
        }

    }
}

@Composable
internal fun SeekBar(positionMs: Long, durationMs: Long, onSeek: (Long) -> Unit) {
    val seekLabel = stringResource(R.string.playback_position)
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }

    val fraction = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f
    val shown = if (dragging) dragFraction else fraction.coerceIn(0f, 1f)

    Column(modifier = Modifier.fillMaxWidth()) {
        Slider(
            modifier = Modifier.semantics { contentDescription = seekLabel },
            value = shown,
            onValueChange = {
                dragging = true
                dragFraction = it
            },
            onValueChangeFinished = {
                if (durationMs > 0) onSeek((dragFraction * durationMs).toLong())
                dragging = false
            },
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
            ),
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                formatDuration(if (dragging) (dragFraction * durationMs).toLong() else positionMs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                formatDuration(durationMs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
            )
        }
    }
}
