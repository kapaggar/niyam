package org.dhamma.gong.relay

import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.dhamma.gong.data.GongDatabase
import org.dhamma.gong.data.GongRepository
import org.dhamma.gong.data.SeedLoader
import org.dhamma.gong.domain.RelayPlan
import org.dhamma.gong.domain.VirtualClock
import org.dhamma.gong.player.AudioRouter
import org.dhamma.gong.player.AudioSink
import org.dhamma.gong.player.MediaResolver
import org.dhamma.gong.player.PlayerEngine
import org.dhamma.gong.schedule.AlarmScheduler
import org.dhamma.gong.schedule.SchedulerEngine
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

/**
 * The amplifier must stay energised for the *whole* burst.
 *
 * The bug this pins: the relay used to be handed
 * [PlayerEngine.Status.playing], which only goes true once audio is actually
 * running. `SchedulerEngine.tick` calls the relay hook in the same pass that
 * dispatched the command — microseconds after `submit` merely *queued* it — so
 * the relay saw "armed, but nothing playing", read that as a missed occurrence
 * and switched the amp off at the exact instant the gong started. On a tablet
 * that logged as `amp_on` 30 s before the hour, `amp_off` on the hour, and a
 * 16-strike gong finishing two and a half minutes later into a dead amp.
 *
 * So this drives the real [SchedulerEngine] with the real [PlayerEngine]
 * behind it, and asserts the three moments that matter: the fire tick, a
 * heartbeat mid-burst, and the lag-out after the last strike.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AmpHoldsAcrossThePlayTest {

    private lateinit var db: GongDatabase
    private lateinit var repo: GongRepository

    private val ist: ZoneId = ZoneId.of("Asia/Kolkata")
    private val start = LocalDate.of(2026, 8, 1)
    private val lead = 5L
    private val lag = 5L

    /** Holds every strike until the test lets go, so "mid-burst" is a real state. */
    private class BlockingSink : AudioSink {
        val played = mutableListOf<String>()
        val gate = CompletableDeferred<Unit>()
        override suspend fun play(uri: Uri, volume: Int, preferredDeviceId: Int?) {
            played += uri.toString()
            gate.await()
        }

        override suspend fun warmUp() = Unit
        override suspend fun release() = Unit
    }

    private class FakeAlarms(context: android.content.Context) : AlarmScheduler(context) {
        override fun canScheduleExact() = true
        override fun arm(at: ZonedDateTime) = Unit
        override fun cancel() = Unit
    }

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
    }

    @After
    fun tearDown() = db.close()

    private fun at(day: Long, h: Int, m: Int, s: Int = 0): ZonedDateTime =
        ZonedDateTime.of(start.plusDays(day), LocalTime.of(h, m, s), ist)

    @Test
    fun theAmpIsNotSwitchedOffAtTheInstantTheGongStarts() = runTest {
        SeedLoader.apply(db, SeedLoader.readAsset(ApplicationProvider.getApplicationContext()))
        repo.addCourse(courseTypeId = 1, startDate = start)

        val sink = BlockingSink()

        // The relay's own bookkeeping, mirrored from RelayController: the arm is
        // sticky, and the lag-out is measured from the real end of the burst.
        var armedForDeadline: ZonedDateTime? = null
        var lastPlayEndedAt: ZonedDateTime? = null
        var testNow: ZonedDateTime = at(2, 3, 59, 40)
        val decisions = mutableListOf<RelayPlan.Desired>()

        val player = PlayerEngine(
            repo = repo,
            resolver = MediaResolver(ApplicationProvider.getApplicationContext(), db),
            router = AudioRouter(ApplicationProvider.getApplicationContext()),
            sink = sink,
            scope = this,
            onPlayEnded = { lastPlayEndedAt = testNow },
        )

        val engine = SchedulerEngine(
            repo = repo,
            clock = VirtualClock(ist, testNow.toInstant()),
            alarms = FakeAlarms(ApplicationProvider.getApplicationContext()),
            scope = this,
            dispatch = { player.submit(it) },
            relayTick = { now, deadline, trusted ->
                val desired = RelayPlan.decide(
                    now = now,
                    nextDeadline = deadline,
                    // Exactly what GongService samples for the relay.
                    playerBusy = player.busy.value,
                    armedForDeadline = armedForDeadline,
                    relayEnabled = true,
                    clockTrusted = trusted,
                    leadSeconds = lead,
                    lagSeconds = lag,
                    heartbeat = SchedulerEngine.HEARTBEAT,
                    estimatedPlaySeconds = RelayPlan.DOHA_CEILING_SECONDS,
                    lastPlayEndedAt = lastPlayEndedAt,
                )
                decisions += desired
                when (desired) {
                    is RelayPlan.Desired.On -> armedForDeadline = deadline
                    is RelayPlan.Desired.Off -> armedForDeadline = null
                    is RelayPlan.Desired.NoChange -> Unit
                }
            },
        )

        // 1. Pre-arm: the 04:00 gong is inside heartbeat + lead, so the amp comes on.
        engine.tick(testNow)
        assertTrue("the pre-arm window must power the amp up", decisions.last() is RelayPlan.Desired.On)
        assertEquals(at(2, 4, 0), armedForDeadline)

        // 2. The fire tick. `submit` has queued the burst but no audio has
        //    started, which is precisely the window the old wiring misread.
        testNow = at(2, 4, 0)
        engine.tick(testNow)

        assertFalse(
            "sanity: the fire tick really does run before the first strike",
            player.status.value.playing,
        )
        assertTrue(
            "a burst the scheduler has already handed over must read as in flight",
            player.busy.value,
        )
        assertTrue(
            "cutting power here is the bug: the gong plays into a dead amp",
            decisions.last() !is RelayPlan.Desired.Off,
        )
        assertEquals("the arm must survive its own gong", at(2, 4, 0), armedForDeadline)

        // 3. A heartbeat mid-burst, with strikes still going out.
        advanceUntilIdle()
        assertTrue("the burst should be sounding by now", sink.played.isNotEmpty())
        testNow = at(2, 4, 0, 30)
        engine.tick(testNow)
        assertEquals(
            "never cut power out from under a play in progress",
            RelayPlan.Desired.NoChange,
            decisions.last(),
        )

        // 4. The burst ends; the amp powers down once the lag has elapsed.
        sink.gate.complete(Unit)
        advanceUntilIdle()
        assertFalse("the queue has drained", player.busy.value)

        testNow = at(2, 4, 1)
        engine.tick(testNow)
        assertEquals(
            "the amp must switch off after the play, not during it",
            RelayPlan.Desired.Off,
            decisions.last(),
        )
    }
}
