# Beta screen review & design polish — multi-agent work assignment

> **For agentic workers (Claude Code):** This is the **complete work assignment**.  
> Use **multiple subagents in parallel** (waves below).  
> **REQUIRED skills every agent must load before coding:**  
> `android-jetpack-compose`, `adaptive`, `android-cli`, `testing-setup`, `android-data-layer`  
> (paths: repo `skills/`, or `$HOME/.claude-personal/skills/` when `CLAUDE_CONFIG_DIR=~/.claude-personal`).  
> Also load project rules: `AGENTS.md`, `CLAUDE.md`, `PROGRESS.md`, `docs/FABLE-REVIEW.md`.

**Goal:** Bring every shipped screen to **beta-ready** visual and interaction quality against the Nocturne handoff, fix gaps that block human tablet testing, produce a **debug beta APK** + **1280×800 screenshot pack** + **human QA checklist**.

**Architecture:** UI is a Compose client of `GongService`. Do **not** move scheduling/play into the Activity. Domain rules stay pure JVM under `domain/`. Screen polish must not change fire/grace/guard semantics unless a unit test fails first.

**Tech stack:** Kotlin, Compose Material 3, Nocturne tokens (`ui/Theme.kt`), Room, Media3, minSdk 29, target 35, landscape appliance ~1280×800.

**Beta version label:** bump `versionName` to `0.2.0-beta1` and `versionCode` to `2` only in the final integrator commit (Wave 4). Do not bump mid-wave.

---

## Global constraints (every agent)

1. **Behavioural law wins.** Never invent schedule/play semantics. If UI and domain conflict, domain + unit tests win. New behaviour → failing unit test first when practical.
2. **Domain purity.** No Android imports under `app/src/main/java/org/dhamma/gong/domain/`.
3. **No Deshna / cloud / analytics.** Out of scope.
4. **No real doha masters** in git. Debug synthetic tones only.
5. **Do not commit** `local.properties`, keystores, secrets, or agent trailer watermarks in commit messages (see `CLAUDE.md` git rules).
6. **Landscape first.** Target 1280×800; phone must scroll without clipping Stop / primary actions.
7. **Hit targets ≥ 44 dp; text ≥ 12 sp** (`Nocturne.MIN_*`).
8. **Immediate saves** — no Save buttons; toast on writes.
9. **UI is a client** of `GongService` — closing Activity must not stop scheduling.
10. **Skills are mandatory.** At the start of each agent brief, state which skills you loaded and what you used them for.
11. **Parallel safety.** Agents own disjoint file sets (table below). Shared files only via Wave 0 lock list or Wave 4 integrator.
12. **Evidence.** Each agent ends with: files touched, `./gradlew` commands run, pass/fail, residual risks, screenshot paths if taken.

### Skill → use map

| Skill | Load when |
|-------|-----------|
| `android-jetpack-compose` | Any Compose state, hoisting, lists, side-effects, Material 3 |
| `adaptive` | Nav rail, 1280×800, weight/scroll traps, large-screen layout |
| `android-cli` | Emulator, install, `wm size/density`, screencap, logcat |
| `testing-setup` | New unit/UI tests, Robolectric, Compose test strategy |
| `android-data-layer` | Room/repo only if a screen needs a data fix (prefer not in pure UI agents) |

### Design sources of truth (read order)

1. Unit tests under `app/src/test/` — **behavioural law**
2. `docs/handoff/README.md` + open `docs/handoff/Gong Appliance Screens.dc.html` in a browser
3. `docs/handoff/Gong Appliance Design Doc.dc.html` (architecture + locked-screen *questions*)
4. Current Compose: `app/src/main/java/org/dhamma/gong/ui/`
5. Existing shots: `docs/screenshots/` + README capture recipe
6. `docs/FABLE-REVIEW.md` open items (B10–B12, B15, M5–M7)

### Repo & verify

```bash
cd /Users/wizops/DIPI/niyam   # or clone of kapaggar/niyam
export ANDROID_HOME=/Users/wizops/Android/Sdk   # adjust if needed
export CLAUDE_CONFIG_DIR="$HOME/.claude-personal"  # this machine's Claude personal

./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

### Known good prior art (do not re-break)

- B1 appliance timezone, B2 transactional `applyOutcome`, B3 play-then-gap
- B5 PIN gate, B6/B14 permission health rows, B7 no locked boot, B8 OVERLAP status, B9 `active_course_id` pin
- `GONGS ONLY` chip when doha slots empty
- Landscape lock; hero time steps down below 560 dp width

### Explicit non-goals for this assignment

| Out | Why |
|-----|-----|
| Full M5 visual design of Sounds/Audio/Network as production hi-fi | Handoff says undesigned; only **minimal usable** stubs allowed (see Wave 2 Agent M5) |
| Backup/restore SAF (full M6) | Separate milestone; optional thin export later if time |
| Play Store listing / R8 release signing | Human beta uses **debug** APK |
| Hindi localization | Open question; English only for beta |
| Phosphor icon library migration | Nice-to-have; Unicode placeholders OK if time-boxed; prefer **one** consistent set if any agent swaps icons |

---

## Parallel ownership map (file locks)

| Owner | Primary files (read-write) | May read-only |
|-------|----------------------------|---------------|
| **Lead (Wave 0 / Wave 4)** | `PROGRESS.md`, `docs/FABLE-REVIEW.md`, `docs/screenshots/**`, `app/build.gradle.kts` version only at end | everything |
| **A Dashboard** | `ui/DashboardScreen.kt` | Theme, AppViewModel, service APIs |
| **B Courses** | `ui/CoursesScreen.kt` | AppViewModel, domain Course |
| **C Schedule** | `ui/ScheduleScreen.kt` | AppViewModel, events DAO |
| **D Logs** | `ui/LogsScreen.kt` | play log entity |
| **E PIN / Security** | `ui/PinScreens.kt`, PIN-related only in `GongApp.kt` if needed | `domain/PinCode.kt` (change only with tests) |
| **F Shell / Theme / Adaptive** | `ui/Theme.kt`, `ui/GongApp.kt` (nav rail, LockedScreen, toast), `ui/MainActivity.kt` | all screens for consistency |
| **G M5 stubs (optional wave)** | **new** `ui/TimeScreen.kt`, `ui/SetupScreen.kt` only; wire tabs in GongApp with F | design doc §08 |
| **H Data (only if needed)** | `data/*`, `AppViewModel.kt` | — coordinate via Lead |
| **I Tests** | `app/src/test/**`, optional `androidTest` | production code read-only except test doubles |

**Conflict rule:** If two agents need `AppViewModel.kt` or `GongApp.kt`, Lead serializes: F owns shell; others open a PR-style note or sequential commit after F lands.

---

## Wave 0 — Lead agent (sequential, ~15–20 min)

**Role:** Orchestrator. Do **not** polish screens yet.

### Steps

- [ ] **0.1** Confirm clean tree / branch. Create working branch if human wants isolation:
  ```bash
  git checkout main && git pull
  git checkout -b beta/screen-review
  ```
- [ ] **0.2** Load skills list + paste this assignment path into the session.
- [ ] **0.3** Run baseline:
  ```bash
  ./gradlew :app:testDebugUnitTest :app:assembleDebug
  ```
  Record test count (expect ≥ 130 green).
- [ ] **0.4** Produce `docs/superpowers/plans/beta-screen-audit-matrix.md` with one row per screen (template below) — leave Status empty for Wave 1 agents to fill.
- [ ] **0.5** Launch **Wave 1 in parallel** (Agents A–F). Paste each agent the **Agent brief** section + file lock + “return findings in the matrix format”.
- [ ] **0.6** After Wave 1 returns: triage findings into **P0 / P1 / P2** (definitions below). Cap Wave 2 scope so beta ships in one session series:
  - **Must ship (P0/P1):** anything that blocks 2 m readability, clips primary actions, breaks PIN, wrong ACTIVE course paint, broken countdown, missing GONGS ONLY, unusable schedule grid on 1280×800
  - **Should ship (P2):** spacing/token drift, empty states, a11y contentDescription, toast copy
  - **Defer:** full M5 hi-fi, backup, icon library, instrumented suite rewrite

### Severity definitions

| Sev | Meaning |
|-----|---------|
| **P0** | Crash, data loss, schedule wrong fire, PIN bypass |
| **P1** | Beta blocker for human centre staff: unreadable at 2 m, Stop clipped, cannot add course/event, health lies |
| **P2** | Visual drift from handoff, polish, minor copy |
| **P3** | Nice-to-have / M5+ |

### Audit matrix row template

```markdown
| Screen | Agent | Handoff match (Y/N/partial) | Adaptive 1280×800 | State/VM issues | A11y | Top fixes (≤5) | Sev |
|--------|-------|-----------------------------|-------------------|-----------------|------|----------------|-----|
```

---

## Wave 1 — Parallel screen audit (no large rewrites)

**Mode:** 6 agents **in parallel**. Read-only preferred; tiny typo fixes OK only if zero conflict risk.  
**Deliverable each:** filled matrix rows + ordered fix list with file:line pointers + proposed Compose changes (sketch, not full patch unless trivial).

### Shared audit checklist (every screen agent)

Using **android-jetpack-compose** + **adaptive**:

1. **Visual parity** vs `docs/handoff/README.md` for that screen (spacing, type scale, colors from `Nocturne`, mono for times).
2. **Layout traps:** `weight()` inside unbounded scroll; nested scroll; fixed 394 dp columns on narrow width; hero wrap.
3. **State:** `collectAsState` vs lifecycle; loading/null PIN flash; empty lists; error toasts.
4. **Touch:** ≥ 44 dp; destructive actions confirm where needed (courses delete already two-tap — verify).
5. **Copy:** toasts match product language; no paths that do not exist (`/sdcard/...` unless implemented).
6. **Service client:** no local ownership of scheduler/player.
7. **Screenshot plan:** which `EXTRA_TAB` / seed state for Wave 3 capture.

Using **android-cli** (optional in Wave 1 if emulator free): one quick glance at current build is enough; full reshoot is Wave 3.

---

### Agent A — Dashboard audit

**Skills:** `android-jetpack-compose`, `adaptive`  
**Files:** `ui/DashboardScreen.kt`, health/permission rows, test panel

**Must verify**

- [ ] Hero time: 98 → 76 → 56 sp stepping; maxLines 1; accent rule 214×3
- [ ] Countdown recomputed from seconds (no hand-rolled minute borrow)
- [ ] Appliance zone on “zero day · zone” line (not device TZ)
- [ ] Course card day progress segments; OVERLAP warning when two courses claim today
- [ ] Toggles: Master/Gong/Doha live; Relay inert 50%
- [ ] Health: Scheduler / Audio / Clock (tap confirm when untrusted) / Exact alarms / Battery / Notify / GONGS ONLY
- [ ] Next events two columns; past opacity 0.38; next time accent
- [ ] Bell rings one per strike; Test gong / Test doha / Stop; Stop never clipped on phone
- [ ] Test doha empty toast honest (GONGS ONLY language)

**Out of scope:** changing fire rules, alarm implementation.

---

### Agent B — Courses audit

**Skills:** `android-jetpack-compose`, `adaptive`  
**Files:** `ui/CoursesScreen.kt`

**Must verify**

- [ ] Header explains **start date = zero day**
- [ ] Add row: type select, date, note, Add; validation toasts
- [ ] Table columns / ACTIVE / OVERLAP / UPCOMING / PAST paint (B8)
- [ ] Delete two-tap confirm; clears `active_course_id` via repo when needed
- [ ] Empty state useful for first-run beta
- [ ] Readable at 1280×800 and on narrower landscape

---

### Agent C — Schedule audit

**Skills:** `android-jetpack-compose`, `adaptive`  
**Files:** `ui/ScheduleScreen.kt`

**Must verify**

- [ ] Grid columns: day 0…N + DEF (or N/C for no-course)
- [ ] Cell select / create default event; inspector 272 dp aside
- [ ] Gap/track **em-dash = inherit (null)** — load-bearing; do not flatten to defaults in DB
- [ ] Strike stepper 1–32; remove event
- [ ] Horizontal scroll for many day columns; no crash on weight/scroll
- [ ] Type switch reshapes grid without losing selection badly

---

### Agent D — Logs audit

**Skills:** `android-jetpack-compose`, `testing-setup` (for what to test later)  
**Files:** `ui/LogsScreen.kt`

**Must verify**

- [ ] UTC “When”; detail may carry local scheduled instant
- [ ] Filters: all / gong / doha / missed / error
- [ ] Result colors: ok / missed|error / stopped|skipped_clock
- [ ] Empty state reachable (not covered by fillMaxSize list — fixed once; re-check)
- [ ] Monospace fields; sticky header if present

---

### Agent E — PIN / Security audit

**Skills:** `android-jetpack-compose`, `android-data-layer` (settings key only)  
**Files:** `ui/PinScreens.kt`, gate in `ui/GongApp.kt`, `domain/PinCode.kt` (read; tests if change)

**Must verify**

- [ ] `pinHash == null` → blank (no dashboard flash); then lock or app
- [ ] Set / change / remove flows; 4–8 digits; mismatch toasts
- [ ] Unlock lasts for ViewModel life only (rotation OK; process death re-locks)
- [ ] Keypad a11y; large tablet targets
- [ ] No PIN stored plaintext

**Do not** weaken PBKDF2 parameters without Lead + test update.

---

### Agent F — Shell / Theme / Adaptive audit

**Skills:** `adaptive`, `android-jetpack-compose`, `android-cli`  
**Files:** `Theme.kt`, `GongApp.kt`, `MainActivity.kt`, locked tab stubs

**Must verify**

- [ ] Nav rail 186 dp; labels/glyphs; locked tabs 42% + lock glyph; inert
- [ ] Nocturne tokens complete vs handoff table; no one-off hex in screens (prefer tokens)
- [ ] Toast position/timing 2.6 s
- [ ] `EXTRA_TAB` deep link for screenshots
- [ ] Notification request + permission refresh on resume still works
- [ ] Landscape lock; configChanges sensible
- [ ] Locked screens copy tells staff *what the tab will do* (design doc questions), not just “coming soon”

---

## Wave 2 — Parallel implementation (after Lead triage)

**Mode:** Parallel agents only on **disjoint files**. Each agent implements **their P0/P1 list only**, then runs:

```bash
./gradlew :app:compileDebugKotlin
# if domain/repo touched:
./gradlew :app:testDebugUnitTest
```

Commit message style (each agent, if human allows per-agent commits):

```text
Polish <Screen>: <one-line why>

<details>. Unit tests: N green / compile only.
```

### Agent A2 — Dashboard implement

- [ ] Apply Lead-approved P0/P1 from Agent A
- [ ] Ensure health rows remain tappable for B6 grants
- [ ] Preserve second-tick clock + countdown math
- [ ] Compile + smoke logic review

### Agent B2 — Courses implement

- [ ] Apply approved P0/P1
- [ ] Keep ACTIVE/OVERLAP semantics
- [ ] Empty-state + zero-day copy polish if listed

### Agent C2 — Schedule implement

- [ ] Apply approved P0/P1
- [ ] **Regression check:** nullable gap/track still null in Room when “—” selected
- [ ] If you touch event write path, add/extend unit or repository test

### Agent D2 — Logs implement

- [ ] Apply approved P0/P1
- [ ] Empty + filter polish

### Agent E2 — PIN implement

- [ ] Apply approved P0/P1
- [ ] If `PinCode` changes: update `PinCodeTest`; keep hash format compatible or migrate explicitly

### Agent F2 — Shell / Theme implement

- [ ] Token cleanup: replace stray colors with `Nocturne.*` where cheap
- [ ] Locked tab copy improvement
- [ ] Nav rail selection/contrast at 2 m
- [ ] Do **not** enable full M5 screens unless Agent G runs

### Agent G — M5 minimal stubs (optional parallel, Lead-gated)

Only if Lead decides beta needs them for human ops. **Not** full design systems.

**Skills:** `android-jetpack-compose`, `adaptive`, `android-cli`

**New files (suggested):**

- `ui/TimeScreen.kt` — show appliance zone + device zone for contrast; list common zones or free-text IANA id with validation via `ApplianceZone`; **Confirm clock** button calling `vm.confirmClock()`; save `timezone` via `setSetting`
- `ui/SetupScreen.kt` — checklist UI binding `AppliancePermissions.status`: notifications, exact alarms, battery; buttons reuse `AppliancePermissions.open*`; show scheduler running + last tick if available

**Wire:** set `Tab.TIME.enabled = true`, `Tab.SETUP.enabled = true` in `GongApp.kt` (coordinate with F). Leave Sounds / Audio out / Network **locked** unless trivial.

**Tests:** zone resolution already covered; add ViewModel-level test only if pure logic extracted.

**Do not** implement SoftAP, Wi‑Fi list, or Bluetooth route picker here (M6).

---

## Wave 3 — Adaptive verification + screenshot pack (parallel possible)

**Skills:** `android-cli`, `adaptive`  
**Agents:** one **Capture** agent (serial AVD) + optional **Review** agent on images.

### Capture recipe (mandatory)

```bash
export ANDROID_HOME=/Users/wizops/Android/Sdk
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"

# Prefer existing AVD cca34
adb devices
# Force appliance surface (always reset after session)
adb shell wm size 1280x800
adb shell wm density 160
adb shell settings put system user_rotation 0
adb shell settings put system accelerometer_rotation 0

./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell pm grant org.dhamma.gong android.permission.POST_NOTIFICATIONS

# Helper: screencap
shot() { adb exec-out screencap -p > "docs/screenshots/$1.png"; }

adb shell am start -n org.dhamma.gong/.ui.MainActivity -e tab DASHBOARD
sleep 2; shot 10-beta-dashboard-idle

# Seed a course for active states (UI or adb deep paths — prefer UI automation notes)
# COURSES → add 10 Day start=today → back to dashboard
adb shell am start -n org.dhamma.gong/.ui.MainActivity -e tab COURSES
# (human or uiautomator steps — document what you did)
shot 11-beta-courses
adb shell am start -n org.dhamma.gong/.ui.MainActivity -e tab SCHEDULE
shot 12-beta-schedule
adb shell am start -n org.dhamma.gong/.ui.MainActivity -e tab LOGS
shot 13-beta-logs
adb shell am start -n org.dhamma.gong/.ui.MainActivity -e tab SECURITY
shot 14-beta-pin
# if Time/Setup enabled:
adb shell am start -n org.dhamma.gong/.ui.MainActivity -e tab TIME
shot 15-beta-time
adb shell am start -n org.dhamma.gong/.ui.MainActivity -e tab SETUP
shot 16-beta-setup

# ALWAYS restore emulator geometry for other projects
adb shell wm size reset
adb shell wm density reset
```

- [ ] Update `docs/screenshots/README.md` gallery with new beta shots
- [ ] Flag any shot that still looks phone-broken at 1280×800 → back to owning agent

---

## Wave 4 — Integrator (Lead only, sequential)

**Skills:** `testing-setup`, `android-cli`, `verification-before-completion` mindset

- [ ] **4.1** Merge all Wave 2 commits; resolve `GongApp` / `AppViewModel` conflicts carefully
- [ ] **4.2** Full verify:
  ```bash
  ./gradlew :app:testDebugUnitTest :app:assembleDebug
  ```
  Claim green only with command output evidence.
- [ ] **4.3** Bump version for human beta:
  - `versionName = "0.2.0-beta1"`
  - `versionCode = 2`
- [ ] **4.4** Rebuild APK; copy artifact note:
  ```text
  app/build/outputs/apk/debug/app-debug.apk
  ```
- [ ] **4.5** Write `docs/BETA-QA-CHECKLIST.md` (template below) for the human tester
- [ ] **4.6** Update `PROGRESS.md` (beta polish slice) + FABLE-REVIEW “Last verified”
- [ ] **4.7** Commit (clean message, no Claude trailers) and push branch / open PR if human wants review before main
- [ ] **4.8** Hand human: APK path, checklist, screenshot folder, known gaps

### Human beta QA checklist (create as `docs/BETA-QA-CHECKLIST.md`)

```markdown
# Niyam 0.2.0-beta1 — human QA

Device: ________  Android: ________  Build: debug APK

## Install
- [ ] Install APK; launcher shows gong icon
- [ ] Grant notifications if prompted
- [ ] Open app; service notification appears (if granted)

## Dashboard
- [ ] Time readable from ~2 m (or tablet distance)
- [ ] No course → add course path obvious
- [ ] Test gong: hear strikes; rings; Stop works
- [ ] GONGS ONLY visible if no doha pack
- [ ] Health rows: if amber, tap opens system settings

## Courses
- [ ] Add 10 Day, start = today → Day 0 on dashboard
- [ ] Overlap: two courses same window → one ACTIVE, one OVERLAP
- [ ] Delete confirm works

## Schedule
- [ ] Grid shows days + DEF
- [ ] Add/edit event; inherit gap/track (—) works
- [ ] Remove event

## Logs
- [ ] Test gong appears; filters work

## PIN
- [ ] Set PIN → kill app → reopen → lock shows (no dashboard flash)
- [ ] Wrong PIN rejected; correct unlocks
- [ ] Remove PIN works

## Time / Setup (if enabled)
- [ ] Timezone change affects hero “today” / zero-day label
- [ ] Confirm clock when untrusted
- [ ] Setup checklist matches real permission state

## Overnight / soak (optional same day)
- [ ] Schedule event +2 min; leave screen off; hear gong
- [ ] Reboot device; after unlock, service returns; next alarm armed

## Failures / notes
...
```

---

## Multi-agent orchestration recipe (Claude Code)

Paste this to the **parent** Claude session:

```text
You are the Lead for Niyam beta screen review.

Read and follow entirely:
  docs/superpowers/plans/2026-08-09-beta-screen-review-assignment.md

Mandatory skills (load before work): android-jetpack-compose, adaptive,
android-cli, testing-setup, android-data-layer — from skills/ or
~/.claude-personal/skills/. Also AGENTS.md + CLAUDE.md.

Execution mode: MULTI-AGENT PARALLEL by wave.

Wave 0 (you): baseline tests, create audit matrix file, then SPAWN six
subagents in parallel for Wave 1 Agents A–F. Each subagent gets ONLY its
brief + file lock + global constraints from the assignment.

After Wave 1: triage P0/P1/P2. Spawn Wave 2 implementers in parallel on
disjoint files only. Optional Agent G for Time+Setup stubs if you judge
beta needs them.

Wave 3: one agent for 1280×800 screenshots via android-cli (reset wm after).

Wave 4 (you): integrate, full test suite, version 0.2.0-beta1, BETA-QA-CHECKLIST,
PROGRESS update, final APK path for human.

Hard rules: no schedule semantic invention; no Claude commit trailers;
domain stays pure; do not push to main without summarizing for the human
unless they asked for push.

Return a single executive summary: what changed per screen, test counts,
APK path, residual risks, and the human checklist path.
```

### Suggested subagent prompt skeleton

```text
You are Agent <LETTER> for Niyam (<Screen>).

Load skills: <list>.
Read: docs/superpowers/plans/2026-08-09-beta-screen-review-assignment.md
      sections Global constraints + your Agent brief only.
Repo: /Users/wizops/DIPI/niyam (or $PWD).
File lock (write): <paths>. Do not edit other agents' files.
Wave: <1 audit | 2 implement>.
Deliverable: <matrix rows | commits + evidence>.
If blocked on shared file, stop and report to Lead — do not invent ownership.
```

### Parallelism limits

| Wave | Max parallel agents | Notes |
|------|---------------------|-------|
| 1 | 6 (A–F) | Read-heavy; OK |
| 2 | 4–6 | Strict file locks; serialize GongApp/VM |
| 3 | 1 on AVD | Emulator is a single resource |
| 4 | 1 | Lead only |

If the harness cannot spawn 6, run **A+B+C** then **D+E+F** in two batches.

---

## Acceptance criteria (beta ready)

| # | Criterion | Evidence |
|---|-----------|----------|
| 1 | All unit tests green | `./gradlew :app:testDebugUnitTest` log |
| 2 | Debug APK builds | `assembleDebug` + path |
| 3 | Four core screens + PIN usable at 1280×800 | screenshots 10–14 |
| 4 | No P0 open; P1 either fixed or listed on checklist as known | Lead summary |
| 5 | Handoff-critical behaviours preserved | ACTIVE/OVERLAP, inherit gap, PIN flash-free, GONGS ONLY, health taps |
| 6 | Human has QA checklist | `docs/BETA-QA-CHECKLIST.md` |
| 7 | versionName `0.2.0-beta1` | `app/build.gradle.kts` |

---

## Effort estimate (for credit planning)

| Wave | Wall clock (parallel) | Serial equivalent |
|------|----------------------|-------------------|
| 0 Lead | 20 min | 20 min |
| 1 Audits ×6 | 30–45 min | 3 h |
| 2 Implements | 45–90 min | 4–6 h |
| 3 Screenshots | 30–45 min | 45 min |
| 4 Integrate + APK | 30–45 min | 45 min |
| **Total** | **~3–4 h parallel** | **~10–12 h solo** |

Optional Agent G (Time/Setup): +45–60 min parallel.

---

## Risk register

| Risk | Mitigation |
|------|------------|
| Agents rewrite domain “for cleanliness” | File locks + behavioural law banner |
| Emulator orientation stuck after wm | Always `wm size/density reset` in Wave 3 |
| PIN flash regression | Explicit Agent E checklist + process-death test note for human |
| Schedule inherit gap flattened | Agent C regression step + Room null check |
| Scope explosion into M5/M6 | Lead triage cap; G optional |
| Commit trailers | CLAUDE.md ban; Lead review messages |

---

## Kickoff one-liner (human → Claude)

> Execute `docs/superpowers/plans/2026-08-09-beta-screen-review-assignment.md` as Lead with multi-agent parallel waves. Use the five Android skills. Ship `0.2.0-beta1` debug APK + screenshots + `docs/BETA-QA-CHECKLIST.md` for human tablet verification. Do not invent schedule semantics.

---

*End of assignment.*
