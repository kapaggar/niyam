# Gong Android appliance — build progress & restart point

Standalone repo **`kapaggar/niyam`** (branch `main`), extracted with history from the gongserver monorepo's `android/` tree.

Last updated: 2026-08-10, **Audio out and Network** (`0.2.0-beta4`,
`versionCode` 5) — the last two locked screens shipped, so the nav rail has no
padlocks left. Audio out also made the
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

**Next milestone: human tablet beta.** Hand `app-debug.apk` (`0.2.0-beta4`,
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
