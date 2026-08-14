package org.dhamma.gong.domain

import org.dhamma.gong.domain.RelayPlan.Desired
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * The relay is a convenience; the gong is the product. These tests pin the six
 * behaviours the design calls out, especially the two edges that only the tick
 * path can see: a *missed* occurrence, and a back-to-back gong → doha pair.
 */
class RelayPlanTest {

    private val zone = ZoneId.of("Asia/Kolkata")
    private val now: ZonedDateTime = ZonedDateTime.of(2026, 8, 9, 4, 0, 0, 0, zone)
    private val heartbeat: Duration = Duration.ofSeconds(30)
    private val lead = 5L
    private val lag = 5L

    private fun decide(
        at: ZonedDateTime = now,
        nextDeadline: ZonedDateTime? = null,
        playing: Boolean = false,
        armedForDeadline: ZonedDateTime? = null,
        relayEnabled: Boolean = true,
        clockTrusted: Boolean = true,
        estimatedPlaySeconds: Long = 60,
        lastPlayEndedAt: ZonedDateTime? = null,
    ): Desired = RelayPlan.decide(
        now = at,
        nextDeadline = nextDeadline,
        playerBusy = playing,
        armedForDeadline = armedForDeadline,
        relayEnabled = relayEnabled,
        clockTrusted = clockTrusted,
        leadSeconds = lead,
        lagSeconds = lag,
        heartbeat = heartbeat,
        estimatedPlaySeconds = estimatedPlaySeconds,
        lastPlayEndedAt = lastPlayEndedAt,
    )

    // ------------------------------------------------------- 1. pre-arm window

    @Test
    fun `deadline inside heartbeat plus lead switches on`() {
        val deadline = now.plusSeconds(20)
        assertTrue(decide(nextDeadline = deadline) is Desired.On)
    }

    @Test
    fun `deadline exactly at the window edge switches on`() {
        val deadline = now.plusSeconds(heartbeat.seconds + lead)
        assertTrue(decide(nextDeadline = deadline) is Desired.On)
    }

    @Test
    fun `deadline outside the window does nothing`() {
        val deadline = now.plusSeconds(heartbeat.seconds + lead + 1)
        assertEquals(Desired.NoChange, decide(nextDeadline = deadline))
    }

    @Test
    fun `no deadline at all does nothing`() {
        assertEquals(Desired.NoChange, decide(nextDeadline = null))
    }

    // -------------------------------------------- 2. disabled / untrusted clock

    @Test
    fun `relay disabled never switches on`() {
        val deadline = now.plusSeconds(10)
        assertEquals(Desired.NoChange, decide(nextDeadline = deadline, relayEnabled = false))
    }

    @Test
    fun `untrusted clock never switches on automatically`() {
        val deadline = now.plusSeconds(10)
        assertEquals(Desired.NoChange, decide(nextDeadline = deadline, clockTrusted = false))
    }

    @Test
    fun `disabling the relay while armed releases the amp`() {
        val deadline = now.plusSeconds(10)
        assertEquals(
            Desired.Off,
            decide(nextDeadline = deadline, armedForDeadline = deadline, relayEnabled = false),
        )
    }

    @Test
    fun `clock going untrusted while armed releases the amp`() {
        val deadline = now.plusSeconds(10)
        assertEquals(
            Desired.Off,
            decide(nextDeadline = deadline, armedForDeadline = deadline, clockTrusted = false),
        )
    }

    // ---------------------------------------------------------- 3. rising edge

    @Test
    fun `second tick while already armed for the same deadline is NoChange`() {
        val deadline = now.plusSeconds(25)
        val first = decide(nextDeadline = deadline)
        assertTrue(first is Desired.On)

        // The heartbeat comes round again 30 s later; the deadline is the same.
        val second = decide(
            at = now.plusSeconds(10),
            nextDeadline = deadline,
            armedForDeadline = deadline,
        )
        assertEquals(
            "re-sending ON would silently refresh toggle_after and defeat the watchdog",
            Desired.NoChange,
            second,
        )
    }

    // ------------------------------------------------- 4. never off when needed

    @Test
    fun `off is suppressed while playing`() {
        val armed = now.minusSeconds(30)
        assertEquals(
            Desired.NoChange,
            decide(nextDeadline = null, playing = true, armedForDeadline = armed),
        )
    }

    @Test
    fun `off is suppressed while the lag has not elapsed`() {
        val armed = now.minusSeconds(120)
        assertEquals(
            Desired.NoChange,
            decide(
                nextDeadline = null,
                armedForDeadline = armed,
                lastPlayEndedAt = now.minusSeconds(lag - 1),
            ),
        )
    }

    @Test
    fun `off is issued once the lag has elapsed`() {
        val armed = now.minusSeconds(120)
        assertEquals(
            Desired.Off,
            decide(
                nextDeadline = null,
                armedForDeadline = armed,
                lastPlayEndedAt = now.minusSeconds(lag),
            ),
        )
    }

    @Test
    fun `back-to-back gong then doha inside the window never powers down between`() {
        val gong = now.minusSeconds(60)
        val doha = now.plusSeconds(20)
        val plan = decide(
            nextDeadline = doha,
            playing = false,
            armedForDeadline = gong,
            lastPlayEndedAt = now.minusSeconds(30),
        )
        assertTrue("a doha inside the pre-arm window must not see an OFF", plan is Desired.On)
    }

    @Test
    fun `nothing is switched off when there is no arm to release`() {
        assertEquals(Desired.NoChange, decide(nextDeadline = null, armedForDeadline = null))
    }

    // -------------------------------------------- 5. sticky arm / miss handling

    @Test
    fun `missed occurrence powers the amp off from the tick path`() {
        // Armed for D. D was missed, so it never reached the player: no play
        // start, no play end, and D is no longer the live nextDeadline.
        val missed = now.minusSeconds(130)
        assertEquals(
            "a missed gong must not wait on toggle_after while the process is alive",
            Desired.Off,
            decide(nextDeadline = null, playing = false, armedForDeadline = missed),
        )
    }

    @Test
    fun `armed deadline superseded by a far-off one powers the amp off`() {
        val armed = now.minusSeconds(90)
        val far = now.plusSeconds(3_600)
        assertEquals(
            Desired.Off,
            decide(nextDeadline = far, armedForDeadline = armed),
        )
    }

    // -------------------------------------------------------- 6. toggle_after

    @Test
    fun `toggle after always exceeds lead plus play plus lag`() {
        for (play in listOf(0L, 12L, 60L, 300L, RelayPlan.DOHA_CEILING_SECONDS)) {
            val toggle = RelayPlan.toggleAfterSeconds(lead, play, lag)
            assertTrue(
                "toggle_after ($toggle) must outlast lead+play+lag for play=$play",
                toggle > lead + play + lag,
            )
        }
    }

    @Test
    fun `on carries the computed toggle after`() {
        val deadline = now.plusSeconds(10)
        val plan = decide(nextDeadline = deadline, estimatedPlaySeconds = 120)
        assertEquals(
            Desired.On(lead + 120 + lag + RelayPlan.MARGIN_SECONDS),
            plan,
        )
    }

    @Test
    fun `gong estimate is repeats times strike plus gap`() {
        assertEquals(
            4 * (RelayPlan.PER_STRIKE_SECONDS + 4),
            RelayPlan.estimatePlaySeconds(PlayKind.GONG, repeats = 4, gapSeconds = 4),
        )
    }

    @Test
    fun `doha estimate is the fixed ceiling`() {
        assertEquals(
            RelayPlan.DOHA_CEILING_SECONDS,
            RelayPlan.estimatePlaySeconds(PlayKind.DOHA, repeats = 1, gapSeconds = 0),
        )
    }

    @Test
    fun `a doha watchdog outlasts a long chant`() {
        val toggle = RelayPlan.toggleAfterSeconds(
            lead,
            RelayPlan.estimatePlaySeconds(PlayKind.DOHA, 1, 0),
            lag,
        )
        assertTrue("de-powering mid-chant is the failure to avoid", toggle >= 1800 + 60)
    }

    // ------------------------------------------------------------ the log row

    @Test
    fun `a switch that worked reads as ok with its reason`() {
        val row = RelayPlan.ampLog(
            kind = PlayKind.AMP_ON,
            host = "10.0.0.42",
            ok = true,
            reason = RelayPlan.Reason.SCHEDULE,
        )
        assertEquals(PlayKind.AMP_ON, row.kind)
        assertEquals("10.0.0.42", row.file)
        assertEquals(PlayResult.OK, row.result)
        assertEquals("schedule", row.detail)
        assertEquals("nothing was struck", 0, row.repeats)
    }

    @Test
    fun `a failed switch carries the reason and the error`() {
        val row = RelayPlan.ampLog(
            kind = PlayKind.AMP_OFF,
            host = "10.0.0.42",
            ok = false,
            reason = RelayPlan.Reason.SCHEDULE,
            error = "timed out",
        )
        assertEquals(PlayResult.ERROR, row.result)
        assertTrue(row.detail.contains("timed out"))
        assertTrue(row.detail.contains("schedule"))
    }

    @Test
    fun `a manual press is told apart from the schedule`() {
        val manual = RelayPlan.ampLog(PlayKind.AMP_ON, "h", ok = true, reason = RelayPlan.Reason.MANUAL)
        val auto = RelayPlan.ampLog(PlayKind.AMP_ON, "h", ok = true, reason = RelayPlan.Reason.SCHEDULE)
        assertTrue("staff pressing on must not read as the scheduler", manual.detail != auto.detail)
    }

    /**
     * The relay password has its own setting and must never reach a log. A host
     * typed as a URL with credentials in it is the one back door into the FILE
     * column, so it is closed here rather than trusted not to happen.
     */
    @Test
    fun `credentials typed into the host never reach the log`() {
        val row = RelayPlan.ampLog(
            kind = PlayKind.AMP_ON,
            host = "admin:hunter2@10.0.0.42",
            ok = true,
            reason = RelayPlan.Reason.MANUAL,
        )
        assertEquals("10.0.0.42", row.file)
        assertTrue(!row.file.contains("hunter2") && !row.detail.contains("hunter2"))
    }

    @Test
    fun `an unset host still produces a readable row`() {
        val row = RelayPlan.ampLog(PlayKind.AMP_TEST, "  ", ok = false, reason = RelayPlan.Reason.TEST)
        assertEquals("-", row.file)
    }

    @Test
    fun `only the amp kinds are treated as amp rows`() {
        assertTrue(PlayKind.isAmp(PlayKind.AMP_ON))
        assertTrue(PlayKind.isAmp(PlayKind.AMP_OFF))
        assertTrue(PlayKind.isAmp(PlayKind.AMP_TEST))
        assertTrue(!PlayKind.isAmp(PlayKind.GONG))
        assertTrue(!PlayKind.isAmp(PlayKind.TEST_DOHA))
    }
}
