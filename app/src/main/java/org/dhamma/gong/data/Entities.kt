package org.dhamma.gong.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entities — column names identical to `ng/gong_ng/db.py` SCHEMA, so a DB
 * pulled off the tablet stays readable by NG tooling (design doc §04).
 *
 * Three deliberate deltas from NG, all additive:
 *   + [MediaSlotEntity]   — the Pi's manifest.json becomes a table, because
 *                           Android holds SAF document URIs, not paths.
 *   + `state.route_last_ok` — a state key, no schema change.
 *   - deshna_* tables      — out of scope for v1.
 */

@Entity(
    tableName = "course_types",
    indices = [Index(value = ["name"], unique = true)],
)
data class CourseTypeEntity(
    @PrimaryKey val id: Int,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "total_days") val totalDays: Int,
    @ColumnInfo(name = "anapana_days") val anapanaDays: Int = 0,
)

@Entity(
    tableName = "courses",
    indices = [Index("course_type_id")],
)
data class CourseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "course_type_id") val courseTypeId: Int,
    /** ISO-8601 local date. This is **day 0**, the arrival day. */
    @ColumnInfo(name = "start_date") val startDate: String,
    @ColumnInfo(name = "note", defaultValue = "") val note: String = "",
)

/**
 * [dayNo] null = the mid-course default pattern.
 * [courseTypeId] null = the no-course schedule.
 * [gapSeconds] / [track] null = inherit the corresponding setting.
 */
@Entity(
    tableName = "schedule_events",
    indices = [
        Index(
            value = ["course_type_id", "day_no", "time_local"],
            unique = true,
            name = "idx_sched_unique",
        ),
    ],
)
data class ScheduleEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "course_type_id") val courseTypeId: Int?,
    @ColumnInfo(name = "day_no") val dayNo: Int?,
    /** "HH:mm" wall clock. */
    @ColumnInfo(name = "time_local") val timeLocal: String,
    @ColumnInfo(name = "repeats") val repeats: Int,
    @ColumnInfo(name = "gap_seconds") val gapSeconds: Int? = null,
    @ColumnInfo(name = "track") val track: String? = null,
)

@Entity(tableName = "settings")
data class SettingEntity(
    @PrimaryKey @ColumnInfo(name = "key") val key: String,
    @ColumnInfo(name = "value") val value: String,
)

/** Daemon state: fired guards, clock watermark, route_last_ok, schema_version. */
@Entity(tableName = "state")
data class StateEntity(
    @PrimaryKey @ColumnInfo(name = "key") val key: String,
    @ColumnInfo(name = "value") val value: String,
)

@Entity(
    tableName = "play_log",
    indices = [Index(value = ["ts_utc"], name = "idx_playlog_ts")],
)
data class PlayLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** ISO-8601 **UTC**, second precision — matches NG exactly. */
    @ColumnInfo(name = "ts_utc") val tsUtc: String,
    /** gong | doha | test_gong | test_doha */
    @ColumnInfo(name = "kind") val kind: String,
    @ColumnInfo(name = "file") val file: String,
    @ColumnInfo(name = "repeats") val repeats: Int,
    /** ok | missed | error | stopped | skipped_clock */
    @ColumnInfo(name = "result") val result: String,
    @ColumnInfo(name = "detail", defaultValue = "") val detail: String = "",
)

/**
 * Android delta: doha slot 1..11 → a playable file.
 *
 * [uri] is a persisted SAF document URI (sideloaded pack) or an
 * `asset:///…` URI for anything bundled. [source] records how the mapping was
 * made so an auto-map can be told apart from a staff override.
 */
@Entity(tableName = "media_slots")
data class MediaSlotEntity(
    @PrimaryKey @ColumnInfo(name = "slot") val slot: Int,
    @ColumnInfo(name = "uri") val uri: String,
    @ColumnInfo(name = "filename") val filename: String,
    /** auto | manual | bundled */
    @ColumnInfo(name = "source") val source: String,
    @ColumnInfo(name = "verified_at") val verifiedAt: String? = null,
)

object MediaSlotSource {
    const val AUTO = "auto"
    const val MANUAL = "manual"
    const val BUNDLED = "bundled"
}
