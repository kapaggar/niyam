# Niyam — Dhamma Gong Android appliance

A phone/tablet left on charge at a Vipassana centre **is** the gong + morning-doha
appliance: offline course schedule, Media3 audio, on-device Compose UI. Ported from
the centre's Raspberry Pi daemon; the domain unit tests are the behavioural law.

| | |
|--|--|
| Application id | `org.dhamma.gong` |
| Min SDK | 29 · target 35 |
| UI | Jetpack Compose, landscape tablet (Nocturne theme) |
| Domain | Pure Kotlin (JVM-testable) under `app/.../domain/` |
| Store | Room `gong.db` (WAL) — field-readable with any SQLite tool |
| Seed | `assets/seed/seed.json` — 12 course types, 335 schedule events |

## Build & test

```bash
./gradlew :app:testDebugUnitTest    # pure-JVM + Robolectric suite
./gradlew :app:assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
```

`local.properties` must point at your SDK (`sdk.dir=…`). JDK 17+, Gradle 8.9,
AGP 8.7.2, Kotlin 2.0.21.

## Layout

```
├── README.md / MEDIA.md / LICENSE-NOTES.md / PROGRESS.md
├── docs/
│   ├── ANDROID-APP-IMPLEMENTATION-PROMPT.md   # milestones M0–M7 + rules
│   ├── FABLE-REVIEW.md                        # code review + restart surface
│   └── handoff/                               # product design (open README.md + .html in a browser)
├── seed/                          # portable seed (JSON + SQL source)
├── media/                         # gong strikes + doha manifest (no doha audio)
├── tools/                         # seed exporter, test-tone generator
└── app/src/
    ├── main/java/org/dhamma/gong/
    │   ├── domain/    # scheduler core, fire rules, clock trust, doha slots, PIN
    │   ├── data/      # Room entities/DAOs, repository, seed loader
    │   ├── player/    # player engine, Media3 sink, audio routing
    │   ├── schedule/  # scheduler loop + exact alarms
    │   ├── service/   # foreground service (the appliance process), receivers
    │   └── ui/        # Compose shell: dashboard, courses, schedule, logs, PIN
    ├── test/          # unit suite — the behavioural spec
    └── debug/assets/media/doha-test/   # synthetic doha tones (debug only)
```

## Behavioural guarantees (do not regress)

- Course **window** matching, not "starts today only"; start date is **day 0**.
- **Calendar-day** arithmetic (`ChronoUnit.DAYS`), never seconds/86400.
- Never fires early; late only within a **120 s grace** window, else logs `missed`.
- Double-fire guard persisted **before** play dispatch.
- Untrusted clock (backwards jump) silences automatic plays until confirmed.
- Doha slots: `legacy_modular` in-course, `no_course_doha` outside.
- Player queue: a new gong preempts a gong; doha waits; stop clears everything.
- Appliance timezone comes from the `timezone` setting (default `Asia/Kolkata`),
  never the device's travel TZ.

Every rule above is pinned by a unit test — see `PROGRESS.md` for the inventory.

## Security

Opening the app can require a **PIN** (4–8 digits): set, change, or remove it from
the in-app **PIN** tab. Stored as salted PBKDF2 in the settings table, never
plaintext. No PIN set → the app opens directly.

## Media licensing

Bundled gong strikes and any doha recordings are **not** MIT-licensed — see
`LICENSE-NOTES.md` before redistributing. Release builds ship no doha audio;
the dashboard shows **GONGS ONLY** until a centre installs a licensed pack.

## History

Extracted (with history) from the `android/` tree of
[GongDohaServer](https://github.com/kapaggar/GongDohaServer); the Pi daemon this
app supersedes lives on in that repo.
