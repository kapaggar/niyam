package org.dhamma.gong.relay

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeout
import org.dhamma.gong.data.GongRepository
import org.dhamma.gong.domain.RelayPlan
import org.dhamma.gong.schedule.SchedulerEngine
import java.time.ZonedDateTime

/**
 * Owns the amplifier relay: the sticky arm, the reachability/last-action state
 * the Amp power screen observes, and the only place [ShellyClient] is called.
 *
 * **The one rule that outranks everything: the relay never blocks, delays, or
 * fails a play.** [onTick] and [onPlayEnded] are ordinary non-suspending
 * functions that hand off to [scope] and return immediately; every network call
 * runs inside a [withTimeout] budget on that scope. An unreachable Shelly logs
 * an error and the gong rings on time regardless.
 *
 * Calls are serialised with a `tryLock` mutex — if a switch is already in
 * flight, the new one is dropped rather than queued. One attempt per
 * transition; no retry storm.
 */
class RelayController(
    private val repo: GongRepository,
    private val scope: CoroutineScope,
    private val client: ShellyClient = ShellyClient(),
) {

    /** Everything the future Amp power screen needs; nothing secret. */
    data class State(
        val enabled: Boolean = false,
        val configured: Boolean = false,
        val host: String = "",
        /** null = never probed. */
        val reachable: Boolean? = null,
        /** "on", "off", "test", or "" — human-facing, from [Action]. */
        val lastAction: String = "",
        val lastActionAt: ZonedDateTime? = null,
        val lastActionOk: Boolean = false,
        val lastError: String = "",
        /** Model / MAC from the last successful `Shelly.GetDeviceInfo`. */
        val deviceInfo: String = "",
        /** True while the relay believes the amp is powered. */
        val armed: Boolean = false,
    )

    object Action {
        const val ON = "on"
        const val OFF = "off"
        const val TEST = "test"
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    /** The deadline the relay is currently armed for; null when not armed. */
    @Volatile
    private var armedForDeadline: ZonedDateTime? = null

    @Volatile
    private var lastPlayEndedAt: ZonedDateTime? = null

    /** Serialises switching. `tryLock`, never `lock` — busy means drop. */
    private val inFlight = Mutex()

    // ------------------------------------------------------------ scheduler hooks

    /**
     * Called once per scheduler tick. Returns immediately: the decision and any
     * switching happen on [scope].
     *
     * @param nextDeadline the scheduler's own `TickOutcome.nextDeadline`. The
     *   relay only reads it — it never influences whether or when a gong fires.
     */
    fun onTick(
        now: ZonedDateTime,
        nextDeadline: ZonedDateTime?,
        playing: Boolean,
        clockTrusted: Boolean,
    ) {
        scope.launch {
            runCatching { evaluate(now, nextDeadline, playing, clockTrusted) }
                .onFailure { Log.w(TAG, "relay tick failed (play unaffected)", it) }
        }
    }

    /**
     * Called when playback stops, so the lag-out is measured from the real end
     * of the burst rather than from the schedule.
     */
    fun onPlayEnded(now: ZonedDateTime) {
        lastPlayEndedAt = now
    }

    private suspend fun evaluate(
        now: ZonedDateTime,
        nextDeadline: ZonedDateTime?,
        playing: Boolean,
        clockTrusted: Boolean,
    ) {
        val cfg = readConfig()
        _state.value = _state.value.copy(
            enabled = cfg.enabled,
            configured = cfg.host.isNotEmpty(),
            host = cfg.host,
        )
        // Host unset: the whole feature is inert, and the arm cannot be stale
        // because we never armed.
        if (cfg.host.isEmpty()) return

        val desired = RelayPlan.decide(
            now = now,
            nextDeadline = nextDeadline,
            playing = playing,
            armedForDeadline = armedForDeadline,
            relayEnabled = cfg.enabled,
            clockTrusted = clockTrusted,
            leadSeconds = cfg.leadSeconds,
            lagSeconds = cfg.lagSeconds,
            heartbeat = SchedulerEngine.HEARTBEAT,
            estimatedPlaySeconds = estimateForNext(),
            lastPlayEndedAt = lastPlayEndedAt,
        )

        when (desired) {
            is RelayPlan.Desired.NoChange -> Unit

            is RelayPlan.Desired.On -> {
                // Arm before switching, and regardless of the outcome: that is
                // what makes it one attempt per transition rather than a retry
                // storm against a Shelly that is powered down.
                armedForDeadline = nextDeadline
                _state.value = _state.value.copy(armed = true)
                switch(cfg, on = true, toggleAfterSeconds = desired.toggleAfterSeconds)
            }

            is RelayPlan.Desired.Off -> {
                armedForDeadline = null
                _state.value = _state.value.copy(armed = false)
                switch(cfg, on = false, toggleAfterSeconds = null)
            }
        }
    }

    /**
     * The watchdog must cover whatever plays next. We do not know the kind here
     * without re-materialising the schedule, so use the doha ceiling: erring
     * long is correct, erring short would de-power the amp mid-chant.
     */
    private fun estimateForNext(): Long = RelayPlan.DOHA_CEILING_SECONDS

    // ------------------------------------------------------------ screen API

    /**
     * Probe the unauthenticated `Shelly.GetDeviceInfo`. Fire-and-forget; watch
     * [state] for the result.
     */
    fun testConnection() {
        scope.launch {
            val cfg = readConfig()
            if (cfg.host.isEmpty()) {
                _state.value = _state.value.copy(
                    configured = false,
                    reachable = false,
                    lastAction = Action.TEST,
                    lastActionOk = false,
                    lastError = "No relay host set",
                )
                return@launch
            }
            if (!inFlight.tryLock()) return@launch
            try {
                applyResult(Action.TEST, bounded { client.deviceInfo(cfg.host) }, describe = true)
            } finally {
                inFlight.unlock()
            }
        }
    }

    /** Staff override from the screen. Works even when the clock is untrusted. */
    fun manualOn(toggleAfterSeconds: Long = RelayPlan.toggleAfterSeconds(0, RelayPlan.DOHA_CEILING_SECONDS, 0)) {
        scope.launch {
            val cfg = readConfig()
            if (cfg.host.isEmpty()) return@launch
            // A manual ON is not a schedule arm; clear the sticky arm so the
            // next tick does not think a deadline is still pending.
            armedForDeadline = null
            _state.value = _state.value.copy(armed = true)
            switch(cfg, on = true, toggleAfterSeconds = toggleAfterSeconds)
        }
    }

    /** Staff override from the screen. */
    fun manualOff() {
        scope.launch {
            val cfg = readConfig()
            if (cfg.host.isEmpty()) return@launch
            armedForDeadline = null
            _state.value = _state.value.copy(armed = false)
            switch(cfg, on = false, toggleAfterSeconds = null)
        }
    }

    /** Re-read settings into [state] after the screen edits them. */
    fun refresh() {
        scope.launch {
            val cfg = readConfig()
            _state.value = _state.value.copy(
                enabled = cfg.enabled,
                configured = cfg.host.isNotEmpty(),
                host = cfg.host,
            )
        }
    }

    // ------------------------------------------------------------ internals

    private data class Config(
        val enabled: Boolean,
        val host: String,
        val user: String,
        val password: String,
        val switchId: Int,
        val leadSeconds: Long,
        val lagSeconds: Long,
    )

    private suspend fun readConfig(): Config = Config(
        enabled = repo.settingBool("relay_enabled"),
        host = repo.setting("relay_host").trim(),
        user = repo.setting("relay_auth_user").ifBlank { ShellyClient.DEFAULT_USER },
        // Never logged, never copied into State.
        password = repo.setting("relay_auth_pass"),
        switchId = repo.settingInt("relay_switch_id"),
        leadSeconds = repo.settingInt("relay_lead_seconds").toLong(),
        lagSeconds = repo.settingInt("relay_lag_seconds").toLong(),
    )

    private suspend fun switch(cfg: Config, on: Boolean, toggleAfterSeconds: Long?) {
        // Busy means a switch is already in flight: drop rather than queue.
        if (!inFlight.tryLock()) {
            Log.i(TAG, "relay busy, dropping ${if (on) "on" else "off"}")
            return
        }
        try {
            val result = bounded {
                client.setSwitch(
                    host = cfg.host,
                    switchId = cfg.switchId,
                    on = on,
                    toggleAfterSeconds = toggleAfterSeconds,
                    user = cfg.user,
                    password = cfg.password,
                )
            }
            applyResult(if (on) Action.ON else Action.OFF, result, describe = false)
        } finally {
            inFlight.unlock()
        }
    }

    /**
     * Every network call goes through here: a hard [ShellyClient.CALL_BUDGET_MS]
     * ceiling, and any failure becomes state rather than an exception. Genuine
     * scope cancellation is rethrown so service teardown is not swallowed.
     */
    private suspend fun bounded(block: suspend () -> ShellyClient.Result): ShellyClient.Result =
        try {
            withTimeout(ShellyClient.CALL_BUDGET_MS) { block() }
        } catch (e: TimeoutCancellationException) {
            ShellyClient.Result.Failed("timed out")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ShellyClient.Result.Failed(e.message ?: e.javaClass.simpleName)
        }

    private fun applyResult(action: String, result: ShellyClient.Result, describe: Boolean) {
        val now = ZonedDateTime.now()
        _state.value = when (result) {
            is ShellyClient.Result.Ok -> {
                Log.i(TAG, "relay $action ok")
                _state.value.copy(
                    reachable = true,
                    lastAction = action,
                    lastActionAt = now,
                    lastActionOk = true,
                    lastError = "",
                    deviceInfo = if (describe) summarise(result.body) else _state.value.deviceInfo,
                )
            }

            is ShellyClient.Result.AuthRequired -> {
                // The realm is the device id, not a secret. The password never
                // appears here or in logcat.
                Log.w(TAG, "relay $action needs authentication")
                _state.value.copy(
                    reachable = true,
                    lastAction = action,
                    lastActionAt = now,
                    lastActionOk = false,
                    lastError = "Authentication required",
                )
            }

            is ShellyClient.Result.Failed -> {
                Log.w(TAG, "relay $action failed: ${result.reason} (play unaffected)")
                _state.value.copy(
                    reachable = false,
                    lastAction = action,
                    lastActionAt = now,
                    lastActionOk = false,
                    lastError = result.reason,
                )
            }
        }
    }

    private fun summarise(body: String): String {
        val model = ShellyClient.field(body, "model") ?: ShellyClient.field(body, "app") ?: "Shelly"
        val mac = ShellyClient.field(body, "mac").orEmpty()
        return if (mac.isEmpty()) model else "$model · $mac"
    }

    private companion object {
        const val TAG = "RelayController"
    }
}
