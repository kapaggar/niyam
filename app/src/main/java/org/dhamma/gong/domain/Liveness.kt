package org.dhamma.gong.domain

import java.time.Duration
import java.time.ZonedDateTime

/**
 * "Is the appliance actually still running?"
 *
 * The scheduler heartbeat ticks every 30 s. A tick that is older than
 * [STALE_AFTER] means the loop has stopped even if the process is technically
 * alive — an OEM battery killer freezing the service, a wedged coroutine, a
 * Doze bucket nobody expected. That is the failure this appliance exists to
 * survive, and it is invisible from the outside: the notification stays up, the
 * UI still draws, and nothing rings at 04:00.
 *
 * The window is deliberately three heartbeats rather than two. One missed tick
 * is normal jitter on a loaded tablet; three in a row is not, and crying wolf
 * on a healthy appliance would train staff to ignore the one time it matters.
 *
 * Pure so the rule can be tested on the JVM — the Android side only supplies
 * the two timestamps.
 */
object Liveness {

    /** Three missed 30 s heartbeats. Fable uses the same 90 s. */
    val STALE_AFTER: Duration = Duration.ofSeconds(90)

    enum class Health {
        /** Ticking within the window. */
        ALIVE,

        /** Running, but the last tick is older than [STALE_AFTER]. */
        STALE,

        /** No tick has ever been recorded — the service has not started yet. */
        UNKNOWN,
    }

    fun health(
        lastTick: ZonedDateTime?,
        now: ZonedDateTime,
        staleAfter: Duration = STALE_AFTER,
    ): Health {
        if (lastTick == null) return Health.UNKNOWN
        val age = Duration.between(lastTick, now)
        // A tick in the future means the clock moved backwards under us, which
        // is the clock-trust problem and not a liveness one. Treat it as alive
        // rather than raising a second, misleading alarm about it.
        if (age.isNegative) return Health.ALIVE
        return if (age > staleAfter) Health.STALE else Health.ALIVE
    }

    fun isAlive(lastTick: ZonedDateTime?, now: ZonedDateTime): Boolean =
        health(lastTick, now) == Health.ALIVE

    /** "12 s ago" / "4 m ago" — the age staff read on Setup. */
    fun ageLabel(lastTick: ZonedDateTime?, now: ZonedDateTime): String {
        if (lastTick == null) return "never"
        val seconds = Duration.between(lastTick, now).seconds
        return when {
            seconds < 0 -> "just now"
            seconds < 60 -> "${seconds}s ago"
            seconds < 3600 -> "${seconds / 60}m ago"
            else -> "${seconds / 3600}h ago"
        }
    }
}
