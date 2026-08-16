package org.dhamma.gong.ui

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The Logs day marker means "days before today", today being the appliance
 * zone's calendar date — never the local-vs-UTC calendar shift.
 *
 * The regression pinned here (2026-08-15, Pixel C in America/Los_Angeles):
 * every row written after 17:00 local crossed UTC midnight, and the old
 * shift-vs-UTC marker stamped it "-1d" — an amp_on logged seconds ago read as
 * yesterday's, and a night could not be reconstructed from the screen.
 */
class LogStampTest {

    private val la: ZoneId = ZoneId.of("America/Los_Angeles")

    @Test
    fun eveningRowFromTodayCarriesNoMarker() {
        // 20:59 PDT = 03:59Z next day; still today, so no marker.
        val today = LocalDate.parse("2026-08-15")
        assertEquals("20:59:27", localStamp("2026-08-16T03:59:27Z", la, today))
    }

    @Test
    fun morningRowFromTodayCarriesNoMarker() {
        val today = LocalDate.parse("2026-08-15")
        assertEquals("04:21:45", localStamp("2026-08-15T11:21:45Z", la, today))
    }

    @Test
    fun yesterdayRowReadsMinusOneDay() {
        val today = LocalDate.parse("2026-08-16")
        assertEquals("20:59:27 -1d", localStamp("2026-08-16T03:59:27Z", la, today))
    }

    @Test
    fun weekOldRowCountsTheDays() {
        val today = LocalDate.parse("2026-08-22")
        assertEquals("20:59:27 -7d", localStamp("2026-08-16T03:59:27Z", la, today))
    }

    @Test
    fun futureRowFromAClockJumpReadsPlus() {
        // A row stamped ahead of today (clock moved backwards after logging)
        // must be visibly odd, not silently folded into today.
        val today = LocalDate.parse("2026-08-15")
        assertEquals("09:00:00 +1d", localStamp("2026-08-16T16:00:00Z", la, today))
    }

    @Test
    fun unparseableStampDegradesToADash() {
        assertEquals("—", localStamp("not-a-time", la, LocalDate.parse("2026-08-15")))
    }
}
