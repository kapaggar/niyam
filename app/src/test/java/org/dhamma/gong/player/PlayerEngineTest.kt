package org.dhamma.gong.player

import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.dhamma.gong.data.GongDatabase
import org.dhamma.gong.data.GongRepository
import org.dhamma.gong.data.MediaSlotEntity
import org.dhamma.gong.data.MediaSlotSource
import org.dhamma.gong.domain.PlayCommand
import org.dhamma.gong.domain.PlayKind
import org.dhamma.gong.domain.PlayResult
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The queue and burst guarantees from the Pi daemon's player.py, asserted with a
 * fake [AudioSink] so no audio device is needed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlayerEngineTest {

    private lateinit var db: GongDatabase
    private lateinit var repo: GongRepository

    /** Records every play, and can be made to block until released. */
    private class FakeSink(private val durationMs: Long = 100) : AudioSink {
        val played = CopyOnWriteArrayList<String>()
        val volumes = CopyOnWriteArrayList<Int>()
        /** The device id each play was steered to; null means system default. */
        val devices = CopyOnWriteArrayList<Int?>()
        var warmedUp = false
        var released = false
        /** When set, play() suspends on this instead of delaying. */
        var block: CompletableDeferred<Unit>? = null

        override suspend fun play(uri: Uri, volume: Int, preferredDeviceId: Int?) {
            played += uri.toString()
            volumes += volume
            devices += preferredDeviceId
            block?.await() ?: delay(durationMs)
        }

        override suspend fun warmUp() {
            warmedUp = true
        }

        override suspend fun release() {
            released = true
        }
    }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            GongDatabase::class.java,
        )
            .allowMainThreadQueries()
            // Room's own executor is invisible to the test scheduler, so a
            // launched coroutine that awaits a query would still be suspended
            // when advanceUntilIdle() returns. Run queries inline instead.
            .setQueryExecutor { it.run() }
            .setTransactionExecutor { it.run() }
            .build()
        repo = GongRepository(db)
    }

    @After
    fun tearDown() = db.close()

    private fun TestScope.engine(sink: FakeSink) = PlayerEngine(
        repo = repo,
        resolver = MediaResolver(ApplicationProvider.getApplicationContext(), db),
        router = AudioRouter(ApplicationProvider.getApplicationContext()),
        sink = sink,
        scope = this,
    )

    private fun gong(repeats: Int = 3, gap: Int = 4, kind: String = PlayKind.GONG) =
        PlayCommand(
            kind = kind, trackStem = "ting", repeats = repeats,
            gapSeconds = gap, volume = 90, label = "gong x$repeats",
        )

    private suspend fun mapSlot(slot: Int) = db.mediaSlots().put(
        MediaSlotEntity(slot, "asset:///media/gongs/ting.mp3", "D%02d.mp3".format(slot), MediaSlotSource.BUNDLED),
    )

    // ------------------------------------------------------------ burst

    @Test
    fun burstPlaysEveryStrike() = runTest {
        val sink = FakeSink()
        val engine = engine(sink)
        engine.submit(gong(repeats = 16))
        advanceUntilIdle()

        assertEquals(16, sink.played.size)
        assertTrue(sink.played.all { it == "asset:///media/gongs/ting.mp3" })
        assertTrue(sink.volumes.all { it == 90 })
    }

    @Test
    fun sikkimGongPlaysOncePerThreeHits() = runTest {
        // The drum stem's recording contains three hits per play (GongTracks),
        // so repeats=6 hits means the file plays exactly twice — and the log
        // still reports the burst in hits, not plays.
        val sink = FakeSink()
        val engine = engine(sink)
        engine.submit(gong(repeats = 6).copy(trackStem = "drum"))
        advanceUntilIdle()

        assertEquals(2, sink.played.size)
        assertTrue(sink.played.all { it == "asset:///media/gongs/drum.mp3" })
        assertEquals(6, db.playLog().recent(1).first().repeats)
    }

    @Test
    fun gapIsCountedAfterTheStrikeEnds() = runTest {
        // Pi parity (B3): each strike plays to the end, then gap_seconds of
        // silence. Four 900 ms strikes with 4 s gaps.
        val sink = FakeSink(durationMs = 900)
        val engine = engine(sink)
        val t0 = currentTime
        engine.submit(gong(repeats = 4, gap = 4))
        advanceUntilIdle()

        assertEquals(4, sink.played.size)
        assertEquals(4 * 900L + 3 * 4000L, currentTime - t0)
    }

    @Test
    fun strikeLongerThanTheGapStillGetsTheGapAfterIt() = runTest {
        // Pi parity: the gap is silence AFTER the strike ends, not a
        // start-to-start cadence. A 5 s recording with a 4 s gap must still
        // leave 4 s of silence between strikes.
        val sink = FakeSink(durationMs = 5_000)
        val engine = engine(sink)
        val t0 = currentTime
        engine.submit(gong(repeats = 3, gap = 4))
        advanceUntilIdle()

        assertEquals(3, sink.played.size)
        assertEquals(3 * 5_000L + 2 * 4_000L, currentTime - t0)
    }

    @Test
    fun zeroGapPlaysBackToBack() = runTest {
        val sink = FakeSink(durationMs = 50)
        val engine = engine(sink)
        val t0 = currentTime
        engine.submit(gong(repeats = 5, gap = 0))
        advanceUntilIdle()
        assertEquals(5, sink.played.size)
        assertEquals(5 * 50L, currentTime - t0)
    }

    @Test
    fun successLogsOkWithTheStrikeCount() = runTest {
        val engine = engine(FakeSink())
        engine.submit(gong(repeats = 6))
        advanceUntilIdle()

        val row = db.playLog().recent().single()
        assertEquals(PlayResult.OK, row.result)
        assertEquals(6, row.repeats)
        assertEquals("ting.mp3", row.file)
        assertEquals(PlayKind.GONG, row.kind)
    }

    // ------------------------------------------------------------ preemption

    @Test
    fun newGongAbortsARunningGong() = runTest {
        val sink = FakeSink()
        val gate = CompletableDeferred<Unit>()
        sink.block = gate
        val engine = engine(sink)

        engine.submit(gong(repeats = 16, gap = 0))
        advanceUntilIdle() // first strike is now blocked in the sink

        sink.block = null
        engine.submit(gong(repeats = 2, gap = 0))
        gate.complete(Unit)
        advanceUntilIdle()

        val results = db.playLog().recent().map { it.result }
        assertTrue("the running burst must be aborted", results.contains(PlayResult.STOPPED))
        assertTrue("the new burst must complete", results.contains(PlayResult.OK))
        // 1 blocked strike from the aborted burst + 2 from the new one.
        assertEquals(3, sink.played.size)
    }

    @Test
    fun dohaWaitsForARunningGongAndNeverPreemptsIt() = runTest {
        val sink = FakeSink(durationMs = 500)
        val engine = engine(sink)
        mapSlot(5)

        engine.submit(gong(repeats = 3, gap = 1))
        engine.submit(
            PlayCommand(kind = PlayKind.DOHA, dohaSlot = 5, repeats = 1, volume = 75, label = "doha"),
        )
        advanceUntilIdle()

        val log = db.playLog().recent().sortedBy { it.id }
        assertEquals(2, log.size)
        assertEquals(PlayKind.GONG, log[0].kind)
        assertEquals(PlayResult.OK, log[0].result)
        assertEquals("the gong must finish all 3 strikes first", 3, log[0].repeats)
        assertEquals(PlayKind.DOHA, log[1].kind)
        assertEquals(PlayResult.OK, log[1].result)
    }

    @Test
    fun aSecondGongReplacesAQueuedGongRatherThanStacking() = runTest {
        val sink = FakeSink()
        val gate = CompletableDeferred<Unit>()
        sink.block = gate
        val engine = engine(sink)
        mapSlot(1)

        engine.submit(gong(repeats = 1, gap = 0, kind = PlayKind.DOHA).copy(dohaSlot = 1))
        advanceUntilIdle()

        // Two gongs queued behind the blocked doha: only the newest survives.
        sink.block = null
        engine.submit(gong(repeats = 2, gap = 0))
        engine.submit(gong(repeats = 7, gap = 0))
        gate.complete(Unit)
        advanceUntilIdle()

        val gongRows = db.playLog().recent().filter { it.kind == PlayKind.GONG }
        assertEquals("only one gong burst may run", 1, gongRows.size)
        assertEquals(7, gongRows.single().repeats)
    }

    // ------------------------------------------------------------ stop

    @Test
    fun stopAbortsTheBurstAndLogsStopped() = runTest {
        val sink = FakeSink()
        val gate = CompletableDeferred<Unit>()
        sink.block = gate
        val engine = engine(sink)

        engine.submit(gong(repeats = 16, gap = 0))
        advanceUntilIdle()
        engine.stop()
        gate.complete(Unit)
        advanceUntilIdle()

        val row = db.playLog().recent().single()
        assertEquals(PlayResult.STOPPED, row.result)
        assertTrue(row.detail.startsWith("stopped after"))
        assertEquals("no further strikes after stop", 1, sink.played.size)
    }

    @Test
    fun stopDropsQueuedJobsAndLogsThem() = runTest {
        val sink = FakeSink()
        val gate = CompletableDeferred<Unit>()
        sink.block = gate
        val engine = engine(sink)
        mapSlot(3)

        engine.submit(gong(repeats = 4, gap = 0))
        advanceUntilIdle()
        engine.submit(PlayCommand(kind = PlayKind.DOHA, dohaSlot = 3, repeats = 1, label = "doha"))
        engine.stop()
        gate.complete(Unit)
        advanceUntilIdle()

        val results = db.playLog().recent().map { it.kind to it.result }
        assertTrue(results.contains(PlayKind.GONG to PlayResult.STOPPED))
        assertTrue("the queued doha is dropped, not played", results.contains(PlayKind.DOHA to PlayResult.STOPPED))
    }

    // ------------------------------------------------------------ failure

    @Test
    fun missingGongTrackLogsErrorAndDoesNotCrash() = runTest {
        val sink = FakeSink()
        val engine = engine(sink)
        engine.submit(gong().copy(trackStem = "does-not-exist"))
        advanceUntilIdle()

        val row = db.playLog().recent().single()
        assertEquals(PlayResult.ERROR, row.result)
        assertEquals("gong track not bundled", row.detail)
        assertTrue(sink.played.isEmpty())
    }

    @Test
    fun unmappedDohaSlotLogsErrorAndDoesNotCrash() = runTest {
        val engine = engine(FakeSink())
        engine.submit(PlayCommand(kind = PlayKind.DOHA, dohaSlot = 9, repeats = 1, label = "doha"))
        advanceUntilIdle()

        val row = db.playLog().recent().single()
        assertEquals(PlayResult.ERROR, row.result)
        assertEquals("slot not mapped", row.detail)
    }

    @Test
    fun aSinkErrorMidBurstIsLoggedWithTheStrikesThatDidPlay() = runTest {
        val sink = object : AudioSink {
            var n = 0
            override suspend fun play(uri: Uri, volume: Int, preferredDeviceId: Int?) {
                if (++n == 3) error("device disappeared")
                delay(10)
            }
        }
        val engine = PlayerEngine(
            repo = repo,
            resolver = MediaResolver(ApplicationProvider.getApplicationContext(), db),
            router = AudioRouter(ApplicationProvider.getApplicationContext()),
            sink = sink,
            scope = this,
        )
        engine.submit(gong(repeats = 8, gap = 0))
        advanceUntilIdle()

        val row = db.playLog().recent().single()
        assertEquals(PlayResult.ERROR, row.result)
        assertEquals("device disappeared", row.detail)
        assertEquals("two strikes rang before the failure", 2, row.repeats)
    }

    @Test
    fun theQueueSurvivesAFailedJob() = runTest {
        val sink = FakeSink()
        val engine = engine(sink)
        mapSlot(2)

        engine.submit(gong().copy(trackStem = "missing"))
        engine.submit(PlayCommand(kind = PlayKind.DOHA, dohaSlot = 2, repeats = 1, label = "doha"))
        advanceUntilIdle()

        val results = db.playLog().recent().sortedBy { it.id }
        assertEquals(2, results.size)
        assertEquals(PlayResult.ERROR, results[0].result)
        assertEquals("the next job still runs", PlayResult.OK, results[1].result)
    }

    // ------------------------------------------------------------ route

    @Test
    fun okPlayRecordsTheLastGoodRoute() = runTest {
        val engine = engine(FakeSink())
        engine.submit(gong(repeats = 1))
        advanceUntilIdle()
        assertEquals(AudioRoute.Speaker.key, repo.stateGet(AudioRoute.LAST_OK_KEY))
    }

    @Test
    fun missingRouteFallsBackToSpeakerAndSaysSo() = runTest {
        repo.putSetting(AudioRoute.SETTING_KEY, "bluetooth")
        val engine = engine(FakeSink())
        engine.submit(gong(repeats = 2))
        advanceUntilIdle()

        val row = db.playLog().recent().single()
        assertEquals("the gong still rings", PlayResult.OK, row.result)
        assertEquals(2, row.repeats)
        assertTrue(row.detail.contains("unavailable"))
    }

    @Test
    fun aCommandsOwnRouteOverridesTheStoredPreference() = runTest {
        // Audio out auditions a device without committing the appliance to it.
        // The setting says speaker, which would resolve cleanly, so a detail
        // naming bluetooth can only have come from the command itself.
        repo.putSetting(AudioRoute.SETTING_KEY, AudioRoute.Speaker.key)
        val engine = engine(FakeSink())
        engine.submit(gong(repeats = 1).copy(routeKey = "bluetooth"))
        advanceUntilIdle()

        val row = db.playLog().recent().single()
        assertEquals(PlayResult.OK, row.result)
        assertTrue(row.detail.contains("bluetooth"))
        assertEquals(
            "the audition must not rewrite the appliance's preference",
            AudioRoute.Speaker.key,
            repo.setting(AudioRoute.SETTING_KEY),
        )
    }

    @Test
    fun theSpeakerRouteAsksForNoParticularDevice() = runTest {
        // Null is deliberate: naming the built-in device by id and missing
        // would be worse than accepting whatever Android would have picked.
        val sink = FakeSink()
        val engine = engine(sink)
        engine.submit(gong(repeats = 2))
        advanceUntilIdle()

        assertEquals(2, sink.devices.size)
        assertTrue(sink.devices.all { it == null })
    }

    // ------------------------------------------------------------ queue cap

    @Test
    fun queueIsCappedRatherThanUnbounded() = runTest {
        val sink = FakeSink()
        val gate = CompletableDeferred<Unit>()
        sink.block = gate
        val engine = engine(sink)
        mapSlot(1)

        engine.submit(gong(repeats = 1, gap = 0))
        advanceUntilIdle()

        val doha = PlayCommand(kind = PlayKind.DOHA, dohaSlot = 1, repeats = 1, label = "doha")
        val accepted = (1..PlayerEngine.MAX_QUEUE + 3).count { engine.submit(doha) }
        assertEquals(PlayerEngine.MAX_QUEUE, accepted)

        gate.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun warmUpReachesTheSink() = runTest {
        val sink = FakeSink()
        engine(sink).warmUp()
        advanceUntilIdle()
        assertTrue(sink.warmedUp)
    }
}
