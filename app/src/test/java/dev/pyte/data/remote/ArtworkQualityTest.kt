package dev.pyte.data.remote

import android.app.Application
import android.graphics.Bitmap
import dev.pyte.domain.Track
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ArtworkQualityTest {
    @Test fun `quality compares decoded dimensions not requested URL or byte size`() {
        val file = File(RuntimeEnvironment.getApplication().cacheDir, "quality-test.png")
        try {
            val bitmap = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888)
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            assertEquals(ArtworkSize(120, 120), ArtworkQuality.size(file))
            assertTrue(ArtworkQuality.shouldReplace(ArtworkQuality.size(file), ArtworkSize(1200, 1200)))
            assertFalse(ArtworkQuality.shouldReplace(ArtworkSize(1200, 1200), ArtworkSize(120, 120)))
            file.writeText("not an image")
            assertNull(ArtworkQuality.size(file))
            assertFalse(ArtworkQuality.shouldReplace(null, null))
        } finally { file.delete() }
    }

    @Test fun `reported song offers a genuinely larger cover`() = runBlocking {
        assumeTrue(System.getenv("PYTE_LIVE_ARTWORK") == "true")
        val http = OkHttpClient()
        val track = MusicSearch(InnerTubeClient(http, Json)).search("Nur ein Wort Noah Kraus Wir sind Helden")
            .tracks.first { it.videoId == "7y0c6q5QoWA" }
        val file = File(RuntimeEnvironment.getApplication().cacheDir, "live-cover.jpg")
        try {
            val sizes = listOf(120, 1200).map { pixels ->
                http.newCall(Request.Builder().url(requireNotNull(ArtworkUrls.sized(track.thumbnailUrl, pixels))).build())
                    .execute().use { response ->
                        assertTrue(response.isSuccessful)
                        file.writeBytes(response.body.bytes())
                        requireNotNull(ArtworkQuality.size(file))
                    }
            }
            assertEquals(120, sizes[0].shortEdge)
            assertTrue(sizes[1].shortEdge >= 1000)
        } finally { file.delete() }
    }
}
