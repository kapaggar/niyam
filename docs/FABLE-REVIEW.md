# Fable review handoff — Gong Android appliance (M0–M4)

**Date:** 2026-08-08  
**Branch:** `android-app` @ gongserver monorepo (`android/`)  
**Reviewer pass:** post–Opus build code review + factory-reset emulator reinstall  
**APK:** `android/app/build/outputs/apk/debug/app-debug.apk` (v`0.1.0-mvp`, minSdk 29)  
**Emulator for manual review:** AVD **`cca34`** (API 34), wiped + reinstalled this session  

This document is the single restart surface for **Fable** (or any follow-on agent) to understand **design → plan → implementation**, what works, what is wrong, and what to build next.

---

## 1. One-sentence product

> A phone/tablet left on charge at a Vipassana centre **is** the gong + morning-doha appliance: offline schedule, Media3 audio, on-device UI — behavioural parity with **Gong-NG** on Raspberry Pi.

---

## 2. Design overview (what the product is supposed to be)

### 2.1 Sources (read in this order)

| Priority | Document | Role |
|---|---|---|
| 1 | **Gong-NG Python** `../ng/gong_ng/{model,scheduler,doha,player,clock}.py` | **Behavioural law** — wins all schedule/play conflicts |
| 2 | `docs/handoff/` (design handoff + HTML design doc + screens) | Product UX, tablet landscape, Nocturne UI, alarm choice |
| 3 | `docs/ANDROID-APP-IMPLEMENTATION-PROMPT.md` | Milestones M0–M7, engineering rules |
| 4 | `docs/ANDROID-APP-DESIGN-PROMPT.md` | Original design-agent brief |
| 5 | `../docs/GONG-NG-DESIGN.md` | Pi appliance architecture (reference) |
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
| **M1** | Room + NG-shaped schema + seed | **Done** |
| **M2** | PlayerEngine + Media3 + FGS | **Done** |
| **M3** | SchedulerEngine + `setAlarmClock` + 30 s heartbeat | **Done** |
| **M4** | Compose UI (4 screens) | **Done** |
| **M5** | PIN + five undesigned screens (design first) | **Not started** |
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
| **B1** | ~~**Timezone is only `ZoneId.systemDefault()`**~~ **FIXED 2026-08-08** | `domain/ApplianceZone.kt`, `GongService` | Clock zone now comes from the `timezone` setting (default `Asia/Kolkata`, NG `config.py` parity); blank/invalid → IST. `SystemGongClock` takes a zone provider; re-read on every poke. UI follows. `ApplianceZoneTest`. |
| **B2** | **`applyOutcome` is not a single DB transaction** | `GongRepository.applyOutcome` | Guards then logs as separate Room ops. Crash between can leave marks without log (or partial marks). Prefer `@Transaction` wrapping put+insert. |
| **B3** | **Gong burst gap is start-to-start, not end-to-gap** | `PlayerEngine.execute` | Absolute deadlines from burst start: if strike audio **longer** than `gap_seconds`, strikes overlap / zero silence. NG waits for play **then** gap. Align with NG or document as deliberate. |
| **B4** | **Day 0 doha uses `no_course_doha` (often random)** | `DohaSlots.pickSlot` (`day > 0`) | Matches NG (`0 < day`). Confirm with centres whether arrival morning should use modular day-1 or off. Not a code bug vs NG; product clarity needed. |

### P2 — product / security / ops

| ID | Issue | Where | Notes |
|---|---|---|---|
| **B5** | **PIN not enforced** | `Tab.requiresPin`, `admin_pin_hash` | Courses/Schedule editable by anyone who unlocks the tablet. M5. |
| **B6** | **No first-run for exact alarms / battery optimization** | Manifest has perms; UI Setup locked | Without `canScheduleExactAlarms()`, falls back to inexact + 30 s heartbeat (may still work; later on some OEMs). |
| **B7** | **`LOCKED_BOOT_COMPLETED` starts service** | `BootReceiver` | DB is credential-encrypted default; early direct-boot start can fail until unlock. Prefer only `BOOT_COMPLETED` or device-protected context. |
| **B8** | **Overlapping courses both painted ACTIVE** | `AppViewModel.courseRows` `else -> ACTIVE` | Drives overlap warning; can confuse staff. Prefer ACTIVE only for resolved course + OVERLAP badge for others. |
| **B9** | **Auto-chosen active course not persisted** | NG writes `active_course_id` on resolve | Android only reads pin. Overlap set can flip if dates change. Minor. |
| **B10** | **No backup/restore** | M6 | Field recovery requires re-seed + re-enter courses. |
| **B11** | **Release builds have no doha media** | intentional | Dashboard must clearly show **GONGS ONLY**; verify copy is obvious. |

### P3 — polish / test gaps

| ID | Issue | Notes |
|---|---|---|
| **B12** | Phone emulator ≠ 1280×800 tablet | Layout regressions possible on real 10" device. |
| **B13** | `onDestroy` `runBlocking` player release | Low risk ANR path if service torn down on main. |
| **B14** | Notification / FGS without POST_NOTIFICATIONS on API 34 | We granted via adb; real first-run needs UX. |
| **B15** | No instrumented UI tests | Compose crash (weight/scroll) was found manually once. |

### What looks solid

- Domain purity + large unit surface (grace, missed, double-fire, clock trust, doha golden tables, 400-day ledger).
- Guard-before-dispatch discipline in `SchedulerEngine.tick`.
- Seed idempotence and corrupt-row null-mapping (`toDomain()`).
- Service owns player+scheduler; UI pokes via service.
- Heartbeat + alarm belt-and-braces.
- Queue preemption rules match NG intent.

---

## 7. How to run / reinstall (for reviewers)

```bash
cd /Users/wizops/gongserver/android   # or the moved android/ git root

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
2. **B2** `@Transaction` on `applyOutcome`.
3. **B3** Decide gap semantics; if NG parity, sleep after each `sink.play` then gap.
4. **B7** Drop `LOCKED_BOOT_COMPLETED` or use device-protected storage.
5. Confirm **GONGS ONLY** / missing doha UX is explicit on Dashboard.

### M5 (product)

6. PIN gate (`admin_pin_hash` + scrypt/Argon2id or Android Keystore + hash).
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
- Domain: gap-after-play if B3 fixed.
- Repository: crash-safe transaction of marks+logs.

---

## 9. Agent rules when improving the app

1. **Do not invent schedule semantics** — port NG; add a unit test that fails first.
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
Behavioural law: ../ng/gong_ng and domain ports in app/src/main/java/org/dhamma/gong/domain/.
Pick one P1 from §6 (prefer B1 timezone or B2 transactional applyOutcome) OR implement M5 PIN gate.
Keep domain pure; add unit tests; run ./gradlew :app:testDebugUnitTest.
Do not start Deshna. Update PROGRESS.md when done.
```

---

*End of Fable review handoff.*
