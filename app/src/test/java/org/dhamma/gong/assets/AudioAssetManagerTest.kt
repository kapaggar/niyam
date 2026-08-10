package org.dhamma.gong.assets

import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.dhamma.gong.domain.AudioAsset
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * [AudioAssetManager] end to end through the real Wave A pieces: real
 * [AssetStore] on a temp dir, real [CdnDownloader] against a loopback
 * server, real [OpenSslSaltedAes] over fixtures encrypted in-test with the
 * same EVP_BytesToKey KDF the OpenSSL vector test already pins.
 *
 * The passphrase below is a throwaway; no production value appears anywhere.
 */
class AudioAssetManagerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val testPass = "niyam-manager-test-2026"

    /** Deterministic plaintext MP3s — ID3 magic, distinct bodies per id. */
    private fun plainBytes(seed: Int): ByteArray =
        ByteArray(48 * 1024) { i -> ((i * 31 + seed) % 251).toByte() }.also {
            it[0] = 'I'.code.toByte(); it[1] = 'D'.code.toByte(); it[2] = '3'.code.toByte()
        }

    private val plain1 = plainBytes(1)
    private val plain2 = plainBytes(2)
    private val enc1 = encrypt(plain1)
    private val enc2 = encrypt(plain2)

    private var server: FakeServer? = null
    private val scopes = mutableListOf<CoroutineScope>()

    @After
    fun tearDown() {
        scopes.forEach { it.cancel() }
        server?.close()
    }

    // ------------------------------------------------------------ fixtures

    private fun serve(bodies: Map<String, ByteArray>): FakeServer =
        FakeServer(bodies).also { server = it }

    private fun asset(id: String, plain: ByteArray, enc: ByteArray): AudioAsset {
        val port = server?.port ?: 1 // port 1 = guaranteed refusal if hit
        return AudioAsset(
            id = id,
            filename = "$id.mp3",
            relativePath = "common-general/$id.mp3",
            cdnUrl = "http://127.0.0.1:$port/common-general/$id.mp3",
            encryptedSha256 = sha256(enc),
            encryptedBytes = enc.size.toLong(),
            decryptedSha256 = sha256(plain),
            decryptedBytes = plain.size.toLong(),
        )
    }

    private lateinit var store: AssetStore

    private fun manager(
        assets: List<AudioAsset>,
        pass: String = testPass,
        online: Boolean = true,
        metered: Boolean = false,
        roots: List<File> = emptyList(),
    ): AudioAssetManager {
        store = AssetStore(File(tmp.root, "store"))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scopes += scope
        return AudioAssetManager(
            assetCatalog = AssetCatalog("http://127.0.0.1/", assets),
            store = store,
            downloader = CdnDownloader(timeoutMs = 5_000),
            scope = scope,
            passphrase = { pass.toCharArray() },
            online = { online },
            metered = { metered },
            legacyRoots = { roots },
        )
    }

    private fun awaitTerminal(
        m: AudioAssetManager,
        id: String,
        timeoutMs: Long = 15_000,
    ): AudioAssetManager.TrackState = runBlocking {
        withTimeout(timeoutMs) {
            m.states.first { states ->
                when (states.getValue(id)) {
                    is AudioAssetManager.TrackState.Ready,
                    is AudioAssetManager.TrackState.Error,
                    AudioAssetManager.TrackState.NoKey,
                    -> true
                    else -> false
                }
            }.getValue(id)
        }
    }

    // ------------------------------------------------------------ tests

    @Test
    fun `cold prepare - download, decrypt, verify, ready`() {
        serve(mapOf("/common-general/D01_test.mp3" to enc1))
        val a = asset("D01_test", plain1, enc1)
        val m = manager(listOf(a))
        assertEquals(
            "states start fully populated",
            AudioAssetManager.TrackState.NotDownloaded,
            m.states.value.getValue(a.id),
        )

        m.request(a.id)
        val state = awaitTerminal(m, a.id)

        assertTrue("$state", state is AudioAssetManager.TrackState.Ready)
        val readyFile = (state as AudioAssetManager.TrackState.Ready).file
        assertArrayEquals("exact plaintext lands in ready/", plain1, readyFile.readBytes())
        assertArrayEquals(
            "keepEncryptedCopy: ciphertext stays for future repair",
            enc1,
            store.encryptedFile(a).readBytes(),
        )
        assertEquals("one fetch, no more", 1, server!!.requests.get())
    }

    @Test
    fun `encrypted present and offline - decrypt only, zero network`() {
        serve(mapOf("/common-general/D01_test.mp3" to enc1))
        val a = asset("D01_test", plain1, enc1)
        val m = manager(listOf(a), online = false)
        store.encryptedFile(a).apply { parentFile?.mkdirs() }.writeBytes(enc1)

        m.request(a.id)
        val state = awaitTerminal(m, a.id)

        assertTrue("$state", state is AudioAssetManager.TrackState.Ready)
        assertArrayEquals(plain1, (state as AudioAssetManager.TrackState.Ready).file.readBytes())
        assertEquals("no socket may open when the ciphertext is good", 0, server!!.requests.get())
    }

    @Test
    fun `corrupt ready file is quarantined and repaired from the good encrypted copy`() {
        val a = asset("D01_test", plain1, enc1)
        val m = manager(listOf(a), online = false)
        store.encryptedFile(a).apply { parentFile?.mkdirs() }.writeBytes(enc1)
        // Right magic, right size, wrong bytes — only the hash can catch it.
        val corrupt = plain1.copyOf().also { it[1024] = (it[1024] + 1).toByte() }
        store.readyFile(a).apply { parentFile?.mkdirs() }.writeBytes(corrupt)

        m.request(a.id)
        val state = awaitTerminal(m, a.id)

        assertTrue("$state", state is AudioAssetManager.TrackState.Ready)
        assertArrayEquals(
            "the repaired file carries the true bytes",
            plain1,
            (state as AudioAssetManager.TrackState.Ready).file.readBytes(),
        )
        val quarantined = File(tmp.root, "store/tmp/quarantine").listFiles().orEmpty()
        assertEquals("the corrupt copy went to quarantine, not oblivion", 1, quarantined.size)
        assertTrue(quarantined.single().name.startsWith(a.filename))
    }

    @Test
    fun `single-flight - a second request while the first runs is dropped`() {
        val gate = CountDownLatch(1)
        serve(mapOf("/common-general/D01_test.mp3" to enc1)).holdUntil = gate
        val a = asset("D01_test", plain1, enc1)
        val m = manager(listOf(a))

        m.request(a.id) // claims the id synchronously
        m.request(a.id) // must be dropped: the first is still in flight
        gate.countDown()
        val state = awaitTerminal(m, a.id)

        assertTrue("$state", state is AudioAssetManager.TrackState.Ready)
        assertEquals("exactly one fetch despite two requests", 1, server!!.requests.get())
    }

    @Test
    fun `empty passphrase - NoKey everywhere except a verified ready file`() {
        serve(mapOf("/common-general/D02_test.mp3" to enc2))
        val a1 = asset("D01_test", plain1, enc1)
        val a2 = asset("D02_test", plain2, enc2)
        val m = manager(listOf(a1, a2), pass = "")
        store.readyFile(a1).apply { parentFile?.mkdirs() }.writeBytes(plain1)

        m.refreshFromDisk()
        val s1 = awaitTerminal(m, a1.id)
        val s2 = awaitTerminal(m, a2.id)

        assertTrue("a verified file plays without any key: $s1", s1 is AudioAssetManager.TrackState.Ready)
        assertEquals(AudioAssetManager.TrackState.NoKey, s2)

        // And an explicit request must refuse network work, not download
        // ciphertext the build can never open. Give the async job time to
        // have misbehaved before asserting it did not.
        m.request(a2.id)
        assertEquals(AudioAssetManager.TrackState.NoKey, awaitTerminal(m, a2.id))
        runBlocking { delay(300) }
        assertEquals("no fetch without a key", 0, server!!.requests.get())
    }

    @Test
    fun `scanStorage copies a verified legacy file into the store and prepares it`() {
        val a = asset("D01_test", plain1, enc1)
        val legacyRoot = tmp.newFolder("legacy")
        File(legacyRoot, "common-general").mkdirs()
        val original = File(legacyRoot, "common-general/${a.filename}").apply { writeBytes(plain1) }
        val m = manager(listOf(a), online = false, roots = listOf(legacyRoot))

        m.scanStorage()
        val state = awaitTerminal(m, a.id)

        assertTrue("$state", state is AudioAssetManager.TrackState.Ready)
        assertArrayEquals(plain1, (state as AudioAssetManager.TrackState.Ready).file.readBytes())
        assertTrue("copy, never move — the foreign volume keeps its file", original.isFile)
        assertTrue(state.file.canonicalPath.startsWith(File(tmp.root, "store").canonicalPath))
    }

    // ------------------------------------------------------------ helpers

    /**
     * `openssl enc -aes-256-cbc -md md5` envelope, built with the same KDF
     * [OpenSslSaltedAesTest] pins against a real OpenSSL vector.
     */
    private fun encrypt(plain: ByteArray): ByteArray {
        val salt = ByteArray(8) { (it * 7 + 3).toByte() }
        val keyIv = OpenSslSaltedAes.evpBytesToKeyMd5(testPass.toByteArray(), salt)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding").apply {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyIv.key, "AES"), IvParameterSpec(keyIv.iv))
        }
        return "Salted__".toByteArray(StandardCharsets.US_ASCII) + salt + cipher.doFinal(plain)
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }

    /** Minimal loopback HTTP server: 200 + exact body per path, else 404. */
    private class FakeServer(private val bodies: Map<String, ByteArray>) {
        private val socket = ServerSocket(0, 4, InetAddress.getByName("127.0.0.1"))
        val port: Int get() = socket.localPort
        val requests = AtomicInteger(0)

        /** When set, every request blocks here before answering. */
        @Volatile
        var holdUntil: CountDownLatch? = null

        private val thread = Thread {
            while (!socket.isClosed) {
                runCatching { socket.accept().use(::handle) }.getOrNull() ?: return@Thread
            }
        }.apply { isDaemon = true; start() }

        private fun handle(client: Socket) {
            val reader = BufferedReader(InputStreamReader(client.getInputStream()))
            val line = reader.readLine() ?: return
            while (true) {
                val h = reader.readLine()
                if (h.isNullOrEmpty()) break
            }
            requests.incrementAndGet()
            holdUntil?.await(10, TimeUnit.SECONDS)
            val path = line.split(' ').getOrElse(1) { "" }
            val body = bodies[path]
            val head = if (body != null) {
                "HTTP/1.1 200 OK\r\nContent-Type: application/octet-stream\r\n" +
                    "Content-Length: ${body.size}\r\nConnection: close\r\n\r\n"
            } else {
                "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
            }
            client.getOutputStream().apply {
                write(
                    ByteArrayOutputStream().apply {
                        write(head.toByteArray())
                        body?.let(::write)
                    }.toByteArray(),
                )
                flush()
            }
        }

        fun close() {
            runCatching { socket.close() }
            thread.interrupt()
        }
    }
}
