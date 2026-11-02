@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package dev.pyte.playback

import androidx.media3.common.MediaItem
import androidx.media3.datasource.DataSpec
import android.net.Uri
import dev.pyte.data.remote.ResolvedStream
import java.io.File

/** Restore local configuration on the service side, where playback URIs are owned. */
internal fun resolvePlaybackItem(item: MediaItem): MediaItem {
    require(Regex("[A-Za-z0-9_-]{11}").matches(item.mediaId)) { "Invalid YouTube track ID" }
    return item.buildUpon()
        .setUri("pyte://stream/${item.mediaId}")
        .build()
}

/** A complete local file must bypass URL resolution entirely, including on a cold offline start. */
internal fun resolveAudioSpec(
    spec: DataSpec,
    localAudio: (String) -> File?,
    resolve: (String) -> ResolvedStream,
): DataSpec {
    val id = spec.uri.lastPathSegment ?: throw IllegalArgumentException("Bad stream uri")
    localAudio(id)?.let { return spec.buildUpon().setUri(Uri.fromFile(it)).setKey("offline:$id").build() }
    val stream = resolve(id)
    return spec.buildUpon().setUri(stream.url).setKey(stream.cacheKey(id))
        .setHttpRequestHeaders(spec.httpRequestHeaders + ("User-Agent" to stream.userAgent)).build()
}
