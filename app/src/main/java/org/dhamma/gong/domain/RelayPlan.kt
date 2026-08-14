package org.dhamma.gong.domain

import java.time.Duration
import java.time.ZonedDateTime

/**
 * Amplifier relay policy, as a pure function of the schedule state.
 *
 * Design: `docs/superpowers/specs/2026-08-09-shelly-relay-design.md`.
 *
 * The relay switches the centre amplifier on a few seconds before a gong or
 * doha and off again afterwards. It is a convenience: **nothing here may ever
 * change whether or when a play happens**. This object only reads the
 * `nextDeadline` [SchedulerCore.tick] already computes.
 *
 * Approach (C): pre-arm from the existing 30 s heartbeat rather than a new
 * alarm, so the amp comes on 5–35 s early rather than exactly 5 s. Harmless for
 * warm-up, and it keeps alarm plumbing out of the most safety-critical code.
 *
 * No Android imports — JVM unit tested, like [FireRules] and [ClockTrust].
 */
object RelayPlan {

    /** What the relay should be doing right now. */
    sealed interface Desired {
        /**
         * Switch on, carrying a device-side [toggleAfterSeconds] flip-back timer
         * so a dead tablet cannot leave the amp energised overnight.
         */
        data class On(val toggleAfterSeconds: Long) : Desired

        /** Switch off now. */
        data object Off : Desired

        /**
         * Leave the device alone. This is what makes ON rising-edge rather than
         * chatty: re-sending ON every heartbeat would silently refresh
         * `toggle_after` and defeat the watchdog.
         */
        data object NoChange : Desired
    }

    /** Conservative per-strike allowance; bundled tracks are a few seconds. */
    const val PER_STRIKE_SECONDS: Long = 10

    /** Doha length is unknown before playback and a chant can run long. */
    const val DOHA_CEILING_SECONDS: Long = 1800

    /** Watchdog slack on top of lead + play + lag. */
    const val MARGIN_SECONDS: Long = 60

    /**
     * @param now appliance-zone clock.
     * @param nextDeadline [TickOutcome.nextDeadline]; null when nothing is pending.
     * @param playerBusy [org.dhamma.gong.player.PlayerEngine.busy] — a play is in
     *   flight, **counting one that has been queued but has not reached the
     *   speaker yet**. It must not be `Status.playing`: the scheduler dispatches
     *   and calls the relay hook in the same tick, so the narrow flag is still
     *   false at the fire and [releaseIfArmed] below mistakes a gong that is
     *   starting for one that was missed, cutting power on the first strike.
     * @param armedForDeadline the deadline the relay was last switched on for,
     *   or null when it is not armed. The sticky arm is what lets a *missed*
     *   occurrence — which never reaches the player — still power the amp down.
     * @param lastPlayEndedAt when the last play finished, so OFF waits out the lag.
     * @param estimatedPlaySeconds see [estimatePlaySeconds].
     */
    fun decide(
        now: ZonedDateTime,
        nextDeadline: ZonedDateTime?,
        playerBusy: Boolean,
        armedForDeadline: ZonedDateTime?,
        relayEnabled: Boolean,
        clockTrusted: Boolean,
        leadSeconds: Long,
        lagSeconds: Long,
        heartbeat: Duration,
        estimatedPlaySeconds: Long = DOHA_CEILING_SECONDS,
        lastPlayEndedAt: ZonedDateTime? = null,
    ): Desired {
        // Rules 2 and 4: a disabled relay or an untrusted clock never switches
        // ON automatically. If we are still armed from before, release the amp
        // rather than leaving it energised until `toggle_after` expires.
        if (!relayEnabled || !clockTrusted) {
            return releaseIfArmed(now, playerBusy, armedForDeadline, lagSeconds, lastPlayEndedAt)
        }

        val inWindow = nextDeadline != null &&
            secondsUntil(now, nextDeadline) <= heartbeat.seconds + leadSeconds

        if (inWindow) {
            // Rule 6: rising edge only. Already armed for this very deadline →
            // say nothing, so `toggle_after` is not pushed forward forever.
            if (armedForDeadline == nextDeadline) return Desired.NoChange
            // A *different* deadline entered the window (back-to-back gong →
            // doha). ON again, which correctly re-sizes the watchdog for the
            // new play — and is never an OFF between the two (rule 5).
            return Desired.On(toggleAfterSeconds(leadSeconds, estimatedPlaySeconds, lagSeconds))
        }

        // Nothing in the pre-arm window. If we armed for a deadline that has
        // since fired, been missed or been superseded, this is the sticky-arm
        // release — the tick path owns it because a missed occurrence never
        // reaches the player.
        return releaseIfArmed(now, playerBusy, armedForDeadline, lagSeconds, lastPlayEndedAt)
    }

    /**
     * `lead + estimatedPlaySeconds + lag + margin`.
     *
     * A watchdog, not a schedule: erring long is correct, erring short would
     * de-power the amp mid-chant.
     */
    fun toggleAfterSeconds(
        leadSeconds: Long,
        estimatedPlaySeconds: Long,
        lagSeconds: Long,
    ): Long = maxOf(0, leadSeconds) +
        maxOf(0, estimatedPlaySeconds) +
        maxOf(0, lagSeconds) +
        MARGIN_SECONDS

    /**
     * Gong: `repeats × (10 + gap)`. Doha: a fixed [DOHA_CEILING_SECONDS] ceiling,
     * because the length is not known until the file plays.
     */
    fun estimatePlaySeconds(kind: String, repeats: Int, gapSeconds: Int): Long = when (kind) {
        PlayKind.GONG, PlayKind.TEST_GONG ->
            maxOf(1, repeats) * (PER_STRIKE_SECONDS + maxOf(0, gapSeconds))

        else -> DOHA_CEILING_SECONDS
    }

    // ------------------------------------------------------------ the log row

    /** Why the relay moved — the first thing the DETAIL column says. */
    object Reason {
        /** The pre-arm window or the sticky-arm release, i.e. [decide]. */
        const val SCHEDULE = "schedule"

        /** Somebody pressed on or off on the Amp power screen. */
        const val MANUAL = "manual"

        /** The reachability probe. Switches nothing. */
        const val TEST = "test"
    }

    /**
     * One `play_log` row for one relay transition.
     *
     * Pure, so what the row *says* is unit-tested without a Shelly, a database
     * or a coroutine. [host] lands in the FILE column: a LAN address is not a
     * secret and is exactly what someone diagnosing a dead amp needs, but
     * anything before an `@` is dropped in case a host was ever typed as
     * `user:pass@10.0.0.5` — the relay password has its own field and must not
     * reach a log by the back door.
     *
     * [repeats] is 0 because nothing was struck.
     */
    fun ampLog(
        kind: String,
        host: String,
        ok: Boolean,
        reason: String,
        error: String = "",
    ): PlayLogEntry = PlayLogEntry(
        kind = kind,
        file = safeHost(host),
        repeats = 0,
        result = if (ok) PlayResult.OK else PlayResult.ERROR,
        detail = when {
            ok -> reason
            error.isBlank() -> reason
            else -> "$reason — $error"
        },
    )

    private fun safeHost(host: String): String {
        val bare = host.trim().substringAfterLast('@')
        return bare.ifBlank { "-" }
    }

    // ------------------------------------------------------------ internals

    private fun releaseIfArmed(
        now: ZonedDateTime,
        playerBusy: Boolean,
        armedForDeadline: ZonedDateTime?,
        lagSeconds: Long,
        lastPlayEndedAt: ZonedDateTime?,
    ): Desired {
        if (armedForDeadline == null) return Desired.NoChange
        // Rule 5: never cut power out from under a play in progress —
        // including one that is queued and about to sound.
        if (playerBusy) return Desired.NoChange
        // Lag-out: hold the amp for `lagSeconds` past the end of the play so a
        // reverberating strike is not clipped by a relay click.
        if (lastPlayEndedAt != null && secondsUntil(lastPlayEndedAt, now) < lagSeconds) {
            return Desired.NoChange
        }
        return Desired.Off
    }

    private fun secondsUntil(from: ZonedDateTime, to: ZonedDateTime): Long =
        Duration.between(from.toInstant(), to.toInstant()).seconds
}
