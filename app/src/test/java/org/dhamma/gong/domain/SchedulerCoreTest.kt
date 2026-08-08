package org.dhamma.gong.domain

import org.dhamma.gong.domain.Fixtures.IST
import org.dhamma.gong.domain.Fixtures.TEN_DAY
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import kotlin.random.Random

/**
 * Behavioural oracle ported from ng/tests/test_scheduler.py.
 * Every assertion here is a Gong-NG guarantee, not an Android detail.
 */
class SchedulerCoreTest {

    private val start = LocalDate.of(2026, 8, 1)
    private val courses = listOf(Course(10, TEN_DAY.id, start))

    private fun tickAt(
        now: ZonedDateTime,
        state: FakeState = FakeState(),
        settings: Map<String, String> = emptyMap(),
        mappedDohaSlots: Set<Int> = (1..11).toSet(),
        clockTrusted: Boolean = true,
    ): TickOutcome = SchedulerCore.tick(
        clock = Fixtures.clock(now),
        now = now,
        snapshot = Fixtures.snapshot(
            courses = courses,
            settings = settings,
            mappedDohaSlots = mappedDohaSlots,
        ),
        firedGuard = state::wasFired,
        clockTrusted = clockTrusted,
        random = Random(7),
    )

    // ---------------------------------------------------------------- firing

    @Test
    fun firesExactlyOnTime() {
        val out = tickAt(Fixtures.ist(start.plusDays(2), 4, 0))
        val gong = out.fired.single { it.kind == PlayKind.GONG }
        assertEquals(16, gong.repeats)
        assertEquals("ting", gong.trackStem)
        assertEquals(4, gong.gapSeconds) // inherited from gong_gap_seconds
        assertEquals("10 Day course, Day 2, 04:00 x16", gong.label)
    }

    @Test
    fun neverFiresEarly() {
        val out = tickAt(Fixtures.ist(start.plusDays(2), 3, 59))
        assertTrue(out.fired.isEmpty())
        assertTrue(out.marks.isEmpty())
        assertEquals(Fixtures.ist(start.plusDays(2), 4, 0), out.nextDeadline)
    }

    @Test
    fun firesAtTheEdgeOfGrace() {
        val out = tickAt(Fixtures.ist(start.plusDays(2), 4, 0).plusSeconds(120))
        assertEquals(1, out.fired.size)
        assertTrue(out.logs.none { it.result == PlayResult.MISSED })
    }

    @Test
    fun logsMissedOneSecondPastGrace() {
        val out = tickAt(Fixtures.ist(start.plusDays(2), 4, 0).plusSeconds(121))
        assertTrue(out.fired.isEmpty())
        val missed = out.logs.single { it.result == PlayResult.MISSED }
        assertEquals(PlayKind.GONG, missed.kind)
        assertEquals(16, missed.repeats)
        assertTrue(missed.detail.startsWith("scheduled 2026-08-03T04:00"))
        // Still consumes the slot, so it cannot fire later in the day.
        assertTrue(out.marks.any { it.key == "g4" || it.key.startsWith("g") })
    }

    // ------------------------------------------------------------ guard

    @Test
    fun doubleFireGuardHoldsAcrossTicks() {
        val state = FakeState()
        val now = Fixtures.ist(start.plusDays(2), 4, 0)
        val first = tickAt(now, state)
        assertEquals(1, first.fired.size)

        state.applyMarks(first.marks)

        val second = tickAt(now.plusSeconds(30), state)
        assertTrue("must not re-fire within the same grace window", second.fired.isEmpty())
        assertTrue(second.logs.isEmpty())
    }

    @Test
    fun marksAreEmittedBeforeCommands() {
        // The contract the service relies on: every fired command has a mark.
        val out = tickAt(Fixtures.ist(start.plusDays(2), 4, 0))
        assertEquals(out.fired.size, out.marks.size)
    }

    // ------------------------------------------------------------ clock trust

    @Test
    fun untrustedClockSuppressesEverything() {
        val out = tickAt(Fixtures.ist(start.plusDays(2), 4, 0), clockTrusted = false)
        assertTrue(out.fired.isEmpty())
        assertTrue(out.marks.isEmpty())
        assertTrue(out.logs.isEmpty())
        assertNull(out.nextDeadline)
    }

    // ------------------------------------------------------------ toggles

    @Test
    fun masterDisabledStillConsumesTheSlot() {
        val out = tickAt(
            Fixtures.ist(start.plusDays(2), 4, 0),
            settings = mapOf("enabled" to "0"),
        )
        assertTrue(out.fired.isEmpty())
        // NG marks fired before dispatch: re-enabling mid-window must not
        // retro-fire a gong the staff deliberately silenced.
        assertTrue(out.marks.isNotEmpty())
    }

    @Test
    fun gongDisabledDohaStillFires() {
        val out = tickAt(
            Fixtures.ist(start.plusDays(2), 6, 37),
            settings = mapOf("gong_enabled" to "0"),
        )
        assertEquals(1, out.fired.size)
        assertEquals(PlayKind.DOHA, out.fired.single().kind)
    }

    // ------------------------------------------------------------ doha

    @Test
    fun dohaUsesLegacyModularSlotAndDohaVolume() {
        val out = tickAt(Fixtures.ist(start.plusDays(5), 6, 37))
        val doha = out.fired.single { it.kind == PlayKind.DOHA }
        assertEquals(DohaSlots.legacyModular(5, 11, 3), doha.dohaSlot)
        assertEquals(75, doha.volume)
        assertEquals(1, doha.repeats)
    }

    @Test
    fun noSlotsMappedIsGongsOnlyNotAnError() {
        val out = tickAt(Fixtures.ist(start.plusDays(5), 6, 37), mappedDohaSlots = emptySet())
        assertTrue(out.fired.none { it.kind == PlayKind.DOHA })
        assertTrue(
            "gongs-only must not log an error",
            out.logs.none { it.kind == PlayKind.DOHA },
        )
        // The doha slot is still consumed, so a later tick does not retry it.
        assertTrue(out.marks.any { it.key == "doha" })
    }

    @Test
    fun missingSlotLogsErrorNeverCrashes() {
        val out = tickAt(
            Fixtures.ist(start.plusDays(5), 6, 37),
            mappedDohaSlots = setOf(1, 2, 3), // day 5 wants slot 5
        )
        assertTrue(out.fired.none { it.kind == PlayKind.DOHA })
        val err = out.logs.single { it.kind == PlayKind.DOHA }
        assertEquals(PlayResult.ERROR, err.result)
        assertEquals("slot not mapped", err.detail)
        assertEquals("slot 5", err.file)
    }

    @Test
    fun dohaOffOutsideCourseIsSilent() {
        val out = SchedulerCore.tick(
            clock = Fixtures.clock(Fixtures.ist(LocalDate.of(2026, 12, 1), 6, 37)),
            now = Fixtures.ist(LocalDate.of(2026, 12, 1), 6, 37),
            snapshot = Fixtures.snapshot(settings = mapOf("no_course_doha" to "off")),
            firedGuard = { _, _ -> false },
        )
        assertTrue(out.fired.none { it.kind == PlayKind.DOHA })
    }

    // ------------------------------------------------------------ precedence

    @Test
    fun explicitDayOverridesDefaultPattern() {
        // Day 4 has only 04:00 and 04:20 defined explicitly, so 11:00 must not fire.
        val out = tickAt(Fixtures.ist(start.plusDays(4), 11, 0))
        assertTrue(out.fired.none { it.kind == PlayKind.GONG })
    }

    @Test
    fun noCourseUsesTheNoCourseSchedule() {
        val day = LocalDate.of(2026, 12, 1) // well outside the course window
        val out = SchedulerCore.tick(
            clock = Fixtures.clock(Fixtures.ist(day, 6, 30)),
            now = Fixtures.ist(day, 6, 30),
            snapshot = Fixtures.snapshot(courses = courses),
            firedGuard = { _, _ -> false },
            random = Random(1),
        )
        val gong = out.fired.single { it.kind == PlayKind.GONG }
        assertEquals(3, gong.repeats)
        assertEquals("No course, 06:30 x3", gong.label)
    }

    // ------------------------------------------------------------ inheritance

    @Test
    fun perEventGapAndTrackOverrideSettings() {
        val events = listOf(
            ScheduleEvent(99, TEN_DAY.id, null, LocalTime.of(9, 0), 5, gapSeconds = 2, track = "drum"),
        )
        val now = Fixtures.ist(start.plusDays(2), 9, 0)
        val out = SchedulerCore.tick(
            clock = Fixtures.clock(now),
            now = now,
            snapshot = Fixtures.snapshot(courses = courses, events = events),
            firedGuard = { _, _ -> false },
        )
        val gong = out.fired.single { it.kind == PlayKind.GONG }
        assertEquals(2, gong.gapSeconds)
        assertEquals("drum", gong.trackStem)
    }

    // ------------------------------------------------------------ deadline

    @Test
    fun nextDeadlineIsTheEarliestUnfiredOccurrence() {
        val now = Fixtures.ist(start.plusDays(2), 12, 0)
        val out = tickAt(now)
        assertNotNull(out.nextDeadline)
        assertEquals(Fixtures.ist(start.plusDays(2), 12, 50), out.nextDeadline)
    }

    @Test
    fun deadlineRollsIntoTomorrowAfterTheLastEvent() {
        val out = tickAt(Fixtures.ist(start.plusDays(2), 23, 0))
        assertEquals(Fixtures.ist(start.plusDays(3), 4, 0), out.nextDeadline)
    }

    @Test
    fun zoneIsPreservedOnTheDeadline() {
        val out = tickAt(Fixtures.ist(start.plusDays(2), 23, 0))
        assertEquals(IST, out.nextDeadline!!.zone)
    }
}
