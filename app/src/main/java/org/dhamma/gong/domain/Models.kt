package org.dhamma.gong.domain

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

/** Active course resolved for a local calendar date (Pi daemon CourseCtx). */
data class CourseCtx(
    val courseId: Long,
    val typeId: Int,
    val typeName: String,
    val startDate: LocalDate,
    val totalDays: Int,
    val anapanaDays: Int,
    /** 0 = arrival day. */
    val day: Int,
) {
    /** "10 Day course, Day 3" — the label the Pi daemon puts in play_log. */
    val label: String get() = "$typeName course, Day $day"
}

data class CourseType(
    val id: Int,
    val name: String,
    val totalDays: Int,
    val anapanaDays: Int,
)

data class Course(
    val id: Long,
    val courseTypeId: Int,
    val startDate: LocalDate,
    val note: String = "",
)

/**
 * One schedule row. [dayNo] null = default mid-course pattern.
 * [courseTypeId] null = no-course schedule.
 *
 * [gapSeconds] and [track] are nullable *by design* — null means "inherit the
 * setting" (design handoff §3: the em-dash option is load-bearing).
 */
data class ScheduleEvent(
    val id: Long,
    val courseTypeId: Int?,
    val dayNo: Int?,
    val timeLocal: LocalTime,
    val repeats: Int,
    val gapSeconds: Int? = null,
    val track: String? = null,
)

data class Occurrence(
    /** e.g. "g12" or "doha" */
    val key: String,
    val kind: Kind,
    val fireAt: ZonedDateTime,
    val localDate: LocalDate,
    val repeats: Int = 1,
    val gapSeconds: Int? = null,
    val track: String? = null,
    val ctx: CourseCtx? = null,
) {
    enum class Kind { GONG, DOHA }
}

enum class FireDecision {
    /** Within grace window and not yet fired — play now. */
    FIRE,

    /** Already fired this local date. */
    ALREADY_FIRED,

    /** now < fireAt — wait. */
    TOO_EARLY,

    /** now > fireAt + grace — log missed, do not play. */
    MISSED,

    /** Clock untrusted — skip automatic play. */
    SKIPPED_CLOCK,
}

/** play_log.kind values (Pi parity). */
object PlayKind {
    const val GONG = "gong"
    const val DOHA = "doha"
    const val TEST_GONG = "test_gong"
    const val TEST_DOHA = "test_doha"
}

/** play_log.result values (Pi parity). */
object PlayResult {
    const val OK = "ok"
    const val MISSED = "missed"
    const val ERROR = "error"
    const val STOPPED = "stopped"
    const val SKIPPED_CLOCK = "skipped_clock"
}

/**
 * A resolved instruction for the player. The scheduler emits these; the
 * Android PlayerEngine renders them. Nothing here is Android-specific.
 */
data class PlayCommand(
    val kind: String,
    /** Gong track stem ("ting"/"drum"); null for doha. */
    val trackStem: String? = null,
    /** Doha slot 1..11; null for gong. */
    val dohaSlot: Int? = null,
    val repeats: Int = 1,
    val gapSeconds: Int = 0,
    /** 0..100 app-level gain. */
    val volume: Int = 90,
    val label: String = "",
)

/** A row to append to play_log. */
data class PlayLogEntry(
    val kind: String,
    val file: String,
    val repeats: Int,
    val result: String,
    val detail: String = "",
)

/** (occurrence key, local date) — the double-fire guard, persisted in `state`. */
data class FiredMark(val key: String, val localDate: LocalDate) {
    val stateKey: String get() = "fired:$key:$localDate"
}

/**
 * Everything [SchedulerCore.tick] needs, read in one transaction.
 * Pure data — the caller loads it from Room.
 */
data class ScheduleSnapshot(
    val courses: List<Course>,
    val typesById: Map<Int, CourseType>,
    val events: List<ScheduleEvent>,
    val settings: Map<String, String>,
    /** Doha slots that actually have a playable file mapped. */
    val mappedDohaSlots: Set<Int> = emptySet(),
) {
    fun setting(key: String): String =
        settings[key] ?: SettingsDefaults.map[key] ?: error("unknown setting $key")

    fun settingInt(key: String): Int = setting(key).toIntOrNull()
        ?: SettingsDefaults.map.getValue(key).toInt()

    fun settingBool(key: String): Boolean = setting(key) == "1"
}

/**
 * The result of one scheduler tick. Apply in this order:
 *  1. persist every [marks] entry **and** commit, then
 *  2. append [logs], then
 *  3. dispatch [fired] to the player.
 *
 * Guard-before-dispatch is what makes a mid-burst process death safe
 * (design doc §5, §10).
 */
data class TickOutcome(
    val marks: List<FiredMark> = emptyList(),
    val logs: List<PlayLogEntry> = emptyList(),
    val fired: List<PlayCommand> = emptyList(),
    /** Earliest un-fired occurrence still in the future; null = nothing pending. */
    val nextDeadline: ZonedDateTime? = null,
)

object SettingsDefaults {
    /** Mirrors the Pi daemon's model.py SETTINGS_DEFAULTS. */
    val map: Map<String, String> = mapOf(
        "enabled" to "1",
        "gong_enabled" to "1",
        "doha_enabled" to "1",
        "relay_enabled" to "0",
        "gong_track" to "ting",
        "gong_volume" to "90",
        "gong_gap_seconds" to "4",
        "doha_time" to "06:37",
        "doha_volume" to "75",
        "doha_strategy" to "legacy_modular",
        "no_course_doha" to "random",
        "active_course_id" to "",
        "admin_pin_hash" to "",
    )

    /** Android-only additions (no Pi counterpart). */
    val androidExtras: Map<String, String> = mapOf(
        "audio_route" to "speaker",
        "timezone" to ApplianceZone.DEFAULT_ID,
        "doha_tree_uri" to "",
        // Amplifier relay (Shelly 1 Gen4) — `relay_enabled` lives in `map`
        // above for Pi parity. Empty host = not configured, relay logic inert.
        // `relay_auth_pass` is a LAN device password: stored like any other
        // setting and, like the PIN, never logged.
        "relay_host" to "",
        "relay_auth_user" to "admin",
        "relay_auth_pass" to "",
        "relay_switch_id" to "0",
        "relay_lead_seconds" to "5",
        "relay_lag_seconds" to "5",
    )

    val all: Map<String, String> = map + androidExtras

    /** Never editable from the generic settings screen. */
    val readOnly: Set<String> = setOf("admin_pin_hash", "active_course_id")

    const val FIRE_GRACE_SECONDS: Long = 120
}
