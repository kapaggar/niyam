package org.dhamma.gong.assets

import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import org.dhamma.gong.domain.Magic

/**
 * Content checks for downloaded and decrypted media: a streaming SHA-256
 * and a first-bytes sniffer that maps file headers onto [Magic]. Both are
 * pure JVM — no Android imports, no logging.
 */
object Integrity {

    private const val BUFFER_BYTES = 64 * 1024
    private const val HEX = "0123456789abcdef"

    private val SALTED = "Salted__".toByteArray(StandardCharsets.US_ASCII)
    private val ID3 = "ID3".toByteArray(StandardCharsets.US_ASCII)
    private val UTF8_BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
    private const val LT = '<'.code.toByte()

    /**
     * SHA-256 of the whole stream as lowercase hex. Streams in 64 KiB
     * chunks — a 45 MB doha never sits in memory — and always closes
     * [input], even on error.
     */
    fun sha256Hex(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        input.use { stream ->
            val buffer = ByteArray(BUFFER_BYTES)
            while (true) {
                val n = stream.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        val raw = digest.digest()
        val out = StringBuilder(raw.size * 2)
        for (b in raw) {
            val v = b.toInt() and 0xFF
            out.append(HEX[v ushr 4]).append(HEX[v and 0x0F])
        }
        return out.toString()
    }

    /**
     * What the first bytes of a file claim to be. [header] is however much
     * of the head the caller read — short or truncated reads degrade to
     * [Magic.OTHER] (or [Magic.EMPTY] for zero bytes), never crash.
     */
    fun sniff(header: ByteArray): Magic = when {
        header.isEmpty() -> Magic.EMPTY
        startsWith(header, ID3) -> Magic.ID3
        isMpegSync(header) -> Magic.MPEG
        startsWith(header, SALTED) -> Magic.SALTED
        header[0] == LT -> Magic.HTML
        startsWith(header, UTF8_BOM) && header.size > 3 && header[3] == LT -> Magic.HTML
        else -> Magic.OTHER
    }

    /** MPEG audio frame sync: 0xFF then a byte whose top three bits are set. */
    private fun isMpegSync(header: ByteArray): Boolean =
        header.size >= 2 &&
            header[0] == 0xFF.toByte() &&
            (header[1].toInt() and 0xE0) == 0xE0

    private fun startsWith(haystack: ByteArray, prefix: ByteArray): Boolean {
        if (haystack.size < prefix.size) return false
        for (i in prefix.indices) if (haystack[i] != prefix[i]) return false
        return true
    }
}
