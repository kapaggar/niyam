package org.dhamma.gong.domain

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** Shared fixtures for the domain suite. IST is the real deployment zone (no DST). */
object Fixtures {

    val IST: ZoneId = ZoneId.of("Asia/Kolkata")

    /** Course types, verbatim from ng/seed/seed.sql. */
    val TEN_DAY = CourseType(1, "10 Day", totalDays = 11, anapanaDays = 3)
    val TWENTY_DAY = CourseType(2, "20 Day", totalDays = 21, anapanaDays = 7)
    val THIRTY_DAY = CourseType(3, "30 Day", totalDays = 31, anapanaDays = 10)
    val STP = CourseType(5, "STP", totalDays = 8, anapanaDays = 2)

    val TYPES: Map<Int, CourseType> = listOf(TEN_DAY, TWENTY_DAY, THIRTY_DAY, STP)
        .associateBy { it.id }

    /**
     * The real NG 10-day gong pattern (design handoff §1 copy notes), stored as
     * the mid-course default (`day_no = null`) plus an explicit day 0.
     */
    val EVENTS: List<ScheduleEvent> = buildList {
        var id = 1L
        // day 0 (arrival) — short pattern
        add(ScheduleEvent(id++, 1, 0, LocalTime.of(19, 30), 3))
        add(ScheduleEvent(id++, 1, 0, LocalTime.of(21, 0), 3))
        // mid-course default pattern
        val default = listOf(
            LocalTime.of(4, 0) to 16,
            LocalTime.of(4, 20) to 12,
            LocalTime.of(6, 32) to 3,
            LocalTime.of(7, 50) to 8,
            LocalTime.of(11, 0) to 6,
            LocalTime.of(12, 50) to 8,
            LocalTime.of(14, 10) to 1,
            LocalTime.of(14, 20) to 3,
            LocalTime.of(17, 0) to 6,
            LocalTime.of(17, 50) to 6,
            LocalTime.of(21, 0) to 3,
        )
        for ((t, n) in default) add(ScheduleEvent(id++, 1, null, t, n))
        // an explicit day 4 override, to exercise precedence
        add(ScheduleEvent(id++, 1, 4, LocalTime.of(4, 0), 16))
        add(ScheduleEvent(id++, 1, 4, LocalTime.of(4, 20), 12))
        // no-course schedule
        add(ScheduleEvent(id++, null, null, LocalTime.of(6, 30), 3))
    }

    /** The 11 wall-clock times of the real NG mid-course day. */
    val DEFAULT_PATTERN: List<Pair<LocalTime, Int>> = listOf(
        LocalTime.of(4, 0) to 16,
        LocalTime.of(4, 20) to 12,
        LocalTime.of(6, 32) to 3,
        LocalTime.of(7, 50) to 8,
        LocalTime.of(11, 0) to 6,
        LocalTime.of(12, 50) to 8,
        LocalTime.of(14, 10) to 1,
        LocalTime.of(14, 20) to 3,
        LocalTime.of(17, 0) to 6,
        LocalTime.of(17, 50) to 6,
        LocalTime.of(21, 0) to 3,
    )

    /** Same default pattern for every seeded type — used by the 400-day run. */
    val EVENTS_ALL_TYPES: List<ScheduleEvent> = buildList {
        var id = 1000L
        for (type in TYPES.values.sortedBy { it.id }) {
            for ((t, n) in DEFAULT_PATTERN) add(ScheduleEvent(id++, type.id, null, t, n))
        }
        add(ScheduleEvent(id, null, null, LocalTime.of(6, 30), 3))
    }

    fun snapshot(
        courses: List<Course> = emptyList(),
        events: List<ScheduleEvent> = EVENTS,
        settings: Map<String, String> = emptyMap(),
        mappedDohaSlots: Set<Int> = (1..11).toSet(),
    ) = ScheduleSnapshot(
        courses = courses,
        typesById = TYPES,
        events = events,
        settings = SettingsDefaults.map + settings,
        mappedDohaSlots = mappedDohaSlots,
    )

    fun clock(at: ZonedDateTime): VirtualClock = VirtualClock(at.zone, at.toInstant())

    fun ist(date: LocalDate, h: Int, m: Int): ZonedDateTime =
        ZonedDateTime.of(date, LocalTime.of(h, m), IST)
}

/** A [KeyValueState] backed by a mutable map, with the fired-guard helpers. */
class FakeState : KeyValueState {
    private val map = mutableMapOf<String, String>()
    override fun get(key: String): String? = map[key]
    override fun put(key: String, value: String) {
        map[key] = value
    }

    override fun remove(key: String) {
        map.remove(key)
    }

    fun wasFired(key: String, date: LocalDate): Boolean =
        map.containsKey(FiredMark(key, date).stateKey)

    fun applyMarks(marks: List<FiredMark>) {
        marks.forEach { map[it.stateKey] = "fired" }
    }

    fun firedKeys(): Set<String> = map.keys.filter { it.startsWith("fired:") }.toSet()
}
