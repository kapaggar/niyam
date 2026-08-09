# Beta screen audit matrix — 0.2.0-beta1

Companion to `2026-08-09-beta-screen-review-assignment.md`.

**STATUS: INCOMPLETE — Wave 2 interrupted by API session limit (resets 05:50 PT).**
Resume instructions in the last section. Branch `beta/screen-review`, checkpoint commit `a5b0526`.

## Baseline & current state

| Item | Baseline (Wave 0) | Now (`a5b0526`) |
|------|-------------------|-----------------|
| Branch | `beta/screen-review` off `main` @ `7f453dd` | same |
| `:app:testDebugUnitTest` | 130 tests, 0 failures | **130 tests, 0 failures** |
| `:app:compileDebugKotlin` | OK | **OK** |
| versionName | `0.1.0-mvp` / code 1 | unchanged — **bump to `0.2.0-beta1` / 2 still TODO** |

Wave 1 (six parallel audits, A–F) completed in full. Wave 2 had 7 agents; **3 landed, 4 never started.**

---

## Matrix (Wave 1 results)

| Screen | Agent | Handoff match | Adaptive 1280×800 | State/VM issues | A11y | Top fixes | Sev |
|--------|-------|---------------|-------------------|-----------------|------|-----------|-----|
| Dashboard | A | partial | fails below ~890 dp; Stop clipped below ~676 dp | `collectAsState` not lifecycle-aware; 1 s tick runs screen-off | health rows ~19 dp, toggles ~20 dp, no contentDescription | adaptive aside; 44 dp health rows & toggles; single "next" accent; test-gong re-entry guard | P1 |
| Courses | B | partial | breaks below ~760 dp — Add button clipped and unreachable | form drafts on `remember`; `courseRows.today` stale across midnight | no contentDescription; delete 30 dp; Field/Button/Picker 38 dp | narrow-landscape clipping; no empty state; date toast lies; sub-44 dp targets | P1 |
| Schedule | C | partial | renders, no crash; time gutter scrolls away past 15 cols | `selected`/`typeId` not saveable; `addEvent` REPLACE churns row id | no labels on cells/chips/steppers; 32–40 dp targets | **DEF inheritance invisible + one-tap destroys a day**; freeze gutter; 44 dp chips; clash guard | **P0** |
| Logs | D | partial | DETAIL degrades &lt;~1060 dp; columns collapse &lt;~860 dp | filter in `remember`, lost on tab switch; subtitle count unfiltered | chips 32 dp, no selected-state semantics | 44 dp chips + semantics; empty-state copy lies under filter; h-scroll + ellipsis | P1 |
| PIN / Security | E | partial | fails &lt;520 dp height: OK key clipped, no scroll | tri-state `pinHash` correct (no flash); **`unlocked` never reset** | no contentDescription on any key or the dot indicator | **relock on background**; mask entry; scrollable keypad; confirm Remove PIN | P1 |
| Shell / Theme | F | partial | fixed 186 dp rail, no scroll, clips &lt;~500 dp height; zero insets at targetSdk 35 | `requiresPin` dead; `initialTab` silently falls back | nav description is a test hook, not a label | **`EXTRA_TAB` dead on warm start**; rail scroll; light platform theme; locked-tab copy | P1 |

---

## Lead triage rulings (binding on resume)

1. **Text size:** the handoff's 11 sp eyebrows / 11 sp mono dates / 10.5 sp tags are **ratified and exempt** from the "≥ 12 sp" constraint. `MIN_TEXT_SP` governs reading/status text only. Do not resize them.
2. **Shared widgets** `Field` / `PrimaryButton` / `TypePicker` live in `CoursesScreen.kt` but are consumed by `PinScreens.kt` and `ScheduleScreen.kt`. **B2 owns them in place**, signatures must stay backward-compatible. Do not move them to a new file mid-wave.
3. **`GongApp.kt` + `MainActivity.kt` are F2's exclusively.** E2 hands F2 the relock observer rather than editing.
4. **`AppViewModel.kt` is E2's** (PIN block only). Other agents' VM asks were deliberately deferred to keep one owner.
5. **Agent G writes only the two new screen files.** The Lead does the 6-line `GongApp` wiring in Wave 4, so a G failure cannot break the build.
6. **Deferred, do not implement in this beta:** `skipped_clock` logging (needs a de-dup policy + failing domain test — that is schedule semantics), OVERLAP "use this" pin affordance, `courseRows` midnight ticker, Logs filter survival across tab switch, per-tab `requiresPin` gating, Inter font, `MaterialTheme.typography` migration, `FLAG_SECURE`.

---

## Wave 2 status

### Landed (commit `a5b0526`, compiles, 130 tests green)

- **A2 Dashboard** — adaptive stacking below 900 dp (Stop no longer clipped), 44 dp health rows + toggles, test-gong re-entry guard, `collectAsStateWithLifecycle` + `repeatOnLifecycle` tick. *Agent died late; the bell-coroutine leak and some `contentDescription`s may be unfinished — re-check `DashboardScreen.kt` against Agent A's P3 list.*
- **C2 Schedule** — **P0 fixed**: inherited DEF rows ghost-render at alpha 0.38 and the first override is a two-tap with an explicit warning. Frozen wall-clock gutter outside the horizontal scroll. `gapSeconds = null` / `track = null` preserved (verified at `ScheduleScreen.kt:206-207`, `:468`).
- **E2 PIN** — masked entry (`PasswordVisualTransformation` + `NumberPassword`), lock and security screens now scroll, explanatory comment on why buffers stay `remember` and not `rememberSaveable`.

### NOT started — no edits made

- **B2 Courses** — `CoursesScreen.kt` untouched. All four P1s open.
- **D2 Logs** — `LogsScreen.kt` untouched. All P1/P2s open.
- **F2 Shell/Theme** — `GongApp.kt`, `MainActivity.kt`, `Theme.kt`, `themes.xml` untouched. All four P1s open.
- **G Time/Setup** — neither file created.

### Incomplete across agents

- **PIN relock is only half-done.** `PinScreens.kt` landed, but `AppViewModel.onBackgrounded()` / `onForegrounded()` were never added (grep confirms 0 matches) and the `GongApp` lifecycle observer was never wired. **The unlocked-session bypass is still open.**

---

## Known blocker for Wave 3 (screenshots)

`MainActivity` reads `EXTRA_TAB` only in `onCreate`, and the manifest sets `launchMode="singleTask"`; `onNewIntent` is not overridden. **The capture recipe at assignment lines 368-386 would silently produce six identical Dashboard screenshots.**

Until F2's fix lands, every capture command must force-stop first:

```bash
adb shell am start -S -n org.dhamma.gong/.ui.MainActivity -e tab COURSES
```

`EXTRA_TAB` key is `tab`; values are the exact enum names, **case-sensitive**: `DASHBOARD`, `SCHEDULE`, `COURSES`, `LOGS`, **`SECURITY`** (the PIN tab — note the label is "PIN"), `AUDIO_OUT`. `SOUNDS`/`AUDIO_OUT`/`TIME`/`NETWORK`/`SETUP` are `enabled = false` and silently fall back to `DASHBOARD`.

Also: **clear any PIN before the capture run**, or every deep link lands on the lock screen. Use `user_rotation 1` to match the existing gallery. Always `wm size reset` / `wm density reset` afterwards.

---

## Resume plan (after the limit resets)

1. Re-run Wave 2 for the four agents that never started: **B2 Courses, D2 Logs, F2 Shell/Theme, G Time+Setup**. Their full approved fix lists are in the Wave 1 findings and the rulings above.
2. Finish the **PIN relock**: add `onBackgrounded()` / `onForegrounded(graceMs = 60_000)` to `AppViewModel`, and the `ON_STOP`/`ON_START` `DisposableEffect` observer in `GongApp`. Use a grace window, not a bare `ON_STOP` relock — the health rows launch system settings and would otherwise re-prompt mid-grant.
3. Re-check `DashboardScreen.kt` for A2's unfinished P3 items.
4. Wave 3 screenshots at 1280×800 density 160 (see blocker above).
5. Wave 4: wire `Tab.TIME` / `Tab.SETUP` (`GongApp.kt:65`, `:67` — drop `enabled = false`, add two `when` arms), bump `versionName = "0.2.0-beta1"` / `versionCode = 2`, full verify, write `docs/BETA-QA-CHECKLIST.md`, update `PROGRESS.md`.
