package dev.pyte.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import dev.pyte.R
import dev.pyte.domain.Track
import dev.pyte.playback.MixUiState
import dev.pyte.playback.PlayerUiState
import dev.pyte.ui.components.AddToPlaylistDialog
import dev.pyte.ui.components.Artwork
import dev.pyte.ui.components.CoverLight
import dev.pyte.ui.components.CoverProgress
import dev.pyte.ui.components.FavoriteButton
import dev.pyte.ui.components.HeroCoverFrame
import dev.pyte.ui.components.LivingPlayButton
import dev.pyte.ui.components.LocalSongActions
import dev.pyte.ui.components.MixAction
import dev.pyte.ui.components.PlayerAtmosphere
import dev.pyte.ui.components.Reveal
import dev.pyte.ui.components.SongActions
import dev.pyte.ui.components.SongMetadata
import dev.pyte.ui.components.SongSwap
import dev.pyte.ui.components.formatDuration
import dev.pyte.ui.components.songDirection
import dev.pyte.ui.theme.LocalLivingUi
import dev.pyte.ui.viewmodels.PlayerViewModel

private enum class PlayerPanel(val label: Int, val weight: Float) {
    COVER(R.string.artwork, 1f),
    LYRICS(R.string.lyrics, 1f),
    QUEUE(R.string.queue, 1.5f),
    SOUND(R.string.sound_studio, 1.4f),
}

@Composable
fun NowPlayingScreen(
    onClose: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
    onDiscover: (Track) -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val mix by viewModel.mixState.collectAsStateWithLifecycle()
    val favoriteIds by viewModel.favoriteIds.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val save by viewModel.playlistSave.collectAsStateWithLifecycle()
    val track = state.current
    val motion = LocalLivingUi.current
    var previousId by remember { mutableStateOf(track?.videoId) }
    val direction = remember(track?.videoId) { songDirection(previousId, state.currentIndex, state.queue, state.shuffle) }
    SideEffect { previousId = track?.videoId }
    var panel by rememberSaveable { mutableStateOf(PlayerPanel.COVER) }
    val songActions = LocalSongActions.current
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
            PlayerHeader(mix.title, onClose)
            if (track == null) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.nothing_playing), style = MaterialTheme.typography.titleLarge)
                }
                return@Column
            }

            PlayerSegments(panel) { panel = it }
            Crossfade(
                targetState = panel,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                animationSpec = tween(if (motion.animate) motion.duration(220) else 0),
                label = "playerPanel",
            ) { shown ->
                when (shown) {
                    PlayerPanel.COVER -> CoverPanel(track, state, direction, Modifier.fillMaxSize())
                    PlayerPanel.LYRICS -> LyricsPanel(viewModel, track, state, Modifier.fillMaxSize())
                    PlayerPanel.QUEUE -> QueueContent(state, viewModel::playIndex, viewModel::moveQueueItem,
                        viewModel::removeQueueItem, viewModel::selectForPlaylist, Modifier.fillMaxSize(), fillHeight = true)
                    PlayerPanel.SOUND -> EqualizerPanel(Modifier.fillMaxSize())
                }
            }

            Reveal(track.videoId, order = 1) {
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    SongMetadata(track, direction, Modifier.weight(1f))
                    val isFav = track.videoId in favoriteIds
                    FavoriteButton(isFav, { viewModel.toggleFavorite(track, !isFav) }, key = track.videoId)
                }
            }
            PlaybackDeck(state, viewModel)
            PlayerActionBar(track, mix, state.isPreview, viewModel, onDiscover, songActions)
        }
    }
}

@Composable
private fun PlayerHeader(mixTitle: String?, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onClose) {
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = stringResource(R.string.close_player))
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (mixTitle != null) "DEIN MIX" else "DEIN MOMENT", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary, letterSpacing = 3.sp)
            Text(mixTitle ?: stringResource(R.string.now_playing), maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.size(48.dp))
    }
}

@Composable
private fun PlayerSegments(selected: PlayerPanel, onSelect: (PlayerPanel) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = .8f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        PlayerPanel.entries.forEach { panel ->
            val isSelected = panel == selected
            Box(
                Modifier.weight(panel.weight).heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = .22f) else Color.Transparent)
                    .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(panel) })
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(panel.label),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CoverPanel(track: Track, state: PlayerUiState, direction: Int, modifier: Modifier) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val cover = minOf(maxWidth, maxHeight).coerceAtMost(360.dp)
        CoverLight(state.isPlaying, Modifier.fillMaxSize()) {
            Box(Modifier.align(Alignment.Center).size(cover + 16.dp)) {
                CoverProgress(if (state.durationMs > 0) state.positionMs.toFloat() / state.durationMs else 0f, Modifier.fillMaxSize())
            }
            HeroCoverFrame(track.videoId, Modifier.align(Alignment.Center).size(cover)
                .shadow(32.dp, RoundedCornerShape(30.dp), ambientColor = MaterialTheme.colorScheme.primary, spotColor = MaterialTheme.colorScheme.primary)
                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .22f), RoundedCornerShape(30.dp))) { artworkModifier ->
                SongSwap(track, direction, artworkModifier) { shown ->
                    Artwork(shown.thumbnailUrl, Modifier.fillMaxSize(), RoundedCornerShape(30.dp), shown.videoId, requestedPixels = 1200)
                }
            }
        }
    }
}

@Composable
private fun PlayerActionBar(
    track: Track,
    mix: MixUiState,
    isPreview: Boolean,
    viewModel: PlayerViewModel,
    onDiscover: (Track) -> Unit,
    songActions: SongActions?,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 12.dp)) {
        mix.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 8.dp).semantics { liveRegion = LiveRegionMode.Polite })
        }
        if (isPreview) Text(stringResource(R.string.mix_preview_hint), style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(bottom = 8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { viewModel.selectForPlaylist(track) },
                modifier = Modifier.weight(1.1f).heightIn(min = 56.dp), shape = MaterialTheme.shapes.medium,
                contentPadding = PaddingValues(horizontal = 12.dp)) {
                Icon(Icons.Default.PlaylistAdd, null, Modifier.size(20.dp))
                Text(stringResource(R.string.add_to_playlist), Modifier.padding(start = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            MixAction(mix, isPreview, viewModel::startMix, viewModel::cancelMixLoading, viewModel::restoreMixQueue,
                Modifier.weight(1f))
            Box {
                IconButton(onClick = { menuOpen = true }, modifier = Modifier.heightIn(min = 56.dp)) {
                    Icon(Icons.Default.MoreVert, stringResource(R.string.song_options, track.title))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.discover_similar)) },
                        onClick = { menuOpen = false; onDiscover(track) })
                    songActions?.let { actions ->
                        DropdownMenuItem(text = { Text(stringResource(R.string.song_options, track.title)) },
                            onClick = { menuOpen = false; actions.open(track) })
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaybackDeck(state: PlayerUiState, viewModel: PlayerViewModel) {
    Column(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
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
