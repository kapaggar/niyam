package org.dhamma.gong.schedule

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.select
import org.dhamma.gong.data.GongRepository
import org.dhamma.gong.domain.ActiveCourse
import org.dhamma.gong.domain.ClockTrust
import org.dhamma.gong.domain.CourseCtx
import org.dhamma.gong.domain.FiredMark
import org.dhamma.gong.domain.GongClock
import org.dhamma.gong.domain.MissedMark
import org.dhamma.gong.domain.Occurrence
import org.dhamma.gong.domain.PlayCommand
import org.dhamma.gong.domain.ScheduleMaterializer
import org.dhamma.gong.domain.SchedulerCore
import org.dhamma.gong.domain.TickOutcome
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * The scheduler loop — the Android shell around [SchedulerCore].
 *
 * State machine from design doc §05:
 *   BOOT → RESOLVE → MATERIALIZE → ARMED → FIRING, with CLOCK_UNTRUSTED as the
 *   one guarded side branch.
 *
 * Belt and braces: an alarm is armed for the next occurrence *and* the loop
 * wakes at least every [HEARTBEAT] regardless. Alarms are an optimisation.
 */
class SchedulerEngine(
    private val repo: GongRepository,
    private val clock: GongClock,
    private val alarms: AlarmScheduler,
    private val scope: CoroutineScope,
    private val dispatch: suspend (PlayCommand) -> Unit,
    private val warmUp: () -> Unit = {},
    /**
     * Amplifier relay pre-arm hook: `(now, nextDeadline, clockTrusted)`.
     *
     * Fire-and-forget by contract — it must return immediately and must never
     * make this tick wait on a network call. The relay is a convenience; the
     * gong is the product. The tick path owns *both* relay edges because a
     * missed occurrence never reaches the player.
     */
    private val relayTick: (ZonedDateTime, ZonedDateTime?, Boolean) -> Unit = { _, _, _ -> },
) {

    data class State(
        val running: Boolean = false,
        val clockTrusted: Boolean = true,
        val course: CourseCtx? = null,
        val next: Occurrence? = null,
        val upcoming: List<Occurrence> = emptyList(),
        val lastTick: ZonedDateTime? = null,
        val exactAlarmsAllowed: Boolean = true,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    /** Anything that invalidates the materialized day sends a poke. */
    private val pokes = Channel<String>(Channel.CONFLATED)

    private var loop: Job? = null
    private var lastTouch: ZonedDateTime? = null
    private var lastPrune: LocalDate? = null
    private var warmedFor: ZonedDateTime? = null

    // ------------------------------------------------------------ lifecycle

    fun start() {
        if (loop?.isActive == true) return
        loop = scope.launch {
            // BOOT: did the wall clock move backwards while we were dead?
            val trusted = repo.checkClockOnStart(clock.now())
            if (!trusted) {
                Log.w(TAG, "clock untrusted at start — automatic playback suppressed")
            }
            _state.value = _state.value.copy(running = true, clockTrusted = trusted)
            run()
        }
    }

    fun stop() {
        loop?.cancel()
        alarms.cancel()
        _state.value = _state.value.copy(running = false)
    }

    /** Course edited, setting changed, alarm fired, time zone flipped… */
    fun poke(reason: String) {
        pokes.trySend(reason)
    }

    // ------------------------------------------------------------ loop

    private suspend fun run() {
        while (true) {
            val now = clock.now()
            val waitMs = runCatching { tick(now) }
                .onFailure { Log.e(TAG, "tick failed", it) }
                .getOrDefault(HEARTBEAT_MS)

            // Wake on whichever comes first: the next deadline, the heartbeat,
            // or a poke. Never sleep past the heartbeat, so a clock jump is
            // noticed within 30 s (design doc §05).
            select {
                pokes.onReceive { reason -> Log.i(TAG, "poked: $reason") }
                onTimeout(waitMs.coerceIn(MIN_WAIT_MS, HEARTBEAT_MS)) { }
            }
        }
    }

    /**
     * One pass of the state machine. Returns how long the loop may sleep.
     */
    suspend fun tick(now: ZonedDateTime): Long {
        // Clock trust: advance the watermark, which also clears untrusted mode
        // once an NTP step carries us past the last known-good instant.
        if (ClockTrust.shouldTouch(lastTouch, now)) {
            repo.touchClock(now)
            lastTouch = now
        }
        val trusted = repo.clockTrusted()

        val today = now.toLocalDate()
        if (lastPrune != today) {
            repo.pruneFired(today)
            repo.trimLog()
            lastPrune = today
        }

        val snapshot = repo.snapshot()
        // Read the guard set once, in the same pass that will write to it.
        val fired = repo.firedKeys()
        val missed = repo.missedKeys()

        val outcome: TickOutcome = SchedulerCore.tick(
            clock = clock,
            now = now,
            snapshot = snapshot,
            firedGuard = { key, date -> FiredMark(key, date).stateKey in fired },
            missedGuard = { key, date -> MissedMark(key, date).stateKey in missed },
            clockTrusted = trusted,
        )

        // Guards first, committed, and only then does the player see anything.
        repo.applyOutcome(outcome, now.toInstant())
        for (command in outcome.fired) {
            Log.i(TAG, "firing ${command.label}")
            dispatch(command)
        }

        // Publish what the dashboard needs.
        val upcoming = ScheduleMaterializer
            .materialize(clock, today, snapshot, days = 2)
            .filter { it.fireAt.toInstant() > now.toInstant() }
        val course = ActiveCourse.resolve(
            snapshot.courses,
            snapshot.typesById,
            today,
            snapshot.setting("active_course_id").takeIf { it.isNotBlank() },
        )
        // Pi parity (FABLE-REVIEW B9): write the resolved course id so a later
        // process restart, pin read, and UI row stay on the same course even if
        // another overlapping window would otherwise re-pick by most-recent start.
        // Only rewrite when the value actually changes — avoid a settings poke
        // storm every heartbeat.
        val resolvedId = course?.courseId?.toString().orEmpty()
        val storedId = snapshot.setting("active_course_id")
        if (resolvedId != storedId) {
            repo.putSetting("active_course_id", resolvedId)
        }
        _state.value = _state.value.copy(
            clockTrusted = trusted,
            course = course,
            next = upcoming.firstOrNull(),
            upcoming = upcoming.take(UPCOMING_SHOWN),
            lastTick = now,
            exactAlarmsAllowed = alarms.canScheduleExact(),
        )

        val deadline = outcome.nextDeadline

        // Amplifier relay: reads the deadline we just computed and nothing more.
        // Called on both paths — a null deadline is exactly how a missed or
        // fired occurrence releases the sticky arm. Wrapped because a relay
        // fault must never abort a scheduler tick.
        runCatching { relayTick(now, deadline, trusted) }
            .onFailure { Log.w(TAG, "relay hook failed (schedule unaffected)", it) }

        if (deadline != null) {
            alarms.arm(deadline)
            maybeWarmUp(now, deadline)
            return Duration.between(now.toInstant(), deadline.toInstant()).toMillis()
        }
        // No deadline (nothing pending, or the clock is untrusted): heartbeat only.
        alarms.cancel()
        return HEARTBEAT_MS
    }

    /** Open the audio device ahead of the fire so strike 1 is not clipped. */
    private fun maybeWarmUp(now: ZonedDateTime, deadline: ZonedDateTime) {
        if (warmedFor == deadline) return
        val lead = Duration.between(now.toInstant(), deadline.toInstant()).seconds
        if (lead in 0..WARM_UP_LEAD_SECONDS) {
            warmedFor = deadline
            warmUp()
        }
    }

    companion object {
        private const val TAG = "SchedulerEngine"

        /** Design doc §03: the service holds this loop even when alarms are armed. */
        val HEARTBEAT: Duration = Duration.ofSeconds(30)
        val HEARTBEAT_MS: Long = HEARTBEAT.toMillis()

        /** Never spin: the grace window is 120 s, a 1 s floor is plenty. */
        const val MIN_WAIT_MS = 1_000L

        /** Design doc §06: prepare the route 15 s before fire_at. */
        const val WARM_UP_LEAD_SECONDS = 15L

        const val UPCOMING_SHOWN = 12
    }
}
