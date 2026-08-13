package org.dhamma.gong.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CourseTypeDao {
    @Query("SELECT * FROM course_types ORDER BY id")
    suspend fun all(): List<CourseTypeEntity>

    @Query("SELECT * FROM course_types ORDER BY id")
    fun observeAll(): Flow<List<CourseTypeEntity>>

    @Query("SELECT COUNT(*) FROM course_types")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<CourseTypeEntity>)
}

@Dao
interface CourseDao {
    @Query("SELECT * FROM courses ORDER BY start_date DESC")
    suspend fun all(): List<CourseEntity>

    @Query("SELECT * FROM courses ORDER BY start_date DESC")
    fun observeAll(): Flow<List<CourseEntity>>

    @Query("SELECT COUNT(*) FROM courses")
    suspend fun count(): Int

    @Insert
    suspend fun insert(row: CourseEntity): Long

    @Insert
    suspend fun insertAll(rows: List<CourseEntity>)

    @Query("DELETE FROM courses WHERE id = :id")
    suspend fun delete(id: Long)

    /** Restore only. Course ids are referenced by `active_course_id`. */
    @Query("DELETE FROM courses")
    suspend fun deleteAll()
}

@Dao
interface ScheduleEventDao {
    @Query("SELECT * FROM schedule_events")
    suspend fun all(): List<ScheduleEventEntity>

    @Query("SELECT * FROM schedule_events")
    fun observeAll(): Flow<List<ScheduleEventEntity>>

    @Query(
        """
        SELECT * FROM schedule_events
         WHERE course_type_id IS :courseTypeId AND day_no IS :dayNo
         ORDER BY time_local
        """,
    )
    suspend fun forDay(courseTypeId: Int?, dayNo: Int?): List<ScheduleEventEntity>

    @Query("SELECT COUNT(*) FROM schedule_events")
    suspend fun count(): Int

    /** REPLACE so re-seeding is idempotent on the unique (type, day, time) index. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<ScheduleEventEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(row: ScheduleEventEntity): Long

    @Upsert
    suspend fun upsert(row: ScheduleEventEntity)

    @Query("DELETE FROM schedule_events WHERE id = :id")
    suspend fun delete(id: Long)

    /** Restore only — the whole matrix is replaced as one unit. */
    @Query("DELETE FROM schedule_events")
    suspend fun deleteAll()
}

@Dao
interface SettingsDao {
    @Query("SELECT * FROM settings")
    suspend fun all(): List<SettingEntity>

    @Query("SELECT * FROM settings")
    fun observeAll(): Flow<List<SettingEntity>>

    @Query("SELECT value FROM settings WHERE key = :key")
    suspend fun get(key: String): String?

    @Upsert
    suspend fun put(row: SettingEntity)

    @Upsert
    suspend fun putAll(rows: List<SettingEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMissing(rows: List<SettingEntity>)
}

@Dao
interface StateDao {
    @Query("SELECT value FROM state WHERE key = :key")
    suspend fun get(key: String): String?

    @Upsert
    suspend fun put(row: StateEntity)

    @Query("DELETE FROM state WHERE key = :key")
    suspend fun remove(key: String)

    @Query("SELECT COUNT(*) FROM state WHERE key = :key")
    suspend fun exists(key: String): Int

    /**
     * The double-fire guard, as one atomic operation: insert only if absent,
     * and report whether *we* are the writer.
     *
     * @return true when this call created the guard — the caller may fire.
     */
    @Transaction
    suspend fun claimFired(stateKey: String, stampUtc: String): Boolean {
        if (exists(stateKey) > 0) return false
        put(StateEntity(stateKey, stampUtc))
        return true
    }

    @Query("SELECT key FROM state WHERE key LIKE 'fired:%'")
    suspend fun firedKeys(): List<String>

    @Query("SELECT key FROM state WHERE key LIKE 'missed:%'")
    suspend fun missedKeys(): List<String>

    /**
     * the Pi daemon's prune_fired: the date is the last 10 chars of the key.
     * Covers `missed:` too — it is day-scoped bookkeeping with the same
     * lifetime, and leaving it behind would grow `state` without bound.
     */
    @Query(
        """
        DELETE FROM state
         WHERE (key LIKE 'fired:%' OR key LIKE 'missed:%')
           AND substr(key, -10) < :cutoffIso
        """,
    )
    suspend fun pruneFired(cutoffIso: String)
}

@Dao
interface PlayLogDao {
    // Newest first, by the instant the row describes rather than by insertion
    // order. `ts_utc` is written as `Instant.truncatedTo(SECONDS).toString()`,
    // a fixed-width UTC string, so lexicographic order IS chronological order.
    // Insertion order drifts from it whenever a batch is written after the
    // fact — a boot that logs a night of `missed` entries, or a restore — and
    // then `id DESC` puts a stale row above a fresh one.
    @Query("SELECT * FROM play_log ORDER BY ts_utc DESC, id DESC LIMIT :limit")
    suspend fun recent(limit: Int = 200): List<PlayLogEntity>

    @Query("SELECT * FROM play_log ORDER BY ts_utc DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int = 200): Flow<List<PlayLogEntity>>

    @Insert
    suspend fun insert(row: PlayLogEntity): Long

    @Insert
    suspend fun insertAll(rows: List<PlayLogEntity>)

    /** Keep the log bounded — a wall tablet runs for years. */
    @Query("DELETE FROM play_log WHERE id < (SELECT MAX(id) - :keep FROM play_log)")
    suspend fun trim(keep: Int = 5000)

    /**
     * Staff-facing "clear all" on the Logs screen. The log is a diagnostic
     * record only — nothing schedules off it, so emptying it cannot change
     * what rings. The `fired:`/`missed:` guards live in `state`, untouched.
     */
    @Query("DELETE FROM play_log")
    suspend fun clear()
}

@Dao
interface MediaSlotDao {
    @Query("SELECT * FROM media_slots ORDER BY slot")
    suspend fun all(): List<MediaSlotEntity>

    @Query("SELECT * FROM media_slots ORDER BY slot")
    fun observeAll(): Flow<List<MediaSlotEntity>>

    @Query("SELECT * FROM media_slots WHERE slot = :slot")
    suspend fun get(slot: Int): MediaSlotEntity?

    @Query("SELECT slot FROM media_slots")
    suspend fun mappedSlots(): List<Int>

    @Upsert
    suspend fun put(row: MediaSlotEntity)

    @Upsert
    suspend fun putAll(rows: List<MediaSlotEntity>)

    @Query("DELETE FROM media_slots WHERE slot = :slot")
    suspend fun delete(slot: Int)

    @Query("DELETE FROM media_slots WHERE source = :source")
    suspend fun deleteBySource(source: String)

    /** Restore only. */
    @Query("DELETE FROM media_slots")
    suspend fun deleteAll()
}
