# Handoff: Gong appliance for Android (Gong-NG port)

## Overview
An Android "appliance" app for Vipassana meditation centres. A phone or tablet, permanently
plugged in at the centre, rings the course gong and the morning doha at the correct wall-clock
time for the correct course day — fully offline — while staff manage schedule, courses, audio
route and diagnostics on the device's own screen. It replaces a Raspberry Pi running the
existing Gong-NG Python daemon; NG's scheduling semantics are the spec.

## About the design files
The files in this bundle are **design references created in HTML**. They are prototypes showing
intended look, information architecture and behaviour — **not production code to copy**.

The target here is a **native Android app** (Kotlin, Jetpack Compose, single activity, foreground
service). Recreate the screens in Compose using the codebase's own patterns; do not embed the
HTML in a WebView. If the team already has an Android project, follow its existing conventions.
If not, the architecture section of the design doc (`Gong Appliance Design Doc.dc.html`)
specifies the intended one.

## Fidelity
**High-fidelity** for the three screens included (Dashboard, Courses, Schedule, Logs): final
colours, type sizes, spacing, states and copy. Recreate them faithfully, translating the CSS
values below into Compose theme tokens.

**Not yet designed** (drawn as locked/disabled nav entries only): Sounds, Audio out, Time,
Network, Setup checklist, PIN lock. The design doc specifies what each must answer; visual
design for them is outstanding.

## Target device
1280×800 logical px, 10" tablet, **landscape only**. Read from ~2 m across a room. Persistent
left nav rail (186 dp) + content pane. Portrait is not designed and not required for v1.

---

## Screens

### 1. Dashboard (default, no PIN)
The permanent view. Answers: what day, what's next, is it healthy, can I test it.

Layout — vertical stack, 26 px top / 32 px side padding, 22 px gap:
- **Top band** (row, 32 px gap):
  - *Left, flexible*: "NEXT GONG" eyebrow (11 px, 0.1 em tracking, uppercase, neutral-500) +
    an accent tag with the event name. Below it the hero time in **98 px monospace,
    line-height 0.92, letter-spacing −0.03em**. Then a 214×3 px solid accent rule (2 px radius,
    14 px above / 11 px below). Then a row: countdown in 19 px mono accent-300 ("in 2h 14m"),
    and a detail line in 13.5 px neutral-500 ("16 strikes · gap 4 s (default) · ting (default) · Speaker").
  - *Right, 394 px fixed*: two surface cards, 10 px gap.
    - **Course card**: "10 Day course — **Day 3**" (17 px body + 27 px mono), "zero day
      2026-08-05 · Asia/Kolkata" (12.5 px neutral-400), a day-progress bar of N+1 segments
      (5 px tall, 3 px gap: past = accent-700, today = accent, future = neutral-800), then a
      12 px-padded top-divider row of four checkbox toggles — Master, Gong, Doha, Relay
      (Relay at 50 % opacity, inert, retained for NG parity). Checkbox: 20×20, 4 px radius,
      1 px neutral-700; checked = accent 26 % fill, accent border, accent-100 tick.
    - **Health card**: rows of (7 px dot, 100 px label neutral-500, value) at 12.5 px —
      Scheduler / Audio route / Clock. Final row is the amber **GONGS ONLY** tag
      (#e0c07a on a 16 % tint, 40 % border) + "0 / 11 doha slots mapped".
- **Bottom band** (row, flex:1, 32 px gap):
  - *Left*: "NEXT EVENTS" eyebrow, then **two scrolling columns** of 6 events each. Row =
    icon (🔔 gong / ♪ doha), 15 px mono time, 11 px mono date, name, "×16" strike count.
    Past events at 0.38 opacity; the next event's time in accent-300.
  - *Right, 394 px*: a 78 px circular bell button that emits expanding accent rings
    (`scale(1)→scale(2.3)`, opacity 0.9→0, 1 s ease-out, one per strike, 900 ms apart),
    beside a primary "Test gong" (50 px tall, full width) and a row of "Test doha"
    (disabled-look, 45 % opacity) + "■ Stop". Below: 11 px mono footnote with free storage
    and last play result.
- **Toast**: centred 38 px from top, amber-tinted, 0.18 s slide-in, 2.6 s auto-dismiss.

Copy notes: real NG strike counts (04:00 ×16, 04:20 ×12, 06:32 ×3, 07:50 ×8, 11:00 ×6,
12:50 ×8, 14:10 ×1, 14:20 ×3, 17:00 ×6, 17:50 ×6, 21:00 ×3).

### 2. Courses (PIN)
Add/remove courses. **Start date is zero day (arrival day)**, not day 1.

- Header + subhead explaining the window rule.
- **Add row** in a surface card (14/16 px padding, 10 px gap): course-type `<select>` (190 px),
  date input (150 px), free-text note (flexible), primary "Add course" button. All 38 px tall.
- **Table**: sticky header, grid `150px 170px 1fr 92px 44px` = Start (zero day) / Type / Note /
  Status / delete. Rows 7 px padding, 1 px hairline divider.
  - The **active** course row is tinted (accent 12 %), its date and status in accent-200,
    status text "ACTIVE". Others read "upcoming" or "past" in neutral-600.
  - Delete is a 30×30 red-tinted ✕ button (#e08a8a on 12 % fill, 34 % border).
- Course types (13, ported verbatim from NG with total_days): No course (0), 10 Day (11),
  20 Day (21), 30 Day (31), 45 Day 10A (46), STP (9), 3 Day (4), 2 Day (3), 1 Day (2),
  STP D9 Ending (10), 45 Day 15A (46), Teen (8), Gratitude (2).

### 3. Schedule (PIN)
The day-column grid — the whole course matrix at once, so a day-3 edit visibly reads as day 3.

- Header: "Schedule" + a course-type `<select>` (34 px tall) that reshapes the grid.
- **Grid**: `grid-template-columns: 60px repeat(N, minmax(46px, 1fr))` where N = total_days + 1
  (days 0…total, plus a **DEF** column). Rows are the wall-clock times; header row 36 px,
  cells 44 px. Cell content is "×16" or "·" when empty.
  - Empty cell: neutral-700 text, hairline borders, hover = 7 % white tint.
  - Filled: accent-200 text on a 14 % accent tint.
  - Selected: 2 px inset accent ring.
  - **DEF** column header sits on a 5 % neutral tint — it is the mid-course default pattern used
    for any day with no rows of its own. For "No course" the grid collapses to a single **N/C** column.
- **Add row** under the grid: time input, repeats number, gap number (placeholder "gap s",
  blank = use setting), track select ("default track" / ting / drum), primary "Add" button,
  and a hint naming the target column.
- **Inspector**, 272 px right aside on #13141f with a left divider: the event time at 30 px mono,
  its course/day label, a −/+ strike stepper (40 px buttons, 25 px mono value, clamp 1–32),
  a 5-up gap chooser (**—**, 2 s, 4 s, 6 s, 8 s) and a 3-up track chooser (**—**, ting, drum).
  **The em-dash option is null, meaning "inherit the setting"** — this nullability is load-bearing
  and must survive into the data model. Then a computed burst duration and a destructive
  "Remove event" button pinned to the bottom.

### 4. Logs (no PIN — read-only)
`play_log`. **Timestamps are UTC**; the detail column carries the local scheduled instant with
offset (e.g. `scheduled 2026-08-07T17:50:00+05:30`) so a missed fire is diagnosable.

- Filter chips: all / gong / doha / missed / error (32 px, accent-tinted when active).
- Grid `124px 96px 104px 46px 96px 1fr` = When (UTC) / What / File / × / Result / Detail,
  sticky header, everything monospace.
- Result colours: ok → neutral-300; missed & error → #e08a8a; stopped & skipped_clock → #e0c07a.
- Kinds seen: `gong`, `doha`, `test_gong`. Results: `ok`, `missed`, `error`, `stopped`,
  `skipped_clock`.

---

## Interactions & behaviour
- **Nav** switches the content pane; nav state is the only routing. Locked items are inert at 42 % opacity with a 🔒 glyph.
- **Test gong** fires a 4-strike burst at 900 ms, drawing one ring per strike, button label → "Ringing…", ignores re-entry while playing. **Stop** cancels immediately.
- **Test doha** with no slots mapped shows the toast "No doha slots mapped — add files to /sdcard/DhammaGong/doha".
- **Toggles** save immediately and toast "Settings saved" — no save button anywhere; every edit is immediate.
- **Grid cell tap** selects it, and creates a default event (×6, inherit gap, inherit track) if the cell was empty.
- **Clock** ticks every second; the countdown recomputes from seconds (never borrow minutes by hand — that was a real bug in an earlier draft).
- Adding a course with no date toasts "Pick a start date"; adding an event with no time toasts "Pick a time first".

## State
`tab`, `now` (1 s tick), `sel` (grid cell key `type|day|time`), `cells` (map of that key →
`{repeats, gap|null, track|null}`), `courses[]`, `enabled{master,gong,doha,relay}`,
`logFilter`, `schedType`, form drafts, `playing`/`rings`, `toast`.

In the real app all of this except `tab`/`sel`/drafts comes from Room and the service's state
flows. The UI is a client of the foreground service, never a peer — closing the activity must
change nothing.

## Design tokens
From the **Nocturne** design system (`_ds/nocturne-*/styles.css`), which the handoff includes.
Use its variables rather than the literals; the literals are here so values can be verified.

| Token | Value | Use |
| --- | --- | --- |
| `--color-bg` | #161826 | app ground (screens use #0d0e17 outside the device frame only) |
| status bar / nav | #101120 / #13141f | chrome |
| `--color-surface` | ramp surface | cards, inputs |
| `--color-text` | #e9e9ed | primary text |
| accent | #9184d9 (`--color-accent`, ramp 100–900) | next-event emphasis, selection, active course |
| warning | #e0c07a | gongs-only chip, stopped/skipped_clock |
| error | #e08a8a | missed/error, destructive |
| ok dot | #7fd6a8 | health indicators |
| radius | `--radius-sm` / `--radius-md` (8 px scale) | controls / cards |
| shadow | `--shadow-sm` / `--shadow-md` / `--shadow-lg` | never stack |
| body font | Inter (`--font-body`) | all UI |
| mono | ui-monospace / SF Mono / Menlo | every time, count, filename, log field |

Type scale in use: 98 (hero), 30–34 (screen numerals), 23 (screen title), 17, 15, 13.5, 12.5,
11.5, 11 (eyebrows, 0.1 em tracking, uppercase), 10.5 (tags).
Hairline divider: `color-mix(in srgb, #e9e9ed 7%, transparent)`.
Never below 12 px on this device; hit targets ≥ 44 px.

## Engineering constraints that shape the UI
Full rationale in `Gong Appliance Design Doc.dc.html` (open in a browser). The short version:
- minSdk 29 / target 35. Single foreground service (`mediaPlayback`), `START_STICKY`, boot receiver.
- `setAlarmClock` for the next occurrence **plus** a 30 s in-service heartbeat — alarms are an optimisation, not the only path.
- Room over SQLite, column names identical to NG so a pulled DB stays readable by NG tooling.
- Day math on `LocalDate` differences, never `/86400`. `start_date` is day 0.
- Fire window `fire_at ≤ now ≤ fire_at + 120 s`; the fired-guard `state["fired:<key>:<date>"]` is committed **before** the job is enqueued.
- Untrusted clock suppresses automatic plays (`skipped_clock`) but still permits tests.
- APK ships gong stems only; doha arrives as sideloaded files in `/sdcard/DhammaGong/doha`, auto-mapped D01…D11 into a `media_slots` table.

## Assets
None bundled. Gong audio (`ting.mp3`, `drum.mp3`) is referenced by name only. Icons in the
prototype are Unicode placeholders (◉ ▤ ▦ ≡ ♪ ⊳ ◷ ⌁ 🔔) — **replace with Phosphor icons**, which
is what Nocturne specifies.

## Files in this bundle
| File | What |
| --- | --- |
| `Gong Appliance Screens.dc.html` | The interactive hi-fi prototype. Open in a browser; everything in it works. |
| `Gong Appliance Design Doc.dc.html` | The engineering design doc — architecture, data model, state machine, permissions, failure matrix, roadmap, risks, readiness. |
| `ANDROID-APP-DESIGN-PROMPT.md` | The original brief, including full Gong-NG semantics to port. |
| `support.js`, `_ds/` | Runtime + Nocturne design system needed for the two HTML files to render. |

## Open questions (blocking, from the design doc §14)
1. Which tablet model will centres buy? OEM battery-killer behaviour is the top risk and is model-specific.
2. Is the system alarm icon from `setAlarmClock` acceptable on a wall tablet?
3. Does any centre need two schedules on one device (two halls)? Assumed no — it changes the data model if yes.
4. Who signs and hosts the centre-edition APK?
5. Is kiosk / screen-pinning first-class in v1, or a documented device-owner setup step?
6. Hindi at launch or after? Cheap now, expensive to retrofit into a grid layout.
