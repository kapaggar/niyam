# Niyam — design & implementation requirements (full agent prompt)

**Product:** Niyam (`org.dhamma.gong`)  
**Shape:** Android centre appliance (wall tablet, on charge, often offline)  
**Repo:** `kapaggar/niyam` (this tree)  
**Audience:** Claude Code / Cursor / Codex / senior Android implementers  

Paste this document at the start of a design or implementation session. Prefer **one milestone slice** per session. Do not redesign fire semantics without a failing unit test first.

---

## 0. Your role

You are an **expert Android application developer** shipping a **field appliance**, not a consumer chat app.

You will **design and implement** the Android app **Niyam** so that a cheap tablet screwed to a wall rings **timely gongs** and plays **morning doha** audio for a Vipassana centre.

**Success is measured in the meditation hall at 04:00**, not in screen polish alone:

- The right sound, once, inside the grace window, even with no internet and no technical staff.
- Wrong, double, or early gongs are worse than a brief silence.
- Closing the UI must not stop the appliance.

Seed data is available (`seed/seed.sql`, `seed/seed.json`, course SQL as provided). UI fidelity lives in `docs/handoff/` (`design_gong_appliance` / Gong Appliance design + screens HTML). Behavioural law lives in `app/src/main/java/org/dhamma/gong/domain/` and the unit tests under `app/src/test/`.

---

## 1. Bird’s-eye product

Niyam turns a cheap Android tablet, left on charge, into the appliance that runs a Vipassana centre’s **gong and morning doha** schedule.

On a course, a bell tells students when to wake (e.g. 04:00), when to sit, when meals are served, when the day rests — a **fixed timetable that varies by course day and by course type**. A recording of the morning **doha** (chanting) plays once daily. Someone has to deliver all of that, **on the second**, for ten days (or longer courses) straight, in a building that may have **no internet** and **no technical staff**.

### 1.1 The three rules everything else serves

Almost every design decision falls out of three commitments. State them when you choose between “clean architecture” and “correct at 04:00”:

1. **Silence beats a wrong gong.**  
   If the clock jumps backwards, the appliance can no longer trust which gongs it has already rung, so it **stops automatic firing** until a human confirms the clock. A centre briefly missing a gong is recoverable; a 04:00 gong at 04:10, or twice, disrupts a hall of a hundred people meditating.

2. **Never early, never twice, never late-and-loud.**  
   A gong fires only inside a **120-second grace window**. Outside it, the event is logged **missed** and stays silent. The **“already fired” guard is written to disk before the sound starts**, so a process killed mid-burst does not re-ring on restart.

3. **The UI is a client, not the appliance.**  
   A **foreground service** owns the scheduler and the player. Closing the app, or the screen going dark, changes nothing. The service must survive reboots and OEM battery killers as far as platform policy allows. The tablet’s casual notion of timezone / “today” is **not** trusted for fire math — the **appliance timezone** setting is.

### 1.2 What each surface buys the centre

| Surface | Why it exists |
|---------|----------------|
| **Dashboard** | Big wall time + next gong/doha + health (clock trust, exact alarms, battery, doha map count, route honesty). Staff glance from across the office. |
| **Courses** | The only input that really matters. Type + start date; day 0 = arrival; day index = **calendar-date difference** (never `seconds/86400`). Active course = window match so a course that began while the tablet was off is still found. Two overlapping courses → one **ACTIVE**, one **OVERLAP** — never a silent pick. |
| **Schedule** | Full day-by-day grid (days across, times down). “Shift day 3 afternoon gong” must look like a day-3 change, not every day. Gap/track **“—” = inherit** is load-bearing: most rows follow the default so changing the default changes them all. |
| **Time** | Schedule fires in **configured appliance timezone**, not the donated travel phone’s TZ. Holds **clock-trust confirmation** — the one action that un-sticks a suppressed appliance. |
| **Sounds** | Maps eleven doha slots to real audio (SAF folder pick / auto-map; optional separate CDN download path). **App ships no copyrighted doha audio.** Gongs may be bundled stems; doha is centre media. |
| **Audio out** | Where sound goes (speaker / BT / USB). Must **steer** the route and be honest when the chosen route fails (fallback + visible last-ok), so an unplugged amp at midnight is noticed at 04:00 rather than silent. |
| **Amp power** | Shelly (or similar) relay: amp on just before play, off after. **Gong still rings if the relay is dead.** Relay never blocks or delays play. |
| **Network** | Informational only (find the tablet’s address on a phone call). Nothing mission-critical depends on it. |
| **Setup + PIN** | “Survive three weeks unattended”: exact alarms, battery unrestricted, notifications, ignore-battery-optimizations as needed. PIN keeps a curious student out of the schedule. |

Detailed visual specs: `docs/handoff/` (open the design/screens HTML in a browser). Do not invent a second product.

---

## 2. Expert Android constraints (non-negotiable)

These are implementation requirements that keep the three rules true on real devices.

### 2.1 Process & lifecycle

- One **foreground service** (`mediaPlayback` type) owns: `SchedulerEngine`, `PlayerEngine`, media resolution, optional relay controller.
- Activity / Compose UI binds as a **client** of the service (or observes shared Room + service flows). **Destroying the activity must not cancel schedule or queue.**
- `RECEIVE_BOOT_COMPLETED` re-starts the service and re-arms the next deadline.
- Prefer `setAlarmClock` (or documented exact-alarm path) **plus** a **≤30 s heartbeat** loop so clock jumps and OEM alarm gaps are noticed. Alarms optimise; heartbeat is belt-and-braces.
- Exact-alarm and battery-exemption grants are **product features**, not nice-to-haves — surface them on Setup/Dashboard until healthy.

### 2.2 Domain purity

- Keep `domain/` free of Android framework imports so fire rules run as **fast JVM unit tests**.
- Port schedule algorithms from the Pi daemon behaviour already encoded in tests: `SchedulerCore`, `FireRules`, `ClockTrust`, `ActiveCourse`, `DohaSlots`, `ScheduleMaterializer`.
- **Do not invent schedule semantics.** If design prose and tests conflict, **tests + domain win** until a human explicitly changes both.

### 2.3 Fire path integrity

- Materialize occurrences for the active course / no-course set using **calendar day math** in the appliance zone.
- For each due occurrence: `FireRules.decide` → if fire: **persist fired mark**, then enqueue play; if missed: log missed; if early: wait.
- Grace default: **120 s** (`SettingsDefaults.FIRE_GRACE_SECONDS`).
- Clock untrusted → **no automatic plays** (and no automatic relay switching). Manual test plays from the UI may still be allowed when product says so.
- Double-fire guard key must be stable across restarts (same key the Pi model uses in spirit).

### 2.4 Player rules (parity)

- Single queue owned by the service.
- New **gong** aborts a running/pending gong (bursts never stack).
- **Doha never preempts a gong** — it waits.
- Stop aborts current and drops queue; log dropped work as appropriate.
- Burst gap is silence **after** a strike finishes (not gapless overlap).
- Missing media → log error, do **not** invent another track.

### 2.5 Media & licensing

- **Never commit copyrighted doha masters.** See `MEDIA.md`, `LICENSE-NOTES.md`.
- Release builds may be **GONGS ONLY** until staff map a doha pack (Dashboard chip).
- Doha mapping: SAF document tree + per-slot persisted URIs in `media_slots` (`auto` / `manual` / `bundled`).
  - Auto-map by filename prefix `D01`…`D11` (case-insensitive).
  - Conflicts: never silent winner; both unassigned for staff resolution.
  - Rescan must not overwrite `manual` or `bundled`.
  - Re-pick folder: take new persistable grant before releasing old; failed take changes nothing.
- Optional **CDN download + dual checksum + decrypt** is a **separate feature** — do not block SAF path on it; do not mix licensing into APK assets.

### 2.6 Amp relay (if implementing Amp power)

Spec: `docs/superpowers/specs/2026-08-09-shelly-relay-design.md`.

- Shelly 1 Gen4 (Gen2+ JSON-RPC over LAN HTTP), manual IP primary (Gen4 mDNS unreliable).
- **Rising-edge ON only**, with device-side `toggle_after` watchdog (err long).
- Pre-arm from existing scheduler tick / `nextDeadline` (heartbeat + lead) — **no new fire alarms** for the relay.
- **Never OFF** while playing, or while next deadline is still inside the pre-arm window (back-to-back gong→doha).
- Missed occurrence: sticky armed deadline → **explicit OFF** on tick (process alive); `toggle_after` if process dies.
- Relay failures: log/UI only; **play proceeds on time**.
- Manifest: `INTERNET` + cleartext policy for centre LAN HTTP as documented in the relay spec. Threat model is local centre Wi‑Fi (`SECURITY.md`).

### 2.7 Data

- Room/SQLite; column naming keeps Pi-tooling readability where already established.
- Settings as key/value with defaults in `SettingsDefaults` (+ Android extras).
- Seed on first run / backfill missing keys (`SeedLoader` idempotent).
- Use provided seed SQL/JSON for course types, default schedule patterns, settings.
- `allowBackup=false` unless a deliberate backup feature lands with its own threat model.
- PIN: salted hash only (never plaintext). Device passwords (e.g. Shelly) never logged.

### 2.8 UI / appliance UX

- Landscape tablet target (~1280×800); Compose; Nocturne / handoff chrome.
- **≥44 dp** interactive targets on staff-critical controls.
- PIN-gated settings tabs; session expiry as already productised for beta.
- Locked/partial tabs must not pretend to be full products (e.g. Sounds may ship doha-map only while volumes remain later).
- Immediate saves + toast for appliance settings; no multi-step “wizard save” for day-to-day edits unless Setup first-run.

### 2.9 Networking philosophy

- **Core gong/doha path works fully offline.**
- Network screen is diagnostic.
- Any download/CDN path is opportunistic and secondary to local media.

### 2.10 Explicitly out of scope (until gong/doha field MVP is stable)

- Deshna jukebox server / `fetch.php` ecosystem.
- Cloud accounts, analytics, ads, required backend for core operation.
- Multi-relay / Shelly Cloud / Matter / Zigbee.
- In-app BLE provisioning of Shelly (use Shelly’s own app).
- Full multi-tenant admin over the public internet.

---

## 3. Architecture map (implement against this)

```
app/src/main/java/org/dhamma/gong/
  domain/     pure law: SchedulerCore, FireRules, ClockTrust, ActiveCourse,
              DohaSlots, DohaPackMapper, RelayPlan, …
  data/       Room entities/DAOs, GongRepository, SeedLoader
  player/     PlayerEngine, MediaResolver, AudioSink, AudioRouter
  schedule/   SchedulerEngine, AlarmScheduler
  relay/      ShellyClient, RelayController (optional until Amp power ships)
  service/    GongService, boot/time receivers, permission helpers
  ui/         Compose client — Dashboard, Courses, Schedule, Logs, Time,
              Sounds/Doha media, Audio out, Amp power, Network, Setup, PIN
```

**Invariant:** service owns schedule + player (+ relay). UI mutates Room/settings and pokes the service; it does not own the fire loop.

---

## 4. Seed & fixtures

| Asset | Role |
|-------|------|
| `seed/seed.sql` / `seed/seed.json` | Course types, default schedule events, settings defaults |
| `seed/courses-sudha-*.sql` (or similar) | Optional centre-specific course rows |
| `seed/doha-manifest.json` | Reference mapping slot → filename (not shipped audio) |
| `media/` + `MEDIA.md` | Layout docs; bundled gongs only as allowed |
| Debug doha test tones | Synthetic only; never treat as production doha |

When adding seed: keep loaders idempotent; backfill new setting keys on upgrade without wiping centre edits.

---

## 5. Milestone-oriented implementation (how to work)

Ship vertical slices with tests green. Suggested order of **value to the hall**:

| Priority | Slice | Done means |
|----------|--------|------------|
| P0 | Domain + Room + seed | JVM tests for day math, fire, clock, doha slots; DB boots with seed |
| P0 | Service + scheduler + player + bundled gong | Fires a gong in grace on emulator/device; reboot re-arms |
| P0 | Dashboard + Courses + Schedule + Logs | Staff can create course and see next fire; logs show fire/miss |
| P0 | Time + clock trust | Wrong clock suppresses; confirm resumes |
| P1 | Doha SAF map (Sounds partial) | 11 slots mappable; GONGS ONLY clears; manual survives rescan |
| P1 | Audio route honesty | Choice steers output; failure falls back visibly |
| P1 | Setup grants + PIN | Exact alarm / battery paths discoverable; PIN gates settings |
| P2 | Amp power (Shelly) | Relay ON/OFF around play without delaying fire |
| P2 | CDN doha pack (separate) | Dual checksum + decrypt; never blocks offline SAF |
| P2 | Backup/restore / first-run polish | Only with explicit threat model |

Update `PROGRESS.md` when a slice ships. Prefer `./gradlew :app:testDebugUnitTest` green before claiming done.

### Commands

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

JDK 17+, `local.properties` → `sdk.dir=…`. minSdk as in project (currently 29 for SAF/hotspot reliability — do not silently lower).

---

## 6. Design requirements (when designing, not only coding)

When asked to **design** a feature (new screen, relay, media path):

1. Restate which of the **three rules** it serves or must not break.
2. List **in / out** scope in one screenful.
3. Name existing seams to reuse (`SchedulerCore.tick`, `media_slots`, settings keys) — no parallel fire engines.
4. Pure domain pieces get JVM tests; Android IO (SAF, HTTP) stays at edges.
5. Failure modes table: unreachable amp, missing file, revoked SAF, clock untrusted, process kill mid-play.
6. Acceptance criteria a QA person can run on a wall tablet without a debugger.
7. Write the design under `docs/superpowers/specs/YYYY-MM-DD-<name>-design.md` before large implementation, unless the user forbids docs.

Existing major specs:

- `docs/superpowers/specs/2026-08-09-doha-media-folder-design.md`
- `docs/superpowers/specs/2026-08-09-shelly-relay-design.md`

---

## 7. Testing requirements

| Layer | What |
|-------|------|
| JVM unit | Fire window, miss, double-fire, clock trust, course day, doha slot sequence, pack mapper, relay plan (on/off/miss/back-to-back) |
| Instrumented / device | Service survives activity destroy; alarm + heartbeat; SAF folder map; audio route; optional real Shelly |
| Manual QA | `docs/BETA-QA-CHECKLIST.md` — 04:00 path, reboot, untrusted clock, GONGS ONLY, PIN |

**Evidence before “done”:** paste test command output or name the suite that passed. Do not claim field-ready on compile-only.

---

## 8. Security & privacy (centre appliance)

- Threat model: physical premises + local Wi‑Fi; integrity of schedule/clock > fancy crypto (`SECURITY.md`).
- No analytics SDKs in default tree.
- No NPI/PII collection beyond what staff type into local DB (names of courses, not student records).
- Do not log PIN, Shelly password, or raw doha file contents.
- Optional LAN features must not bind open to untrusted networks.

---

## 9. Definition of field MVP

A centre can:

1. Install APK, grant Setup permissions, set PIN and appliance timezone.  
2. Enter course type + start date.  
3. Hear gongs on the timetable (speaker or configured route) with correct day inheritance.  
4. Map doha files (SAF) and hear morning doha when scheduled (or knowingly run GONGS ONLY).  
5. Recover from clock distrust with one confirm action.  
6. Reboot overnight without losing the next fire.  
7. (Optional) Drive amp power via Shelly without risking a late/silent gong if Shelly is off.

If any of 1–6 fail, polish screens are not the priority.

---

## 10. How to start a session (paste-friendly)

```
Read docs/NIYAM-DESIGN-AND-IMPLEMENTATION-PROMPT.md and AGENTS.md.
Work only on: <MILESTONE OR FEATURE>.
Do not change FireRules / SchedulerCore behaviour without a failing test first.
Run ./gradlew :app:testDebugUnitTest before claiming done.
Update PROGRESS.md if the slice ships.
```

For design-only:

```
Read docs/NIYAM-DESIGN-AND-IMPLEMENTATION-PROMPT.md.
Write/amend a design under docs/superpowers/specs/ for: <FEATURE>.
Respect the three rules and §2 expert constraints. No code unless asked.
```

---

## 11. Sources of truth (priority order)

1. Unit tests under `app/src/test/` + `domain/`  
2. This prompt + `AGENTS.md` + `SECURITY.md` / `PRIVACY.md` / `MEDIA.md`  
3. `PROGRESS.md` (what is actually shipped)  
4. Feature specs in `docs/superpowers/specs/`  
5. Handoff UX: `docs/handoff/`  
6. Older implementation prompt `docs/ANDROID-APP-IMPLEMENTATION-PROMPT.md` (historical milestones; prefer this document + current code when they conflict)

---

*End of prompt. The gong is the product; the UI is how staff keep the gong honest.*
