# On-demand doha asset pipeline — multi-agent plan

**Date:** 2026-08-09 · **Branch:** `beta/screen-review` · **Lead:** orchestrator session
**Status:** COMPLETE — all six agents shipped; suite 292 green; integrated as `0.2.0-beta2`

## Mission (condensed)

The app ships gong + doha only. On request, if no valid local copy exists,
download ciphertext from `https://apt.vridhamma.org/updates/v2/`, verify
(SHA-256 + size), decrypt (OpenSSL `Salted__` AES-256-CBC, EVP_BytesToKey MD5),
verify plaintext (SHA-256 + size + ID3/MPEG magic), store under app storage,
play from `ready/` only. Reuse and repair half-broken files. Full spec is in
the user brief; the edge-case table there is the acceptance contract.

**Owner is rights-holder for this audio. Integrity checks only — no DRM.**

## Decisions already made (do not relitigate)

1. **No new dependencies.** `HttpURLConnection` + raw `ServerSocket` test
   server, matching `relay/ShellyClient`. No OkHttp, no WorkManager — prepare
   jobs run in the app scope with a single-flight guard (spec table allows
   "single-flight mutex" as the alternative).
2. **Passphrase** comes from `BuildConfig.MEDIA_PASSPHRASE`, injected from
   `local.properties` key `niyam.mediaPassphrase` or env
   `NIYAM_MEDIA_PASSPHRASE` (already wired in `app/build.gradle.kts`). Empty
   value = downloads blocked with `BlockReason.NO_KEY`. **Never** log, toast,
   persist, or test with the real value. Tests use their own throwaway
   passphrase.
3. **Catalog** is `app/src/main/assets/doha_manifest.json` (already copied;
   schema_version 1, `assets[]` with dual checksums). `seed/doha-manifest.json`
   is a *different file* (slot map) — do not touch it.
4. **Shared types are pinned** in `domain/AssetTypes.kt` (written by Lead).
   `AudioAsset`, `Magic`, `Observed`, `Target`, `BlockReason`, `Step`.
   Compile against them; do not edit that file — ask the Lead via your report.
5. Status caching (skip re-hash of a 45 MB file on every play) lives in A4's
   index keyed by (path, size, mtime); the domain machine itself always
   receives a real hash observation.

## File locks

| Agent | Owns (create/edit) | Must not touch |
|---|---|---|
| A1 | `domain/AssetResolve.kt`, `test .../domain/AssetResolveTest.kt` | everything else |
| A2 | `assets/OpenSslSaltedAes.kt`, `assets/Integrity.kt`, their tests, `app/src/test/resources/crypto/*` | everything else |
| A3 | `assets/AssetCatalog.kt`, `assets/CdnDownloader.kt`, their tests, `app/src/test/resources/catalog/*` | everything else |
| A4 | `assets/AssetStore.kt`, `assets/StorageLocator.kt`, their tests | everything else |
| B1 | `assets/AudioAssetManager.kt` + test, service/container wiring lines | UI files |
| B2 | `ui/DohaMediaScreen.kt`, `ui/AppViewModel.kt` (downloads section) | `assets/*`, `domain/*` |

`assets/` = `app/src/main/java/org/dhamma/gong/assets/` (new package).
Tests mirror under `app/src/test/java/org/dhamma/gong/...`.

## Pinned public APIs

### A1 — `domain/AssetResolve.kt` (pure, JVM-tested)

```kotlin
object AssetResolve {
    fun verifyReady(asset: AudioAsset, o: Observed): Boolean
    fun verifyEncrypted(asset: AudioAsset, o: Observed): Boolean
    fun next(
        asset: AudioAsset,
        ready: Observed,
        encrypted: Observed,
        partialBytes: Long?,      // size of tmp/<filename>.partial, null if none
        networkAllowed: Boolean,  // user intent + connectivity + metering policy
        freeBytes: Long,
        passphraseAvailable: Boolean,
    ): Step
}
```

Rules `next` must encode (each is a test): ready verified → `Play`; ready
present but wrong size/hash/magic → `Discard(READY)`; `Salted__` in ready →
`MoveReadyToEncrypted`; encrypted verified → `Decrypt` (but `NO_KEY` if no
passphrase); encrypted corrupt → `Discard(ENCRYPTED)`; nothing usable +
network → `Download(resumeFrom = partialBytes ?: 0)`; partial ≥ expected →
`Discard(PARTIAL)`; no network → `Blocked(OFFLINE)`; free space <
`encryptedBytes + decryptedBytes + 5 MiB` before download → `Blocked(NO_SPACE)`.
D01 vs D09: identity is id+hash, never title.

### A2 — crypto + integrity

```kotlin
object OpenSslSaltedAes {
    class KeyIv(val key: ByteArray, val iv: ByteArray)
    fun evpBytesToKeyMd5(password: ByteArray, salt: ByteArray): KeyIv
    /** Consumes the 16-byte Salted__ header; throws IllegalArgumentException on bad magic. */
    fun decryptingStream(input: java.io.InputStream, passphrase: CharArray): java.io.InputStream
}
object Integrity {
    fun sha256Hex(input: java.io.InputStream): String      // streaming, closes input
    fun sniff(header: ByteArray): Magic                    // first bytes → Magic
}
```

KDF: `D1 = MD5(pass||salt)`, `D2 = MD5(D1||pass||salt)`, `D3 = MD5(D2||pass||salt)`,
key = D1‖D2 (32 B), iv = D3 (16 B). Cipher `AES/CBC/PKCS5Padding` via
`CipherInputStream`. Tests: fixed KDF vector, round-trip on a small fixture
generated at dev time with `openssl enc -aes-256-cbc -md md5` and a TEST
passphrase, bad-magic rejection, wrong-pass → padding failure surfaces as
exception not garbage success. Sniff: ID3, 0xFFEx sync, `Salted__`, `<`/HTML,
empty, other.

### A3 — catalog + downloader

```kotlin
class AssetCatalog(val cdnBase: String, val assets: List<AudioAsset>) {
    companion object { fun parse(json: String): AssetCatalog }  // kotlinx.serialization
    fun byId(id: String): AudioAsset?
}
class CdnDownloader(private val timeoutMs: Int = 20_000) {
    sealed interface Outcome {
        data class Complete(val file: java.io.File) : Outcome
        data class Failed(val reason: String, val retryable: Boolean) : Outcome
    }
    /** Streams to dest (a .partial path), verifying size+sha inline. Resume re-hashes the existing prefix, sends Range. */
    fun fetch(
        url: String,
        dest: java.io.File,
        expectedBytes: Long,
        expectedSha256: String,
        resume: Boolean,
        onProgress: (received: Long, total: Long) -> Unit,
    ): Outcome
}
```

Reject: non-200/206; `Content-Type` containing `text/html`; body starting
`<`; final size < 1024 or ≠ expected; hash mismatch (delete dest on
mismatch). 206 only when Range was sent; a 200 answer to a Range request
restarts from zero. Test server: raw `ServerSocket` like `ShellyClientTest`
(happy path, resume/206, HTML soft-fail, truncation, hash mismatch, 404).
The `id` field for `AudioAsset` = filename without `.mp3`.

### A4 — storage

```kotlin
class AssetStore(private val root: java.io.File) {   // root = {appMediaRoot}/audio
    fun readyFile(a: AudioAsset): java.io.File        // ready/common-general/<filename>
    fun encryptedFile(a: AudioAsset): java.io.File    // encrypted/common-general/<filename>
    fun partialFile(a: AudioAsset): java.io.File      // tmp/<filename>.partial
    fun decPartialFile(a: AudioAsset): java.io.File   // tmp/<filename>.dec.partial
    fun observe(f: java.io.File, sniffOnlyBytes: Int = 16): Observed  // size + sniff + streaming sha256
    fun observeCached(f: java.io.File): Observed      // via index; (path,size,mtime) hit skips re-hash
    fun atomicMove(from: java.io.File, to: java.io.File)
    fun quarantine(f: java.io.File)                   // tmp/quarantine/, timestamped suffix
    fun freeBytes(): Long
    fun cleanupOrphans(maxAgeMs: Long)                // stale *.partial + quarantine
    fun invalidate(f: java.io.File)                   // drop index entry
}
class StorageLocator(private val roots: List<java.io.File>) {
    /** filename → file. Depth ≤ 2: {root}/<f>, {root}/<dir>/<f>. If a dir named common-general exists at depth ≤2, list it flat. Never deeper. */
    fun scan(filenames: Set<String>): Map<String, java.io.File>
}
```

Index: JSON via kotlinx.serialization at `{root}/.state/index.json`, corrupt
index = treated as empty, never crashes. Tests on temp dirs, plain JVM.

### B1 — `assets/AudioAssetManager.kt`

```kotlin
class AudioAssetManager(
    private val catalog: AssetCatalog,
    private val store: AssetStore,
    private val downloader: CdnDownloader,
    private val scope: kotlinx.coroutines.CoroutineScope,
    private val passphrase: () -> CharArray,          // from BuildConfig, empty = no key
    private val networkAllowed: (allowMetered: Boolean) -> Boolean,
) {
    sealed interface TrackState {
        data object NotDownloaded : TrackState
        data class Downloading(val received: Long, val total: Long) : TrackState
        data object Preparing : TrackState
        data class Ready(val file: java.io.File) : TrackState
        data class Error(val message: String) : TrackState   // human words, no crypto jargon
        data object NoKey : TrackState
    }
    val states: kotlinx.coroutines.flow.StateFlow<Map<String, TrackState>>  // key = asset id
    fun refreshFromDisk()                       // startup: cleanup orphans + index ready assets
    fun request(id: String, allowMetered: Boolean = true)   // single-flight per id
    fun requestAll(allowMetered: Boolean)
    fun scanStorage(extraRoots: List<java.io.File> = emptyList())  // depth-2 discovery, copy hits into store
}
```

Drives `AssetResolve.next` in a loop (observe → step → effect → re-observe),
max ~6 iterations then Error (defends against livelock). Decrypt path:
`decryptingStream` → `.dec.partial` → verify size+sha+magic → atomic move to
ready. `keepEncryptedCopy = true` default. Errors shown to users: plain
sentences ("Download failed — check the connection and try again",
"Checksum mismatch; try again", "This build has no media key").

### B2 — UI

Sounds screen gains a **Downloads** section: 11 rows (slot + short title
derived from filename), per-row state chip + progress bytes, retry on error;
`Download all dohas (~470 MB)` button — Wi-Fi by default, cellular needs an
explicit confirm dialog; `Scan storage for existing media` action; a one-line
note the first time about download size. Never render exception text,
`Salted__`, or the passphrase. Nocturne chrome, ≥44 dp targets, same Field /
section idioms as the rest of the screen.

## Wave order

- **Wave A (parallel):** A1, A2, A3, A4 — independent, all compile against
  `AssetTypes.kt` only.
- **Wave B (parallel, after A merges green):** B1, B2 — B2 codes against B1's
  pinned API above.
- **Lead integrates:** build + tests, ready→slot auto-map review, docs, QA
  checklist, commit (no trailers).

## Standing rules for every agent

- Read `AGENTS.md`, `CLAUDE.md`; skills at `~/.claude-personal/skills/`
  (testing-setup, android-data-layer for A/B waves; android-jetpack-compose
  for B2).
- **Edit incrementally; never whole-file `Write` on an existing file.**
- Checkpoint to disk as you go; leave a progress note at the end of this file
  under "Agent reports".
- `./gradlew :app:testDebugUnitTest --tests "org.dhamma.gong.<your scope>*"`
  must pass before you report done.
- No `Log.*` of passphrase, key, iv, or raw headers. No real passphrase
  anywhere, including tests.
- No Claude/session trailers in anything.

## Agent reports

(append below)

### A2 — crypto + integrity (2026-08-09)

Done: `assets/OpenSslSaltedAes.kt`, `assets/Integrity.kt`, both tests
(7 + 12 cases), fixtures in `app/src/test/resources/crypto/` (throwaway
passphrase `niyam-fixture-2026`, KDF vector from `openssl -P`).
**Blocked on verify:** `kspDebugKotlin` fails in A4's `AssetStore.kt`
(missing `}` 154:6, unclosed comment 266:1) on three attempts over ~4 min —
A2 tests never executed. Details: `asset-pipeline-reports/A2.md`.

### A1 — done

`domain/AssetResolve.kt` + `AssetResolveTest.kt` written; 42 tests green
(`testDebugUnitTest`, 0 failures). Pinned API implemented exactly, no
deviations. Details: `asset-pipeline-reports/A1.md`. One note for the Lead:
hash compare is case-insensitive in both verify functions, not just ready.

### A4 — done

`assets/AssetStore.kt` + `assets/StorageLocator.kt` + tests; 29 tests green
(20 + 9, 0 failures). Pinned API exact; index at `.state/index.json` treats
corrupt/missing as empty. One addition: `atomicMove` also drops index
entries for both paths (move preserves mtime, so a stale cache hit at the
destination could serve a wrong hash). Details: `asset-pipeline-reports/A4.md`.

### B1 — done (2026-08-09)

`assets/AudioAssetManager.kt` (pinned API exact) + `assets/AudioAssets.kt`
singleton + `assets/DownloadedSlotRegistrar.kt`; locked edits:
`MediaSlotSource.DOWNLOADED`, `DohaPackMapper.classify` lets a folder pack
claim `downloaded` slots (manual/bundled untouched, +1 test), GongService
wiring (get + refreshFromDisk + registrar), `ACCESS_NETWORK_STATE` in the
manifest. 9 new tests; full suite 292 green, 0 failures. Details:
`asset-pipeline-reports/B1.md`.

### B2 — done

Downloads UI landed: `ui/AppViewModel.kt` gained a "doha downloads" section
(lazy `AudioAssets.get`, `downloadStates`/`downloadCatalog` proxies,
`downloadDoha`, `downloadAllDohas`, `rescanDownloads`, `scanStorageForMedia`,
`downloadsMetered`); `ui/DohaMediaScreen.kt` gained a Downloads card — 11
rows sorted by filename (D-tag + derived title + state chip/progress/Retry),
NoKey banner once above the list, first-use size note, Download-all with
metered AlertDialog confirm (470 MB / 45 MB variants), scan-storage action,
and `rescanDownloads()` on screen entry. Compiled against B1's pinned API
exactly; `compileDebugKotlin` and full `testDebugUnitTest` green. No ui unit
tests exist. One note: no dialog idiom existed anywhere in `ui/`, so the
metered confirm is M3 `AlertDialog` on `Nocturne.SurfaceHigh` with
`PackButton`s. Details: `asset-pipeline-reports/B2.md`.
