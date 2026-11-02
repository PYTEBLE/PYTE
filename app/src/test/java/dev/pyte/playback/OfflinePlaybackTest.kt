@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package dev.pyte.playback

import android.app.Application
import android.net.Uri
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class OfflinePlaybackTest {
    @Test fun `cold offline playback and seeking read local bytes without invoking resolver`() {
        val context = RuntimeEnvironment.getApplication()
        val file = File(context.filesDir, "test-audio").apply { writeText("0123456789") }
        try {
            val spec = DataSpec.Builder().setUri(Uri.parse("pyte://stream/dQw4w9WgXcQ"))
                .setPosition(3).setLength(4).build()
            val resolved = resolveAudioSpec(spec, { file }) { error("Must not contact YouTube") }
            assertEquals("file", resolved.uri.scheme)
            assertEquals("offline:dQw4w9WgXcQ", resolved.key)
            val source = DefaultDataSource.Factory(context).createDataSource()
            try {
                assertEquals(4L, source.open(resolved))
                val buffer = ByteArray(4)
                assertEquals(4, source.read(buffer, 0, 4))
                assertEquals("3456", buffer.toString(Charsets.UTF_8))
            } finally { source.close() }
        } finally { file.delete() }
    }
}
