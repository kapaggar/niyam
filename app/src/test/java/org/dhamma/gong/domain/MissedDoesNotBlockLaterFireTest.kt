package org.dhamma.gong.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * A miss must not consume the double-fire guard.
 *
 * Found on a real tablet: the appliance was running in `America/New_York`, the
 * device timezone became `America/Los_Angeles`, and the day re-materialized
 * three hours earlier. Every occurrence that had passed in New York was logged
 * `missed` — correct — but the miss also wrote `fired:<key>:<date>`. When
 * 21:00 *Los Angeles* genuinely came round, that guard already existed, so the
 * gong resolved to ALREADY_FIRED and was suppressed. No sound, no log row,
 * nothing to see. The hall got silence.
 *
 * The two marks answer different questions and must not be the same row:
 *
 *   - `fired:` means "this actually made a noise" and exists to guarantee
 *     never-twice. Sacred.
 *   - `missed:` means "we have already logged that this was missed" and exists
 *     only to stop the 30 s heartbeat flooding the log.
 *
 * Anything that moves the wall clock — a timezone change, an NTP correction —
 * can make a missed occurrence genuinely due again. It must be allowed to ring.
 */
class MissedDoesNotBlockLaterFireTest {

    private val ny = ZoneId.of("America/New_York")
    private val la = ZoneId.of("America/Los_Angeles")
    private val day = LocalDate.of(2026, 8, 11)

    /** One 21:00 gong on the no-course schedule, so no course window is needed. */
    private fun snapshot() = ScheduleSnapshot(
        courses = emptyList(),
        typesById = emptyMap(),
        events = listOf(
            ScheduleEvent(
                id = 51,
                courseTypeId = null,
                dayNo = null,
                timeLocal = LocalTime.of(21, 0),
                repeats = 3,
            ),
        ),
        settings = SettingsDefaults.all + mapOf("no_course_doha" to "off"),
    )

    private fun tickAt(
        zone: ZoneId,
        at: LocalTime,
        fired: MutableSet<String>,
        missed: MutableSet<String>,
    ): TickOutcome {
        val now = ZonedDateTime.of(day, at, zone)
        val outcome = SchedulerCore.tick(
            clock = SystemGongClock { zone },
            now = now,
            snapshot = snapshot(),
            firedGuard = { k, d -> FiredMark(k, d).stateKey in fired },
            missedGuard = { k, d -> MissedMark(k, d).stateKey in missed },
        )
        // Persist exactly as GongRepository.applyOutcome does.
        outcome.marks.forEach { fired += it.stateKey }
        outcome.missedMarks.forEach { missed += it.stateKey }
        return outcome
    }

    @Test
    fun aTimezoneShiftLetsTheGongRingRatherThanSilencingIt() {
        val fired = mutableSetOf<String>()
        val missed = mutableSetOf<String>()

        // 22:33 in New York: the 21:00 gong is 93 minutes past, well outside
        // the 120 s grace, so it is logged missed and NOT played.
        val inNewYork = tickAt(ny, LocalTime.of(22, 33), fired, missed)
        assertEquals("must not blast a 93-minute-late gong", 0, inNewYork.fired.size)
        assertEquals(
            1,
            inNewYork.logs.count {
                it.result == PlayResult.MISSED && it.kind == PlayKind.GONG
            },
        )
        assertTrue("the miss must not consume the fire guard", fired.isEmpty())

        // The tablet's zone becomes Los Angeles. 21:00 LA now genuinely arrives.
        val inLosAngeles = tickAt(la, LocalTime.of(21, 0), fired, missed)
        assertEquals("the gong must ring", 1, inLosAngeles.fired.size)
        assertEquals(3, inLosAngeles.fired.single().repeats)
        assertTrue("and only now is the fire guard written", fired.isNotEmpty())
    }

    @Test
    fun aRepeatedMissIsNotLoggedTwice() {
        // The heartbeat runs every 30 s. Without the missed mark the log would
        // gain a row per tick for the rest of the day.
        val fired = mutableSetOf<String>()
        val missed = mutableSetOf<String>()

        val first = tickAt(ny, LocalTime.of(22, 33), fired, missed)
        val second = tickAt(ny, LocalTime.of(22, 34), fired, missed)

        assertEquals(
            1,
            first.logs.count { it.result == PlayResult.MISSED && it.kind == PlayKind.GONG },
        )
        assertEquals("already logged once", 0, second.logs.size)
    }

    @Test
    fun aRealFireStillBlocksASecondOne() {
        // The rule this whole change must not weaken.
        val fired = mutableSetOf<String>()
        val missed = mutableSetOf<String>()

        val rang = tickAt(la, LocalTime.of(21, 0), fired, missed)
        assertEquals(1, rang.fired.size)

        val again = tickAt(la, LocalTime.of(21, 1), fired, missed)
        assertEquals("never twice", 0, again.fired.size)
        assertEquals(0, again.logs.size)
    }

    @Test
    fun aMissedOccurrenceThatBecomesDueIsStillRefusedIfItAlreadyRang() {
        // Fire it, then wind the zone back so it looks due again. The fired
        // guard, not the missed mark, is what must hold the line.
        val fired = mutableSetOf<String>()
        val missed = mutableSetOf<String>()

        tickAt(la, LocalTime.of(21, 0), fired, missed)
        val shifted = tickAt(ny, LocalTime.of(21, 0), fired, missed)
        assertEquals(0, shifted.fired.size)
    }
}
