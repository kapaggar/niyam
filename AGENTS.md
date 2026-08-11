# AGENTS.md — guidance for coding agents working on Niyam

This file is for **autonomous coding agents** (Claude Code, Cursor, Codex, Fable, etc.). Humans may use it too.

## What this repository is

**Niyam** is an Android **appliance** app: a phone/tablet on charge runs the Vipassana course **gong** and **morning doha** schedule offline. Application id: `org.dhamma.gong`.

Remote: https://github.com/kapaggar/niyam  

Historical parent: Gong-NG / GongDohaServer Raspberry Pi daemon (behavioural reference, not a required submodule).

## Read before you change scheduling or playback

| Priority | Path | Why |
|----------|------|-----|
| 1 | `app/src/main/java/org/dhamma/gong/domain/` | **Behavioural law** — pure Kotlin ports |
| 2 | `app/src/test/java/org/dhamma/gong/` | Tests are the regression gate |
| 3 | `PROGRESS.md` | Milestone status and known gaps |
| 4 | `docs/FABLE-REVIEW.md` | Review findings and next work |
| 5 | `docs/ANDROID-APP-IMPLEMENTATION-PROMPT.md` | M0–M7 plan |
| 6 | `docs/handoff/` | Product UX (open HTML in a browser) |
| 7 | `SECURITY.md`, `PRIVACY.md`, `LICENSE`, `LICENSE-NOTES.md` | Compliance and threat model |
| 8 | `docs/SKILLS.md` + project skills under `skills/` | Installed Android agent skills (Compose, Room, tests, AVD) |

### Installed agent skills (load when relevant)

| Skill | Use for |
|-------|---------|
| `android-cli` | Emulator, install, screenshots, SDK |
| `adaptive` | Tablet / landscape / nav-rail layouts |
| `testing-setup` | Test harness and strategy |
| `android-data-layer` | Room, repository, offline data |
| `android-jetpack-compose` | Compose UI patterns |

Paths: repo `skills/`, project `.grok/skills/`, and for Claude on this machine **`$HOME/.claude-personal/skills/`** (not `~/.claude`). See `docs/SKILLS.md`.

**Do not invent schedule semantics.** If Gong-NG and a design note conflict on fire rules, prefer domain tests + `SchedulerCore` / `FireRules` / `ActiveCourse` / `DohaSlots`. Add a failing unit test before changing behaviour.

## Hard rules

1. Keep `domain/` free of Android framework imports (JVM unit tests must stay fast).
2. Persist double-fire guards **before** dispatching play.
3. Never fire early; late only within grace (default 120 s); else log `missed`.
4. Calendar day math only — never `seconds/86400` for course day.
5. Appliance timezone: the `timezone` setting **may pin** an IANA id, and a pin always wins.
   **Blank means follow the device** — a tablet installed at the centre already knows where it
   is and tracks DST without anyone touching the app. Do not reintroduce a hardcoded IST fallback.
6. Do not commit `local.properties`, keystores, secrets, or full copyrighted doha libraries.
7. Do not add analytics, ads, or a required cloud backend for core operation.
8. Deshna jukebox server is **out of scope** until gong/doha field MVP is stable.
9. Prefer small milestones; update `PROGRESS.md` when you finish a milestone slice.
10. Run `./gradlew :app:testDebugUnitTest` before claiming done.
11. **Bump the APK version whenever a substantive change lands.** `versionCode`
    +1 and a new `versionName` in `app/build.gradle.kts`, in the same commit as
    the change. See "Versioning the test APK" below.

## Layout (agent map)

```
app/src/main/java/org/dhamma/gong/
  domain/     SchedulerCore, FireRules, ClockTrust, ActiveCourse, DohaSlots, PinCode, …
  data/       Room, GongRepository, SeedLoader
  player/     PlayerEngine, Media3 sink, routing
  schedule/   SchedulerEngine, AlarmScheduler
  service/    GongService (≈ gongd), boot/time receivers
  ui/         Compose client of the service — must not own the schedule
```

The **service** owns scheduler + player. The **activity** is a client; closing the UI must not stop the appliance.

## Commands

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Requires JDK 17+, Android SDK in `local.properties` (`sdk.dir=…`).

## Versioning the test APK

Every build that could reach a tester carries a version, and it is the only
way anyone can tell two APKs apart once they are on a tablet. So:

**Bump `versionCode` by 1 and set a new `versionName` in
`app/build.gradle.kts` in the same commit as any substantive change.**

Substantive means: a new or unlocked screen, a behaviour change, a media
swap, a permission added, a fix to anything on the QA checklist. It does not
mean comment-only edits, doc-only edits, or a test-only refactor — those ride
along on the next real bump.

`versionName` follows `0.MINOR.PATCH-betaN`; increment `N` for an ordinary
slice and move the numeral for a milestone.

Why this is a hard rule and not a nicety: a tester installs over the top. An
*equal* `versionCode` lets Android keep the old code in place on some
installs, and a *lower* one is refused outright — so a stale APK presents as
"the bug you fixed came back", and a real morning of QA gets spent chasing it.

With the bump, in the same commit:

- update the version line at the top of `docs/BETA-QA-CHECKLIST.md`
- name the new version in `PROGRESS.md`

The running build is shown on the **Setup** screen ("Build"), so a tester can
confirm what they are holding without a cable.

## Current product gaps (do not pretend they are done)

- M5+: full Sounds / Audio out / Time / Network / Setup screens (some may be partial)
- M6: backup/restore, doha SAF pack, first-run wizard
- M7: Play hardening
- Real overnight tablet / OEM battery validation

See `PROGRESS.md` for the live checklist.

## Commits and PRs

- Complete sentences in commit messages; explain *why* (work done only).
- **Never** add agent attribution trailers or watermarks to commits, including:
  - `Co-Authored-By: Claude …` / `…@anthropic.com`
  - `Claude-Session: …` or other session URLs
  - `Generated with …` / `Made-with: …` style lines
- Do not force-push `main` unless the human explicitly asks.
- Do not amend published commits unless explicitly asked.
- Never skip hooks without human approval.

## Security / privacy

Follow `SECURITY.md` and `PRIVACY.md`. Do not log PINs or dump full databases into chat transcripts.

### Media key

The legacy CDN media passphrase decrypts the downloaded doha ciphertext. It
lives in **`media.properties`** at the repo root — gitignored, never committed,
never printed. `media.properties.example` is the committed template.

Resolution order at build time, first hit wins: `media.properties` →
`local.properties` → `NIYAM_MEDIA_PASSPHRASE` (CI) → empty. It reaches the app
only as `BuildConfig.MEDIA_PASSPHRASE`.

It has its own file rather than another line in `local.properties` because
Android Studio regenerates that file when the SDK path changes and silently
drops everything else in it — and a build that quietly loses its key is
indistinguishable from one that never had it.

Rules, unchanged:

- Never in source control, logs, crash reports, analytics or UI. The build
  prints only `media key present` / `media key ABSENT`, never the value or its
  length — build output ends up pasted into issues.
- **A keyless build is valid, not broken.** Sounds shows the no-media-key state
  and downloads stay disabled; that path is the first item on the QA checklist.
  Never "fix" a missing key by hardcoding a default or committing one.
- Test fixtures use their own throwaway passphrase and must never use the real
  one.
