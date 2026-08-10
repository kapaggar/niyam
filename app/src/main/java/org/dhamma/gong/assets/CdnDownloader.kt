package org.dhamma.gong.assets

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.security.MessageDigest

/**
 * Streams one ciphertext file from the CDN to a `.partial` destination,
 * hashing inline so a finished download is already verified — the caller
 * renames after [Outcome.Complete] and never re-reads 45 MB just to check it.
 *
 * Deliberately dependency-free ([HttpURLConnection], no OkHttp), matching
 * `relay/ShellyClient`. One attempt per call; retry policy belongs to the
 * caller, guided by [Outcome.Failed.retryable].
 *
 * Failure reasons are plain sentences — they can reach the UI verbatim, so
 * no exception class names, no HTTP jargon beyond a status code.
 */
class CdnDownloader(private val timeoutMs: Int = 20_000) {

    sealed interface Outcome {
        /** [file] is `dest`, fully written and hash-verified. */
        data class Complete(val file: File) : Outcome

        /** [retryable] = a later attempt could plausibly succeed unchanged. */
        data class Failed(val reason: String, val retryable: Boolean) : Outcome
    }

    /**
     * Fetches [url] into [dest] (the `.partial` path).
     *
     * With [resume] and a shorter-than-expected [dest] on disk, the existing
     * prefix is first replayed through the digest, then a `Range` request asks
     * for the rest; a server that answers 200 instead of 206 makes the
     * download restart from zero. Without [resume], any existing [dest] is
     * truncated.
     *
     * On truncation or a network drop the partial file is kept so a future
     * call can resume; on a hash/size mismatch or an HTML error page it is
     * deleted, because those bytes can never become the right file.
     */
    fun fetch(
        url: String,
        dest: File,
        expectedBytes: Long,
        expectedSha256: String,
        resume: Boolean,
        onProgress: (received: Long, total: Long) -> Unit,
    ): Outcome {
        if (expectedSha256.isBlank()) {
            return Outcome.Failed("This recording has no integrity data in the catalog", retryable = false)
        }
        val target = runCatching { URL(url) }.getOrElse {
            return Outcome.Failed("The download address is not valid", retryable = false)
        }

        val digest = MessageDigest.getInstance("SHA-256")
        var startFrom = 0L
        if (resume && dest.exists() && dest.length() in 1 until expectedBytes) {
            // Replay the existing prefix through the digest so the final hash
            // covers the whole file, not just the resumed tail.
            try {
                FileInputStream(dest).use { input ->
                    val buf = ByteArray(BUFFER_BYTES)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        digest.update(buf, 0, n)
                        startFrom += n
                    }
                }
            } catch (e: IOException) {
                dest.delete()
                return Outcome.Failed("The partly downloaded file could not be reused; try again", retryable = true)
            }
        } else if (dest.exists()) {
            // Not resuming, or the partial is empty / already too big to be a
            // prefix of the expected file: start over.
            dest.delete()
        }

        var conn: HttpURLConnection? = null
        try {
            conn = (target.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                useCaches = false
                // instanceFollowRedirects stays at its default (true).
                // HttpURLConnection refuses to follow a redirect across
                // protocols (https -> http or back), which is exactly the
                // policy we want; such a redirect surfaces as its raw 3xx
                // status and is rejected below.
                if (startFrom > 0) setRequestProperty("Range", "bytes=$startFrom-")
            }

            when (val code = conn.responseCode) {
                HttpURLConnection.HTTP_OK -> if (startFrom > 0) {
                    // Server ignored the Range request: restart from zero.
                    startFrom = 0
                    digest.reset()
                }

                HttpURLConnection.HTTP_PARTIAL -> if (startFrom == 0L) {
                    return Outcome.Failed("The server sent an unexpected partial answer", retryable = true)
                }

                HttpURLConnection.HTTP_NOT_FOUND ->
                    return Outcome.Failed("This recording was not found on the server", retryable = false)

                in 500..599 ->
                    return Outcome.Failed("The server had a problem; try again later", retryable = true)

                else ->
                    return Outcome.Failed("The server refused the download (HTTP $code)", retryable = false)
            }

            if ((conn.contentType ?: "").contains("text/html", ignoreCase = true)) {
                dest.delete()
                return Outcome.Failed("Server returned an error page", retryable = true)
            }

            val buf = ByteArray(BUFFER_BYTES)
            var received = startFrom
            var lastReported = startFrom
            var firstChunk = startFrom == 0L
            var failure: Outcome.Failed? = null
            var deleteDest = false

            conn.inputStream.use { input ->
                FileOutputStream(dest, /* append = */ startFrom > 0).use { out ->
                    loop@ while (true) {
                        val n = input.read(buf)
                        if (n < 0) break@loop
                        if (n == 0) continue@loop
                        if (firstChunk) {
                            firstChunk = false
                            if (buf[0] == '<'.code.toByte()) {
                                failure = Outcome.Failed("Server returned an error page", retryable = true)
                                deleteDest = true
                                break@loop
                            }
                        }
                        if (received + n > expectedBytes) {
                            failure = Outcome.Failed("The downloaded file was the wrong size; try again", retryable = true)
                            deleteDest = true
                            break@loop
                        }
                        out.write(buf, 0, n)
                        digest.update(buf, 0, n)
                        received += n
                        if (received - lastReported >= PROGRESS_EVERY_BYTES) {
                            lastReported = received
                            onProgress(received, expectedBytes)
                        }
                    }
                }
            }

            failure?.let {
                if (deleteDest) dest.delete()
                return it
            }

            if (received < expectedBytes) {
                // Truncated: keep the partial so the next attempt can resume.
                return Outcome.Failed("The download stopped early; try again", retryable = true)
            }
            if (received < MIN_PLAUSIBLE_BYTES) {
                dest.delete()
                return Outcome.Failed("The downloaded file was too small to be real audio", retryable = false)
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            if (!actual.equals(expectedSha256, ignoreCase = true)) {
                dest.delete()
                return Outcome.Failed("Checksum mismatch; try again", retryable = true)
            }

            onProgress(received, expectedBytes)
            return Outcome.Complete(dest)
        } catch (e: SocketTimeoutException) {
            // Partial (if any) stays on disk for a future resume.
            return Outcome.Failed("The connection timed out; try again", retryable = true)
        } catch (e: IOException) {
            return Outcome.Failed("The connection was interrupted; check the network and try again", retryable = true)
        } finally {
            runCatching { conn?.disconnect() }
        }
    }

    companion object {
        /** Fixed streaming buffer. */
        private const val BUFFER_BYTES = 64 * 1024

        /** Progress callback cadence — never per read call. */
        private const val PROGRESS_EVERY_BYTES = 256L * 1024

        /** Anything smaller than this is an error page, not a doha. */
        private const val MIN_PLAUSIBLE_BYTES = 1024L
    }
}
