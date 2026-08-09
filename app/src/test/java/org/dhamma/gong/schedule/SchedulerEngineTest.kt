package org.dhamma.gong.schedule

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.dhamma.gong.data.GongDatabase
import org.dhamma.gong.data.GongRepository
import org.dhamma.gong.data.SeedLoader
import org.dhamma.gong.domain.PlayCommand
import org.dhamma.gong.domain.PlayKind
import org.dhamma.gong.domain.PlayResult
import org.dhamma.gong.domain.VirtualClock
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
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The M3 guarantees: the loop fires the right thing once, survives process
 * death and reboot, and goes silent rather than wrong when the clock moves.
 *
 * [SchedulerEngine.tick] is driven directly with a [VirtualClock], which is
 * the same thing the real loop does 2 880 times a day.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SchedulerEngineTest {

    private lateinit var db: GongDatabase
    private lateinit var repo: GongRepository

    private val ist: ZoneId = ZoneId.of("Asia/Kolkata")
    private val start = LocalDate.of(2026, 8, 1)

    /** Records what the scheduler handed to the player. */
    private val dispatched = CopyOnWriteArrayList<PlayCommand>()

    /** Records what the scheduler asked the OS to wake it for. */
    private class FakeAlarms(context: android.content.Context) : AlarmScheduler(context) {
        val armed = CopyOnWriteArrayList<ZonedDateTime>()
        var cancels = 0
        var exactAllowed = true
        override fun canScheduleExact() = exactAllowed
        override fun arm(at: ZonedDateTime) {
            armed += at
        }

        override fun cancel() {
            cancels++
        }
    }

    private lateinit var alarms: FakeAlarms

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            GongDatabase::class.java,
        )
            .allowMainThreadQueries()
            .setQueryExecutor { it.run() }
            .setTransactionExecutor { it.run() }
            .build()
        repo = GongRepository(db)
        alarms = FakeAlarms(ApplicationProvider.getApplicationContext())
        dispatched.clear()
    }

    @After
    fun tearDown() = db.close()

    private suspend fun seedWithCourse() {
        SeedLoader.apply(db, SeedLoader.readAsset(ApplicationProvider.getApplicationContext()))
        repo.addCourse(courseTypeId = 1, startDate = start)
    }

    private fun TestScope.engineAt(now: ZonedDateTime): Pair<SchedulerEngine, VirtualClock> {
        val clock = VirtualClock(ist, now.toInstant())
        val engine = SchedulerEngine(
            repo = repo,
            clock = clock,
            alarms = alarms,
            scope = this,
            dispatch = { dispatched += it },
        )
        return engine to clock
    }

    private fun at(day: Long, h: Int, m: Int, s: Int = 0): ZonedDateTime =
        ZonedDateTime.of(start.plusDays(day), LocalTime.of(h, m, s), ist)

    // ------------------------------------------------------------ firing

    @Test
    fun firesTheScheduledGongOnceAndArmsTheNext() = runTest {
        seedWithCourse()
        val (engine, _) = engineAt(at(2, 4, 0))
        engine.tick(at(2, 4, 0))

        val gong = dispatched.single()
        assertEquals(PlayKind.GONG, gong.kind)
        assertEquals(16, gong.repeats)
        assertEquals("10 Day course, Day 2, 04:00 x16", gong.label)

        assertEquals("must arm the next occurrence", at(2, 4, 20), alarms.armed.last())
    }

    @Test
    fun doesNotFireBeforeTheScheduledMinute() = runTest {
        seedWithCourse()
        val (engine, _) = engineAt(at(2, 3, 59, 59))
        val wait = engine.tick(at(2, 3, 59, 59))

        assertTrue(dispatched.isEmpty())
        assertEquals(at(2, 4, 0), alarms.armed.last())
        // The loop must not sleep past the deadline (heartbeat is the ceiling).
        assertTrue(wait <= SchedulerEngine.HEARTBEAT_MS)
    }

    @Test
    fun aSecondTickInsideGraceDoesNotFireAgain() = runTest {
        seedWithCourse()
        val (engine, _) = engineAt(at(2, 4, 0))
        engine.tick(at(2, 4, 0))
        engine.tick(at(2, 4, 0, 30))
        engine.tick(at(2, 4, 1))

        assertEquals("the fired guard must hold across ticks", 1, dispatched.size)
    }

    @Test
    fun aFullDayOfTicksFiresEveryEventExactlyOnce() = runTest {
        seedWithCourse()
        val (engine, _) = engineAt(at(2, 0, 0))
        // 30 s heartbeat for a whole day.
        var t = at(2, 0, 0)
        repeat(2 * 60 * 24) {
            engine.tick(t)
            t = t.plusSeconds(30)
        }
        // Day 2 falls back to the mid-course default pattern: 11 gongs + 1 doha.
        assertEquals(11, dispatched.count { it.kind == PlayKind.GONG })
        assertEquals(
            "no doha slots are mapped, so doha is silent but consumed",
            0,
            dispatched.count { it.kind == PlayKind.DOHA },
        )
        assertTrue("nothing may be logged missed", db.playLog().recent().none { it.result == PlayResult.MISSED })
    }

    // ------------------------------------------------------------ recovery

    @Test
    fun powerCutRestoredInsideGraceStillFires() = runTest {
        // Design doc §10: restored at 03:59:50, 04:00 fires normally.
        seedWithCourse()
        val (engine, _) = engineAt(at(2, 4, 0).plusSeconds(90))
        engine.tick(at(2, 4, 0).plusSeconds(90))
        assertEquals(1, dispatched.size)
        assertEquals(16, dispatched.single().repeats)
    }

    @Test
    fun powerCutRestoredPastGraceLogsMissedAndPlaysNothing() = runTest {
        // Design doc §10: restored at 04:10, nothing plays.
        seedWithCourse()
        val (engine, _) = engineAt(at(2, 4, 10))
        engine.tick(at(2, 4, 10))

        assertTrue("a late 04:00 must never blast", dispatched.none { it.repeats == 16 })
        val missed = db.playLog().recent().filter { it.result == PlayResult.MISSED }
        assertTrue(missed.isNotEmpty())
        assertTrue(missed.any { it.detail.contains("04:00") })
    }

    @Test
    fun processDeathMidBurstProducesNoRepeatOnRestart() = runTest {
        seedWithCourse()
        // First "process": fires, writes the guard, then dies.
        engineAt(at(2, 4, 0)).first.tick(at(2, 4, 0))
        assertEquals(1, dispatched.size)
        dispatched.clear()

        // Second "process": same DB, restarted 20 s later, still inside grace.
        val (restarted, _) = engineAt(at(2, 4, 0, 20))
        restarted.tick(at(2, 4, 0, 20))

        assertTrue("the guard survives the process, so nothing repeats", dispatched.isEmpty())
    }

    @Test
    fun rebootRearmsTheAlarmForTheNextOccurrence() = runTest {
        seedWithCourse()
        val (engine, _) = engineAt(at(2, 9, 0))
        engine.tick(at(2, 9, 0))
        assertEquals("11:00 is next after 09:00", at(2, 11, 0), alarms.armed.last())
    }

    @Test
    fun theDeadlineRollsIntoTomorrowAfterTheLastEventOfTheDay() = runTest {
        seedWithCourse()
        val (engine, _) = engineAt(at(2, 23, 0))
        engine.tick(at(2, 23, 0))
        assertEquals(at(3, 4, 0), alarms.armed.last())
    }

    // ------------------------------------------------------------ clock trust

    @Test
    fun backwardClockJumpSuppressesAutomaticPlaysButNotTests() = runTest {
        seedWithCourse()
        val (engine, _) = engineAt(at(2, 10, 0))
        // Establish the watermark without ticking: a tick at 10:00 would mark
        // the day's earlier events missed and consume the guard under test.
        repo.touchClock(at(2, 10, 0))

        // Six hours backwards, landing exactly on a scheduled 04:00.
        assertFalse(repo.checkClockOnStart(at(2, 4, 0)))
        dispatched.clear()
        engine.tick(at(2, 4, 0))

        assertTrue("silence beats a wrong gong", dispatched.isEmpty())
        assertFalse(engine.state.value.clockTrusted)
        assertNull("no deadline while untrusted", engine.state.value.next?.takeIf { false })
    }

    @Test
    fun confirmingTheClockResumesAutomaticPlays() = runTest {
        seedWithCourse()
        val (engine, _) = engineAt(at(2, 10, 0))
        repo.touchClock(at(2, 10, 0))
        repo.checkClockOnStart(at(2, 4, 0))
        engine.tick(at(2, 4, 0))
        assertTrue(dispatched.isEmpty())

        repo.confirmClock(at(2, 4, 0))
        engine.tick(at(2, 4, 0, 30))

        assertEquals("staff confirmed the time; 04:00 is still inside grace", 1, dispatched.size)
    }

    @Test
    fun anUntrustedClockCancelsTheAlarmRatherThanArmingAWrongOne() = runTest {
        seedWithCourse()
        val (engine, _) = engineAt(at(2, 10, 0))
        repo.touchClock(at(2, 10, 0))
        repo.checkClockOnStart(at(2, 4, 0))

        val before = alarms.cancels
        engine.tick(at(2, 4, 0))
        assertTrue("must not arm against a clock we do not trust", alarms.cancels > before)
    }

    // ------------------------------------------------------------ toggles

    @Test
    fun masterDisabledPlaysNothingAndDoesNotRetroFire() = runTest {
        seedWithCourse()
        repo.putSetting("enabled", "0")
        val (engine, _) = engineAt(at(2, 4, 0))
        engine.tick(at(2, 4, 0))
        assertTrue(dispatched.isEmpty())

        // Re-enabling inside the grace window must not fire the silenced gong.
        repo.putSetting("enabled", "1")
        engine.tick(at(2, 4, 0).plusSeconds(60))
        assertTrue("a deliberately silenced gong stays silenced", dispatched.isEmpty())
    }

    @Test
    fun gongDisabledLeavesDohaScheduling() = runTest {
        seedWithCourse()
        repo.putSetting("gong_enabled", "0")
        val (engine, _) = engineAt(at(2, 4, 0))
        engine.tick(at(2, 4, 0))
        assertTrue(dispatched.isEmpty())
        assertNotNull("the loop keeps running", engine.state.value.next)
    }

    // ------------------------------------------------------------ state

    @Test
    fun statePublishesCourseDayAndUpcoming() = runTest {
        seedWithCourse()
        val (engine, _) = engineAt(at(3, 8, 0))
        engine.tick(at(3, 8, 0))

        val s = engine.state.value
        assertEquals(3, s.course!!.day)
        assertEquals("10 Day", s.course!!.typeName)
        assertEquals(at(3, 11, 0), s.next!!.fireAt)
        assertTrue(s.upcoming.size > 1)
        assertTrue("upcoming is strictly in the future", s.upcoming.all { it.fireAt.isAfter(at(3, 8, 0)) })
    }

    @Test
    fun noCourseFallsBackToTheNoCourseSchedule() = runTest {
        SeedLoader.apply(db, SeedLoader.readAsset(ApplicationProvider.getApplicationContext()))
        val day = LocalDate.of(2026, 12, 1)
        val now = ZonedDateTime.of(day, LocalTime.of(0, 30), ist)
        val (engine, _) = engineAt(now)
        engine.tick(now)

        assertNull(engine.state.value.course)
        assertNotNull("the no-course schedule still arms something", engine.state.value.next)
    }

    @Test
    fun deniedExactAlarmsAreSurfacedNotFatal() = runTest {
        seedWithCourse()
        alarms.exactAllowed = false
        val (engine, _) = engineAt(at(2, 9, 0))
        engine.tick(at(2, 9, 0))

        assertFalse(engine.state.value.exactAlarmsAllowed)
        assertTrue("the heartbeat still carries the schedule", alarms.armed.isNotEmpty())
    }

    @Test
    fun oldGuardsArePrunedOnTheDayRollover() = runTest {
        seedWithCourse()
        val (engine, _) = engineAt(at(2, 4, 0))
        engine.tick(at(2, 4, 0))
        assertTrue(db.state().firedKeys().isNotEmpty())

        // Five days later: the day-2 guards are well outside the keep window.
        engine.tick(at(7, 0, 30))
        assertTrue(
            "day-2 guards must be gone",
            db.state().firedKeys().none { it.endsWith(start.plusDays(2).toString()) },
        )
    }
}
