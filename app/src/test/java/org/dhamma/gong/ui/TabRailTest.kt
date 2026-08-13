package org.dhamma.gong.ui

import org.dhamma.gong.domain.UiMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun networkIsNotADestinationInEitherMode() {
        // Spec §6: the facts live on Setup. A rail entry for them was one tap
        // to a screen that only ever reported.
        val names = Tab.entries.map { it.name }
        assertTrue("NETWORK must be gone from Tab entirely", "NETWORK" !in names)
    }

    // --- shouldApplyTabRequest: cold-start deep links to Advanced-only tabs ---
    //
    // GongApp seeds `uiMode` with SIMPLE (Eagerly, before Room answers) so the
    // first frame never flashes nine items. That means a deep link to POWER
    // arriving (or already pending) while `mode` still holds that provisional
    // value must not be dropped for good — it has to survive to be retried
    // once the real mode comes in. These are the two ends of that rule.

    @Test
    fun requestForAnAdvancedOnlyTabIsNotAppliedUnderTheProvisionalSimpleRail() {
        val simpleRail = Tab.railFor(UiMode.SIMPLE)
        assertFalse(Tab.shouldApplyTabRequest(Tab.POWER, simpleRail))
    }

    @Test
    fun sameRequestAppliesOnceTheRealAdvancedModeArrives() {
        val advancedRail = Tab.railFor(UiMode.ADVANCED)
        assertTrue(Tab.shouldApplyTabRequest(Tab.POWER, advancedRail))
    }

    @Test
    fun noPendingRequestIsNeverApplied() {
        assertFalse(Tab.shouldApplyTabRequest(null, Tab.railFor(UiMode.ADVANCED)))
    }

    @Test
    fun requestForATabInTheCurrentRailApplies() {
        assertTrue(Tab.shouldApplyTabRequest(Tab.DASHBOARD, Tab.railFor(UiMode.SIMPLE)))
    }
}
