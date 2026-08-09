# Claude Code prompt: implement Gong Android appliance app

**Scope: BUILD A WORKING APP** (not a design essay).  
Design context lives in the original design prompt (removed with the Pi-era docs; see git history) and the Pi daemon in the Pi design doc (removed; see git history) + `ng/`.

---

## How to run this session

Paste this document into **Claude Code** (or Cursor/agent) at the start of an implementation session, then say:

> Implement milestone **M0** (or M1…) from this prompt. Stay inside the milestone. Do not redesign the product.

**Recommended workflow**

1. Human (or Claude Design) produces `docs/android/DESIGN.md` from the design prompt **or** accept **§3 MVP defaults** below if no design doc exists yet.
2. Claude Code implements **one milestone at a time**, with tests green before the next.
3. After each milestone: summarize what shipped, how to run, known gaps.

**Repo layout assumption**

- The Pi monorepo may live at `gongserver/` (this tree).
- Create the Android project as a **sibling or subdir**:
  - Preferred: `gongserver/android/` (single monorepo), **or**
  - `GongAndroid/` next to `gongserver` if user prefers a separate git root.
- If unsure, use `android/` under the gongserver root and a module name `app`.

Do **not** rewrite Python `ng/` unless fixing a shared seed export script.

---

## 0. Your role (Claude Code)

You are a senior Android engineer shipping a **centre appliance app**:

- Device **is** the scheduler + player (speaker / Bluetooth / USB DAC).
- Offline-first; no cloud required for course operation.
- Behavioural parity with the Pi daemon scheduling (not a PHP clone).
- Display on device for staff; hotspot + office Wi‑Fi configuration in-app.
- Deshna jukebox server is **out of scope** until after MVP gong/doha works.

**Engineering rules**

1. **Small vertical slices** — ship runnable app early; do not build all screens before scheduler works.
2. **Test domain logic first** — pure Kotlin unit tests for day math, materialization, grace/missed, doha slots (port cases from `ng/tests/`).
3. **No speculative frameworks** — one architecture; avoid multi-module explosion until needed.
4. **Do not invent schedule semantics** — copy the Pi daemon algorithms; link to source file in comments when porting.
5. **Media licensing** — do not assume Play Store may ship real doha/gong masters. Ship **tiny synthetic test tones** in-repo; document how centres add licensed media packs.
6. **Evidence before “done”** — run unit tests; for instrumented/emulator, state what you ran and results.
7. Ask before destructive git ops, Play Console setup, or committing secrets.

---

## 1. Product locked decisions

| Item | Decision |
|------|----------|
| Shape | Appliance: phone/tablet is gong box |
| Audio | Built-in speaker + Bluetooth + USB audio; abstract for future wired amp |
| UI | Native on-device (Jetpack Compose) |
| Network | Configure hotspot and/or join office Wi‑Fi; scheduling works offline |
| Auth | PIN for settings; optional “kiosk” later |
| Data | Local SQLite (Room) |
| Pi parity | Course window, schedule materialization, grace 120s, double-fire guard, doha modular, clock trust |
| Out of MVP | Deshna `fetch.php`, GPIO relay, cloud accounts, full schedule CSV import |

---

## 2. Suggested tech stack (use unless design doc overrides)

| Layer | Choice |
|-------|--------|
| Language | Kotlin |
| Min SDK | 26 (or 28 if you justify); Target / compile latest stable |
| UI | Jetpack Compose + Material 3 |
| DI | Hilt (or manual if faster for M0–M1 — pick one and stick) |
| DB | Room + SQLite |
| Async | Coroutines + Flow |
| Player | Media3 (ExoPlayer) |
| Background | Foreground `Service` (mediaPlayback) + `AlarmManager` setExactAndAllowWhileIdle **or** WorkManager only as backup — **exact fires matter**; document choice |
| Nav | Navigation Compose |
| Serial | Kotlinx serialization or Moshi if needed |
| Tests | JUnit5/4 + Truth; Robolectric optional; Espresso/Compose UI tests later |
| Build | Gradle Kotlin DSL, version catalog |

**Package name (default):** `org.dhamma.gong` or `com.dhammagong.appliance` — use one consistently; confirm if user already owns a reverse-DNS name.

---

## 3. MVP defaults if no design doc yet

If `docs/android/DESIGN.md` is missing, implement this MVP without blocking:

**In**

- Foreground scheduler service + persistent notification (next event, course day)
- Room schema matching the Pi daemon entities (see design prompt §3.3)
- Seed importer from exported JSON/SQL derived from the Pi repo's seed.sql (course_types + schedule_events)
- Course CRUD (type + start date)
- Active course derivation + dashboard
- Gong burst player + doha once daily
- Settings toggles + volumes + doha time + track
- Test gong / test doha / stop
- PIN gate for settings (simple encrypted PIN in EncryptedSharedPreferences or hashed in DB)
- Logs screen (play_log)
- Backup export/import of DB file
- Audio output: default device + pick Bluetooth if available (best-effort)
- First-run: permissions (notifications, exact alarm, battery optimization intent)
- Network screen: deep links / instructions + `Settings.ACTION_*` where system UI is required; implement SoftAP only if API-feasible, else clear “open system hotspot” flow

**Out**

- Deshna, LAN HTTP admin API, multi-centre, Play media pack store, smart-plug amp power

---

## 4. Domain logic — port these exactly

Implement as **pure Kotlin** in a `domain/` or `core/schedule/` package (no Android framework imports) so unit tests run on JVM.

### 4.1 Active course

```
candidates = courses where startDate <= today <= startDate + totalDays
if activeCourseId override in candidates → use it
elif one candidate → use it
elif many → most recent startDate, surface warning
else → none

currentDay = ChronoUnit.DAYS.between(startDate, today)  // calendar, not /86400
```

Reference: the Pi daemon's model.py `active_course`.

### 4.2 Schedule materialization

- No course → events with `courseTypeId == null`
- In course → events for `(type, day_no == currentDay)` if any, else `(type, day_no == null)` default pattern
- Plus synthetic doha at `doha_time` settings

Reference: the Pi daemon's scheduler.py `upcoming_occurrences`, design §3.1.

### 4.3 Fire rules

- Never early
- Persist fired key **before** play: `fired:{key}:{localDate}`
- Fire if `now` in `[fireAt, fireAt + grace]` (default grace **120s**)
- Else past window → log `missed`
- Clock invalid → automatic plays `skipped_clock`; tests still work

Reference: the Pi daemon's scheduler.py, design §5.3, §6.

### 4.4 Doha slots

Port `legacy_modular` from the Pi daemon's doha.py byte-for-byte. Outside course: `random` | `off` | `slot:n`. Resolve via manifest 1..11.

### 4.5 Gong burst

`repeats` times, `gap_seconds` between, track file stem, volume; stop aborts; doha waits for gong (queue).

Reference: the Pi daemon's player.py, design §5.4.

Copy unit-test intent from:

- `ng/tests/test_scheduler.py`
- `ng/tests/test_doha.py`
- `ng/tests/test_player.py` (queue/stop semantics as applicable)

---

## 5. Milestones (implement in order)

### M0 — Project skeleton + domain tests (no device required)

**Done when:**

- [ ] Android project builds (`./gradlew :app:assembleDebug`)
- [ ] `domain` module or package with: `ActiveCourse`, `Materializer`, `DohaSlots`, `FireDecision`
- [ ] Unit tests covering: day 0/4/total, default pattern fallback, grace fire, missed, double-fire guard, modular doha for sample 10-day
- [ ] README `android/README.md`: open in Android Studio, run unit tests

**Do not** spend M0 on Compose polish.

### M1 — Data layer + seed

**Done when:**

- [ ] Room entities + DAOs for course_types, courses, schedule_events, settings, state, play_log
- [ ] Seed load on first launch (course types + schedule from assets)
- [ ] Settings defaults match Pi
- [ ] Tiny **generated** beep/gong placeholder WAV/MP3 in `assets/media/` for CI (not full doha masters)
- [ ] Optional script under `android/tools/` or `tools/` to export seed JSON from the Pi repo's seed.sql if helpful

### M2 — Player + foreground service + manual test fires

**Done when:**

- [ ] Media3 playback of gong file N× with gap
- [ ] Doha single play
- [ ] Foreground service sticky notification
- [ ] Dashboard minimal UI: test gong, test doha, stop, show “service running”
- [ ] Runs on emulator with audio

### M3 — Real scheduler loop

**Done when:**

- [ ] Materialize today/tomorrow; schedule next alarm / loop
- [ ] Automatic fire within grace; missed logged
- [ ] Survives process death (alarm reschedule on boot + service restart)
- [ ] `BOOT_COMPLETED` receiver restarts scheduling when permitted
- [ ] Toggles: enabled / gong_enabled / doha_enabled respected
- [ ] Clock-trust stub: if wall clock jumps backward hard, suppress auto fires + banner

**Verify:** instrumented or manual script: set a schedule event 1–2 minutes ahead, confirm fire + log.

### M4 — Courses UI + full dashboard

**Done when:**

- [ ] Add/list/delete courses
- [ ] Dashboard shows type, day, next 5 events, toggles
- [ ] PIN lock for settings (and optionally for course edits)
- [ ] Sounds screen: track, volumes, gap, doha time, no_course_doha
- [ ] Logs screen

### M5 — Schedule editor (basic)

**Done when:**

- [ ] View events for course type + day / default / no-course
- [ ] Add/delete event (time, repeats)
- [ ] Changes poke scheduler to recompute

### M6 — Backup, audio route, network UX, first-run

**Done when:**

- [ ] Export/import DB backup
- [ ] Audio device preference (speaker vs BT when available)
- [ ] First-run permissions wizard
- [ ] Network screen: join Wi‑Fi (system settings), hotspot instructions / SoftAP if implemented
- [ ] `android/README.md` field setup for a centre volunteer

### M7 — Hardening (before any Play upload)

**Done when:**

- [ ] No crash on missing media (log error, skip)
- [ ] Battery optimization guidance UI
- [ ] ProGuard/R8 rules if minify on
- [ ] Privacy policy stub + permission rationale strings
- [ ] Document media pack install path for licensed audio
- [ ] Checklist vs the Pi daemon parity table filled in README

**Stop after M7 unless user asks for Deshna / LAN API / Play release engineering.**

---

## 6. Architecture sketch (implement, don’t over-abstract)

```
app/
  ui/          # Compose screens, ViewModels
  service/     # GongForegroundService, receivers
  data/        # Room, seed, backup
  player/      # Media3, burst executor, audio route
  schedule/    # Android bridge: alarms + domain materializer
domain/        # pure Kotlin (or app/src/main/java/.../domain)
```

**Single source of “what fires next”:** domain materializer.  
**Single player queue:** one executor in service process.

Avoid multiple competing schedulers (WorkManager + AlarmManager + loop) without a clear owner.

---

## 7. Permissions (declare only what you use)

Typical set — trim if unused:

- `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`
- `POST_NOTIFICATIONS` (33+)
- `SCHEDULE_EXACT_ALARM` or `USE_EXACT_ALARM` (justify)
- `RECEIVE_BOOT_COMPLETED`
- `WAKE_LOCK` (if needed)
- Bluetooth connect/scan (if BT picker)
- Optional: `CHANGE_WIFI_STATE` / nearby Wi‑Fi (API-dependent; prefer system Settings)

For each permission: short rationale string in UI + README.

---

## 8. Media strategy (mandatory)

| Build | Audio content |
|-------|----------------|
| Debug / CI | Synthetic short tones named like `ting.mp3`, `doha_slot_01.mp3` |
| Centre production | User-installed pack under app-specific storage; verified by manifest |
| Play Store public | **Do not** ship VRI doha masters without legal clearance; gate features or use placeholders |

Add `docs/android/MEDIA.md` explaining install of a media pack (folder layout matching Pi).

---

## 9. Parity checklist (track in README)

| Behaviour | Pi | Android |
|-----------|----|---------|
| Calendar current_day | yes | |
| Course window not start-only | yes | |
| Default day pattern fallback | yes | |
| Grace + missed | yes | |
| Double-fire state | yes | |
| Doha modular | yes | |
| Queue stop / doha waits | yes | |
| Clock invalid silence | yes | |
| Backup DB | yes | |

---

## 10. Commands you should run

```bash
# from android/
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
# optional
./gradlew :app:connectedDebugAndroidTest
```

If no SDK in environment, still write code + unit tests; state that emulator build was not run.

---

## 11. Definition of “working app” (MVP exit)

A volunteer can:

1. Install debug APK on a tablet left on charge.
2. Complete first-run permissions.
3. Seed loads; add a 10-day course starting today.
4. See Day 0/N and upcoming gongs.
5. Test gong (multiple strikes) on speaker or BT.
6. Schedule a test event 2 minutes ahead; hear automatic fire; see log `ok`.
7. Force-stop app, reopen before event; still fires once (or correctly misses if past grace).
8. Export backup.

That is enough to call MVP **working**. Polish and Play release are separate.

---

## 12. Session start checklist (do this first)

1. Confirm workspace path and whether `android/` already exists.
2. Read the Pi design doc (removed; see git history) §§3,5,6 and `the Pi daemon sources` headers.
3. If `docs/android/DESIGN.md` exists, follow it; else §3 MVP defaults.
4. Create todo list for the **current milestone only**.
5. Implement → test → short summary for the user.

---

## 13. What not to do

- Do not port Apache/MariaDB/cron.
- Do not block MVP on SoftAP parity with Pi `192.168.5.1`.
- Do not commit signing keystores, PINs, or real centre passwords.
- Do not download large copyrighted media into git without explicit user request.
- Do not expand into Deshna mid-milestone.
- Do not claim “done” without listing test commands and results.

---

## 14. Optional one-shot kickoff message (user → Claude Code)

Copy-paste:

```text
Read docs/ANDROID-APP-IMPLEMENTATION-PROMPT.md
(for domain context). Implement milestone M0 in android/ under this repo.
Use the Pi daemon algorithms from the Pi daemon sources. Domain unit tests first, then skeleton app.
Do not implement M1+ until M0 tests pass and you show how to run them.
```

Then for each later session:

```text
Continue Gong Android app: implement milestone M<N> only per
docs/ANDROID-APP-IMPLEMENTATION-PROMPT.md. Summarize diff and test results when done.
```

---

## 15. After design doc exists

If the user provides `docs/android/DESIGN.md` (from the design prompt):

1. Diff it against §3 MVP defaults; note extra scope.
2. Prefer design doc screen names and package structure **if** they do not break domain purity or milestones.
3. If design conflicts with the Pi daemon fire semantics, **the Pi daemon wins** unless design explicitly documents a deliberate change.

---

*Implementation prompt version: 2026-08-08 — pairs with the original design prompt (removed) and the Pi daemon M1+M2.*
