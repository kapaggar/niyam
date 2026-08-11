package org.dhamma.gong.data

import android.util.Log
import androidx.room.withTransaction
import kotlinx.serialization.json.Json
import org.dhamma.gong.domain.BackupCourse
import org.dhamma.gong.domain.BackupEvent
import org.dhamma.gong.domain.BackupFile
import org.dhamma.gong.domain.BackupSlot
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Reads and writes the configuration backup described by [BackupFile].
 *
 * Deliberately a JSON document rather than a copy of `gong.db`. A file copy
 * would be less code and much worse: it carries the `fired:` guards and the
 * play log, both of which are wrong on any other day or any other device, and
 * it is opaque — a centre with a broken schedule cannot look inside it, and
 * neither can anyone helping them over the phone.
 */
class BackupManager(private val db: GongDatabase) {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** Build the document. Pure read — nothing is modified. */
    suspend fun export(appVersion: String): BackupFile = BackupFile(
        exportedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString(),
        appVersion = appVersion,
        settings = BackupFile.exportableSettings(
            db.settings().all().associate { it.key to it.value },
        ),
        courses = db.courses().all().map {
            BackupCourse(it.courseTypeId, it.startDate, it.note)
        },
        scheduleEvents = db.scheduleEvents().all().map {
            BackupEvent(it.courseTypeId, it.dayNo, it.timeLocal, it.repeats, it.gapSeconds, it.track)
        },
        mediaSlots = db.mediaSlots().all().map {
            BackupSlot(it.slot, it.uri, it.filename, it.source)
        },
    )

    fun encode(file: BackupFile): String = json.encodeToString(BackupFile.serializer(), file)

    /** @return null when the text is not a readable backup. */
    fun decode(text: String): BackupFile? = runCatching {
        json.decodeFromString(BackupFile.serializer(), text)
    }.onFailure { Log.w(TAG, "unreadable backup file", it) }.getOrNull()

    /**
     * Apply a backup, replacing courses, schedule and slots wholesale.
     *
     * One transaction, because a half-applied restore is the worst of both
     * worlds: the old schedule is gone and the new one is incomplete, and the
     * appliance would keep running on the wreckage. If anything throws, the
     * whole thing rolls back and the tablet is exactly as it was.
     *
     * Settings are merged rather than replaced, so a key this build knows about
     * but the backup predates keeps its default instead of vanishing.
     *
     * @return a short summary for the toast.
     */
    suspend fun restore(file: BackupFile): String = db.withTransaction {
        // Only types this build knows. A course pointing at a missing type
        // resolves to no schedule — it would sit in the list looking real.
        val knownTypes = db.courseTypes().all().map { it.id }.toSet()
        val courses = file.courses.filter { it.courseTypeId in knownTypes }
        val events = file.scheduleEvents.filter {
            it.courseTypeId == null || it.courseTypeId in knownTypes
        }

        db.courses().deleteAll()
        db.scheduleEvents().deleteAll()
        db.mediaSlots().deleteAll()

        if (courses.isNotEmpty()) {
            db.courses().insertAll(
                courses.map { CourseEntity(courseTypeId = it.courseTypeId, startDate = it.startDate, note = it.note) },
            )
        }
        if (events.isNotEmpty()) {
            db.scheduleEvents().insertAll(
                events.map {
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
        }
        if (file.mediaSlots.isNotEmpty()) {
            db.mediaSlots().putAll(
                file.mediaSlots.map {
                    // verifiedAt stays null: a SAF URI from another tablet is
                    // meaningless here, and a green "verified" beside a file
                    // this device cannot open is exactly the lie the Sounds
                    // screen exists to avoid.
                    MediaSlotEntity(
                        slot = it.slot,
                        uri = it.uri,
                        filename = it.filename,
                        source = it.source,
                        verifiedAt = null,
                    )
                },
            )
        }

        val settings = BackupFile.exportableSettings(file.settings)
        if (settings.isNotEmpty()) {
            db.settings().putAll(settings.map { (k, v) -> SettingEntity(k, v) })
        }

        // The pin the backup could have carried is gone by construction; clear
        // the course override too, since the ids it referenced no longer exist.
        db.settings().put(SettingEntity("active_course_id", ""))

        val dropped = (file.courses.size - courses.size) + (file.scheduleEvents.size - events.size)
        Log.i(TAG, "restored ${courses.size} courses, ${events.size} events, $dropped dropped")
        buildString {
            append("Restored ${courses.size} courses and ${events.size} schedule rows")
            if (file.mediaSlots.isNotEmpty()) {
                append(", ${file.mediaSlots.size} doha slots (unverified — rescan the folder)")
            }
            if (dropped > 0) append(". $dropped rows dropped: unknown course type")
        }
    }

    private companion object {
        const val TAG = "BackupManager"
    }
}
