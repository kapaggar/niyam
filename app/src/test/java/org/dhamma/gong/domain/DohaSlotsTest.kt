package org.dhamma.gong.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

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

    // ------------------------------------------------ between courses (G3)

    private val noCourse: CourseCtx? = null

    @Test
    fun theSameDateAlwaysPicksTheSameSlot() {
        // The whole point: a re-materialize after a restart, a re-seed or a
        // settings poke must not change what "today's doha" is, or the
        // fired-guard and the play_log end up describing different tracks.
        val date = LocalDate.of(2026, 8, 11)
        val first = DohaSlots.pickSlot(noCourse, "random", date)
        repeat(50) {
            assertEquals(first, DohaSlots.pickSlot(noCourse, "random", date))
        }
    }

    @Test
    fun everySlotIsAlwaysInRange() {
        var d = LocalDate.of(2026, 1, 1)
        repeat(400) {
            val slot = DohaSlots.randomSlotFor(d)
            assertTrue("slot $slot out of range on $d", slot in DohaSlots.SLOTS)
            d = d.plusDays(1)
        }
    }

    @Test
    fun elevenConsecutiveDaysPlayAllElevenDohasWithNoRepeat() {
        // The stride is coprime with 11, so a centre between courses hears the
        // whole set before anything comes round again. True random would
        // cheerfully repeat two days running.
        val start = LocalDate.of(2026, 8, 11)
        val slots = (0 until 11).map { DohaSlots.randomSlotFor(start.plusDays(it.toLong())) }
        assertEquals("no repeats within one cycle", 11, slots.toSet().size)
        assertEquals(DohaSlots.SLOTS.toSet(), slots.toSet())
    }

    @Test
    fun consecutiveDaysDiffer() {
        var d = LocalDate.of(2026, 3, 1)
        repeat(60) {
            assertNotEquals(
                "two days running picked the same doha at $d",
                DohaSlots.randomSlotFor(d),
                DohaSlots.randomSlotFor(d.plusDays(1)),
            )
            d = d.plusDays(1)
        }
    }

    @Test
    fun datesBeforeTheEpochStillPickAValidSlot() {
        // floorMod, not %, so a negative epoch day cannot produce slot 0 or -3.
        for (d in listOf(LocalDate.of(1969, 1, 1), LocalDate.of(1900, 6, 30))) {
            assertTrue(DohaSlots.randomSlotFor(d) in DohaSlots.SLOTS)
        }
    }

    @Test
    fun offMeansNoDohaAndFixedSlotIsHonoured() {
        val date = LocalDate.of(2026, 8, 11)
        assertNull(DohaSlots.pickSlot(noCourse, "off", date))
        assertEquals(7, DohaSlots.pickSlot(noCourse, "slot:7", date))
        // Out-of-range or unparseable fixed slots are refused, not clamped —
        // silently playing slot 1 when staff asked for 12 hides the mistake.
        assertNull(DohaSlots.pickSlot(noCourse, "slot:12", date))
        assertNull(DohaSlots.pickSlot(noCourse, "slot:zero", date))
    }

    @Test
    fun inCourseDaysIgnoreTheDateEntirely() {
        // legacyModular is the verified port and owns in-course days; the
        // between-courses date must never leak into it.
        val ctx = CourseCtx(
            courseId = 1, typeId = 1, typeName = "10 Day",
            startDate = LocalDate.of(2026, 8, 1), totalDays = 11, anapanaDays = 3, day = 5,
        )
        val a = DohaSlots.pickSlot(ctx, "random", LocalDate.of(2026, 8, 6))
        val b = DohaSlots.pickSlot(ctx, "random", LocalDate.of(2027, 2, 17))
        assertEquals(DohaSlots.legacyModular(5, 11, 3), a)
        assertEquals(a, b)
    }
}
