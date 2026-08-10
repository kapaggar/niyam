package org.dhamma.gong.assets

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.dhamma.gong.domain.AudioAsset
import org.dhamma.gong.domain.Magic
import org.dhamma.gong.domain.Observed
import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/**
 * On-disk layout and observation for the doha asset pipeline.
 *
 * Layout under [root] (`{appMediaRoot}/audio`):
 *
 * ```
 * ready/<dir-of-relativePath>/<filename>   verified plaintext, playable
 * encrypted/<relativePath>                 verified ciphertext, kept for repair
 * tmp/<filename>.partial                   in-flight download
 * tmp/<filename>.dec.partial               in-flight decryption
 * tmp/quarantine/<name>.<epochMs>          failed artifacts kept briefly
 * .state/index.json                        (path, size, mtime) → sha256 cache
 * ```
 *
 * All methods are safe to call from a few coroutines at once; index access is
 * serialized on a private lock. Pure JVM — no Android imports — so everything
 * here is unit-testable on temp directories.
 */
class AssetStore(private val root: File) {

    // ---- path mapping -----------------------------------------------------

    /** `ready/<dir-of-relativePath>/<filename>` — subdir derived, not hardcoded. */
    fun readyFile(a: AudioAsset): File {
        val dir = File(a.relativePath).parent
        val readyRoot = File(root, "ready")
        return if (dir.isNullOrEmpty()) File(readyRoot, a.filename)
        else File(File(readyRoot, dir), a.filename)
    }

    /** `encrypted/<relativePath>`. */
    fun encryptedFile(a: AudioAsset): File = File(File(root, "encrypted"), a.relativePath)

    /** `tmp/<filename>.partial`. */
    fun partialFile(a: AudioAsset): File = File(File(root, "tmp"), "${a.filename}.partial")

    /** `tmp/<filename>.dec.partial`. */
    fun decPartialFile(a: AudioAsset): File = File(File(root, "tmp"), "${a.filename}.dec.partial")

    // ---- observation ------------------------------------------------------

    /**
     * Looks at [f] and reports [Observed.Absent] or [Observed.Present] with
     * actual size, magic sniff of the first [sniffOnlyBytes] bytes, and a
     * streaming lowercase-hex SHA-256 of the full content. Single read pass,
     * fixed buffer — never loads the file into memory.
     */
    fun observe(f: File, sniffOnlyBytes: Int = 16): Observed {
        if (!f.isFile) return Observed.Absent
        val digest = MessageDigest.getInstance("SHA-256")
        val header = ByteArray(sniffOnlyBytes)
        var headerLen = 0
        var total = 0L
        try {
            f.inputStream().use { input ->
                val buf = ByteArray(READ_BUFFER_BYTES)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    if (headerLen < header.size) {
                        val take = minOf(header.size - headerLen, n)
                        System.arraycopy(buf, 0, header, headerLen, take)
                        headerLen += take
                    }
                    digest.update(buf, 0, n)
                    total += n
                }
            }
        } catch (e: IOException) {
            return Observed.Absent
        }
        return Observed.Present(total, sniff(header.copyOf(headerLen)), digest.digest().toHexLower())
    }

    /**
     * Like [observe], but a cache hit on (canonical path, size, mtime) in the
     * on-disk index skips re-hashing 45 MB files on every play. A miss
     * delegates to [observe] and records the result.
     */
    fun observeCached(f: File): Observed {
        if (!f.isFile) return Observed.Absent
        val key = canonicalKey(f)
        val size = f.length()
        val mtime = f.lastModified()
        val cachedSha = synchronized(indexLock) {
            index[key]?.takeIf { it.size == size && it.mtime == mtime }?.sha256
        }
        if (cachedSha != null) {
            return Observed.Present(size, sniffHead(f), cachedSha)
        }
        val fresh = observe(f)
        if (fresh is Observed.Present) {
            synchronized(indexLock) {
                index[key] = IndexEntry(fresh.size, f.lastModified(), fresh.sha256)
                persistIndexLocked()
            }
        }
        return fresh
    }

    /** Drops the index entry for [f] so the next [observeCached] re-hashes. */
    fun invalidate(f: File) {
        synchronized(indexLock) {
            if (index.remove(canonicalKey(f)) != null) persistIndexLocked()
        }
    }

    // ---- moves ------------------------------------------------------------

    /**
     * Moves [from] to [to], creating parent directories. Tries an atomic
     * rename first; falls back to a plain replacing move where the filesystem
     * refuses atomic semantics.
     */
    fun atomicMove(from: File, to: File) {
        to.parentFile?.mkdirs()
        try {
            Files.move(from.toPath(), to.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } catch (e: AtomicMoveNotSupportedException) {
            Files.move(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        // Content at both paths changed; drop any stale hashes.
        invalidate(from)
        invalidate(to)
    }

    /** Moves a failed artifact to `tmp/quarantine/<name>.<epochMs>`. */
    fun quarantine(f: File) {
        val dest = File(File(root, "tmp/quarantine"), "${f.name}.${System.currentTimeMillis()}")
        atomicMove(f, dest)
    }

    // ---- housekeeping -----------------------------------------------------

    /** Usable bytes on the volume holding [root]. */
    fun freeBytes(): Long {
        root.mkdirs()
        return root.usableSpace
    }

    /**
     * Deletes stale `.partial` and `.dec.partial` files under `tmp` plus quarantined files
     * whose mtime is older than [maxAgeMs]. Other files in `tmp/` are left
     * alone.
     */
    fun cleanupOrphans(maxAgeMs: Long) {
        val cutoff = System.currentTimeMillis() - maxAgeMs
        val tmp = File(root, "tmp")
        tmp.listFiles()?.forEach { f ->
            if (f.isFile && f.name.endsWith(".partial") && f.lastModified() < cutoff) {
                if (f.delete()) invalidate(f)
            }
        }
        File(tmp, "quarantine").listFiles()?.forEach { f ->
            if (f.isFile && f.lastModified() < cutoff) {
                if (f.delete()) invalidate(f)
            }
        }
    }

    // ---- index ------------------------------------------------------------

    @Serializable
    private data class IndexEntry(val size: Long, val mtime: Long, val sha256: String)

    private val indexLock = Any()
    private val indexFile = File(File(root, ".state"), "index.json")

    /** Corrupt or missing index = empty; never throws. */
    private val index: MutableMap<String, IndexEntry> = run {
        try {
            if (indexFile.isFile) {
                json.decodeFromString<Map<String, IndexEntry>>(indexFile.readText()).toMutableMap()
            } else {
                mutableMapOf()
            }
        } catch (e: Exception) {
            mutableMapOf()
        }
    }

    /** Caller must hold [indexLock]. */
    private fun persistIndexLocked() {
        try {
            indexFile.parentFile?.mkdirs()
            indexFile.writeText(json.encodeToString(index.toMap()))
        } catch (e: IOException) {
            // Cache only; losing it just means a re-hash next time.
        }
    }

    // ---- helpers ----------------------------------------------------------

    private fun canonicalKey(f: File): String =
        try {
            f.canonicalPath
        } catch (e: IOException) {
            f.absolutePath
        }

    /** Cheap 16-byte header read for cache hits. */
    private fun sniffHead(f: File): Magic =
        try {
            f.inputStream().use { input ->
                val header = ByteArray(16)
                var len = 0
                while (len < header.size) {
                    val n = input.read(header, len, header.size - len)
                    if (n < 0) break
                    len += n
                }
                sniff(header.copyOf(len))
            }
        } catch (e: IOException) {
            Magic.OTHER
        }

    /**
     * First-bytes classification. Duplicates the logic of Agent A2's
     * `Integrity.sniff` on purpose: that object may not exist yet while the
     * A-wave agents run in parallel, so we must not depend on it. The Lead
     * may unify the two later.
     */
    private fun sniff(header: ByteArray): Magic {
        if (header.isEmpty()) return Magic.EMPTY
        if (header.size >= 3 &&
            header[0] == 'I'.code.toByte() &&
            header[1] == 'D'.code.toByte() &&
            header[2] == '3'.code.toByte()
        ) return Magic.ID3
        if (header.size >= 2 &&
            (header[0].toInt() and 0xFF) == 0xFF &&
            (header[1].toInt() and 0xE0) == 0xE0
        ) return Magic.MPEG
        if (header.size >= SALTED_MAGIC.size &&
            SALTED_MAGIC.indices.all { header[it] == SALTED_MAGIC[it] }
        ) return Magic.SALTED
        if (header[0] == '<'.code.toByte()) return Magic.HTML
        return Magic.OTHER
    }

    private fun ByteArray.toHexLower(): String =
        joinToString("") { "%02x".format(it) }

    private companion object {
        const val READ_BUFFER_BYTES = 64 * 1024
        val SALTED_MAGIC = "Salted__".toByteArray(Charsets.US_ASCII)
        val json = Json { ignoreUnknownKeys = true }
    }
}
