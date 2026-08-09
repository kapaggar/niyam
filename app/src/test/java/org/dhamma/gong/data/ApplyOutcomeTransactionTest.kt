package org.dhamma.gong.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.dhamma.gong.domain.FiredMark
import org.dhamma.gong.domain.PlayKind
import org.dhamma.gong.domain.PlayLogEntry
import org.dhamma.gong.domain.PlayResult
import org.dhamma.gong.domain.TickOutcome
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate

/**
 * FABLE-REVIEW B2: a tick's marks and logs must land in ONE transaction.
 * A crash (or failed write) between them must leave neither, or the guard
 * consumes the slot with no record of why the gong never rang.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ApplyOutcomeTransactionTest {

    private lateinit var db: GongDatabase
    private lateinit var repo: GongRepository

    private val date: LocalDate = LocalDate.parse("2026-08-08")
    private val now: Instant = Instant.parse("2026-08-08T04:00:00Z")

    private val outcome = TickOutcome(
        marks = listOf(FiredMark("g12", date)),
        logs = listOf(PlayLogEntry(PlayKind.GONG, "ting.mp3", 3, PlayResult.OK)),
    )

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            GongDatabase::class.java,
        ).allowMainThreadQueries()
            .setQueryExecutor { it.run() }
            .setTransactionExecutor { it.run() }
            .build()
        repo = GongRepository(db)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun marksAndLogsBothLand() = runTest {
        repo.applyOutcome(outcome, now)
        assertTrue(repo.wasFired("g12", date))
        assertEquals(1, db.playLog().recent().size)
    }

    @Test
    fun marksRollBackWhenTheLogWriteFails() = runTest {
        // Simulated mid-apply crash: the log write throws after the guard
        // write. Non-atomic apply leaves an orphaned mark; atomic must not.
        db.openHelper.writableDatabase.execSQL("DROP TABLE play_log")
        runCatching { repo.applyOutcome(outcome, now) }
        assertFalse(
            "a fired mark without its log row must roll back",
            repo.wasFired("g12", date),
        )
    }
}
