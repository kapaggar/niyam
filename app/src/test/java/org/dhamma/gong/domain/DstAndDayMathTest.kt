package org.dhamma.gong.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Regression tests for the legacy `/86400` day-math bug (CLAUDE.md finding #4)
 * and the DST rules in design doc §05.
 */
class DstAndDayMathTest {

    private val newYork = ZoneId.of("America/New_York")
    private val clock = object : GongClock {
        override val zone = newYork
        override fun now() = java.time.ZonedDateTime.now(newYork)
    }

    @Test
    fun springForwardGapResolvesToFirstValidInstant() {
        // 2026-03-08: 02:00 EST jumps to 03:00 EDT. 02:30 does not exist.
        val at = clock.materialize(LocalDate.of(2026, 3, 8), LocalTime.of(2, 30))
        assertEquals(LocalTime.of(3, 30), at.toLocalTime())
        assertEquals(ZoneOffset.ofHours(-4), at.offset)
    }

    @Test
    fun fallBackAmbiguityTakesTheFirstOccurrence() {
        // 2026-11-01: 01:30 happens twice. The Pi daemon takes fold=0 (the EDT one).
        val at = clock.materialize(LocalDate.of(2026, 11, 1), LocalTime.of(1, 30))
        assertEquals(LocalTime.of(1, 30), at.toLocalTime())
        assertEquals(ZoneOffset.ofHours(-4), at.offset)
    }

    @Test
    fun normalTimesAreUnaffected() {
        val at = clock.materialize(LocalDate.of(2026, 6, 1), LocalTime.of(4, 0))
        assertEquals(LocalTime.of(4, 0), at.toLocalTime())
    }

    @Test
    fun dayMathIsCalendarNotElapsedSeconds() {
        // A 23-hour spring-forward day. /86400 would report day 0 here.
        val zero = LocalDate.of(2026, 3, 7)
        assertEquals(1, currentDay(zero, LocalDate.of(2026, 3, 8)))
        assertEquals(2, currentDay(zero, LocalDate.of(2026, 3, 9)))
    }

    @Test
    fun dayMathAcrossAFullDstYear() {
        val zero = LocalDate.of(2026, 1, 1)
        assertEquals(365, currentDay(zero, LocalDate.of(2027, 1, 1)))
    }

    @Test
    fun courseDayIsStableAcrossTheDstBoundary() {
        val start = LocalDate.of(2026, 3, 5)
        val courses = listOf(Course(1, Fixtures.TEN_DAY.id, start))
        val ctx = ActiveCourse.resolve(courses, Fixtures.TYPES, LocalDate.of(2026, 3, 9))
        assertEquals(4, ctx!!.day)
    }
}
