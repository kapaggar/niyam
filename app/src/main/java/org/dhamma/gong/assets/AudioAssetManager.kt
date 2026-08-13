package org.dhamma.gong.assets

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.dhamma.gong.domain.AssetResolve
import org.dhamma.gong.domain.AudioAsset
import org.dhamma.gong.domain.BlockReason
import org.dhamma.gong.domain.Step
import org.dhamma.gong.domain.Target

/**
 * Orchestrator of the on-demand doha pipeline: per-asset state machine driver
 * around the pure [AssetResolve] core and the Wave A pieces ([AssetStore],
 * [CdnDownloader], [OpenSslSaltedAes], [StorageLocator]).
 *
 * Plan: `docs/superpowers/plans/2026-08-09-doha-asset-pipeline-plan.md` (B1).
 *
 * Deliberately free of Android imports — the [AudioAssets] singleton supplies
 * the Android-flavoured collaborators (connectivity lambdas, legacy storage
 * roots, `BuildConfig` passphrase), so every path in here is JVM unit-testable
 * on temp directories and a loopback HTTP server.
 *
 * Concurrency: all work runs on [scope]; a per-id in-flight guard makes
 * [request] single-flight (a second request while the first runs is dropped,
 * per the spec's "single-flight mutex" alternative to WorkManager).
 */
class AudioAssetManager(
    private val assetCatalog: AssetCatalog,
    private val store: AssetStore,
    private val downloader: CdnDownloader,
    private val scope: CoroutineScope,
    /** Fresh copy per call; empty array = this build carries no media key. */
    private val passphrase: () -> CharArray,
    /** Any connectivity with internet capability right now? Unknown = false. */
    private val online: () -> Boolean,
    /** Current connection metered? Unknown = true (the cautious default). */
    private val metered: () -> Boolean,
    /** Legacy storage roots for [scanStorage]; may not exist or be readable. */
    private val legacyRoots: () -> List<File> = { emptyList() },
) {

    sealed interface TrackState {
        data object NotDownloaded : TrackState
        data class Downloading(val received: Long, val total: Long) : TrackState

        /** Decrypting and verifying — no progress to show, just "busy". */
        data object Preparing : TrackState
        data class Ready(val file: File) : TrackState

        /** Plain human words — safe to render verbatim. */
        data class Error(val message: String) : TrackState

        /** The build has no media passphrase; downloads cannot help. */
        data object NoKey : TrackState
    }

    /** One entry per catalog asset, always fully populated. Key = asset id. */
    private val _states = MutableStateFlow(
        assetCatalog.assets.associate { it.id to (TrackState.NotDownloaded as TrackState) },
    )
    val states: StateFlow<Map<String, TrackState>> = _states.asStateFlow()

    val catalog: List<AudioAsset> get() = assetCatalog.assets

    /** Ids with a prepare job running right now. Guarded by itself. */
    private val inFlight = mutableSetOf<String>()

    /** True when the current connection is metered (or unknown). */
    fun isMetered(): Boolean = metered()

    /**
     * Startup pass, async: drop stale partials and old quarantine, then mark
     * each catalog asset [TrackState.Ready] or not from what is on disk.
     * No network, no decryption — the cached index keeps this cheap.
     */
    fun refreshFromDisk() {
        scope.launch {
            runCatching { store.cleanupOrphans(ORPHAN_MAX_AGE_MS) }
            val hasKey = passphraseAvailable()
            for (asset in assetCatalog.assets) {
                if (isInFlight(asset.id)) continue
                val ready = store.readyFile(asset)
                val state = if (AssetResolve.verifyReady(asset, store.observeCached(ready))) {
                    TrackState.Ready(ready)
                } else if (!hasKey) {
                    TrackState.NoKey
                } else {
                    TrackState.NotDownloaded
                }
                setStateUnlessInFlight(asset.id, state)
            }
        }
    }

    /**
     * Make this asset playable, async. Single-flight: a request for an id
     * whose job is still running is dropped. Unknown ids are ignored.
     */
    fun request(id: String, allowMetered: Boolean = true) {
        val asset = assetCatalog.byId(id) ?: return
        if (!claim(asset.id)) return
        scope.launch {
            try {
                prepareLocked(asset) { online() && (allowMetered || !metered()) }
            } finally {
                release(asset.id)
            }
        }
    }

    fun requestAll(allowMetered: Boolean) {
        for (asset in assetCatalog.assets) request(asset.id, allowMetered)
    }

    /**
     * Depth-2 discovery over the legacy storage roots, async: a verified hit
     * is *copied* (never moved — foreign volume) into the store, then prepared
     * without any network. Unreadable roots under scoped storage simply yield
     * nothing; the SAF folder picker remains the supported path.
     */
    fun scanStorage() {
        scope.launch {
            val roots = legacyRoots()
                .filter { it.exists() && it.canRead() }
                .distinctBy { canonicalOrAbsolute(it) }
            if (roots.isEmpty()) return@launch
            val wanted = assetCatalog.assets
                .filter { states.value[it.id] !is TrackState.Ready }
                .associateBy { it.filename }
            if (wanted.isEmpty()) return@launch
            val hits = StorageLocator(roots).scan(wanted.keys)
            for ((filename, file) in hits) {
                val asset = wanted[filename] ?: continue
                if (!claim(asset.id)) continue
                try {
                    adoptFound(asset, file)
                } finally {
                    release(asset.id)
                }
            }
        }
    }

    // ------------------------------------------------------------ prepare loop

    /**
     * The observe → [AssetResolve.next] → perform → re-observe loop. The
     * domain machine never loops itself; this driver does, with a hard cap
     * so a pathological disk can never spin forever.
     *
     * Caller must hold the in-flight claim for [asset].
     */
    private fun prepareLocked(asset: AudioAsset, networkAllowed: () -> Boolean) {
        setState(asset.id, TrackState.Preparing)
        var mutated = false
        repeat(MAX_STEPS) {
            val readyFile = store.readyFile(asset)
            val encryptedFile = store.encryptedFile(asset)
            val partialFile = store.partialFile(asset)

            // Fast path first: a verified ready file needs no second hash of
            // a 45 MB ciphertext. Cached observe until we mutate the disk.
            val ready = if (mutated) store.observe(readyFile) else store.observeCached(readyFile)
            if (AssetResolve.verifyReady(asset, ready)) {
                setState(asset.id, TrackState.Ready(readyFile))
                return
            }
            val encrypted =
                if (mutated) store.observe(encryptedFile) else store.observeCached(encryptedFile)

            val step = AssetResolve.next(
                asset = asset,
                ready = ready,
                encrypted = encrypted,
                partialBytes = partialFile.takeIf { it.isFile }?.length(),
                networkAllowed = networkAllowed(),
                freeBytes = store.freeBytes(),
                passphraseAvailable = passphraseAvailable(),
            )

            when (step) {
                is Step.Play -> {
                    setState(asset.id, TrackState.Ready(readyFile))
                    return
                }

                is Step.Decrypt -> {
                    if (!decryptVerified(asset, encryptedFile)) return
                    mutated = true
                }

                is Step.Download -> {
                    // A keyless build must not spend the user's data on
                    // ciphertext it can never open.
                    if (!passphraseAvailable()) {
                        setState(asset.id, TrackState.NoKey)
                        return
                    }
                    if (!download(asset, partialFile, encryptedFile, step.resumeFrom)) return
                    mutated = true
                }

                is Step.Discard -> {
                    when (step.target) {
                        Target.READY -> runCatching { store.quarantine(readyFile) }
                        Target.ENCRYPTED -> runCatching { store.quarantine(encryptedFile) }
                        Target.PARTIAL -> {
                            partialFile.delete()
                            store.invalidate(partialFile)
                        }
                    }
                    mutated = true
                }

                is Step.MoveReadyToEncrypted -> {
                    runCatching { store.atomicMove(readyFile, encryptedFile) }
                    mutated = true
                }

                is Step.Blocked -> {
                    setState(
                        asset.id,
                        when (step.reason) {
                            BlockReason.OFFLINE ->
                                TrackState.Error("No connection. Connect to WiFi and try again.")
                            BlockReason.NO_SPACE ->
                                TrackState.Error("Not enough free space on this device.")
                            BlockReason.NO_KEY -> TrackState.NoKey
                            BlockReason.NOT_IN_CATALOG ->
                                TrackState.Error("This recording is not in the catalog.")
                        },
                    )
                    return
                }
            }
        }
        // Livelock defence: the loop should converge in 3-4 steps.
        setState(asset.id, TrackState.Error("Could not prepare this track"))
    }

    /**
     * `encrypted/<f>` → decrypting stream → `tmp/<f>.dec.partial`, verify
     * size + sha + magic, promote to `ready/`. The ciphertext stays put
     * (keepEncryptedCopy) so a future corrupt ready file can be repaired
     * offline. Returns false after setting a terminal state.
     */
    private fun decryptVerified(asset: AudioAsset, encryptedFile: File): Boolean {
        setState(asset.id, TrackState.Preparing)
        val dec = store.decPartialFile(asset)
        dec.parentFile?.mkdirs()
        val pass = passphrase()
        try {
            FileInputStream(encryptedFile).use { cipherIn ->
                OpenSslSaltedAes.decryptingStream(cipherIn, pass).use { plain ->
                    FileOutputStream(dec).use { out -> plain.copyTo(out, COPY_BUFFER_BYTES) }
                }
            }
        } catch (e: Exception) {
            // Bad padding (wrong key) or IO. The ciphertext verified clean,
            // so retrying the same decrypt cannot end differently.
            dec.delete()
            store.invalidate(dec)
            setState(asset.id, TrackState.Error("Could not prepare this track"))
            return false
        } finally {
            pass.fill(' ')
        }
        if (!AssetResolve.verifyReady(asset, store.observe(dec))) {
            dec.delete()
            store.invalidate(dec)
            setState(asset.id, TrackState.Error("Could not prepare this track"))
            return false
        }
        return runCatching { store.atomicMove(dec, store.readyFile(asset)) }
            .onFailure {
                dec.delete()
                setState(asset.id, TrackState.Error("Could not prepare this track"))
            }
            .isSuccess
    }

    /** One CDN attempt into `tmp/<f>.partial`, promoted to `encrypted/` on success. */
    private fun download(
        asset: AudioAsset,
        partialFile: File,
        encryptedFile: File,
        resumeFrom: Long,
    ): Boolean {
        partialFile.parentFile?.mkdirs()
        setState(asset.id, TrackState.Downloading(resumeFrom, asset.encryptedBytes))
        val outcome = downloader.fetch(
            url = asset.cdnUrl,
            dest = partialFile,
            expectedBytes = asset.encryptedBytes,
            expectedSha256 = asset.encryptedSha256,
            resume = resumeFrom > 0,
            onProgress = { received, total ->
                setState(asset.id, TrackState.Downloading(received, total))
            },
        )
        return when (outcome) {
            is CdnDownloader.Outcome.Complete -> {
                store.invalidate(partialFile)
                runCatching { store.atomicMove(partialFile, encryptedFile) }
                    .onFailure { setState(asset.id, TrackState.Error("Could not prepare this track")) }
                    .isSuccess
            }
            is CdnDownloader.Outcome.Failed -> {
                // Reasons are already plain sentences; a kept partial resumes
                // on the next request.
                setState(asset.id, TrackState.Error(outcome.reason))
                false
            }
        }
    }

    /**
     * A [scanStorage] hit: copy a verified foreign file into the store via the
     * matching tmp path, then run the normal prepare loop with the network
     * off — discovery must never turn into a surprise download.
     */
    private fun adoptFound(asset: AudioAsset, found: File) {
        val observed = store.observe(found)
        val into = when {
            AssetResolve.verifyEncrypted(asset, observed) ->
                store.partialFile(asset) to store.encryptedFile(asset)
            AssetResolve.verifyReady(asset, observed) ->
                store.decPartialFile(asset) to store.readyFile(asset)
            else -> return // unverifiable strangers stay where they are
        }
        val (tmp, home) = into
        try {
            tmp.parentFile?.mkdirs()
            FileInputStream(found).use { input ->
                FileOutputStream(tmp).use { out -> input.copyTo(out, COPY_BUFFER_BYTES) }
            }
            store.invalidate(tmp)
            store.atomicMove(tmp, home)
        } catch (e: Exception) {
            tmp.delete()
            return
        }
        prepareLocked(asset) { false }
    }

    // ------------------------------------------------------------ state + guard

    private fun setState(id: String, state: TrackState) {
        _states.update { it + (id to state) }
    }

    /** refreshFromDisk must not clobber a live Downloading/Preparing entry. */
    private fun setStateUnlessInFlight(id: String, state: TrackState) {
        synchronized(inFlight) {
            if (id in inFlight) return
            setState(id, state)
        }
    }

    private fun claim(id: String): Boolean = synchronized(inFlight) { inFlight.add(id) }

    private fun release(id: String) {
        synchronized(inFlight) { inFlight.remove(id) }
    }

    private fun isInFlight(id: String): Boolean = synchronized(inFlight) { id in inFlight }

    private fun passphraseAvailable(): Boolean {
        val pass = passphrase()
        val available = pass.isNotEmpty()
        pass.fill(' ')
        return available
    }

    private fun canonicalOrAbsolute(f: File): String =
        runCatching { f.canonicalPath }.getOrDefault(f.absolutePath)

    private companion object {
        /** The loop converges in 3-4 steps; anything past this is livelock. */
        const val MAX_STEPS = 8

        /** Stale partials and quarantined corpses older than this are removed. */
        const val ORPHAN_MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000

        const val COPY_BUFFER_BYTES = 64 * 1024
    }
}
