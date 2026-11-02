package dev.pyte.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.BoxWithConstraints
import dev.pyte.data.remote.ArtworkUrls
import dev.pyte.data.remote.ArtworkQuality
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import coil3.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import java.io.File
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.graphics.graphicsLayer
import dev.pyte.domain.Track
import dev.pyte.domain.PlaylistSummary
import dev.pyte.R

@Composable
fun Artwork(
    url: String?,
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(10.dp),
    videoId: String? = null,
    requestedPixels: Int? = null,
    placeholderUrl: String? = null,
) {
    val context = LocalContext.current
    val local = videoId?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{11}")) }
        ?.let { File(context.filesDir, "offline/$it.cover").takeIf(File::isFile) }
    BoxWithConstraints(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null || local != null) {
            val pixels = (requestedPixels ?: with(LocalDensity.current) { maxWidth.roundToPx() }).coerceIn(96, 1600)
            val sized = ArtworkUrls.sized(url, pixels)
            val revision = local?.let { "${it.lastModified()}:${it.length()}" }
            val localSize by produceState<dev.pyte.data.remote.ArtworkSize?>(null, local?.path, revision) {
                value = withContext(Dispatchers.IO) { local?.let(ArtworkQuality::size) }
            }
            val preferLocal = local != null && (url == null || (localSize?.shortEdge ?: 0) >= pixels.coerceAtMost(1200))
            var failed by remember(url, sized, revision) { mutableStateOf(false) }
            val data = if (preferLocal || failed && local != null) local else if (failed) url else sized
            var loading by remember(data) { mutableStateOf(true) }
            AsyncImage(
                model = ImageRequest.Builder(context).data(data)
                    .memoryCacheKey(if (data is File) "offline-cover:${data.path}:$revision" else data?.toString())
                    .apply {
                        if (requestedPixels != null) size(pixels, pixels)
                        if (placeholderUrl != null) placeholderMemoryCacheKey(
                            if (data is File) "offline-cover:${data.path}:$revision" else placeholderUrl)
                    }
                    .build(),
                onLoading = { loading = true },
                onSuccess = { loading = false },
                onError = { loading = false; failed = true },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (loading) ShimmerPlaceholder(Modifier.fillMaxSize())
        } else {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
fun TrackRow(
    track: Track,
    isCurrent: Boolean,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    modifier: Modifier = Modifier,
    onPlayNext: (() -> Unit)? = null,
    onAddToQueue: (() -> Unit)? = null,
    onAddToPlaylist: (() -> Unit)? = null,
    isDownloaded: Boolean = false,
) {
    var menuOpen by remember(track.videoId) { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(url = track.thumbnailUrl, modifier = Modifier.size(48.dp), videoId = track.videoId)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        ) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (isDownloaded) {
            Icon(Icons.Default.DownloadDone, "Available offline", tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 8.dp).size(18.dp))
        }
        if (track.durationMs > 0) {
            Text(
                text = formatDuration(track.durationMs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FavoriteButton(isFavorite, onFavoriteClick, key = track.videoId)
        if (onPlayNext != null || onAddToQueue != null || onAddToPlaylist != null) {
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Options for ${track.title}")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    onAddToPlaylist?.let { action ->
                        DropdownMenuItem(text = { Text("Add to local playlist") }, onClick = { menuOpen = false; action() })
                    }
                    onPlayNext?.let { action ->
                        DropdownMenuItem(text = { Text("Play next") }, onClick = { menuOpen = false; action() })
                    }
                    onAddToQueue?.let { action ->
                        DropdownMenuItem(text = { Text("Add to queue") }, onClick = { menuOpen = false; action() })
                    }
                }
            }
        }
    }
}

@Composable
fun PlaylistCard(playlist: PlaylistSummary, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        Artwork(
            url = playlist.thumbnailUrl,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            shape = RoundedCornerShape(16.dp),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = playlist.title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "${playlist.trackCount} tracks",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun MiniPlayer(
    title: String,
    artist: String,
    artworkUrl: String?,
    isPlaying: Boolean,
    progress: Float,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    videoId: String? = null,
) {
    val morph = LocalPlayerMorph.current
    val miniTrack = Track(videoId ?: "mini", title, artist, 0, artworkUrl)
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp,
    ) {
        Column {
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(3.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpen)
                    .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Artwork(url = artworkUrl, modifier = Modifier.size(44.dp)
                    .onGloballyPositioned { coordinates -> videoId?.let { morph?.miniBounds(it, coordinates.boundsInWindow()) } }
                    .graphicsLayer { alpha = if (videoId != null && morph?.hiddenId == videoId) 0f else 1f }, videoId = videoId)
                SongMetadata(miniTrack, 1, Modifier.weight(1f).padding(horizontal = 12.dp), compact = true)
                LivingPlayButton(isPlaying, onToggle, size = 42.dp)
                IconButton(onClick = onNext) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Next")
                }
            }
        }
    }
}

@Composable
fun EmptyState(
    title: String,
    body: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(R.drawable.pyte_symbol),
            contentDescription = null,
            modifier = Modifier.size(88.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(24.dp))
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
