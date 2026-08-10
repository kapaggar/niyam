package org.dhamma.gong.assets

import java.io.InputStream
import java.nio.CharBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Reader for the legacy `openssl enc -aes-256-cbc -md md5` envelope the
 * doha CDN serves: ASCII `Salted__`, 8 salt bytes, then AES-256-CBC
 * ciphertext keyed by EVP_BytesToKey (MD5, one round). The app decrypts
 * its own licensed media for local playback — this is interoperability
 * with an existing distribution format, nothing more.
 *
 * Pure JVM — no Android imports, no logging: key, iv and passphrase must
 * never reach a log line or an exception message.
 */
object OpenSslSaltedAes {

    private val MAGIC = "Salted__".toByteArray(StandardCharsets.US_ASCII)
    private const val SALT_BYTES = 8

    class KeyIv(val key: ByteArray, val iv: ByteArray)

    /**
     * OpenSSL `EVP_BytesToKey` with MD5 and one round per block:
     * `D1 = MD5(pass‖salt)`, `D2 = MD5(D1‖pass‖salt)`, `D3 = MD5(D2‖pass‖salt)`;
     * key = D1‖D2 (32 bytes), iv = D3 (16 bytes).
     */
    fun evpBytesToKeyMd5(password: ByteArray, salt: ByteArray): KeyIv {
        val md5 = MessageDigest.getInstance("MD5")
        val data = password + salt
        try {
            val d1 = md5.digest(data)
            md5.update(d1)
            md5.update(data)
            val d2 = md5.digest()
            md5.update(d2)
            md5.update(data)
            val d3 = md5.digest()
            return KeyIv(d1 + d2, d3)
        } finally {
            data.fill(0)
        }
    }

    /**
     * Consumes the 16-byte `Salted__` + salt header from [input] and returns
     * a stream of plaintext. Throws [IllegalArgumentException] if the header
     * is missing, truncated, or carries the wrong magic. A wrong passphrase
     * surfaces later as an [java.io.IOException] (bad PKCS#5 padding) when
     * the stream is drained — never as silently wrong bytes accepted here.
     */
    fun decryptingStream(input: InputStream, passphrase: CharArray): InputStream {
        val header = ByteArray(MAGIC.size + SALT_BYTES)
        var read = 0
        while (read < header.size) {
            val n = input.read(header, read, header.size - read)
            if (n < 0) break
            read += n
        }
        require(read == header.size) { "Envelope header truncated" }
        require(regionMatches(header, MAGIC)) { "Not an OpenSSL Salted__ envelope" }

        val salt = header.copyOfRange(MAGIC.size, header.size)
        val password = utf8Bytes(passphrase)
        val keyIv = try {
            evpBytesToKeyMd5(password, salt)
        } finally {
            password.fill(0)
        }
        // SecretKeySpec / IvParameterSpec clone their inputs, so the local
        // copies can be zeroed as soon as the cipher is initialised.
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        try {
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(keyIv.key, "AES"),
                IvParameterSpec(keyIv.iv),
            )
        } finally {
            keyIv.key.fill(0)
            keyIv.iv.fill(0)
        }
        return CipherInputStream(input, cipher)
    }

    private fun regionMatches(haystack: ByteArray, prefix: ByteArray): Boolean {
        if (haystack.size < prefix.size) return false
        for (i in prefix.indices) if (haystack[i] != prefix[i]) return false
        return true
    }

    /** UTF-8 encode without minting an immortal [String] of the passphrase. */
    private fun utf8Bytes(chars: CharArray): ByteArray {
        val buffer = StandardCharsets.UTF_8.encode(CharBuffer.wrap(chars))
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        if (buffer.hasArray()) buffer.array().fill(0)
        return bytes
    }
}
