package org.dhamma.gong.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutePlanTest {

    private val all = listOf(RoutePlan.SPEAKER, RoutePlan.BLUETOOTH, RoutePlan.USB)

    // ------------------------------------------------------------ choose

    @Test
    fun anAvailablePreferenceIsUsedAsIs() {
        val c = RoutePlan.choose(all, RoutePlan.BLUETOOTH)
        assertEquals(RoutePlan.BLUETOOTH, c.key)
        assertFalse(c.fellBack)
    }

    @Test
    fun aMissingPreferenceFallsBackToTheSpeakerAndSaysSo() {
        val c = RoutePlan.choose(listOf(RoutePlan.SPEAKER), RoutePlan.BLUETOOTH)
        assertEquals(RoutePlan.SPEAKER, c.key)
        // The requested key survives so the play_log can name what was missing.
        assertEquals(RoutePlan.BLUETOOTH, c.requested)
        assertTrue(c.fellBack)
    }

    @Test
    fun blankOrNullPreferenceIsTheSpeakerAndIsNotAFallback() {
        for (pref in listOf(null, "", "   ")) {
            val c = RoutePlan.choose(all, pref)
            assertEquals(RoutePlan.SPEAKER, c.key)
            assertFalse("blank preference must not read as a fallback", c.fellBack)
        }
    }

    @Test
    fun theSpeakerIsChosenEvenWhenTheProbeListedNothing() {
        // A device query that came back empty must never conclude the appliance
        // has no output at all.
        val c = RoutePlan.choose(emptyList(), RoutePlan.SPEAKER)
        assertEquals(RoutePlan.SPEAKER, c.key)
        assertFalse(c.fellBack)
    }

    @Test
    fun anUnknownKeyFallsBackRatherThanBeingTrusted() {
        val c = RoutePlan.choose(all, "carrier_pigeon")
        assertEquals(RoutePlan.SPEAKER, c.key)
        assertTrue(c.fellBack)
    }

    // ------------------------------------------------------------ rows

    @Test
    fun rowsCoverEveryAvailableRouteInDisplayOrder() {
        val rows = RoutePlan.rows(listOf(RoutePlan.USB, RoutePlan.BLUETOOTH), RoutePlan.SPEAKER)
        assertEquals(
            listOf(RoutePlan.SPEAKER, RoutePlan.BLUETOOTH, RoutePlan.USB),
            rows.map { it.key },
        )
        assertTrue(rows.all { it.available })
    }

    @Test
    fun aSelectedRouteThatVanishedStillGetsARow() {
        // Otherwise the picker shows the speaker ticked while the setting says
        // bluetooth, and staff cannot see or undo the silent nightly fallback.
        val rows = RoutePlan.rows(listOf(RoutePlan.SPEAKER), RoutePlan.BLUETOOTH)
        val bt = rows.single { it.key == RoutePlan.BLUETOOTH }
        assertTrue(bt.selected)
        assertFalse(bt.available)
    }

    @Test
    fun theSpeakerRowExistsEvenIfTheProbeMissedIt() {
        val rows = RoutePlan.rows(emptyList(), RoutePlan.USB)
        assertTrue(rows.any { it.key == RoutePlan.SPEAKER && it.available })
    }

    @Test
    fun exactlyOneRowIsSelected() {
        val rows = RoutePlan.rows(all, RoutePlan.USB)
        assertEquals(1, rows.count { it.selected })
        assertEquals(RoutePlan.USB, rows.single { it.selected }.key)
    }

    @Test
    fun blankPreferenceSelectsTheSpeakerRow() {
        val rows = RoutePlan.rows(all, "")
        assertEquals(RoutePlan.SPEAKER, rows.single { it.selected }.key)
    }

    @Test
    fun lastKnownGoodMarksAtMostOneRowAndIsBlankSafe() {
        val marked = RoutePlan.rows(all, RoutePlan.SPEAKER, RoutePlan.USB)
        assertEquals(listOf(RoutePlan.USB), marked.filter { it.lastOk }.map { it.key })

        for (blank in listOf(null, "", "  ")) {
            assertTrue(
                "a never-fired appliance must mark nothing",
                RoutePlan.rows(all, RoutePlan.SPEAKER, blank).none { it.lastOk },
            )
        }
    }

    @Test
    fun genericLabelsAreStaffFacingAndUnknownKeysPassThrough() {
        assertEquals("Built-in speaker", RoutePlan.genericLabel(RoutePlan.SPEAKER))
        assertEquals("Bluetooth", RoutePlan.genericLabel(RoutePlan.BLUETOOTH))
        assertEquals("USB audio", RoutePlan.genericLabel(RoutePlan.USB))
        assertEquals("mystery", RoutePlan.genericLabel("mystery"))
    }
}
