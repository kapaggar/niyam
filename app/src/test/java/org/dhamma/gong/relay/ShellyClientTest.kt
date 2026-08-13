package org.dhamma.gong.relay

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.concurrent.CopyOnWriteArrayList

/**
 * [ShellyClient] against a hand-rolled loopback HTTP server — request shape, the
 * RFC 7616 digest handshake, and the timeout that keeps an unresponsive Shelly
 * from ever mattering.
 *
 * Raw [ServerSocket] rather than a fake-webserver library on purpose: no new
 * dependency, and `com.sun.net.httpserver` is not on the Android unit-test
 * classpath. No real hardware — a live Shelly is a QA-checklist item.
 */
class ShellyClientTest {

    private var fake: FakeShelly? = null

    @After
    fun tearDown() {
        fake?.close()
    }

    private fun serve(handler: (FakeShelly.Request) -> String): FakeShelly =
        FakeShelly(handler).also { fake = it }

    private val host: String get() = "127.0.0.1:${fake!!.port}"

    private val requests: List<FakeShelly.Request> get() = fake!!.seen

    // ------------------------------------------------------------ request shape

    @Test
    fun `switch on sends id, on and toggle_after`() {
        serve { ok("""{"was_on":false}""") }

        val result = runBlocking {
            ShellyClient().setSwitch(host, switchId = 0, on = true, toggleAfterSeconds = 1870)
        }

        assertTrue(result is ShellyClient.Result.Ok)
        val path = requests.single().path
        assertTrue(path, path.startsWith("/rpc/Switch.Set?"))
        assertTrue(path, path.contains("id=0"))
        assertTrue(path, path.contains("on=true"))
        assertTrue("the watchdog must be on the wire: $path", path.contains("toggle_after=1870"))
    }

    @Test
    fun `switch off never carries toggle_after`() {
        serve { ok("""{"was_on":true}""") }

        runBlocking {
            ShellyClient().setSwitch(host, switchId = 1, on = false, toggleAfterSeconds = 900)
        }

        val path = requests.single().path
        assertTrue(path, path.contains("id=1"))
        assertTrue(path, path.contains("on=false"))
        assertTrue(
            "an OFF carrying a timer would flip the amp back on: $path",
            !path.contains("toggle_after"),
        )
    }

    @Test
    fun `device info probe is unauthenticated and returns the body`() {
        serve { ok("""{"model":"S4SW-001X16EU","mac":"AABBCCDDEEFF"}""") }

        val result = runBlocking { ShellyClient().deviceInfo(host) }

        assertEquals("/rpc/Shelly.GetDeviceInfo", requests.single().path)
        assertEquals(null, requests.single().authorization)
        val body = (result as ShellyClient.Result.Ok).body
        assertEquals("S4SW-001X16EU", ShellyClient.field(body, "model"))
        assertEquals("AABBCCDDEEFF", ShellyClient.field(body, "mac"))
    }

    @Test
    fun `a host with a scheme or trailing slash still works`() {
        serve { ok("""{"was_on":false}""") }

        val result = runBlocking {
            ShellyClient().setSwitch("http://$host/", switchId = 0, on = true)
        }
        assertTrue(result is ShellyClient.Result.Ok)
    }

    @Test
    fun `an empty host fails fast without a request`() {
        val result = runBlocking { ShellyClient().deviceInfo("   ") }
        assertTrue(result is ShellyClient.Result.Failed)
    }

    // ------------------------------------------------------------ digest auth

    @Test
    fun `digest sha-256 challenge is answered correctly`() {
        val realm = "shelly1g4-aabbccddeeff"
        val nonce = "1710000000"
        serve { req ->
            if (req.authorization == null) challenge(realm, nonce) else ok("""{"was_on":false}""")
        }

        val result = runBlocking {
            ShellyClient().setSwitch(
                host, switchId = 0, on = true, toggleAfterSeconds = 100,
                user = "admin", password = "secret",
            )
        }

        assertTrue("$result", result is ShellyClient.Result.Ok)
        assertEquals("one challenge, one answer — never a retry loop", 2, requests.size)
        assertEquals(null, requests[0].authorization)

        val header = requests[1].authorization!!
        assertTrue(header, header.startsWith("Digest "))
        assertTrue(header, header.contains("""username="admin""""))
        assertTrue(header, header.contains("algorithm=SHA-256"))
        assertTrue("the password must never appear on the wire", !header.contains("secret"))

        // Recompute RFC 7616 independently and compare.
        val ha1 = sha256("admin:$realm:secret")
        val ha2 = sha256("GET:${requests[1].path}")
        val expected = sha256(
            "$ha1:$nonce:${field(header, "nc")}:${field(header, "cnonce")}:auth:$ha2",
        )
        assertEquals(expected, field(header, "response"))
    }

    @Test
    fun `a 401 with no password is reported as auth required, not a failure`() {
        serve { challenge("shelly1g4-test", "abc") }

        val result = runBlocking { ShellyClient().setSwitch(host, 0, on = true) }

        assertEquals(ShellyClient.Result.AuthRequired("shelly1g4-test"), result)
        assertEquals("no password means no second request", 1, requests.size)
    }

    @Test
    fun `a wrong password gives up after one retry`() {
        serve { challenge("shelly1g4-test", "abc") }

        val result = runBlocking {
            ShellyClient().setSwitch(host, 0, on = true, password = "wrong")
        }

        assertTrue("$result", result is ShellyClient.Result.AuthRequired)
        assertEquals("one attempt per transition — never a retry storm", 2, requests.size)
    }

    // ------------------------------------------------------------ failure modes

    @Test
    fun `a hung relay fails inside the read timeout`() {
        serve {
            Thread.sleep(3_000)
            ok("""{"was_on":false}""")
        }

        val started = System.currentTimeMillis()
        val result = runBlocking {
            ShellyClient(connectTimeoutMs = 300, readTimeoutMs = 300).deviceInfo(host)
        }
        val elapsed = System.currentTimeMillis() - started

        assertTrue("$result", result is ShellyClient.Result.Failed)
        assertTrue("took ${elapsed}ms — a play must never wait on this", elapsed < 2_500)
    }

    @Test
    fun `an unreachable host is a failure, never an exception`() {
        // Port 1 on loopback: nothing listens, connection refused.
        val result = runBlocking { ShellyClient(300, 300).deviceInfo("127.0.0.1:1") }
        assertTrue("$result", result is ShellyClient.Result.Failed)
    }

    @Test
    fun `a non-2xx status is reported with its code`() {
        serve { "HTTP/1.1 500 Internal Server Error\r\nContent-Length: 0\r\nConnection: close\r\n\r\n" }

        val result = runBlocking { ShellyClient().deviceInfo(host) }

        assertEquals(ShellyClient.Result.Failed("HTTP 500"), result)
    }

    // ------------------------------------------------------------ fake server

    private fun ok(body: String): String =
        "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n" +
            "Content-Length: ${body.toByteArray().size}\r\nConnection: close\r\n\r\n$body"

    private fun challenge(realm: String, nonce: String): String =
        "HTTP/1.1 401 Unauthorized\r\n" +
            "WWW-Authenticate: Digest qop=\"auth\", realm=\"$realm\", " +
            "nonce=\"$nonce\", algorithm=SHA-256\r\n" +
            "Content-Length: 0\r\nConnection: close\r\n\r\n"

    /** A one-connection-at-a-time HTTP/1.1 responder on loopback. */
    class FakeShelly(private val handler: (Request) -> String) {

        data class Request(val line: String, val headers: Map<String, String>) {
            val path: String get() = line.split(' ').getOrElse(1) { "" }
            val authorization: String? get() = headers["authorization"]
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
            val response = handler(request)
            socket.getOutputStream().apply {
                write(response.toByteArray())
                flush()
            }
        }

        fun close() {
            runCatching { server.close() }
            thread.interrupt()
        }
    }

    // ------------------------------------------------------------ helpers

    private fun field(header: String, name: String): String =
        Regex("""$name=("?)([^",]+)\1""").find(header)?.groupValues?.get(2)
            ?: error("no $name in $header")

    private fun sha256(s: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
