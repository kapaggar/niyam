package org.dhamma.gong.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class ScheduleMaterializerTest {

    private val type = CourseType(1, "10 Day", 11, 3)
    private val types = mapOf(1 to type)

    private val events = listOf(
        ScheduleEvent(1, 1, 0, LocalTime.of(4, 0), 16),
        ScheduleEvent(2, 1, 4, LocalTime.of(4, 0), 16),
        ScheduleEvent(3, 1, 4, LocalTime.of(4, 20), 12),
        // default pattern (day_no null)
        ScheduleEvent(4, 1, null, LocalTime.of(4, 0), 16),
        ScheduleEvent(5, 1, null, LocalTime.of(11, 0), 6),
        // no-course
        ScheduleEvent(6, null, null, LocalTime.of(6, 0), 3),
    )

    @Test
    fun explicitDayPreferredOverDefault() {
        val ctx = CourseCtx(1, 1, "10 Day", LocalDate.of(2026, 8, 1), 11, 3, day = 4)
        val rows = ScheduleMaterializer.eventsFor(ctx, events)
        assertEquals(listOf(2L, 3L), rows.map { it.id })
    }

    @Test
    fun fallsBackToDefaultPattern() {
        val ctx = CourseCtx(1, 1, "10 Day", LocalDate.of(2026, 8, 1), 11, 3, day = 5)
        val rows = ScheduleMaterializer.eventsFor(ctx, events)
        assertEquals(listOf(4L, 5L), rows.map { it.id })
    }

    @Test
    fun noCourseUsesNullType() {
        val rows = ScheduleMaterializer.eventsFor(null, events)
        assertEquals(listOf(6L), rows.map { it.id })
    }

    @Test
    fun materializeIncludesDoha() {
        val start = LocalDate.of(2026, 8, 1)
        val today = start.plusDays(4)
        val clock = Fixtures.clock(Fixtures.ist(today, 0, 0))
        val occ = ScheduleMaterializer.materialize(
            clock = clock,
            today = today,
            snapshot = ScheduleSnapshot(
                courses = listOf(Course(1, 1, start)),
                typesById = types,
                events = events,
                settings = SettingsDefaults.map,
            ),
            days = 1,
        )
        assertTrue(occ.any { it.kind == Occurrence.Kind.DOHA })
        assertTrue(occ.any { it.kind == Occurrence.Kind.GONG && it.key == "g2" })
        assertEquals(LocalTime.of(6, 37), occ.single { it.kind == Occurrence.Kind.DOHA }.fireAt.toLocalTime())
    }

    @Test
    fun materializeCoversTodayAndTomorrow() {
        val start = LocalDate.of(2026, 8, 1)
        val today = start.plusDays(4)
        val occ = ScheduleMaterializer.materialize(
            clock = Fixtures.clock(Fixtures.ist(today, 0, 0)),
            today = today,
            snapshot = ScheduleSnapshot(
                courses = listOf(Course(1, 1, start)),
                typesById = types,
                events = events,
                settings = SettingsDefaults.map,
            ),
            days = 2,
        )
        assertEquals(setOf(today, today.plusDays(1)), occ.map { it.localDate }.toSet())
        // sorted by fire instant
        assertEquals(occ.map { it.fireAt }, occ.map { it.fireAt }.sorted())
    }
}
