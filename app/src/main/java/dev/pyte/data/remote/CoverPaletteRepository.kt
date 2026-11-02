package dev.pyte.data.remote

import android.content.Context
import android.util.LruCache
import android.graphics.Bitmap
import coil3.BitmapImage
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.pyte.domain.Track
import dev.pyte.ui.theme.CoverColorExtractor
import dev.pyte.ui.theme.CoverColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CoverPaletteRepository @Inject constructor(@ApplicationContext private val context: Context) {
    private val cache = LruCache<String, CoverColors>(64)
    suspend fun colors(track: Track): CoverColors? = withContext(Dispatchers.IO) {
        if (!track.videoId.matches(Regex("[A-Za-z0-9_-]{11}"))) return@withContext null
        val local = File(context.filesDir, "offline/${track.videoId}.cover").takeIf { it.isFile }
        val key = "${track.videoId}:${track.thumbnailUrl}:${local?.lastModified()}:${local?.length()}"
        cache.get(key)?.let { return@withContext it }
        for (data in listOfNotNull(local, ArtworkUrls.sized(track.thumbnailUrl, 128), track.thumbnailUrl).distinct()) {
            try {
                val result = withTimeoutOrNull(5000) {
                    SingletonImageLoader.get(context).execute(ImageRequest.Builder(context)
                        .data(data).size(64, 64).allowHardware(false).build())
                } as? SuccessResult ?: continue
                val bitmap = (result.image as? BitmapImage)?.bitmap ?: continue
                val sampled = Bitmap.createScaledBitmap(bitmap, 64, 64, true)
                val pixels = IntArray(64 * 64)
                sampled.getPixels(pixels, 0, 64, 0, 0, 64, 64)
                if (sampled !== bitmap) sampled.recycle()
                val colors = CoverColorExtractor.extract(pixels) ?: return@withContext null
                cache.put(key, colors)
                return@withContext colors
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { /* Try the original URL or retain the violet theme. */ }
        }
        null
    }
}
