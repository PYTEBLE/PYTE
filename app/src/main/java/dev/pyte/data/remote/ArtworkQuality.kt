package dev.pyte.data.remote

import android.graphics.BitmapFactory
import java.io.File

data class ArtworkSize(val width: Int, val height: Int) {
    val shortEdge: Int get() = minOf(width, height)
    val pixels: Long get() = width.toLong() * height
}

object ArtworkQuality {
    const val OFFLINE_TARGET = 1200
    fun size(file: File): ArtworkSize? = runCatching {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, options)
        if (options.outWidth > 0 && options.outHeight > 0) ArtworkSize(options.outWidth, options.outHeight) else null
    }.getOrNull()

    fun shouldReplace(old: ArtworkSize?, candidate: ArtworkSize?): Boolean =
        candidate != null && candidate.pixels <= 25_000_000 &&
            (old == null || candidate.shortEdge > old.shortEdge)
}
