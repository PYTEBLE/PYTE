package dev.pyte.playback

import org.junit.Assert.*
import org.junit.Test

class EqualizerControllerTest {
    private class Device : EqualizerDevice {
        override val frequencies = listOf(60, 230, 910, 3600, 14000)
        override val range = -1500..1500
        override var hasControl = true
        val levels = mutableMapOf<Int, Int>()
        var active = false
        var released = false
        var control: ((Boolean) -> Unit)? = null
        override fun level(band: Int, gainMb: Int) { levels[band] = gainMb }
        override fun enable(enabled: Boolean) { active = enabled }
        override fun controlListener(listener: (Boolean) -> Unit) { control = listener }
        override fun release() { released = true }
    }

    @Test fun `never attaches to global output and releases previous sessions`() {
        val devices = mutableListOf<Device>()
        val controller = EqualizerController { id -> assertTrue(id > 0); Device().also { devices += it } }
        controller.attach(0)
        controller.attach(-1)
        assertTrue(devices.isEmpty())
        controller.attach(12)
        controller.attach(12)
        assertEquals(1, devices.size)
        controller.attach(13)
        assertTrue(devices[0].released)
        controller.release()
        assertTrue(devices[1].released)
        assertFalse(controller.state.value.available)
    }

    @Test fun `saved curve reapplies to new sessions with headroom and bypass restores unity`() {
        val devices = mutableListOf<Device>()
        val controller = EqualizerController { Device().also { devices += it } }
        val bass = EqualizerSettings(enabled = true, points = EqualizerCurves.presets.first { it.id == "bass" }.points)
        controller.apply(bass)
        controller.attach(20)
        assertTrue(devices.last().active)
        assertTrue(devices.last().levels.getValue(0) > 0)
        assertTrue(controller.state.value.headroom < 1f)
        controller.attach(21)
        assertTrue(devices.last().active)
        controller.apply(bass.copy(enabled = false))
        assertFalse(devices.last().active)
        assertEquals(1f, controller.state.value.headroom)
        controller.release()
    }

    @Test fun `unsupported effects fail safely and can retry`() {
        var fail = true
        val controller = EqualizerController { if (fail) throw UnsupportedOperationException(); Device() }
        controller.attach(2)
        assertFalse(controller.state.value.available)
        assertEquals(1f, controller.state.value.headroom)
        fail = false
        controller.retry()
        assertTrue(controller.state.value.available)
    }

    @Test fun `control loss is reflected and stale callbacks cannot change a new session`() {
        val devices = mutableListOf<Device>()
        val controller = EqualizerController { Device().also { devices += it } }
        controller.attach(2)
        devices[0].hasControl = false
        devices[0].control!!(false)
        assertFalse(controller.state.value.available)
        controller.attach(3)
        devices[0].control!!(false)
        assertTrue(controller.state.value.available)
    }

    @Test fun `frequency interpolation is logarithmic and stored values are bounded`() {
        assertEquals(300, EqualizerCurves.gainAt(listOf(EqPoint(100, 0), EqPoint(10000, 600)), 1000))
        assertEquals(600, EqualizerCurves.gainAt(listOf(EqPoint(100, 9000)), 10000))
        assertEquals(EqualizerCurves.flat, EqualizerCurves.normalize(listOf(EqPoint(-2, 0))))
        assertEquals(1f, EqualizerCurves.headroom(listOf(-400, 0)))
    }

    @Test fun `effect failures release native resources and restore bypass volume`() {
        val device = Device()
        val controller = EqualizerController {
            object : EqualizerDevice by device {
                override fun level(band: Int, gainMb: Int) { throw IllegalStateException("Effect rejected band") }
            }
        }
        controller.apply(EqualizerSettings(enabled = true))
        controller.attach(5)
        assertTrue(device.released)
        assertFalse(device.active)
        assertFalse(controller.state.value.available)
        assertEquals(1f, controller.state.value.headroom)
    }
}
