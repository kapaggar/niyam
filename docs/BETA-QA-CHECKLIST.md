# Niyam 0.2.0-beta1 — human QA

Device: ________  Android: ________  Build: debug APK (`versionCode` 2)

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

- [ ] Grid shows days 0…N plus DEF
- [ ] **Days with no rows of their own show their inherited rows dimmed** —
      they are not blank
- [ ] Tapping an empty cell on such a day **warns first and does not write**;
      only the second tap creates the row
- [ ] Scroll right on a 20/30/45 Day type — **the wall-clock times stay pinned**
      on the left
- [ ] Select a cell → inspector; set gap/track to "—" (inherit), reopen, still "—"
- [ ] Adding a time that already exists is refused with a message
- [ ] Remove event asks twice

## Logs

- [ ] Test gong appears as `ok`
- [ ] LOCAL column matches the appliance zone; WHEN stays UTC
- [ ] Filters work; with `missed` selected and nothing matching, the message
      says so rather than "Nothing logged yet"
- [ ] Columns do not vanish when the window is narrower

## PIN

- [ ] Set PIN → digits are **masked** while typing
- [ ] Kill app → reopen → lock shows, **no dashboard flash**
- [ ] Wrong PIN rejected; repeated wrong attempts get slower
- [ ] **Unlock, press Home, wait over a minute, return → it asks for the PIN again**
- [ ] Unlock, go to system settings via a health row and come straight back →
      it should **not** re-ask (that is the grace window working)
- [ ] Remove PIN asks twice and is styled as destructive
- [ ] On a phone in landscape, the keypad scrolls — the OK key is reachable

## Time / Setup (new in this build)

- [ ] Time shows appliance zone and device zone side by side
- [ ] Changing the zone saves immediately and moves the Dashboard "today"
- [ ] A nonsense zone id is rejected, not silently accepted
- [ ] Confirm clock works when the clock is untrusted
- [ ] Setup checklist matches the real permission state — an amber row must
      never be green when the grant is actually missing

## Sounds — doha pack folder (new)

- [ ] Sounds → pick the folder that **directly contains** `D01…D11` mp3s →
      all 11 slots map and the Dashboard GONGS ONLY chip clears
- [ ] Pick a *parent* folder instead → empty state naming the `D01…D11`
      convention, not a silent no-op
- [ ] Pick a folder whose only match is inside a single `doha/` subfolder →
      still maps (one level down only)
- [ ] Reassign a file to a different slot, then Rescan → **the manual
      assignment survives**
- [ ] Put two files with the same `D03` prefix in the folder → shown as a
      **conflict**, neither is assigned
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

## Overnight / soak

- [ ] Schedule an event +2 min; screen off; hear the gong
- [ ] Reboot; after unlock the service returns and the next alarm is armed
- [ ] Leave it overnight on charge and confirm the 04:00 gong fires

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
4. **Sounds, Audio out and Network remain locked.** Their cards now say what
   they will do. The schedule runs without them.
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
9. **Sounds is partial.** It holds the doha slot mapping only; track choice,
   volumes, burst gap, doha time and no-course mode are still unbuilt.

## Failures / notes

...
