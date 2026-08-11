package org.dhamma.gong.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime

class LivenessTest {

    private val zone = ZoneId.of("Asia/Kolkata")
    private val now = ZonedDateTime.of(2026, 8, 11, 4, 0, 0, 0, zone)

    private fun ago(seconds: Long) = now.minusSeconds(seconds)

    @Test
    fun aRecentTickIsAlive() {
        assertEquals(Liveness.Health.ALIVE, Liveness.health(ago(5), now))
        assertTrue(Liveness.isAlive(ago(29), now))
    }

    @Test
    fun oneOrTwoMissedHeartbeatsAreStillAlive() {
        // 30 s jitter on a loaded tablet is normal. Crying wolf here would
        // train staff to ignore the warning that actually matters.
        assertTrue(Liveness.isAlive(ago(35), now))
        assertTrue(Liveness.isAlive(ago(70), now))
    }

    @Test
    fun threeMissedHeartbeatsAreStale() {
        assertFalse(Liveness.isAlive(ago(91), now))
        assertEquals(Liveness.Health.STALE, Liveness.health(ago(600), now))
    }

    @Test
    fun theBoundaryItselfIsStillAlive() {
        // Exactly 90 s is the last good tick, not the first bad one.
        assertEquals(Liveness.Health.ALIVE, Liveness.health(ago(90), now))
        assertEquals(Liveness.Health.STALE, Liveness.health(ago(91), now))
    }

    @Test
    fun noTickEverIsUnknownNotStale() {
        // A service that has not started yet has not failed. Painting it red
        // on first launch would be a lie.
        assertEquals(Liveness.Health.UNKNOWN, Liveness.health(null, now))
        assertFalse(Liveness.isAlive(null, now))
    }

    @Test
    fun aTickFromTheFutureIsNotReportedAsStale() {
        // The clock moved backwards. That is the clock-trust problem and it has
        // its own banner; raising a second, wrong alarm about liveness would
        // send staff hunting the wrong fault.
        assertEquals(Liveness.Health.ALIVE, Liveness.health(now.plusMinutes(5), now))
    }

    @Test
    fun theWindowIsOverridable() {
        assertEquals(
            Liveness.Health.STALE,
            Liveness.health(ago(11), now, staleAfter = Duration.ofSeconds(10)),
        )
    }

    @Test
    fun ageLabelsReadLikeSomeoneSaidThem() {
        assertEquals("never", Liveness.ageLabel(null, now))
        assertEquals("12s ago", Liveness.ageLabel(ago(12), now))
        assertEquals("4m ago", Liveness.ageLabel(ago(4 * 60 + 30), now))
        assertEquals("2h ago", Liveness.ageLabel(ago(2 * 3600 + 60), now))
        assertEquals("just now", Liveness.ageLabel(now.plusSeconds(3), now))
    }
}
