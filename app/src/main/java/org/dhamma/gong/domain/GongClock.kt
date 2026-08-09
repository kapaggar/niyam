package org.dhamma.gong.domain

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Wall-clock access, injectable so the engine can be driven by a virtual clock
 * in tests (design doc §12 — a 400-day run must execute in seconds).
 *
 * Port of the Pi daemon's clock.py Clock.
 */
interface GongClock {
    val zone: ZoneId
    fun now(): ZonedDateTime
    fun today(): LocalDate = now().toLocalDate()

    /**
     * Local instant for wall time [hhmm] on [day].
     *
     * Spring-forward gap → first valid instant after the gap.
     * Fall-back ambiguity → the *first* occurrence (earlier offset).
     *
     * `ZonedDateTime.of` implements exactly these two rules, which is why
     * the Pi daemon's astimezone round-trip is not needed here.
     */
    fun materialize(day: LocalDate, hhmm: LocalTime): ZonedDateTime =
        ZonedDateTime.of(day, hhmm, zone)
}

/**
 * Real device clock. The zone is read through [zoneProvider] on every use so a
 * settings change (Time screen, timezone poke) takes effect without rebuilding
 * the scheduler.
 */
class SystemGongClock(private val zoneProvider: () -> ZoneId) : GongClock {
    constructor(zone: ZoneId) : this({ zone })

    override val zone: ZoneId get() = zoneProvider()
    override fun now(): ZonedDateTime = ZonedDateTime.now(zone)
}

/** Test / compressed-time clock. Not thread-safe by design; drive it from one thread. */
class VirtualClock(
    override val zone: ZoneId,
    private var instant: Instant,
) : GongClock {
    override fun now(): ZonedDateTime = ZonedDateTime.ofInstant(instant, zone)

    fun advanceSeconds(seconds: Long) {
        instant = instant.plusSeconds(seconds)
    }

    fun set(at: ZonedDateTime) {
        instant = at.toInstant()
    }
}

/** Calendar-date difference. Never `/86400` (design doc §04). */
fun currentDay(zeroDay: LocalDate, today: LocalDate): Int =
    java.time.temporal.ChronoUnit.DAYS.between(zeroDay, today).toInt()
