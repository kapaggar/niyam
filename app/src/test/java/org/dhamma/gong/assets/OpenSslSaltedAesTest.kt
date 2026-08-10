package org.dhamma.gong.assets

import java.io.ByteArrayInputStream
import java.io.IOException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The OpenSSL `Salted__` envelope reader. Fixtures under
 * `src/test/resources/crypto/` were generated on a dev machine with the
 * system OpenSSL (3.6.3) and the throwaway TEST passphrase below — no
 * production passphrase appears anywhere in this repo.
 *
 * ```
 * openssl enc -aes-256-cbc -md md5 -pass pass:niyam-fixture-2026 \
 *     -in plain.bin -out salted.bin
 * ```
 *
 * `plain.bin` is 3072 deterministic bytes starting with ASCII `ID3` so it
 * sniffs as an MP3.
 */
class OpenSslSaltedAesTest {

    private val testPass = "niyam-fixture-2026"

    // Captured from:
    //   openssl enc -aes-256-cbc -md md5 -pass pass:niyam-fixture-2026 \
    //       -S 00112233445566FF -P
    private val kdfSaltHex = "00112233445566ff"
    private val kdfKeyHex =
        "b580e7e8ae44b892abd41857d6b3eb299e9fff3cb0ca357d689cd16193d732cf"
    private val kdfIvHex = "c47b18c7d1f522db15916df3a7e2f1d1"

    /** sha256 of plain.bin, from `shasum -a 256 plain.bin` at generation time. */
    private val plainSha256 =
        "b529bf1e8552cdd73e4342e9284f40cf356df8b4e468ccdd045a37c7fab90fee"

    @Test
    fun kdfMatchesOpenSslVector() {
        val out = OpenSslSaltedAes.evpBytesToKeyMd5(
            testPass.toByteArray(Charsets.UTF_8),
            fromHex(kdfSaltHex),
        )
        assertEquals(kdfKeyHex, toHex(out.key))
        assertEquals(kdfIvHex, toHex(out.iv))
    }

    @Test
    fun decryptsFixtureToExactPlaintext() {
        val expected = resource("/crypto/plain.bin")
        val decrypted = OpenSslSaltedAes
            .decryptingStream(resourceStream("/crypto/salted.bin"), testPass.toCharArray())
            .use { it.readBytes() }
        assertArrayEquals(expected, decrypted)
        assertEquals(
            plainSha256,
            Integrity.sha256Hex(ByteArrayInputStream(decrypted)),
        )
    }

    @Test
    fun decryptedFixtureSniffsAsMp3() {
        val decrypted = OpenSslSaltedAes
            .decryptingStream(resourceStream("/crypto/salted.bin"), testPass.toCharArray())
            .use { it.readBytes() }
        assertEquals(
            org.dhamma.gong.domain.Magic.ID3,
            Integrity.sniff(decrypted.copyOf(16)),
        )
    }

    @Test
    fun badMagicIsRejected() {
        // Right length, wrong eight bytes up front.
        val bogus = "NotSalt_12345678then-some-ciphertext".toByteArray(Charsets.US_ASCII)
        assertThrows(IllegalArgumentException::class.java) {
            OpenSslSaltedAes.decryptingStream(ByteArrayInputStream(bogus), testPass.toCharArray())
        }
    }

    @Test
    fun truncatedHeaderIsRejected() {
        // A valid magic but the stream ends before the 8 salt bytes arrive.
        val short = "Salted__123".toByteArray(Charsets.US_ASCII)
        assertThrows(IllegalArgumentException::class.java) {
            OpenSslSaltedAes.decryptingStream(ByteArrayInputStream(short), testPass.toCharArray())
        }
    }

    @Test
    fun wrongPassphraseFailsLoudlyNotSilently() {
        // Bad key -> bad PKCS#5 padding at end of stream. The cipher stream
        // must surface that as an exception, never hand back garbage as if
        // it were a clean decrypt.
        val stream = OpenSslSaltedAes.decryptingStream(
            resourceStream("/crypto/salted.bin"),
            "definitely-not-the-fixture-pass".toCharArray(),
        )
        val thrown = assertThrows(IOException::class.java) {
            stream.use { it.readBytes() }
        }
        // javax.crypto.CipherInputStream wraps the padding failure.
        assertTrue(thrown.cause is javax.crypto.BadPaddingException)
    }

    @Test
    fun emptyStreamIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            OpenSslSaltedAes.decryptingStream(
                ByteArrayInputStream(ByteArray(0)),
                testPass.toCharArray(),
            )
        }
    }

    // -- helpers ------------------------------------------------------------

    private fun resourceStream(path: String) =
        checkNotNull(javaClass.getResourceAsStream(path)) { "missing fixture $path" }

    private fun resource(path: String): ByteArray =
        resourceStream(path).use { it.readBytes() }

    private fun fromHex(hex: String): ByteArray =
        ByteArray(hex.length / 2) { i ->
            hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }

    private fun toHex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02x".format(it) }
}
