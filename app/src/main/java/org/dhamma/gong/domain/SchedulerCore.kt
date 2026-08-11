package org.dhamma.gong.domain

import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * The scheduler, as a pure function of (now, snapshot, fired-guard).
 *
 * Port of the Pi daemon's scheduler.py `Scheduler.tick` + `dispatch`. The Android
 * service is a shell that loads a [ScheduleSnapshot], calls [tick], applies the
 * [TickOutcome] in order, and re-arms an alarm for [TickOutcome.nextDeadline].
 *
 * Guarantees (design doc §05):
 *  - never fires early;
 *  - never fires twice for one (event, local date);
 *  - fires late only inside the grace window, else logs `missed`;
 *  - suppresses every automatic play while the clock is untrusted.
 */
object SchedulerCore {

    /**
     * @param firedGuard true when `state["fired:<key>:<date>"]` already exists.
     *   Must be read from the same transaction the caller writes
     *   [TickOutcome.marks] into.
     * @param clockTrusted result of [ClockTrust.isTrusted].
     */
    fun tick(
        clock: GongClock,
        now: ZonedDateTime,
        snapshot: ScheduleSnapshot,
        firedGuard: (key: String, date: LocalDate) -> Boolean,
        clockTrusted: Boolean = true,
        graceSeconds: Long = SettingsDefaults.FIRE_GRACE_SECONDS,
    ): TickOutcome {
        // §05: untrusted clock suppresses automatic playback entirely. The Pi daemon
        // returns no deadline at all, so the loop falls back to its heartbeat.
        if (!clockTrusted) return TickOutcome()

        val grace = Duration.ofSeconds(graceSeconds)
        val marks = ArrayList<FiredMark>()
        val logs = ArrayList<PlayLogEntry>()
        val fired = ArrayList<PlayCommand>()
        var next: ZonedDateTime? = null

        for (occ in ScheduleMaterializer.materialize(clock, now.toLocalDate(), snapshot)) {
            val decision = FireRules.decide(
                now = now.toInstant(),
                fireAt = occ.fireAt.toInstant(),
                alreadyFired = firedGuard(occ.key, occ.localDate),
                clockOk = true, // handled above, for the whole tick
                graceSeconds = grace.seconds,
            )
            when (decision) {
                FireDecision.ALREADY_FIRED, FireDecision.SKIPPED_CLOCK -> continue

                FireDecision.TOO_EARLY -> {
                    if (next == null || occ.fireAt.toInstant() < next.toInstant()) next = occ.fireAt
                }

                // Past due. The Pi daemon marks fired *before* deciding what to do with it,
                // so a disabled toggle or a missed window still consumes the slot.
                FireDecision.MISSED -> {
                    marks += FiredMark(occ.key, occ.localDate)
                    logs += PlayLogEntry(
                        kind = occ.kind.logName,
                        file = "-",
                        repeats = occ.repeats,
                        result = PlayResult.MISSED,
                        detail = "scheduled ${occ.fireAt}",
                    )
                }

                FireDecision.FIRE -> {
                    marks += FiredMark(occ.key, occ.localDate)
                    val dispatched = dispatch(occ, snapshot)
                    dispatched.log?.let { logs += it }
                    dispatched.command?.let { fired += it }
                }
            }
        }

        return TickOutcome(marks = marks, logs = logs, fired = fired, nextDeadline = next)
    }

    private data class Dispatched(val command: PlayCommand?, val log: PlayLogEntry?)

    /** Port of `Scheduler.dispatch` — resolves inherited settings and toggles. */
    private fun dispatch(
        occ: Occurrence,
        snapshot: ScheduleSnapshot,
    ): Dispatched {
        val master = snapshot.settingBool("enabled")
        val course = occ.ctx?.label ?: "No course"

        return when (occ.kind) {
            Occurrence.Kind.GONG -> {
                if (!master || !snapshot.settingBool("gong_enabled")) return Dispatched(null, null)
                val track = occ.track ?: snapshot.setting("gong_track")
                val gap = occ.gapSeconds ?: snapshot.settingInt("gong_gap_seconds")
                Dispatched(
                    PlayCommand(
                        kind = PlayKind.GONG,
                        trackStem = track,
                        repeats = occ.repeats,
                        gapSeconds = maxOf(0, gap),
                        volume = snapshot.settingInt("gong_volume"),
                        label = "$course, ${"%02d:%02d".format(
                            occ.fireAt.hour,
                            occ.fireAt.minute,
                        )} x${occ.repeats}",
                    ),
                    null,
                )
            }

            Occurrence.Kind.DOHA -> {
                if (!master || !snapshot.settingBool("doha_enabled")) return Dispatched(null, null)
                val slot = DohaSlots.pickSlot(
                    ctx = occ.ctx,
                    noCourseDoha = snapshot.setting("no_course_doha"),
                    // Same calendar day must always resolve to the same doha,
                    // or a re-materialize after a restart contradicts the
                    // fired-guard and the log.
                    date = occ.localDate,
                ) ?: return Dispatched(null, null)

                // Missing media is an error log, never a crash (design doc §05).
                if (snapshot.mappedDohaSlots.isNotEmpty() && slot !in snapshot.mappedDohaSlots) {
                    return Dispatched(
                        null,
                        PlayLogEntry(
                            kind = PlayKind.DOHA,
                            file = "slot $slot",
                            repeats = 1,
                            result = PlayResult.ERROR,
                            detail = "slot not mapped",
                        ),
                    )
                }
                if (snapshot.mappedDohaSlots.isEmpty()) {
                    // "gongs only" is a normal state, not an error — stay silent.
                    return Dispatched(null, null)
                }
                Dispatched(
                    PlayCommand(
                        kind = PlayKind.DOHA,
                        dohaSlot = slot,
                        repeats = 1,
                        gapSeconds = 0,
                        volume = snapshot.settingInt("doha_volume"),
                        label = "$course, doha slot $slot",
                    ),
                    null,
                )
            }
        }
    }
}

private val Occurrence.Kind.logName: String
    get() = when (this) {
        Occurrence.Kind.GONG -> PlayKind.GONG
        Occurrence.Kind.DOHA -> PlayKind.DOHA
    }
