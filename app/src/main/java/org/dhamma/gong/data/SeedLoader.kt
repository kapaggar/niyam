package org.dhamma.gong.data

import android.content.Context
import android.util.Log
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.dhamma.gong.domain.SettingsDefaults

/**
 * First-launch seed: course types + the whole schedule matrix, exported from
 * the Pi repo's seed.sql by `tools/export_seed_json.py`.
 *
 * Idempotent — [apply] is a no-op once `course_types` is populated, matching
 * `init_db(seed_sql=...)` in the Pi daemon's db.py.
 */
object SeedLoader {

    const val ASSET_PATH = "seed/seed.json"
    const val COURSES_ASSET_PATH = "seed/courses.json"
    const val SCHEMA_VERSION_KEY = "schema_version"
    const val SCHEMA_VERSION = "1"
    const val SEEDED_AT_KEY = "seeded_at"

    /**
     * Marks that the bundled centre calendar has been offered once.
     *
     * Deliberately separate from "is the courses table empty". Staff who delete
     * every course have *decided* the calendar is wrong — resurrecting thirty-nine
     * of them on the next launch would be the appliance arguing back, and each
     * one silently starts a schedule.
     */
    const val COURSES_SEEDED_KEY = "courses_seeded_at"

    private const val TAG = "SeedLoader"

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class Seed(
        val version: Int = 1,
        val source: String = "",
        @SerialName("settings_defaults") val settingsDefaults: Map<String, String> = emptyMap(),
        @SerialName("course_types") val courseTypes: List<SeedCourseType> = emptyList(),
        @SerialName("schedule_events") val scheduleEvents: List<SeedEvent> = emptyList(),
    )

    @Serializable
    data class SeedCourseType(
        val id: Int,
        val name: String,
        @SerialName("total_days") val totalDays: Int,
        @SerialName("anapana_days") val anapanaDays: Int = 0,
    )

    @Serializable
    data class SeedEvent(
        @SerialName("course_type_id") val courseTypeId: Int? = null,
        @SerialName("day_no") val dayNo: Int? = null,
        @SerialName("time_local") val timeLocal: String,
        val repeats: Int,
        @SerialName("gap_seconds") val gapSeconds: Int? = null,
        val track: String? = null,
    )

    /**
     * A centre's course calendar, generated from its `.sql` by
     * `tools/export_courses_json.py`. Separate from [Seed] because it is
     * *centre-specific* data with a different lifecycle: the schedule matrix is
     * the same everywhere and effectively permanent, while this is one venue's
     * calendar for a couple of years and staff will edit it.
     */
    @Serializable
    data class CourseSeed(
        val version: Int = 1,
        val source: String = "",
        val centre: String = "",
        val courses: List<SeedCourse> = emptyList(),
    )

    @Serializable
    data class SeedCourse(
        @SerialName("course_type_id") val courseTypeId: Int,
        /** ISO-8601 arrival day — day 0. */
        @SerialName("start_date") val startDate: String,
        val note: String = "",
    )

    fun parse(text: String): Seed = json.decodeFromString(Seed.serializer(), text)

    fun parseCourses(text: String): CourseSeed =
        json.decodeFromString(CourseSeed.serializer(), text)

    fun readAsset(context: Context): Seed =
        context.assets.open(ASSET_PATH).bufferedReader().use { parse(it.readText()) }

    /** @return null when the build ships no centre calendar. */
    fun readCoursesAsset(context: Context): CourseSeed? = runCatching {
        context.assets.open(COURSES_ASSET_PATH).bufferedReader().use { parseCourses(it.readText()) }
    }.getOrNull()

    /**
     * Populate an empty DB. Always fills in missing settings defaults, even on
     * an already-seeded DB, so a new setting added by an app update gets a
     * value without a migration.
     *
     * @return true when the schedule was actually seeded.
     */
    suspend fun apply(db: GongDatabase, seed: Seed): Boolean {
        db.state().put(StateEntity(SCHEMA_VERSION_KEY, SCHEMA_VERSION))

        // INSERT-OR-IGNORE semantics: never clobber a staff-edited setting.
        db.settings().insertMissing(
            (SettingsDefaults.all + seed.settingsDefaults).map { (k, v) -> SettingEntity(k, v) },
        )

        if (db.courseTypes().count() > 0) {
            Log.i(TAG, "seed skipped — course_types already populated")
            return false
        }

        db.courseTypes().insertAll(
            seed.courseTypes.map {
                CourseTypeEntity(it.id, it.name, it.totalDays, it.anapanaDays)
            },
        )
        db.scheduleEvents().insertAll(
            seed.scheduleEvents.map {
                ScheduleEventEntity(
                    courseTypeId = it.courseTypeId,
                    dayNo = it.dayNo,
                    timeLocal = it.timeLocal,
                    repeats = it.repeats,
                    gapSeconds = it.gapSeconds,
                    track = it.track,
                )
            },
        )
        db.state().put(StateEntity(SEEDED_AT_KEY, seed.source.ifBlank { "seed.json" }))
        Log.i(
            TAG,
            "seeded ${seed.courseTypes.size} course types, " +
                "${seed.scheduleEvents.size} schedule events",
        )
        return true
    }

    /**
     * Install the centre's course calendar, once ever.
     *
     * Two guards, and they mean different things. The marker says "this build
     * already offered its calendar" and survives staff emptying the table. The
     * empty-table check stops a calendar landing on top of courses someone has
     * already entered by hand, which would produce overlapping windows and a
     * dashboard that cannot say which schedule is running.
     *
     * Rows whose `course_type_id` is not in `course_types` are dropped rather
     * than inserted: a course pointing at a missing type resolves to no
     * schedule, so it would sit in the list looking real and ring nothing.
     *
     * @return how many courses were inserted.
     */
    suspend fun applyCourses(db: GongDatabase, seed: CourseSeed): Int {
        if (db.state().get(COURSES_SEEDED_KEY) != null) {
            Log.i(TAG, "course calendar already offered — skipping")
            return 0
        }
        if (db.courses().count() > 0) {
            Log.i(TAG, "courses already present — skipping calendar")
            db.state().put(StateEntity(COURSES_SEEDED_KEY, "skipped: courses existed"))
            return 0
        }

        val knownTypes = db.courseTypes().all().map { it.id }.toSet()
        val (usable, unknown) = seed.courses.partition { it.courseTypeId in knownTypes }
        if (unknown.isNotEmpty()) {
            Log.e(
                TAG,
                "dropping ${unknown.size} seeded courses with unknown course_type_id " +
                    unknown.map { it.courseTypeId }.distinct(),
            )
        }
        if (usable.isNotEmpty()) {
            db.courses().insertAll(
                usable.map {
                    CourseEntity(
                        courseTypeId = it.courseTypeId,
                        startDate = it.startDate,
                        note = it.note,
                    )
                },
            )
        }
        db.state().put(
            StateEntity(COURSES_SEEDED_KEY, seed.source.ifBlank { COURSES_ASSET_PATH }),
        )
        Log.i(TAG, "seeded ${usable.size} courses for ${seed.centre.ifBlank { "this centre" }}")
        return usable.size
    }

    suspend fun applyFromAssets(context: Context, db: GongDatabase): Boolean {
        val seeded = apply(db, readAsset(context))
        // After the schedule matrix, never before: the calendar's rows reference
        // course_types that the line above is what creates.
        readCoursesAsset(context)?.let { runCatching { applyCourses(db, it) } }
        return seeded
    }
}
