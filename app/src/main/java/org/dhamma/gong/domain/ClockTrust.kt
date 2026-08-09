package org.dhamma.gong.domain

import java.time.Duration
import java.time.ZonedDateTime

/**
 * Clock-sanity model — port of the Pi daemon's clock.py (design doc §05).
 *
 * "Silence beats a wrong gong": if the wall clock appears to have gone
 * backwards since we last saw it, automatic plays are suppressed and logged
 * `skipped_clock`. Manual tests still work.
 *
 * Pure: it reads/writes through [KeyValueState] so the same logic runs against
 * Room on device and an in-memory map in tests.
 */
object ClockTrust {

    const val LAST_GOOD_KEY = "last_good_time"
    const val CLOCK_INVALID_KEY = "clock_invalid"

    /** How far backwards the clock may drift before we stop trusting it. */
    val BACKWARDS_TOLERANCE: Duration = Duration.ofMinutes(10)

    /** How often [touchLastGood] advances the watermark. */
    val TOUCH_INTERVAL: Duration = Duration.ofMinutes(5)

    /**
     * Call on service start / boot. Marks the clock untrusted if it went
     * backwards past [BACKWARDS_TOLERANCE].
     *
     * @return true if the clock is trusted afterwards.
     */
    fun checkOnStart(state: KeyValueState, now: ZonedDateTime): Boolean {
        val lastGood = state.get(LAST_GOOD_KEY)?.let(::parse)
        if (lastGood != null && now.toInstant() < lastGood.toInstant().minus(BACKWARDS_TOLERANCE)) {
            state.put(CLOCK_INVALID_KEY, "1")
        }
        return !isInvalid(state)
    }

    fun isInvalid(state: KeyValueState): Boolean = state.get(CLOCK_INVALID_KEY) == "1"

    fun isTrusted(state: KeyValueState): Boolean = !isInvalid(state)

    /** Staff confirmed or set the time; trust it from here (design doc §05). */
    fun confirm(state: KeyValueState, now: ZonedDateTime) {
        state.remove(CLOCK_INVALID_KEY)
        state.put(LAST_GOOD_KEY, now.toInstant().toString())
    }

    /**
     * Advance the known-good watermark. Also auto-clears untrusted mode once
     * the clock has caught up past the last known-good instant (an NTP step
     * forward), matching the Pi daemon's touch_last_good.
     */
    fun touchLastGood(state: KeyValueState, now: ZonedDateTime) {
        val lastGood = state.get(LAST_GOOD_KEY)?.let(::parse)
        if (isInvalid(state)) {
            if (lastGood == null || now.toInstant() >= lastGood.toInstant()) {
                state.remove(CLOCK_INVALID_KEY)
            } else {
                return // do not advance the watermark while untrusted
            }
        }
        state.put(LAST_GOOD_KEY, now.toInstant().toString())
    }

    /** True when [now] has jumped more than [TOUCH_INTERVAL] since [lastTouch]. */
    fun shouldTouch(lastTouch: ZonedDateTime?, now: ZonedDateTime): Boolean =
        lastTouch == null ||
            Duration.between(lastTouch.toInstant(), now.toInstant()) >= TOUCH_INTERVAL

    private fun parse(s: String): ZonedDateTime? = runCatching {
        ZonedDateTime.parse(s)
    }.recoverCatching {
        java.time.Instant.parse(s).atZone(java.time.ZoneOffset.UTC)
    }.getOrNull()
}

/** The `state` table, as the domain sees it. */
interface KeyValueState {
    fun get(key: String): String?
    fun put(key: String, value: String)
    fun remove(key: String)
}

/** In-memory implementation for tests and for a scheduler tick's scratch space. */
class MapState(private val backing: MutableMap<String, String> = mutableMapOf()) : KeyValueState {
    override fun get(key: String): String? = backing[key]
    override fun put(key: String, value: String) {
        backing[key] = value
    }

    override fun remove(key: String) {
        backing.remove(key)
    }

    fun snapshot(): Map<String, String> = backing.toMap()
}
