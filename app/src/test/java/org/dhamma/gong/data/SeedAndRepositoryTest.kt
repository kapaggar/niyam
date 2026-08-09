package org.dhamma.gong.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.dhamma.gong.domain.FiredMark
import org.dhamma.gong.domain.PlayKind
import org.dhamma.gong.domain.PlayLogEntry
import org.dhamma.gong.domain.PlayResult
import org.dhamma.gong.domain.SchedulerCore
import org.dhamma.gong.domain.SettingsDefaults
import org.dhamma.gong.domain.SystemGongClock
import org.dhamma.gong.domain.TickOutcome
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * M1 data layer against real SQLite (Robolectric), plus the seed exported from
 * the Pi repo's seed.sql. These assert Pi parity of the *stored* shape, not just the
 * in-memory domain.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SeedAndRepositoryTest {

    private lateinit var db: GongDatabase
    private lateinit var repo: GongRepository

    private val ist = ZoneId.of("Asia/Kolkata")

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            GongDatabase::class.java,
        ).allowMainThreadQueries().build()
        repo = GongRepository(db)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun seed(): SeedLoader.Seed {
        val seed = SeedLoader.readAsset(ApplicationProvider.getApplicationContext())
        SeedLoader.apply(db, seed)
        return seed
    }

    // ------------------------------------------------------------ seed

    @Test
    fun seedLoadsEveryCourseTypeAndEvent() = runTest {
        val seed = seed()
        assertEquals(12, seed.courseTypes.size)
        assertEquals(seed.courseTypes.size, db.courseTypes().count())
        assertEquals(seed.scheduleEvents.size, db.scheduleEvents().count())

        val tenDay = db.courseTypes().all().single { it.id == 1 }
        assertEquals("10 Day", tenDay.name)
        assertEquals(11, tenDay.totalDays)
        assertEquals(3, tenDay.anapanaDays)
    }

    @Test
    fun seedIsIdempotent() = runTest {
        val seed = seed()
        val eventsAfterFirst = db.scheduleEvents().count()

        assertFalse("second apply must be a no-op", SeedLoader.apply(db, seed))
        assertEquals(eventsAfterFirst, db.scheduleEvents().count())
        assertEquals(seed.courseTypes.size, db.courseTypes().count())
    }

    @Test
    fun settingsDefaultsMatchNg() = runTest {
        seed()
        val stored = db.settings().all().associate { it.key to it.value }
        for ((key, value) in SettingsDefaults.map) {
            assertEquals("setting $key", value, stored[key])
        }
        assertEquals("06:37", stored["doha_time"])
        assertEquals("ting", stored["gong_track"])
        assertEquals("random", stored["no_course_doha"])
    }

    @Test
    fun seedDoesNotClobberEditedSettings() = runTest {
        seed()
        repo.putSetting("gong_volume", "42")
        SeedLoader.apply(db, SeedLoader.readAsset(ApplicationProvider.getApplicationContext()))
        assertEquals("42", repo.setting("gong_volume"))
    }

    @Test
    fun defaultPatternRowsSurviveAsNullDayNo() = runTest {
        seed()
        val defaults = db.scheduleEvents().forDay(courseTypeId = 1, dayNo = null)
        assertTrue("10 Day must have a mid-course default pattern", defaults.isNotEmpty())
        assertTrue(defaults.all { it.dayNo == null })
        assertEquals(LocalTime.of(4, 0), LocalTime.parse(defaults.first().timeLocal))
    }

    @Test
    fun noCourseScheduleIsReachable() = runTest {
        seed()
        val rows = db.scheduleEvents().forDay(courseTypeId = null, dayNo = null)
        assertTrue("seed must carry a no-course schedule", rows.isNotEmpty())
    }

    @Test
    fun nullableGapAndTrackRoundTrip() = runTest {
        seed()
        val id = db.scheduleEvents().insert(
            ScheduleEventEntity(
                courseTypeId = 1, dayNo = 3, timeLocal = "09:15",
                repeats = 5, gapSeconds = null, track = null,
            ),
        )
        val row = db.scheduleEvents().all().single { it.id == id }
        // The em-dash "inherit" option must survive the DB (design handoff §3).
        assertEquals(null, row.gapSeconds)
        assertEquals(null, row.track)
    }

    // ------------------------------------------------------------ snapshot

    @Test
    fun snapshotFeedsTheDomainUnchanged() = runTest {
        seed()
        val start = LocalDate.now(ist).minusDays(2)
        repo.addCourse(courseTypeId = 1, startDate = start)

        val snapshot = repo.snapshot()
        assertEquals(12, snapshot.typesById.size)
        assertEquals(1, snapshot.courses.size)
        assertEquals(start, snapshot.courses.single().startDate)
        assertEquals("ting", snapshot.setting("gong_track"))
        assertEquals(4, snapshot.settingInt("gong_gap_seconds"))
        assertTrue(snapshot.settingBool("enabled"))
    }

    @Test
    fun schedulerFiresFromARealSeededDb() = runTest {
        seed()
        val today = LocalDate.of(2026, 8, 3)
        repo.addCourse(courseTypeId = 1, startDate = today.minusDays(2))

        val now = ZonedDateTime.of(today, LocalTime.of(4, 0), ist)
        val outcome = SchedulerCore.tick(
            clock = SystemGongClock(ist),
            now = now,
            snapshot = repo.snapshot(),
            firedGuard = { _, _ -> false },
        )
        val gong = outcome.fired.single { it.kind == PlayKind.GONG }
        assertEquals(16, gong.repeats)
        assertEquals("10 Day course, Day 2, 04:00 x16", gong.label)
    }

    // ------------------------------------------------------------ guard

    @Test
    fun firedGuardPersistsAndBlocksTheSecondFire() = runTest {
        seed()
        val date = LocalDate.of(2026, 8, 3)
        assertFalse(repo.wasFired("g1", date))

        repo.applyOutcome(TickOutcome(marks = listOf(FiredMark("g1", date))), Instant.now())
        assertTrue(repo.wasFired("g1", date))
        assertFalse("a different date is a different slot", repo.wasFired("g1", date.plusDays(1)))
    }

    @Test
    fun claimFiredIsAtomicAndSingleWinner() = runTest {
        val key = FiredMark("g1", LocalDate.of(2026, 8, 3)).stateKey
        assertTrue(db.state().claimFired(key, "t0"))
        assertFalse("only the first claimant may fire", db.state().claimFired(key, "t1"))
        assertEquals("t0", db.state().get(key))
    }

    @Test
    fun pruneFiredDropsOldGuardsOnly() = runTest {
        val today = LocalDate.of(2026, 8, 10)
        for (d in listOf(today, today.minusDays(1), today.minusDays(2), today.minusDays(5))) {
            db.state().put(StateEntity(FiredMark("g1", d).stateKey, "x"))
        }
        repo.pruneFired(today, keepDays = 2)
        val keys = db.state().firedKeys()
        assertEquals(3, keys.size)
        assertTrue(keys.none { it.endsWith(today.minusDays(5).toString()) })
    }

    // ------------------------------------------------------------ play log

    @Test
    fun playLogStoresUtcSecondsLikeNg() = runTest {
        repo.log(
            PlayLogEntry(PlayKind.GONG, "ting.mp3", 16, PlayResult.OK, "test"),
            Instant.parse("2026-08-03T04:00:00.987654Z"),
        )
        val row = db.playLog().recent().single()
        assertEquals("2026-08-03T04:00:00Z", row.tsUtc)
        assertEquals(PlayKind.GONG, row.kind)
        assertEquals(PlayResult.OK, row.result)
        assertNotNull(Instant.parse(row.tsUtc))
    }

    @Test
    fun missedOutcomeIsLoggedAndGuarded() = runTest {
        seed()
        val date = LocalDate.of(2026, 8, 3)
        repo.applyOutcome(
            TickOutcome(
                marks = listOf(FiredMark("g7", date)),
                logs = listOf(
                    PlayLogEntry(PlayKind.GONG, "-", 16, PlayResult.MISSED, "scheduled …"),
                ),
            ),
            Instant.now(),
        )
        assertTrue(repo.wasFired("g7", date))
        assertEquals(PlayResult.MISSED, db.playLog().recent().single().result)
    }

    // ------------------------------------------------------------ clock trust

    @Test
    fun clockTrustRoundTripsThroughRoom() = runTest {
        val t0 = ZonedDateTime.of(2026, 8, 3, 10, 0, 0, 0, ist)
        repo.touchClock(t0)
        assertTrue(repo.clockTrusted())

        // Six hours backwards.
        assertFalse(repo.checkClockOnStart(t0.minusHours(6)))
        assertFalse(repo.clockTrusted())

        repo.confirmClock(t0.minusHours(6))
        assertTrue(repo.clockTrusted())
    }

    @Test
    fun untrustedClockPersistsAcrossRepositoryInstances() = runTest {
        val t0 = ZonedDateTime.of(2026, 8, 3, 10, 0, 0, 0, ist)
        repo.touchClock(t0)
        repo.checkClockOnStart(t0.minusHours(6))

        val reopened = GongRepository(db)
        assertFalse("trust state lives in the DB, not in memory", reopened.clockTrusted())
    }

    // ------------------------------------------------------------ media slots

    @Test
    fun mappedSlotsDriveTheSnapshot() = runTest {
        seed()
        assertTrue(repo.snapshot().mappedDohaSlots.isEmpty())

        db.mediaSlots().putAll(
            (1..11).map {
                MediaSlotEntity(it, "content://doha/D%02d.mp3".format(it), "D%02d.mp3".format(it), MediaSlotSource.AUTO)
            },
        )
        assertEquals((1..11).toSet(), repo.snapshot().mappedDohaSlots)
    }

    // ------------------------------------------------------------ robustness

    @Test
    fun corruptRowsAreSkippedNotFatal() = runTest {
        seed()
        db.courses().insert(CourseEntity(courseTypeId = 1, startDate = "not-a-date"))
        db.scheduleEvents().insert(
            ScheduleEventEntity(courseTypeId = 1, dayNo = 9, timeLocal = "99:99", repeats = 3),
        )
        val snapshot = repo.snapshot()
        assertTrue("bad course row dropped", snapshot.courses.isEmpty())
        assertTrue("bad event row dropped", snapshot.events.none { it.dayNo == 9 })
    }

    @Test
    fun deletingTheActiveCourseClearsThePin() = runTest {
        seed()
        val id = repo.addCourse(1, LocalDate.of(2026, 8, 1))
        repo.putSetting("active_course_id", id.toString())
        repo.deleteCourse(id)
        assertEquals("", repo.setting("active_course_id"))
    }
}
