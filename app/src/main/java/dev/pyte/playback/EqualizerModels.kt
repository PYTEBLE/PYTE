package dev.pyte.playback

import kotlinx.serialization.Serializable
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

@Serializable
data class EqPoint(val hz: Int, val gainMb: Int)

@Serializable
data class SoundProfile(val id: String, val name: String, val points: List<EqPoint>)

@Serializable
data class EqualizerSettings(
    val enabled: Boolean = false,
    val selectedId: String = "neutral",
    val points: List<EqPoint> = EqualizerCurves.flat,
    val profiles: List<SoundProfile> = emptyList(),
) {
    fun normalized() = copy(points = EqualizerCurves.normalize(points),
        profiles = profiles.take(12).distinctBy { it.id }.map {
            it.copy(name = it.name.trim().take(48), points = EqualizerCurves.normalize(it.points))
        }.filter { it.name.isNotBlank() })
}

object EqualizerCurves {
    val frequencies = listOf(63, 250, 1000, 4000, 16000)
    val flat = frequencies.map { EqPoint(it, 0) }
    private fun preset(id: String, name: String, gains: List<Int>) = SoundProfile(id, name,
        frequencies.zip(gains) { hz, gain -> EqPoint(hz, gain) })
    val presets = listOf(
        preset("neutral", "Neutral", listOf(0, 0, 0, 0, 0)),
        preset("bass", "Bass", listOf(500, 250, 0, -100, 0)),
        preset("voices", "Voices", listOf(-300, -150, 250, 300, -100)),
        preset("warm", "Warm", listOf(250, 150, 0, -150, -250)),
        preset("electronic", "Electronic", listOf(350, 100, -150, 150, 350)),
    )
    fun normalize(points: List<EqPoint>): List<EqPoint> = points.take(32)
        .filter { it.hz in 20..24000 }.distinctBy { it.hz }.sortedBy { it.hz }
        .map { it.copy(gainMb = it.gainMb.coerceIn(-1200, 600)) }.ifEmpty { flat }

    fun gainAt(points: List<EqPoint>, hz: Int): Int {
        val sorted = normalize(points)
        if (hz <= sorted.first().hz) return sorted.first().gainMb
        if (hz >= sorted.last().hz) return sorted.last().gainMb
        val right = sorted.indexOfFirst { it.hz >= hz }
        val a = sorted[right - 1]; val b = sorted[right]
        val fraction = ln(hz.toDouble() / a.hz) / ln(b.hz.toDouble() / a.hz)
        return (a.gainMb + (b.gainMb - a.gainMb) * fraction).roundToInt().coerceIn(-1200, 600)
    }

    /** Conservative headroom, not a limiter. Native effects can vary between devices. */
    fun headroom(gains: List<Int>): Float {
        val boost = (gains.maxOrNull() ?: 0).coerceAtLeast(0) / 100.0
        return if (boost == 0.0) 1f else 10.0.pow(-(boost + 1.5) / 20.0).toFloat()
    }
}
