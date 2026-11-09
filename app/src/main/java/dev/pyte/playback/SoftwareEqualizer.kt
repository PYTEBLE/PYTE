@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package dev.pyte.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*
import android.content.Context
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink

internal class EqualizerRenderersFactory(context: Context, private val engine: SoftwareEqualizer) : DefaultRenderersFactory(context) {
    override fun buildAudioSink(context: Context, enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean): AudioSink = DefaultAudioSink.Builder(context)
        .setEnableFloatOutput(false)
        .setAudioProcessors(arrayOf(engine.processor()))
        .build()
}

/** Audio-thread-only, independent stereo biquads. Never touches another app's audio session. */
internal class EqualizerDsp(private val sampleRate: Int, private val channels: Int) {
    private class Band(val b0: Double, val b1: Double, val b2: Double, val a1: Double, val a2: Double, channels: Int) {
        val z1 = DoubleArray(channels)
        val z2 = DoubleArray(channels)
        fun tick(x: Double, channel: Int): Double {
            val y = b0 * x + z1[channel]
            z1[channel] = b1 * x - a1 * y + z2[channel]
            z2[channel] = b2 * x - a2 * y
            return y
        }
    }
    private var bands = emptyList<Band>()
    private var previous = emptyList<Band>()
    private var transition = 0
    private val rampFrames = max(1, sampleRate / 100) // 10 ms crossfade, no coefficient discontinuities.
    private var oldPreamp = 1.0
    private var preamp = 1.0
    private var limiterGain = 1.0
    private val release = exp(-1.0 / (sampleRate * .05))
    private val work = DoubleArray(channels)
    private var limiter = true

    fun configure(settings: EqualizerSettings, immediate: Boolean = false) {
        previous = bands
        oldPreamp = preamp
        bands = if (!settings.enabled) emptyList() else EqualizerCurves.frequencies.mapNotNull { hz ->
            if (hz >= sampleRate * .45) return@mapNotNull null
            val gain = EqualizerCurves.gainAt(settings.points, hz) / 100.0
            if (abs(gain) < .001) return@mapNotNull null
            val a = 10.0.pow(gain / 40)
            val w = 2 * PI * hz / sampleRate
            val alpha = sin(w) / (2 * 1.1) // Same Q as the web's peaking filters.
            val a0 = 1 + alpha / a
            Band((1 + alpha * a) / a0, -2 * cos(w) / a0, (1 - alpha * a) / a0,
                -2 * cos(w) / a0, (1 - alpha / a) / a0, channels)
        }
        preamp = if (settings.enabled) EqualizerCurves.headroom(EqualizerCurves.frequencies.filter { it < sampleRate * .45 }.map {
            EqualizerCurves.gainAt(settings.points, it)
        }).toDouble() else 1.0
        limiter = settings.enabled && settings.limiter
        transition = if (immediate) 0 else rampFrames
        if (immediate) previous = emptyList()
    }

    fun frame(input: DoubleArray): DoubleArray {
        val blend = if (transition > 0) 1.0 - transition.toDouble() / rampFrames else 1.0
        var peak = 0.0
        for (channel in 0 until channels) {
            var value = input[channel] * preamp
            bands.forEach { value = it.tick(value, channel) }
            if (transition > 0) {
                var old = input[channel] * oldPreamp
                previous.forEach { old = it.tick(old, channel) }
                value = old * (1 - blend) + value * blend
            }
            work[channel] = if (value.isFinite()) value else 0.0
            peak = max(peak, abs(work[channel]))
        }
        if (transition > 0 && --transition == 0) previous = emptyList()
        // Stereo-linked instantaneous peak protection, 50 ms release. PCM conversion always saturates.
        val target = if (limiter && peak > .98) .98 / peak else 1.0
        limiterGain = if (target < limiterGain) target else target + (limiterGain - target) * release
        if (limiter) for (channel in work.indices) work[channel] *= limiterGain
        return work
    }
}

@Singleton
class SoftwareEqualizer @Inject constructor() {
    @Volatile internal var settings = EqualizerSettings()
        private set
    @Volatile var analyzing = false
    @Volatile var fastBypass = false
    private val _state = MutableStateFlow(EqualizerState(frequencies = EqualizerCurves.frequencies,
        available = true, message = "15-Band-Software-EQ · nur für PYTE"))
    val state = _state.asStateFlow()
    private val _spectrum = MutableStateFlow<List<Float>>(emptyList())
    val spectrum = _spectrum.asStateFlow()
    fun apply(value: EqualizerSettings) {
        settings = value.normalized()
        _state.update { it.copy(headroom = headroom(value, it.frequencies)) }
    }
    internal fun format(rate: Int) {
        val frequencies = EqualizerCurves.frequencies.filter { it < rate * .45 }
        _state.update { it.copy(available = true, frequencies = frequencies,
            headroom = headroom(settings, frequencies), message = "15-Band-Software-EQ · ${rate / 1000.0} kHz PCM") }
    }
    private fun headroom(value: EqualizerSettings, frequencies: List<Int>) = if (value.enabled)
        EqualizerCurves.headroom(frequencies.map { EqualizerCurves.gainAt(value.points, it) }) else 1f
    fun clearSpectrum() { _spectrum.value = emptyList() }
    internal fun spectrum(values: List<Float>) { if (analyzing) _spectrum.value = values }
    fun processor() = EqualizerAudioProcessor(this)
}

/** PCM16 processor stays in the chain while bypassed, so toggling never restarts playback. */
class EqualizerAudioProcessor internal constructor(private val engine: SoftwareEqualizer) : BaseAudioProcessor() {
    private var dsp: EqualizerDsp? = null
    private var applied: EqualizerSettings? = null
    private var rate = 44100
    private var channels = 2
    private var frame = DoubleArray(channels)
    private val window = DoubleArray(2048)
    private val hann = DoubleArray(window.size) { .5 - .5 * cos(2 * PI * it / (window.size - 1)) }
    private var written = 0
    private var captured = 0
    private var analyzeFrames = 0
    private var wasAnalyzing = false
    private var bypassRampFrames = 0

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) throw androidx.media3.common.audio.AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        require(inputAudioFormat.channelCount in 1..8)
        require(inputAudioFormat.sampleRate in 8000..384000)
        return inputAudioFormat
    }
    @Suppress("OVERRIDE_DEPRECATION") // Media3 still routes the metadata-free flush here.
    override fun onFlush() {
        if (inputAudioFormat == AudioFormat.NOT_SET) { dsp = null; applied = null; engine.clearSpectrum(); return }
        rate = inputAudioFormat.sampleRate
        channels = inputAudioFormat.channelCount
        engine.format(rate)
        dsp = EqualizerDsp(rate, channels)
        applied = engine.settings
        dsp!!.configure(checkNotNull(applied), immediate = true)
        frame = DoubleArray(channels)
        written = 0
        captured = 0
        analyzeFrames = 0
        window.fill(0.0)
        engine.clearSpectrum()
        bypassRampFrames = 0
    }
    override fun queueInput(inputBuffer: ByteBuffer) {
        val processor = checkNotNull(dsp)
        val current = engine.settings
        if (current != applied) {
            processor.configure(current); applied = current
            bypassRampFrames = if (current.enabled) 0 else rate / 100
        }
        val output = replaceOutputBuffer(inputBuffer.remaining())
        val capture = engine.analyzing
        if (capture != wasAnalyzing) { written = 0; captured = 0; analyzeFrames = 0; wasAnalyzing = capture }
        if (engine.fastBypass && !current.enabled && !capture && bypassRampFrames == 0) {
            output.put(inputBuffer)
            output.flip()
            return
        }
        bypassRampFrames = (bypassRampFrames - inputBuffer.remaining() / (channels * 2)).coerceAtLeast(0)
        while (inputBuffer.remaining() >= channels * 2) {
            for (channel in 0 until channels) frame[channel] = inputBuffer.short / 32768.0
            val processed = processor.frame(frame)
            var mono = 0.0
            for (channel in 0 until channels) {
                val value = processed[channel].coerceIn(-1.0, 32767.0 / 32768)
                output.putShort((value * 32768).roundToInt().toShort())
                mono += value / channels
            }
            if (capture) {
                window[written] = mono
                written = (written + 1) % window.size
                captured = min(window.size, captured + 1)
                if (++analyzeFrames >= rate / 12 && captured >= window.size) {
                    analyzeFrames = 0
                    engine.spectrum(analyze())
                }
            }
        }
        output.flip()
    }
    private fun analyze(): List<Float> = (0 until 48).map { index ->
        val hz = 30.0 * (min(18000.0, rate * .45) / 30).pow(index / 47.0)
        val coefficient = 2 * cos(2 * PI * hz / rate)
        var q1 = 0.0; var q2 = 0.0
        for (i in window.indices) {
            val sample = window[(written + i) % window.size] * hann[i]
            val q0 = sample + coefficient * q1 - q2
            q2 = q1; q1 = q0
        }
        val magnitude = sqrt(max(0.0, q1 * q1 + q2 * q2 - coefficient * q1 * q2)) * 4 / window.size
        ((20 * log10(max(1e-6, magnitude)) + 72) / 72).coerceIn(0.0, 1.0).toFloat()
    }
    override fun onReset() { dsp = null; applied = null; engine.clearSpectrum() }
}
