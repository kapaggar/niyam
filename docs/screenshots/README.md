# Screenshots

Product screens for GitHub / docs. Captured from a USB **Google Pixel C**
(`ryu`, API 27) running the Niyam debug build.

## Capture settings

| Setting | Value |
|---------|--------|
| Device | Google Pixel C (tablet) |
| Orientation | Landscape |
| Resolution | **2560 × 1800** (device native) |
| Density | 320 dpi |
| Method | `adb shell screencap -p` + `adb pull` |
| App | `org.dhamma.gong` debug |

`adb exec-out screencap` is unreliable on this device (GPU log lines corrupt the
PNG stream). Prefer writing to `/sdcard` then `adb pull`.

Deep-link a tab (warm start keeps the activity unlocked):

```bash
adb shell am start -n org.dhamma.gong/.ui.MainActivity --es tab DASHBOARD
```

`tab` is a `Tab` enum name (case-insensitive): `DASHBOARD`, `SCHEDULE`,
`COURSES`, `LOGS`, `SOUNDS`, `AUDIO_OUT`, `POWER`, `TIME`, `SETUP`. `NETWORK`
was removed when the Network screen was folded into a card on Setup — see
"Network" below.

`SOUNDS`, `AUDIO_OUT`, `POWER` and `TIME` are Advanced-only since Simple/
Advanced UI mode shipped (`0.2.0-beta12`). A fresh install boots into Simple,
the shipped default, and a deep link to any of those four is silently ignored
in that mode — the app lands on Dashboard instead. Switch to Advanced first
(Setup → Screens, or `adb shell am start … --es tab SETUP` and tap it by hand)
before capturing them.

## Gallery

**Every capture below predates Simple/Advanced UI mode** (`0.2.0-beta12`).
The nav rail they all show is the old nine-item rail — the shipped default is
now the five-item Simple rail (Dashboard, Schedule, Courses, Logs, Setup).
None of these are wrong for what they document, but none of them show the
rail a fresh install actually renders; see "Outstanding — Simple/Advanced"
below for the captures that would.

### Dashboard

![Dashboard](01-dashboard.png)

Main dashboard: next gong hero, active course card, health, next events, test panel.

![Dashboard scrolled](01b-dashboard-scrolled.png)

Lower band / scrolled view of the dashboard.

### Schedule

![Schedule](02-schedule.png)

Day-column schedule grid with inspector.

![Schedule scrolled](02c-schedule-vscroll.png)

Schedule after vertical scroll.

### Courses

![Courses](03b-courses-scrolled.png)

Course table (start = zero day, status paint).

![Courses lower](03c-courses-lower.png)

Further down the courses list.

### Logs

![Logs](04-logs.png)

Play log with filter chips (UTC timestamps).

![Logs scrolled](04b-logs-scrolled.png)

Logs after scroll.

### Sounds

![Sounds](05-sounds.png)

Sounds / doha media mapping (top of screen).

![Sounds scrolled](05c-sounds-scrolled-more.png)

Sounds mid-scroll.

![Sounds bottom](05d-sounds-bottom.png)

Sounds lower section (pack / download area).

### Audio out

![Audio out](06-audio-out.png)

Audio route / output settings.

### Amp power

![Amp power](07-amp-power.png)

Mains / Shelly relay control (top).

![Amp power scrolled](07b-amp-power-scrolled.png)

Amp power lower section.

### Time

![Time](08-time.png)

Appliance timezone vs device zone; clock confirm.

### Network

Retired as a destination: `NetworkScreen` and `Tab.NETWORK` were deleted, and
the network facts plus Wi-Fi/hotspot buttons now live in a `NetworkCard` on
Setup instead. `09-network.png` below is kept for history but no longer shows
a current screen — see the Setup captures for where this content lives now.

### Setup

**Stale, same as Network below.** Taken before Simple/Advanced UI mode
(`0.2.0-beta12`): Setup has since gained a **Network** card and an
**Amp power** card (`AmpPowerSimpleCard`) that these captures do not show,
and lost three explanatory paragraphs that moved behind ⓘ info dialogs. Kept
for history; see "Outstanding — Simple/Advanced" below for the replacements.

![Setup](10-setup.png)

First-run checklist (notifications, exact alarms, battery, appliance state).

![Setup scrolled](10b-setup-scrolled.png)

Setup after scroll.

### System

![Notification shade](12-notification-shade.png)

System notification shade showing the Gong foreground-service notification.

## Outstanding — Simple/Advanced

The Simple/Advanced UI mode shipped in `0.2.0-beta12` has no captures yet.
The Pixel C used for this gallery went off the network before this batch
could be shot, so the following five are wanted and do not exist as PNGs
in this folder:

- `simple-01-dashboard` — Dashboard in Simple mode
- `simple-02-setup` — Setup in Simple mode, showing the Network and Amp
  power cards
- `simple-03-setup-scrolled` — Setup in Simple mode, scrolled
- `advanced-01-rail` — the full nav rail after switching to Advanced
- `advanced-02-amp-power` — the Advanced Amp power screen

Do not fabricate these; capture them from a real device once one is
reachable, following the "Re-shoot" recipe below.

## File index

| File | Screen |
|------|--------|
| `01-dashboard.png` | Dashboard |
| `01b-dashboard-scrolled.png` | Dashboard (scrolled) |
| `02-schedule.png` | Schedule |
| `02c-schedule-vscroll.png` | Schedule (v-scroll) |
| `03b-courses-scrolled.png` | Courses |
| `03c-courses-lower.png` | Courses (lower) |
| `04-logs.png` | Logs |
| `04b-logs-scrolled.png` | Logs (scrolled) |
| `05-sounds.png` | Sounds |
| `05c-sounds-scrolled-more.png` | Sounds (scrolled) |
| `05d-sounds-bottom.png` | Sounds (bottom) |
| `06-audio-out.png` | Audio out |
| `07-amp-power.png` | Amp power |
| `07b-amp-power-scrolled.png` | Amp power (scrolled) |
| `08-time.png` | Time |
| `09-network.png` | Network (retired — folded into a Setup card) |
| `10-setup.png` | Setup (stale — pre-Simple/Advanced, missing the Network and Amp power cards) |
| `10b-setup-scrolled.png` | Setup, scrolled (stale — see above) |
| `12-notification-shade.png` | Notification shade |

## Re-shoot

```bash
export ANDROID_HOME=/Users/wizops/Android/Sdk
export PATH="$ANDROID_HOME/platform-tools:$PATH"
SERIAL=<device-serial>
OUT=docs/screenshots
adb -s "$SERIAL" shell am start -n org.dhamma.gong/.ui.MainActivity --es tab DASHBOARD
adb -s "$SERIAL" shell screencap -p /sdcard/niyam_shot.png
adb -s "$SERIAL" pull /sdcard/niyam_shot.png "$OUT/01-dashboard.png"
```
