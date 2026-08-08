package org.dhamma.gong.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class ActiveCourseTest {

    private val tenDay = CourseType(1, "10 Day", totalDays = 11, anapanaDays = 3)
    private val types = mapOf(1 to tenDay)

    @Test
    fun findsCourseInWindowNotOnlyStartDay() {
        val start = LocalDate.of(2026, 8, 1)
        val courses = listOf(Course(10, 1, start))
        // day 4
        val ctx = ActiveCourse.resolve(courses, types, start.plusDays(4))
        assertEquals(4, ctx!!.day)
        assertEquals(10L, ctx.courseId)
        assertEquals("10 Day", ctx.typeName)
    }

    @Test
    fun outsideWindow_returnsNull() {
        val start = LocalDate.of(2026, 8, 1)
        val courses = listOf(Course(10, 1, start))
        // total_days=11 → last day Aug 12; Aug 13 is out
        assertNull(ActiveCourse.resolve(courses, types, start.plusDays(12)))
    }

    @Test
    fun dayZero_arrival() {
        val start = LocalDate.of(2026, 8, 1)
        val ctx = ActiveCourse.resolve(listOf(Course(1, 1, start)), types, start)
        assertEquals(0, ctx!!.day)
    }

    @Test
    fun prefersMostRecentWhenOverlap() {
        val a = Course(1, 1, LocalDate.of(2026, 7, 1))
        val b = Course(2, 1, LocalDate.of(2026, 7, 5))
        val today = LocalDate.of(2026, 7, 8)
        val ctx = ActiveCourse.resolve(listOf(a, b), types, today)
        assertEquals(2L, ctx!!.courseId)
    }

    @Test
    fun pinnedOverride() {
        val a = Course(1, 1, LocalDate.of(2026, 7, 1))
        val b = Course(2, 1, LocalDate.of(2026, 7, 5))
        val today = LocalDate.of(2026, 7, 8)
        val ctx = ActiveCourse.resolve(listOf(a, b), types, today, activeCourseId = "1")
        assertEquals(1L, ctx!!.courseId)
    }
}
