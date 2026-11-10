package dev.pyte.designreview

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pyte.R
import dev.pyte.ui.components.pressMotion
import dev.pyte.ui.components.touchBloom
import dev.pyte.ui.theme.ListeningComponents as Components
import dev.pyte.ui.theme.ListeningSpace as Space

@Immutable
internal data class ReviewSong(val id: Int, val title: String, val artist: String, val cover: String)

internal val demoSongs = listOf(
    ReviewSong(0, "Zwischen den Lichtern", "Mira Nord", "NACHT\nFAHRT"),
    ReviewSong(1, "Alles wird leise", "Leon Weiss", "BLAUE\nSTUNDE"),
    ReviewSong(2, "Unter freiem Himmel", "Studio Sommer", "WEITER\nDRAUSSEN"),
)

@Immutable
internal data class ReviewPlaylist(val id: String, val title: String, val count: Int, val artwork: Int, val offline: Boolean)

internal val demoPlaylists = listOf(
    ReviewPlaylist("night", "Nachtfahrt", 24, 0, true),
    ReviewPlaylist("blue", "Blaue Stunde", 18, 1, false),
    ReviewPlaylist("outside", "Weiter draußen", 32, 2, true),
    ReviewPlaylist("morning", "Ruhiger Morgen", 12, 1, false),
)

/** Locally drawn fictional artwork. No image download, microphone, spectrum or frame loop. */
@Composable
internal fun ReviewCover(index: Int, label: String, modifier: Modifier = Modifier, lettering: Boolean = true) {
    val accent = Components.artworkColors[index % Components.artworkColors.size]
    val ink = MaterialTheme.colorScheme.background
    BoxWithConstraints(modifier.clip(Components.cardShape).background(ink).clearAndSetSemantics { }) {
        val density = LocalDensity.current
        val longestLine = label.lines().maxOf { it.length }.coerceAtLeast(1)
        val letteringSize = with(density) { (maxWidth - Space.content * 2).toSp() }.value
            .div(longestLine * .76f).coerceIn(10f, 26f).sp
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.linearGradient(listOf(accent.copy(alpha = .85f), ink), Offset.Zero, Offset(size.width, size.height)))
            drawCircle(Brush.radialGradient(listOf(accent, Color.Transparent),
                Offset(size.width * .68f, size.height * .32f), size.width * .9f),
                size.width * .9f, Offset(size.width * .68f, size.height * .32f))
            repeat(8) { line ->
                drawCircle(Color.White.copy(alpha = .10f), size.width * (.19f + line * .055f),
                    Offset(size.width * .78f, size.height * .28f), style = Stroke(size.width * .006f))
            }
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .55f))))
        }
        if (lettering) Text(label, Modifier.align(Alignment.BottomStart).padding(Space.content),
            color = Color.White, fontWeight = FontWeight.Black, fontSize = letteringSize, lineHeight = letteringSize * 1.1f,
            letterSpacing = (-.8).sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}

internal fun coverLettering(title: String): String = when (title) {
    "Nachtfahrt" -> "NACHT\nFAHRT"
    "Weiter draußen" -> "WEITER\nDRAUSSEN"
    else -> title.uppercase(java.util.Locale.GERMAN).replace(" ", "\n")
}

@Composable
internal fun ReviewIcon(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    IconButton(onClick = onClick, modifier = modifier.sizeIn(minWidth = Components.touchTarget, minHeight = Components.touchTarget)) {
        Icon(icon, label, tint = tint, modifier = Modifier.size(Space.section))
    }
}

@Composable
internal fun ReviewPrimary(label: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val source = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Button(onClick = onClick, interactionSource = source, shape = Components.buttonShape,
        modifier = modifier.heightIn(min = Components.buttonHeight).pressMotion(source)
            .clip(Components.buttonShape).touchBloom(source), contentPadding = PaddingValues(Space.item)) {
        Icon(icon, null, Modifier.size(Space.section))
        Text(label, Modifier.padding(start = Space.tight), style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
internal fun ReviewSongRow(song: ReviewSong, onPlay: () -> Unit, onSave: () -> Unit, onMore: () -> Unit,
    current: Boolean = false) {
    Surface(onClick = onPlay, color = if (current) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.background,
        shape = Components.buttonShape, modifier = Modifier.fillMaxWidth().heightIn(min = Components.songHeight)) {
        Row(Modifier.padding(horizontal = Space.tight, vertical = Space.tight), verticalAlignment = Alignment.CenterVertically) {
            ReviewCover(song.id, song.cover, Modifier.size(52.dp), lettering = false)
            Column(Modifier.weight(1f).padding(horizontal = Space.item)) {
                Text(song.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(song.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            ReviewIcon(Icons.AutoMirrored.Filled.PlaylistAdd, stringResource(R.string.add_song_to_playlist, song.title), onSave,
                tint = MaterialTheme.colorScheme.primary)
            ReviewIcon(Icons.Default.MoreVert, stringResource(R.string.song_options, song.title), onMore)
        }
    }
}

@Composable
internal fun ReviewCollection(playlist: ReviewPlaylist, onOpen: () -> Unit) {
    Surface(onClick = onOpen, color = MaterialTheme.colorScheme.surface, shape = Components.cardShape) {
        Column {
            ReviewCover(playlist.artwork, coverLettering(playlist.title),
                Modifier.fillMaxWidth().aspectRatio(1f))
            Column(Modifier.padding(Space.item), verticalArrangement = Arrangement.spacedBy(Space.unit)) {
                Text(playlist.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.unit)) {
                    if (playlist.offline) Icon(Icons.Default.DownloadDone, stringResource(R.string.offline_available),
                        Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
                    Text(pluralStringResource(R.plurals.songs_count, playlist.count, playlist.count),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
internal fun ReviewAction(label: String, icon: ImageVector, onClick: () -> Unit, tag: String = "") {
    val source = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Surface(onClick = onClick, interactionSource = source, color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = Components.buttonShape, modifier = Modifier.fillMaxWidth().testTag(tag)
            .heightIn(min = Components.buttonHeight).pressMotion(source).clip(Components.buttonShape).touchBloom(source)) {
        Row(Modifier.padding(Space.item), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.item)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Composable
internal fun ReviewNotice(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
