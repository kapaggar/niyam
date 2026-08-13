package org.dhamma.gong.player

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.dhamma.gong.data.GongRepository
import org.dhamma.gong.domain.GongTracks
import org.dhamma.gong.domain.PlayCommand
import org.dhamma.gong.domain.PlayKind
import org.dhamma.gong.domain.PlayLogEntry
import org.dhamma.gong.domain.PlayResult

/**
 * The single player queue. Exactly one of these exists, in the service process.
 *
 * Preemption rules ported from the Pi daemon's player.py `Player.submit`:
 *   - a new gong aborts a still-running gong (bursts never stack);
 *   - a doha never preempts a gong — it waits its turn;
 *   - [stop] aborts what is playing and drops what is queued.
 *
 * The gap is silence *after* a strike finishes, matching the Pi daemon: a
 * recording longer than `gap_seconds` still gets its full gap, never an
 * overlapping or gapless burst (FABLE-REVIEW B3).
 */
class PlayerEngine(
    private val repo: GongRepository,
    private val resolver: MediaResolver,
    private val router: AudioRouter,
    private val sink: AudioSink,
    private val scope: CoroutineScope,
    /**
     * Called when a play finishes, so the amplifier relay can time its lag-out
     * from the real end of the burst.
     *
     * Fire-and-forget by contract: it must return immediately and must never
     * be something playback waits on.
     */
    private val onPlayEnded: () -> Unit = {},
) {

    data class Status(
        val playing: Boolean = false,
        val label: String = "",
        /** 1-based strike index while a burst runs; 0 otherwise. */
        val strike: Int = 0,
        val ofStrikes: Int = 0,
        val route: String = AudioRoute.Speaker.label,
        val lastResult: String = "",
        val lastFile: String = "",
    )

    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status.asStateFlow()

    /** One emission per strike, so the dashboard can draw a ring. */
    private val _strikes = MutableSharedFlow<Int>(
        replay = 0,
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val strikes: SharedFlow<Int> = _strikes.asSharedFlow()

    private val queue = ArrayDeque<PlayCommand>()
    private val lock = Mutex()
    private var current: PlayCommand? = null
    private var currentJob: Job? = null
    private var pump: Job? = null

    // ------------------------------------------------------------ API

    /**
     * Enqueue a command; returns as soon as it is queued.
     *
     * @return false when the queue is saturated — the Pi daemon drops rather than stacks.
     */
    suspend fun submit(command: PlayCommand): Boolean {
        lock.withLock {
            if (command.isGong) {
                // A new gong replaces any pending gong and kills a running one.
                queue.removeAll { it.isGong }
                if (current?.isGong == true) currentJob?.cancel(BurstPreempted())
            }
            if (queue.size >= MAX_QUEUE) {
                Log.e(TAG, "queue full, dropping ${command.label.ifBlank { command.kind }}")
                return false
            }
            queue.addLast(command)
        }
        ensurePump()
        return true
    }

    /** Abort what is playing and drop everything queued. */
    suspend fun stop() {
        val dropped: List<PlayCommand>
        lock.withLock {
            dropped = queue.toList()
            queue.clear()
            currentJob?.cancel(BurstStopped())
        }
        for (c in dropped) {
            repo.log(PlayLogEntry(c.kind, "-", c.repeats, PlayResult.STOPPED, c.label))
        }
    }

    /** Open the output device ahead of a fire (design doc §06: 15 s before). */
    fun warmUp() {
        scope.launch { runCatching { sink.warmUp() } }
    }

    val busy: Boolean get() = current != null || queue.isNotEmpty()

    /**
     * Service-teardown path: abort everything WITHOUT logging (the process is
     * dying; Room writes here risk blocking the main thread) and free the
     * sink. Safe from the main thread — the sink releases on Main.immediate.
     */
    suspend fun release() {
        lock.withLock {
            queue.clear()
            currentJob?.cancel(BurstStopped())
            pump?.cancel()
        }
        sink.release()
    }

    /** Test hook: suspends until the queue has fully drained. */
    suspend fun awaitIdle() {
        pump?.join()
    }

    // ------------------------------------------------------------ pump

    private suspend fun ensurePump() {
        lock.withLock {
            if (pump?.isActive == true) return
            pump = scope.launch { drain() }
        }
    }

    private suspend fun drain() {
        while (true) {
            val next = lock.withLock {
                val n = queue.removeFirstOrNull()
                if (n == null) {
                    // Release the pump slot under the SAME lock that saw the
                    // empty queue. Otherwise a submit() landing between that
                    // check and this coroutine's completion sees a pump that
                    // is still active but has already decided to exit, and
                    // its command sits queued until the next submit.
                    _status.value = _status.value.copy(
                        playing = false, strike = 0, ofStrikes = 0, label = "",
                    )
                    pump = null
                } else {
                    current = n
                }
                n
            } ?: return

            val job = scope.launch { execute(next) }
            lock.withLock { currentJob = job }
            job.join()
            lock.withLock {
                current = null
                currentJob = null
            }
        }
    }

    // ------------------------------------------------------------ execute

    private suspend fun execute(command: PlayCommand) {
        val resolved = resolver.resolve(command)
        if (resolved is MediaResolver.Resolved.Missing) {
            // Missing media is a logged error, never a crash (design doc §05).
            Log.e(TAG, "cannot play ${resolved.displayName}: ${resolved.reason}")
            repo.log(
                PlayLogEntry(
                    command.kind, resolved.displayName, 0,
                    PlayResult.ERROR, resolved.reason,
                ),
            )
            _status.value = _status.value.copy(
                lastResult = PlayResult.ERROR, lastFile = resolved.displayName,
            )
            return
        }
        val ok = resolved as MediaResolver.Resolved.Ok
        // A command may name its own route (Audio out auditioning a device);
        // everything the scheduler emits leaves it null and follows the setting.
        val route = router.resolve(command.routeKey ?: repo.setting(AudioRoute.SETTING_KEY))

        var played = 0
        var result = PlayResult.OK
        var detail = if (route.fellBack) {
            // A gong from the wrong speaker beats no gong (design doc §06).
            "route ${route.requested} unavailable, used ${route.route.key}"
        } else {
            ""
        }

        _status.value = Status(
            playing = true,
            label = command.label.ifBlank { command.kind },
            strike = 0,
            ofStrikes = command.repeats,
            route = route.route.label,
            lastFile = ok.displayName,
        )

        try {
            val gapMs = command.gapSeconds * 1000L
            // `repeats` counts audible hits, but one play of a multi-hit
            // recording delivers several (the Sikkim gong rings three times
            // per file). GongTracks does the division; strike/played numbers
            // stay in hits so "n of repeats" in the log keeps meaning it.
            val plays = GongTracks.playsFor(command.repeats, command.trackStem)
            for (i in 0 until plays) {
                if (i > 0 && gapMs > 0) delay(gapMs)
                _status.value = _status.value.copy(
                    strike = GongTracks.hitsAfterPlays(i, command.repeats, command.trackStem) + 1,
                )
                _strikes.tryEmit(i + 1)
                sink.play(ok.uri, command.volume, route.route.deviceId)
                played = GongTracks.hitsAfterPlays(i + 1, command.repeats, command.trackStem)
            }
        } catch (e: CancellationException) {
            result = PlayResult.STOPPED
            detail = when (e) {
                is BurstPreempted -> "preempted after $played of ${command.repeats}"
                else -> "stopped after $played of ${command.repeats}"
            }
            // The log must survive the cancellation that caused it.
            withContext(NonCancellable) { finish(command, ok, route, played, result, detail) }
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "play failed: ${command.label}", e)
            result = PlayResult.ERROR
            detail = e.message.orEmpty()
            finish(command, ok, route, played, result, detail)
            return
        }
        finish(command, ok, route, played, result, detail)
    }

    private suspend fun finish(
        command: PlayCommand,
        media: MediaResolver.Resolved.Ok,
        route: AudioRouter.Resolution,
        played: Int,
        result: String,
        detail: String,
    ) {
        repo.log(PlayLogEntry(command.kind, media.displayName, played, result, detail))
        if (result == PlayResult.OK) {
            repo.statePut(AudioRoute.LAST_OK_KEY, route.route.key)
            repo.statePut(AudioRoute.LAST_OK_AT_KEY, java.time.Instant.now().toString())
        }
        _status.value = _status.value.copy(
            playing = false, strike = 0, ofStrikes = 0, label = "",
            lastResult = result, lastFile = media.displayName,
        )
        // Relay lag-out starts here, not at the scheduled time. Never allowed
        // to throw into the play path.
        runCatching { onPlayEnded() }
            .onFailure { Log.w(TAG, "play-end hook failed", it) }
    }

    private class BurstPreempted : CancellationException("preempted")
    private class BurstStopped : CancellationException("stopped")

    companion object {
        private const val TAG = "PlayerEngine"
        const val MAX_QUEUE = 8
    }
}

private val PlayCommand.isGong: Boolean
    get() = kind == PlayKind.GONG || kind == PlayKind.TEST_GONG
