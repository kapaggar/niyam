package org.dhamma.gong.domain

/**
 * Decision core of the on-demand doha pipeline: given what the IO layer
 * observed on disk, emit the single next [Step].
 *
 * Plan: `docs/superpowers/plans/2026-08-09-doha-asset-pipeline-plan.md` (A1).
 *
 * The half-broken-file table lives here so it can be tested without a
 * filesystem. The caller performs the step, re-observes, and asks again —
 * this object never loops, never touches IO, never reads a clock.
 *
 * Identity is `id` + hash, never title: D01 and D09 share a title family and
 * identical sizes, so only the checksums can tell them apart.
 *
 * No Android imports — JVM unit tested, like [RelayPlan] and [FireRules].
 */
object AssetResolve {

    /** Headroom kept free beyond the bytes we are about to write. */
    private const val SPARE_BYTES: Long = 5L * 1024 * 1024

    /**
     * A playable plaintext copy: right size, right SHA-256 (hex compared
     * case-insensitively — dual-sourced manifests differ in case), and audio
     * magic ([Magic.ID3] or [Magic.MPEG]).
     */
    fun verifyReady(asset: AudioAsset, o: Observed): Boolean =
        o is Observed.Present &&
            o.size == asset.decryptedBytes &&
            hexEquals(o.sha256, asset.decryptedSha256) &&
            (o.magic == Magic.ID3 || o.magic == Magic.MPEG)

    /**
     * An intact ciphertext copy: right size, right SHA-256, and the OpenSSL
     * `Salted__` envelope ([Magic.SALTED]).
     */
    fun verifyEncrypted(asset: AudioAsset, o: Observed): Boolean =
        o is Observed.Present &&
            o.size == asset.encryptedBytes &&
            hexEquals(o.sha256, asset.encryptedSha256) &&
            o.magic == Magic.SALTED

    /**
     * The next side effect, evaluated strictly in spec order: ready first,
     * then encrypted, then the partial, then the network.
     *
     * @param ready what sits at `ready/…/<filename>`.
     * @param encrypted what sits at `encrypted/…/<filename>`.
     * @param partialBytes size of `tmp/<filename>.partial`, null if none.
     * @param networkAllowed user intent + connectivity + metering policy,
     *   already collapsed by the caller.
     * @param freeBytes free space on the target volume.
     * @param passphraseAvailable false when the build carries no media key.
     */
    fun next(
        asset: AudioAsset,
        ready: Observed,
        encrypted: Observed,
        partialBytes: Long?,
        networkAllowed: Boolean,
        freeBytes: Long,
        passphraseAvailable: Boolean,
    ): Step {
        // Step 1: a verified ready file wins over everything — offline, no
        // key, no space: none of it matters when we can already play.
        if (verifyReady(asset, ready)) return Step.Play

        if (ready is Observed.Present) {
            // Step 2: ciphertext sitting in ready/ (interrupted move, manual
            // copy). Salvage it — do NOT discard a possibly-good download.
            if (ready.magic == Magic.SALTED) return Step.MoveReadyToEncrypted
            // Step 3: present but not playable and not salvageable.
            return Step.Discard(Target.READY, failureReason(asset.decryptedBytes, asset.decryptedSha256, ready))
        }

        // Step 4: verified ciphertext → decrypt, if we hold the key and the
        // plaintext fits. NO_KEY before NO_SPACE: freeing space cannot help a
        // build that ships without a passphrase.
        if (verifyEncrypted(asset, encrypted)) {
            if (!passphraseAvailable) return Step.Blocked(BlockReason.NO_KEY)
            if (freeBytes < asset.decryptedBytes + SPARE_BYTES) {
                return Step.Blocked(BlockReason.NO_SPACE)
            }
            return Step.Decrypt
        }

        // Step 5: ciphertext present but corrupt — re-download beats trying
        // to decrypt garbage.
        if (encrypted is Observed.Present) {
            return Step.Discard(Target.ENCRYPTED, failureReason(asset.encryptedBytes, asset.encryptedSha256, encrypted))
        }

        // Step 6: a partial at least as large as the full ciphertext can
        // never complete into a valid file — resuming it would only append
        // past the expected end.
        if (partialBytes != null && partialBytes >= asset.encryptedBytes) {
            return Step.Discard(Target.PARTIAL, "partial larger than expected")
        }

        // Step 7: nothing usable on disk; the network is the only way
        // forward. OFFLINE before NO_SPACE: it is the cheaper condition for
        // the user to change, and space may free up before connectivity does.
        if (!networkAllowed) return Step.Blocked(BlockReason.OFFLINE)
        if (freeBytes < asset.encryptedBytes + asset.decryptedBytes + SPARE_BYTES) {
            return Step.Blocked(BlockReason.NO_SPACE)
        }
        return Step.Download(resumeFrom = partialBytes ?: 0)
    }

    // ------------------------------------------------------------ internals

    /** Case-insensitive hex compare; manifests and hashers disagree on case. */
    private fun hexEquals(a: String, b: String): Boolean = a.equals(b, ignoreCase = true)

    /**
     * Why a present-but-unverified artifact failed, most diagnostic signal
     * first: a non-audio magic explains itself; only then blame size or hash.
     */
    private fun failureReason(expectedBytes: Long, expectedSha256: String, o: Observed.Present): String =
        when {
            o.magic == Magic.EMPTY -> "empty file"
            o.magic == Magic.HTML -> "HTML error page saved as audio"
            o.magic == Magic.OTHER -> "unrecognised content"
            o.size != expectedBytes -> "wrong size (${o.size} != $expectedBytes)"
            !hexEquals(o.sha256, expectedSha256) -> "checksum mismatch"
            // Reachable only for plaintext magic in the encrypted slot: size
            // and hash match the *expected* values passed in, magic does not.
            else -> "wrong content type (${o.magic})"
        }
}
