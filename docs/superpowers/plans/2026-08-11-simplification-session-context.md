# Session context — screen simplification + schedule/courses rework

**Started:** 2026-08-11. Written so this survives a context reset.
**Repo:** `/Users/wizops/DIPI/niyam` (branch `beta/screen-review`).
**Fable reference (read-only):** `/Users/wizops/Downloads/niyam`.

## Where the tree was when this started

Last commit `b8a5925` "Seed Dhamma Sudha's calendar at install, and add
backup/restore". Version `0.2.0-beta6` / `versionCode` 7. Suite 387 green.
Eleven unpushed commits on `beta/screen-review`.

## What the user asked for (verbatim intent)

1. **Time screen** — simplify. The app should take the timezone from Android
   itself. Do **not** force IST.
2. **Logs** — remove the `WHEN (UTC)` column. Just local time + details.
3. **Network** — simplify the display, drop the detail about hotspot detection
   being untrustworthy.
4. **Sounds** — remove the "Doha slots" table. Keep only: scan + download doha
   tracks, plus the gong and morning-doha settings.
5. **PIN** — remove the PIN screen from the nav; move PIN reset into **Setup**.
6. **Schedule** — "totally wrong". Must let staff modify the seeded per-slot
   gong configuration. Target look = screenshot: day columns `0..11` + `DEF`
   across, wall-clock times down, cells reading `×N`, course-type picker at the
   top. Reference seeds: `/Users/wizops/Downloads/niyam/seed/gong.sql` and
   `/Users/wizops/Downloads/niyam/seed/seed.sql`.
7. **Courses** — target look = screenshot: add-row (type dropdown, dd/mm/yyyy,
   note, "Add course"), then a table `START (ZERO DAY) | TYPE | NOTE | STATUS`
   with a red × delete per row, ACTIVE row highlighted.
8. **New idea (not necessarily this session):** pull the course calendar at
   runtime from dhamma.org. Centre schedules are public, e.g.
   `https://www.dhamma.org/en/schedules/schsudha`. The user pasted the India
   centre directory HTML; the useful part is the mapping
   `subdomain -> centre name -> /en/schedules/sch<subdomain>`.

## Hard rules that still apply (do not regress)

- Silence beats a wrong gong; never early/twice/late-and-loud; 120 s grace;
  fired-guard written before sound.
- `DohaSlots.legacyModular` is the verified PHP port — do NOT replace.
- UI is a client; `GongService` owns scheduler + player.
- Keep Shelly relay, CDN download pipeline, Nocturne styling.
- `domain/` stays free of Android imports.
- Bump `versionCode`/`versionName` on substantive change, and update
  `docs/BETA-QA-CHECKLIST.md` + `PROGRESS.md` in the same commit.
- Commit messages: no Claude/agent trailers.
- Run `./gradlew :app:testDebugUnitTest` before claiming done.

## Timezone change — the careful bit

`timezone` setting currently defaults to `Asia/Kolkata` and
`ApplianceZone.resolve()` falls back to IST. The user wants the **device zone**
used by default instead. Do this WITHOUT breaking the scheduler:

- Keep the `timezone` settings key (schedule rows, seeded DBs depend on it).
- Change the meaning: empty/blank `timezone` = "follow the device".
  `ApplianceZone.resolve(null/"")` should return `ZoneId.systemDefault()`.
- Keep an explicit override possible (a centre may still pin a zone).
- The hard rule "appliance TZ from settings not raw device TZ" becomes
  "settings may pin a zone; blank means follow the device" — update AGENTS.md
  rule 5 wording rather than silently contradicting it.
- `ApplianceZoneTest` will need updating; it currently asserts IST fallback.

## Progress log (update as work lands)

- [x] Context file written.
- [x] Time screen simplified — `ApplianceZone` blank = device zone, pin is the
      escape hatch, IST fallback gone, AGENTS.md rule 5 rewritten.
- [x] Logs UTC column removed (one local-time column).
- [x] Network simplified — "what needs a connection" table and hotspot caveat
      box both removed.
- [x] Sounds: doha slot table + its ~200 lines of helpers removed.
- [x] PIN screen removed from the nav; `SecurityScreen` became `SecurityCard`
      on Setup.
- [x] Schedule: added the explanatory subtitle. **The grid, DEF column and
      inspector already matched the target screenshot** — structure was not
      changed. If the user still calls it wrong, the next step is to screenshot
      DIPI's Schedule on the emulator and compare cell-by-cell; I did not spend
      the context to do that this session.
- [x] Courses: already matched the target screenshot (add row + START (ZERO
      DAY)/TYPE/NOTE/STATUS table + red x + ACTIVE highlight). No change made.
- [x] Centre directory: `tools/export_centres_json.py` written (parses the
      dhamma.org directory HTML into `assets/seed/centres.json`). **Not yet
      run** — needs the directory HTML saved to a file first:
      `curl -s https://www.dhamma.org/en/locations/directory > /tmp/directory.html`
      then run the tool. Design for the runtime fetch is in
      `docs/superpowers/specs/2026-08-11-runtime-course-calendar-design.md`.
- [x] Suite 389 green; `0.2.0-beta7` / versionCode 8; QA checklist + PROGRESS
      updated.

## Still open after this session

1. **Schedule** — user called it "totally wrong"; I could not reproduce a
   structural mismatch against the screenshot. Needs their specifics, or an
   emulator screenshot comparison.
2. **Centres asset not generated** — the tool exists, the JSON does not.
3. **Runtime calendar fetch** — designed, not built. Open question in the spec:
   are non-centre (gypsy course) venues in scope for the centre picker?
4. Unchanged hardware risks: no Bluetooth amp / USB DAC / real Shelly on the
   bench; OEM battery-killer recovery unproven.
