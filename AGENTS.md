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

Paths: `skills/`, `.claude/skills/`, `.grok/skills/` (see `docs/SKILLS.md`).

**Do not invent schedule semantics.** If Gong-NG and a design note conflict on fire rules, prefer domain tests + `SchedulerCore` / `FireRules` / `ActiveCourse` / `DohaSlots`. Add a failing unit test before changing behaviour.

## Hard rules

1. Keep `domain/` free of Android framework imports (JVM unit tests must stay fast).
2. Persist double-fire guards **before** dispatching play.
3. Never fire early; late only within grace (default 120 s); else log `missed`.
4. Calendar day math only — never `seconds/86400` for course day.
5. Appliance timezone comes from settings (`timezone`, default `Asia/Kolkata`), not casual device TZ for travel phones.
6. Do not commit `local.properties`, keystores, secrets, or full copyrighted doha libraries.
7. Do not add analytics, ads, or a required cloud backend for core operation.
8. Deshna jukebox server is **out of scope** until gong/doha field MVP is stable.
9. Prefer small milestones; update `PROGRESS.md` when you finish a milestone slice.
10. Run `./gradlew :app:testDebugUnitTest` before claiming done.

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
