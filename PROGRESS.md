# Gong Android appliance — build progress & restart point

Working branch: **`android-app`** (off `gong-ng`). Everything lives in `android/`.

Last updated: 2026-08-08, end of M3.

---

## How to resume

```bash
cd /Users/wizops/gongserver/android
./gradlew :app:testDebugUnitTest     # 110 tests, all green
./gradlew :app:assembleDebug         # app/build/outputs/apk/debug/app-debug.apk
```

`local.properties` (gitignored) must contain `sdk.dir=/Users/wizops/Android/Sdk`.
Environment used: JDK 20, AGP 8.7.2, Kotlin 2.0.21, Gradle 8.9, compileSdk 35
(auto-downloaded on first build), minSdk 29.

**Next milestone: M4 — Compose UI.** See "What's next" below.

---

## Sources of truth (read these before changing behaviour)

| What | Where |
|---|---|
| Milestones & engineering rules | `docs/ANDROID-APP-IMPLEMENTATION-PROMPT.md` (repo root `docs/`) |
| Product design + screen specs | `android/docs/handoff/README.md` |
| Engineering design doc | `android/docs/handoff/Gong Appliance Design Doc.dc.html` (open in a browser) |
| Interactive hi-fi prototype | `android/docs/handoff/Gong Appliance Screens.dc.html` |
| **Behavioural spec** | `ng/gong_ng/{model,scheduler,doha,player,clock}.py` — **NG wins any conflict** |

Conflicts resolved so far:
- **minSdk 29**, not the prompt's 26 — design doc §03 (uniform SAF, dependable
  LocalOnlyHotspot). Recorded in `app/build.gradle.kts`.
- **`setAlarmClock`**, not `setExactAndAllowWhileIdle` — design doc §03.
- Course types are **12** in the seed; the handoff's "13" counts the UI's
  "No course" row, which is `course_type_id IS NULL`, not a DB row.

---

## Shipped

### M0 — pure domain (`app/src/main/java/org/dhamma/gong/domain/`)
Framework-free, runs on the JVM.

| File | Role |
|---|---|
| `SchedulerCore.kt` | `tick()` — port of `scheduler.py` tick+dispatch. Pure: returns a `TickOutcome` (marks, logs, commands, nextDeadline). |
| `FireRules.kt` | The single fire-window decision point. Grace 120 s. |
| `ClockTrust.kt` | Port of `clock.py`: backwards-jump detect, NTP-step recovery, staff confirm. |
| `GongClock.kt` | `SystemGongClock` / `VirtualClock`; `materialize()` carries the DST rules. |
| `ScheduleMaterializer.kt` | Day precedence: explicit day → `day_no NULL` default → no-course set. |
| `ActiveCourse.kt` | Window match, most-recent-start wins, pinned override. |
| `DohaSlots.kt` | `legacy_modular` verbatim + `no_course_doha` modes. |
| `Models.kt` | Entities, `PlayCommand`, `PlayLogEntry`, `FiredMark`, `SettingsDefaults`. |

### M1 — Room store (`data/`)
Column names identical to `ng/gong_ng/db.py` so a pulled DB stays NG-readable.
Entities: `course_types`, `courses`, `schedule_events`, `settings`, `state`,
`play_log`, **`media_slots`** (the Android delta — SAF URIs, not paths).
`GongRepository` is the only Room↔domain bridge. `SeedLoader` is idempotent and
backfills missing settings on every launch.

### M2 — player + service (`player/`, `service/`)
`PlayerEngine` carries NG's queue rules (new gong aborts a running gong; doha
waits; stop drops everything and logs it). `AudioSink` splits "render once" out
so the rules are testable without an audio device; `ExoAudioSink` is Media3 with
`USAGE_MEDIA` + `CONTENT_TYPE_SONIFICATION`. `AudioRouter` falls back to the
speaker and records `state.route_last_ok`. `GongService` is the foreground
`mediaPlayback` service that owns everything.

### M3 — scheduler loop (`schedule/`)
`SchedulerEngine.tick()` + `AlarmScheduler`. `setAlarmClock` for the next
occurrence **and** a 30 s heartbeat. Guard committed before dispatch. Prune +
log-trim on day rollover. Route warm-up 15 s ahead.

---

## Parity checklist vs Gong-NG

| Behaviour | NG | Android | Covered by |
|---|---|---|---|
| Calendar `current_day` (never `/86400`) | yes | **yes** | `DstAndDayMathTest` |
| Course window, not start-day only | yes | **yes** | `ActiveCourseTest` |
| Default day pattern fallback | yes | **yes** | `ScheduleMaterializerTest` |
| Grace 120 s + `missed` | yes | **yes** | `SchedulerCoreTest`, `SchedulerEngineTest` |
| Double-fire guard, committed before dispatch | yes | **yes** | `SchedulerEngineTest.processDeathMidBurst…` |
| Doha `legacy_modular` | yes | **yes** | `DohaSlotsTest`, `VirtualClockLedgerTest` |
| Queue: gong preempts gong, doha waits | yes | **yes** | `PlayerEngineTest` |
| Stop aborts + logs `stopped` | yes | **yes** | `PlayerEngineTest` |
| Untrusted clock silences auto plays | yes | **yes** | `ClockTrustTest`, `SchedulerEngineTest` |
| Toggle silences without retro-firing | yes | **yes** | `SchedulerEngineTest` |
| Backup DB | yes | **no** | M6 |
| Schedule editor | yes (web UI) | **no** | M5 |

---

## Test inventory (110)

| Class | N | What it guards |
|---|---|---|
| `SchedulerCoreTest` | 19 | tick semantics, grace edges, toggles, doha resolution |
| `SchedulerEngineTest` | 18 | power cut, process death, reboot, clock jumps, pruning |
| `PlayerEngineTest` | 17 | burst timing, preemption, stop, missing media, route fallback |
| `SeedAndRepositoryTest` | 19 | seed idempotence, NG column parity, guard atomicity, corrupt rows |
| `ClockTrustTest` | 7 | backwards jump, NTP recovery, confirm |
| `DstAndDayMathTest` | 6 | spring-forward gap, fall-back ambiguity, `/86400` regression |
| `ActiveCourseTest` | 5 | window, overlap, pin |
| `ScheduleMaterializerTest` | 5 | day precedence, doha injection |
| `FireRulesTest` | 6 | the window, in isolation |
| `DohaSlotsTest` | 4 | golden slot tables per course type |
| `VirtualClockLedgerTest` | 4 | 400-day ledger over every course type (1.9 s) |

Two test-harness facts worth keeping:
1. Room's own executor is invisible to `runTest`'s scheduler. Any test that
   asserts on work done inside a **launched** coroutine must build the DB with
   `.setQueryExecutor { it.run() }.setTransactionExecutor { it.run() }`.
2. `PlayerEngine` takes an `elapsedMs` lambda so burst timing runs on
   `TestScope.currentTime` (virtual time) instead of `SystemClock`.

---

## What's next

### M4 — Compose UI (per `android/docs/handoff/README.md`)
Target: 1280×800 logical px, 10" tablet, **landscape only**, readable at 2 m.
Nocturne tokens are already transcribed in `ui/Theme.kt`.

- Persistent left **nav rail** (186 dp) + content pane; nav state is the only routing.
- **Dashboard** (no PIN): 98 px mono hero time, 214×3 accent rule, countdown
  recomputed *from seconds*, course card with day-progress segments, health card
  with the amber `GONGS ONLY` chip, two scrolling columns of 6 next events, and
  the 78 px bell button emitting one expanding ring per strike
  (`scale(1)→scale(2.3)`, 0.9→0 opacity, 1 s ease-out, 900 ms apart).
  `PlayerEngine.strikes` already emits exactly one value per strike.
- **Courses** (PIN): add row + table, active row tinted, "Start date is zero day".
- **Schedule** (PIN): the day-column grid, `60px repeat(N, minmax(46px,1fr))`
  where N = `total_days + 1` plus a **DEF** column; inspector aside at 272 px
  with the **em-dash = inherit** option for gap and track. That nullability is
  already load-bearing in the data model — do not flatten it.
- **Logs** (no PIN): UTC timestamps, filter chips, result colouring.
- Toasts on every immediate save; no save buttons anywhere.

Not yet designed (draw as locked nav entries at 42 % opacity with a 🔒):
Sounds, Audio out, Time, Network, Setup checklist, PIN lock.

### M5 — schedule editor writes
Grid cell tap creates a default event (×6, inherit gap, inherit track);
every write must call `GongService.pokeScheduler(...)`.

### M6 — backup, audio route picker, doha SAF folder, first-run wizard
Doha files auto-map by `D01`…`D11` prefix into `media_slots`; unmatched files
are listed as "unassigned", never guessed. Debug builds already ship 11
synthetic tones at `app/src/debug/assets/media/doha-test/`
(regenerate with `python3 tools/make_test_tones.py`).

### M7 — hardening before any Play upload
Battery-optimisation guidance, R8 rules, privacy stub, permission rationale
strings, media-pack install docs.

---

## Known gaps / decisions still open

- **Nothing has run on a real device or emulator.** Every result above is
  `testDebugUnitTest` (JVM + Robolectric) plus `assembleDebug`. The
  overnight-on-a-tablet run in design doc M1 has not happened.
- Release builds ship **no doha audio at all** — `media_slots` is empty until
  staff sideload a pack, and the dashboard is expected to show `GONGS ONLY`.
- `relay_enabled` is retained in settings for NG parity and is inert.
- Design doc §14 open questions are all still open; #1 (which tablet model)
  blocks M1-field-testing in the design doc's own numbering.
