package dev.pyte.playback

import androidx.media3.common.MediaItem

/** Restore local configuration on the service side, where playback URIs are owned. */
internal fun resolvePlaybackItem(item: MediaItem): MediaItem {
    require(Regex("[A-Za-z0-9_-]{11}").matches(item.mediaId)) { "Invalid YouTube track ID" }
    return item.buildUpon()
        .setUri("pyte://stream/${item.mediaId}")
        .build()
}
