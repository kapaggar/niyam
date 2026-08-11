package org.dhamma.gong.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * The schedule fires in the device's own zone unless a centre pins one.
 *
 * The appliance used to force `Asia/Kolkata`. That was one more thing to get
 * wrong on install day, and getting it wrong moves every gong — whereas a
 * tablet bought and installed at the centre already knows where it is and keeps
 * knowing across DST and government changes without anyone touching the app.
 * The pin remains as the escape hatch for a donated phone that insists it is
 * still somewhere else.
 */
class ApplianceZoneTest {

    @Test
    fun aPinnedZoneWins() {
        assertEquals(ZoneId.of("Europe/London"), ApplianceZone.resolve("Europe/London"))
        assertEquals(ZoneId.of("Asia/Kolkata"), ApplianceZone.resolve("Asia/Kolkata"))
    }

    @Test
    fun blankFollowsTheDevice() {
        assertEquals(ApplianceZone.deviceZone(), ApplianceZone.resolve(""))
        assertEquals(ApplianceZone.deviceZone(), ApplianceZone.resolve("   "))
    }

    @Test
    fun nullFollowsTheDevice() {
        assertEquals(ApplianceZone.deviceZone(), ApplianceZone.resolve(null))
    }

    @Test
    fun anUnparseableIdFollowsTheDeviceRatherThanAHardcodedZone() {
        // A corrupt row should leave the appliance on the tablet's own idea of
        // local time — very likely right — instead of silently moving every
        // gong to a zone nobody chose.
        assertEquals(ApplianceZone.deviceZone(), ApplianceZone.resolve("Mars/Olympus_Mons"))
        assertEquals(ApplianceZone.deviceZone(), ApplianceZone.resolve("not a zone"))
    }

    @Test
    fun surroundingWhitespaceIsTolerated() {
        assertEquals(ZoneId.of("Europe/London"), ApplianceZone.resolve(" Europe/London "))
    }

    @Test
    fun pinnedIsTrueOnlyForARealId() {
        assertFalse(ApplianceZone.isPinned(null))
        assertFalse(ApplianceZone.isPinned(""))
        assertFalse(ApplianceZone.isPinned("   "))
        assertTrue(ApplianceZone.isPinned("Europe/London"))
    }

    @Test
    fun freshInstallsSeedFollowTheDevice() {
        assertEquals(ApplianceZone.FOLLOW_DEVICE, SettingsDefaults.all["timezone"])
    }

    @Test
    fun anAlreadySeededKolkataRowKeepsWorking() {
        // Tablets seeded before this change persist "Asia/Kolkata".
        // `insertMissing` never rewrites a seeded setting, so those devices must
        // keep firing in IST rather than silently jumping to the device zone.
        assertEquals(ZoneId.of("Asia/Kolkata"), ApplianceZone.resolve("Asia/Kolkata"))
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
