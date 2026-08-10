package org.dhamma.gong.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The half-broken-file table from the pipeline plan, one behaviour per test.
 * Everything here is a pure function of observations — no filesystem, no
 * network, no clock — so every edge the IO layer might ever see is pinned.
 */
class AssetResolveTest {

    private val mib = 1024L * 1024
    private val spare = 5 * mib

    /** D01 — sizes deliberately shared with [d09]; only hashes differ. */
    private val d01 = AudioAsset(
        id = "D01_morning_doha",
        filename = "D01_morning_doha.mp3",
        relativePath = "common-general/D01_morning_doha.mp3",
        cdnUrl = "https://apt.vridhamma.org/updates/v2/D01_morning_doha.mp3.enc",
        encryptedSha256 = "aaaa1111aaaa1111aaaa1111aaaa1111aaaa1111aaaa1111aaaa1111aaaa1111",
        encryptedBytes = 40 * mib,
        decryptedSha256 = "bbbb2222bbbb2222bbbb2222bbbb2222bbbb2222bbbb2222bbbb2222bbbb2222",
        decryptedBytes = 39 * mib,
    )

    /** D09 — same title family and byte sizes as [d01], distinct hashes. */
    private val d09 = d01.copy(
        id = "D09_morning_doha",
        filename = "D09_morning_doha.mp3",
        relativePath = "common-general/D09_morning_doha.mp3",
        cdnUrl = "https://apt.vridhamma.org/updates/v2/D09_morning_doha.mp3.enc",
        encryptedSha256 = "cccc3333cccc3333cccc3333cccc3333cccc3333cccc3333cccc3333cccc3333",
        decryptedSha256 = "dddd4444dddd4444dddd4444dddd4444dddd4444dddd4444dddd4444dddd4444",
    )

    private fun goodReady(a: AudioAsset = d01, magic: Magic = Magic.ID3) =
        Observed.Present(a.decryptedBytes, magic, a.decryptedSha256)

    private fun goodEncrypted(a: AudioAsset = d01) =
        Observed.Present(a.encryptedBytes, Magic.SALTED, a.encryptedSha256)

    /** Defaults describe a healthy device with nothing on disk yet. */
    private fun next(
        asset: AudioAsset = d01,
        ready: Observed = Observed.Absent,
        encrypted: Observed = Observed.Absent,
        partialBytes: Long? = null,
        networkAllowed: Boolean = true,
        freeBytes: Long = 1024 * mib,
        passphraseAvailable: Boolean = true,
    ): Step = AssetResolve.next(
        asset = asset,
        ready = ready,
        encrypted = encrypted,
        partialBytes = partialBytes,
        networkAllowed = networkAllowed,
        freeBytes = freeBytes,
        passphraseAvailable = passphraseAvailable,
    )

    // ------------------------------------------------------------ verifyReady

    @Test
    fun `verifyReady accepts a correct ID3 file`() {
        assertTrue(AssetResolve.verifyReady(d01, goodReady(magic = Magic.ID3)))
    }

    @Test
    fun `verifyReady accepts a correct headerless MPEG file`() {
        assertTrue(AssetResolve.verifyReady(d01, goodReady(magic = Magic.MPEG)))
    }

    @Test
    fun `verifyReady compares hashes case-insensitively`() {
        val upper = Observed.Present(d01.decryptedBytes, Magic.ID3, d01.decryptedSha256.uppercase())
        assertTrue(AssetResolve.verifyReady(d01, upper))
    }

    @Test
    fun `verifyReady rejects a wrong size`() {
        val short = Observed.Present(d01.decryptedBytes - 1, Magic.ID3, d01.decryptedSha256)
        assertFalse(AssetResolve.verifyReady(d01, short))
    }

    @Test
    fun `verifyReady rejects a wrong hash`() {
        val bad = Observed.Present(d01.decryptedBytes, Magic.ID3, "0".repeat(64))
        assertFalse(AssetResolve.verifyReady(d01, bad))
    }

    @Test
    fun `verifyReady rejects non-audio magic`() {
        for (magic in listOf(Magic.SALTED, Magic.HTML, Magic.EMPTY, Magic.OTHER)) {
            val o = Observed.Present(d01.decryptedBytes, magic, d01.decryptedSha256)
            assertFalse("$magic must not verify as playable", AssetResolve.verifyReady(d01, o))
        }
    }

    @Test
    fun `verifyReady rejects an absent file`() {
        assertFalse(AssetResolve.verifyReady(d01, Observed.Absent))
    }

    // -------------------------------------------------------- verifyEncrypted

    @Test
    fun `verifyEncrypted accepts a correct salted envelope`() {
        assertTrue(AssetResolve.verifyEncrypted(d01, goodEncrypted()))
    }

    @Test
    fun `verifyEncrypted compares hashes case-insensitively`() {
        val upper = Observed.Present(d01.encryptedBytes, Magic.SALTED, d01.encryptedSha256.uppercase())
        assertTrue(AssetResolve.verifyEncrypted(d01, upper))
    }

    @Test
    fun `verifyEncrypted rejects a wrong size`() {
        val short = Observed.Present(d01.encryptedBytes - 1, Magic.SALTED, d01.encryptedSha256)
        assertFalse(AssetResolve.verifyEncrypted(d01, short))
    }

    @Test
    fun `verifyEncrypted rejects a wrong hash`() {
        val bad = Observed.Present(d01.encryptedBytes, Magic.SALTED, "f".repeat(64))
        assertFalse(AssetResolve.verifyEncrypted(d01, bad))
    }

    @Test
    fun `verifyEncrypted rejects plaintext magic`() {
        val plain = Observed.Present(d01.encryptedBytes, Magic.ID3, d01.encryptedSha256)
        assertFalse(AssetResolve.verifyEncrypted(d01, plain))
    }

    @Test
    fun `verifyEncrypted rejects an absent file`() {
        assertFalse(AssetResolve.verifyEncrypted(d01, Observed.Absent))
    }

    // -------------------------------------------- identity is id + hash

    @Test
    fun `a file whose hash matches D09 never verifies as D01`() {
        // Same title family, identical sizes — only the checksum can tell
        // them apart, and it must.
        val d09Plain = goodReady(a = d09)
        val d09Cipher = goodEncrypted(a = d09)
        assertTrue(AssetResolve.verifyReady(d09, d09Plain))
        assertFalse("title-family confusion", AssetResolve.verifyReady(d01, d09Plain))
        assertTrue(AssetResolve.verifyEncrypted(d09, d09Cipher))
        assertFalse("title-family confusion", AssetResolve.verifyEncrypted(d01, d09Cipher))
    }

    @Test
    fun `D09 content sitting in the D01 ready slot is discarded, not played`() {
        val step = next(asset = d01, ready = goodReady(a = d09))
        assertTrue(step is Step.Discard && step.target == Target.READY)
    }

    // ----------------------------------------------- next(): step 1, ready

    @Test
    fun `verified ready file plays`() {
        assertEquals(Step.Play, next(ready = goodReady()))
    }

    @Test
    fun `verified ready file plays even offline, keyless and out of space`() {
        // Step 1 outranks every blocker: we can already play.
        assertEquals(
            Step.Play,
            next(ready = goodReady(), networkAllowed = false, freeBytes = 0, passphraseAvailable = false),
        )
    }

    // ------------------------------------- next(): step 2, misplaced cipher

    @Test
    fun `salted file in ready moves home instead of being discarded`() {
        val misplaced = Observed.Present(d01.encryptedBytes, Magic.SALTED, d01.encryptedSha256)
        assertEquals(Step.MoveReadyToEncrypted, next(ready = misplaced))
    }

    @Test
    fun `salted file in ready moves home even when its size and hash look wrong`() {
        // Whether the ciphertext is intact is judged *after* the move, in the
        // encrypted slot — never thrown away from here.
        val odd = Observed.Present(123L, Magic.SALTED, "e".repeat(64))
        assertEquals(Step.MoveReadyToEncrypted, next(ready = odd))
    }

    // -------------------------------------------- next(): step 3, bad ready

    @Test
    fun `ready file with wrong size is discarded`() {
        val short = Observed.Present(d01.decryptedBytes - 512, Magic.ID3, d01.decryptedSha256)
        val step = next(ready = short)
        assertTrue(step is Step.Discard && step.target == Target.READY)
    }

    @Test
    fun `ready file with wrong hash is discarded`() {
        val bad = Observed.Present(d01.decryptedBytes, Magic.ID3, "9".repeat(64))
        val step = next(ready = bad)
        assertTrue(step is Step.Discard && step.target == Target.READY)
    }

    @Test
    fun `HTML error page in ready is discarded`() {
        val html = Observed.Present(4321, Magic.HTML, "a".repeat(64))
        val step = next(ready = html)
        assertTrue(step is Step.Discard && step.target == Target.READY)
    }

    @Test
    fun `empty file in ready is discarded`() {
        val empty = Observed.Present(0, Magic.EMPTY, "b".repeat(64))
        val step = next(ready = empty)
        assertTrue(step is Step.Discard && step.target == Target.READY)
    }

    @Test
    fun `unrecognised content in ready is discarded`() {
        val other = Observed.Present(d01.decryptedBytes, Magic.OTHER, d01.decryptedSha256)
        val step = next(ready = other)
        assertTrue(step is Step.Discard && step.target == Target.READY)
    }

    @Test
    fun `bad ready is cleared before a verified encrypted copy is decrypted`() {
        // Step 3 before step 4: one effect per call; the caller re-asks after
        // the discard and only then gets Decrypt.
        val bad = Observed.Present(d01.decryptedBytes, Magic.ID3, "9".repeat(64))
        val step = next(ready = bad, encrypted = goodEncrypted())
        assertTrue(step is Step.Discard && step.target == Target.READY)
    }

    // -------------------------------------- next(): step 4, decrypt gating

    @Test
    fun `verified encrypted copy decrypts`() {
        assertEquals(Step.Decrypt, next(encrypted = goodEncrypted()))
    }

    @Test
    fun `verified encrypted copy without a passphrase is blocked NO_KEY`() {
        assertEquals(
            Step.Blocked(BlockReason.NO_KEY),
            next(encrypted = goodEncrypted(), passphraseAvailable = false),
        )
    }

    @Test
    fun `decrypt needs plaintext size plus five MiB free`() {
        assertEquals(
            Step.Blocked(BlockReason.NO_SPACE),
            next(encrypted = goodEncrypted(), freeBytes = d01.decryptedBytes + spare - 1),
        )
    }

    @Test
    fun `decrypt proceeds at exactly the space threshold`() {
        assertEquals(
            Step.Decrypt,
            next(encrypted = goodEncrypted(), freeBytes = d01.decryptedBytes + spare),
        )
    }

    @Test
    fun `no key outranks no space`() {
        // Freeing storage cannot help a build that ships without a key.
        assertEquals(
            Step.Blocked(BlockReason.NO_KEY),
            next(encrypted = goodEncrypted(), passphraseAvailable = false, freeBytes = 0),
        )
    }

    // ---------------------------------------- next(): step 5, bad encrypted

    @Test
    fun `corrupt encrypted copy is discarded`() {
        val bad = Observed.Present(d01.encryptedBytes, Magic.SALTED, "1".repeat(64))
        val step = next(encrypted = bad)
        assertTrue(step is Step.Discard && step.target == Target.ENCRYPTED)
    }

    @Test
    fun `truncated encrypted copy is discarded`() {
        val short = Observed.Present(d01.encryptedBytes - 1024, Magic.SALTED, d01.encryptedSha256)
        val step = next(encrypted = short)
        assertTrue(step is Step.Discard && step.target == Target.ENCRYPTED)
    }

    @Test
    fun `plaintext sitting in the encrypted slot is discarded`() {
        val plain = Observed.Present(d01.decryptedBytes, Magic.ID3, d01.decryptedSha256)
        val step = next(encrypted = plain)
        assertTrue(step is Step.Discard && step.target == Target.ENCRYPTED)
    }

    // ------------------------------------- next(): step 6, oversized partial

    @Test
    fun `partial at least as large as the ciphertext is discarded`() {
        val step = next(partialBytes = d01.encryptedBytes + 1)
        assertEquals(Step.Discard(Target.PARTIAL, "partial larger than expected"), step)
    }

    @Test
    fun `partial exactly the ciphertext size is discarded, not resumed`() {
        // A complete-looking partial that never verified must not be trusted;
        // resuming would append past the expected end.
        val step = next(partialBytes = d01.encryptedBytes)
        assertTrue(step is Step.Discard && step.target == Target.PARTIAL)
    }

    @Test
    fun `oversized partial is discarded even offline`() {
        // Step 6 before step 7: the junk goes first; OFFLINE is reported on
        // the re-ask.
        val step = next(partialBytes = d01.encryptedBytes + 5, networkAllowed = false)
        assertTrue(step is Step.Discard && step.target == Target.PARTIAL)
    }

    // ------------------------------------------ next(): step 7, download

    @Test
    fun `nothing on disk and no network is blocked OFFLINE`() {
        assertEquals(Step.Blocked(BlockReason.OFFLINE), next(networkAllowed = false))
    }

    @Test
    fun `offline outranks no space`() {
        assertEquals(
            Step.Blocked(BlockReason.OFFLINE),
            next(networkAllowed = false, freeBytes = 0),
        )
    }

    @Test
    fun `download needs cipher plus plain plus five MiB free`() {
        assertEquals(
            Step.Blocked(BlockReason.NO_SPACE),
            next(freeBytes = d01.encryptedBytes + d01.decryptedBytes + spare - 1),
        )
    }

    @Test
    fun `download proceeds at exactly the space threshold`() {
        assertEquals(
            Step.Download(resumeFrom = 0),
            next(freeBytes = d01.encryptedBytes + d01.decryptedBytes + spare),
        )
    }

    @Test
    fun `nothing on disk downloads from zero`() {
        assertEquals(Step.Download(resumeFrom = 0), next())
    }

    @Test
    fun `a sane partial resumes from its size`() {
        val resume = 12 * mib
        assertEquals(Step.Download(resumeFrom = resume), next(partialBytes = resume))
    }
}
