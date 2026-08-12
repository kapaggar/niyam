package org.dhamma.gong.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeModeTest {

    @Test
    fun theShippedDefaultIsDark() {
        // Not a preference — a hall full of people sitting in the dark at 04:00.
        // If this ever flips, it has to be somebody's deliberate decision.
        assertEquals(ThemeMode.DARK, ThemeMode.DEFAULT)
        assertEquals("dark", SettingsDefaults.all.getValue(ThemeMode.SETTING_KEY))
    }

    @Test
    fun garbageResolvesToTheDefaultRatherThanCrashing() {
        // A restore from an older backup has no `theme` row at all; a
        // hand-edited one can have anything.
        listOf(null, "", "  ", "nocturne", "DARKK", "0").forEach {
            assertEquals("input=$it", ThemeMode.DEFAULT, ThemeMode.parse(it))
        }
    }

    @Test
    fun parseIsCaseAndWhitespaceInsensitive() {
        assertEquals(ThemeMode.LIGHT, ThemeMode.parse("light"))
        assertEquals(ThemeMode.LIGHT, ThemeMode.parse(" Light "))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.parse("SYSTEM"))
    }

    @Test
    fun everyModeRoundTripsThroughItsStoredKey() {
        ThemeMode.entries.forEach { assertEquals(it, ThemeMode.parse(it.key)) }
    }

    @Test
    fun onlySystemLooksAtTheDevice() {
        assertTrue(ThemeMode.DARK.isDark(deviceIsDark = false))
        assertFalse(ThemeMode.LIGHT.isDark(deviceIsDark = true))
        assertTrue(ThemeMode.SYSTEM.isDark(deviceIsDark = true))
        assertFalse(ThemeMode.SYSTEM.isDark(deviceIsDark = false))
    }
}
