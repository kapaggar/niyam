package org.dhamma.gong.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupFileTest {

    // -------------------------------------------------- what must not travel

    @Test
    fun theFiredGuardsAreNotEvenRepresentable() {
        // `state` has no field in the format at all. This is the one that
        // matters most: restoring yesterday's fired: guards would tell the
        // scheduler today's gongs already rang, and a whole morning would pass
        // in silence with nothing unusual in the log.
        val fields = BackupFile::class.java.declaredFields.map { it.name }
        assertFalse("state must not be backed up", fields.any { it.contains("state", true) })
        assertFalse("play_log must not be backed up", fields.any { it.contains("playLog", true) })
    }

    @Test
    fun thePinNeverTravels() {
        // A restore must not be able to lock staff out of the appliance with a
        // PIN nobody at this centre knows.
        val exported = BackupFile.exportableSettings(
            mapOf("admin_pin_hash" to "pbkdf2:whatever", "gong_volume" to "90"),
        )
        assertFalse(exported.containsKey("admin_pin_hash"))
        assertEquals("90", exported["gong_volume"])
    }

    @Test
    fun theRelayPasswordNeverTravels() {
        val exported = BackupFile.exportableSettings(
            mapOf("relay_auth_pass" to "hunter2", "relay_host" to "192.168.1.50"),
        )
        assertFalse(exported.containsKey("relay_auth_pass"))
        // The host is fine — it is not a credential and saves re-typing.
        assertEquals("192.168.1.50", exported["relay_host"])
    }

    @Test
    fun deviceSpecificSettingsAreDropped() {
        val exported = BackupFile.exportableSettings(
            mapOf(
                // A row id that will not exist after restore.
                "active_course_id" to "7",
                // A SAF grant belonging to the tablet that issued it.
                "doha_tree_uri" to "content://com.android.externalstorage/tree/primary%3Adoha",
                "timezone" to "Asia/Kolkata",
            ),
        )
        assertFalse(exported.containsKey("active_course_id"))
        assertFalse(exported.containsKey("doha_tree_uri"))
        assertEquals("Asia/Kolkata", exported["timezone"])
    }

    @Test
    fun everythingElseIsKept() {
        val all = mapOf(
            "gong_track" to "drum",
            "gong_gap_seconds" to "4",
            "doha_time" to "06:37",
            "no_course_doha" to "random",
            "timezone" to "Asia/Kolkata",
        )
        assertEquals(all, BackupFile.exportableSettings(all))
    }

    // -------------------------------------------------- inspect before apply

    private fun sample(
        version: Int = BackupFile.VERSION,
        courses: Int = 2,
        events: Int = 3,
    ) = BackupFile(
        version = version,
        exportedAt = "2026-08-11T04:00:00Z",
        courses = List(courses) { BackupCourse(1, "2026-08-0${it + 1}") },
        scheduleEvents = List(events) { BackupEvent(1, null, "04:00", 16) },
    )

    @Test
    fun aGoodFileReportsWhatWouldBeApplied() {
        // Staff see the counts before committing: "replace your 12 courses with
        // 39" is a very different decision from a bare "restore".
        val result = BackupCheck.inspect(sample())
        assertTrue(result is BackupCheck.Result.Ok)
        result as BackupCheck.Result.Ok
        assertEquals(2, result.courses)
        assertEquals(3, result.events)
        assertEquals("2026-08-11T04:00:00Z", result.exportedAt)
    }

    @Test
    fun anUnparseableFileIsRejectedNotGuessedAt() {
        val result = BackupCheck.inspect(null)
        assertTrue(result is BackupCheck.Result.Rejected)
    }

    @Test
    fun aNewerFormatIsRefusedRatherThanPartiallyRead() {
        // Reading a future format field-by-field would silently drop whatever
        // it added — on an appliance that means silently dropping schedule.
        val result = BackupCheck.inspect(sample(version = BackupFile.VERSION + 1))
        assertTrue(result is BackupCheck.Result.Rejected)
        assertTrue((result as BackupCheck.Result.Rejected).reason.contains("newer version"))
    }

    @Test
    fun anEmptyBackupIsRefused() {
        // Almost certainly a truncated or wrong file. Applying it would leave
        // an appliance that rings nothing at all.
        val result = BackupCheck.inspect(sample(courses = 0, events = 0))
        assertTrue(result is BackupCheck.Result.Rejected)
    }

    @Test
    fun anOlderFormatIsStillAccepted() {
        // Forward compatibility is the point of the version field; refusing an
        // older backup would strand exactly the person who needs it.
        assertTrue(BackupCheck.inspect(sample(version = 1)) is BackupCheck.Result.Ok)
    }

    @Test
    fun aCourselessButScheduledBackupIsAcceptable() {
        // A centre may keep the matrix and enter courses by hand.
        assertTrue(BackupCheck.inspect(sample(courses = 0, events = 5)) is BackupCheck.Result.Ok)
    }

    @Test
    fun suggestedNamesSortByDate() {
        assertEquals("niyam-backup-2026-08-11.json", BackupFile.suggestedName("2026-08-11"))
    }
}
