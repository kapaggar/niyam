package org.dhamma.gong.domain

import org.dhamma.gong.domain.DohaPackMapper.DirNode
import org.dhamma.gong.domain.DohaPackMapper.ScannedFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pack mapper is where a wrong answer becomes the wrong recording at 04:30,
 * so every "never guess" rule in the design doc gets a test here.
 */
class DohaPackMapperTest {

    private fun f(name: String) = ScannedFile(name, "content://tree/$name")

    // ---------------------------------------------------------------- prefix

    @Test
    fun `parses D01 through D11 with and without separators`() {
        assertEquals(1, DohaPackMapper.parseSlot("D01 morning.mp3"))
        assertEquals(1, DohaPackMapper.parseSlot("d01-morning.mp3"))
        assertEquals(1, DohaPackMapper.parseSlot("D01.mp3"))
        assertEquals(1, DohaPackMapper.parseSlot("D01_0632_Doha-Hindi-1_NA_NA.mp3"))
        assertEquals(7, DohaPackMapper.parseSlot("d07x.mp3"))
        assertEquals(11, DohaPackMapper.parseSlot("D11_0632_Doha-Homage_NA_NA.mp3"))
    }

    @Test
    fun `slot 0 and slot 12 are rejected, not clamped`() {
        assertNull(DohaPackMapper.parseSlot("D00 intro.mp3"))
        assertNull(DohaPackMapper.parseSlot("D12 extra.mp3"))
        assertNull(DohaPackMapper.parseSlot("D99.mp3"))
    }

    @Test
    fun `a three digit run does not parse as a slot`() {
        assertNull(DohaPackMapper.parseSlot("D011.mp3"))
    }

    @Test
    fun `slot 0 and slot 12 files land in unassigned`() {
        val m = DohaPackMapper.classify(listOf(f("D00 a.mp3"), f("D12 b.mp3")))
        assertTrue(m.assigned.isEmpty())
        assertEquals(listOf("D00 a.mp3", "D12 b.mp3"), m.unassigned.map { it.name })
    }

    // ------------------------------------------------------------ classification

    @Test
    fun `a clean pack of eleven files maps every slot`() {
        val files = (1..11).map { f("D%02d track.mp3".format(it)) }
        val m = DohaPackMapper.classify(files)
        assertEquals((1..11).toList(), m.assigned.keys.sorted())
        assertTrue(m.conflicts.isEmpty())
        assertTrue(m.unassigned.isEmpty())
        assertTrue(m.skipped.isEmpty())
    }

    @Test
    fun `two files claiming one slot conflict with no silent winner`() {
        val m = DohaPackMapper.classify(
            listOf(f("D03 alpha.mp3"), f("D03-beta.mp3"), f("D04 ok.mp3")),
        )
        assertEquals(setOf(4), m.assigned.keys)
        assertEquals(1, m.conflicts.size)
        assertEquals(3, m.conflicts.first().slot)
        assertEquals(
            listOf("D03 alpha.mp3", "D03-beta.mp3"),
            m.conflicts.first().files.map { it.name },
        )
    }

    @Test
    fun `non-mp3 and prefix-less files are unassigned`() {
        val m = DohaPackMapper.classify(
            listOf(f("D02 chant.wav"), f("readme.txt"), f("morning.mp3"), f("D05 ok.MP3")),
        )
        assertEquals(setOf(5), m.assigned.keys)
        assertEquals(
            listOf("D02 chant.wav", "morning.mp3", "readme.txt"),
            m.unassigned.map { it.name }.sorted(),
        )
    }

    // ------------------------------------------------------------ precedence

    @Test
    fun `rescan replaces auto rows and preserves manual rows`() {
        val held = mapOf(2 to "auto", 3 to "manual")
        val m = DohaPackMapper.classify(
            listOf(f("D02 new.mp3"), f("D03 new.mp3")),
            held,
        )
        // slot 2 was auto — auto-map may take it back.
        assertEquals("D02 new.mp3", m.assigned[2]?.name)
        // slot 3 is the staff's explicit correction — untouched.
        assertNull(m.assigned[3])
        assertEquals(listOf(3), m.skipped.map { it.slot })
        assertEquals("manual", m.skipped.first().heldBy)
    }

    @Test
    fun `a bundled slot survives a rescan and is reported skipped`() {
        val m = DohaPackMapper.classify(
            listOf(f("D01 side.mp3"), f("D06 side.mp3")),
            mapOf(1 to "bundled"),
        )
        assertEquals(setOf(6), m.assigned.keys)
        assertEquals(1, m.skipped.size)
        assertEquals(1, m.skipped.first().slot)
        assertEquals("bundled", m.skipped.first().heldBy)
        assertEquals("D01 side.mp3", m.skipped.first().file.name)
    }

    @Test
    fun `rescan replaces downloaded rows - a folder pack outranks the pipeline`() {
        val held = mapOf(2 to "downloaded", 3 to "manual", 4 to "bundled")
        val m = DohaPackMapper.classify(
            listOf(f("D02 new.mp3"), f("D03 new.mp3"), f("D04 new.mp3")),
            held,
        )
        // slot 2 was filled by the download pipeline — a staff-chosen pack takes it.
        assertEquals("D02 new.mp3", m.assigned[2]?.name)
        assertEquals(setOf(2), m.assigned.keys)
        // manual and bundled stay protected exactly as before.
        assertEquals(listOf(3, 4), m.skipped.map { it.slot }.sorted())
    }

    @Test
    fun `an empty slot is writable`() {
        val m = DohaPackMapper.classify(listOf(f("D09 x.mp3")), mapOf(1 to "manual"))
        assertEquals(setOf(9), m.assigned.keys)
        assertTrue(m.skipped.isEmpty())
    }

    // ------------------------------------------------------------ scan depth

    @Test
    fun `top level audio wins and no child is consulted`() {
        val root = DirNode(
            name = "pack",
            files = listOf(f("D01 a.mp3"), f("notes.txt")),
            dirs = listOf(DirNode("doha", files = listOf(f("D02 b.mp3")))),
        )
        val target = DohaPackMapper.resolveScanTarget(root)
        assertEquals(listOf("D01 a.mp3"), target.files.map { it.name })
        assertTrue(!target.viaDohaChild)
    }

    @Test
    fun `a single doha child is descended into, case-insensitively`() {
        val root = DirNode(
            name = "pack",
            files = listOf(f("manifest.json")),
            dirs = listOf(DirNode("Doha", files = listOf(f("D01 a.mp3"), f("D02 b.mp3")))),
        )
        val target = DohaPackMapper.resolveScanTarget(root)
        assertEquals(listOf("D01 a.mp3", "D02 b.mp3"), target.files.map { it.name })
        assertTrue(target.viaDohaChild)
    }

    @Test
    fun `a two-level-deep tree resolves to nothing`() {
        val root = DirNode(
            name = "media",
            dirs = listOf(
                DirNode(
                    name = "doha",
                    dirs = listOf(DirNode("2024", files = listOf(f("D01 a.mp3")))),
                ),
            ),
        )
        assertTrue(DohaPackMapper.resolveScanTarget(root).files.isEmpty())
    }

    @Test
    fun `two doha children are ambiguous and resolve to nothing`() {
        val root = DirNode(
            name = "pack",
            dirs = listOf(
                DirNode("doha", files = listOf(f("D01 a.mp3"))),
                DirNode("DOHA", files = listOf(f("D01 b.mp3"))),
            ),
        )
        assertTrue(DohaPackMapper.resolveScanTarget(root).files.isEmpty())
    }

    @Test
    fun `a wrong parent with no doha child resolves to nothing`() {
        val root = DirNode(
            name = "Download",
            files = listOf(f("holiday.jpg")),
            dirs = listOf(DirNode("gongs", files = listOf(f("ting.mp3")))),
        )
        val target = DohaPackMapper.resolveScanTarget(root)
        assertTrue(target.files.isEmpty())
        assertTrue(DohaPackMapper.classify(target.files).isEmpty)
    }
}
