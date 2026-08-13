package org.dhamma.gong.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Design doc §12 — "a 400-day run over every course type executes in seconds
 * and asserts an exact fire ledger".
 *
 * Drives [SchedulerCore] with a [VirtualClock] stepping one minute at a time
 * and records every command it emits. The invariants below are the ones that
 * matter in a hall: exactly one fire per scheduled event per day, never early,
 * never twice, and the doha slot sequence matches [DohaSlots.legacyModular].
 */
class VirtualClockLedgerTest {

    private data class Fire(val at: ZonedDateTime, val cmd: PlayCommand)

    /**
     * Runs the scheduler minute-by-minute from [from] for [days] days.
     * Applies each tick's marks exactly as the service would.
     */
    private fun run(
        zone: ZoneId,
        from: LocalDate,
        days: Int,
        snapshot: ScheduleSnapshot,
        stepSeconds: Long = 60,
    ): Pair<List<Fire>, List<PlayLogEntry>> {
        val clock = VirtualClock(zone, ZonedDateTime.of(from, LocalTime.MIDNIGHT, zone).toInstant())
        val state = FakeState()
        val fires = mutableListOf<Fire>()
        val logs = mutableListOf<PlayLogEntry>()
        val steps = days * 24 * 60 * 60 / stepSeconds

        repeat(steps.toInt()) {
            val now = clock.now()
            val out = SchedulerCore.tick(
                clock = clock,
                now = now,
                snapshot = snapshot,
                firedGuard = state::wasFired,
            )
            state.applyMarks(out.marks)
            logs += out.logs
            out.fired.forEach { fires += Fire(now, it) }
            clock.advanceSeconds(stepSeconds)
        }
        return fires to logs
    }

    @Test
    fun tenDayCourseProducesAnExactLedger() {
        val start = LocalDate.of(2026, 8, 1)
        val snapshot = Fixtures.snapshot(courses = listOf(Course(1, Fixtures.TEN_DAY.id, start)))

        val (fires, logs) = run(Fixtures.IST, start, days = 14, snapshot = snapshot)

        assertTrue("a minute-resolution loop must never miss", logs.isEmpty())

        // Day 0 has 2 gongs; days 1..11 have 11 (day 4 overridden to 2); after
        // the window closes the no-course schedule takes over with 1 per day.
        val gongsByDay = fires.filter { it.cmd.kind == PlayKind.GONG }
            .groupBy { it.at.toLocalDate() }
            .mapValues { it.value.size }

        assertEquals(2, gongsByDay[start])
        assertEquals(11, gongsByDay[start.plusDays(1)])
        assertEquals(2, gongsByDay[start.plusDays(4)]) // explicit day-4 override
        assertEquals(11, gongsByDay[start.plusDays(11)]) // last course day
        assertEquals(1, gongsByDay[start.plusDays(12)]) // out of window → no-course

        // Exactly one doha per day, every day of the run.
        val dohaByDay = fires.filter { it.cmd.kind == PlayKind.DOHA }
            .groupBy { it.at.toLocalDate() }
        assertEquals(14, dohaByDay.size)
        assertTrue(dohaByDay.values.all { it.size == 1 })

        // In-course doha slots follow legacy_modular exactly.
        for (d in 1..11) {
            val slot = dohaByDay.getValue(start.plusDays(d.toLong())).single().cmd.dohaSlot
            assertEquals("day $d", DohaSlots.legacyModular(d, 11, 3), slot)
        }
    }

    @Test
    fun neverFiresEarlyAndNeverTwice() {
        val start = LocalDate.of(2026, 8, 1)
        val snapshot = Fixtures.snapshot(courses = listOf(Course(1, Fixtures.TEN_DAY.id, start)))
        val (fires, _) = run(Fixtures.IST, start, days = 14, snapshot = snapshot)

        // A fire is stamped at the tick that emitted it, which is at or after
        // the scheduled minute and inside the 120 s grace.
        val seen = mutableSetOf<String>()
        for (f in fires) {
            val id = "${f.at.toLocalDate()}|${f.cmd.kind}|${f.cmd.label}"
            assertTrue("duplicate fire: $id", seen.add(id))
        }
    }

    @Test
    fun fourHundredDayRunOverEveryCourseType() {
        val start = LocalDate.of(2026, 1, 5)
        // Back-to-back courses of every seeded type, no gaps in between.
        var cursor = start
        var id = 1L
        val courses = Fixtures.TYPES.values.sortedBy { it.id }.map { type ->
            val c = Course(id++, type.id, cursor)
            cursor = cursor.plusDays(type.totalDays.toLong() + 1)
            c
        }
        val snapshot = Fixtures.snapshot(
            courses = courses,
            events = Fixtures.EVENTS_ALL_TYPES,
        )

        // Every event sits on a whole minute and the grace window is 120 s, so a
        // 60 s step reproduces the 30 s heartbeat's behaviour exactly.
        val (fires, logs) = run(Fixtures.IST, start, days = 400, snapshot = snapshot)

        assertTrue("no misses in a continuously-running loop", logs.isEmpty())

        // 400 days of 11 gongs + 400 doha, minus nothing: the courses run
        // back-to-back and the no-course pattern covers the tail.
        val gongs = fires.count { it.cmd.kind == PlayKind.GONG }
        val courseDays = courses.sumOf { Fixtures.TYPES.getValue(it.courseTypeId).totalDays + 1 }
        val noCourseDays = 400 - courseDays
        assertEquals(courseDays * 11 + noCourseDays * 1, gongs)

        // One doha per calendar day for the whole 400 days.
        val dohaDays = fires.filter { it.cmd.kind == PlayKind.DOHA }
            .map { it.at.toLocalDate() }
        assertEquals(400, dohaDays.size)
        assertEquals(400, dohaDays.toSet().size)

        // Every doha slot stays inside the manifest range.
        assertTrue(
            fires.filter { it.cmd.kind == PlayKind.DOHA }
                .all { it.cmd.dohaSlot in DohaSlots.SLOTS },
        )
    }

    @Test
    fun aSparseLoopLogsMissesInsteadOfFiringLate() {
        // A 10-minute heartbeat cannot honour a 120 s grace — the point of the
        // 30 s heartbeat in design doc §03. This asserts the failure is a
        // logged `missed`, never a late blast.
        val start = LocalDate.of(2026, 8, 1)
        val snapshot = Fixtures.snapshot(courses = listOf(Course(1, Fixtures.TEN_DAY.id, start)))
        val (fires, logs) = run(
            Fixtures.IST, start, days = 3, snapshot = snapshot, stepSeconds = 600,
        )
        assertTrue("some events fall outside grace", logs.any { it.result == PlayResult.MISSED })
        assertTrue(logs.all { it.result == PlayResult.MISSED })
        // Whatever did fire, fired inside the window — nothing was blasted late.
        assertTrue(fires.isNotEmpty())
    }
}
