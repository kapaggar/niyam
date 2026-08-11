package org.dhamma.gong.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadinessTest {

    private val allGood = Readiness.Checks(
        notificationsGranted = true,
        exactAlarmsAllowed = true,
        batteryUnrestricted = true,
        serviceAlive = true,
        pinSet = true,
    )

    @Test
    fun everythingGreenIsReady() {
        assertTrue(Readiness.isReady(allGood))
        assertTrue(Readiness.blockers(allGood).isEmpty())
    }

    @Test
    fun anySingleFailureBlocksReady() {
        // The whole point of an aggregate: mostly-green must not read as ready,
        // because the skipped grant is what costs a gong three weeks later.
        val each = listOf(
            allGood.copy(notificationsGranted = false),
            allGood.copy(exactAlarmsAllowed = false),
            allGood.copy(batteryUnrestricted = false),
            allGood.copy(serviceAlive = false),
            allGood.copy(pinSet = false),
        )
        for (checks in each) {
            assertFalse("$checks must not be ready", Readiness.isReady(checks))
            assertEquals(1, Readiness.blockers(checks).size)
        }
    }

    @Test
    fun blockersAreOrderedWorstFirst() {
        val nothing = Readiness.Checks(false, false, false, false, false)
        val blockers = Readiness.blockers(nothing)
        assertEquals(5, blockers.size)
        // A dead scheduler outranks a missing PIN — fix what stops gongs first.
        assertTrue(blockers.first().contains("scheduler"))
        assertTrue(blockers.last().contains("PIN"))
    }

    @Test
    fun theSummaryNamesTheBlockerRatherThanCountingIt() {
        // "1 thing to fix" sends someone hunting; naming it sends them to the row.
        val one = allGood.copy(exactAlarmsAllowed = false)
        assertEquals("Not ready — exact alarms are denied.", Readiness.summary(one))
    }

    @Test
    fun theSummaryCountsTheRestWhenSeveralAreWrong() {
        val two = allGood.copy(exactAlarmsAllowed = false, pinSet = false)
        val summary = Readiness.summary(two)
        assertTrue(summary, summary.startsWith("Not ready — exact alarms are denied"))
        assertTrue(summary, summary.contains("1 more"))
    }

    @Test
    fun theReadySummaryTellsStaffTheyCanWalkAway() {
        assertTrue(Readiness.summary(allGood).contains("left on charge"))
    }
}
