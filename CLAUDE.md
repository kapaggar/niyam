# CLAUDE.md — context for Claude Code in Niyam

## Project

**Niyam** (`org.dhamma.gong`) — Android appliance for Vipassana centres: schedules **gong** bursts and **morning doha** offline on a phone/tablet left on charge.

Repo: https://github.com/kapaggar/niyam  

Also read **`AGENTS.md`** (agent rules), **`PROGRESS.md`** (status), **`docs/FABLE-REVIEW.md`** (review queue).

## Architecture in one paragraph

`GongService` is the long-lived process (foreground `mediaPlayback`): it loads Room seed data, runs `SchedulerEngine` (exact alarms + ~30 s heartbeat), and owns `PlayerEngine` (Media3). Compose UI is a **client** of the service. Pure scheduling/play rules live under `domain/` and are unit-tested on the JVM.

## Behavioural non-negotiables

Copy these; do not “simplify” them away:

- Active course = **date window**, not “starts today only”; start date is day **0**.
- Day index = calendar days between dates.
- Fire: never early; grace **120 s**; else **`missed`** (no late blast).
- Write **`fired:…` guard before play**.
- Clock untrusted (large backwards jump) → suppress automatic plays.
- Doha: `legacy_modular` when `0 < day ≤ total_days`.
- Queue: gong preempts gong; doha waits; stop clears all.
- Timezone: settings key `timezone` (default `Asia/Kolkata`), not raw device TZ alone.

## Stack

- Kotlin, minSdk **29**, target/compile **35**
- Compose + Material 3 (Nocturne theme), landscape-oriented
- Room (WAL `gong.db`), Media3, coroutines/Flow
- Gradle 8.9 / AGP 8.7.x / Kotlin 2.0.x (see version catalog)

## Verify

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

## Out of scope unless asked

- Deshna `fetch.php` media server
- Cloud accounts / analytics
- Committing real doha master recordings or signing secrets

## Style

- Prefer small, reviewable diffs.
- Match existing package structure and naming.
- New schedule behaviour → new domain unit test first when practical.
- Update `PROGRESS.md` when a milestone or P1 fix lands.

## Security / media

- PIN: salted hash only (`SECURITY.md`).
- Audio rights: `LICENSE-NOTES.md` / `MEDIA.md` — not MIT.
- Code license: `LICENSE` (MIT) for software/docs as stated there.
