package dev.pyte.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SoftwareEqualizerTest {
    @Test fun `battery fast bypass is byte exact and does not bypass enabled EQ or requested spectrum`() {
        val engine = SoftwareEqualizer().apply { fastBypass = true }
        val processor = engine.processor()
        processor.configure(AudioFormat(48000, 2, C.ENCODING_PCM_16BIT)); processor.flush()
        val bytes = ByteArray(48000).also { java.util.Random(71).nextBytes(it) }
        fun input() = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).put(bytes).apply { flip() }
        processor.queueInput(input())
        assertArrayEquals(bytes, ByteArray(bytes.size).also(processor.output::get))
        engine.apply(EqualizerSettings(enabled = true, points = EqualizerCurves.presets.first { it.id == "bass" }.points))
        processor.queueInput(input())
        assertFalse(bytes.contentEquals(ByteArray(bytes.size).also(processor.output::get)))
        engine.apply(EqualizerSettings()); engine.analyzing = true
        processor.queueInput(input()); processor.output
        assertEquals(48, engine.spectrum.value.size)
    }
    @Test fun `every band independently changes PCM at its center frequency`() {
        for (rate in listOf(44100, 48000)) for (hz in EqualizerCurves.frequencies) {
            val points = EqualizerCurves.flat.map { it.copy(gainMb = if (it.hz == hz) 600 else 0) }
            val dsp = EqualizerDsp(rate, 2)
            dsp.configure(EqualizerSettings(enabled = true, points = points, limiter = false), true)
            val frame = DoubleArray(2)
            var inputPower = 0.0; var outputPower = 0.0
            repeat(rate) { sample ->
                frame[0] = .1 * sin(2 * PI * hz * sample / rate)
                frame[1] = 0.0
                val out = dsp.frame(frame)
                if (sample > rate / 2) { inputPower += frame[0].pow(2); outputPower += out[0].pow(2) }
                assertEquals("No stereo cross-talk", 0.0, out[1], 1e-12)
            }
            val expected = 10.0.pow(6.0 / 20) * EqualizerCurves.headroom(listOf(600))
            assertEquals("$hz Hz at $rate", expected, sqrt(outputPower / inputPower), .025)
        }
    }

    @Test fun `disabled processor is byte exact and keeps duration channels and frame count`() {
        val engine = SoftwareEqualizer()
        val processor = engine.processor()
        val format = AudioFormat(48000, 2, C.ENCODING_PCM_16BIT)
        assertEquals(format, processor.configure(format))
        processor.flush()
        val bytes = ByteArray(8192).also { java.util.Random(42).nextBytes(it) }
        processor.queueInput(ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).put(bytes).apply { flip() })
        val output = processor.output
        assertEquals(bytes.size, output.remaining())
        assertArrayEquals(bytes, ByteArray(output.remaining()).also(output::get))
    }

    @Test fun `pending format changes take effect only at flush and reset is safe before configuration`() {
        val processor = SoftwareEqualizer().processor()
        processor.reset(); processor.flush()
        processor.configure(AudioFormat(44100, 2, C.ENCODING_PCM_16BIT)); processor.flush()
        processor.configure(AudioFormat(48000, 1, C.ENCODING_PCM_16BIT))
        val stereo = ByteBuffer.allocateDirect(8).order(ByteOrder.nativeOrder()).apply {
            putShort(100); putShort(200); putShort(300); putShort(400); flip()
        }
        processor.queueInput(stereo)
        assertEquals(8, processor.output.remaining())
        processor.flush()
        processor.queueInput(ByteBuffer.allocateDirect(2).order(ByteOrder.nativeOrder()).putShort(500).apply { flip() })
        assertEquals(2, processor.output.remaining())
        processor.reset()
    }

    @Test fun `limiter handles combined band boosts and never emits nonfinite or over ceiling samples`() {
        val dsp = EqualizerDsp(48000, 2)
        dsp.configure(EqualizerSettings(enabled = true, points = EqualizerCurves.flat.map { it.copy(gainMb = 600) }), true)
        val frame = DoubleArray(2)
        repeat(48000) { i ->
            frame[0] = sin(2 * PI * 1000 * i / 48000)
            frame[1] = sin(2 * PI * 400 * i / 48000)
            dsp.frame(frame).forEach { assertTrue(it.isFinite() && abs(it) <= .980001) }
        }
    }

    @Test fun `changing settings and bypass during playback stays finite and recovers exact silence`() {
        val dsp = EqualizerDsp(8000, 2)
        val frame = doubleArrayOf(.5, -.5)
        repeat(20) { iteration ->
            dsp.configure(EqualizerSettings(enabled = iteration % 3 != 0, points = EqualizerCurves.flat.map {
                it.copy(gainMb = if (iteration % 2 == 0) 600 else -1200)
            }))
            repeat(100) { dsp.frame(frame).forEach { assertTrue(it.isFinite()) } }
        }
        dsp.configure(EqualizerSettings())
        repeat(1000) { dsp.frame(doubleArrayOf(0.0, 0.0)) }
        dsp.frame(doubleArrayOf(0.0, 0.0)).forEach { assertEquals(0.0, it, 1e-9) }
    }

    @Test fun `flush clears spectral history and measurement runs only on demand`() {
        val engine = SoftwareEqualizer()
        val processor = engine.processor()
        processor.configure(AudioFormat(44100, 1, C.ENCODING_PCM_16BIT)); processor.flush()
        fun audio() = ByteBuffer.allocateDirect(16384).order(ByteOrder.nativeOrder()).apply {
            repeat(8192) { putShort((sin(2 * PI * 1000 * it / 44100) * 12000).toInt().toShort()) }; flip()
        }
        processor.queueInput(audio()); processor.output
        assertTrue(engine.spectrum.value.isEmpty())
        engine.analyzing = true
        processor.queueInput(audio()); processor.output
        assertEquals(48, engine.spectrum.value.size)
        assertTrue(engine.spectrum.value.max() > .5f)
        processor.flush()
        assertTrue(engine.spectrum.value.isEmpty())
    }

    @Test fun `old five band settings and profiles migrate without losing their control points`() {
        val old = """{"enabled":true,"points":[{"hz":63,"gainMb":500},{"hz":250,"gainMb":250},{"hz":1000,"gainMb":0},{"hz":4000,"gainMb":-100},{"hz":16000,"gainMb":0}]}"""
        val settings = Json.decodeFromString<EqualizerSettings>(old).normalized()
        assertTrue(settings.limiter)
        assertEquals(500, EqualizerCurves.gainAt(settings.points, 63))
        assertEquals(250, EqualizerCurves.gainAt(settings.points, 250))
        assertEquals(5, settings.points.size)
        assertTrue(EqualizerCurves.gainAt(settings.points, 100) in 250..500)
    }

    @Test fun `preset count band frequencies and gains match the web reference`() {
        assertEquals(15, EqualizerCurves.frequencies.size)
        assertEquals(16, EqualizerCurves.presets.size)
        EqualizerCurves.presets.forEach { preset ->
            assertEquals(EqualizerCurves.frequencies, preset.points.map { it.hz })
            assertTrue(preset.points.all { it.gainMb in -1200..600 && it.gainMb % 50 == 0 })
        }
        val web = java.io.File("../web/src/equalizer.ts").readText()
        val pattern = Regex("id: \"([^\"]+)\",\\s*name: \"[^\"]+\",\\s*gains: \\[([^]]+)]")
        val reference = pattern.findAll(web).associate { match ->
            match.groupValues[1] to match.groupValues[2].split(',').map { (it.trim().toDouble() * 100).roundToInt() }
        }
        assertEquals(15, reference.size)
        EqualizerCurves.presets.filter { it.id != "neutral" }.forEach { preset ->
            val id = when (preset.id) { "voices" -> "stimmen"; "electronic" -> "elektro"; else -> preset.id }
            assertEquals(preset.name, reference.getValue(id), preset.points.map { it.gainMb })
        }
    }
}
