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
    val limiter: Boolean = true,
) {
    fun normalized() = copy(points = EqualizerCurves.normalize(points),
        profiles = profiles.take(12).distinctBy { it.id }.map {
            it.copy(name = it.name.trim().take(48), points = EqualizerCurves.normalize(it.points))
        }.filter { it.name.isNotBlank() })
}

object EqualizerCurves {
    val frequencies = listOf(25, 40, 63, 100, 160, 250, 400, 630, 1000, 1600, 2500, 4000, 6300, 10000, 16000)
    val flat = frequencies.map { EqPoint(it, 0) }
    private fun preset(id: String, name: String, vararg gains: Double) = SoundProfile(id, name,
        frequencies.zip(gains.toList()) { hz, gain -> EqPoint(hz, (gain * 100).roundToInt()) })
    val presets = listOf(
        SoundProfile("neutral", "Neutral", flat),
        preset("bass", "Bass", 5.0,4.5,4.0,3.5,2.5,1.5,.5,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0),
        preset("tiefbass", "Tiefbass", 6.0,5.5,5.0,4.5,3.0,1.5,0.0,-1.0,-1.5,-2.0,-2.0,-2.0,-2.0,-2.0,-2.0),
        preset("voices", "Stimmen", -3.0,-2.5,-2.0,-1.0,0.0,1.5,3.0,3.5,3.0,2.0,1.0,.5,0.0,-.5,-1.0),
        preset("warm", "Warm", 2.0,2.0,2.0,1.5,1.0,.5,0.0,-.5,-1.0,-1.5,-2.0,-2.0,-2.5,-3.0,-3.0),
        preset("brillanz", "Brillanz", 0.0,0.0,0.0,0.0,0.0,0.0,0.0,.5,1.0,1.5,2.0,3.0,3.5,4.0,4.0),
        preset("electronic", "Elektro", 4.0,3.5,2.0,0.0,-1.5,-1.0,0.0,1.0,2.0,3.0,3.0,2.5,2.0,3.0,3.5),
        preset("rock", "Rock", 3.5,3.0,2.0,.5,-1.0,-1.5,0.0,1.5,2.5,3.0,3.0,3.0,2.5,2.0,1.5),
        preset("pop", "Pop", -1.0,-.5,.5,1.5,2.5,2.5,2.0,1.0,0.0,-.5,-1.0,-.5,.5,1.0,1.0),
        preset("jazz", "Jazz", 1.5,1.5,1.0,1.0,-.5,-1.0,0.0,.5,1.0,1.5,2.0,2.0,2.0,1.5,1.0),
        preset("klassik", "Klassik", 0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,.5,1.0,1.5,2.0,2.0),
        preset("hiphop", "Hip-Hop", 5.0,4.5,3.5,2.0,.5,-1.0,-1.0,0.0,.5,1.0,1.5,1.5,1.5,1.5,1.5),
        preset("akustik", "Akustik", 1.0,1.0,1.5,1.5,1.5,1.0,.5,.5,1.0,1.5,2.0,2.5,2.5,2.0,1.5),
        preset("podcast", "Podcast", -6.0,-5.0,-4.0,-3.0,-1.0,1.0,3.0,3.5,3.0,2.0,0.0,-1.0,-2.0,-3.0,-4.0),
        preset("nacht", "Nachtmodus", 3.0,2.5,2.0,1.5,1.0,.5,0.0,-1.0,-2.0,-3.0,-4.0,-4.5,-5.0,-5.0,-5.0),
        preset("lautheit", "Lautheit", 4.0,3.5,3.0,2.0,1.0,0.0,-.5,-1.0,-1.0,-.5,0.0,.5,1.0,1.5,1.5),
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
