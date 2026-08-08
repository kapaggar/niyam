# Dhamma Gong — Android appliance

Self-contained Android project: **phone/tablet is the gong + doha scheduler and player**
(Gong-NG semantics), offline-first. Scaffolded to be **moved into its own git repo**.

| | |
|--|--|
| Application id | `org.dhamma.gong` |
| Min SDK | 26 |
| UI | Jetpack Compose (placeholder shell) |
| Domain | Pure Kotlin under `app/.../domain/` |
| Seed | `seed/seed.json` + `assets/seed/seed.json` (from Gong-NG `ng/seed/seed.sql`) |

## Move to a new git repo

```bash
# from wherever this folder lives
cp -R android /path/to/GongAndroid
cd /path/to/GongAndroid
git init
git add .
git commit -m "Initial Gong Android appliance scaffold from Gong-NG"
```

Or: `git subtree split` / drag the `android/` directory into a new GitHub repo.
Everything needed to develop without the parent monorepo is inside this tree
(`docs/`, `seed/`, `reference/`, `media/`, Gradle project).

## Open in Android Studio

1. **File → Open** → select this `android/` directory (the one with `settings.gradle.kts`).
2. Let Gradle sync (needs Android SDK + JDK 17).
3. Run configuration: `app` on emulator or device.

## Tests (domain / M0)

```bash
./gradlew :app:testDebugUnitTest
```

Covers:

- `DohaSlots` — golden vectors from Gong-NG `test_doha.py`
- `ActiveCourse` — window match, day number, pin override
- `FireRules` — grace / missed / double-fire / clock
- `ScheduleMaterializer` — explicit day vs default pattern vs no-course

If `./gradlew` is missing, open once in Android Studio (generates wrapper) or install a
Gradle wrapper:

```bash
gradle wrapper --gradle-version 8.9
```

## Layout

```
android/
├── README.md
├── LICENSE-NOTES.md
├── MEDIA.md
├── docs/                          # design + implementation prompts + GONG-NG design
│   ├── ANDROID-APP-DESIGN-PROMPT.md
│   ├── ANDROID-APP-IMPLEMENTATION-PROMPT.md
│   └── GONG-NG-DESIGN.md
├── seed/                          # portable seed (JSON + SQL source)
│   ├── seed.json                  # course_types + schedule_events
│   ├── seed.sql
│   ├── doha-manifest.json
│   └── courses-sudha-2026-2027.sql
├── media/                         # source media (not full doha library)
│   ├── gongs/{ting,drum}.mp3
│   └── doha/manifest.json
├── reference/                     # read-only Gong-NG Python for algorithm parity
│   ├── gong_ng/{doha,model,scheduler,player,clock,jobs}.py
│   └── tests/
├── app/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── assets/seed/seed.json
│       │   ├── assets/media/...
│       │   └── java/org/dhamma/gong/
│       │       ├── domain/        # pure Kotlin (M0 done)
│       │       ├── ui/            # MainActivity placeholder
│       │       ├── data/          # (next: Room)
│       │       ├── player/        # (next: Media3)
│       │       └── service/       # (next: FGS scheduler)
│       └── test/.../domain/       # unit tests
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

## Product intent (short)

- Appliance app: device schedules gongs + morning doha for Vipassana courses.
- Parity with **Gong-NG** (not legacy PHP bugs): course window, calendar day, 120s grace,
  missed vs double-fire, `legacy_modular` doha slots.
- Later: Bluetooth/USB audio, hotspot + office Wi‑Fi UI, smart-plug amp power.
- **Not** in MVP: Deshna jukebox server.

## Implementation roadmap

Follow `docs/ANDROID-APP-IMPLEMENTATION-PROMPT.md` milestones:

| Milestone | Status in this scaffold |
|-----------|-------------------------|
| M0 Domain + skeleton | **Started** — domain + tests + empty UI |
| M1 Room + seed import | TODO |
| M2 Player + FGS | TODO |
| M3 Scheduler alarms | TODO |
| M4–M7 UI / backup / network | TODO |

### Claude Code kickoff (after move)

```text
Read docs/ANDROID-APP-IMPLEMENTATION-PROMPT.md and docs/GONG-NG-DESIGN.md.
Continue from M0 (domain exists). Implement M1: Room + import assets/seed/seed.json.
Keep domain pure JVM-testable. Do not add Deshna.
```

## Regenerating seed from parent monorepo (optional)

If you still have Gongserver checked out:

```bash
# from gongserver root
python3 - <<'PY'
# same converter used when scaffolding — see tools/export_seed_json.py
PY
```

Use `tools/export_seed_json.py` if present, or re-copy `ng/seed/seed.sql` and convert.

## Parent project

Extracted from [GongDohaServer / gongserver](https://github.com/kapaggar/GongDohaServer)
`ng/` stack. Parent path on the authoring machine: often `~/gongserver/android`.
