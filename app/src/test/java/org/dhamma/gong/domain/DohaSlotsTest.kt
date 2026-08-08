package org.dhamma.gong.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/** Golden tests matching ng/tests/test_doha.py */
class DohaSlotsTest {

    @Test
    fun tenDay_slots() {
        val expected = mapOf(
            1 to 1, 2 to 2, 3 to 3, 4 to 4, 5 to 5, 6 to 6,
            7 to 7, 8 to 8, 9 to 9, 10 to 10, 11 to 11,
        )
        for ((day, slot) in expected) {
            assertEquals("day $day", slot, DohaSlots.legacyModular(day, 11, 3))
        }
    }

    @Test
    fun stp_slots() {
        val expected = mapOf(1 to 1, 2 to 2, 3 to 4, 4 to 5, 5 to 6, 6 to 7, 7 to 10, 8 to 11)
        for ((day, slot) in expected) {
            assertEquals("day $day", slot, DohaSlots.legacyModular(day, 8, 2))
        }
    }

    @Test
    fun thirtyDay_doubleMetta() {
        assertEquals(10, DohaSlots.legacyModular(29, 31, 10))
        assertEquals(10, DohaSlots.legacyModular(30, 31, 10))
        assertEquals(11, DohaSlots.legacyModular(31, 31, 10))
    }

    @Test
    fun vipassanaCycleWraps() {
        val slots = (11..17).map { DohaSlots.legacyModular(it, 46, 10) }
        assertEquals(listOf(4, 5, 6, 7, 8, 9, 4), slots)
    }
}
