package org.dhamma.gong.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Ported from ng/tests/test_clock.py — design doc §05 / §10. */
class ClockTrustTest {

    private val day = LocalDate.of(2026, 8, 3)

    @Test
    fun freshDeviceIsTrusted() {
        val state = FakeState()
        assertTrue(ClockTrust.checkOnStart(state, Fixtures.ist(day, 4, 0)))
    }

    @Test
    fun smallBackwardsDriftIsTolerated() {
        val state = FakeState()
        ClockTrust.touchLastGood(state, Fixtures.ist(day, 4, 0))
        // 9 minutes back — inside the 10-minute tolerance.
        assertTrue(ClockTrust.checkOnStart(state, Fixtures.ist(day, 3, 51)))
    }

    @Test
    fun bigBackwardsJumpMarksUntrusted() {
        val state = FakeState()
        ClockTrust.touchLastGood(state, Fixtures.ist(day, 10, 0))
        assertFalse(ClockTrust.checkOnStart(state, Fixtures.ist(day, 4, 0)))
        assertTrue(ClockTrust.isInvalid(state))
    }

    @Test
    fun watermarkDoesNotAdvanceWhileUntrusted() {
        val state = FakeState()
        ClockTrust.touchLastGood(state, Fixtures.ist(day, 10, 0))
        ClockTrust.checkOnStart(state, Fixtures.ist(day, 4, 0))

        ClockTrust.touchLastGood(state, Fixtures.ist(day, 4, 5))
        assertTrue("still untrusted", ClockTrust.isInvalid(state))
        // last_good must still be the pre-jump 10:00 instant.
        assertTrue(state.get(ClockTrust.LAST_GOOD_KEY)!!.contains("04:30")) // 10:00 IST = 04:30Z
    }

    @Test
    fun ntpStepForwardRestoresTrust() {
        val state = FakeState()
        ClockTrust.touchLastGood(state, Fixtures.ist(day, 10, 0))
        ClockTrust.checkOnStart(state, Fixtures.ist(day, 4, 0))
        assertTrue(ClockTrust.isInvalid(state))

        ClockTrust.touchLastGood(state, Fixtures.ist(day, 10, 1))
        assertFalse(ClockTrust.isInvalid(state))
    }

    @Test
    fun staffConfirmRestoresTrust() {
        val state = FakeState()
        state.put(ClockTrust.CLOCK_INVALID_KEY, "1")
        ClockTrust.confirm(state, Fixtures.ist(day, 4, 0))
        assertTrue(ClockTrust.isTrusted(state))
    }

    @Test
    fun shouldTouchRespectsInterval() {
        val t0 = Fixtures.ist(day, 4, 0)
        assertTrue(ClockTrust.shouldTouch(null, t0))
        assertFalse(ClockTrust.shouldTouch(t0, t0.plusMinutes(4)))
        assertTrue(ClockTrust.shouldTouch(t0, t0.plusMinutes(5)))
    }
}
