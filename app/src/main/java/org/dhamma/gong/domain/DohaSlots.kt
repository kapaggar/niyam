package org.dhamma.gong.domain

import java.time.LocalDate

/**
 * Port of the Pi daemon's doha.py — byte-for-byte legacy_modular algorithm
 * (verified against app/dhamma/doha.php).
 */
object DohaSlots {

    val SLOTS: IntRange = 1..11

    private val SLOT_COUNT = SLOTS.last - SLOTS.first + 1

    /**
     * Slot 1..11 for an in-course day.
     * @param day current course day (1..total typically for modular; callers may pass 0+)
     * @param total total_days (last day index, e.g. 11 for 10 Day)
     * @param anapana anapana_days
     */
    fun legacyModular(day: Int, total: Int, anapana: Int): Int {
        var slot: Int
        if (day <= anapana) {
            slot = ((day - 1) % 3) + 1 // anapana: 1,2,3 cycle
        } else if (day == anapana + 1) {
            slot = 4 // first vipassana day
        } else {
            slot = 3 + ((day - (anapana + 1)) % 6) + 1 // 4..9 cycle
        }
        val mettaDays = if (total >= 30) 2 else 1
        if (day == total) {
            slot = 11 // homage, last day
        } else if (day >= total - mettaDays) {
            slot = 10 // metta day(s)
        }
        return slot
    }

    /**
     * The between-courses slot for [date] when `no_course_doha = random`.
     *
     * Deterministic on purpose. A real random number generator here means the
     * scheduler can materialize the same calendar day twice — after a restart,
     * a re-seed, a settings poke — and pick a *different* doha each time, so
     * the fired-guard and the log disagree about what "today's doha" was. Same
     * date must always mean the same slot.
     *
     * The stride is 9 (31 mod 11) and 9 is coprime with 11, so consecutive days
     * walk every slot exactly once before repeating. Over any eleven-day
     * stretch between courses a centre hears all eleven dohas and no duplicate
     * — better than true random, which would happily play the same one twice
     * running.
     */
    fun randomSlotFor(date: LocalDate): Int =
        Math.floorMod(date.toEpochDay() * 31 + 7, SLOT_COUNT.toLong()).toInt() + SLOTS.first

    /**
     * @param date the occurrence's local date, used only when
     *   `no_course_doha = random`. In-course days ignore it — they are decided
     *   by [legacyModular], which is the verified PHP port and must not change.
     * @return slot 1..11 or null for no doha today.
     */
    fun pickSlot(
        ctx: CourseCtx?,
        noCourseDoha: String,
        date: LocalDate,
    ): Int? {
        if (ctx != null && ctx.day > 0 && ctx.day <= ctx.totalDays) {
            return legacyModular(ctx.day, ctx.totalDays, ctx.anapanaDays)
        }
        return when {
            noCourseDoha == "off" -> null
            noCourseDoha.startsWith("slot:") -> {
                val n = noCourseDoha.removePrefix("slot:").toIntOrNull()
                if (n != null && n in SLOTS) n else null
            }
            else -> randomSlotFor(date)
        }
    }
}
