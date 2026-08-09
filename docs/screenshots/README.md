# Screenshots

Product screens for GitHub / docs. Captured from the debug build on an emulator
forced to the **appliance target surface**.

## Capture settings

| Setting | Value |
|---------|--------|
| Orientation | **Landscape** (app locks with `android:screenOrientation="landscape"`) |
| Resolution | **1280 × 800** logical pixels |
| Density | **160 dpi** (1 dp ≈ 1 px) |
| AVD used | `cca34` (API 34) with `adb shell wm size 1280x800` + `wm density 160` |
| App version | `0.1.0-mvp` (`org.dhamma.gong`) |

Design target is a ~10″ tablet in landscape (Nocturne UI). Phone-shaped AVDs
should be overridden with `wm size` / `wm density` before re-shooting.

## Gallery

### 1. Installation

![App installed — system App info](01-installation.png)

After installing the debug APK, the system **App info** page for **Dhamma Gong**
(`org.dhamma.gong`, version `0.1.0-mvp`).

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 2. Main dashboard

#### Idle (no course)

![Dashboard with no active course](02-dashboard-idle.png)

Scheduler running, clock trusted, **No course** — between-course / first-run state.
**GONGS ONLY** when doha media slots are unmapped.

#### Active course

![Dashboard with active 10 Day course](03-dashboard-main.png)

Active course card, next gong hero time, next-events list, health strip, test
gong / doha / stop. Appliance timezone shown as **Asia/Kolkata**.

### 3. Critical working / settings

#### Courses

![Courses list and add form](04-courses.png)

Add course (type + zero-day start date), active row highlighted.

#### Schedule

![Schedule grid for 10 Day](05-schedule.png)

Day columns 0…N + **DEF** (mid-course default), strike counts, inspector for edits.

#### Logs

![Play log with missed events](06-logs.png)

UTC timestamps, filters, `missed` / result colouring — staff diagnostics.

#### PIN

![PIN setup screen](07-pin-settings.png)

Optional 4–8 digit PIN (salted PBKDF2). Empty = open app without a gate.

## Re-capture (maintainers)

```bash
# Emulator surface
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 1
adb shell wm size 1280x800
adb shell wm density 160

# Optional deep-link tab (after force-stop):
#   DASHBOARD | COURSES | SCHEDULE | LOGS | SECURITY
adb shell am force-stop org.dhamma.gong
adb shell am start -n org.dhamma.gong/.ui.MainActivity --es tab COURSES
adb exec-out screencap -p > docs/screenshots/04-courses.png
```

Nav items also expose `content-desc` values `nav_DASHBOARD`, `nav_COURSES`, etc.
for automation.
