package org.dhamma.gong.domain

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * The one place that decides whether an occurrence may play right now
 * (design doc §05, `ng/gong_ng/scheduler.py`).
 *
 * [SchedulerCore] calls this; nothing else should re-implement the window.
 */
object FireRules {

    fun decide(
        now: Instant,
        fireAt: Instant,
        alreadyFired: Boolean,
        clockOk: Boolean,
        graceSeconds: Long = SettingsDefaults.FIRE_GRACE_SECONDS,
    ): FireDecision {
        if (!clockOk) return FireDecision.SKIPPED_CLOCK
        if (alreadyFired) return FireDecision.ALREADY_FIRED
        if (now < fireAt) return FireDecision.TOO_EARLY
        val late = Duration.between(fireAt, now)
        return if (late.seconds <= graceSeconds) FireDecision.FIRE else FireDecision.MISSED
    }

    /** Convenience overload for wall-clock reasoning in tests. */
    fun decide(
        now: LocalDateTime,
        fireAt: LocalDateTime,
        alreadyFired: Boolean,
        clockOk: Boolean,
        graceSeconds: Long = SettingsDefaults.FIRE_GRACE_SECONDS,
    ): FireDecision = decide(
        now.toInstant(ZoneOffset.UTC),
        fireAt.toInstant(ZoneOffset.UTC),
        alreadyFired,
        clockOk,
        graceSeconds,
    )

    fun firedStateKey(eventKey: String, localDate: LocalDate): String =
        FiredMark(eventKey, localDate).stateKey
}
