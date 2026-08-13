# Niyam 0.2.0-beta11 — human QA

Device: ________  Android: ________  Build: debug APK (`versionCode` 12)

Check **Setup → Appliance state** on the tablet before you start:

- **Build** must read `0.2.0-beta10 (11)`. If it does not, the install did not
  take — reinstall before reporting anything.
- **Media key** tells you which doha-download section applies. `present` means
  downloads should work; `absent — doha downloads off` is a deliberate build
  state, not a fault on the tablet.

APK: `app/build/outputs/apk/debug/app-debug.apk`

This build is the first pass of the beta screen review. Every item below was
changed or is a known gap — please try to break them in that order.

---

## Install

- [ ] Install APK; launcher shows the gong icon
- [ ] Grant notifications when prompted
- [ ] Open app; service notification appears
- [ ] **No white flash on cold start** — the app should come up dark, with a dark
      status bar (this was a light theme before)

## Dashboard

- [ ] Time readable from ~2 m
- [ ] No course → "add a course to start the schedule" **and the timezone** are
      both shown
- [ ] Test gong: hear strikes; bell rings; **Stop is fully visible, not cut off**
- [ ] The test gong is now the **Single_Gong recording** — one clean ring per
      strike, not the old ting
- [ ] Double-tap Test gong while it is ringing — it should be **ignored**, not
      restart the burst
- [ ] GONGS ONLY chip visible when no doha pack
- [ ] Health rows: tap an amber row → the matching system settings page opens.
      Rows are now finger-sized; check they are comfortable on a wall tablet
- [ ] Only **one** event in the two "next events" columns is accent-coloured

## Courses

- [ ] Fresh install → Courses shows an **empty state** explaining day 0
- [ ] Add 10 Day, start = today → Dashboard shows Day 0
- [ ] Row shows the **window end date** under the start date
- [ ] Bad dates give distinct messages: blank vs `2026-8-5` vs `2026-02-30`
      (none of them should say "Pick a start date" when you did pick one)
- [ ] Delete is two-tap; the row does not jump sideways when armed
- [ ] Overlap: two courses covering today → exactly one ACTIVE, one OVERLAP

## Schedule

- [ ] **The grid actually draws.** Days 0…N plus DEF across the top, wall-clock
      times down the left, `xN` in the cells. A blank grid is the bug that
      shipped in beta7 and it must not come back
- [ ] The course-type picker sits beside the title and switches the whole grid
- [ ] "Add a time" is in the right rail under the inspector, not under the grid
- [ ] Grid shows days 0…N plus DEF
- [ ] **Days with no rows of their own show their inherited rows dimmed** —
      they are not blank
- [ ] Tapping an empty cell on such a day **warns first and does not write**;
      only the second tap creates the row
- [ ] Scroll right on a 20/30/45 Day type — **the wall-clock times stay pinned**
      on the left
- [ ] Select a cell → inspector; set gap/track to "—" (inherit), reopen, still "—"
- [ ] Adding a time that already exists is refused with a message
- [ ] Track chips read **single gong / sikkim gong**; pick sikkim gong on a
      6-repeat row → the file plays **twice** (3 hits per play = 6 hits), with
      the gap between the two plays
- [ ] Remove event asks twice

## Logs

- [ ] Test gong appears as `ok`
- [ ] **One time column**, in the appliance zone — the old UTC column is gone
- [ ] **All six columns fit without scrolling** on the tablet: WHEN, WHAT, FILE,
      x, RESULT, DETAIL. RESULT being clipped off the right edge was the beta7
      bug — that column is half the reason anyone opens this screen
- [ ] Filters work; with `missed` selected and nothing matching, the message
      says so rather than "Nothing logged yet"
- [ ] Columns do not vanish when the window is narrower

## PIN (now on Setup, no longer its own tab)

- [ ] There is **no PIN entry in the nav rail**; PIN lives on Setup
- [ ] Setup → PIN card: set a PIN → digits are **masked** while typing
- [ ] Kill app → reopen → lock shows, **no dashboard flash**
- [ ] Wrong PIN rejected; repeated wrong attempts get slower
- [ ] **Unlock, press Home, wait over a minute, return → it asks for the PIN again**
- [ ] Unlock, go to system settings via a health row and come straight back →
      it should **not** re-ask (that is the grace window working)
- [ ] Remove PIN asks twice and is styled as destructive
- [ ] On a phone in landscape, the keypad scrolls — the OK key is reachable

## Time — now follows the tablet (changed in this build)

- [ ] Time shows **one clock**, tagged with the zone, and says it is taken from
      the tablet
- [ ] Change the tablet's timezone in Android settings → the Time screen and the
      Dashboard "today" follow it, with **no app setting to touch**
- [ ] "Override the zone" → pin `Europe/London` → the tag reads PINNED and the
      clock moves; "Follow the tablet" puts it back
- [ ] A nonsense zone id is rejected, not silently accepted
- [ ] **A tablet upgraded from an older build keeps firing in `Asia/Kolkata`** —
      the seeded row is not rewritten. Only fresh installs follow the device
- [ ] Confirm clock works when the clock is untrusted

## Setup
- [ ] Setup checklist matches the real permission state — an amber row must
      never be green when the grant is actually missing

## Sounds — doha pack folder

The eleven-row slot table is gone; the folder card's mapped count is now the
signal that a scan worked.

- [ ] Sounds → pick the folder that **directly contains** `D01…D11` mp3s →
      the card reads 11 of 11 mapped and the Dashboard GONGS ONLY chip clears
- [ ] Pick a *parent* folder instead → empty state naming the `D01…D11`
      convention, not a silent no-op
- [ ] Pick a folder whose only match is inside a single `doha/` subfolder →
      still maps (one level down only)
- [ ] Re-pick a different folder → remaps cleanly, no stale path shown
- [ ] Reboot the tablet → the folder is still readable, slots still mapped
- [ ] Play a test doha → the mapped file is what you hear

## Amp power — Shelly relay (new)

Provision the Shelly onto the centre WiFi with the **Shelly app** first; Niyam
does not do BLE setup. Give it a DHCP reservation so the IP is stable.

- [ ] Amp power → enter the Shelly IP → **Test connection** reports model and MAC
- [ ] Wrong IP → clear error, and reachability shows **unreachable** (not
      "unknown", and never green)
- [ ] Before any test, reachability reads **not probed yet** — neither green nor red
- [ ] Manual Amp on / Amp off actually switch the relay
- [ ] Enable Relay on the Dashboard (only possible once a host is set)
- [ ] Schedule a gong +2 min → **the amp switches on shortly before it and off
      after** (on may come up to ~35 s early — that is by design)
- [ ] Gong followed by a doha a minute later → the amp does **not** drop out
      between them
- [ ] **Power the Shelly down, then let a gong fire → the gong still rings on
      time.** This is the single most important check on this page
- [ ] Kill the app mid-play → the amp powers itself off within the watchdog
      window rather than staying on all night
- [ ] Set a device password on the Shelly, enter it in Niyam → Test still passes,
      and the password is never displayed back

## Doha downloads — CDN pipeline (new)

The QA build must be made with the media passphrase present: copy
`media.properties.example` to `media.properties` (gitignored), fill in
`niyam.mediaPassphrase=`, then `assembleDebug`. The build prints
`niyam: media key present` and Setup reads `Media key: present`. Without it
every track shows the "no media key" banner — that state is itself the first
check below.

- [ ] Build **without** the passphrase → Sounds shows one banner "This build
      has no media key", rows read Unavailable, and nothing crashes
- [ ] Build **with** it → 11 rows D01…D11 with titles, all "Not downloaded"
      on a cold install, plus the first-use note about download size
- [ ] Download one track on WiFi → progress counts up in MB → "Preparing…" →
      "Ready", and its slot appears in the doha slot list as source
      `downloaded`
- [ ] Play a test doha for that slot → you hear the downloaded track
- [ ] Kill the app mid-download → reopen → retry → completes (it should
      resume, not restart from zero — watch the starting MB figure)
- [ ] Switch to mobile data → per-track Download asks before spending ~45 MB;
      "Download all" asks about ~470 MB; declining does nothing
- [ ] Airplane mode + not downloaded → clear "No connection" error, no crash,
      and an already-Ready track still plays
- [ ] "Download all dohas" on WiFi → all 11 reach Ready (long; leave it
      plugged in) — this is acceptance for the live CDN
- [ ] Reboot → Sounds still shows Ready without re-downloading (startup
      re-index)
- [ ] Truncate test if you can shell in: delete a few KB off a ready file →
      request it again → it repairs without redownloading (re-decrypts from
      the kept ciphertext)
- [ ] Copy one catalog mp3 to `/sdcard/common-general/` → "Scan storage for
      existing media" finds and imports it (may legitimately find nothing
      under scoped storage — note which)
- [ ] `adb logcat` during a download in a release-style run: no passphrase,
      no `Salted__`, no key/iv bytes anywhere

## Audio out (new)

The route now genuinely steers playback — before this build the picker would
have been decorative, because the resolved route never reached the player.

- [ ] With nothing attached: one row, **Built-in speaker**, ticked, and the
      headline reads that the chosen output is attached
- [ ] Test on the speaker row → the gong rings, and **Last played** becomes
      Built-in speaker
- [ ] Plug in a USB audio interface → a **USB** row appears within a few
      seconds without leaving the screen
- [ ] Test the USB row **without selecting it** → sound comes out of the USB
      device, and the ticked row is still the speaker. This is the whole point
      of the screen; if testing silently re-points the appliance, stop and report
- [ ] Now select USB → Test gong on the **Dashboard** also comes out of USB
- [ ] Unplug it while USB is still selected → the row stays, marked
      **NOT ATTACHED**, headline turns amber and reads FALLING BACK
- [ ] **Fire a real scheduled gong in that state → it rings from the built-in
      speaker, on time.** This is the single most important check on this page
- [ ] That gong's Logs row says `ok`, with a detail naming the unavailable route
- [ ] Pair a Bluetooth speaker in system settings → a Bluetooth row appears;
      select and test it. Expect a small extra delay before the first strike —
      note whether the burst rhythm is acceptable in a hall
- [ ] Kill the app (service stopped) → Test buttons render inert rather than
      failing silently

## Network (simplified)

Informational only. Nothing here should ever suggest the schedule is at risk.
The "what needs a connection" table and the hotspot caveat box are gone.

- [ ] On the centre Wi-Fi → mode reads **Wi-Fi**, address matches what the
      router shows for that tablet
- [ ] Network name shows either the real SSID or **name withheld** with the
      explanation — never the literal `<unknown ssid>`
- [ ] Airplane mode → **OFFLINE**, and the copy still says the schedule is
      unaffected. Confirm a scheduled gong then fires normally in airplane mode
- [ ] Join a Wi-Fi with a sign-in page → **NO INTERNET** amber, not green
- [ ] On mobile data → **METERED** tag appears (matches what Sounds warns about
      before a download)
- [ ] Turn on the tablet's own hotspot → Hotspot card flips to **LOOKS ACTIVE**
      within a few seconds. If it does not, that is a known-heuristic miss, not
      a bug — note the device model
- [ ] Both **Open Wi-Fi settings** and **Open hotspot settings** land somewhere
      sensible; neither crashes if the OEM lacks the screen
- [ ] Return from system settings within the PIN grace window → no re-prompt

## Service liveness — the third belt (new)

The appliance now has three keep-alive paths: the exact next-fire alarm, the
30 s in-service heartbeat, and a 15-minute WorkManager check that arms a
kickstart alarm when the service has been killed. The first two assume the
service is alive; this section attacks that assumption.

- [ ] Setup → Appliance state → **Last tick** shows a time *and* an age
      ("6s ago"), green
- [ ] Leave the app on Setup for two minutes — the age never climbs past
      about 35 s on a healthy tablet
- [ ] **Kill the app from recents 1 min before a fire → the gong still
      fires.** The service is separate from the activity; if this fails
      nothing else in this section matters
- [ ] Kill it from recents and wait — within ~15 min the notification
      returns on its own (WorkManager → kickstart). Note how long it took
- [ ] **Reboot 5 min before a fire** → notification returns, fire lands
- [ ] Force-stop from App info (harsher than recents) → confirm whether it
      comes back; on some OEMs force-stop blocks WorkManager until the app
      is opened once. Record the device and the result
- [ ] Dashboard health shows **Scheduler: alive · Ns ago** while running,
      and **STALLED** if you manage to freeze it

## Sounds — gong and doha settings (new)

- [ ] Track chips read single gong / sikkim gong; changing it changes what
      Test gong plays
- [ ] Gap stepper changes the silence between strikes on a multi-strike test
- [ ] Gong volume at 20% is audibly quieter than 90%; note that Android's
      own media volume multiplies it
- [ ] Doha time: type `06:37` → Save → the Dashboard "next events" doha
      moves to match
- [ ] Type `25:00` or `6.37` → refused with a message, nothing saved
- [ ] Between courses → **rotate daily**: with no active course, note the
      slot the Dashboard predicts, then re-open the app — **it must predict
      the same slot** (this is the determinism fix; a different slot is a bug)
- [ ] Between courses → **fixed** → pick a slot → that slot is used
- [ ] Between courses → **off** → no doha appears in next events; gongs
      unaffected
- [ ] Doha volume changes Test doha loudness
- [ ] Slot table shows roles (metta on 10, homage on 11)
- [ ] Put a stray `notes.txt` in the doha folder → listed under "In the
      folder but not named D01…D11", not silently ignored

## Setup READY banner (new)

- [ ] With everything granted and the service ticking → **READY** in green
- [ ] Deny any one grant → **NOT READY**, naming that specific blocker
- [ ] Remove the PIN → NOT READY says so (a public tablet with no PIN is
      not field-ready)
- [ ] Change a grant in system settings and come straight back → the banner
      flips **without** force-reopening the app (it re-polls every second)

## Course calendar seeded at install (new)

This build ships **Dhamma Sudha's** calendar, 39 courses from 15 Jul 2026 to
15 Dec 2027 (`seed/courses-sudha-2026-2027.sql`). If the tablet is for a
different centre, say so before it goes out — the calendar is centre-specific.

- [ ] **Fresh install** → Courses already lists 39 courses without anyone
      entering one, and the Dashboard resolves today's course day if today
      falls inside a window
- [ ] Spot-check three rows against dhamma.org/schedules/schsudha: a 10-Day,
      the Satipatthana (21 Oct 2026), and a 3-Day (30 Jul 2026)
- [ ] Add a course by hand, then reinstall over the top → **your course
      survives and 39 are not added again**
- [ ] Delete every course, force-stop, reopen → **the calendar does not come
      back**. Staff who emptied it meant it
- [ ] No two courses share an arrival date (no permanent OVERLAP badge)

## Appearance — light and dark (new)

Setup → Appearance. Dark is the shipped default and must stay so on a fresh
install: a wall tablet that lights the hall at 04:00 is a defect, not a taste.

- [ ] Fresh install (or after clearing data) → **Dark** is selected
- [ ] Tap **Light** → the whole shell repaints on the tap, no restart, and the
      status-bar clock and battery icons turn dark so they stay visible
- [ ] Walk every tab in Light — Dashboard, Schedule, Courses, Logs, Sounds,
      Audio out, Network, Time, Setup — and confirm **nothing is unreadable**:
      no white-on-white text, no invisible borders, no dark card on a dark page
- [ ] The Dashboard hero clock and countdown are still legible from 2 m
- [ ] Open a dropdown (Courses → course type) in Light: the menu is light, not
      a dark rectangle
- [ ] Tap **Follow device**, then flip the tablet's own dark mode in Android
      settings → the app follows without being reopened
- [ ] Choice survives a restart: set Light, force-stop the app, reopen → Light
- [ ] Backup taken in Light and restored → the theme comes back with it
- [ ] The theme has **no** effect on scheduling: set Light and confirm a gong
      still fires on time

## Backup and restore (new)

Setup → Backup. The file is settings, courses, schedule and slot mapping — it
deliberately carries **no** PIN, relay password, doha folder grant, play log or
fired-guard record.

- [ ] **Save a backup** → choose a location → a `niyam-backup-YYYY-MM-DD.json`
      file appears and opens in any text viewer
- [ ] Open it and confirm by eye: no `admin_pin_hash`, no `relay_auth_pass`,
      no `fired:` keys anywhere
- [ ] Change the gong volume and delete a course, then **Restore from file** →
      the confirm sheet states *both* counts ("on this tablet now: N" vs "in
      the backup: M") before you commit
- [ ] Confirm → volume and course come back; a toast names what was restored
- [ ] **Cancel** on that sheet → nothing changes
- [ ] Pick a non-backup file (any .txt) → refused with a clear message and
      **nothing is changed**
- [ ] Restore a backup taken on a *different* tablet → doha slots appear but
      read **unverified**; rescanning the folder in Sounds re-verifies them
- [ ] After restore, the PIN on this tablet is still the PIN you set here —
      not one from the backup
- [ ] After restore, a scheduled gong still fires correctly the same day
      (the fired-guards were not overwritten)

## Fire correctness — power and grace

- [ ] Schedule a row 3 min out; the gong fires on the minute
- [ ] **Power the tablet off across a fire time, back on within 120 s →
      it fires once.** Not twice, not never
- [ ] Power on more than 120 s past the fire time → Logs show `missed` and
      **nothing sounds**. A late blast into a silent hall is the worst
      outcome of all
- [ ] Press Stop during a multi-strike burst → Logs row reads `stopped`
      with the strike count that actually rang

## Clock trust — the first thing to check when nothing rings

Found on a real tablet: no gongs all day, and the reason was at the top of the
Dashboard the whole time. If the wall clock jumps **backwards more than 10
minutes** the appliance suppresses every automatic play until a human confirms.
An NTP correction is enough to trigger it.

- [ ] Dashboard shows **CLOCK UNTRUSTED** whenever the clock has jumped back;
      the foreground notification says it too, so it is visible without unlocking
- [ ] Tapping **Confirm clock** clears it and the next gong arms immediately
- [ ] After confirming, a scheduled gong **actually rings**
- [ ] A gong logged `missed` earlier in the day does **not** stop a later gong
      from ringing. This was the beta8 bug: a miss wrote the same `fired:` guard
      a real fire writes, so once the wall clock moved, everything after it was
      silently suppressed — no sound and no log row at all

## Clock trust

- [ ] Set the device clock back 30 minutes → the Dashboard shows the
      **CLOCK UNTRUSTED** banner and the next fire is suppressed
- [ ] Confirm the clock (banner button or Time screen) → banner clears and
      fires resume
- [ ] While untrusted, tests still work — a person standing at the tablet
      may always ring it

## Overnight / soak (48 h)

- [ ] Schedule an event +2 min; screen off; hear the gong
- [ ] Reboot; after unlock the service returns and the next alarm is armed
- [ ] Leave it overnight on charge and confirm the 04:00 gong fires
- [ ] Over 48 h: no missed and no doubled fires in Logs
- [ ] Over 48 h: Setup's last-tick age never exceeds ~35 s
- [ ] Audio out → "Last played" **When** keeps advancing after each fire —
      a stamp stuck at yesterday means the route silently stopped working

---

## Known gaps in this build

These are understood and deliberately not fixed here.

1. **Clock-suppressed plays are invisible.** When the clock is untrusted the
   appliance correctly suppresses automatic plays, but writes no log row, so a
   suppressed morning looks the same as a quiet one. Fixing it needs a
   de-duplication rule (the 30 s heartbeat would otherwise flood the log) and a
   domain test, so it was left for a separate change.
2. **Overlapping courses cannot be resolved from the UI.** Two courses covering
   today paint ACTIVE and OVERLAP correctly, but there is no button to say
   "run that one instead" — the most recent start wins.
3. **Logs filter resets when you switch tabs.** It survives rotation and process
   death, not tab switching.
4. **No screen is locked any more.** Audio out and Network have shipped, so
   the nav rail has no padlocks left. `Tab.enabled` stays in the enum so the
   next half-built screen is a one-line lock rather than a special case.
5. **Not verified on real 1280×800 hardware.** Screenshots were taken on an
   emulator forced to that size at density 160. A real 10" tablet reports fewer
   dp, so please look hard at the Dashboard hero and the event columns.
6. **API 35 insets not verified.** The app now handles window insets, but the
   test emulator is API 34. On an Android 15 device check nothing hides under
   the status bar.
7. **The relay has never met real hardware.** Everything is unit-tested against
   the documented Gen2+ API and a fake HTTP server, but no Shelly has been in
   the loop. If Test connection returns 401 repeatedly with a password you know
   is right, suspect the auth variant rather than the password: some Shelly Gen2
   firmware documents a non-standard digest `ha2` for RPC instead of plain
   RFC 7616. Report that and it is a small fix.
8. **Cleartext HTTP is permitted app-wide**, not just to the relay's address.
   Android's network-security `<domain>` matcher takes hostnames and rejects
   CIDR, and the relay host is typed at setup, so the narrowing lives in code —
   `ShellyClient` is the app's only networking. Worth tightening if the relay
   ever gets a fixed address.
9. **Sounds is partial.** It holds the doha slot mapping and CDN downloads;
   track choice, volumes, burst gap, doha time and no-course mode are still
   unbuilt.
10. **The download pipeline has not met the live CDN from a device.** Every
    layer is unit-tested against fixtures and a local fake server with the
    real catalog hashes, but "Download all dohas" against
    `apt.vridhamma.org` is a manual QA item, above.
11. **Depth-2 storage scan is best-effort on API 29+.** Scoped storage means
    `/sdcard` may be unreadable without extra permissions the app
    deliberately does not request; the SAF folder picker in Sounds remains
    the reliable route for user-supplied files.
12. **No instrumentation test for the downloader.** JVM tests cover resume,
    truncation, HTML soft-fail and checksum rejection against a local
    server; an on-device MockWebServer run was skipped to keep the
    dependency count at zero.
13. **No audio route has met real hardware.** The fallback rule and the
    picker's row logic are unit-tested, and the resolved device id is now
    passed to ExoPlayer's `setPreferredAudioDevice` — but no Bluetooth amp or
    USB DAC has been on the bench. Bluetooth burst latency in particular is an
    open number the design doc flags as a risk; measure it before recommending
    Bluetooth to a hall.
14. **Hotspot detection is a heuristic.** Android removed the public "am I
    tethering" API in Android 9, so the screen reads its own interface names
    (`ap0`, `softap0`, `wlan1`, …). A device using a name outside that list
    reports no hotspot. Nothing in the app acts on the answer.
15. **The Wi-Fi network name is usually withheld.** Naming an SSID on API 29+
    requires a location permission the appliance deliberately does not
    request, so most devices show "name withheld" and point staff at system
    settings. This is the intended trade, not a defect.

## Failures / notes

...
