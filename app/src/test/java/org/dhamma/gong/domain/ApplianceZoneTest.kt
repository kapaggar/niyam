package org.dhamma.gong.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * FABLE-REVIEW B1: the appliance timezone comes from the `timezone` setting,
 * never from the phone's travel TZ. Pi parity: the Pi daemon's config.py defaults
 * `timezone = "Asia/Kolkata"`.
 */
class ApplianceZoneTest {

    @Test
    fun validIdResolvesToThatZone() {
        assertEquals(ZoneId.of("Europe/London"), ApplianceZone.resolve("Europe/London"))
        assertEquals(ZoneId.of("Asia/Kolkata"), ApplianceZone.resolve("Asia/Kolkata"))
    }

    @Test
    fun blankFallsBackToKolkata() {
        // Devices seeded before this fix have a persisted `timezone` row of ""
        // (SeedLoader insertMissing never clobbers) — blank must mean default.
        assertEquals(ZoneId.of("Asia/Kolkata"), ApplianceZone.resolve(""))
        assertEquals(ZoneId.of("Asia/Kolkata"), ApplianceZone.resolve("   "))
    }

    @Test
    fun nullFallsBackToKolkata() {
        assertEquals(ZoneId.of("Asia/Kolkata"), ApplianceZone.resolve(null))
    }

    @Test
    fun invalidIdFallsBackToKolkata() {
        // A corrupt setting must never crash the appliance or silently pick UTC.
        assertEquals(ZoneId.of("Asia/Kolkata"), ApplianceZone.resolve("Mars/Olympus_Mons"))
        assertEquals(ZoneId.of("Asia/Kolkata"), ApplianceZone.resolve("not a zone"))
    }

    @Test
    fun surroundingWhitespaceIsTolerated() {
        assertEquals(ZoneId.of("Europe/London"), ApplianceZone.resolve(" Europe/London "))
    }

    @Test
    fun settingsDefaultIsKolkataNotEmpty() {
        // Fresh installs must seed the Pi default, not "unset".
        assertEquals("Asia/Kolkata", SettingsDefaults.all["timezone"])
    }

    @Test
    fun systemClockFollowsAZoneProviderChange() {
        // The service holds one clock for its lifetime; a Time-screen edit must
        // take effect without a service restart.
        var zone = ZoneId.of("Asia/Kolkata")
        val clock = SystemGongClock { zone }
        val day = LocalDate.of(2026, 8, 10)
        val t = LocalTime.of(4, 0)

        assertEquals(ZoneId.of("Asia/Kolkata"), clock.materialize(day, t).zone)
        zone = ZoneId.of("Europe/London")
        assertEquals(ZoneId.of("Europe/London"), clock.materialize(day, t).zone)
    }
}
