package org.dhamma.gong.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.dhamma.gong.domain.BackupCheck
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupManagerTest {

    private lateinit var db: GongDatabase
    private lateinit var backups: BackupManager
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, GongDatabase::class.java)
            .allowMainThreadQueries().build()
        backups = BackupManager(db)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun seeded() {
        SeedLoader.apply(db, SeedLoader.readAsset(context))
    }

    // ------------------------------------------------------------ round trip

    @Test
    fun aBackupSurvivesEncodingAndRestoresTheSchedule() = runTest {
        seeded()
        db.courses().insert(CourseEntity(courseTypeId = 1, startDate = "2026-08-05"))
        db.settings().put(SettingEntity("gong_volume", "42"))
        val events = db.scheduleEvents().count()

        val text = backups.encode(backups.export("test"))

        // Wipe the tablet as thoroughly as a factory reset would.
        db.courses().deleteAll()
        db.scheduleEvents().deleteAll()
        db.settings().put(SettingEntity("gong_volume", "90"))

        val decoded = requireNotNull(backups.decode(text))
        backups.restore(decoded)

        assertEquals(1, db.courses().count())
        assertEquals("2026-08-05", db.courses().all().single().startDate)
        assertEquals(events, db.scheduleEvents().count())
        assertEquals("42", db.settings().get("gong_volume"))
    }

    @Test
    fun theFiredGuardsNeverTravel() = runTest {
        // The worst outcome available to this feature: restoring guards would
        // tell the scheduler today's gongs had already rung, and the appliance
        // would sit through a morning in silence with nothing odd in the log.
        seeded()
        db.state().put(StateEntity("fired:g12:2026-08-05", "2026-08-05T04:00:00Z"))

        val text = backups.encode(backups.export("test"))
        assertFalse("a fired guard leaked into the file", text.contains("fired:"))

        backups.restore(requireNotNull(backups.decode(text)))
        // The device's own guard is untouched by a restore.
        assertNotNull(db.state().get("fired:g12:2026-08-05"))
    }

    @Test
    fun thePinAndRelayPasswordNeverTravel() = runTest {
        seeded()
        db.settings().put(SettingEntity("admin_pin_hash", "pbkdf2\$secret"))
        db.settings().put(SettingEntity("relay_auth_pass", "hunter2"))

        val text = backups.encode(backups.export("test"))
        assertFalse(text.contains("pbkdf2"))
        assertFalse(text.contains("hunter2"))
    }

    @Test
    fun restoringDoesNotChangeThePinOnThisDevice() = runTest {
        // Being locked out of an appliance is worse than re-typing a PIN.
        seeded()
        db.settings().put(SettingEntity("admin_pin_hash", "local-pin"))
        val text = backups.encode(backups.export("test"))

        backups.restore(requireNotNull(backups.decode(text)))
        assertEquals("local-pin", db.settings().get("admin_pin_hash"))
    }

    @Test
    fun restoredSlotsArriveUnverified() = runTest {
        // A SAF URI from another tablet is meaningless here; a green "verified"
        // beside a file this device cannot open is the exact lie Sounds avoids.
        seeded()
        db.mediaSlots().put(
            MediaSlotEntity(1, "content://elsewhere/doc/1", "D01.mp3", MediaSlotSource.MANUAL, "2026-08-01T00:00:00Z"),
        )
        val text = backups.encode(backups.export("test"))
        db.mediaSlots().deleteAll()

        backups.restore(requireNotNull(backups.decode(text)))
        val slot = db.mediaSlots().all().single()
        assertEquals("D01.mp3", slot.filename)
        assertNull("restored slots must not claim to be verified", slot.verifiedAt)
    }

    @Test
    fun theCourseOverrideIsClearedOnRestore() = runTest {
        // active_course_id points at a row id that no longer exists after the
        // table is replaced; a stale pin would select the wrong schedule.
        seeded()
        db.courses().insert(CourseEntity(courseTypeId = 1, startDate = "2026-08-05"))
        db.settings().put(SettingEntity("active_course_id", "7"))

        val text = backups.encode(backups.export("test"))
        backups.restore(requireNotNull(backups.decode(text)))

        assertEquals("", db.settings().get("active_course_id"))
    }

    @Test
    fun restoreReplacesRatherThanMerges() = runTest {
        seeded()
        db.courses().insert(CourseEntity(courseTypeId = 1, startDate = "2026-08-05"))
        val text = backups.encode(backups.export("test"))

        // A second course exists at restore time and must not survive: staff
        // were told "replace", so a silent merge would contradict the dialog.
        db.courses().insert(CourseEntity(courseTypeId = 1, startDate = "2027-01-06"))
        assertEquals(2, db.courses().count())

        backups.restore(requireNotNull(backups.decode(text)))
        assertEquals(1, db.courses().count())
        assertEquals("2026-08-05", db.courses().all().single().startDate)
    }

    @Test
    fun garbageIsRejectedRatherThanPartiallyApplied() = runTest {
        seeded()
        assertNull(backups.decode("this is not json"))
        assertTrue(BackupCheck.inspect(backups.decode("{}")) is BackupCheck.Result.Rejected)
    }

    @Test
    fun aRealExportPassesItsOwnInspection() = runTest {
        seeded()
        db.courses().insert(CourseEntity(courseTypeId = 1, startDate = "2026-08-05"))
        val decoded = requireNotNull(backups.decode(backups.encode(backups.export("test"))))
        val check = BackupCheck.inspect(decoded)
        assertTrue(check is BackupCheck.Result.Ok)
        assertEquals(1, (check as BackupCheck.Result.Ok).courses)
    }
}
