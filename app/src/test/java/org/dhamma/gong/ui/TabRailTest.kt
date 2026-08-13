package org.dhamma.gong.ui

import org.dhamma.gong.domain.UiMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `Tab` is a plain enum with no Android types in it, so the rail's composition
 * is answerable on the JVM — which is the only reason this rule is a function
 * and not an `if` buried in a composable.
 */
class TabRailTest {

    @Test
    fun simpleShowsFiveDestinationsInWallOrder() {
        assertEquals(
            listOf(Tab.DASHBOARD, Tab.SCHEDULE, Tab.COURSES, Tab.LOGS, Tab.SETUP),
            Tab.railFor(UiMode.SIMPLE),
        )
    }

    @Test
    fun simpleHidesTheTechnicianScreens() {
        val simple = Tab.railFor(UiMode.SIMPLE)
        listOf(Tab.SOUNDS, Tab.AUDIO_OUT, Tab.POWER, Tab.TIME).forEach {
            assertTrue("$it must not be in the Simple rail", it !in simple)
        }
    }

    @Test
    fun advancedShowsEveryTab() {
        assertEquals(Tab.entries.toList(), Tab.railFor(UiMode.ADVANCED))
    }

    @Test
    fun everyModeStartsAtTheDashboard() {
        // GongApp falls back to DASHBOARD whenever the current tab leaves the
        // rail, so DASHBOARD being present in both modes is load-bearing.
        UiMode.entries.forEach {
            assertEquals("mode=$it", Tab.DASHBOARD, Tab.railFor(it).first())
        }
    }
}
