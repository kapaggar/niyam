package org.dhamma.gong.assets

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class StorageLocatorTest {

    private lateinit var tmp: File

    @Before
    fun setUp() {
        tmp = Files.createTempDirectory("storage-locator-test").toFile()
    }

    @After
    fun tearDown() {
        // Restore any permissions the tests dropped so cleanup succeeds.
        tmp.walkBottomUp().forEach {
            it.setReadable(true)
            it.setExecutable(true)
        }
        tmp.deleteRecursively()
    }

    private fun dir(path: String): File = File(tmp, path).apply { mkdirs() }

    private fun file(path: String): File {
        val f = File(tmp, path)
        f.parentFile?.mkdirs()
        f.writeBytes("ID3-stub".toByteArray())
        return f
    }

    @Test
    fun findsAtRootAndOneLevel_neverTwoLevels() {
        val root = dir("root")
        file("root/a.mp3")
        file("root/sub/b.mp3")
        file("root/x/y/c.mp3") // depth 3 from root — out of bounds

        val found = StorageLocator(listOf(root)).scan(setOf("a.mp3", "b.mp3", "c.mp3"))

        assertEquals(File(root, "a.mp3"), found["a.mp3"])
        assertEquals(File(root, "sub/b.mp3"), found["b.mp3"])
        assertNull(found["c.mp3"])
        assertEquals(2, found.size)
    }

    @Test
    fun commonGeneralAtDepthOne_listsOnlyThatDirFlat() {
        val root = dir("root")
        file("root/common-general/f.mp3")
        file("root/other/g.mp3")
        file("root/h.mp3")

        val found = StorageLocator(listOf(root)).scan(setOf("f.mp3", "g.mp3", "h.mp3"))

        assertEquals(File(root, "common-general/f.mp3"), found["f.mp3"])
        assertEquals(1, found.size)
    }

    @Test
    fun commonGeneralAtDepthTwo_isAlsoUsed() {
        val root = dir("root")
        file("root/media/common-general/f.mp3")
        file("root/g.mp3")

        val found = StorageLocator(listOf(root)).scan(setOf("f.mp3", "g.mp3"))

        assertEquals(File(root, "media/common-general/f.mp3"), found["f.mp3"])
        assertEquals(1, found.size)
    }

    @Test
    fun firstHitWinsInRootsOrder() {
        val root1 = dir("root1")
        val root2 = dir("root2")
        file("root1/dup.mp3")
        file("root2/dup.mp3")

        val found = StorageLocator(listOf(root1, root2)).scan(setOf("dup.mp3"))

        assertEquals(File(root1, "dup.mp3"), found["dup.mp3"])
    }

    @Test
    fun missingRoot_isSkippedSilently() {
        val good = dir("good")
        file("good/a.mp3")

        val found = StorageLocator(listOf(File(tmp, "does-not-exist"), good))
            .scan(setOf("a.mp3"))

        assertEquals(File(good, "a.mp3"), found["a.mp3"])
    }

    @Test
    fun unreadableRoot_isSkippedSilently() {
        val restricted = dir("restricted")
        file("restricted/a.mp3")
        val good = dir("good")
        file("good/a.mp3")

        if (!restricted.setReadable(false) || restricted.listFiles() != null) {
            // Filesystem (or a root user) ignores the permission drop; the
            // "unreadable directory" case cannot be simulated here.
            restricted.setReadable(true)
            return
        }
        try {
            val found = StorageLocator(listOf(restricted, good)).scan(setOf("a.mp3"))
            assertEquals(File(good, "a.mp3"), found["a.mp3"])
        } finally {
            restricted.setReadable(true)
        }
    }

    @Test
    fun symlinksEscapingTheRoot_areIgnored() {
        val root = dir("root")
        val outside = dir("outside")
        file("outside/evil.mp3")
        file("outside/evil2.mp3")
        try {
            // A directory symlink whose contents canonicalize outside root,
            // and a direct file symlink pointing out.
            Files.createSymbolicLink(
                File(root, "linkdir").toPath(),
                outside.toPath(),
            )
            Files.createSymbolicLink(
                File(root, "evil2.mp3").toPath(),
                File(outside, "evil2.mp3").toPath(),
            )
        } catch (e: Exception) {
            return // Filesystem refuses symlinks; nothing to verify here.
        }

        val found = StorageLocator(listOf(root)).scan(setOf("evil.mp3", "evil2.mp3"))

        assertTrue(found.isEmpty())
    }

    @Test
    fun emptyFilenames_returnsEmptyWithoutTouchingRoots() {
        val found = StorageLocator(listOf(dir("root"))).scan(emptySet())
        assertTrue(found.isEmpty())
    }

    @Test
    fun subdirectoriesMatchingAFilename_areNotReported() {
        val root = dir("root")
        dir("root/a.mp3") // a directory named like the file
        val found = StorageLocator(listOf(root)).scan(setOf("a.mp3"))
        assertFalse(found.containsKey("a.mp3"))
    }
}
