package org.dhamma.gong.domain

import kotlin.random.Random

/**
 * Port of the Pi daemon's doha.py — byte-for-byte legacy_modular algorithm
 * (verified against app/dhamma/doha.php).
 */
object DohaSlots {

    val SLOTS: IntRange = 1..11

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
     * @return slot 1..11 or null for no doha today.
     */
    fun pickSlot(
        ctx: CourseCtx?,
        noCourseDoha: String,
        random: Random = Random.Default,
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
            else -> random.nextInt(SLOTS.first, SLOTS.last + 1) // random
        }
    }
}
