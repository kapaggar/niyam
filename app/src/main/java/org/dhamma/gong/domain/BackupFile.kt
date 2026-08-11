package org.dhamma.gong.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * What a Niyam backup contains, and — more importantly — what it deliberately
 * does not.
 *
 * A backup is **configuration, not state**. That distinction is the whole
 * design, because the obvious implementation (copy `gong.db`) is actively
 * dangerous:
 *
 * - `state` holds the `fired:<key>:<date>` guards. Restoring yesterday's guards,
 *   or another tablet's, tells the scheduler that today's gongs have already
 *   sounded. The appliance would sit silent through a morning and log nothing
 *   unusual. This is the single worst outcome available to a restore feature.
 * - `play_log` is history. It belongs to the device that lived it; merging two
 *   devices' logs would make the one record of what actually rang unreliable.
 * - `admin_pin_hash` is excluded so a restore cannot lock staff out of an
 *   appliance with a PIN nobody at this centre remembers. The device keeps the
 *   PIN it has.
 * - `active_course_id` is a pinned override referencing a row id that will be
 *   different after restore. Dropped rather than remapped: the course window
 *   resolves the right course on its own, and a stale pin would quietly select
 *   the wrong schedule.
 * - `relay_auth_pass` is a LAN device credential. It stays out of a plaintext
 *   file staff will email to themselves; the relay is re-authenticated by hand.
 *
 * Media slots travel, but their `verifiedAt` does not — a SAF document URI from
 * another tablet is meaningless here, so every restored slot arrives unverified
 * and the folder must be rescanned. Claiming otherwise would paint a green
 * "verified" next to a file this device cannot open.
 */
@Serializable
data class BackupFile(
    val version: Int = VERSION,
    /** ISO-8601 UTC, stamped by the exporter. */
    @SerialName("exported_at") val exportedAt: String = "",
    /** App versionName, so an old file can be recognised on sight. */
    @SerialName("app_version") val appVersion: String = "",
    val settings: Map<String, String> = emptyMap(),
    val courses: List<BackupCourse> = emptyList(),
    @SerialName("schedule_events") val scheduleEvents: List<BackupEvent> = emptyList(),
    @SerialName("media_slots") val mediaSlots: List<BackupSlot> = emptyList(),
) {
    companion object {
        const val VERSION = 1

        /** Suggested filename; the date makes a folder of these sortable. */
        fun suggestedName(today: String): String = "niyam-backup-$today.json"

        /**
         * Settings that must never travel in a backup. See the class comment —
         * each one of these can strand a working appliance.
         */
        val EXCLUDED_SETTINGS: Set<String> = setOf(
            "admin_pin_hash",
            "active_course_id",
            "relay_auth_pass",
            // Device-specific: the SAF grant behind it belongs to the tablet
            // that issued it, so the path would resolve to nothing here.
            "doha_tree_uri",
        )

        fun exportableSettings(all: Map<String, String>): Map<String, String> =
            all.filterKeys { it !in EXCLUDED_SETTINGS }
    }
}

@Serializable
data class BackupCourse(
    @SerialName("course_type_id") val courseTypeId: Int,
    @SerialName("start_date") val startDate: String,
    val note: String = "",
)

@Serializable
data class BackupEvent(
    @SerialName("course_type_id") val courseTypeId: Int? = null,
    @SerialName("day_no") val dayNo: Int? = null,
    @SerialName("time_local") val timeLocal: String,
    val repeats: Int,
    @SerialName("gap_seconds") val gapSeconds: Int? = null,
    val track: String? = null,
)

@Serializable
data class BackupSlot(
    val slot: Int,
    val uri: String,
    val filename: String,
    val source: String,
)

/**
 * Whether a parsed file is safe to apply, and what applying it would do.
 *
 * Restore replaces the schedule wholesale, so staff get the count *before*
 * they commit — "this will replace your 12 courses with 39" is a very
 * different decision from "restore".
 */
object BackupCheck {

    sealed interface Result {
        data class Ok(
            val courses: Int,
            val events: Int,
            val slots: Int,
            val settings: Int,
            val exportedAt: String,
        ) : Result

        data class Rejected(val reason: String) : Result
    }

    fun inspect(file: BackupFile?): Result = when {
        file == null ->
            Result.Rejected("That file is not a Niyam backup, or it is damaged.")

        file.version > BackupFile.VERSION ->
            Result.Rejected(
                "That backup was written by a newer version of Niyam " +
                    "(format ${file.version}, this build reads ${BackupFile.VERSION}). " +
                    "Update the app first.",
            )

        // An empty schedule is almost certainly a truncated or wrong file, and
        // applying it would leave an appliance that rings nothing at all.
        file.scheduleEvents.isEmpty() && file.courses.isEmpty() ->
            Result.Rejected("That backup has no courses and no schedule — nothing to restore.")

        else -> Result.Ok(
            courses = file.courses.size,
            events = file.scheduleEvents.size,
            slots = file.mediaSlots.size,
            settings = file.settings.size,
            exportedAt = file.exportedAt.ifBlank { "unknown date" },
        )
    }
}
