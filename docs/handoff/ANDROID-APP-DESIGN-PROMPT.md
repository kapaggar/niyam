# Design prompt: Gong-NG as an Android appliance app

**How to use:** Paste this entire document into Claude (or another design agent) as the sole brief for a **system design / product architecture** session. Ask it to produce a design doc, not code, unless you later request implementation.

**Source of truth for scheduling semantics:** this repo’s `docs/GONG-NG-DESIGN.md`, `ng/gong_ng/*`, and `db/gong.sql` / `ng/seed/seed.sql`. Prefer those algorithms over reinventing course-day logic.

---

## 0. Your role (Claude)

You are a product + systems designer for a **centre-deployed Android appliance** that replaces a Raspberry Pi “Gongserver” for Vipassana courses.

**Deliverables (in order):**

1. **Product summary** (1 page): who uses it, what “done” looks like on day 0 of a 10-day course.
2. **Feature set** split into **MVP / v1.1 / later**, with explicit non-goals.
3. **Information architecture** + screen list (wireframe-level, mobile-first; the device *is* the display).
4. **Domain model** (SQLite tables / entities) mapped 1:1 or with justified deltas from Gong-NG.
5. **Runtime architecture** on Android: always-on scheduler, playback, audio routing, process survival (Doze, OEM killers, exact alarms, foreground service).
6. **Audio pipeline**: built-in speaker, Bluetooth A2DP/LE Audio amp, USB DAC; future **wired amp** (USB audio / 3.5 mm / dock); volume model; gong burst semantics.
7. **Networking**: device as **Wi‑Fi hotspot** for staff phones *and* client on **office / centre Wi‑Fi**; discovery and admin access patterns.
8. **Security & threat model** (centre LAN / physical device, not public multi-tenant SaaS).
9. **Reliability**: clock trust, power loss, missed events, battery, “scheduler dead” UX.
10. **Distribution**: Google Play worldwide + private / side-load path for centres that cannot use Play; media licensing implications.
11. **Migration / parity** checklist vs Gong-NG Pi stack.
12. **Open questions** you still need answered before implementation (max 15, ranked).
13. **Phased implementation plan** (milestones, test strategy without a real hall).

**Constraints:**

- Do **not** invent a different scheduling algorithm for course day / gong times / doha slots unless you flag a deliberate, justified change.
- Do **not** design cloud-required core operation. Offline-first is mandatory.
- Do **not** put Deshna jukebox (`fetch.php` / discourse library) in MVP unless you argue it as optional module after core gong/doha is solid.
- Prefer boring, field-repairable choices (local SQLite, no build-dependent CDN UI for critical paths).
- Call out Android API / Play policy risks (exact alarms, FGS types, background audio, local hotspot APIs) honestly.

---

## 1. Product decision (locked for this design)

| Decision | Choice |
|----------|--------|
| Product shape | **A — Appliance app.** The Android device **is** the scheduler and primary player. |
| Hardware | Phone or tablet, preferably **plugged in 24/7** at the centre; has a **display** (on-device admin UI). |
| Audio out | **Built-in speaker** (dev / small rooms) **or** drives amp via **Bluetooth**, **USB DAC**, later **wired amp** path. |
| Network | Staff can configure **hotspot (AP)** and/or join **office / centre Wi‑Fi**. |
| Distribution | Easy **worldwide publish** (Google Play + optional private APK). |
| Relation to Pi | Long-term **replacement** for Gong-NG on Raspberry Pi for centres that choose Android; parity of *behaviour* matters more than porting Python line-for-line. |
| Internet | **Not required** for gong/doha during a course. Updates/media packs may use network when available. |

### Out of MVP (unless design proves cheap)

- Full **Deshna** media server for the existing Deshna Android course-audio app (3k+ tracks).
- GPIO **relay** control of amplifier power (Pi-specific); replace with “amp always on”, Bluetooth amp power, or later smart-plug / USB-C accessory.
- Multi-tenant cloud accounts, remote fleet management (may be v2+).
- Field migration *from* a live Pi DB (fresh setup + seed + manual course entry is OK for v1).

---

## 2. Domain: what problem this solves

At a Vipassana centre, a **gong (bell)** marks the schedule (wake-up, sit, break, etc.) and a **morning doha** (chant / teaching track) plays once per day. Staff must not babysit a phone timer. Behaviour must be:

- Correct for the **course day number** (arrival = day 0).
- Correct **wall-clock** times (04:00 means 04:00 on the wall in the centre timezone).
- Safe if power / process restarts: **no double fire**, **no blasting a missed 04:00 gong hours late**.
- Operable by non-technical staff with a PIN, on a device with a screen.

The current gold-standard implementation is **Gong-NG** (`ng/` in this monorepo): one Python daemon on a Pi with SQLite, second-accurate scheduler, player queue, and mobile-first admin UI.

---

## 3. Full know-how: Gong-NG stack (context dump)

### 3.1 Repository map

```
gongserver/   (GitHub: kapaggar/GongDohaServer)
├── app/                 # LEGACY PHP LAMP (cron + MariaDB + Apache) — production-derived
│   └── dhamma/          # poll.php, doha.php, constants.inc, gong-*.mp3, doha/*.mp3
├── db/gong.sql          # Legacy schema + schedule seed
├── ng/                  # NEXT-GEN (target semantics for Android)
│   ├── gong_ng/         # Python package: scheduler, player, doha, model, web
│   ├── seed/            # seed.sql, doha-manifest.json (generated from legacy)
│   ├── tests/           # pytest — pin behaviour
│   └── README.md
├── docs/GONG-NG-DESIGN.md   # Architecture decisions
└── docs/ANDROID-APP-DESIGN-PROMPT.md  # this file
```

**Legacy lessons already fixed in NG (Android must not reintroduce them):**

1. Zero-day only updated when course starts “today” → running course invisible if boot missed start day.  
2. Disabled cron spam + exit 1 every minute.  
3. Invalid time strings accepted into system clock.  
4. Day math via `/86400` breaks around DST.  
5. Doha index landmine (`0 => 0`).  
6. Single settings row assumptions without guards.

### 3.2 Runtime architecture (Pi / NG)

```
systemd → gongd (one process)
  ├── Scheduler thread   → next event, grace, double-fire guard
  ├── Player thread      → queue: gong bursts / doha; optional relay; spawns mpv
  └── Web thread         → Flask + waitress; PIN session; admin UI + JSON API
SQLite WAL at /var/lib/gong/gong.db
Media under /var/lib/gong/media/{gongs,doha}/
```

**Android translation target:**

```
Android app (single package)
  ├── Foreground service + exact alarms / AlarmManager strategy
  ├── Scheduler engine (port NG semantics)
  ├── Player engine (ExoPlayer / Media3 or equivalent)
  ├── Local SQLite (Room or raw)
  └── On-device Compose/XML UI (+ optional LAN admin later)
```

### 3.3 Data model (implement this semantics)

```sql
-- Conceptual; Android may use Room entities with same meaning.

course_types (
  id INTEGER PK,           -- preserve seed IDs from legacy ct_id
  name TEXT UNIQUE,        -- '10 Day'
  total_days INTEGER,      -- last day index; 10 Day = 11 in legacy
  anapana_days INTEGER
)

courses (
  id INTEGER PK,
  course_type_id FK,
  start_date TEXT,         -- 'YYYY-MM-DD' local; THIS is zero day (arrival)
  note TEXT
)

schedule_events (
  id INTEGER PK,
  course_type_id INTEGER NULL,  -- NULL = no-course schedule
  day_no INTEGER NULL,          -- NULL = default mid-course pattern
  time_local TEXT,              -- 'HH:MM' 24h wall clock
  repeats INTEGER 1..32,
  gap_seconds INTEGER NULL,     -- NULL → settings.gong_gap_seconds
  track TEXT NULL,              -- NULL → settings.gong_track
  UNIQUE (course_type_id, day_no, time_local)
)

settings (key TEXT PK, value TEXT)   -- singleton KV
state (key TEXT PK, value TEXT)      -- fired guards, last_good_time, etc.
play_log (
  id, ts_utc, kind, file, repeats, result, detail
)
-- kind: gong | doha | test_gong | test_doha
-- result: ok | error | skipped_clock | stopped | missed
```

**Default settings (NG):**

| Key | Default | Meaning |
|-----|---------|---------|
| `enabled` | `1` | Master switch |
| `gong_enabled` | `1` | Automatic gongs |
| `doha_enabled` | `1` | Automatic doha |
| `relay_enabled` | `0` | Pi GPIO; Android: map to “amp control” later or ignore |
| `gong_track` | `ting` | Stem under gongs media |
| `gong_volume` | `90` | 0–100 style |
| `gong_gap_seconds` | `4` | Silence between strikes in a burst |
| `doha_time` | `06:37` | Wall-clock daily doha |
| `doha_volume` | `75` | |
| `doha_strategy` | `legacy_modular` | Only strategy required in MVP |
| `no_course_doha` | `random` | `random` \| `off` \| `slot:<n>` |
| `active_course_id` | `` | Override when multiple windows |
| `admin_pin_hash` | | PIN (scrypt on Pi; Android: hardware-backed preferred) |

Media layout (logical):

```
media/gongs/{ting,drum,...}.mp3
media/doha/D01_....mp3 … D11_....mp3
media/doha/manifest.json   -- {"1":"D01_....mp3", ... "11":"..."}
```

Audio files exist in-repo under `app/dhamma/` but are **not MIT-licensed**; distribution packaging must respect VRI / tradition rights (private centre builds vs public Play assets).

### 3.4 Active course derivation (critical)

Recompute on boot, local midnight, course/settings change, large clock change:

```
candidates = courses where start_date <= today <= start_date + total_days
if settings.active_course_id points at a candidate → use it
elif exactly one candidate → use it
elif multiple → most recent start_date; warn in UI
else → no course

current_day = (today - start_date).days   # calendar dates, NOT unix/86400
# day 0 = arrival; in-course for doha typically 0 < day <= total_days
```

A course that started while the device was off is still found because the **window** is matched, not “starts today only”.

### 3.5 Schedule materialization

For local date D:

1. Resolve `CourseCtx` or no-course.
2. **No course:** events where `course_type_id IS NULL`.
3. **In course:** events where `course_type_id = CT AND day_no = current_day` if any exist;  
   **else** events where `course_type_id = CT AND day_no IS NULL` (default pattern).  
   (Legacy fell back to day 2; seed stores legacy day-2 rows as the default pattern.)

Each schedule row becomes a fire at wall `time_local` on date D.

**Plus** one synthetic **doha** occurrence at `settings.doha_time` (subject to enable flags and slot selection).

### 3.6 Fire loop guarantees (must preserve)

For each upcoming occurrence `(key, local_date, fire_at)`:

- **Never fire early.**
- **Never fire twice** for same `(key, local_date)` — persist `fired:<key>:<date>` in `state` **before** enqueueing play.
- Fire if `fire_at <= now <= fire_at + fire_grace_seconds` (NG default **120 s**).
- If `now > fire_at + grace` and not fired → log **`missed`**, do not play.
- After restart at 03:59:50, 04:00 still fires; after restore at 04:10, 04:00 is missed (safe).
- Wake loop frequently enough to notice clock jumps (NG caps sleep at 30 s).

**Clock invalid mode (must preserve spirit):**

- Persist `last_good_time` periodically.
- If on start `now < last_good_time - 10 min` → clock untrusted: suppress *automatic* plays (`skipped_clock`); banner; test buttons still OK; clear on confirm or trusted time set / forward NTP step.
- Wrong 04:00 is worse than silence.

### 3.7 Gong playback sequence

```
relay_on (optional) → settle (~5 s) →
for i in 1..N:
  stop current short sound if needed
  play gong file at gong_volume
  if i < N: sleep(gap_seconds)
relay_off
play_log result
```

**Queue rules (NG):**

- New gong while gong playing → abort current burst, start new (relay stays on if still needed).
- Doha vs running gong → **doha waits** (does not preempt gong).
- Stop now → abort current job.

### 3.8 Doha selection (`legacy_modular`)

Port byte-for-byte (slots 1–11):

```python
def legacy_modular(day, total, anapana):
    if day <= anapana:
        slot = ((day - 1) % 3) + 1              # 1,2,3 cycle
    elif day == anapana + 1:
        slot = 4                                # first vipassana day
    else:
        slot = 3 + ((day - (anapana + 1)) % 6) + 1  # 4..9 cycle
    metta_days = 2 if total >= 30 else 1
    if day == total:
        slot = 11                               # homage last day
    elif day >= total - metta_days:
        slot = 10                               # metta day(s)
    return slot
```

- In course with `0 < day <= total_days`: use modular strategy.
- Outside course: `no_course_doha` = `random` | `off` | `slot:n`.
- Resolve file via `manifest.json` with bounds check; missing slot → skip + log error (no crash).

### 3.9 Time & timezone

- Stored times: wall-clock local strings (`HH:MM`, `YYYY-MM-DD`).
- Default centre TZ often `Asia/Kolkata` (no DST) but code must be DST-safe:
  - `current_day` from **calendar dates**.
  - Materializing HH:MM: if spring-forward gap, fire first valid instant after; if fall-back ambiguity, fire **first** occurrence only.
- Device timezone should match centre; UI must show TZ clearly.

### 3.10 Admin surfaces (parity screens)

NG mobile-first screens to mirror on Android (native UI, not a webview of Flask required):

| Screen | Purpose |
|--------|---------|
| Login / PIN | 4–8 digit PIN; lockout after failures |
| Dashboard | Time, course day, toggles, next events, test gong/doha/stop, clock warning |
| Courses | List/add/delete; active indicator |
| Schedule | Filter by course type + day / default / no-course; CRUD events |
| Sounds | Track, volumes, gap, doha time, no-course doha mode, audio output device |
| Time | Display clock/TZ; set/confirm trust (Android limitations!) |
| Logs | play_log history |
| Backup | Export/import DB (+ settings); media separate or optional pack |
| Network | Hotspot vs station Wi‑Fi, SSID/password, show how staff connect |
| Audio output | Speaker / BT device / USB DAC picker + test |

NG JSON API (reference contracts for optional LAN remote admin later):

```
GET  /api/status
GET/PUT /api/settings
GET/POST/DELETE /api/courses
GET/POST/DELETE /api/schedule
POST /api/test/gong | /api/test/doha | /api/stop
POST /api/time
GET  /api/logs
GET  /healthz   # unauthenticated minimal health
```

Android MVP can implement the **same capabilities in-process** without exposing HTTP; design should note optional embedded HTTP for second-phone admin later.

### 3.11 Networking on Pi (intent for Android)

- **AP mode:** SSID e.g. `DhammaGong`, WPA2-PSK, `192.168.5.1`, staff phones join, open admin UI.
- **Station mode:** join centre office Wi‑Fi; staff use LAN IP or mDNS.
- Threat model: **WPA2 / physical access**, not “auth over public internet”.
- Android: use `WifiManager` / `SoftApConfiguration` / local-only hotspot APIs as available per API level; document OEM variance. On-device UI always works without any client.

### 3.12 Seed data

- Course types + full gong schedule matrix converted from `db/gong.sql` → `ng/seed/seed.sql`.
- Optional calendar: Dhamma Sudha 2026–2027 course list exists as SQL seed for demos; centres usually enter their own course dates.
- Do **not** renumber `course_types.id` or schedule identity casually — tools and habits may depend on IDs.

### 3.13 Deshna (context only — not MVP)

Gong-NG also implements legacy **Deshna** `GET /fetch.php?...` audio server for the separate Deshna Android *client* app, large media tree, USB import. Treat as **future module**. Gong appliance app must not block on shipping Deshna server v1.

### 3.14 Security principles

- Offline centre, shared device.
- PIN for configuration; playback can run without unlocking if desired (kiosk).
- No secrets in git; backups may contain PIN hash — protect backup files.
- Mutating actions require auth (and CSRF if web).
- Safe failure: silence + log > wrong gong at wrong time.

---

## 4. Android-specific requirements (product + platform)

### 4.1 Always-on behaviour

- Device is expected **plugged into power**; still design for brief unplug and reboot.
- Scheduler must run with screen off.
- Combat Doze / App Standby / OEM battery optimizers:
  - Foreground service with appropriate type (e.g. mediaPlayback / specialUse with justification).
  - Exact alarms where permitted; fallback strategy if denied.
  - First-run **setup checklist**: ignore battery optimizations, allow alarms, keep media notification.
- Persistent **status notification**: next event, course day, “scheduler running”.

### 4.2 Audio routing

- Selectable output: phone speaker, paired Bluetooth amp/speaker, USB audio device.
- Remember last route; recover when BT reconnects before next event (and log if route missing at fire time).
- Gong bursts: short MP3s with precise gaps — avoid BT codec latency destroying rhythm if possible; document limitations.
- Separate logical volumes for gong vs doha (map to app-controlled player volume; document interaction with system media volume).
- **Future wired amp:** USB-C DAC dock, 3.5 mm (if hardware), or accessory; design audio abstraction so adding a route is not a rewrite.
- Optional future: control amp power via smart plug / USB accessory — not MVP.

### 4.3 Display & UX

- On-device UI is primary (large touch targets; readable in bright hall office).
- Dashboard always answers: *What course day is it? When is the next gong? Is the scheduler healthy? Is audio OK?*
- Optional kiosk / screen-pinning mode for a wall tablet.
- Language: design for English first; structure for i18n (Hindi etc.) later.

### 4.4 Network UX (user-configurable)

- **Hotspot mode:** enable local AP; show SSID, password, QR to open admin if LAN admin added later; show “this device is the gong box”.
- **Office Wi‑Fi mode:** join centre SSID; show IP; optional mDNS/`gong.local` if feasible.
- Switching modes must not require ADB.
- Core scheduling must work with **airplane mode** / no Wi‑Fi if audio and clock are local.

### 4.5 Distribution

- Google Play worldwide: privacy policy, limited permissions justification, exact alarm policy, background FGS declaration.
- **Media licensing:** public store build may ship **without** copyrighted doha/gong masters, with download of licensed pack after auth, **or** private “centre edition” APK. Design packaging strategy; do not assume free redistribution of `app/dhamma/*.mp3`.
- Update story: Play updates for code; media packs versioned separately.

### 4.6 Backup & field recovery

- One-tap export: DB + settings (+ pin hash) to file / USB-OTG / share sheet.
- Restore with confirmation.
- “Simulate course starting DATE” for staff training (NG has `gongctl simulate`).

---

## 5. Suggested MVP acceptance criteria

A centre can:

1. Install app, grant battery/alarm/notification permissions, set PIN and timezone.
2. Load seed schedules + media pack.
3. Add a **10 Day** course starting today (or a test start date).
4. See **Day N** and next gong times on dashboard.
5. With dummy or real audio, **test gong** (N strikes + gap) and **test doha**.
6. Leave device overnight plugged in; automatic fires within grace; no doubles after force-stop + restart within grace rules.
7. Miss a fire beyond grace → `missed` in logs, no late blast.
8. Toggle master/gong/doha; disabled automatic plays stay quiet without error spam.
9. Export backup; wipe app data; restore; schedule intact.
10. Select Bluetooth speaker; test fire routes there; disconnect → clear error at next fire.

---

## 6. Explicit non-goals for design v1

- Replacing teacher guidance / course management systems (DIPI, etc.).
- Streaming from internet for scheduled fires.
- Social features, accounts, analytics SDKs that require PII.
- Pixel-perfect clone of Flask templates — native Android UX is fine if information is equivalent.
- Bit-identical Python port — **behavioural parity tests** matter more.

---

## 7. Reference algorithms & files to re-read while designing

| Topic | Location |
|-------|----------|
| Full NG design | `docs/GONG-NG-DESIGN.md` |
| Scheduler | `ng/gong_ng/scheduler.py` |
| Doha slots | `ng/gong_ng/doha.py` |
| Course window | `ng/gong_ng/model.py` (`active_course`) |
| Player queue | `ng/gong_ng/player.py` |
| Settings defaults | `ng/gong_ng/model.py` `SETTINGS_DEFAULTS` |
| API contracts | `ng/gong_ng/web/routes_api.py` |
| UI pages | `ng/gong_ng/web/routes_ui.py` |
| Seed conversion rules | design §7 + `ng/tools/convert_legacy_seed.py` |
| Legacy gong poll | `app/dhamma/poll.php` |
| Legacy doha | `app/dhamma/doha.php` |
| Tests as oracle | `ng/tests/test_scheduler.py`, `test_doha.py`, `test_player.py` |

When in doubt, **encode NG test cases as Android unit tests** for day math, materialization, grace/missed, and doha slots.

---

## 8. Open product questions (answer if you can; else design around them)

1. Primary form factor: dedicated tablet vs recycled staff phone?
2. Minimum Android API level for centres in target countries?
3. Acceptable public Play media strategy (strip audio vs licensed download vs private APK only)?
4. Is **LAN second-device admin** required in v1, or on-device UI only?
5. Default timezone / country for first-run wizard?
6. Must multiple simultaneous “virtual centres” exist on one device? (Assume **no**.)
7. Wired amp v1.1: USB audio only, or also 3.5 mm + dock accessory power control?
8. Should automatic brightness / kiosk lock be first-class?

---

## 9. Output format for Claude’s design response

Use markdown with:

1. Title + status (Draft)
2. Goals / non-goals
3. Personas (AT, assistant teacher, tech volunteer)
4. Architecture diagram (mermaid OK)
5. Data model
6. Scheduler state machine
7. Audio routing design
8. Screen IA
9. Permissions matrix
10. Offline / power / clock matrix
11. Phased roadmap (M0…M4)
12. Test plan (unit + instrumented + field)
13. Risks & mitigations
14. Unresolved questions

End with: **“Ready for implementation?”** checklist (yes/no per area).

---

## 10. One-sentence north star

> **A phone or tablet, plugged in at the centre, reliably rings the course gong and morning doha on the correct wall-clock times for the correct course day—without internet—while staff manage schedule, audio route, and Wi‑Fi/hotspot on its own screen, shippable worldwide as an Android app.**

---

*Prompt version: 2026-08-08 — aligned with Gong-NG M1+M2 semantics in `ng/` and design doc `docs/GONG-NG-DESIGN.md`.*
