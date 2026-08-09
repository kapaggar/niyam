package org.dhamma.gong.data

import android.content.Context
import android.util.Log
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.dhamma.gong.domain.SettingsDefaults

/**
 * First-launch seed: course types + the whole schedule matrix, exported from
 * `ng/seed/seed.sql` by `android/tools/export_seed_json.py`.
 *
 * Idempotent — [apply] is a no-op once `course_types` is populated, matching
 * `init_db(seed_sql=...)` in `ng/gong_ng/db.py`.
 */
object SeedLoader {

    const val ASSET_PATH = "seed/seed.json"
    const val SCHEMA_VERSION_KEY = "schema_version"
    const val SCHEMA_VERSION = "1"
    const val SEEDED_AT_KEY = "seeded_at"

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

    fun parse(text: String): Seed = json.decodeFromString(Seed.serializer(), text)

    fun readAsset(context: Context): Seed =
        context.assets.open(ASSET_PATH).bufferedReader().use { parse(it.readText()) }

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

    suspend fun applyFromAssets(context: Context, db: GongDatabase): Boolean =
        apply(db, readAsset(context))
}
