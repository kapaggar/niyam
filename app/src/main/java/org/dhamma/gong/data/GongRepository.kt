package org.dhamma.gong.data

import android.content.Context
import org.dhamma.gong.domain.ClockTrust
import org.dhamma.gong.domain.Course
import org.dhamma.gong.domain.CourseType
import org.dhamma.gong.domain.FiredMark
import org.dhamma.gong.domain.KeyValueState
import org.dhamma.gong.domain.PlayLogEntry
import org.dhamma.gong.domain.ScheduleEvent
import org.dhamma.gong.domain.ScheduleSnapshot
import org.dhamma.gong.domain.SettingsDefaults
import org.dhamma.gong.domain.TickOutcome
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * The only bridge between Room and the pure domain.
 *
 * Everything the scheduler needs is read into a [ScheduleSnapshot]; everything
 * it decides comes back as a [TickOutcome] that [applyOutcome] persists in the
 * order the guarantees require.
 */
class GongRepository(private val db: GongDatabase) {

    // ------------------------------------------------------------ snapshot

    suspend fun snapshot(): ScheduleSnapshot = ScheduleSnapshot(
        courses = db.courses().all().mapNotNull { it.toDomain() },
        typesById = db.courseTypes().all().associate { it.id to it.toDomain() },
        events = db.scheduleEvents().all().mapNotNull { it.toDomain() },
        settings = db.settings().all().associate { it.key to it.value },
        mappedDohaSlots = db.mediaSlots().mappedSlots().toSet(),
    )

    // ------------------------------------------------------------ guard

    /**
     * The double-fire guard read. Cheap enough to call per occurrence per tick
     * (a handful of indexed point lookups).
     */
    suspend fun wasFired(key: String, date: LocalDate): Boolean =
        db.state().exists(FiredMark(key, date).stateKey) > 0

    /**
     * Persist a tick's decisions. Order is load-bearing (design doc §05):
     * guards first and committed, then the log, and only then does the caller
     * hand [TickOutcome.fired] to the player.
     */
    suspend fun applyOutcome(outcome: TickOutcome, nowUtc: Instant) {
        for (mark in outcome.marks) {
            db.state().put(StateEntity(mark.stateKey, nowUtc.toString()))
        }
        if (outcome.logs.isNotEmpty()) {
            db.playLog().insertAll(outcome.logs.map { it.toEntity(nowUtc) })
        }
    }

    suspend fun log(entry: PlayLogEntry, nowUtc: Instant = Instant.now()) {
        db.playLog().insert(entry.toEntity(nowUtc))
    }

    /** NG's prune_fired — keeps `state` from growing without bound. */
    suspend fun pruneFired(today: LocalDate, keepDays: Long = 2) {
        db.state().pruneFired(today.minusDays(keepDays).toString())
    }

    suspend fun trimLog(keep: Int = 5000) = db.playLog().trim(keep)

    // ------------------------------------------------------------ settings

    suspend fun setting(key: String): String =
        db.settings().get(key) ?: SettingsDefaults.all[key].orEmpty()

    suspend fun settingBool(key: String): Boolean = setting(key) == "1"

    suspend fun settingInt(key: String): Int =
        setting(key).toIntOrNull() ?: SettingsDefaults.all[key]?.toIntOrNull() ?: 0

    suspend fun putSetting(key: String, value: String) {
        db.settings().put(SettingEntity(key, value))
    }

    // ------------------------------------------------------------ clock trust

    /**
     * Run [block] against the clock-trust keys, then write back only what it
     * changed. Lets the pure [ClockTrust] logic run over suspend-based Room.
     */
    suspend fun <T> withClockState(block: (KeyValueState) -> T): T {
        val keys = listOf(ClockTrust.LAST_GOOD_KEY, ClockTrust.CLOCK_INVALID_KEY)
        val before = keys.associateWith { db.state().get(it) }
        val buffer = StateBuffer(before)
        val result = block(buffer)
        for ((key, value) in buffer.changes()) {
            if (value == null) db.state().remove(key) else db.state().put(StateEntity(key, value))
        }
        return result
    }

    suspend fun clockTrusted(): Boolean = withClockState { ClockTrust.isTrusted(it) }

    suspend fun confirmClock(now: ZonedDateTime) = withClockState { ClockTrust.confirm(it, now) }

    suspend fun checkClockOnStart(now: ZonedDateTime): Boolean =
        withClockState { ClockTrust.checkOnStart(it, now) }

    suspend fun touchClock(now: ZonedDateTime) = withClockState { ClockTrust.touchLastGood(it, now) }

    // ------------------------------------------------------------ courses

    suspend fun addCourse(courseTypeId: Int, startDate: LocalDate, note: String = ""): Long =
        db.courses().insert(CourseEntity(courseTypeId = courseTypeId, startDate = startDate.toString(), note = note))

    suspend fun deleteCourse(id: Long) {
        db.courses().delete(id)
        if (setting("active_course_id") == id.toString()) putSetting("active_course_id", "")
    }

    // ------------------------------------------------------------ state kv

    suspend fun stateGet(key: String): String? = db.state().get(key)

    suspend fun statePut(key: String, value: String) = db.state().put(StateEntity(key, value))

    companion object {
        fun from(context: Context) = GongRepository(GongDatabase.get(context))
    }
}

/** Records writes so only genuine changes hit the DB. */
private class StateBuffer(initial: Map<String, String?>) : KeyValueState {
    private val current = initial.toMutableMap()
    private val original = initial.toMap()

    override fun get(key: String): String? = current[key]

    override fun put(key: String, value: String) {
        current[key] = value
    }

    override fun remove(key: String) {
        current[key] = null
    }

    fun changes(): Map<String, String?> =
        current.filter { (k, v) -> original[k] != v }
}

// ---------------------------------------------------------------- mapping

fun CourseTypeEntity.toDomain() = CourseType(id, name, totalDays, anapanaDays)

/** Null when `start_date` is unparseable — a corrupt row must not crash the loop. */
fun CourseEntity.toDomain(): Course? = runCatching {
    Course(id, courseTypeId, LocalDate.parse(startDate), note)
}.getOrNull()

/** Null when `time_local` is unparseable, for the same reason. */
fun ScheduleEventEntity.toDomain(): ScheduleEvent? = runCatching {
    ScheduleEvent(id, courseTypeId, dayNo, LocalTime.parse(timeLocal), repeats, gapSeconds, track)
}.getOrNull()

fun PlayLogEntry.toEntity(nowUtc: Instant) = PlayLogEntity(
    tsUtc = nowUtc.truncatedTo(ChronoUnit.SECONDS).toString(),
    kind = kind,
    file = file,
    repeats = repeats,
    result = result,
    detail = detail,
)
