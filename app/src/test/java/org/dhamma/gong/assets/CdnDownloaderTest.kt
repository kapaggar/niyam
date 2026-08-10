package org.dhamma.gong.assets

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.concurrent.CopyOnWriteArrayList

/**
 * [CdnDownloader] against a hand-rolled loopback HTTP server serving real
 * bytes — resume/Range handshake, inline hashing, and every rejection row of
 * the download contract. Raw [ServerSocket] like `ShellyClientTest`: no new
 * dependency, and never the real CDN.
 */
class CdnDownloaderTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private var fake: FakeCdn? = null

    @After
    fun tearDown() {
        fake?.close()
    }

    private fun serve(handler: (FakeCdn.Request) -> ByteArray): FakeCdn =
        FakeCdn(handler).also { fake = it }

    private fun url(path: String = "/common-general/D06.mp3"): String =
        "http://127.0.0.1:${fake!!.port}$path"

    private val requests: List<FakeCdn.Request> get() = fake!!.seen

    /** Deterministic pseudo-body, big enough for several progress ticks. */
    private val body = ByteArray(600 * 1024) { (it % 251).toByte() }
    private val bodySha = sha256(body)

    private fun dest(): File = File(tmp.root, "D06.mp3.partial")

    private fun fetch(
        dest: File,
        expectedBytes: Long = body.size.toLong(),
        expectedSha256: String = bodySha,
        resume: Boolean = false,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
    ): CdnDownloader.Outcome = CdnDownloader(timeoutMs = 4_000)
        .fetch(url(), dest, expectedBytes, expectedSha256, resume, onProgress)

    // ------------------------------------------------------------ happy path

    @Test
    fun `happy path verifies bytes and hash with monotonic progress`() {
        serve { ok(body) }
        val dest = dest()
        val progress = mutableListOf<Pair<Long, Long>>()

        val outcome = fetch(dest, onProgress = { received, total -> progress += received to total })

        assertEquals(CdnDownloader.Outcome.Complete(dest), outcome)
        assertTrue("exact bytes on disk", dest.readBytes().contentEquals(body))

        assertTrue("progress must tick more than once for 600 KiB", progress.size > 1)
        assertTrue(
            "progress is throttled, never per 64 KiB read (${progress.size} calls)",
            progress.size < body.size / (64 * 1024),
        )
        assertTrue(
            "received must never decrease: $progress",
            progress.zipWithNext().all { (a, b) -> a.first <= b.first },
        )
        assertEquals("final callback reports completion", body.size.toLong(), progress.last().first)
        assertTrue(progress.all { it.second == body.size.toLong() })
        assertEquals("no Range header on a fresh download", null, requests.single().range)
    }

    // ------------------------------------------------------------ resume

    @Test
    fun `resume sends Range, re-hashes the prefix and completes on 206`() {
        val half = body.size / 2
        serve { req ->
            assertEquals("bytes=$half-", req.range)
            partial(body.copyOfRange(half, body.size), from = half, of = body.size)
        }
        val dest = dest().apply { writeBytes(body.copyOf(half)) }

        val outcome = fetch(dest, resume = true)

        assertEquals(CdnDownloader.Outcome.Complete(dest), outcome)
        assertTrue("prefix + tail must hash as the whole file", dest.readBytes().contentEquals(body))
    }

    @Test
    fun `a 200 answer to a Range request restarts from zero and still completes`() {
        val half = body.size / 2
        serve { req ->
            assertEquals("bytes=$half-", req.range)
            ok(body) // server ignores the Range and sends everything
        }
        val dest = dest().apply { writeBytes(body.copyOf(half)) }

        val outcome = fetch(dest, resume = true)

        assertEquals(CdnDownloader.Outcome.Complete(dest), outcome)
        assertTrue("restart must not append to the old prefix", dest.readBytes().contentEquals(body))
    }

    // ------------------------------------------------------------ rejections

    @Test
    fun `an html error page with 200 fails and deletes dest`() {
        val page = "<html><body>Please sign in to the network</body></html>".toByteArray()
        serve { ok(page, contentType = "text/html; charset=utf-8") }
        val dest = dest()

        val outcome = fetch(dest)

        assertTrue("$outcome", outcome is CdnDownloader.Outcome.Failed)
        assertEquals("Server returned an error page", (outcome as CdnDownloader.Outcome.Failed).reason)
        assertFalse("an error page must never linger as a partial", dest.exists())
    }

    @Test
    fun `a body starting with an angle bracket fails even with a lying content type`() {
        val page = "<!DOCTYPE html><html>Blocked</html>".toByteArray()
        serve { ok(page, contentType = "application/octet-stream") }
        val dest = dest()

        val outcome = fetch(dest)

        assertTrue("$outcome", outcome is CdnDownloader.Outcome.Failed)
        assertFalse(dest.exists())
    }

    @Test
    fun `a truncated body fails retryable and keeps the partial for resume`() {
        val half = body.size / 2
        serve { truncated(body, claimedLength = body.size, actuallySent = half) }
        val dest = dest()

        val outcome = fetch(dest)

        assertTrue("$outcome", outcome is CdnDownloader.Outcome.Failed)
        assertTrue("truncation must be retryable", (outcome as CdnDownloader.Outcome.Failed).retryable)
        assertTrue("the partial must survive for a future resume", dest.exists())
        assertTrue("some bytes must have landed", dest.length() > 0)
        assertTrue("but not all of them", dest.length() < body.size)
    }

    @Test
    fun `a hash mismatch fails and deletes dest`() {
        serve { ok(body) }
        val dest = dest()

        val outcome = fetch(dest, expectedSha256 = "0".repeat(64))

        assertEquals(
            CdnDownloader.Outcome.Failed("Checksum mismatch; try again", retryable = true),
            outcome,
        )
        assertFalse("corrupt bytes can never become the right file", dest.exists())
    }

    @Test
    fun `a 404 is a permanent failure`() {
        serve { status(404, "Not Found") }

        val outcome = fetch(dest())

        assertTrue("$outcome", outcome is CdnDownloader.Outcome.Failed)
        assertFalse("404 will not fix itself", (outcome as CdnDownloader.Outcome.Failed).retryable)
    }

    @Test
    fun `a 500 is retryable`() {
        serve { status(500, "Internal Server Error") }

        val outcome = fetch(dest())

        assertTrue("$outcome", outcome is CdnDownloader.Outcome.Failed)
        assertTrue((outcome as CdnDownloader.Outcome.Failed).retryable)
    }

    @Test
    fun `an empty expected hash is refused before any request`() {
        serve { ok(body) }

        val outcome = fetch(dest(), expectedSha256 = "")

        assertTrue("$outcome", outcome is CdnDownloader.Outcome.Failed)
        assertFalse((outcome as CdnDownloader.Outcome.Failed).retryable)
        assertEquals("must not even touch the network", 0, requests.size)
    }

    // ------------------------------------------------------------ responses

    private fun ok(payload: ByteArray, contentType: String = "application/octet-stream"): ByteArray =
        response("HTTP/1.1 200 OK", contentType, payload.size, payload)

    private fun partial(payload: ByteArray, from: Int, of: Int): ByteArray = response(
        "HTTP/1.1 206 Partial Content",
        "application/octet-stream",
        payload.size,
        payload,
        extraHeader = "Content-Range: bytes $from-${of - 1}/$of",
    )

    private fun truncated(payload: ByteArray, claimedLength: Int, actuallySent: Int): ByteArray =
        response(
            "HTTP/1.1 200 OK", "application/octet-stream", claimedLength,
            payload.copyOf(actuallySent),
        )

    private fun status(code: Int, text: String): ByteArray =
        response("HTTP/1.1 $code $text", "text/plain", 0, ByteArray(0))

    private fun response(
        statusLine: String,
        contentType: String,
        contentLength: Int,
        payload: ByteArray,
        extraHeader: String? = null,
    ): ByteArray {
        val head = buildString {
            append(statusLine).append("\r\n")
            append("Content-Type: ").append(contentType).append("\r\n")
            append("Content-Length: ").append(contentLength).append("\r\n")
            extraHeader?.let { append(it).append("\r\n") }
            append("Connection: close\r\n\r\n")
        }
        return ByteArrayOutputStream().apply {
            write(head.toByteArray())
            write(payload)
        }.toByteArray()
    }

    // ------------------------------------------------------------ fake server

    /** A one-connection-at-a-time HTTP/1.1 responder on loopback, binary body. */
    class FakeCdn(private val handler: (Request) -> ByteArray) {

        data class Request(val line: String, val headers: Map<String, String>) {
            val path: String get() = line.split(' ').getOrElse(1) { "" }
            val range: String? get() = headers["range"]
        }

        private val server = ServerSocket(0, 4, InetAddress.getByName("127.0.0.1"))
        val port: Int get() = server.localPort
        val seen = CopyOnWriteArrayList<Request>()

        private val thread = Thread {
            while (!server.isClosed) {
                runCatching { server.accept().use(::handle) }.getOrNull() ?: return@Thread
            }
        }.apply { isDaemon = true; start() }

        private fun handle(socket: Socket) {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val line = reader.readLine() ?: return
            val headers = HashMap<String, String>()
            while (true) {
                val h = reader.readLine()
                if (h.isNullOrEmpty()) break
                val i = h.indexOf(':')
                if (i > 0) headers[h.take(i).lowercase()] = h.substring(i + 1).trim()
            }
            val request = Request(line, headers)
            seen += request
            socket.getOutputStream().apply {
                write(handler(request))
                flush()
            }
        }

        fun close() {
            runCatching { server.close() }
            thread.interrupt()
        }
    }

    // ------------------------------------------------------------ helpers

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
}
