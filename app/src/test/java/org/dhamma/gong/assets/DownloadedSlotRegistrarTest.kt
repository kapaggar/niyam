package org.dhamma.gong.assets

import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.dhamma.gong.data.GongDatabase
import org.dhamma.gong.data.MediaSlotEntity
import org.dhamma.gong.data.MediaSlotSource
import org.dhamma.gong.domain.AudioAsset
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The registrar's one law: downloaded files may fill *empty* slots only.
 * Any existing row — manual, bundled, auto, even downloaded — is never
 * replaced; displacement by a folder pack happens in DohaPackMapper, not here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DownloadedSlotRegistrarTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var db: GongDatabase
    private lateinit var manager: AudioAssetManager
    private lateinit var store: AssetStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Slots 1, 3 and 5 via the D-prefix parse. */
    private val assets = listOf(
        readyAsset("D01_0632_Doha-Test-1_NA_NA", seed = 1),
        readyAsset("D03_0632_Doha-Test-3_NA_NA", seed = 3),
        readyAsset("D05_0632_Doha-Test-5_NA_NA", seed = 5),
    )

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            GongDatabase::class.java,
        ).allowMainThreadQueries().build()
        store = AssetStore(File(tmp.root, "store"))
        manager = AudioAssetManager(
            assetCatalog = AssetCatalog("http://127.0.0.1/", assets),
            store = store,
            downloader = CdnDownloader(timeoutMs = 1_000),
            scope = scope,
            passphrase = { "throwaway-test-pass".toCharArray() },
            online = { false },
            metered = { true },
        )
        // Verified plaintext already on disk for all three assets.
        for ((i, a) in assets.withIndex()) {
            store.readyFile(a).apply { parentFile?.mkdirs() }.writeBytes(plain(assets[i].id))
        }
    }

    @After
    fun tearDown() {
        scope.cancel()
        db.close()
    }

    private suspend fun awaitReadyAll() {
        manager.refreshFromDisk()
        withTimeout(10_000) {
            manager.states.first { s -> s.values.all { it is AudioAssetManager.TrackState.Ready } }
        }
    }

    private suspend fun awaitSlot(slot: Int): MediaSlotEntity = withTimeout(10_000) {
        var row = db.mediaSlots().get(slot)
        while (row == null) {
            delay(25)
            row = db.mediaSlots().get(slot)
        }
        row
    }

    @Test
    fun `fills only empty slots, with a file uri and source downloaded`() = runBlocking {
        awaitReadyAll()
        DownloadedSlotRegistrar(db, manager, scope).start()

        val row1 = awaitSlot(1)
        val row5 = awaitSlot(5)

        assertEquals(MediaSlotSource.DOWNLOADED, row1.source)
        assertEquals(assets[0].filename, row1.filename)
        assertEquals(Uri.fromFile(store.readyFile(assets[0])).toString(), row1.uri)
        assertEquals(MediaSlotSource.DOWNLOADED, row5.source)
        assertNull("slot 2 has no ready asset and must stay empty", db.mediaSlots().get(2))
    }

    @Test
    fun `never overwrites existing rows of any source`() = runBlocking {
        val manual = MediaSlotEntity(1, "content://tree/staff-pick.mp3", "staff-pick.mp3", MediaSlotSource.MANUAL)
        val auto = MediaSlotEntity(3, "content://tree/pack-d03.mp3", "pack-d03.mp3", MediaSlotSource.AUTO)
        db.mediaSlots().put(manual)
        db.mediaSlots().put(auto)
        awaitReadyAll()

        DownloadedSlotRegistrar(db, manager, scope).start()

        // Slot 5 is written last (ids are processed in order), so once it
        // lands the registrar has already passed over slots 1 and 3.
        awaitSlot(5)
        assertEquals("a manual row is untouchable", manual, db.mediaSlots().get(1))
        assertEquals("an auto row is untouchable too — only classify() may displace", auto, db.mediaSlots().get(3))
    }

    // ------------------------------------------------------------ fixtures

    private fun plain(id: String): ByteArray =
        ByteArray(8 * 1024) { i -> ((i * 17 + id.hashCode()) % 251).toByte() }.also {
            it[0] = 'I'.code.toByte(); it[1] = 'D'.code.toByte(); it[2] = '3'.code.toByte()
        }

    private fun readyAsset(id: String, seed: Int): AudioAsset {
        val plain = plain(id)
        return AudioAsset(
            id = id,
            filename = "$id.mp3",
            relativePath = "common-general/$id.mp3",
            cdnUrl = "http://127.0.0.1:1/common-general/$id.mp3",
            encryptedSha256 = "%064x".format(seed),
            encryptedBytes = plain.size.toLong() + 16,
            decryptedSha256 = MessageDigest.getInstance("SHA-256").digest(plain)
                .joinToString("") { "%02x".format(it) },
            decryptedBytes = plain.size.toLong(),
        )
    }
}
