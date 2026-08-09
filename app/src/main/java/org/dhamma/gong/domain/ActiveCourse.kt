package org.dhamma.gong.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Port of the Pi daemon's model.py active_course (design §5.2).
 * Matches course *window*, not only start day.
 */
object ActiveCourse {

    fun resolve(
        courses: List<Course>,
        typesById: Map<Int, CourseType>,
        today: LocalDate,
        activeCourseId: String? = null,
    ): CourseCtx? {
        val candidates = mutableListOf<Pair<LocalDate, Course>>()
        for (c in courses) {
            val type = typesById[c.courseTypeId] ?: continue
            val end = c.startDate.plusDays(type.totalDays.toLong())
            if (!today.isBefore(c.startDate) && !today.isAfter(end)) {
                candidates += c.startDate to c
            }
        }
        if (candidates.isEmpty()) return null

        val pinned = activeCourseId?.takeIf { it.isNotBlank() }
        var chosen: Pair<LocalDate, Course>? = null
        if (pinned != null) {
            chosen = candidates.firstOrNull { it.second.id.toString() == pinned }
        }
        if (chosen == null) {
            // most recent start wins (same as the Pi daemon: max by start)
            chosen = candidates.maxBy { it.first }
        }

        val (start, course) = chosen
        val type = typesById.getValue(course.courseTypeId)
        val day = ChronoUnit.DAYS.between(start, today).toInt()
        return CourseCtx(
            courseId = course.id,
            typeId = type.id,
            typeName = type.name,
            startDate = start,
            totalDays = type.totalDays,
            anapanaDays = type.anapanaDays,
            day = day,
        )
    }
}
