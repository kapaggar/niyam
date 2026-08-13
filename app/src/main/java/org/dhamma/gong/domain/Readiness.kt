package org.dhamma.gong.domain

/**
 * "Is this tablet actually fit to be left alone for three weeks?"
 *
 * Setup's one-line answer. It is deliberately an **and** of everything: a
 * checklist that reads mostly-green invites a tech volunteer to walk away, and
 * the grants that get skipped are exactly the ones that cost a 04:00 gong three
 * weeks later, long after anyone connects the two.
 *
 * Pure so the rule is testable and cannot drift from what the screen paints.
 */
object Readiness {

    data class Checks(
        val notificationsGranted: Boolean,
        val exactAlarmsAllowed: Boolean,
        val batteryUnrestricted: Boolean,
        /** Scheduler ticking within [Liveness.STALE_AFTER]. */
        val serviceAlive: Boolean,
        val pinSet: Boolean,
    )

    /**
     * What is standing between this tablet and READY, in the order staff
     * should fix it: the things that stop gongs first, cosmetics last.
     */
    fun blockers(c: Checks): List<String> = buildList {
        if (!c.serviceAlive) add("the scheduler is not ticking")
        if (!c.exactAlarmsAllowed) add("exact alarms are denied")
        if (!c.batteryUnrestricted) add("battery is restricted")
        if (!c.notificationsGranted) add("notifications are denied")
        if (!c.pinSet) add("no PIN is set")
    }

    fun isReady(c: Checks): Boolean = blockers(c).isEmpty()

    /**
     * A one-line summary for the banner.
     *
     * Names the first blocker rather than only counting them — "1 thing to fix"
     * sends someone hunting, "exact alarms are denied" sends them to the row.
     */
    fun summary(c: Checks): String {
        val blockers = blockers(c)
        return when (blockers.size) {
            0 -> "Every grant is in place and the scheduler is ticking. " +
                "This tablet can be left on charge."
            1 -> "Not ready — ${blockers.single()}."
            else -> "Not ready — ${blockers.first()}, and ${blockers.size - 1} more below."
        }
    }
}
