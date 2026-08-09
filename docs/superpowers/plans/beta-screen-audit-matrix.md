# Beta screen audit matrix — 0.2.0-beta1

Companion to `2026-08-09-beta-screen-review-assignment.md`.
Wave 1 agents fill their own row + append a detailed findings block.

**Baseline (Wave 0, Lead):**

| Item | Value |
|------|-------|
| Branch | `beta/screen-review` (from `main` @ `7f453dd`) |
| `./gradlew :app:testDebugUnitTest` | **130 tests, 0 failures, 0 errors, 0 skipped** |
| `./gradlew :app:assembleDebug` | BUILD SUCCESSFUL |
| APK | `app/build/outputs/apk/debug/app-debug.apk` |
| versionName at baseline | `0.1.0-mvp` (bump to `0.2.0-beta1` in Wave 4 only) |

---

## Matrix

| Screen | Agent | Handoff match (Y/N/partial) | Adaptive 1280×800 | State/VM issues | A11y | Top fixes (≤5) | Sev |
|--------|-------|-----------------------------|-------------------|-----------------|------|----------------|-----|
| Dashboard | A | | | | | | |
| Courses | B | | | | | | |
| Schedule | C | | | | | | |
| Logs | D | | | | | | |
| PIN / Security | E | | | | | | |
| Shell / Theme / Adaptive | F | | | | | | |

---

## Severity key

| Sev | Meaning |
|-----|---------|
| P0 | Crash, data loss, schedule wrong fire, PIN bypass |
| P1 | Beta blocker for centre staff: unreadable at 2 m, Stop clipped, cannot add course/event, health lies |
| P2 | Visual drift from handoff, polish, minor copy |
| P3 | Nice-to-have / M5+ |

---

## Wave 1 findings

_(agents append below)_
