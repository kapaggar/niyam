# Gong Android appliance — build progress & restart point

Standalone repo **`kapaggar/niyam`** (branch `main`), extracted with history from the gongserver monorepo's `android/` tree.

Last updated: 2026-08-13, **first shrunk release APK**
(`0.2.0-beta12`, `versionCode` 13) — R8 + resource shrinking take the QA build
from 17 MB to 3.6 MB. It is signed with the local Android debug key on purpose,
so a tester upgrades over `app-debug.apk` in place and keeps their database;
this is NOT a distribution key. Smoke-tested on the Pixel C: service foreground,
scheduler armed, 11/11 doha slots, every `@Serializable` serializer survives R8
(checked in `mapping.txt`). Prior: **Logs and Courses list order**
(`0.2.0-beta11`, `versionCode` 12) — Logs gained a two-tap "clear log" and now
orders by `ts_utc` rather than insertion, so a batch of `missed` rows written at
boot no longer lands above fresher entries. Courses runs in calendar order with
the old finished courses folded behind one expandable row. Prior:
light theme (`0.2.0-beta10`, `versionCode` 11). Prior: **gongs not firing on a real tablet**
(`0.2.0-beta9`, `versionCode` 10) — two causes, one of them a real domain bug;
see below. Prior: Schedule grid fix (`0.2.0-beta8`, `versionCode` 9) — the grid was drawing nothing at all; see below. Prior:
screen simplification (`0.2.0-beta7`, `versionCode` 8) — the app now follows the tablet's timezone, the PIN moved
onto Setup, and Logs/Network/Sounds shed detail nobody acts on. Prior same day:
course calendar + backup/restore (`0.2.0-beta6`, `versionCode` 7) — Dhamma Sudha's 39-course calendar seeds
itself at install, and settings/courses/schedule can be exported and restored.
Prior same day: Fable gap iteration (`0.2.0-beta5`) — deterministic
between-courses doha, a third keep-alive belt, a complete Sounds panel, and
honest READY / service-alive surfaces. Prior:
Audio out and Network (`0.2.0-beta4`), which shipped the last two locked screens
so the nav rail has no padlocks left. Audio out also made the
route *real*: the resolved device now reaches ExoPlayer instead of only
labelling the log. Prior: doha download pipeline (`0.2.0-beta2`), gong
recording swap, beta screen review (`0.2.0-beta1`), doha SAF folder, Shelly
relay, M0–M4, B1–B9/B13/B14, app-open PIN, standalone scrub.

---

## How to resume

```bash
cd /Users/wizops/DIPI/niyam
./gradlew :app:testDebugUnitTest     # unit suite green
./gradlew :app:assembleDebug         # app/build/outputs/apk/debug/app-debug.apk
```

`local.properties` (gitignored) must contain `sdk.dir=/Users/wizops/Android/Sdk`.
Environment used: JDK 20, AGP 8.7.2, Kotlin 2.0.21, Gradle 8.9, compileSdk 35
(auto-downloaded on first build), minSdk 29.

**Next milestone: human tablet beta.** Hand `app-debug.apk` (`0.2.0-beta9`,
built **with** `media.properties` filled in so downloads work — check
Setup shows `Media key: present`) to a tester with `docs/BETA-QA-CHECKLIST.md`. Every screen in design doc
§08 now exists. The two checks that most need real hardware are the Shelly
relay and an audio route — no Bluetooth amp or USB DAC has been on the bench,
so Bluetooth burst latency is still an unmeasured number.

---

## Sources of truth (read these before changing behaviour)

| What | Where |
|---|---|
| Milestones & engineering rules | `docs/ANDROID-APP-IMPLEMENTATION-PROMPT.md` (repo root `docs/`) |
| Product design + screen specs | `docs/handoff/README.md` |
| Engineering design doc | `docs/handoff/Gong Appliance Design Doc.dc.html` (open in a browser) |
| Interactive hi-fi prototype | `docs/handoff/Gong Appliance Screens.dc.html` |
| **Behavioural spec** | the unit-test suite under `app/src/test/` — ports of the Pi daemon; **the tests win any conflict** |

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
Column names identical to the Pi daemon's schema so a pulled DB stays readable by Pi tooling.
Entities: `course_types`, `courses`, `schedule_events`, `settings`, `state`,
`play_log`, **`media_slots`** (the Android delta — SAF URIs, not paths).
`GongRepository` is the only Room↔domain bridge. `SeedLoader` is idempotent and
backfills missing settings on every launch.

### M2 — player + service (`player/`, `service/`)
`PlayerEngine` carries the Pi daemon's queue rules (new gong aborts a running gong; doha
waits; stop drops everything and logs it). `AudioSink` splits "render once" out
so the rules are testable without an audio device; `ExoAudioSink` is Media3 with
`USAGE_MEDIA` + `CONTENT_TYPE_SONIFICATION`. `AudioRouter` falls back to the
speaker and records `state.route_last_ok`. `GongService` is the foreground
`mediaPlayback` service that owns everything.

### M4 — Compose UI (`ui/`)
Nav rail + Dashboard / Courses / Schedule grid / Logs, recreated natively from
`docs/handoff/`. `AppViewModel` reads Room and the service's flows only. The
five screens the handoff did not design are drawn inert with a lock glyph.
Two layout traps found by running it, worth remembering: `weight()` and a
nested `verticalScroll` are unbounded inside a scrolling column (runtime
crash), and the 98 sp hero wraps below the 1280x800 target width, so it steps
down and the pane scrolls.

### M3 — scheduler loop (`schedule/`)
`SchedulerEngine.tick()` + `AlarmScheduler`. `setAlarmClock` for the next
occurrence **and** a 30 s heartbeat. Guard committed before dispatch. Prune +
log-trim on day rollover. Route warm-up 15 s ahead.

---

## Parity checklist vs the Pi daemon

| Behaviour | Pi | Android | Covered by |
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
| Schedule editor | yes (web UI) | **yes** | M4 grid + inspector |
| Nullable gap/track = inherit | yes | **yes** | `SeedAndRepositoryTest`, inspector |
| Appliance TZ from config, not host | yes (`config.timezone`) | **yes** (`timezone` setting) | `ApplianceZoneTest` |

---

## Test inventory (130)

| Class | N | What it guards |
|---|---|---|
| `SchedulerCoreTest` | 19 | tick semantics, grace edges, toggles, doha resolution |
| `SchedulerEngineTest` | 21 | power cut, process death, reboot, clock jumps, pruning, active_course_id pin |
| `PlayerEngineTest` | 18 | burst timing (gap after strike), preemption, stop, missing media, route fallback |
| `ApplyOutcomeTransactionTest` | 2 | marks+logs land atomically; orphaned guards roll back |
| `SeedAndRepositoryTest` | 19 | seed idempotence, Pi column parity, guard atomicity, corrupt rows |
| `ClockTrustTest` | 7 | backwards jump, NTP recovery, confirm |
| `DstAndDayMathTest` | 6 | spring-forward gap, fall-back ambiguity, `/86400` regression |
| `ActiveCourseTest` | 5 | window, overlap, pin |
| `ScheduleMaterializerTest` | 5 | day precedence, doha injection |
| `FireRulesTest` | 6 | the window, in isolation |
| `DohaSlotsTest` | 4 | golden slot tables per course type |
| `ApplianceZoneTest` | 7 | timezone-setting resolution, IST fallback, dynamic-zone clock |
| `PinCodeTest` | 7 | PBKDF2 hash/verify, salting, stored format, 4–8 digit shape |
| `VirtualClockLedgerTest` | 4 | 400-day ledger over every course type (1.9 s) |

Two test-harness facts worth keeping:
1. Room's own executor is invisible to `runTest`'s scheduler. Any test that
   asserts on work done inside a **launched** coroutine must build the DB with
   `.setQueryExecutor { it.run() }.setTransactionExecutor { it.run() }`.
2. `PlayerEngine` burst timing is plain `delay()` (gap after each strike),
   so it runs on the test scheduler's virtual time with no clock injection.

---

## What's next

### (M4 is done — kept here for the parts still outstanding)
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

### M5 — the five undesigned screens + PIN
**Time** and **Setup** shipped 2026-08-09 in the beta screen review
(`ui/TimeScreen.kt`, `ui/SetupScreen.kt`): Time shows the appliance zone against
the device zone, sets `timezone` from a common list or a validated IANA id, and
confirms an untrusted clock; Setup binds a permission checklist to the real
`AppliancePermissions.status` and shows live scheduler state. Sounds, Audio out
and Network are still nav entries only and need visual design first (design doc
§08 says what each must answer); their locked cards now state what the screen
will do and that the schedule runs without it.

The PIN gate shipped 2026-08-08 as an **app-open** gate (stricter than the
per-tab design): `domain/PinCode.kt` (salted PBKDF2 into `admin_pin_hash`),
`ui/PinScreens.kt` (lock keypad + the PIN tab to set/change/remove it),
gate in `GongApp`. `Tab.requiresPin` is now moot — the whole app is behind
the PIN when one is set. As of the beta review the unlocked session is
released once the UI has been backgrounded past a 60 s grace window; before
that it was never reset, so on an appliance whose foreground service keeps the
process alive a single unlock persisted indefinitely.

### Amplifier relay — Shelly 1 Gen4 (2026-08-09)
`domain/RelayPlan.kt` (pure), `relay/ShellyClient.kt`, `relay/RelayController.kt`,
`ui/RelayScreen.kt` on the new **Amp power** tab. Spec:
`docs/superpowers/specs/2026-08-09-shelly-relay-design.md`.

Switches the amp on ~5 s before a play and off 5 s after, using the
`nextDeadline` the scheduler already computes on its 30 s heartbeat — **no new
alarms and no change to the fire path**. Consequence accepted deliberately: the
amp comes on 5–35 s early rather than exactly 5.

Rules worth not regressing:

- **The relay can never delay or fail a play.** The scheduler and player hooks
  are non-suspending and wrapped in `runCatching`; every request has a timeout,
  no retry queue, and a `tryLock` that drops rather than queues.
- **ON is rising-edge only** — re-sending each heartbeat would keep pushing
  `toggle_after` forward and defeat the watchdog it exists to be.
- **No OFF while playing, or while the next deadline is inside the pre-arm
  window** — otherwise a gong followed by doha powers the amp down and re-warms
  it cold.
- **A missed occurrence never reaches the player**, so the tick path holds a
  sticky armed deadline and switches off explicitly once it goes stale.
- `toggle_after` is a device-side watchdog and errs long on purpose; too short
  would de-power the amp mid-chant.

Adds `INTERNET` and a network security config — Gen2+ RPC is cleartext HTTP on
the LAN and neither existed before. BLE provisioning is explicitly out of scope:
the Shelly is joined to WiFi with Shelly's own app.

### Beta screen review (2026-08-09, `0.2.0-beta1`)
Six parallel audits, then implementation per screen. Full findings and the
triage rulings are in `docs/superpowers/plans/beta-screen-audit-matrix.md`.

Behavioural fix worth calling out: on **Schedule**, a day with no explicit rows
inherits the whole DEF pattern, but painted as empty — so one tap wrote a single
row and silently replaced that day's entire inherited schedule, under an "Event
saved" toast. Inherited rows now render dimmed and the first override takes two
taps. This reveals existing semantics; it does not change them, and selecting
"—" still writes SQL NULL for gap and track.

Also: adaptive layout so Stop / Add are never clipped on narrow landscape;
44 dp hit targets across all screens (painted sizes unchanged); `onNewIntent`
so `EXTRA_TAB` deep links work on a warm start; dark platform theme, removing
the grey status bar and white cold-start flash; window-inset handling for
targetSdk 35; masked PIN entry; scrollable keypad and nav rail.

### M6 — backup, audio route picker, doha SAF folder, first-run wizard
**Doha SAF folder shipped 2026-08-09** (`ui/DohaMediaScreen.kt`,
`domain/DohaPackMapper.kt`, spec in
`docs/superpowers/specs/2026-08-09-doha-media-folder-design.md`). Staff pick the
folder that directly holds the `D01`…`D11` files, with one optional descent into
a lone `doha/` child. Auto-map may only write slots that are empty or already
`auto`, so bundled debug tones and staff overrides survive a rescan; a file that
would displace one is reported as skipped. Two files claiming a slot become a
visible conflict — never a guess. Re-pick takes the new grant before releasing
the old, and a failed take changes nothing.

This lands on the **Sounds** tab but only implements the doha slot mapping part
of it — track choice, volumes, burst gap, doha time and no-course mode are still
unbuilt, so Sounds counts as **partial**, not done.

Still open in M6: backup/restore, audio route picker, first-run wizard.

Doha files auto-map by `D01`…`D11` prefix into `media_slots`; unmatched files
are listed as "unassigned", never guessed. Debug builds already ship 11
synthetic tones at `app/src/debug/assets/media/doha-test/`
(regenerate with `python3 tools/make_test_tones.py`).

### Light theme (2026-08-12, `0.2.0-beta10`)

Setup → Appearance now offers **Dark / Light / Follow device**, applied on the
tap with no restart. Dark stays the shipped default and the default is load
bearing: the appliance's home is a bracket on the wall of a dim hall, and a
tablet that turns white at 04:00 lights the room for everyone sitting in it.
Light is for the hour the same build spends on an office desk being set up.

How it was done matters more than what it looks like. Fifteen screens read
`Nocturne.Bg`, `Nocturne.Text`, `Nocturne.Neutral500` and friends by name,
directly. Threading a CompositionLocal through all of them would have been a
thousand-line diff with a thousand chances to miss one and leave a dark smear
on a white page. Instead every token in `Nocturne` became a getter over a
single snapshot-backed `Palette` slot, written by `GongTheme` before any child
composes. **No call site changed**, and a missed one is not possible.

The ramps are semantic, not literal, which is what makes that work:
`neutral300` always means "closest to the text colour" and `neutral800`
"closest to the background", so the greys run dark-to-light in `LightPalette`
where they run light-to-dark in `DarkPalette`. Same for `accent100..700` — 100
is always the high-contrast ink, 700 always the container fill.

Two things the flip exposed and fixed:

- **System bar icons.** They are drawn by the OS, outside the colour scheme.
  Left dark on a light page the clock and battery simply vanish, so
  `MainActivity` re-applies `enableEdgeToEdge` whenever the resolved mode
  changes.
- **The toast.** It was a 16 % amber wash with nothing opaque behind it —
  solid-looking over a near-black page, a smear you can read the screen
  through over a near-white one. It now paints `Surface` first.

`ThemeMode` is pure domain with its own tests, so an absent or hand-edited
`theme` row resolves to dark rather than crashing or rendering unpainted. The
setting rides along in backup/restore like any other.

Verified on the emulator, not just in tests: Dashboard, Schedule, Courses,
Logs, Sounds, Network and Setup all walked in Light, plus the course-type
dropdown (the one that historically fell back to the M3 baseline browns — it
renders white). The toast fix is the one thing screenshotted only in its broken
state; the corrected frame proved too transient to capture.

### Gongs not firing on a real tablet (2026-08-11, `0.2.0-beta9`)

Reported from a Pixel C at a centre: the app looked healthy and no gong ever
sounded. Diagnosed against the device's own database and logs. **Two separate
causes**, and only the second was a bug.

**1. The clock was untrusted, and that is the appliance working.** `state`
held `last_good_time = 2026-08-12T04:56:00Z` while the device clock read
`04:13:43Z` — 42 minutes backwards, well past the 10-minute
`ClockTrust.BACKWARDS_TOLERANCE`. `SchedulerCore.tick` returns an empty outcome
on an untrusted clock, so nothing fires at all until someone confirms. The
Dashboard banner and the foreground notification both said so. Confirming the
clock on the device restored it: clock `trusted`, next gong armed.

Worth watching in the field: an ordinary NTP correction can exceed ten minutes
on a tablet whose clock has drifted, and each time it does the appliance goes
silent until a human taps Confirm. That is the design ("silence beats a wrong
gong") but it is now a known operational cost, not a surprise.

**2. A miss consumed the double-fire guard — the real bug.** The same tablet had
been running in `America/New_York` and its zone became `America/Los_Angeles`.
The day re-materialized three hours earlier, every occurrence that had already
passed was logged `missed` — correct — but the miss also wrote
`fired:<key>:<date>`. When 21:00 *Los Angeles* genuinely arrived, that guard
already existed, so the gong resolved to ALREADY_FIRED and was suppressed:
no sound, no log row, nothing to see.

Fixed by splitting the two marks, which answer different questions:

- `fired:` means "this made a noise" and guarantees never-twice. Written only
  when sound is dispatched. Unchanged and still sacred.
- `missed:` means "we already logged this as missed" and exists solely to stop
  the 30 s heartbeat flooding the log. It never blocks a fire.

Anything that moves the wall clock — a timezone change, an NTP step — can make a
missed occurrence genuinely due again, and it must be allowed to ring.
`SchedulerCoreTest.logsMissedOneSecondPastGrace` asserted the old invariant
("still consumes the slot, so it cannot fire later in the day") and was
inverted, with the reason recorded inline.

Suite 393 green (+4). The stale guards on the tablet were for 2026-08-11 only
and are pruned on day rollover, so no device surgery was needed.

### Schedule grid fix (2026-08-11, `0.2.0-beta8`)

**The Schedule grid was drawing nothing** — 335 seeded rows in the database and
a blank pane. Two faults, and the second is the one worth remembering.

First, the explanatory subtitle added in beta7 went inside the header `Row`
via `ScreenTitle`. Its subtitle is an unconstrained `Text`, which in a `Row`
takes the whole width, so the course-type picker was squeezed to nothing and
vanished. The title is now a plain `Text` in the row and the subtitle sits
below it.

Second, and the real one: the left column held a title, a three-line paragraph,
the grid at `weight(1f)`, and an add-a-time form. On a 1280x800 tablet that
column is about 411 dp tall, and the fixed-height children claimed all of it —
so the weighted grid resolved to **zero height** and rendered nothing, silently.
A `heightIn(min = 220.dp)` did not help, because a min larger than the parent's
max is coerced away. The fix was to stop competing for the height: the add-time
form moved into the right rail under the inspector, and the paragraph is now one
line. The grid is the screen; a form is not.

Worth stating plainly: **the whole suite was green through all of this.** 389
unit tests cover the schedule domain and none of them can see a Compose layout
that computes to zero height. This was found by screenshotting the running app,
which is the only thing that would have found it.

Logs also had columns clipped off the right edge — RESULT and DETAIL, which are
most of the reason anyone opens that screen. Cell type is down to 11 sp, headers
to 9.5 sp, and every column is narrower; all six now fit.

### Screen simplification (2026-08-11, `0.2.0-beta7`)

Six screens lost material that looked informative but gave staff nothing to act
on. The theme: an appliance screen should either answer a question or offer a
decision, and anything else is noise a server has to read past at 04:00.

**Time now follows the tablet.** `ApplianceZone` forced `Asia/Kolkata` and made
install day include picking a zone from a list — one more thing to get wrong,
and getting it wrong moves every gong by hours. Blank now means "follow the
device", which a tablet installed at the centre already knows and keeps knowing
across DST without anyone touching the app. An unparseable id also defers to the
device rather than to a hardcoded fallback: a corrupt row should leave the
appliance on the tablet's own local time, very likely right, instead of silently
relocating the schedule. The pin survives as the escape hatch for a donated
phone that insists it is elsewhere, and it is deliberately the last card on the
screen — putting it first invites people to set it "just in case". **Tablets
already seeded with `Asia/Kolkata` keep it**, because `insertMissing` never
rewrites a seeded setting; only fresh installs follow the device. AGENTS.md
hard rule 5 was rewritten to match rather than left contradicting the code.

**Logs** dropped the `WHEN (UTC)` column. `ts_utc` is still what the database
stores and orders by; showing it beside the local time asked the reader to
reconcile two numbers that always mean the same instant.

**Network** dropped the "what needs a connection" table and the paragraph
explaining that hotspot detection is a heuristic. Both were true and neither
changed what anyone would do.

**Sounds** dropped the eleven-row doha slot table. It exposed the internals of
`DohaSlots.legacyModular` — which slot serves which course day — to people who
have no decision to make about it: that mapping is the verified PHP port and is
not theirs to change. What staff actually do is point the appliance at a folder
or download the tracks, and the folder card's mapped count already says whether
that worked. About 200 lines of now-dead table code went with it.

**PIN** is no longer a nav entry; it is a card on Setup. It is an install-day
decision made once by the same person working through the OS grants, not
somewhere staff visit. The nav rail is down to nine entries.

**Schedule** gained the explanatory subtitle it was missing (days across, times
down, what DEF means, what a blank gap/track means). The grid, the DEF column
and the cell inspector already matched the target design.

**Groundwork for pulling calendars from dhamma.org.** Centre schedules are
public and canonical, so a tablet could stay current instead of shipping one
centre's hand-transcribed year. `tools/export_centres_json.py` turns the public
directory HTML into a centre list (subdomain, name, place, region, schedule
path); the design for the fetch itself is in
`docs/superpowers/specs/2026-08-11-runtime-course-calendar-design.md`. Nothing
fetches yet, on purpose: a misparsed start date moves day 0 and every gong with
it, so any import must be reviewed before it fires and a parse failure must mean
"keep what we have", never "this centre has no courses".

Suite 389 green.

### Course calendar + backup/restore (2026-08-11, `0.2.0-beta6`)

**The tablet arrives knowing its year.** `seed/courses-sudha-2026-2027.sql` —
39 courses from 15 Jul 2026 to 15 Dec 2027, transcribed from Dhamma Sudha's
published calendar — is flattened by `tools/export_courses_json.py` into
`assets/seed/courses.json` and applied on first launch. The `.sql` stays the
source of truth because it is what diffs against next year's schedule; the app
does not parse SQL.

Two guards, meaning different things. A `courses_seeded_at` state marker means
"this build already offered its calendar", and it survives staff emptying the
table — deleting all 39 is a decision, and re-adding them next launch would be
the appliance arguing back, each row silently starting a schedule. Separately,
a non-empty courses table blocks the calendar entirely, so it can never land on
top of hand-entered courses and create permanent overlapping windows. Rows
naming an unknown `course_type_id` are dropped rather than inserted: such a
course resolves to no schedule, so it would sit in the list looking real and
ring nothing.

**Backup is configuration, not state**, and that distinction is the whole
design. Copying `gong.db` would have been less code and much worse:

- `state` holds the `fired:<key>:<date>` guards. Restoring yesterday's — or
  another tablet's — tells the scheduler today's gongs already rang. The
  appliance would sit through a morning in silence with nothing odd in the log.
  This is the worst outcome available to a restore feature, and the format has
  no field capable of expressing it.
- `play_log` is history and belongs to the device that lived it.
- `admin_pin_hash` is excluded so a restore cannot lock staff out with a PIN
  nobody here remembers. `relay_auth_pass` is a credential and stays out of a
  plaintext file. `active_course_id` and `doha_tree_uri` reference ids and SAF
  grants that do not survive the trip.

Media slots travel but arrive **unverified** — a document URI from another
tablet is meaningless here, and a green "verified" beside an unopenable file is
exactly the lie the Sounds screen exists to avoid. Restore runs in one Room
transaction: a half-applied restore would leave the old schedule gone and the
new one incomplete, with the appliance running on the wreckage.

The UI is two taps with the numbers in between — the confirm sheet states both
"on this tablet now" and "in the backup" before writing, because "replace your
12 courses with 39" is a very different decision from "restore".

Suite 387 green (+32: 13 backup domain/round-trip, 11 course seeding, plus the
Fable slice's).

### Fable gap iteration (2026-08-11, `0.2.0-beta5`)

A parallel Fable design export landed as a read-only reference. It was mined
for UX and reliability gaps only — DIPI's verified schedule domain, Shelly
relay, CDN download pipeline and Nocturne chrome all stayed. In particular
`DohaSlots.legacyModular` was **not** replaced with Fable's simpler
`floorMod(day-1, 9)`: ours is the anapana-aware PHP port with golden tests,
theirs is a best guess.

**G3 — between-courses doha is now deterministic.** `pickSlot` took a `Random`;
the same calendar day could resolve to a different doha on every
re-materialize, so the fired-guard and the play_log could disagree about what
"today's doha" was. It now takes the occurrence's `LocalDate` and derives the
slot from the epoch day. The `Random` parameter is gone from `SchedulerCore`
entirely rather than defaulted, so non-determinism cannot creep back. Bonus
property: the stride is coprime with 11, so eleven consecutive days walk the
whole set with no repeat — better than true random, which happily plays the
same doha twice running.

**G1 — a third keep-alive belt.** The exact next-fire alarm and the 30 s
in-service heartbeat both assume the service is alive; an OEM battery killer
breaks that assumption silently, and nobody finds out until 04:00. A 15-minute
`LivenessWorker` (WorkManager, so the OS owns the schedule) checks whether the
service is running and, if not, arms a near-immediate kickstart alarm. It
cannot start the service itself — background code may not start a
`mediaPlayback` FGS — but alarm delivery opens a short allowlist window, and
`KickstartReceiver` spends it on exactly one `startForegroundService`.
`BootReceiver` now takes both paths, so an API 34+ boot that refuses the direct
start costs seconds rather than a morning. Neither existing belt was touched.

**Liveness is now visible.** New pure `domain/Liveness.kt`: a tick older than
90 s (three missed heartbeats) is STALE, no tick yet is UNKNOWN, and a tick
from the future is treated as alive because that is the clock-trust problem and
already has its own banner. Dashboard's Scheduler row reads
`alive · 6s ago` / `STALLED`; Setup's last-tick row shows the age, not just a
timestamp — a clock reading 04:12:31 tells nobody whether the loop is running.

**Sounds is no longer mapping-only.** Gong track, burst gap and gong volume;
doha time, between-courses mode (rotate / fixed slot / off) and doha volume —
all wired straight to the settings rows the scheduler and player already read.
Volumes stayed 0–100 integers (DIPI's stored format) rather than adopting
Fable's 0–1 floats. Steppers rather than sliders throughout: this is a wall
tablet tapped in passing, and a −/+ pair has no drag to mis-land. Doha time is
validated through `ScheduleMaterializer.parseHhMm`, the scheduler's own parser,
so the screen cannot accept a string the materializer would silently ignore.
Slot rows now carry a role (metta, homage, day pattern).

**Setup answers one question honestly.** New pure `domain/Readiness.kt`
aggregates grants **and** service liveness **and** whether a PIN is set into a
single READY / NOT READY banner that names the first blocker rather than
counting them. Grants alone were never enough: a tablet with every permission
and a frozen scheduler is not field-ready, and that is exactly what an OEM
killer leaves behind. The checklist now re-polls every second, so a grant
changed in system settings flips the banner without reopening the app.

**Audio out** polls devices every 2 s instead of 4, and records *when* a route
last carried a finished burst (`state.route_last_ok_at`), not just which one —
a stamp three weeks old is how staff spot an amp that has been quietly dead.

Shared Nocturne controls moved into `ui/Controls.kt` (Stepper, ChoiceChip,
Banner, `rememberNow`), replacing the private copies in RelayScreen.

Suite 355 green (+21: Liveness 8, Readiness 6, DohaSlots 7).
Residual risks are unchanged and still hardware-shaped: no Bluetooth amp or USB
DAC has been on the bench, no real Shelly, and the WorkManager kickstart has not
been proven against a real OEM battery killer — force-stop behaviour in
particular is device-specific and is now a QA item.

### Audio out and Network (2026-08-10)

The last two screens from design doc §08, and with them the end of the nav
rail's padlocks.

**Audio out** is a route picker, a test per route, and a last-known-good
indicator. The substantive part was not the screen: `AudioRouter.resolve()`
already computed a route, but its answer only ever reached the log — nothing
was passed to the sink, so playback went wherever Android chose. A picker on
top of that would have been decorative. The resolved device id now travels
`PlayerEngine → AudioSink.play → ExoPlayer.setPreferredAudioDevice`, looked up
again at play time so a Bluetooth amp that dropped since resolution falls back
to the speaker rather than failing the burst.

Two rules the screen exists to make visible:

- **Chosen and effective are different things.** A route that is not attached
  when an alarm fires falls back to the built-in speaker (a gong from the wrong
  speaker beats no gong, §06/§10). The unattached row keeps its place in the
  picker and the headline turns amber — the failure being prevented is a centre
  discovering in week three that every morning gong came out of the tablet.
- **Testing a route does not select it.** `PlayCommand.routeKey` renders one
  burst through a named route without touching the `audio_route` setting.
  Auditioning an amp by pointing the whole appliance at it, and forgetting to
  point it back, is how the above happens.

The fallback rule itself moved into pure `domain/RoutePlan.kt`; `AudioRouter`
now only supplies what Android reports and maps the answer back to a device.

**Network** is informational and says so repeatedly — the appliance runs in
airplane mode indefinitely, and only doha downloads want a connection, so every
red state on the screen is written to reassure rather than alarm. It reports
mode, address, metered and validated state, and refuses to guess about the two
things Android will not tell it: the SSID (withheld without a location
permission this app deliberately does not request) and tethering state (no
public API since Android 9, so the hotspot card reads interface names and is
labelled a guess). Parsing lives in pure `domain/NetworkFacts.kt`;
`net/NetworkProbe.kt` only fetches, and every lookup is wrapped — a probe that
throws is a blank row, never a missed gong.

Suite 334 green (+33). New: `domain/RoutePlan.kt`, `domain/NetworkFacts.kt`,
`net/NetworkProbe.kt`, `ui/AudioOutScreen.kt`, `ui/NetworkScreen.kt`.
`ACCESS_WIFI_STATE` added; `ACCESS_FINE_LOCATION` deliberately not.

### Doha download pipeline (2026-08-09, `0.2.0-beta2`)

The app ships no doha audio; on request it downloads ciphertext from the
legacy CDN (`apt.vridhamma.org/updates/v2/`), verifies it against a bundled
dual-checksum catalog (`assets/doha_manifest.json`, 11 assets), decrypts the
OpenSSL `Salted__` AES-256-CBC envelope (EVP_BytesToKey MD5 — compatibility
with the existing distribution format, owner holds the rights), verifies the
plaintext (size + SHA-256 + ID3/MPEG magic) and only then promotes it to
`{appFiles}/audio/ready/`, the sole place playback reads from. Half-broken
files repair themselves: truncated downloads resume with `Range`, corrupt
ready files re-decrypt from the kept ciphertext, misplaced ciphertext in
`ready/` is moved home, junk is quarantined. All decisions live in pure
`domain/AssetResolve.kt` (42 JVM tests); IO lives in `assets/` (`AssetStore`,
`CdnDownloader`, `OpenSslSaltedAes`, `StorageLocator` depth-2 scan,
`AudioAssetManager` single-flight orchestrator — 64 more tests).

Ready tracks register into empty `media_slots` rows as source `downloaded`;
precedence is manual > bundled > folder pack (`auto`) > `downloaded`, so a
staff-picked SAF folder always outranks downloads and nothing ever
auto-overwrites a manual assignment. Sounds gained a Downloads card: per-track
progress, retry, "Download all dohas (~470 MB)" with a mobile-data confirm,
storage scan, and a "no media key" banner when the build lacks the passphrase.

The media passphrase is injected at build time from `local.properties`
(`niyam.mediaPassphrase`) or `NIYAM_MEDIA_PASSPHRASE` into
`BuildConfig.MEDIA_PASSPHRASE`; it is never committed, logged, or shown.
A keyless build still plays already-verified files. Live-CDN "download all"
remains a manual QA item (`docs/BETA-QA-CHECKLIST.md`).

### M7 — hardening before any Play upload
Battery-optimisation guidance, R8 rules, privacy stub, permission rationale
strings, media-pack install docs.

---

## Known gaps / decisions still open

- **Ran on an emulator, never on real hardware.** The end-to-end fire was
  verified on the `cca34` AVD (API 34): seed loaded, a 10 Day course starting
  today showed Day 0, an event ~75 s ahead was picked up by the heartbeat,
  `setAlarmClock` woke the service at `19:13:00.006`, the burst fired, and
  `play_log` recorded `gong|ting.mp3|3|ok`. Earlier events that day logged
  `missed` rather than blasting late.
  The overnight-on-a-tablet run from design doc M1 has **not** happened, and
  OEM battery behaviour (risk #1) is untested by definition.
- Emulator note: the `Android31`, `dhamma` and `dhammaplay` AVDs have broken
  `image.sysdir` paths — only `cca34` boots, because android-34 is the one
  system image installed.
- The UI was exercised on a **phone-shaped** emulator, not a 1280x800 tablet.
  It degrades correctly there, but the design's fixed 394 dp columns have not
  been seen at their intended size.
- Release builds ship **no doha audio at all** — `media_slots` is empty until
  staff sideload a pack, and the dashboard is expected to show `GONGS ONLY`.
- `relay_enabled` is retained in settings for Pi parity and is inert.
- Design doc §14 open questions are all still open; #1 (which tablet model)
  blocks M1-field-testing in the design doc's own numbering.

---

## External review (2026-08-08)

Code review + factory-reset reinstall handoff for Fable:

→ **`docs/FABLE-REVIEW.md`**

Contains design/plan/implementation overview, P1–P3 findings, reinstall steps, and next-work queue.

### Review fixes applied

- **B1 (2026-08-08, Fable): appliance timezone.** The scheduler clock no longer
  uses `ZoneId.systemDefault()`. `domain/ApplianceZone.kt` resolves the
  `timezone` setting (Pi parity: the daemon's config defaulted
  `Asia/Kolkata`); blank or unparseable values fall back to IST — important
  because devices seeded before this fix carry a persisted `timezone=""` row
  that `SeedLoader.insertMissing` will never overwrite. `SystemGongClock` now
  takes a zone *provider*, and `GongService` re-reads the setting on every
  poke/time-change, so a future Time-screen edit takes effect without a service
  restart. The dashboard hero clock, course "today" resolution, and the
  zero-day zone label all follow the appliance zone, not the device zone.
  Covered by `ApplianceZoneTest` (7 tests).
- **Review batch (2026-08-08, Fable):** **B2** `applyOutcome` now runs in one
  Room transaction (`ApplyOutcomeTransactionTest` proves an orphaned guard
  rolls back). **B3** strike gap is now counted *after* the strike ends (Pi
  parity, user-confirmed); the `elapsedMs` clock injection is gone. **B7**
  `LOCKED_BOOT_COMPLETED` removed — no direct-boot start against a
  credential-encrypted DB. **B8** overlapping courses paint `OVERLAP` (amber),
  never a second `ACTIVE`. **B13** teardown deadlock fixed: scope cancelled
  first, `release()` does no Room writes, sink frees on `Main.immediate`.
  Also: pump-slot race in `PlayerEngine.drain` closed (a submit landing while
  the pump exits no longer strands its command), Logs empty-state actually
  renders, doha test toast no longer names a path that never existed, and
  course delete is a two-tap confirm. **B4** closed as *keep Pi behaviour*
  (user-confirmed): day-0 doha stays on `no_course_doha`.
- **Review improvements (2026-08-09):** **B6/B14** first-run surface —
  `AppliancePermissions` + dashboard health rows open exact-alarm, battery, and
  notification system settings; notification runtime request on open; status
  refreshed on every resume. Untrusted clock row is tappable to confirm.
  **B9** `SchedulerEngine.tick` persists the resolved `active_course_id`
  (clears when none; honours pin on overlap). Three new unit tests.
