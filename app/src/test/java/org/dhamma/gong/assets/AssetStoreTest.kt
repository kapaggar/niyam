package org.dhamma.gong.assets

import org.dhamma.gong.domain.AudioAsset
import org.dhamma.gong.domain.Magic
import org.dhamma.gong.domain.Observed
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest

class AssetStoreTest {

    private lateinit var root: File
    private lateinit var store: AssetStore

    @Before
    fun setUp() {
        root = Files.createTempDirectory("asset-store-test").toFile()
        store = AssetStore(root)
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun asset(
        filename: String = "D01-morning-chanting.mp3",
        relativePath: String = "common-general/$filename",
    ) = AudioAsset(
        id = filename.removeSuffix(".mp3"),
        filename = filename,
        relativePath = relativePath,
        cdnUrl = "https://cdn.invalid/updates/v2/$relativePath",
        encryptedSha256 = "aa",
        encryptedBytes = 32L,
        decryptedSha256 = "bb",
        decryptedBytes = 16L,
    )

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun file(path: String, bytes: ByteArray = ByteArray(0)): File {
        val f = File(root, path)
        f.parentFile?.mkdirs()
        f.writeBytes(bytes)
        return f
    }

    // ---- path mapping ----

    @Test
    fun readyFile_derivesSubdirFromRelativePath() {
        val a = asset(relativePath = "common-general/D01-morning-chanting.mp3")
        assertEquals(
            File(root, "ready/common-general/D01-morning-chanting.mp3"),
            store.readyFile(a),
        )
    }

    @Test
    fun readyFile_flatRelativePath_hasNoSubdir() {
        val a = asset(relativePath = "D01-morning-chanting.mp3")
        assertEquals(File(root, "ready/D01-morning-chanting.mp3"), store.readyFile(a))
    }

    @Test
    fun encryptedFile_usesFullRelativePath() {
        val a = asset()
        assertEquals(
            File(root, "encrypted/common-general/D01-morning-chanting.mp3"),
            store.encryptedFile(a),
        )
    }

    @Test
    fun partialPaths_liveInTmp() {
        val a = asset()
        assertEquals(File(root, "tmp/D01-morning-chanting.mp3.partial"), store.partialFile(a))
        assertEquals(
            File(root, "tmp/D01-morning-chanting.mp3.dec.partial"),
            store.decPartialFile(a),
        )
    }

    // ---- observe ----

    @Test
    fun observe_missingFile_isAbsent() {
        assertEquals(Observed.Absent, store.observe(File(root, "nope.mp3")))
    }

    @Test
    fun observe_directory_isAbsent() {
        val dir = File(root, "adir").apply { mkdirs() }
        assertEquals(Observed.Absent, store.observe(dir))
    }

    @Test
    fun observe_id3() {
        val bytes = "ID3".toByteArray() + ByteArray(40) { it.toByte() }
        val o = store.observe(file("a.mp3", bytes)) as Observed.Present
        assertEquals(bytes.size.toLong(), o.size)
        assertEquals(Magic.ID3, o.magic)
        assertEquals(sha256Hex(bytes), o.sha256)
    }

    @Test
    fun observe_mpegSync() {
        val bytes = byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0x90.toByte(), 0x00) + ByteArray(20)
        val o = store.observe(file("b.mp3", bytes)) as Observed.Present
        assertEquals(Magic.MPEG, o.magic)
        assertEquals(sha256Hex(bytes), o.sha256)
    }

    @Test
    fun observe_salted() {
        val bytes = "Salted__".toByteArray() + ByteArray(8) { 0x5A }
        val o = store.observe(file("c.mp3", bytes)) as Observed.Present
        assertEquals(Magic.SALTED, o.magic)
    }

    @Test
    fun observe_html() {
        val bytes = "<html><body>error</body></html>".toByteArray()
        val o = store.observe(file("d.mp3", bytes)) as Observed.Present
        assertEquals(Magic.HTML, o.magic)
    }

    @Test
    fun observe_empty() {
        val o = store.observe(file("e.mp3")) as Observed.Present
        assertEquals(0L, o.size)
        assertEquals(Magic.EMPTY, o.magic)
        // SHA-256 of zero bytes.
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            o.sha256,
        )
    }

    @Test
    fun observe_other() {
        val bytes = byteArrayOf(0x00, 0x01, 0x02, 0x03)
        val o = store.observe(file("f.mp3", bytes)) as Observed.Present
        assertEquals(Magic.OTHER, o.magic)
    }

    // ---- observeCached / index ----

    @Test
    fun observeCached_hitSkipsRehash_untilInvalidated() {
        val original = "ID3".toByteArray() + ByteArray(64) { 1 }
        val f = file("cache.mp3", original)
        val mtime = f.lastModified()

        val first = store.observeCached(f) as Observed.Present
        assertEquals(sha256Hex(original), first.sha256)

        // Corrupt the content but keep size + mtime: a real re-hash would
        // notice, the (path, size, mtime) cache must not.
        val corrupted = "ID3".toByteArray() + ByteArray(64) { 2 }
        f.writeBytes(corrupted)
        assertTrue(f.setLastModified(mtime))

        val cached = store.observeCached(f) as Observed.Present
        assertEquals(first.sha256, cached.sha256)

        store.invalidate(f)
        val fresh = store.observeCached(f) as Observed.Present
        assertNotEquals(first.sha256, fresh.sha256)
        assertEquals(sha256Hex(corrupted), fresh.sha256)
    }

    @Test
    fun observeCached_indexPersistsAcrossInstances() {
        val original = ByteArray(32) { 7 }
        val f = file("persist.mp3", original)
        val mtime = f.lastModified()
        val first = store.observeCached(f) as Observed.Present

        f.writeBytes(ByteArray(32) { 8 })
        assertTrue(f.setLastModified(mtime))

        // A brand-new store reads the persisted index and still trusts it.
        val second = AssetStore(root).observeCached(f) as Observed.Present
        assertEquals(first.sha256, second.sha256)
    }

    @Test
    fun observeCached_missingFile_isAbsent() {
        assertEquals(Observed.Absent, store.observeCached(File(root, "gone.mp3")))
    }

    @Test
    fun corruptIndex_isTreatedAsEmpty() {
        file(".state/index.json", "{ this is not json".toByteArray())
        val bytes = ByteArray(16) { 3 }
        val f = file("g.mp3", bytes)
        val o = AssetStore(root).observeCached(f) as Observed.Present
        assertEquals(sha256Hex(bytes), o.sha256)
    }

    // ---- moves ----

    @Test
    fun atomicMove_createsParentsAndReplaces() {
        val target = File(root, "ready/common-general/x.mp3")

        val from1 = file("tmp/x.mp3.partial", "hello".toByteArray())
        store.atomicMove(from1, target)
        assertFalse(from1.exists())
        assertEquals("hello", target.readText())

        val from2 = file("tmp/x.mp3.partial", "world".toByteArray())
        store.atomicMove(from2, target)
        assertFalse(from2.exists())
        assertEquals("world", target.readText())
    }

    @Test
    fun quarantine_movesToTimestampedName() {
        val bad = file("tmp/bad.mp3", "junk".toByteArray())
        store.quarantine(bad)
        assertFalse(bad.exists())

        val quarantined = File(root, "tmp/quarantine").listFiles()!!.toList()
        assertEquals(1, quarantined.size)
        val name = quarantined[0].name
        assertTrue(name.startsWith("bad.mp3."))
        // Suffix is an epoch-millis timestamp.
        assertTrue(name.removePrefix("bad.mp3.").toLong() > 0)
        assertEquals("junk", quarantined[0].readText())
    }

    // ---- housekeeping ----

    @Test
    fun freeBytes_isPositive() {
        assertTrue(store.freeBytes() > 0)
    }

    @Test
    fun cleanupOrphans_removesOnlyStalePartialsAndQuarantine() {
        val oldTime = System.currentTimeMillis() - 100_000

        val oldPartial = file("tmp/old.mp3.partial", byteArrayOf(1))
        val oldDecPartial = file("tmp/old.mp3.dec.partial", byteArrayOf(2))
        val freshPartial = file("tmp/fresh.mp3.partial", byteArrayOf(3))
        val bystander = file("tmp/notes.txt", byteArrayOf(4))
        val oldQuarantined = file("tmp/quarantine/bad.mp3.123", byteArrayOf(5))
        val freshQuarantined = file("tmp/quarantine/new.mp3.456", byteArrayOf(6))
        for (f in listOf(oldPartial, oldDecPartial, bystander, oldQuarantined)) {
            assertTrue(f.setLastModified(oldTime))
        }

        store.cleanupOrphans(maxAgeMs = 50_000)

        assertFalse(oldPartial.exists())
        assertFalse(oldDecPartial.exists())
        assertFalse(oldQuarantined.exists())
        assertTrue(freshPartial.exists())
        assertTrue(freshQuarantined.exists())
        // Non-partial files in tmp/ are not the cleaner's business.
        assertTrue(bystander.exists())
    }
}
