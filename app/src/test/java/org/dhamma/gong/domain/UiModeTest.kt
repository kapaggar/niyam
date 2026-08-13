package org.dhamma.gong.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class UiModeTest {

    @Test
    fun theShippedDefaultIsSimple() {
        // A centre server opening the appliance for the first time gets five
        // screens, not nine. Flipping this is a product decision, not a tidy-up.
        assertEquals(UiMode.SIMPLE, UiMode.DEFAULT)
        assertEquals("simple", SettingsDefaults.all.getValue(UiMode.SETTING_KEY))
    }

    @Test
    fun garbageResolvesToTheDefaultRatherThanCrashing() {
        // A restore from a backup written before this release has no `ui_mode`
        // row at all; a hand-edited one can hold anything.
        listOf(null, "", "  ", "expert", "SIMPLEE", "1").forEach {
            assertEquals("input=$it", UiMode.DEFAULT, UiMode.parse(it))
        }
    }

    @Test
    fun parseIsCaseAndWhitespaceInsensitive() {
        assertEquals(UiMode.ADVANCED, UiMode.parse("advanced"))
        assertEquals(UiMode.ADVANCED, UiMode.parse(" Advanced "))
        assertEquals(UiMode.SIMPLE, UiMode.parse("SIMPLE"))
    }

    @Test
    fun everyModeRoundTripsThroughItsStoredKey() {
        UiMode.entries.forEach { assertEquals(it, UiMode.parse(it.key)) }
    }
}
