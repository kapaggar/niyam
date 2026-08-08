package org.dhamma.gong.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class FireRulesTest {

    private val fireAt = LocalDateTime.of(2026, 8, 1, 4, 0)

    @Test
    fun tooEarly() {
        val d = FireRules.decide(fireAt.minusSeconds(1), fireAt, alreadyFired = false, clockOk = true)
        assertEquals(FireDecision.TOO_EARLY, d)
    }

    @Test
    fun fireOnTime() {
        assertEquals(
            FireDecision.FIRE,
            FireRules.decide(fireAt, fireAt, alreadyFired = false, clockOk = true),
        )
    }

    @Test
    fun fireWithinGrace() {
        assertEquals(
            FireDecision.FIRE,
            FireRules.decide(fireAt.plusSeconds(120), fireAt, alreadyFired = false, clockOk = true),
        )
    }

    @Test
    fun missedAfterGrace() {
        assertEquals(
            FireDecision.MISSED,
            FireRules.decide(fireAt.plusSeconds(121), fireAt, alreadyFired = false, clockOk = true),
        )
    }

    @Test
    fun alreadyFired() {
        assertEquals(
            FireDecision.ALREADY_FIRED,
            FireRules.decide(fireAt.plusSeconds(10), fireAt, alreadyFired = true, clockOk = true),
        )
    }

    @Test
    fun clockInvalid() {
        assertEquals(
            FireDecision.SKIPPED_CLOCK,
            FireRules.decide(fireAt, fireAt, alreadyFired = false, clockOk = false),
        )
    }
}
