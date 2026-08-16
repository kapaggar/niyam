# Fable review handoff — Gong Android appliance (M0–M4)

**Date:** 2026-08-08  
**Branch:** `android-app` @ gongserver monorepo (`android/`)  
**Reviewer pass:** post–Opus build code review + factory-reset emulator reinstall  
**APK:** `android/app/build/outputs/apk/debug/app-debug.apk` (v`0.1.0-mvp`, minSdk 29)  
**Emulator for manual review:** AVD **`cca34`** (API 34), wiped + reinstalled this session  

> **⚠️ Historical snapshot — frozen at 2026-08-08 (`0.1.0-mvp`, 110 tests).**
> Nearly everything in the status tables and "next work" queue below has since
> shipped or changed (M5–M7 largely done, PIN gate enforced, Network screen
> deleted, 420 tests, minSdk TEMP 27, `0.2.0-beta15`). Read `PROGRESS.md` for
> live status; keep this file for the review findings and their rationale.

This document is the single restart surface for **Fable** (or any follow-on agent) to understand **design → plan → implementation**, what works, what is wrong, and what to build next.

---

## 1. One-sentence product

> A phone/tablet left on charge at a Vipassana centre **is** the gong + morning-doha appliance: offline schedule, Media3 audio, on-device UI — behavioural parity with the Pi daemon.

---

## 2. Design overview (what the product is supposed to be)

### 2.1 Sources (read in this order)

| Priority | Document | Role |
|---|---|---|
| 1 | **The unit-test suite** (`app/src/test/`, ports of the Pi daemon) | **Behavioural law** — wins all schedule/play conflicts |
| 2 | `docs/handoff/` (design handoff + HTML design doc + screens) | Product UX, tablet landscape, Nocturne UI, alarm choice |
| 3 | `docs/ANDROID-APP-IMPLEMENTATION-PROMPT.md` | Milestones M0–M7, engineering rules |
| 4 | *(removed — see git history)* | Original design-agent brief |
| 5 | *(removed — see git history)* | Pi appliance architecture reference |
| 6 | `PROGRESS.md` | Living build status |

### 2.2 Locked product decisions

| Decision | Choice |
|---|---|
| Shape | **Appliance** (device = scheduler + speaker / BT / USB DAC) |
| Offline | Required for course operation |
| Trust boundary | Centre tablet + optional Wi‑Fi; PIN for config (specified, not built) |
| Deshna jukebox | **Out of MVP** |
| Amp power (smart plug / relay) | Future; `relay_enabled` retained inert |
| Distribution | Play later; debug APK + synthetic doha tones for now |

### 2.3 Scheduling law (must not regress)

1. **Course window**, not “starts today only” (`ActiveCourse`).
2. **Calendar day** (`ChronoUnit.DAYS`) — never `/86400`.
3. Materialize: explicit `day_no` → default `day_no NULL` → no-course (`course_type_id NULL`).
4. Fire window: never early; **120 s grace**; else **`missed`** (no late blast).
5. Double-fire: persist `fired:<key>:<date>` **before** play dispatch.
6. Untrusted clock → suppress automatic plays (`skipped_clock` / silent tick).
7. Doha slots: `legacy_modular` when `0 < day ≤ total_days`; else `no_course_doha`.
8. Player queue: new gong aborts gong; doha waits; stop clears all.

### 2.4 Target UX (design handoff)

- Landscape tablet ~1280×800; left **nav rail**; Nocturne theme.
- Screens **designed + built:** Dashboard, Courses, Schedule grid, Logs.
- Screens **nav stubs only (locked):** Sounds, Audio out, Time, Network, Setup.
- PIN required flags exist on tabs but **gate is not enforced**.

---

## 3. Plan overview (milestones)

| M | Intent | Status |
|---|---|---|
| **M0** | Pure Kotlin domain + unit tests | **Done** |
| **M1** | Room + Pi-shaped schema + seed | **Done** |
| **M2** | PlayerEngine + Media3 + FGS | **Done** |
| **M3** | SchedulerEngine + `setAlarmClock` + 30 s heartbeat | **Done** |
| **M4** | Compose UI (4 screens) | **Done** |
| **M5** | PIN + five undesigned screens (design first) | **PIN done** (app-open gate, 2026-08-08); screens not started |
| **M6** | Backup/restore, audio route picker, doha SAF, first-run | **Not started** |
| **M7** | Play hardening (battery, R8, privacy, media pack docs) | **Not started** |

**Conflict resolutions already applied:**

- **minSdk 29** (handoff), not 26 (impl prompt).
- **`setAlarmClock`** (handoff §03), not only `setExactAndAllowWhileIdle`.
- Seed has **12** course types; UI “No course” is not a type row.

---

## 4. Implementation map (where code lives)

```
android/app/src/main/java/org/dhamma/gong/
├── domain/          # pure JVM — SchedulerCore, FireRules, ClockTrust, GongClock,
│                    # ActiveCourse, DohaSlots, ScheduleMaterializer, Models
├── data/            # Room entities/DAOs, GongDatabase, GongRepository, SeedLoader
├── player/          # PlayerEngine, AudioSink/ExoAudioSink, AudioRouter, MediaResolver
├── schedule/        # SchedulerEngine, AlarmScheduler
├── service/         # GongService (FGS), BootReceiver, TimeChangeReceiver
└── ui/              # MainActivity, GongApp, Theme, Dashboard/Courses/Schedule/Logs, VM
```

**Process model:** `GongService` ≈ Pi `gongd`. UI is a **client**; closing the activity must not stop scheduling.

**Data:** Room file `gong.db` (WAL). Seed: `assets/seed/seed.json` → 12 types, 335 events (idempotent).

**Audio:** `assets/media/gongs/{ting,drum}.mp3`. Debug doha synthetics under `src/debug/assets/media/doha-test/`. Release has empty `media_slots` until SAF pack (M6).

---

## 5. Verification evidence (this review session)

| Check | Result |
|---|---|
| `./gradlew :app:testDebugUnitTest` | **BUILD SUCCESSFUL** (110 tests, previously green; up-to-date) |
| `./gradlew :app:assembleDebug` | **OK** |
| AVD `cca34` factory wipe (`-wipe-data`) | **OK** |
| `adb install` debug APK | **Success** |
| Launch `MainActivity` | **OK** |
| Log: `SeedLoader: seeded 12 course types, 335 schedule events` | **OK** |
| `GongService` foreground | **OK** (`types=mediaPlayback`) |
| `AlarmScheduler: armed for … 21:00` | **OK** (device TZ was `America/Los_Angeles`) |
| Overnight real tablet / OEM battery | **Not tested** (top residual risk) |
| 1280×800 landscape tablet AVD | **Not used** — `cca34` is phone-shaped Pixel 6 |

Prior Opus session also ran a live fire (~75 s ahead → `play_log` `gong|ting.mp3|3|ok` + `missed` for earlier slots). That was not re-run in this wipe pass; smoke here confirms clean boot + seed + arm.

---

## 6. Code review — bugs & risks

Severity: **P0** ship-blocker · **P1** wrong course behaviour · **P2** UX/ops · **P3** polish.

### P1 — semantic / reliability

| ID | Issue | Where | Notes / fix direction |
|---|---|---|---|
| **B1** | ~~**Timezone is only `ZoneId.systemDefault()`**~~ **FIXED 2026-08-08** | `domain/ApplianceZone.kt`, `GongService` | Clock zone now comes from the `timezone` setting (default `Asia/Kolkata`, Pi-daemon config parity); blank/invalid → IST. `SystemGongClock` takes a zone provider; re-read on every poke. UI follows. `ApplianceZoneTest`. |
| **B2** | ~~**`applyOutcome` is not a single DB transaction**~~ **FIXED 2026-08-08** | `GongRepository.applyOutcome` | `db.withTransaction` wraps marks+logs; `ApplyOutcomeTransactionTest` proves rollback. |
| **B3** | ~~**Gong burst gap is start-to-start**~~ **FIXED 2026-08-08** | `PlayerEngine.execute` | Pi parity (user-confirmed): strike plays to the end, then `gap_seconds` of silence. `elapsedMs` injection removed. |
| **B4** | ~~**Day 0 doha uses `no_course_doha`**~~ **CLOSED 2026-08-08** | `DohaSlots.pickSlot` | Product decision confirmed: keep Pi behaviour — day 0 uses the `no_course_doha` setting. Not a bug. |

### P2 — product / security / ops

| ID | Issue | Where | Notes |
|---|---|---|---|
| **B5** | ~~**PIN not enforced**~~ **FIXED 2026-08-08** | `domain/PinCode.kt`, `ui/PinScreens.kt`, `GongApp` | App-open PIN gate (salted PBKDF2 in `admin_pin_hash`); set/change/remove from the in-app PIN tab. `PinCodeTest`. |
| **B6** | ~~**No first-run for exact alarms / battery optimization**~~ **FIXED 2026-08-09** | `service/AppliancePermissions.kt`, Dashboard health card, `MainActivity` | Runtime notification request on open; health rows for exact alarms / battery / notifications open the matching system settings; status re-checked on every `ON_RESUME`. Without exact alarms still falls back to inexact + 30 s heartbeat. |
| **B7** | ~~**`LOCKED_BOOT_COMPLETED` starts service**~~ **FIXED 2026-08-08** | manifest | Action removed; service starts only after first unlock (`BOOT_COMPLETED` / `MY_PACKAGE_REPLACED`). |
| **B8** | ~~**Overlapping courses both painted ACTIVE**~~ **FIXED 2026-08-08** | `AppViewModel.courseRows` | New `OVERLAP` status (amber); exactly one course paints ACTIVE. Overlap warning now keys off it. |
| **B9** | ~~**Auto-chosen active course not persisted**~~ **FIXED 2026-08-09** | `SchedulerEngine.tick` | Writes resolved `active_course_id` (clears when no window); honours a staff pin when two windows overlap. Covered by three `SchedulerEngineTest` cases. |
| **B10** | **No backup/restore** | M6 | Field recovery requires re-seed + re-enter courses. |
| **B11** | **Release builds have no doha media** | intentional | Dashboard shows **GONGS ONLY** chip when `media_slots` is empty. |

### P3 — polish / test gaps

| ID | Issue | Notes |
|---|---|---|
| **B12** | Phone emulator ≠ 1280×800 tablet | Layout regressions possible on real 10" device. Screenshots taken at 1280×800 via `wm`. |
| **B13** | ~~`onDestroy` `runBlocking` player release~~ **FIXED 2026-08-08** — was a real deadlock (`runBlocking` on main + `withContext(Main)` in the sink). Scope cancelled first; `release()` skips Room writes; sink on `Main.immediate`. |
| **B14** | ~~Notification / FGS without POST_NOTIFICATIONS on API 34~~ **FIXED 2026-08-09** (with B6) | Runtime request + health-card deep link into app notification settings. |
| **B15** | No instrumented UI tests | Compose crash (weight/scroll) was found manually once. |

### What looks solid

- Domain purity + large unit surface (grace, missed, double-fire, clock trust, doha golden tables, 400-day ledger).
- Guard-before-dispatch discipline in `SchedulerEngine.tick`.
- Seed idempotence and corrupt-row null-mapping (`toDomain()`).
- Service owns player+scheduler; UI pokes via service.
- Heartbeat + alarm belt-and-braces.
- Queue preemption rules match the Pi daemon's intent.

---

## 7. How to run / reinstall (for reviewers)

```bash
cd /Users/wizops/DIPI/niyam   # standalone repo kapaggar/niyam

# Tests + APK
./gradlew :app:testDebugUnitTest :app:assembleDebug

# Emulator factory reset + boot
export ANDROID_HOME=/Users/wizops/Android/Sdk
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"
adb emu kill 2>/dev/null || true
emulator -avd cca34 -wipe-data -no-snapshot-load -no-snapshot-save &
adb wait-for-device
# wait until:
adb shell getprop sys.boot_completed   # → 1

# Install + launch
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell pm grant org.dhamma.gong android.permission.POST_NOTIFICATIONS
adb shell am start -n org.dhamma.gong/.ui.MainActivity

# Logs
adb logcat -s SeedLoader:I AlarmScheduler:I SchedulerEngine:I GongService:I PlayerEngine:I
```

**Manual smoke (recommended for humans):**

1. Confirm dashboard shows **No course** (fresh wipe).
2. **Courses** → add **10 Day**, start = today → dashboard **Day 0**.
3. **Schedule** → inspect day 0 / DEF columns; optional add event +2 min.
4. **Test gong** (4 strikes) — hear audio; rings if UI wired.
5. **Logs** — `test_gong` / later `gong` / `missed` rows.
6. Force-stop app → reopen → notification / service should return (START_STICKY + start from activity).

---

## 8. Suggested work for Fable (ordered)

### Immediate hardening (before field tablet)

1. **B1** Appliance timezone setting (default `Asia/Kolkata` or settings-driven `GongClock`).
2. ~~**B2**~~ done — `db.withTransaction`.
3. ~~**B3**~~ done — Pi parity chosen and implemented.
4. ~~**B7**~~ done — action dropped.
5. Confirm **GONGS ONLY** / missing doha UX is explicit on Dashboard.

### M5 (product)

6. ~~PIN gate~~ done — app-open gate, salted PBKDF2 (`PinCode`).
7. Design + implement: Sounds, Audio out, Time (clock confirm!), Network, Setup checklist.

### M6 (field ops)

8. Backup/restore of `gong.db` (SAF).
9. Doha media pack via SAF + `media_slots` mapping by `D0x` prefix.
10. Audio route picker (speaker / BT).
11. First-run: notifications, exact alarms, battery unrestricted.

### M7 / hardware truth

12. Real tablet overnight run (design’s top risk).
13. Tablet AVD or device at 1280×800 landscape for UI sign-off.
14. Play policy package (exact alarm justification, privacy stub).

### Tests to add when touching code

- Instrumented: seed → add course → inject near-term event → assert `play_log` result.
- ~~Domain: gap-after-play if B3 fixed.~~ done (`gapIsCountedAfterTheStrikeEnds`).
- Repository: crash-safe transaction of marks+logs.

---

## 9. Agent rules when improving the app

1. **Do not invent schedule semantics** — port the Pi behaviour; add a unit test that fails first.
2. **One milestone / one concern per PR** when possible.
3. Keep `domain/` free of Android imports.
4. After behaviour change: `./gradlew :app:testDebugUnitTest`.
5. Do not commit `local.properties`, keystores, or real doha masters.
6. Update `PROGRESS.md` + this file’s “Last verified” when shipping a milestone.
7. Deshna server stays out until gong/doha MVP is field-stable.

---

## 10. Last verified (this session)

| Item | Value |
|---|---|
| Branch tip (authoring) | `android-app` / `47f29b5` (PROGRESS M4) — re-verify with `git log -1` |
| Unit tests | Green (110) |
| Emulator | `cca34` wiped, APK installed, app + FGS running |
| Seed on device | 12 types / 335 events logged |
| Next alarm at wipe boot | 21:00 local (`America/Los_Angeles` on that AVD) |

---

## 11. Kickoff prompt for Fable

```text
Read android/docs/FABLE-REVIEW.md and android/PROGRESS.md end-to-end.
Behavioural law: the unit tests and domain ports in app/src/main/java/org/dhamma/gong/domain/.
Pick one P1 from §6 (prefer B1 timezone or B2 transactional applyOutcome) OR implement M5 PIN gate.
Keep domain pure; add unit tests; run ./gradlew :app:testDebugUnitTest.
Do not start Deshna. Update PROGRESS.md when done.
```

---

*End of Fable review handoff.*
