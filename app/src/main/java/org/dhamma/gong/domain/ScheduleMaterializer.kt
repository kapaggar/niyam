package org.dhamma.gong.domain

import java.time.LocalDate
import java.time.LocalTime

/**
 * Port of the Pi daemon's scheduler.py `upcoming_occurrences` +
 * the Pi daemon's model.py `events_for` (design §3.1).
 */
object ScheduleMaterializer {

    /**
     * Effective rows for a context, in the Pi daemon's precedence order:
     * explicit day → default pattern (`day_no IS NULL`) → no-course set.
     */
    fun eventsFor(ctx: CourseCtx?, allEvents: List<ScheduleEvent>): List<ScheduleEvent> {
        if (ctx == null) {
            return allEvents
                .filter { it.courseTypeId == null && it.dayNo == null }
                .sortedWith(compareBy({ it.timeLocal }, { it.id }))
        }
        val explicit = allEvents.filter { it.courseTypeId == ctx.typeId && it.dayNo == ctx.day }
        if (explicit.isNotEmpty()) {
            return explicit.sortedWith(compareBy({ it.timeLocal }, { it.id }))
        }
        return allEvents
            .filter { it.courseTypeId == ctx.typeId && it.dayNo == null }
            .sortedWith(compareBy({ it.timeLocal }, { it.id }))
    }

    /**
     * Materialize gong + synthetic doha occurrences for [today] and the next
     * [days] - 1 dates, sorted by fire time.
     *
     * Does **not** filter by fired state — [SchedulerCore] does that, because
     * the guard has to be read in the same transaction it is written in.
     */
    fun materialize(
        clock: GongClock,
        today: LocalDate,
        snapshot: ScheduleSnapshot,
        days: Int = 2,
    ): List<Occurrence> {
        val dohaTime = parseHhMm(snapshot.setting("doha_time")) ?: LocalTime.of(6, 37)
        val activeCourseId = snapshot.setting("active_course_id").takeIf { it.isNotBlank() }
        val out = ArrayList<Occurrence>()

        for (offset in 0 until days) {
            val day = today.plusDays(offset.toLong())
            val ctx = ActiveCourse.resolve(snapshot.courses, snapshot.typesById, day, activeCourseId)
            for (row in eventsFor(ctx, snapshot.events)) {
                out += Occurrence(
                    key = "g${row.id}",
                    kind = Occurrence.Kind.GONG,
                    fireAt = clock.materialize(day, row.timeLocal),
                    localDate = day,
                    repeats = row.repeats,
                    gapSeconds = row.gapSeconds,
                    track = row.track,
                    ctx = ctx,
                )
            }
            out += Occurrence(
                key = "doha",
                kind = Occurrence.Kind.DOHA,
                fireAt = clock.materialize(day, dohaTime),
                localDate = day,
                ctx = ctx,
            )
        }
        return out.sortedBy { it.fireAt.toInstant() }
    }

    /** Tolerant "HH:mm" / "HH:mm:ss" parse; null when unparseable. */
    fun parseHhMm(value: String): LocalTime? = runCatching { LocalTime.parse(value) }.getOrNull()
}
