package dev.pyte.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import kotlin.random.Random

/** A random start in an unrelated shuffle order would play only its remaining suffix. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun ExoPlayer.shuffleFromCurrent(random: Random = Random.Default) {
    val current = currentMediaItemIndex
    if (current !in 0 until mediaItemCount) return
    val order = listOf(current) + (0 until mediaItemCount).filter { it != current }.shuffled(random)
    setShuffleOrder(DefaultShuffleOrder(order.toIntArray(), random.nextLong()))
}

/** Explicit edits use visible queue order, so turn off random traversal first. */
internal fun Player.enqueue(item: MediaItem, next: Boolean) {
    if (mediaItemCount == 0) {
        setMediaItem(item)
        prepare()
        play()
        return
    }
    if (next) shuffleModeEnabled = false
    val insertion = if (next) (currentMediaItemIndex + 1).coerceIn(0, mediaItemCount) else mediaItemCount
    addMediaItem(insertion, item)
}

internal fun Player.moveQueuedItem(from: Int, to: Int) {
    if (from !in 0 until mediaItemCount || to !in 0 until mediaItemCount || from == to) return
    shuffleModeEnabled = false
    moveMediaItem(from, to)
}

internal fun Player.removeQueuedItem(index: Int) {
    if (index in 0 until mediaItemCount) removeMediaItem(index)
}
