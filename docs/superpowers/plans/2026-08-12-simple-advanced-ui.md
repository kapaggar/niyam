# Simple / Advanced UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship a `ui_mode` setting (`simple` default / `advanced`) that shrinks the nav rail to five destinations, folds Network and a minimal Amp-power card into Setup, and cuts the copy on every screen a Simple user sees — without touching schedule semantics or any stored Advanced setting.

**Architecture:** `UiMode` is a domain enum stored in the Room `settings` table, parsed exactly like the existing `ThemeMode` (unknown/blank → default). `Tab.railFor(UiMode)` is a pure function over the existing `Tab` enum — the only new JVM-testable logic in the feature, and the thing the acceptance criteria call "rail composition". Everything else is Compose: `GongApp` renders `Tab.railFor(mode)` instead of `Tab.entries`, `NetworkScreen` becomes a `NetworkCard` embedded in Setup and its tab is deleted, and a new `AmpPowerSimpleCard` binds the *existing* `AppViewModel` relay APIs to a four-control subset. No backend keys change: `relay_host`, `relay_enabled`, `relay_auth_*`, `relay_switch_id`, `relay_lead_seconds`, `relay_lag_seconds` keep their names, defaults and readers.

**Tech Stack:** Kotlin 2.0.x, Jetpack Compose + Material 3 (Nocturne palette), Room, JUnit4 JVM unit tests. minSdk 29, target/compile 35. Gradle 8.9 / AGP 8.7.x.

**Source spec:** `docs/superpowers/specs/2026-08-12-simple-advanced-ui-design.md`

---

## Global Constraints

Every task's requirements implicitly include this section.

- **No domain / schedule changes.** Nothing under `domain/` that the scheduler
  reads may change behaviour. `SchedulerCore`, `FireRules`, `ScheduleMaterializer`,
  `RelayPlan` are not touched by this plan. The one new `domain/` file
  (`UiMode.kt`) is presentation-only, like `ThemeMode.kt`.
- **No new permissions, no new network calls.** Same manifest.
- **Gong must still play when the Shelly is unreachable.** Existing law
  (`docs/superpowers/specs/2026-08-09-shelly-relay-design.md`); no code in this
  plan may make a play path await a relay result.
- **Never log or render the PIN or the relay password.** `relay_auth_pass` is
  not read into any Simple-mode composable at all.
- **Simple never wipes Advanced fields.** Switching modes only changes which
  destinations render. No task may clear, reset or migrate a settings row.
- **Settings key is `ui_mode`, values `simple` | `advanced`, default `simple`.**
  Exact strings, lowercase.
- **Backfill is automatic** — `SeedLoader.apply` runs `insertMissing` over
  `SettingsDefaults.all` on every start (`SeedLoader.kt:109-115`), so adding a
  key to `SettingsDefaults.androidExtras` is the whole "seed missing" job. Do
  not write a Room migration.
- **`nav_<TAB>` semantics contract stays.** `GongApp.kt:248-251` sets
  `contentDescription = "nav_${tab.name}"`; screenshot automation and
  `docs/screenshots/README.md` match on it. Do not rename or reformat it.
- **Nocturne tokens only.** No raw `Color(0xFF…)`, no hardcoded touch heights —
  use `Nocturne.*` and `Nocturne.MIN_TOUCH_DP.dp`.
- **No Material icons dependency.** The version catalog ships
  `androidx.compose.material3:material3` only (`gradle/libs.versions.toml:37`)
  and glyphs outside the platform font have drawn as tofu before
  (`GongApp.kt:85-87`). Draw the ⓘ affordance from a bordered `Box` + `Text("i")`.
- **Verify after every task:**
  ```bash
  ./gradlew :app:testDebugUnitTest
  ./gradlew :app:assembleDebug
  ```
  Both must be green before the commit.
- **Commit messages:** imperative subject ≤ ~72 chars, optional body about what
  changed and why. **No** `Co-Authored-By:`, no session URLs, no
  "Generated with", no empty trailer blocks (`CLAUDE.md`, "Git commit messages").
- **Version bump happens once, in Task 8.** See "Deviations" below.
- **Branch:** work continues on `beta/screen-review`. The working tree already
  carries uncommitted Courses/Logs/version edits from a prior session — commit
  or stash those *before* Task 1 so each task below is a clean, revertible diff.

---

## Locked decisions (spec open points resolved)

The spec left three points open (§12). This plan closes them so no task has to
stop and ask:

1. **ⓘ component = `AlertDialog`.** Already the app's one modal pattern
   (`SetupScreen.kt:471` restore confirm). No bottom sheet, no second pattern.
2. **Advanced keeps the username field.** Removing it is a separate change with
   its own risk (a centre with a locked Gen1 Shelly), and the spec puts key
   removal out of scope (§8). Unchanged.
3. **Simple Dashboard gets no read-only "Amp: on/off" chip.** Spec calls it
   nice-to-have (§12.3); Setup is the single place amp state is read and edited
   in Simple. Not built.

## Deviations from the spec (deliberate, flagged for review)

- **§4.1 ordering vs the wide layout.** The spec lists Appliance state last
  (item 8). Setup today pairs Permissions | Appliance state in a two-column row
  above 900 dp (`SetupScreen.kt:254-265`) — that pairing is the readiness view
  the Pixel C review approved. This plan keeps that row where it is and inserts
  Network | Amp power as a second wide row beneath it, then Appearance, PIN,
  UI mode, Backup. Simple's vertical (narrow) stack follows spec order exactly.
- **One version bump for the whole feature.** `CLAUDE.md` asks for a bump per
  substantive change; eight task-commits on one branch would burn eight
  `versionCode`s for one APK a tester ever sees. Task 8 bumps once
  (`versionCode` 12 → 13, `versionName` → `0.2.0-beta12`) and the QA checklist
  and `PROGRESS.md` name that build. If any task below is shipped to a tester
  on its own, bump then instead.
- **§9 Courses / Schedule / Logs empty-state copy is already short.**
  `LogsScreen.kt:151` is "Nothing logged yet.", `CoursesScreen.kt:212` is
  "No courses yet." — there is no essay left to move to ⓘ. No task is written
  for it; Task 8 records the check.
- **No Compose UI tests.** The project has no `app/src/androidTest` source set
  and no Robolectric. TDD applies to the two pure-Kotlin pieces (`UiMode`,
  `Tab.railFor`); Compose changes are verified by `assembleDebug` plus the
  manual QA checklist rows Task 8 adds. Do not add a UI-test framework as a
  side effect of this plan.

---

## File Structure

**Created**

| File | Responsibility |
|------|----------------|
| `app/src/main/java/org/dhamma/gong/domain/UiMode.kt` | The `simple`/`advanced` enum: setting key, default, tolerant `parse`. Presentation-only, mirrors `ThemeMode.kt`. |
| `app/src/test/java/org/dhamma/gong/domain/UiModeTest.kt` | Default is `simple`; garbage parses to default; round-trips. |
| `app/src/test/java/org/dhamma/gong/ui/TabRailTest.kt` | Rail composition: Simple = 5 named tabs in order, Advanced = every tab, Network in neither. |
| `app/src/main/java/org/dhamma/gong/ui/NetworkCard.kt` | The Network facts card + Wi-Fi/hotspot buttons + its own poll loop, embedded in Setup. Replaces `NetworkScreen.kt`. |
| `app/src/main/java/org/dhamma/gong/ui/AmpPowerCard.kt` | `AmpPowerSimpleCard` — host/IP, Test, Amp ON/OFF, auto-with-schedule, honest status. |

**Modified**

| File | Change |
|------|--------|
| `domain/Models.kt:226-243` | `ui_mode` added to `SettingsDefaults.androidExtras`. |
| `ui/AppViewModel.kt:160-169` | `uiMode: StateFlow<UiMode>` + `setUiMode`, alongside `themeMode`. |
| `ui/GongApp.kt:66-91, 93-198, 200-235` | `Tab.railFor`, `NETWORK` entry deleted, rail + deep link filtered by mode. |
| `ui/SetupScreen.kt` | UI-mode card, Network card, Amp card, clock row with Confirm, permission copy → ⓘ. |
| `ui/Controls.kt` | Gains `InfoDot`, `CommittingField`, `Toggle` — the three shared pieces more than one screen now needs. |
| `ui/DashboardScreen.kt:316-329, 355-395, 110-125` | Relay toggle hidden in Simple; `Toggle` moves out to Controls; clock banner stops naming a hidden screen. |
| `ui/RelayScreen.kt:259-285` | Local `CommittingField` deleted in favour of the shared one. |
| `ui/DohaMediaScreen.kt:230-260` | Doha time saves on focus loss like every other field; Save button gone. |
| `ui/MainActivity.kt:100-115` | Deep-link doc comment drops `NETWORK`. |
| `docs/screenshots/README.md`, `docs/BETA-QA-CHECKLIST.md`, `PROGRESS.md`, `app/build.gradle.kts` | Docs + version. |

**Deleted**

| File | Why |
|------|-----|
| `app/src/main/java/org/dhamma/gong/ui/NetworkScreen.kt` | Spec §6: Network is not a destination in either mode. Its content moves to `NetworkCard.kt`; leaving a dead screen behind invites drift between two copies of the same facts. |

---

## Task 1: The `ui_mode` setting

**Files:**
- Create: `app/src/main/java/org/dhamma/gong/domain/UiMode.kt`
- Create: `app/src/test/java/org/dhamma/gong/domain/UiModeTest.kt`
- Modify: `app/src/main/java/org/dhamma/gong/domain/Models.kt:226-243`
- Modify: `app/src/main/java/org/dhamma/gong/ui/AppViewModel.kt:160-169`

**Interfaces:**
- Consumes: `SettingsDefaults.androidExtras` (existing map in `Models.kt`);
  `AppViewModel.settings: StateFlow<Map<String, String>>`;
  `AppViewModel.setSetting(key: String, value: String, announce: String)`.
- Produces:
  - `org.dhamma.gong.domain.UiMode` — enum, entries `SIMPLE`, `ADVANCED`,
    properties `key: String`, `label: String`, `why: String`;
    `UiMode.SETTING_KEY: String` = `"ui_mode"`; `UiMode.DEFAULT` = `SIMPLE`;
    `UiMode.parse(raw: String?): UiMode`.
  - `AppViewModel.uiMode: StateFlow<UiMode>` (Eagerly-started, seeded with
    `UiMode.DEFAULT`).
  - `AppViewModel.setUiMode(mode: UiMode)`.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/org/dhamma/gong/domain/UiModeTest.kt`:

```kotlin
package org.dhamma.gong.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class UiModeTest {

    @Test
    fun theShippedDefaultIsSimple() {
        // A centre server opening the appliance for the first time gets five
        // screens, not nine. Flipping this is a product decision, not a tidy-up.
        assertEquals(UiMode.SIMPLE, UiMode.DEFAULT)
        assertEquals("simple", SettingsDefaults.all.getValue(UiMode.SETTING_KEY))
    }

    @Test
    fun garbageResolvesToTheDefaultRatherThanCrashing() {
        // A restore from a backup written before this release has no `ui_mode`
        // row at all; a hand-edited one can hold anything.
        listOf(null, "", "  ", "expert", "SIMPLEE", "1").forEach {
            assertEquals("input=$it", UiMode.DEFAULT, UiMode.parse(it))
        }
    }

    @Test
    fun parseIsCaseAndWhitespaceInsensitive() {
        assertEquals(UiMode.ADVANCED, UiMode.parse("advanced"))
        assertEquals(UiMode.ADVANCED, UiMode.parse(" Advanced "))
        assertEquals(UiMode.SIMPLE, UiMode.parse("SIMPLE"))
    }

    @Test
    fun everyModeRoundTripsThroughItsStoredKey() {
        UiMode.entries.forEach { assertEquals(it, UiMode.parse(it.key)) }
    }
}
```

- [ ] **Step 2: Run the test and watch it fail**

Run: `./gradlew :app:testDebugUnitTest --tests '*UiModeTest*'`
Expected: FAIL — compilation error, `Unresolved reference: UiMode`.

- [ ] **Step 3: Write `UiMode`**

Create `app/src/main/java/org/dhamma/gong/domain/UiMode.kt`:

```kotlin
package org.dhamma.gong.domain

/**
 * How many destinations the shell offers.
 *
 * Two different people use this appliance. A centre server sets a course up in
 * the morning and wants the five screens that run it. A technician wires the
 * amplifier relay, pins the timezone and maps doha slots once, and never comes
 * back. Showing the technician's screens to the server every day is what made
 * the rail eleven items long.
 *
 * [SIMPLE] is the shipped default because the server is the daily user, and
 * because a screen nobody in the hall needs is a screen somebody in the hall
 * can break. Switching modes only changes what renders — it never edits, clears
 * or migrates a setting, so a centre configured in [ADVANCED] keeps every one
 * of those values while showing the short rail.
 */
enum class UiMode(val key: String, val label: String, val why: String) {
    SIMPLE(
        key = "simple",
        label = "Simple",
        why = "The five screens a course needs. Amp power and network live in Setup.",
    ),
    ADVANCED(
        key = "advanced",
        label = "Advanced",
        why = "Adds sounds, audio out, the full amp page and the timezone pin.",
    ),
    ;

    companion object {
        const val SETTING_KEY = "ui_mode"

        val DEFAULT = SIMPLE

        /**
         * Unknown, blank and null all resolve to [DEFAULT]. A restore from a
         * backup written before this release carries no `ui_mode` row, and an
         * appliance that answered "no mode at all" would render an empty rail.
         */
        fun parse(raw: String?): UiMode =
            entries.firstOrNull { it.key == raw?.trim()?.lowercase() } ?: DEFAULT
    }
}
```

- [ ] **Step 4: Add the default**

In `app/src/main/java/org/dhamma/gong/domain/Models.kt`, inside
`SettingsDefaults.androidExtras`, directly under the `ThemeMode.SETTING_KEY`
line (currently `Models.kt:231`):

```kotlin
        // Presentation only — nothing in the scheduler reads it.
        ThemeMode.SETTING_KEY to ThemeMode.DEFAULT.key,
        // Which destinations the rail offers. Presentation only; Advanced-only
        // settings keep their values and keep being read in Simple.
        UiMode.SETTING_KEY to UiMode.DEFAULT.key,
```

(`Models.kt` is in package `org.dhamma.gong.domain`, so `UiMode` needs no import.)

- [ ] **Step 5: Run the test and watch it pass**

Run: `./gradlew :app:testDebugUnitTest --tests '*UiModeTest*'`
Expected: PASS, 4 tests.

- [ ] **Step 6: Expose it on the ViewModel**

In `app/src/main/java/org/dhamma/gong/ui/AppViewModel.kt`, add the import
alongside the other domain imports:

```kotlin
import org.dhamma.gong.domain.UiMode
```

and add this immediately after `setThemeMode` (currently `AppViewModel.kt:168-169`):

```kotlin
    /**
     * How many destinations the rail offers. Eagerly started and seeded with
     * the shipped default so the first frame draws the Simple rail rather than
     * flashing nine items while Room answers.
     */
    val uiMode: StateFlow<UiMode> = settings
        .map { UiMode.parse(it[UiMode.SETTING_KEY]) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, UiMode.DEFAULT)

    fun setUiMode(mode: UiMode) =
        setSetting(UiMode.SETTING_KEY, mode.key, announce = "${mode.label} screens")
```

- [ ] **Step 7: Verify the whole suite and the build**

Run:
```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```
Expected: both BUILD SUCCESSFUL. (`SeedAndRepositoryTest` iterates
`SettingsDefaults.map`, not `.all`, so the new key needs no test edit.)

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/org/dhamma/gong/domain/UiMode.kt \
        app/src/test/java/org/dhamma/gong/domain/UiModeTest.kt \
        app/src/main/java/org/dhamma/gong/domain/Models.kt \
        app/src/main/java/org/dhamma/gong/ui/AppViewModel.kt
git commit -m "Add a ui_mode setting, defaulting to simple"
```

---

## Task 2: Rail composition, the mode switch, and the ⓘ pattern

Simple hides Sounds, Audio out, Amp power and Time. The moment Time is hidden,
two strings that point at it become lies — Setup's clock row says
"untrusted — see Time" and the Dashboard banner says "Check the time on the
Time screen". Setup's is fixed here because this task already rewrites Setup;
the Dashboard's is fixed in Task 6.

**Files:**
- Modify: `app/src/main/java/org/dhamma/gong/ui/GongApp.kt:66-91` (Tab companion), `:93-198` (GongApp), `:200-235` (NavRail)
- Modify: `app/src/main/java/org/dhamma/gong/ui/Controls.kt` (add `InfoDot`)
- Modify: `app/src/main/java/org/dhamma/gong/ui/SetupScreen.kt:204-209` (clock row), `:267-275` (card order)
- Create: `app/src/test/java/org/dhamma/gong/ui/TabRailTest.kt`

**Interfaces:**
- Consumes: `UiMode`, `AppViewModel.uiMode`, `AppViewModel.setUiMode` (Task 1);
  `AppViewModel.confirmClock()` (existing, `AppViewModel.kt:985`);
  `ChoiceChip(label, selected, description, onClick)` (`Controls.kt:116`).
- Produces:
  - `Tab.railFor(mode: UiMode): List<Tab>` — the visible destinations, in rail
    order.
  - `InfoDot(title: String, body: String)` in `Controls.kt` — a 44 dp "i" badge
    that opens an `AlertDialog`. The one progressive-disclosure affordance;
    Tasks 3, 4 and 6 reuse it.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/org/dhamma/gong/ui/TabRailTest.kt`:

```kotlin
package org.dhamma.gong.ui

import org.dhamma.gong.domain.UiMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `Tab` is a plain enum with no Android types in it, so the rail's composition
 * is answerable on the JVM — which is the only reason this rule is a function
 * and not an `if` buried in a composable.
 */
class TabRailTest {

    @Test
    fun simpleShowsFiveDestinationsInWallOrder() {
        assertEquals(
            listOf(Tab.DASHBOARD, Tab.SCHEDULE, Tab.COURSES, Tab.LOGS, Tab.SETUP),
            Tab.railFor(UiMode.SIMPLE),
        )
    }

    @Test
    fun simpleHidesTheTechnicianScreens() {
        val simple = Tab.railFor(UiMode.SIMPLE)
        listOf(Tab.SOUNDS, Tab.AUDIO_OUT, Tab.POWER, Tab.TIME).forEach {
            assertTrue("$it must not be in the Simple rail", it !in simple)
        }
    }

    @Test
    fun advancedShowsEveryTab() {
        assertEquals(Tab.entries.toList(), Tab.railFor(UiMode.ADVANCED))
    }

    @Test
    fun everyModeStartsAtTheDashboard() {
        // GongApp falls back to DASHBOARD whenever the current tab leaves the
        // rail, so DASHBOARD being present in both modes is load-bearing.
        UiMode.entries.forEach {
            assertEquals("mode=$it", Tab.DASHBOARD, Tab.railFor(it).first())
        }
    }
}
```

Note: `Tab.SCHEDULE` before `Tab.COURSES` matches the current enum declaration
order (`GongApp.kt:72-75`). The spec's §3.2 table lists Courses second; the rail
keeps its shipped order rather than reshuffling muscle memory for a mode switch.

- [ ] **Step 2: Run the test and watch it fail**

Run: `./gradlew :app:testDebugUnitTest --tests '*TabRailTest*'`
Expected: FAIL — `Unresolved reference: railFor`.

- [ ] **Step 3: Add `railFor` to the `Tab` enum**

In `app/src/main/java/org/dhamma/gong/ui/GongApp.kt`, add the import:

```kotlin
import org.dhamma.gong.domain.UiMode
```

and close the `Tab` enum (currently ends `SETUP("Setup", "✓", requiresPin = true),`
then `}` at line 91) with a companion object:

```kotlin
    SETUP("Setup", "✓", requiresPin = true),
    ;

    companion object {
        /**
         * Set once by a technician, then never again: relay wiring, doha slot
         * mapping, the audio route and the timezone pin. Hiding them is what
         * Simple *is* — the settings behind them keep their values and keep
         * being read by the service either way.
         */
        private val ADVANCED_ONLY = setOf(SOUNDS, AUDIO_OUT, POWER, TIME)

        /** The visible destinations, in rail order. */
        fun railFor(mode: UiMode): List<Tab> = when (mode) {
            UiMode.ADVANCED -> entries.toList()
            UiMode.SIMPLE -> entries.filter { it !in ADVANCED_ONLY }
        }
    }
}
```

- [ ] **Step 4: Run the test and watch it pass**

Run: `./gradlew :app:testDebugUnitTest --tests '*TabRailTest*'`
Expected: PASS, 4 tests.

- [ ] **Step 5: Render the filtered rail**

In `GongApp` (`GongApp.kt:93-198`):

Read the mode next to the other collected state (after
`val unlocked by vm.unlocked.collectAsState()`, currently line 133):

```kotlin
    val mode by vm.uiMode.collectAsState()
    val rail = Tab.railFor(mode)
```

Replace the deep-link `LaunchedEffect` body (currently lines 110-114) so a
request for a hidden destination is consumed rather than silently landing on a
screen the rail cannot get back to:

```kotlin
    LaunchedEffect(requested) {
        val t = requested ?: return@LaunchedEffect
        if (t.enabled && t in rail) tab = t
        tabRequest?.value = null
    }
```

Add a fallback right after it, so switching Advanced → Simple while standing on
Amp power does not strand the user on a screen with no rail entry:

```kotlin
    // Advanced → Simple can pull the floor out from under the current screen.
    // The Dashboard is in both rails, which is what makes it a safe landing.
    LaunchedEffect(mode) { if (tab !in rail) tab = Tab.DASHBOARD }
```

Pass the rail down (currently line 148):

```kotlin
                NavRail(tabs = rail, current = tab, onSelect = { tab = it })
```

And change `NavRail`'s signature and loop (`GongApp.kt:201`, `:227`):

```kotlin
private fun NavRail(tabs: List<Tab>, current: Tab, onSelect: (Tab) -> Unit) {
```
```kotlin
        for (t in tabs) {
```

Leave the `when (tab)` content switch (`GongApp.kt:158-173`) exactly as it is —
every branch is still reachable in Advanced.

- [ ] **Step 6: Add the ⓘ affordance**

In `app/src/main/java/org/dhamma/gong/ui/Controls.kt`, add the import:

```kotlin
import androidx.compose.material3.AlertDialog
```

and append:

```kotlin
/**
 * The one way this app explains itself.
 *
 * Everything a control does is on the control. Everything about *why Android
 * behaves that way* goes behind this badge, in three sentences or fewer. It is
 * a dialog and not a hover tip because the appliance is a tablet on a wall and
 * there is no pointer to hover with.
 *
 * The badge is drawn rather than typed: glyphs outside the platform font have
 * shipped as tofu on centre tablets before.
 */
@Composable
fun InfoDot(title: String, body: String) {
    var open by remember { mutableStateOf(false) }
    Box(
        Modifier
            .size(Nocturne.MIN_TOUCH_DP.dp)
            .semantics { contentDescription = "About $title" }
            .clickable(role = Role.Button) { open = true },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(19.dp)
                .clip(RoundedCornerShape(10.dp))
                .border(1.dp, Nocturne.Neutral600, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text("i", fontSize = 12.sp, fontFamily = Nocturne.Mono, color = Nocturne.Neutral400)
        }
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            containerColor = Nocturne.Surface,
            title = { Text(title, fontSize = 17.sp, color = Nocturne.Text) },
            text = { Text(body, fontSize = 13.5.sp, color = Nocturne.Neutral300) },
            confirmButton = { OutlineButton("Close", Nocturne.Neutral300) { open = false } },
        )
    }
}
```

- [ ] **Step 7: Add the mode switch to Setup**

In `app/src/main/java/org/dhamma/gong/ui/SetupScreen.kt`, add the import:

```kotlin
import org.dhamma.gong.domain.UiMode
```

Add the card composable next to `AppearanceCard`:

```kotlin
/**
 * Which screens this tablet offers.
 *
 * Deliberately sits with Appearance and the PIN rather than at the top: it is
 * an install-day decision made once by whoever works down this screen, not a
 * thing staff toggle. Nothing is hidden destructively — an Advanced setting
 * made here keeps working after the switch to Simple.
 */
@Composable
private fun UiModeCard(vm: AppViewModel) {
    val mode by vm.uiMode.collectAsStateWithLifecycle()
    SurfaceCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Eyebrow("Screens", Modifier.weight(1f))
            InfoDot(
                "Simple and Advanced",
                "Simple shows the five screens a course needs, with network and " +
                    "amp power folded into this page. Advanced adds sounds, " +
                    "audio out, the full amp page and the timezone pin. " +
                    "Switching hides screens; it never changes a setting.",
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UiMode.entries.forEach { option ->
                ChoiceChip(
                    label = option.label,
                    selected = option == mode,
                    description = "${option.label} screens. ${option.why}",
                    onClick = { vm.setUiMode(option) },
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(mode.why, fontSize = 12.5.sp, color = Nocturne.Neutral500)
    }
}
```

Call it in `SetupScreen`'s column, between `SecurityCard(vm)` and
`BackupCard(...)` (currently `SetupScreen.kt:272-274`):

```kotlin
            SecurityCard(vm)

            UiModeCard(vm)

            BackupCard(vm, zone)
```

- [ ] **Step 8: Stop the clock row pointing at a hidden screen**

In `SetupScreen.kt`, replace the Clock `StateRow` (currently lines 204-209):

```kotlin
                    Spacer(Modifier.height(8.dp))
                    ClockRow(vm, trusted = state.clockTrusted)
```

and add the composable beside `StateRow`:

```kotlin
/**
 * The clock row, carrying its own way out.
 *
 * The old value read "untrusted — see Time", and Simple has no Time screen to
 * see. Confirm was always the whole action anyway: it tells the scheduler the
 * wall clock is right, which is what un-suppresses automatic plays.
 */
@Composable
private fun ClockRow(vm: AppViewModel, trusted: Boolean) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = Nocturne.MIN_TOUCH_DP.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.clearAndSetSemantics {}) {
            Dot(if (trusted) Nocturne.Ok else Nocturne.Warning)
        }
        Text(
            "Clock",
            fontSize = 12.5.sp,
            color = Nocturne.Neutral500,
            modifier = Modifier.width(100.dp),
        )
        Text(
            if (trusted) "trusted" else "untrusted",
            fontSize = 12.5.sp,
            fontFamily = Nocturne.Mono,
            color = if (trusted) Nocturne.Ok else Nocturne.Warning,
        )
        if (!trusted) OutlineButton("Confirm", Nocturne.Warning) { vm.confirmClock() }
    }
}
```

- [ ] **Step 9: Verify**

Run:
```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```
Expected: both BUILD SUCCESSFUL.

- [ ] **Step 10: Commit**

```bash
git add app/src/main/java/org/dhamma/gong/ui/GongApp.kt \
        app/src/main/java/org/dhamma/gong/ui/Controls.kt \
        app/src/main/java/org/dhamma/gong/ui/SetupScreen.kt \
        app/src/test/java/org/dhamma/gong/ui/TabRailTest.kt
git commit -m "Show a five-item rail in Simple mode

Sounds, Audio out, Amp power and Time are technician screens; Simple
hides them and Setup gains the switch. Setup's clock row now carries its
own Confirm instead of pointing at a screen Simple does not show."
```

---

## Task 3: Network moves into Setup

**Files:**
- Create: `app/src/main/java/org/dhamma/gong/ui/NetworkCard.kt`
- Delete: `app/src/main/java/org/dhamma/gong/ui/NetworkScreen.kt`
- Modify: `app/src/main/java/org/dhamma/gong/ui/GongApp.kt` (drop `NETWORK` from `Tab`, drop its `when` branch)
- Modify: `app/src/main/java/org/dhamma/gong/ui/SetupScreen.kt` (render the card)
- Modify: `app/src/main/java/org/dhamma/gong/ui/MainActivity.kt:104-107` (deep-link doc comment)
- Modify: `app/src/test/java/org/dhamma/gong/ui/TabRailTest.kt` (assert it is gone from both rails)

**Interfaces:**
- Consumes: `AppViewModel.network: StateFlow<NetworkFacts.Facts>`,
  `AppViewModel.networkProbed: StateFlow<Boolean>`,
  `AppViewModel.refreshNetwork()` (all existing, `AppViewModel.kt:770-783`);
  `NetworkSettings.openWifi(context)` / `openHotspot(context)`
  (`org.dhamma.gong.net.NetworkSettings`); `InfoDot` (Task 2).
- Produces: `NetworkCard(vm: AppViewModel, advanced: Boolean)` — a full-width
  `SurfaceCard` that polls while STARTED and renders kind / address / internet /
  data plus the two system-settings buttons.

- [ ] **Step 1: Write the failing test**

Add to `app/src/test/java/org/dhamma/gong/ui/TabRailTest.kt`:

```kotlin
    @Test
    fun networkIsNotADestinationInEitherMode() {
        // Spec §6: the facts live on Setup. A rail entry for them was one tap
        // to a screen that only ever reported.
        val names = Tab.entries.map { it.name }
        assertTrue("NETWORK must be gone from Tab entirely", "NETWORK" !in names)
    }
```

- [ ] **Step 2: Run the test and watch it fail**

Run: `./gradlew :app:testDebugUnitTest --tests '*TabRailTest*'`
Expected: FAIL — `networkIsNotADestinationInEitherMode` fails on
"NETWORK must be gone from Tab entirely".

- [ ] **Step 3: Write `NetworkCard`**

Create `app/src/main/java/org/dhamma/gong/ui/NetworkCard.kt`:

```kotlin
package org.dhamma.gong.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import org.dhamma.gong.domain.NetworkFacts
import org.dhamma.gong.net.NetworkSettings

/**
 * The network facts, as a Setup card rather than a destination (spec §6).
 *
 * Nothing on this appliance depends on a connection — it fires gongs and dohas
 * in airplane mode forever, and the only thing a network buys is the on-demand
 * doha download. That is exactly why it stopped being a rail entry: a screen
 * whose whole job is to report "still offline, still fine" does not deserve a
 * tap of its own. Someone on the phone still needs the tablet's address, so the
 * facts survive; the paragraph explaining Android's location rule moved behind
 * the ⓘ, where curiosity can find it and nobody else has to read it.
 *
 * The screen refuses to guess. Android will not name the Wi-Fi network to an
 * app without a location permission this appliance deliberately never requests,
 * and has offered no way to ask "am I tethering" since API 28. Both blanks are
 * labelled as blanks.
 */
@Composable
fun NetworkCard(vm: AppViewModel, advanced: Boolean) {
    val facts by vm.network.collectAsStateWithLifecycle()
    val probed by vm.networkProbed.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // A cable pulled or a hotspot started elsewhere shows up on the next poll.
    // STARTED-bound: nothing polls behind a dark screen.
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                vm.refreshNetwork()
                delay(5_000)
            }
        }
    }

    SurfaceCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Eyebrow("Network", Modifier.weight(1f))
            when {
                !probed -> Tag("CHECKING", Nocturne.Neutral500)
                !facts.online -> Tag("OFFLINE", Nocturne.Neutral500)
                !facts.validated -> Tag("NO INTERNET", Nocturne.Warning)
                else -> Tag(modeLabel(facts.mode).uppercase(), Nocturne.Ok)
            }
            if (probed && facts.metered) Tag("METERED", Nocturne.Warning)
            InfoDot(
                "Network",
                "The gong and doha schedule runs with no network at all — only " +
                    "doha downloads need one. Android names the Wi-Fi network " +
                    "to an app only if it holds a location permission, and this " +
                    "one does not ask for one; open Wi-Fi settings to see the name.",
            )
        }
        Spacer(Modifier.height(12.dp))

        NetRow(
            "Kind",
            if (probed) modeLabel(facts.mode) else "checking",
            when {
                !probed || !facts.online -> Nocturne.Neutral400
                facts.validated -> Nocturne.Ok
                else -> Nocturne.Warning
            },
        )
        Spacer(Modifier.height(8.dp))
        NetRow(
            "Network",
            when {
                facts.ssid != null -> facts.ssid!!
                facts.ssidWithheld -> "name withheld"
                facts.mode == NetworkFacts.Mode.ETHERNET -> "wired"
                else -> "—"
            },
            if (facts.ssidWithheld) Nocturne.Neutral400 else Nocturne.Text,
        )
        Spacer(Modifier.height(8.dp))
        NetRow("Address", facts.ip ?: "—")
        Spacer(Modifier.height(8.dp))
        NetRow(
            "Internet",
            when {
                !facts.online -> "no connection"
                facts.validated -> "reachable"
                else -> "not reachable"
            },
            when {
                !facts.online -> Nocturne.Neutral400
                facts.validated -> Nocturne.Ok
                else -> Nocturne.Warning
            },
        )

        // Advanced keeps the metered line and the full explanation; Simple gets
        // the four facts and the two buttons that change them.
        if (advanced) {
            Spacer(Modifier.height(8.dp))
            NetRow(
                "Data",
                if (facts.metered) "metered" else "unmetered",
                if (facts.metered) Nocturne.Warning else Nocturne.Text,
            )
            if (facts.ssidWithheld) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Android only tells an app the network's name if the app holds " +
                        "a location permission. This one does not ask for one — a " +
                        "location grant to caption an informational card is a bad trade.",
                    fontSize = 12.5.sp,
                    color = Nocturne.Neutral500,
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlineButton("Wi-Fi settings", Nocturne.Neutral300) { NetworkSettings.openWifi(context) }
            OutlineButton("Hotspot settings", Nocturne.Neutral300) { NetworkSettings.openHotspot(context) }
        }
    }
}

private fun modeLabel(mode: NetworkFacts.Mode): String = when (mode) {
    NetworkFacts.Mode.OFFLINE -> "Offline"
    NetworkFacts.Mode.WIFI -> "Wi-Fi"
    NetworkFacts.Mode.ETHERNET -> "Ethernet"
    NetworkFacts.Mode.CELLULAR -> "Mobile data"
    NetworkFacts.Mode.OTHER -> "Other"
}

@Composable
private fun NetRow(label: String, value: String, color: Color = Nocturne.Text) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.clearAndSetSemantics {}) { Spacer(Modifier.width(0.dp)) }
        Text(label, fontSize = 12.5.sp, color = Nocturne.Neutral500, modifier = Modifier.width(84.dp))
        Text(value, fontSize = 12.5.sp, fontFamily = Nocturne.Mono, color = color)
    }
}
```

If `NetworkFacts.Facts.ssid` is a non-null `String?` smart-cast failure appears
on `facts.ssid!!`, that is expected: `facts` is a delegated `val` read from a
`StateFlow`, so the compiler cannot smart-cast it. The `!!` inside the branch
that already tested `!= null` is correct and matches how `NetworkScreen.kt:186-188`
read it.

- [ ] **Step 4: Delete the screen and its tab**

```bash
git rm app/src/main/java/org/dhamma/gong/ui/NetworkScreen.kt
```

In `GongApp.kt`, delete the enum entry (currently line 89):

```kotlin
    NETWORK("Network", "⌁", requiresPin = true),
```

and its content branch (currently line 167):

```kotlin
                        Tab.NETWORK -> NetworkScreen(vm)
```

In `MainActivity.kt`, the doc comment at lines 104-107 lists deep-linkable tabs;
leave the code alone (`Tab.valueOf` already returns null for an unknown name via
`runCatching`) and update the comment:

```kotlin
        /**
         * Optional deep-link for docs/screenshots:
         * `-e tab DASHBOARD|SCHEDULE|COURSES|LOGS|SOUNDS|SETUP`.
         *
         * Case-insensitive: `-e tab courses` used to fail [Tab.valueOf] and fall
         * back to the Dashboard without saying so. A name that is not a tab, or
         * a tab the current UI mode hides, is ignored the same way.
         */
```

- [ ] **Step 5: Render the card on Setup**

In `SetupScreen.kt`, read the mode at the top of `SetupScreen` next to the
other collected state (after `val pinHash by ...`, currently line 69):

```kotlin
    val mode by vm.uiMode.collectAsStateWithLifecycle()
```

and add the card to the column immediately after the permissions/appliance-state
block (currently `SetupScreen.kt:254-265`), before `AppearanceCard(vm)`:

```kotlin
            NetworkCard(vm, advanced = mode == UiMode.ADVANCED)
```

- [ ] **Step 6: Run the tests and watch them pass**

Run: `./gradlew :app:testDebugUnitTest --tests '*TabRailTest*'`
Expected: PASS, 5 tests — including `advancedShowsEveryTab`, which now compares
against a `Tab.entries` with no `NETWORK` in it.

- [ ] **Step 7: Verify**

Run:
```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```
Expected: both BUILD SUCCESSFUL. If `assembleDebug` reports an unresolved
`NetworkScreen`, a `when` branch was missed in `GongApp.kt`.

- [ ] **Step 8: Commit**

```bash
git add -A app/src/main/java/org/dhamma/gong/ui app/src/test/java/org/dhamma/gong/ui
git commit -m "Fold Network into Setup and drop the Network tab

The screen only ever reported, and nothing on the appliance depends on a
connection. The facts and the two system-settings buttons become a Setup
card; the location-permission explanation moves behind an info dialog."
```

---

## Task 4: The Simple Amp power card

**Files:**
- Create: `app/src/main/java/org/dhamma/gong/ui/AmpPowerCard.kt`
- Modify: `app/src/main/java/org/dhamma/gong/ui/Controls.kt` (add shared `CommittingField` and `Toggle`)
- Modify: `app/src/main/java/org/dhamma/gong/ui/RelayScreen.kt:259-285` (delete the local `CommittingField`)
- Modify: `app/src/main/java/org/dhamma/gong/ui/DashboardScreen.kt:355-395` (delete the local `Toggle`)
- Modify: `app/src/main/java/org/dhamma/gong/ui/SetupScreen.kt` (render the card)

**Interfaces:**
- Consumes: `AppViewModel.settings`, `AppViewModel.relayState:
  StateFlow<RelayController.State>`, `AppViewModel.setRelaySetting(key, value,
  announce)`, `AppViewModel.relayTest()`, `AppViewModel.relayManualOn()`,
  `AppViewModel.relayManualOff()`, `AppViewModel.toggle(key)` — all existing
  (`AppViewModel.kt:99-144`); `InfoDot` (Task 2).
- Produces:
  - `AmpPowerSimpleCard(vm: AppViewModel)`.
  - `CommittingField(stored, placeholder, description, keyboardOptions, onCommit)`
    in `Controls.kt` (public; moved verbatim from `RelayScreen.kt`).
  - `Toggle(label: String, checked: Boolean, enabled: Boolean = true, onClick: () -> Unit)`
    in `Controls.kt` (public; moved verbatim from `DashboardScreen.kt`).

There is no unit test in this task: every line is Compose, and the project has
no UI-test source set (see Deviations). Verification is `assembleDebug` plus the
QA rows Task 8 adds.

- [ ] **Step 1: Move `CommittingField` into `Controls.kt`**

Cut the whole `CommittingField` composable and its KDoc from
`RelayScreen.kt:253-285` and paste it into `Controls.kt`, changing `private fun`
to `fun`:

```kotlin
/**
 * A text field that keeps a local buffer and writes it back when focus leaves
 * or the value it was seeded from changes underneath it. Saving on every
 * keystroke would write a partial IP address to the settings row the tick path
 * reads; a save button was ruled out by the design.
 */
@Composable
fun CommittingField(
    stored: String,
    placeholder: String,
    description: String,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    onCommit: (String) -> Unit,
) {
    var typed by remember(stored) { mutableStateOf(stored) }
    var focused by remember { mutableStateOf(false) }
    Field(
        value = typed,
        onValueChange = { typed = it },
        placeholder = placeholder,
        keyboardOptions = keyboardOptions,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = description }
            // `hasFocus`, not `isFocused`: the focus target is the child
            // BasicTextField inside Field, so this node is never itself focused.
            .onFocusChanged { focus ->
                val had = focused
                focused = focus.hasFocus
                if (had && !focus.hasFocus && typed.trim() != stored) onCommit(typed)
            },
    )
}
```

Add to `Controls.kt`'s imports:

```kotlin
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.onFocusChanged
```

`RelayScreen.kt` calls it unqualified from the same package, so its call sites
(`RelayScreen.kt:193`, `:204`) need no edit. Remove now-unused imports from
`RelayScreen.kt` only if the build warns.

- [ ] **Step 2: Move `Toggle` into `Controls.kt`**

Cut the `Toggle` composable and its two-line comment from
`DashboardScreen.kt:354-395` and paste it into `Controls.kt` as `fun Toggle(...)`
(drop `private`), verbatim otherwise. Add to `Controls.kt`'s imports:

```kotlin
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.semantics.clearAndSetSemantics
```

Dashboard's call sites are in the same package and need no edit.

- [ ] **Step 3: Verify the move compiles before adding anything new**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL, with no behaviour change on screen. Commit this
refactor on its own if you prefer a bisectable history:

```bash
git add app/src/main/java/org/dhamma/gong/ui/Controls.kt \
        app/src/main/java/org/dhamma/gong/ui/RelayScreen.kt \
        app/src/main/java/org/dhamma/gong/ui/DashboardScreen.kt
git commit -m "Share CommittingField and Toggle from Controls"
```

- [ ] **Step 4: Write the card**

Create `app/src/main/java/org/dhamma/gong/ui/AmpPowerCard.kt`:

```kotlin
package org.dhamma.gong.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Amp power on install day: where the Shelly is, whether it answers, whether
 * the schedule drives it, and a way to switch the amp by hand (spec §7).
 *
 * Four controls, because four is what a Shelly on the centre LAN needs. Switch
 * id, lead and lag, and the device password stay on Advanced → Amp power; a
 * centre that set them there keeps them, because this card writes the same
 * settings rows [org.dhamma.gong.relay.RelayController] already reads and
 * touches nothing else.
 *
 * Two honesty rules carry over from the full screen and are not negotiable
 * here either:
 *
 * 1. **Reachability has three states.** `RelayController.State.reachable` is a
 *    `Boolean?` and `null` means *never probed* — painted neutral, never green,
 *    and never the red that would accuse a working Shelly of being down.
 * 2. **`lastError` is shown verbatim.** A silent failure on a mains relay is
 *    unacceptable; a stack trace on a wall tablet is useless. One line, as the
 *    controller reported it.
 *
 * The relay password is never read into this file at all.
 */
@Composable
fun AmpPowerSimpleCard(vm: AppViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val relay by vm.relayState.collectAsStateWithLifecycle()

    val host = settings["relay_host"].orEmpty()
    val configured = host.isNotBlank()
    val auto = settings["relay_enabled"] == "1"

    SurfaceCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Eyebrow("Amp power", Modifier.weight(1f))
            when {
                !configured -> Tag("NO ADDRESS", Nocturne.Neutral500)
                relay.reachable == null -> Tag("NOT PROBED", Nocturne.Neutral500)
                relay.reachable == true -> Tag("OK", Nocturne.Ok)
                else -> Tag("UNREACHABLE", Nocturne.Error)
            }
            InfoDot(
                "Amp power",
                "A Shelly relay on the centre network switches the amplifier on " +
                    "just before a gong and off just after. The gong rings on " +
                    "time whether or not the relay answers. Every manual ON also " +
                    "arms the Shelly's own auto-off timer, so a dead tablet " +
                    "cannot leave the amp energised overnight.",
            )
        }
        Spacer(Modifier.height(12.dp))

        // Auto with schedule — `relay_enabled`. Dead until there is an address,
        // because a relay with no host cannot switch anything.
        Row(
            Modifier.fillMaxWidth().heightIn(min = Nocturne.MIN_TOUCH_DP.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Auto with schedule",
                fontSize = 13.5.sp,
                color = Nocturne.Text,
                modifier = Modifier.weight(1f),
            )
            if (configured) {
                Toggle("", auto) { vm.toggle("relay_enabled") }
            } else {
                Box(Modifier.alpha(0.5f)) { Toggle("", false, enabled = false) {} }
            }
        }

        Spacer(Modifier.height(12.dp))
        Hairline()
        Spacer(Modifier.height(12.dp))

        Eyebrow("Host or IP")
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.weight(1f)) {
                CommittingField(
                    stored = host,
                    placeholder = "192.168.1.50",
                    description = "Relay host or IP address",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                ) { vm.setRelaySetting("relay_host", it.trim(), "Relay host saved") }
            }
            // Blank host is not an error state to hide the button for — staff
            // tap Test to find out what is wrong, so say what is wrong.
            OutlineButton("Test", Nocturne.Neutral300) {
                if (configured) vm.relayTest() else vm.toast("Enter the Shelly's IP first.")
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Saved as you leave the field. Give the Shelly a fixed address on the " +
                "centre router so this stays true.",
            fontSize = 12.5.sp,
            color = Nocturne.Neutral500,
        )

        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlineButton("Amp on", Nocturne.Neutral300) {
                if (configured) vm.relayManualOn() else vm.toast("Enter the Shelly's IP first.")
            }
            OutlineButton("Amp off", Nocturne.Neutral300) {
                if (configured) vm.relayManualOff() else vm.toast("Enter the Shelly's IP first.")
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            when {
                !configured -> "No address yet. Until one is set the relay does nothing."
                relay.reachable == null -> "Nothing has been sent to the Shelly yet. Tap Test."
                relay.reachable == true && relay.armed -> "Reachable. Amp believed on."
                relay.reachable == true -> "Reachable. Amp believed off."
                else -> "The last call did not reach the Shelly. The schedule is unaffected."
            },
            fontSize = 12.5.sp,
            color = if (relay.reachable == false) Nocturne.Warning else Nocturne.Neutral500,
        )

        if (relay.lastError.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                relay.lastError,
                fontSize = 12.5.sp,
                fontFamily = Nocturne.Mono,
                color = Nocturne.Error,
            )
        }
    }
}
```

`vm.toast(...)` is the existing public helper used by `CoursesScreen.kt:92`; if
it is not public on `AppViewModel`, promote it rather than inventing a new path.

- [ ] **Step 5: Render it on Setup**

In `SetupScreen.kt`, add the card directly after `NetworkCard(...)` from Task 3:

```kotlin
            NetworkCard(vm, advanced = mode == UiMode.ADVANCED)

            // Simple's only amp surface. Advanced keeps the full screen and
            // shows this card too — the four controls are the ones both need.
            AmpPowerSimpleCard(vm)
```

- [ ] **Step 6: Verify**

Run:
```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```
Expected: both BUILD SUCCESSFUL.

- [ ] **Step 7: Manual check on a device or emulator**

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n org.dhamma.gong/.ui.MainActivity --es tab SETUP
```
Confirm, with no Shelly on the network: status reads `NO ADDRESS`, Test toasts
"Enter the Shelly's IP first.", the auto toggle is dimmed and inert. Type an
unreachable IP, tap elsewhere to commit, tap Test: status becomes `UNREACHABLE`
and one error line appears — no stack trace.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/org/dhamma/gong/ui/AmpPowerCard.kt \
        app/src/main/java/org/dhamma/gong/ui/SetupScreen.kt
git commit -m "Add a Simple amp power card to Setup

IP, Test, Amp on/off and auto-with-schedule — what a Shelly on the centre
LAN needs. Switch id, lead/lag and the device password stay on the full
Amp power screen; this card writes the same settings rows."
```

---

## Task 5: Dashboard in Simple

**Files:**
- Modify: `app/src/main/java/org/dhamma/gong/ui/DashboardScreen.kt:110-125` (clock banner), `:316-329` (toggle row)

**Interfaces:**
- Consumes: `AppViewModel.uiMode` (Task 1); `Toggle` from `Controls.kt` (Task 4).
- Produces: nothing new.

- [ ] **Step 1: Hide the Relay toggle in Simple**

In `DashboardScreen.kt`, read the mode where the composable already collects
its state, and add the import:

```kotlin
import org.dhamma.gong.domain.UiMode
```

Then replace the relay branch of the toggle row (currently lines 320-328):

```kotlin
            // Live once a Shelly address is set; dimmed and inert until then,
            // because a relay with no host cannot switch anything (relay design,
            // "Error handling": host unset → relay logic inert).
            //
            // Simple drops it entirely: Setup's amp card is the one place amp
            // configuration lives there, and two switches for one relay on two
            // screens is exactly the density this mode exists to remove.
            if (mode == UiMode.ADVANCED) {
                val relayConfigured = settings["relay_host"].orEmpty().isNotBlank()
                if (relayConfigured) {
                    Toggle("Relay", settings["relay_enabled"] == "1") { vm.toggle("relay_enabled") }
                } else {
                    Box(Modifier.alpha(0.5f)) { Toggle("Relay", false, enabled = false) {} }
                }
            }
```

The `mode` value comes from a `val mode by vm.uiMode.collectAsStateWithLifecycle()`
added to whichever composable owns that `Row` — pass it down as a parameter if
the row lives in a private child composable rather than in `DashboardScreen`
itself.

- [ ] **Step 2: Stop the clock banner naming a hidden screen**

At `DashboardScreen.kt:119`, the untrusted-clock banner reads "Check the time on
the Time screen, then confirm the clock." Time is hidden in Simple. Replace the
string with one that works in both modes:

```kotlin
                    "Automatic plays are suppressed until someone confirms the " +
                        "wall clock is right.",
```

Leave `actionLabel = "Confirm clock"` and `onAction = { vm.confirmClock() }`
(lines 121-122) untouched — that is the action, and it is on the banner already.

- [ ] **Step 3: Verify**

Run:
```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```
Expected: both BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/org/dhamma/gong/ui/DashboardScreen.kt
git commit -m "Drop the Dashboard relay toggle in Simple mode

Setup's amp card is the single place amp configuration lives in Simple.
The untrusted-clock banner no longer sends staff to a screen Simple hides."
```

---

## Task 6: Copy diet

Spec §4.2 and §5: no multi-sentence paragraph under a green row, one subtitle
per screen at most, and no explicit Save where the rest of the app commits on
focus loss.

**Files:**
- Modify: `app/src/main/java/org/dhamma/gong/ui/SetupScreen.kt:102-131` (title + hint), `:336-385` (`CheckRow`), `:244-251` (trailing paragraph)
- Modify: `app/src/main/java/org/dhamma/gong/ui/DohaMediaScreen.kt:230-260` (doha time)

**Interfaces:**
- Consumes: `InfoDot` (Task 2), `CommittingField` (Task 4),
  `ScheduleMaterializer.parseHhMm(String): LocalTime?` (existing, used at
  `DohaMediaScreen.kt:253`).
- Produces: nothing new.

- [ ] **Step 1: Move the permission "why" text behind ⓘ**

Rewrite `CheckRow` (`SetupScreen.kt:336-385`) so the explanation is a dialog,
not a paragraph under every row:

```kotlin
/**
 * One checklist item. The whole row is the tap target when the grant is
 * missing (>= 44 dp), with the button as the visible affordance — both run
 * the same handler, so a tap anywhere does the right thing.
 *
 * The "why" moved behind the ⓘ. Three green rows with three paragraphs under
 * them is three paragraphs nobody reads on the day they matter; the reason
 * this grant exists is still one tap away on the day it does.
 */
@Composable
private fun CheckRow(
    label: String,
    granted: Boolean,
    grantedText: String,
    deniedText: String,
    why: String,
    action: String,
    onAction: () -> Unit,
) {
    val dot = if (granted) Nocturne.Ok else Nocturne.Warning
    val rowMod = if (granted) {
        Modifier.fillMaxWidth()
    } else {
        Modifier
            .fillMaxWidth()
            .heightIn(min = Nocturne.MIN_TOUCH_DP.dp)
            .clip(RoundedCornerShape(8.dp))
            .semantics { contentDescription = "$label: $deniedText. $action" }
            .clickable(role = Role.Button, onClick = onAction)
    }
    Column(rowMod) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.clearAndSetSemantics {}) { Dot(dot) }
            Text(
                label,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = Nocturne.Text,
                modifier = Modifier.weight(1f),
            )
            Text(
                if (granted) grantedText else deniedText,
                fontSize = 12.5.sp,
                fontFamily = Nocturne.Mono,
                color = dot,
            )
            InfoDot(label, why)
        }
        if (!granted) {
            Spacer(Modifier.height(10.dp))
            PrimaryButton(action, onClick = onAction)
        }
    }
}
```

The three `why` strings at the call sites (`SetupScreen.kt:143-145`, `:157-158`,
`:171-173`) are unchanged — they are now dialog bodies rather than row subtitles.

- [ ] **Step 2: Cut the screen subtitle to one line**

Replace `SetupScreen.kt:102-106`:

```kotlin
            ScreenTitle(
                "Setup",
                "Amber rows are not fatal — they are unreliable.",
            )
```

and delete the conditional hint at lines 125-131 ("Tap an amber row below to
open the matching system page.") — the readiness banner above it already names
what is missing, and the amber rows are already buttons.

- [ ] **Step 3: Cut the appliance-state trailing paragraph**

Delete `SetupScreen.kt:244-251` (the "The scheduler lives in the service…"
Text and its preceding `Spacer`) and hang the same fact off the card's eyebrow
instead. Change the eyebrow row at `SetupScreen.kt:181-182` to:

```kotlin
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Eyebrow("Appliance state", Modifier.weight(1f))
                        InfoDot(
                            "Appliance state",
                            "The scheduler lives in the foreground service, not in " +
                                "this screen. Closing the app leaves it running. " +
                                "Last tick is the 30 s heartbeat — anything much " +
                                "older means the service was frozen.",
                        )
                    }
```

- [ ] **Step 4: Make doha time save like every other field**

In `DohaMediaScreen.kt`, replace the time `Row` (lines 230-260) with:

```kotlin
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "Time",
                fontSize = 12.5.sp,
                color = Nocturne.Neutral500,
                modifier = Modifier.width(96.dp),
            )
            Box(Modifier.width(120.dp)) {
                // Commits on focus loss like the relay host and every other
                // field in the app. Validation still runs through the
                // scheduler's own parser, so the screen can never store a
                // string the materializer would silently fall back on.
                CommittingField(
                    stored = stored,
                    placeholder = "06:37",
                    description = "Doha time, 24 hour HH:MM",
                ) { typed ->
                    val trimmed = typed.trim()
                    if (ScheduleMaterializer.parseHhMm(trimmed) == null) {
                        vm.toast("Doha time must be 24-hour HH:MM, e.g. 06:37")
                    } else {
                        vm.setSetting("doha_time", trimmed, "Doha time set to $trimmed")
                    }
                }
            }
        }
```

Rejecting a bad value leaves the field showing what was typed while the stored
value is unchanged — the toast is the feedback, and moving focus away again
re-offers the commit. That matches the relay host's behaviour.

Remove imports that go unused after the edit (`mutableStateOf`, `remember`,
`getValue`, `setValue` may still be used elsewhere in the file — only remove
what the compiler flags).

- [ ] **Step 5: Verify**

Run:
```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```
Expected: both BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/org/dhamma/gong/ui/SetupScreen.kt \
        app/src/main/java/org/dhamma/gong/ui/DohaMediaScreen.kt
git commit -m "Put Setup's explanations behind info dialogs

Three green rows no longer carry three paragraphs. Doha time commits on
focus loss like every other field, so the lone Save button is gone."
```

---

## Task 7: Screenshot the result

Not decoration: `docs/screenshots/` is how this design gets reviewed without a
tablet, and Task 8's checklist points at it.

**Files:**
- Create: `docs/screenshots/design-review-20260812/` PNGs (the directory already exists untracked — confirm what is in it before adding)
- Modify: `docs/screenshots/README.md:20-30` (deep-link list), `:105-115` (Network section), `:140-155` (file table)

- [ ] **Step 1: Install the build and capture Simple**

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n org.dhamma.gong/.ui.MainActivity --es tab DASHBOARD
adb exec-out screencap -p > docs/screenshots/design-review-20260812/simple-01-dashboard.png
adb shell am start -n org.dhamma.gong/.ui.MainActivity --es tab SETUP
adb exec-out screencap -p > docs/screenshots/design-review-20260812/simple-02-setup.png
```

Scroll Setup and capture the lower half (Network card, Amp card, Appearance,
PIN, Screens) as `simple-03-setup-scrolled.png`.

- [ ] **Step 2: Switch to Advanced and capture the rail**

Tap **Setup → Screens → Advanced**, then:

```bash
adb exec-out screencap -p > docs/screenshots/design-review-20260812/advanced-01-rail.png
adb shell am start -n org.dhamma.gong/.ui.MainActivity --es tab POWER
adb exec-out screencap -p > docs/screenshots/design-review-20260812/advanced-02-amp-power.png
```

- [ ] **Step 3: Update the screenshots README**

- Line 27's tab list: remove `NETWORK`.
- The `### Network` section (lines ~107-111): replace the screen shot with a
  note that Network is now a Setup card, pointing at `simple-03-setup-scrolled.png`.
- The file table (~line 148): drop the `09-network.png` row, add the five new
  files above.

- [ ] **Step 4: Commit**

```bash
git add docs/screenshots
git commit -m "Add Simple/Advanced screenshots and retire the Network shot"
```

---

## Task 8: Version, checklist, PROGRESS

The commit a tester installs. Nothing else in this plan bumps the version — see
Deviations.

**Files:**
- Modify: `app/build.gradle.kts:62-63`
- Modify: `docs/BETA-QA-CHECKLIST.md:1-10` and body
- Modify: `PROGRESS.md`

- [ ] **Step 1: Bump the APK identity**

In `app/build.gradle.kts:62-63`:

```kotlin
        versionCode = 13
        versionName = "0.2.0-beta12"
```

- [ ] **Step 2: Re-point the checklist header**

In `docs/BETA-QA-CHECKLIST.md`, update the title, the `versionCode` line and
the Build assertion (lines 1-8) to `0.2.0-beta12` / `versionCode` 13. The
existing header wrongly asserts `0.2.0-beta10 (11)` against `versionCode` 12 —
fix both halves so they agree.

- [ ] **Step 3: Add the QA rows this feature needs**

Append a section to `docs/BETA-QA-CHECKLIST.md`:

```markdown
## Simple / Advanced

- [ ] Fresh install (or `adb shell pm clear org.dhamma.gong`) opens on a rail of
      exactly five: Dashboard, Schedule, Courses, Logs, Setup
- [ ] No Network tab, no Amp power tab, no Sounds, Audio out or Time
- [ ] Setup shows a **Network** card with kind, address and internet, plus
      Wi-Fi settings and Hotspot settings buttons
- [ ] Setup shows an **Amp power** card: host/IP, Test, Amp on, Amp off, auto
      with schedule
- [ ] Amp card with no address: status reads NO ADDRESS, Test says "Enter the
      Shelly's IP first.", auto toggle is dimmed
- [ ] Amp card with a wrong IP, after Test: status reads UNREACHABLE and one
      error line — no stack trace
- [ ] **Gong still fires with the Shelly unplugged.** Set a schedule row a
      minute out with a bad relay IP and auto on; the gong must ring on time
- [ ] Setup → Screens → Advanced restores the full rail including Amp power
- [ ] A relay host typed in Simple is still there on the Advanced Amp power
      screen, and vice versa
- [ ] Switch to Advanced, open Amp power, switch back to Simple from Setup —
      the app lands on the Dashboard, not a blank pane
- [ ] Mode survives a force-stop and relaunch
- [ ] The ⓘ badges on Setup (permissions, network, amp, screens, appliance
      state) each open a dialog and close again
- [ ] Sounds → Morning doha: the time saves when you tap away, with no Save
      button; a bad value like `25:00` toasts and does not save
- [ ] Untrusted clock: Dashboard banner and Setup clock row both offer Confirm,
      and neither mentions a Time screen while in Simple
- [ ] Setup → Appliance state → Build reads `0.2.0-beta12 (13)`
```

- [ ] **Step 4: Note the milestone in PROGRESS.md**

Add an entry naming `0.2.0-beta12`, what landed (Simple/Advanced rail, Network
and Amp folded into Setup, copy diet) and what was deliberately not done (the
Advanced username field, the Simple Dashboard amp chip, a full copy diet across
Sounds/Audio out — spec §9 follow-up).

- [ ] **Step 5: Confirm §9's empty states really are short**

Run:
```bash
grep -n "Nothing logged yet\|No courses yet" \
  app/src/main/java/org/dhamma/gong/ui/LogsScreen.kt \
  app/src/main/java/org/dhamma/gong/ui/CoursesScreen.kt
```
Expected: both present and one sentence each. If either has grown into a
paragraph, move the surplus behind an `InfoDot` in this commit.

- [ ] **Step 6: Final verify**

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```
Expected: both BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add app/build.gradle.kts docs/BETA-QA-CHECKLIST.md PROGRESS.md
git commit -m "Ship Simple/Advanced as 0.2.0-beta12

Bumps versionCode to 13 so a tester installing over the top gets the new
code, and adds the QA rows for the five-item rail, the Setup amp card and
the gong-fires-without-the-Shelly check."
```

---

## Acceptance criteria (from spec §11)

| Criterion | Where it is met | How it is checked |
|-----------|-----------------|-------------------|
| Fresh install defaults to Simple: 5 items, no Network, no Amp full page, no Sounds/Audio/Time | Tasks 1-3 | `UiModeTest`, `TabRailTest`, QA row 1-2 |
| Setup shows Network facts + Wi-Fi/hotspot buttons | Task 3 | QA row 3 |
| Setup shows Amp card: IP, Test, ON/OFF, auto switch, honest status | Task 4 | QA rows 4-6 |
| Switching to Advanced restores the full rail including Amp power | Task 2 | `TabRailTest.advancedShowsEveryTab`, QA row 8 |
| Existing `relay_host` continues to drive `RelayController` in Simple | Tasks 1, 4 (no key renamed, no row cleared) | QA row 9 |
| Unit tests green; no PIN/password logged | All tasks | `./gradlew :app:testDebugUnitTest`; `relay_auth_pass` appears in no file this plan creates |
| Gong still plays if the Shelly is unreachable | Untouched (`RelayPlan`, `SchedulerCore` not modified) | QA row 7 — the one row that must not be skipped |

---

## Self-review

**Spec coverage.** §2 Amp placement → Tasks 4, 8. §3.1 mode storage → Task 1.
§3.2 Simple rail → Task 2. §3.3 Advanced rail + Network removed → Tasks 2, 3.
§3.4 Advanced settings keep working → guaranteed by not touching any settings
row; QA row 9. §4.1 Setup order → Tasks 2, 3, 4 (with the wide-layout deviation
noted). §4.2 copy rules → Task 6. §5 progressive disclosure → `InfoDot`
(Task 2), reused in 3, 4, 6; copy diet → Task 6. §6 Network → Task 3. §7 Amp
Simple card, including the blank-IP and fail-line rules → Task 4. §7.5 Dashboard
relay toggle → Task 5. §8 Advanced `RelayScreen` unchanged → nothing edits it
beyond lifting `CommittingField` out. §9 Sounds/Audio hidden → Task 2;
Courses/Schedule/Logs empty states → verified already short (Task 8 step 5).
§10 implementation sketch → Tasks 1-8 in the same order. §11 → table above.
§12 open points → closed in "Locked decisions".

**Interface consistency.** `UiMode.SETTING_KEY`/`DEFAULT`/`parse` (Task 1) are
used verbatim in Tasks 1-3. `Tab.railFor(mode)` (Task 2) is consumed in Task 2's
`GongApp` and asserted in Tasks 2-3. `InfoDot(title, body)` (Task 2) is called
with exactly two positional strings in Tasks 3, 4, 6. `CommittingField` (Task 4,
moved) keeps the parameter names `stored`/`placeholder`/`description`/
`keyboardOptions` and a trailing `onCommit` lambda in both its Task 4 and Task 6
call sites. `Toggle(label, checked, enabled, onClick)` (Task 4, moved) matches
its Dashboard call sites. `NetworkCard(vm, advanced)` and `AmpPowerSimpleCard(vm)`
are called exactly as declared.

**Known sharp edges for the implementer.** (1) `SetupScreen` grows two cards;
if it passes ~600 lines, split `NetworkCard`/`AmpPowerCard` usage stays where it
is but consider moving `BackupCard` to its own file in a follow-up — not in this
plan. (2) Deleting `Tab.NETWORK` makes `advancedShowsEveryTab` pass trivially
against the new `entries`; that is intended — the explicit "NETWORK is gone"
test is what pins the deletion. (3) `Eyebrow` takes a `Modifier` second
parameter (`GongApp.kt:337`) — the `Modifier.weight(1f)` calls in Tasks 2, 3, 4
depend on it.
