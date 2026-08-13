package org.dhamma.gong.domain

/**
 * Shared vocabulary of the on-demand audio asset pipeline.
 *
 * Pure JVM, no Android imports, no IO — same law as the rest of `domain/`.
 * The IO layers (`assets/`) observe files and networks, translate what they
 * see into these types, and ask [AssetResolve] what to do next. Decisions
 * live here so they can be unit-tested against the whole half-broken-file
 * table without a filesystem.
 */

/** One catalog entry. D01 and D09 share a title family but are distinct assets. */
data class AudioAsset(
    val id: String,
    val filename: String,
    val relativePath: String,
    val cdnUrl: String,
    val encryptedSha256: String,
    val encryptedBytes: Long,
    val decryptedSha256: String,
    val decryptedBytes: Long,
)

/** What the first bytes of a file claim to be. */
enum class Magic {
    /** `ID3` tag header — plaintext MP3. */
    ID3,

    /** MPEG frame sync (0xFF 0xEx) — plaintext MP3 without ID3. */
    MPEG,

    /** ASCII `Salted__` — OpenSSL-enveloped ciphertext. */
    SALTED,

    /** Looks like an HTML error page served with HTTP 200. */
    HTML,

    /** Zero-length file. */
    EMPTY,

    /** None of the above. */
    OTHER,
}

/** What the IO layer found at one expected location. */
sealed interface Observed {
    data object Absent : Observed

    /** [sha256] is lowercase hex of the full content. */
    data class Present(val size: Long, val magic: Magic, val sha256: String) : Observed
}

/** Which on-disk artifact a [Step.Discard] refers to. */
enum class Target { READY, ENCRYPTED, PARTIAL }

/** Why the pipeline cannot proceed without outside help. */
enum class BlockReason { OFFLINE, NO_SPACE, NO_KEY, NOT_IN_CATALOG }

/**
 * The single next side effect the IO layer should perform. The caller
 * performs it, re-observes, and asks again — the machine itself never loops.
 */
sealed interface Step {
    /** Ready file verified; play it. Terminal success. */
    data object Play : Step

    /** Encrypted file verified; decrypt to a temp file, verify, promote. */
    data object Decrypt : Step

    /** Fetch ciphertext from the CDN, resuming from [resumeFrom] bytes. */
    data class Download(val resumeFrom: Long) : Step

    /**
     * Remove or quarantine a failed artifact, then ask again.
     * Never emitted for an artifact that verified clean.
     */
    data class Discard(val target: Target, val reason: String) : Step

    /** Ciphertext was found sitting in ready/; move it home, then ask again. */
    data object MoveReadyToEncrypted : Step

    /** Terminal failure until conditions change. */
    data class Blocked(val reason: BlockReason) : Step
}
