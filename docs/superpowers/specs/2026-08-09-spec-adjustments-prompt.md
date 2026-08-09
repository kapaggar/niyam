# Prompt for Claude — amend design specs (major concerns only)

Copy everything below the line into a Claude session that has the `niyam` repo open.

---

## Task

Amend these two **approved but not-yet-implemented** design specs so the **high-severity** review findings are written into the documents. Do **not** implement code. Do **not** expand scope. Do **not** rewrite the specs from scratch.

**Files to edit:**

1. `docs/superpowers/specs/2026-08-09-doha-media-folder-design.md`
2. `docs/superpowers/specs/2026-08-09-shelly-relay-design.md`

**After edits:** leave `Status` as approved design (or add a one-line “Amended 2026-08-09 — major review fixes”), keep both ready for implementation planning.

## Rules for the edit

- Surgical: add short subsections / table rows / acceptance bullets. Prefer 5–20 line insertions over large rewrites.
- Match existing voice (concise, centre-appliance, no fluff).
- Ground wording in real code already in the repo (`MediaSlotDao`, `MediaResolver`, `SchedulerEngine.HEARTBEAT` 30 s, `Tab.SOUNDS` currently `enabled = false`, Dashboard inert Relay toggle, no `INTERNET` in the manifest today).
- Only address the **major** items listed below. Skip medium/low review notes unless a one-line note is unavoidable for a major item to make sense.

---

## Spec A — Doha media folder

### Major 1 — Pack folder shape vs scan depth

**Problem:** Spec says scan **immediate children** only. Real packs live under a `doha/` folder (`MEDIA.md`). If staff pick the parent directory, the scan finds zero files and looks broken.

**Required spec change:**

- State explicitly that staff must pick the folder that **directly contains** the `D01`…`D11` audio files (not an ancestor).
- Optional, still simple: if the picked tree’s immediate children include a single subdirectory named `doha` (case-insensitive) and no matching audio at the top level, scan **that** child’s immediate children once. Do **not** recurse further.
- List allowed extensions for v1: at least `.mp3` (case-insensitive). If you mention others, keep the list finite and explicit.

Add an acceptance bullet: picking the wrong parent (no matches) shows the empty-state that names the `D01…D11` convention, not a silent no-op.

### Major 2 — (Treat as major for field ops) Permission lifecycle on re-pick

**Problem:** Spec takes persistable read permission but never releases the previous tree grant or handles take-permission failure.

**Required spec change:**

- When staff pick a **new** folder successfully: `releasePersistableUriPermission` on the **previous** `doha_tree_uri` (if any), then take permission on the new URI, then persist the new URI.
- If `takePersistableUriPermission` throws: show a banner, **do not** replace `doha_tree_uri` or wipe existing `media_slots` rows.
- Keep existing “permission lost later → banner + slots marked unverified” behaviour.

Add acceptance: re-pick releases the old grant and remaps from the new folder without leaving a stale tree URI.

### Major 3 — Do not clobber `manual` / `bundled` on auto-map

**Problem:** Rescan does `deleteBySource(AUTO)` then `putAll(auto)`. Upsert by slot can overwrite a `bundled` (debug) or, if logic is wrong, threaten manual rows. Spec already protects manual on rescan; harden it.

**Required spec change:**

- Auto-map may **only** write slots that are empty or currently `source = auto`.
- Never overwrite `source = manual` or `source = bundled` via auto-map or rescan.
- Manual clear of a slot remains an explicit staff action.

Add a mapper/unit-test bullet for “bundled/manual slot survives rescan when a file also claims that slot.”

### UI label (one-line, blocking confusion)

**Problem:** “day it serves” is wrong — slots are not calendar days (`DohaSlots.legacyModular` is course-day → slot).

**Required:** In the slot table layout, rename to **Slot 01…11** (or equivalent). Do **not** claim a fixed calendar day per slot. Optional subtitle is fine; no day column that implies Day N = Slot N.

---

## Spec B — Shelly relay

### Major 1 — Lag-off must not fight the next event

**Problem:** Play end → `offAfterLag()` can power the amp off while the next `nextDeadline` is still inside the pre-arm window (e.g. gong then doha a minute later). Cold amp / cut lead-in.

**Required spec change:**

- Add a non-negotiable (or elevate under Trigger / Architecture):  
  **Never issue OFF if `nextDeadline` is within `heartbeat + lead` (same window used for pre-arm ON), or if a play is still in progress.**
- `RelayPlan` inputs must include at least: `now`, `nextDeadline`, `playing` (or equivalent), `relay_enabled`, lead/lag/heartbeat constants, and enough to compute desired on/off.
- Unit tests: back-to-back occurrences — after first play ends, plan stays ON (or re-arms ON) until the second play’s lag window completes; no spurious OFF between them.

### Major 2 — Missed occurrence must explicitly OFF while process is alive

**Problem:** Error table and tests claim “missed → explicit off,” but architecture only wires tick→ON and play-end→OFF. Misses never hit `PlayerEngine`, so amp can stay on until `toggle_after` only.

**Required spec change:**

- Document sticky arm state, e.g. controller remembers `armedForDeadline` (or pure plan derives from last desired-on deadline).
- On each tick: if we had armed for deadline *D*, *D* is no longer the live `nextDeadline` (fired, missed, or superseded), and we are not playing → **Desired.off** (explicit OFF), with `toggle_after` still the crash backstop.
- Architecture diagram / bullet list must show:  
  `SchedulerEngine` tick → `RelayController` (ON pre-arm **and** OFF on miss/cancel), not only play-end OFF.
- Keep unit test: “missed occurrence does not leave desired-on when process is alive.”

### Major 3 — Android network prerequisites (blocking)

**Problem:** App manifest has no `INTERNET`. Shelly Gen2+ is HTTP on the LAN; cleartext to `http://192.168.x.x` is blocked by default on modern Android. “Test connection” will fail without this.

**Required spec change:**

Add an **Implementation prerequisites** (or Architecture) subsection:

1. `android.permission.INTERNET` in the manifest.
2. Network security config (or equivalent) that permits **cleartext HTTP to the configured relay host / private LAN** as needed for Gen2+ RPC. Document that this is centre-local Wi‑Fi only; no public exposure of the relay API via the app.
3. Optional mDNS convenience may need multicast-related permission; must remain non-blocking (manual IP stays primary).

Add acceptance: Test connection succeeds against a LAN Shelly over `http://<ip>` on a cleartext-restricted Android version.

### Major 4 — ON semantics (rising edge)

**Problem:** Unclear whether every tick re-sends `Switch.Set on` (chatty / timer refresh) or only on rising edge.

**Required:** Choose and document **rising-edge ON only** (preferred): issue ON when transitioning to desired-on, with a single `toggle_after` computed for that play class (gong estimate or doha 1800 s ceiling + lead + lag + margin). Do not spam ON every heartbeat unless you explicitly choose refresh-on-tick and justify it. Rising-edge is the default to write into the spec.

---

## What not to do

- Do not implement Kotlin/Compose/Room changes in this task.
- Do not redesign BLE provisioning, multi-relay, cloud Shelly, or full Sounds (volumes / burst / doha time).
- Do not change schedule fire rules, grace, or `AlarmScheduler` beyond what Approach C already says (read `nextDeadline` only).
- Do not add medium/low items from the broader review (manifest.json import, EncryptedSharedPreferences for pass, etc.) unless a single clarifying sentence is needed.

## Done criteria

1. Both markdown specs updated in place.
2. Each major item above is **visible in the doc** (not only implied).
3. Acceptance / Testing sections include the new bullets called out above.
4. No code diffs outside the two spec files (and this prompt file may stay untouched).
5. Short summary reply: what you changed in each file (bullet list).
---
