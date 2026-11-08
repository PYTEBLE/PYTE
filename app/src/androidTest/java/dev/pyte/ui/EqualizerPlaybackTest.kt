@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package dev.pyte.ui

import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.pyte.playback.EqualizerRenderersFactory
import dev.pyte.playback.SoftwareEqualizer
import dev.pyte.playback.EqualizerSettings
import dev.pyte.playback.EqualizerCurves
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

/** Real decoder -> shared production sink -> DSP -> AudioTrack, no network or microphone. */
@RunWith(AndroidJUnit4::class)
class EqualizerPlaybackTest {
    @Test fun decoderAudioPassesThroughTheSoftwareEqualizerWithoutRestartOnEdits() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val frames = 48000 * 12
        val wave = ByteBuffer.allocate(44 + frames * 4).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(capacity() - 8); put("WAVEfmt ".toByteArray()); putInt(16)
            putShort(1); putShort(2); putInt(48000); putInt(48000 * 4); putShort(4); putShort(16)
            put("data".toByteArray()); putInt(frames * 4)
            repeat(frames) { i -> val sample = (sin(2 * PI * 1000 * i / 48000) * 10000).toInt().toShort(); putShort(sample); putShort(sample) }
        }
        val file = File(context.cacheDir, "eq-test-tone.wav").apply { writeBytes(wave.array()) }
        val engine = SoftwareEqualizer().apply { analyzing = true; apply(EqualizerSettings(enabled = true)) }
        lateinit var player: ExoPlayer
        instrumentation.runOnMainSync {
            player = ExoPlayer.Builder(context, EqualizerRenderersFactory(context, engine)).build()
            player.volume = 0f // Analyze real PCM, but don't play a test tone through speakers.
            player.setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(file)))
            player.prepare(); player.play()
        }
        fun <T> read(block: () -> T): T {
            var result: T? = null
            instrumentation.runOnMainSync { result = block() }
            return checkNotNull(result)
        }
        try {
            val deadline = android.os.SystemClock.elapsedRealtime() + 30000
            while (engine.spectrum.value.isEmpty() && android.os.SystemClock.elapsedRealtime() < deadline) android.os.SystemClock.sleep(50)
            assertEquals(48, engine.spectrum.value.size)
            assertTrue(engine.state.value.message.contains("48.0"))
            assertTrue(read { player.isPlaying })
            val before = read { player.currentPosition }
            engine.apply(EqualizerSettings(enabled = true, points = EqualizerCurves.presets.first { it.id == "bass" }.points))
            assertTrue(read { player.isPlaying })
            assertTrue(read { player.currentPosition } >= before)
            assertTrue(read { player.playerError == null })
        } finally {
            instrumentation.runOnMainSync { player.release() }
            file.delete()
        }
    }
}
