# Niyam — Dhamma Gong Android appliance

[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Android-API%2029%2B-blue.svg)](app/build.gradle.kts)

A phone or tablet left on charge at a Vipassana centre **is** the gong and morning-doha appliance: offline course schedule, Media3 audio, on-device Compose UI. Behaviour is pinned by a pure-Kotlin domain suite (course day, grace window, double-fire guards, doha slots).

| | |
|--|--|
| **Application id** | `org.dhamma.gong` |
| **Min / target SDK** | 29 / 35 |
| **UI** | Jetpack Compose · landscape · Nocturne theme |
| **Store** | Room `gong.db` (WAL) — open with any SQLite tool for field repair |
| **Seed** | 12 course types · 335 schedule events (`assets/seed/seed.json`) |
| **Remote** | [github.com/kapaggar/niyam](https://github.com/kapaggar/niyam) |

## Quick start

```bash
# Clone
git clone https://github.com/kapaggar/niyam.git
cd niyam

# Point Gradle at your SDK (gitignored)
echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties   # macOS example

# Tests + debug APK
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk
```

Open the project root in **Android Studio** (the folder with `settings.gradle.kts`). JDK **17+**.

Install on a device/emulator:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n org.dhamma.gong/.ui.MainActivity
```

## What it does

- Runs a **foreground scheduler** so gongs can fire with the screen off  
- Matches the **active course window** (start date = day 0), not “only if the course starts today”  
- Plays **gong bursts** (repeats + gap) and optional **morning doha**  
- Logs plays, misses, and errors for staff  
- Optional **PIN** for opening the app (salted hash, not plaintext)  
- Appliance **timezone** from settings (default `Asia/Kolkata`), not a travel phone’s TZ alone  

## Behavioural guarantees (do not regress)

- Calendar-day arithmetic (`ChronoUnit.DAYS`) — never `seconds/86400` for course day  
- Never fires early; late only within **120 s grace**, else logs `missed`  
- Double-fire guard persisted **before** play dispatch  
- Untrusted clock (large backwards jump) silences automatic plays until confirmed  
- Doha: `legacy_modular` in-course; `no_course_doha` outside  
- Player queue: new gong preempts gong; doha waits; stop clears everything  

Each rule is covered by unit tests — see [`PROGRESS.md`](PROGRESS.md).

## Repository layout

```text
├── README.md                 # this file
├── LICENSE                   # MIT (software & docs)
├── LICENSE-NOTES.md          # audio rights (not MIT)
├── PRIVACY.md                # privacy policy
├── SECURITY.md               # threat model & reporting
├── AGENTS.md                 # rules for coding agents
├── CLAUDE.md                 # Claude Code context
├── PROGRESS.md               # milestones & restart point
├── MEDIA.md                  # media pack layout
├── docs/
│   ├── ANDROID-APP-IMPLEMENTATION-PROMPT.md
│   ├── FABLE-REVIEW.md
│   └── handoff/              # product design (open HTML in a browser)
├── seed/                     # portable seed JSON/SQL
├── media/                    # gong strikes + doha manifest (no full doha library)
├── tools/                    # seed export, test tones
└── app/src/main/java/org/dhamma/gong/
    ├── domain/               # pure scheduler / fire / doha / PIN
    ├── data/                 # Room + seed
    ├── player/               # Media3 + queue
    ├── schedule/             # alarms + heartbeat loop
    ├── service/              # GongService (the appliance process)
    └── ui/                   # Compose client of the service
```

## Screenshots

Captured at **1280×800 landscape** (appliance target). Full captions:
[docs/screenshots/README.md](docs/screenshots/README.md).

| | |
|--|--|
| **Install** | [![Installation](docs/screenshots/01-installation.png)](docs/screenshots/01-installation.png) |
| **Dashboard** | [![Dashboard](docs/screenshots/03-dashboard-main.png)](docs/screenshots/03-dashboard-main.png) |
| **Schedule** | [![Schedule](docs/screenshots/05-schedule.png)](docs/screenshots/05-schedule.png) |

| Screen | File |
|--------|------|
| Installation (app info) | [`01-installation.png`](docs/screenshots/01-installation.png) |
| Dashboard (idle / no course) | [`02-dashboard-idle.png`](docs/screenshots/02-dashboard-idle.png) |
| Dashboard (active course) | [`03-dashboard-main.png`](docs/screenshots/03-dashboard-main.png) |
| Courses | [`04-courses.png`](docs/screenshots/04-courses.png) |
| Schedule | [`05-schedule.png`](docs/screenshots/05-schedule.png) |
| Logs | [`06-logs.png`](docs/screenshots/06-logs.png) |
| PIN settings | [`07-pin-settings.png`](docs/screenshots/07-pin-settings.png) |

## Documentation index

| Doc | Purpose |
|-----|---------|
| [docs/screenshots/README.md](docs/screenshots/README.md) | Screenshot gallery & re-capture notes |
| [PRIVACY.md](PRIVACY.md) | What is stored on-device; no cloud backend |
| [SECURITY.md](SECURITY.md) | Threat model, reporting, centre hardening |
| [LICENSE](LICENSE) | MIT for code and project docs |
| [LICENSE-NOTES.md](LICENSE-NOTES.md) | Gong/doha audio are **not** MIT |
| [MEDIA.md](MEDIA.md) | File layout for media packs |
| [AGENTS.md](AGENTS.md) / [CLAUDE.md](CLAUDE.md) | AI/agent onboarding |
| [PROGRESS.md](PROGRESS.md) | M0–M7 status and known gaps |
| [docs/FABLE-REVIEW.md](docs/FABLE-REVIEW.md) | Implementation review & next work |

## Security (short)

- Optional **PIN** (4–8 digits): set/change/remove in-app when implemented in UI  
- Stored as **salted PBKDF2** hash in settings — never plaintext  
- Treat the tablet as centre equipment; physical access is the outer boundary  
- Details: [SECURITY.md](SECURITY.md)

## Media licensing

Bundled gong strikes (and any doha masters you add) are **not** covered by the MIT license. See [LICENSE-NOTES.md](LICENSE-NOTES.md) and [MEDIA.md](MEDIA.md) before Play Store or public redistribution. Release builds may show **GONGS ONLY** until a licensed doha pack is installed.

## Development status

MVP path **M0–M4** (domain, Room, player, scheduler, core UI) is in tree. Later work: remaining settings screens, backup/restore, first-run wizard, OEM/field hardening — tracked in `PROGRESS.md`.

## History

Extracted from the `android/` tree of [GongDohaServer](https://github.com/kapaggar/GongDohaServer). The Raspberry Pi **Gong-NG** daemon remains the behavioural reference for scheduling semantics.

## Contributing

1. Keep `domain/` free of Android framework APIs.  
2. Add or update unit tests for schedule/play changes.  
3. Run `./gradlew :app:testDebugUnitTest`.  
4. Do not commit `local.properties`, keystores, or non-free full doha libraries.  

Bug reports and PRs: [github.com/kapaggar/niyam](https://github.com/kapaggar/niyam).

## License

Software and documentation in this repository are under the [MIT License](LICENSE), **except** audio content described in [LICENSE-NOTES.md](LICENSE-NOTES.md).
