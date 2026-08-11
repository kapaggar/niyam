# Claude Code prompt — next iteration: Fable gaps + screen refinement

**Date:** 2026-08-11  
**Worktree (implement here):** `/Users/wizops/DIPI/niyam`  
**Fable reference (read-only):** `/Users/wizops/Downloads/niyam`  

Paste this entire document into Claude Code (or Cursor agent) at the start of the session.

---

## Mission

You are implementing the **next iteration** of **Niyam** on the **already-under-development** tree:

```
/Users/wizops/DIPI/niyam
```

A fresh Fable design+implementation landed as a parallel reference export:

```
/Users/wizops/Downloads/niyam
```

**Do not replace DIPI/niyam with the Fable tree.** Treat Fable as:

1. A **UX and reliability checklist** (what staff should see and what keeps the service alive).
2. A **source of missing vertical features** (full Sounds panel, liveness kickstart, READY banner, unmatched files, etc.).
3. **Not** the behavioural source of truth for schedule math when DIPI already has verified ports + tests.

Your job: **port Fable’s useful gaps into DIPI**, refine screens so they feel as clear as Fable’s 9-screen app, and keep DIPI’s strengths (Shelly amp power, CDN doha download, verified `DohaSlots`, Room tests, Nocturne handoff chrome).

---

## How to run this session

```
cd /Users/wizops/DIPI/niyam
# Read these first:
#   docs/superpowers/plans/2026-08-11-fable-gap-iteration-claude-prompt.md  (this file)
#   AGENTS.md
#   PROGRESS.md
#   docs/NIYAM-DESIGN-AND-IMPLEMENTATION-PROMPT.md  (three rules)
# Then skim Fable:
#   /Users/wizops/Downloads/niyam/README.md
#   /Users/wizops/Downloads/niyam/docs/PROGRESS.md
#   /Users/wizops/Downloads/niyam/docs/ASSUMPTIONS.md
#   /Users/wizops/Downloads/niyam/docs/BETA-QA-CHECKLIST.md
#   /Users/wizops/Downloads/niyam/app/src/main/java/org/dhamma/gong/ui/screens/*
```

Implement in **small vertical slices**. After each slice:

```bash
./gradlew :app:testDebugUnitTest
```

Update `PROGRESS.md` when a slice ships. Prefer evidence over claims.

---

## Three rules (never regress)

1. **Silence beats a wrong gong** — untrusted clock suppresses automatic fire.  
2. **Never early, never twice, never late-and-loud** — 120 s grace; fired-guard **before** sound.  
3. **UI is a client** — `GongService` owns scheduler + player (+ relay). Closing the activity changes nothing.

Do **not** change `FireRules` / `SchedulerCore` / `DohaSlots.legacyModular` without a **failing unit test first**. DIPI’s `DohaSlots` is the verified PHP-port algorithm (anapana cycles). Fable’s simpler `floorMod(day-1, 9)` is an **assumption** — **do not adopt it**.

---

## Source-of-truth matrix

| Concern | Winner | Why |
|---------|--------|-----|
| Course day math, fire/grace, clock trust | **DIPI tests + domain** | Verified against Pi/PHP; larger suite |
| Doha slot in-course mapping | **DIPI `DohaSlots.legacyModular`** | Anapana-aware; Fable is best-guess |
| SAF pack map rules (conflict, manual, bundled) | **DIPI `DohaPackMapper` + specs** | Already amended for major review items |
| Shelly amp power | **DIPI only** | Fable left relay inert |
| CDN download + decrypt | **DIPI only** | Fable: SAF only |
| Full Sounds UX (volumes, gap, track, doha time, no_course) | **Fable `Sounds.kt`** | DIPI Sounds tab is still mostly slot mapping |
| Service liveness / OEM kill recovery | **Fable `LivenessWorker` + kickstart** | DIPI may lack 15 min WorkManager re-kick |
| Setup READY / NOT READY banner | **Fable `Setup.kt`** | Clear field checklist |
| Dashboard health chips (service alive, grants) | **Fable `Dashboard.kt`** | Instant glanceability |
| Audio out honesty (present / NOT PRESENT, live list) | **Fable `AudioOut.kt`** | DIPI has route; refine clarity |
| Visual system | **DIPI Nocturne + handoff** | Keep DIPI chrome; steal Fable *information*, not Material3 default skins |
| Seed depth (39 courses etc.) | Compare carefully | Prefer DIPI seed + Fable’s convert script ideas if missing courses |

---

## Gap inventory (implement these)

### P0 — Reliability (hall at 04:00)

#### G1. Liveness kickstart (from Fable)

Fable: `service/LivenessWorker.kt` + `AlarmScheduler.armKickstart` + `BootReceiver` dual path.

Port into DIPI:

1. **Periodic WorkManager** (~15 min unique work) that, if `GongService` is not running, arms a **near-term exact kickstart alarm** (Worker cannot always start FGS on modern Android).
2. **AlarmReceiver / kickstart** path that **is** allowed to start the `mediaPlayback` FGS.
3. **BootReceiver**: try direct `GongService.start`; also arm kickstart (~3 s) so API 34+ boot restrictions do not leave the appliance dead.
4. Setup/Dashboard show **service alive** when last scheduler tick age &lt; ~90 s (Fable uses 90_000 ms).

Do not remove the existing 30 s in-service heartbeat or `setAlarmClock` next-fire arming — kickstart is a **third** belt.

Tests: unit-test whatever pure bits you extract; instrumented optional. Document manual QA: kill from recents, reboot, confirm notification returns.

#### G2. Suppression clarity (only if DIPI is weaker)

Fable ASSUMPTIONS #5:

- Kind-disabled → mark `skipped_disabled` so re-enabling mid-window does **not** fire a stale event.
- Master-off / clock-untrusted → suppress **without** writing fire/miss marks as appropriate; untrusted → log-only, no new guards.

Audit DIPI `SchedulerCore` / engine against this. **If DIPI already matches tests, leave it.** If a gap is real, add a failing test then fix — do not paste Fable’s domain wholesale.

#### G3. Deterministic `no_course_doha = random` (small, high value)

Fable: `floorMod(epochDay * 31 + 7, 11) + 1` so re-materializing the same day always picks the same slot.

DIPI today: `Random.Default` in `DohaSlots.pickSlot` — **non-deterministic**, bad for materializer stability.

**Change DIPI** to deterministic-per-date random (pass `LocalDate` or epoch day into pick path). Keep `off` and `slot:n`. Update unit tests.

---

### P1 — Sounds screen completion (biggest UX gap)

DIPI `Tab.SOUNDS` → `DohaMediaScreen` is **slot mapping + downloads only**. Comments admit track/volume/gap/doha time/no-course are unbuilt.

Fable `Sounds.kt` is the product-complete Sounds panel:

| Section | Controls |
|---------|----------|
| Gong | track radio (`ting`/`drum`), gap dropdown, gong volume slider, Test gong |
| Doha schedule | `doha_time`, between-courses mode (`random` / fixed slot), doha volume, Test doha |
| Doha audio files | Choose folder, Rescan, 11-row table, per-slot Pick/Clear, unmatched list, GONGS ONLY banner |

**Implement in DIPI** (prefer one screen or clear sections under Sounds):

1. **Keep** existing SAF/folder/rescan/conflict/manual/bundled rules and CDN download section if already on that screen.
2. **Add** gong track + gap + volumes + doha time + `no_course_doha` UI wired to existing settings keys (`gong_track`, `gong_gap_seconds`, `gong_volume`, `doha_volume`, `doha_time`, `no_course_doha`).
3. **Unmatched files** list (in folder but not `D01`…`D11`) like Fable.
4. Slot roles for staff: e.g. D10 = metta day, D11 = homage — labels only, **do not** change mapping algorithm.
5. Volume scale: match **existing DIPI player** convention (do not invent 0–1 vs 0–100 without reading `PlayerEngine` / settings defaults). If Fable uses 0–1 float and DIPI uses 0–100 integers, **keep DIPI’s stored format** and map the UI.
6. Stay on **Nocturne** components (`SurfaceCard`, `Tag`, 44 dp targets) — do not switch the whole app to Fable’s Material3 SectionCard skin.

Partial acceptance:

- Staff can set volumes and gap without leaving Sounds.
- Folder map still passes prior doha SAF acceptance.
- `./gradlew :app:testDebugUnitTest` green.

---

### P1 — Dashboard refinement

Steal Fable **information architecture**, keep DIPI layout/branding:

1. **CLOCK UNTRUSTED** banner → points to Time screen confirm (if not already).
2. **Health chips/row**: clock · exact alarms · battery · **service alive** (tick age) · doha `n/11` / GONGS ONLY.
3. **Next** block: countdown + short list of upcoming (DIPI may already have this — align labels with Fable clarity: `gong xN` / `doha D0n`).
4. Master toggles: Master / Gong / Doha (+ Relay when host configured). Consider **PIN-session gate** for toggles when a PIN is set (Fable: unlock any PIN screen first) — match existing DIPI PIN session model; do not invent a second auth system.
5. Test gong / Test doha / Stop remain obvious; ≥44 dp.

---

### P1 — Setup READY banner

Fable Setup: single **READY / NOT READY** banner when exact alarms + battery + notifications + service alive + PIN are all good; rows re-poll every ~1 s after user returns from system Settings.

DIPI Setup already has grant rows — add:

1. Aggregate **READY / NOT READY** banner at top.
2. Live re-check (ticker) so grants flip without force-reopen.
3. Heartbeat / last tick age text (Fable QA: never exceed ~35 s when healthy).
4. Keep media-key / other DIPI-specific rows (CDN) if present.

---

### P1 — Audio out refinement

Fable `AudioOut.kt`:

- Preferred route radios with **present / NOT PRESENT**.
- Live device list poll (~2 s).
- Last successful preferred-route timestamp.
- “Test on this route” + copy that **missing route falls back to speaker** (silence is worse).
- Note warm-up ~15 s before fire (already in DIPI scheduler).

Refine DIPI `AudioOutScreen` to match this honesty without deleting real routing work already done (`RoutePlan`, ExoPlayer device steering).

---

### P2 — Polish / secondary

1. **Network** — keep informational; ensure IP/SSID/hostname readable for phone support (Fable is thin; DIPI `NetworkScreen` may already be richer).
2. **Schedule** — preserve DIPI inherit-row / two-tap override fixes; use Fable only if a clearer editor pattern is missing.
3. **Courses OVERLAP** — both trees warn; ensure DIPI Dashboard + Courses still show OVERLAP when two windows cover today.
4. **Amp power** — Fable has no UI; **leave DIPI Shelly as-is** unless a Dashboard chip for relay reachability is an easy win.
5. **QA checklist** — merge useful Fable `docs/BETA-QA-CHECKLIST.md` items into DIPI `docs/BETA-QA-CHECKLIST.md` (reboot, kill-from-recents, power-across-fire, untrusted clock, longevity 48 h). Do not delete DIPI-specific Shelly/CDN checks.
6. **Seed** — if Fable seed has more courses and DIPI seed is missing them, port data via existing `SeedLoader` / convert tools carefully; never wipe centre DB on upgrade.

---

## Explicit non-goals this iteration

- Rewriting domain to Fable’s simpler `DohaSlots` / millis-based model.
- Deleting Shelly, CDN, or Nocturne handoff styling.
- Replacing Room schema wholesale.
- BLE Shelly provisioning, Shelly Cloud, Deshna server.
- Committing copyrighted doha audio or secrets (`media.properties` stays local).
- Full redesign of Schedule grid unless a clear bug is found.

---

## Implementation order (recommended)

| Order | Slice | Done when |
|------:|-------|-----------|
| 1 | G3 deterministic no-course random | Tests updated; same day → same slot |
| 2 | G1 LivenessWorker + boot kickstart | Code + PROGRESS note; manual QA listed |
| 3 | Sounds: volumes / gap / track / doha time / no_course | Wired to settings; Test buttons work |
| 4 | Sounds: unmatched list + GONGS ONLY banner polish | Matches Fable clarity |
| 5 | Dashboard health chips + untrusted banner | Glanceable grants + service alive |
| 6 | Setup READY banner + live recheck | READY only when all grants + alive + PIN |
| 7 | Audio out present/list/test copy | Honesty copy + live devices |
| 8 | QA checklist merge | `docs/BETA-QA-CHECKLIST.md` updated |
| 9 | `./gradlew :app:testDebugUnitTest` + `assembleDebug` | Green; APK path noted in PROGRESS |

Bump versionCode / versionName only if project convention expects it for beta drops (see current `app/build.gradle.kts`).

---

## Fable file map (read these)

```
/Users/wizops/Downloads/niyam/
  README.md
  docs/PROGRESS.md
  docs/ASSUMPTIONS.md
  docs/BETA-QA-CHECKLIST.md
  docs/MEDIA.md
  docs/SECURITY-PRIVACY.md
  app/src/main/java/org/dhamma/gong/
    ui/screens/Dashboard.kt
    ui/screens/Sounds.kt          # primary UX reference for Sounds completion
    ui/screens/Setup.kt           # READY banner
    ui/screens/AudioOut.kt
    ui/screens/Common.kt
    ui/MainActivity.kt            # 9-tab nav
    service/LivenessWorker.kt
    service/Receivers.kt
    service/GongService.kt
    schedule/AlarmScheduler.kt    # kickstart if present
    domain/DohaSlots.kt           # DO NOT port algorithm; only deterministic no-course idea
```

## DIPI file map (edit these)

```
/Users/wizops/DIPI/niyam/
  app/src/main/java/org/dhamma/gong/
    ui/DohaMediaScreen.kt / GongApp.kt   # Sounds tab
    ui/DashboardScreen.kt
    ui/SetupScreen.kt
    ui/AudioOutScreen.kt
    ui/AppViewModel.kt
    service/*  schedule/*
    domain/DohaSlots.kt                  # deterministic random only
  docs/BETA-QA-CHECKLIST.md
  PROGRESS.md
```

---

## Definition of done (this iteration)

1. `./gradlew :app:testDebugUnitTest` green.  
2. Sounds is no longer “mapping only” — volumes, gap, track, doha time, no-course are staff-editable.  
3. Service has a **third** keep-alive path (WorkManager → kickstart alarm) documented in PROGRESS.  
4. Setup shows **READY / NOT READY** honestly.  
5. Dashboard shows **service alive** (or equivalent heartbeat age).  
6. No regression to fire/double-fire/clock-trust tests.  
7. `PROGRESS.md` updated with version, slices shipped, and residual risks (BT amp latency, real Shelly if still untested).  
8. Do **not** claim field-ready without listing remaining hardware QA.

---

## Session starter (short)

```
Work in /Users/wizops/DIPI/niyam only.
Reference UX/reliability from /Users/wizops/Downloads/niyam (Fable) — read-only.
Implement gaps from docs/superpowers/plans/2026-08-11-fable-gap-iteration-claude-prompt.md
in order G3 → G1 → Sounds → Dashboard → Setup → Audio out → QA merge.
Do not replace DohaSlots.legacyModular with Fable’s simple modular.
Keep Shelly + CDN + Nocturne.
Run ./gradlew :app:testDebugUnitTest before claiming done.
Update PROGRESS.md.
```

---

*End of prompt. The gong is the product; Fable’s gift is clearer staff surfaces and harder-to-kill service life.*
