package dev.pyte.playback

import android.media.audiofx.Equalizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

internal interface EqualizerDevice {
    val frequencies: List<Int>
    val range: IntRange
    val hasControl: Boolean
    fun level(band: Int, gainMb: Int)
    fun enable(enabled: Boolean)
    fun controlListener(listener: (Boolean) -> Unit)
    fun release()
}

private class AndroidEqualizer(session: Int) : EqualizerDevice {
    private val effect = Equalizer(0, session)
    override val frequencies get() = (0 until effect.numberOfBands.toInt()).map { effect.getCenterFreq(it.toShort()) / 1000 }
    override val range get() = effect.bandLevelRange.let { it[0].toInt()..it[1].toInt() }
    override val hasControl get() = effect.hasControl()
    override fun level(band: Int, gainMb: Int) = effect.setBandLevel(band.toShort(), gainMb.toShort())
    override fun enable(enabled: Boolean) { check(effect.setEnabled(enabled) == 0) }
    override fun controlListener(listener: (Boolean) -> Unit) {
        effect.setControlStatusListener { _, granted -> listener(granted) }
    }
    override fun release() = effect.release()
}

data class EqualizerState(
    val frequencies: List<Int> = emptyList(),
    val minMb: Int = -1200,
    val maxMb: Int = 600,
    val available: Boolean = false,
    val message: String = "Start a song to check this device's equalizer.",
    val headroom: Float = 1f,
)

/** Service-owned audio effect. Never attaches to global output/session 0. Calls run on main. */
@Singleton
class EqualizerController internal constructor(private val factory: (Int) -> EqualizerDevice) {
    @Inject constructor() : this({ AndroidEqualizer(it) })
    private var device: EqualizerDevice? = null
    private var session = 0
    private var settings = EqualizerSettings()
    private val _state = MutableStateFlow(EqualizerState())
    val state = _state.asStateFlow()

    fun attach(id: Int) {
        if (id == session && device != null) return
        releaseDevice()
        session = id
        if (id <= 0) { _state.value = EqualizerState(); return }
        try {
            val next = factory(id)
            device = next
            val frequencies = next.frequencies
            val minimum = maxOf(-1200, next.range.first)
            val maximum = minOf(600, next.range.last)
            require(frequencies.isNotEmpty() && frequencies.size <= 32 && frequencies.all { it in 20..24000 } && minimum < maximum)
            _state.value = EqualizerState(frequencies, minimum, maximum, true, "")
            next.controlListener { granted ->
                if (device === next) {
                    if (granted) apply(settings)
                    else _state.value = _state.value.copy(available = false, headroom = 1f,
                        message = "Another audio effect has control. Try again after closing it.")
                }
            }
            apply(settings)
        } catch (_: Exception) { unavailable() }
    }

    fun apply(value: EqualizerSettings) {
        settings = value.normalized()
        val current = device ?: return
        try {
            if (!current.hasControl) {
                _state.value = _state.value.copy(available = false, headroom = 1f,
                    message = "Another audio effect has control. Try again after closing it.")
                return
            }
            val gains = _state.value.frequencies.map { EqualizerCurves.gainAt(settings.points, it)
                .coerceIn(_state.value.minMb, _state.value.maxMb) }
            // Disable while reconfiguring to avoid briefly applying a partially updated curve.
            current.enable(false)
            gains.forEachIndexed { index, gain -> current.level(index, gain) }
            current.enable(settings.enabled)
            _state.value = _state.value.copy(available = true, message = "", headroom =
                if (settings.enabled) EqualizerCurves.headroom(gains) else 1f)
        } catch (_: Exception) { unavailable() }
    }

    fun retry() { val id = session; releaseDevice(); attach(id) }
    fun release() { releaseDevice(); session = 0; _state.value = EqualizerState() }
    private fun releaseDevice() {
        val previous = device
        device = null
        runCatching { previous?.enable(false) }
        runCatching { previous?.release() }
    }
    private fun unavailable() {
        releaseDevice()
        _state.value = EqualizerState(message = "The equalizer is not available on this device or audio output.")
    }
}
