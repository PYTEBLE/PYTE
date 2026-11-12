package dev.pyte.ui.components

import org.junit.Assert.*
import org.junit.Test

class SliderFeedbackTest {
    private var now = 0L
    private val ticks = mutableListOf<Boolean>()
    private val slider = SliderFeedback(36, setOf(0, 24, 36), { now }, ticks::add)

    @Test fun initializationAndSubNotchMovementAreSilent() {
        slider.move(.5f)
        slider.start(.5f)
        slider.move(.5001f)
        assertTrue(ticks.isEmpty())
    }

    @Test fun eachCrossedNotchTicksButSkippedNotchesNeverQueue() {
        slider.start(.1f)
        slider.move(.2f)
        assertEquals(listOf(false), ticks)
        now += 50
        slider.move(.5f)
        assertEquals(listOf(false, false), ticks)
    }

    @Test fun fastMovementsAreRateLimitedInBothDirections() {
        slider.start(.1f)
        slider.move(.2f)
        now += 10
        slider.move(.3f)
        now += 10
        slider.move(.2f)
        assertEquals(1, ticks.size)
        now += 25
        slider.move(.3f)
        assertEquals(2, ticks.size)
    }

    @Test fun zeroCrossingAndBothEndsProduceDetents() {
        slider.start(.6f)
        slider.move(.7f)
        now += 50
        slider.move(1f)
        now += 50
        slider.move(0f)
        assertEquals(listOf(true, true, true), ticks)
    }

    @Test fun detentsAreAlsoBoundedDuringFastReversals() {
        slider.start(.6f)
        slider.move(.7f)
        now += 5
        slider.move(.6f)
        assertEquals(1, ticks.size)
        now += 25
        slider.move(.7f)
        assertEquals(2, ticks.size)
    }

    @Test fun identicalValuesAndRestingAtEndNeverBuzz() {
        slider.start(.9f)
        slider.move(1f)
        repeat(10) { now += 100; slider.move(1f) }
        assertEquals(listOf(true), ticks)
    }

    @Test fun newGestureStartsFromActualValueWithoutAStaleTick() {
        slider.start(.1f)
        slider.move(.2f)
        slider.start(.8f)
        slider.move(.8f)
        assertEquals(1, ticks.size)
        slider.move(.9f)
        assertEquals(2, ticks.size)
    }
}
