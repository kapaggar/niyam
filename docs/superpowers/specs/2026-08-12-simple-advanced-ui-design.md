# Simple / Advanced UI — design spec

**Date:** 2026-08-12  
**Status:** Draft for review  
**Product:** Niyam (`org.dhamma.gong`)  
**Source:** Pixel C design review + staff simplification goals  

---

## 1. Goal

Make the appliance **fast to operate at a centre** without deleting technician power.

- **Less text** on every screen; controls and status chips carry meaning.
- **Progressive disclosure** for lore (ⓘ sheets), not long body copy under every field.
- **Simple / Advanced** modes so daily staff and technicians see different density.
- **Network** is not a top-level destination.
- **Amp power (Simple)** is only what you need for Shelly on LAN: **IP + test + on/off**.

Non-goals for this design:

- Redesigning schedule fire semantics or domain law.
- Shelly Cloud / BLE provisioning.
- Full redesign of the Schedule grid (stays Advanced-capable as today).
- True mouse “hover” as the only way to learn something (tablets dominate).

---

## 2. Decision: where Amp power lives

**Option A (locked):**

| Mode | Amp power UX |
|------|----------------|
| **Simple** | A **card inside Setup** — host/IP, Test connection, Amp ON / OFF, one status line. Enable “auto with schedule” as a single switch. |
| **Advanced** | Existing **Amp power** rail destination (or equivalent full screen): switch id, lead/lag, optional auth password, diagnostics, longer copy. |

Rationale:

- Daily staff set Shelly once and walk away; Setup is the “leave the tablet” surface.
- Username/password are not required for the common Shelly LAN case; keep out of Simple.
- Full relay diagnostics stay one tap away for technicians without cluttering the wall path.

Backend (`relay_host`, `relay_enabled`, `relay_auth_*`, switch id, lead/lag) **unchanged**. Simple only **exposes** a subset; Advanced edits the rest.

---

## 3. Simple vs Advanced

### 3.1 Mode storage

- Setting key: `ui_mode` with values `simple` | `advanced` (default **`simple`**).
- Change lives in **Setup** (Appearance-adjacent): segmented control **Simple | Advanced**.
- Survives restarts (Room settings, same path as other keys).
- Does **not** require PIN by itself; if a PIN is set, Setup is already behind the app-open gate.

### 3.2 Navigation (Simple)

Persistent left rail, fewer items:

| Order | Tab | Role |
|------|-----|------|
| 1 | Dashboard | Wall view, test, health |
| 2 | Courses | Add/remove courses |
| 3 | Schedule | Grid + edit |
| 4 | Logs | Play history |
| 5 | Setup | Permissions, PIN, theme, network, **amp card**, ui_mode |

**Hidden from Simple rail** (reachable only when Advanced):

- Sounds  
- Audio out  
- Amp power (full screen)  
- Time (optional: keep a **minimal** Time entry in Simple — see §5 — or fold clock confirm into Setup; **prefer fold clock confirm into Setup/Dashboard health and hide Time rail in Simple**)

**Locked decision for Time in Simple:** hide **Time** from Simple rail. Clock trust “Confirm” stays on Dashboard health row (already tappable when untrusted) and may appear once on Setup under Appliance state. Zone pin is Advanced-only (Time screen).

### 3.3 Navigation (Advanced)

Current full rail (or current set of shipped screens), including:

Dashboard · Schedule · Courses · Logs · Sounds · Audio out · Amp power · Time · Setup  

**Network** is **removed** as a top-level tab in both modes (content lives in Setup).

### 3.4 Behaviour when Advanced-only settings already exist

- If `relay_host` / sounds / audio route were configured earlier, they **keep working** in Simple.
- Simple does not wipe Advanced fields.
- Dashboard may still show route / relay toggle if product already shows them; prefer **read-only status** in Simple and edit in Setup/Advanced screens.

---

## 4. Setup screen (Simple)

Setup is the **install and leave** surface. Vertical stack, minimal prose.

### 4.1 Sections (order)

1. **Readiness banner** — one line (e.g. NOT READY — No PIN; or READY).  
2. **Permissions** — Notifications / Exact alarms / Battery: status chip + tap opens system page. Sub-explanations → ⓘ only.  
3. **Network** (merged from Network screen) — see §6.  
4. **Amp power** (Simple card) — see §7.  
5. **Appearance** — Dark / Light / Follow device (keep).  
6. **PIN** — set/change/remove (keep).  
7. **UI mode** — Simple | Advanced.  
8. **Appliance state** (compact) — scheduler running, last tick, clock trusted, build; optional Confirm clock if untrusted.

### 4.2 Copy rules on Setup

- No multi-sentence paragraphs under green rows.
- One short subtitle at top of screen max (~1 line).
- Failures: one amber/red line with the actionable fact.

---

## 5. Progressive disclosure (all modes)

| Pattern | Use |
|---------|-----|
| **Status chip** | `allowed`, `Speaker`, `RELAY ON`, `Wi-Fi`, `not probed` |
| **ⓘ** | Opens bottom sheet or dialog ≤ 3 short sentences |
| **Advanced mode** | Whole screens/fields, not nested “show more” on every control |
| **Dashboard health** | Dots + short values; long reasons on tap if needed |

Avoid relying on mouse hover; tablet and finger are primary.

### Copy diet (global)

- Remove or move to ⓘ any paragraph that explains *why Android works that way* when the control already works.
- Prefer immediate-save; **remove inconsistent explicit Save** on doha time if still present (match rest of app), or label why Save is required in one word.

---

## 6. Network merged into Setup

Remove `Tab.NETWORK` from the rail in both modes.

**Setup → Network card (Simple):**

| Element | Behaviour |
|---------|-----------|
| Kind chip | Wi-Fi / Offline / Hotspot (best effort) |
| Address | IPv4 if known |
| Internet | reachable / not (existing probe) |
| Actions | Open Wi-Fi settings · Open hotspot settings |

**Drop from Simple UI:** long location-permission essay. Optional ⓘ: “SSID needs system Wi-Fi settings; this app does not request location.”

Network polling stays as today when Setup is visible (or on a short interval while Setup is resumed).

---

## 7. Amp power — Simple card (in Setup)

### 7.1 Layout

```
AMP POWER
[ Auto with schedule ● ]     status: not probed | OK | fail reason

Host / IP
[ 10.0.0.20                    ]  [ Test ]

[ Amp ON ]  [ Amp OFF ]
```

### 7.2 Fields (Simple)

| UI | Setting / action |
|----|------------------|
| Auto with schedule | `relay_enabled` 1/0 |
| Host / IP | `relay_host` (trim; blank clears config) |
| Test | existing test-connection path |
| Amp ON / OFF | existing manual override (clock-trust independent) |
| Status | three-state reachability + last error (unchanged honesty rules) |

### 7.3 Not in Simple UI

- Username  
- Password / Clear password  
- Switch id stepper  
- Lead / lag  
- Long “manual override” / “lead and lag” essays  

These remain on **Advanced → Amp power** full screen (`RelayScreen`).

### 7.4 Empty / failure

- Blank IP + Test → toast or inline: “Enter Shelly IP first.”  
- Test fail → show `lastError` one line (no stack).  
- Relay still must **never** block gong play (existing law).

### 7.5 Dashboard

- Keep Master / Gong / Doha as today.  
- **Relay** toggle on Dashboard: keep for Advanced users; in Simple either hide Relay toggle (edit only in Setup card) **or** keep it if already used in hall — **prefer hide in Simple** so Setup is the only place for amp config in Simple.

---

## 8. Advanced Amp power screen

Keep current `RelayScreen` responsibilities:

- Full connection form (host; optional auth if product still needs password for locked Shellys — hide username if unused).  
- Switch id, lead/lag.  
- Manual override + status cards.  
- Can shorten copy in a later pass; behaviour unchanged.

If username is never used on Gen2+ LAN auth, Advanced may show password only; out of scope to remove stored keys in this spec.

---

## 9. Other Simple-mode screen density (same release if cheap)

Not blocking Amp/Setup, but part of the same design language:

| Screen | Simple treatment |
|--------|------------------|
| Sounds / Audio out | Hidden in Simple; prior settings still apply |
| Dashboard | Unchanged layout; optional trim of detail line |
| Courses / Schedule / Logs | Stay in Simple; move long empty-state essays to ⓘ |

Full copy diet across Sounds/Audio can be a follow-up milestone.

---

## 10. Implementation sketch (for planning)

1. Add `ui_mode` to defaults + seed missing.  
2. `GongApp` / `Tab`: filter rail by mode; remove Network tab; wire Network into Setup.  
3. Extract `AmpPowerSimpleCard` composable; embed in Setup; bind existing VM relay APIs.  
4. Hide Dashboard Relay toggle when Simple.  
5. Time tab Advanced-only; Confirm clock remains on Dashboard.  
6. Setup: UI mode control + Network card + Amp card; strip permission subtitles to ⓘ.  
7. Tests: settings default simple; rail composition; relay_host still read by controller when Simple.  
8. Docs: BETA checklist + PROGRESS note; screenshots of Setup with amp card.

No domain schedule changes. No new network permissions.

---

## 11. Acceptance criteria

- [ ] Fresh install defaults to **Simple** rail (5 items, no Network, no Amp full page, no Sounds/Audio/Time).  
- [ ] Setup shows **Network** facts + Wi-Fi/hotspot buttons.  
- [ ] Setup shows **Amp** card: IP, Test, ON/OFF, auto switch, honest status.  
- [ ] Switching to **Advanced** restores full rail including Amp power screen.  
- [ ] Existing `relay_host` continues to drive RelayController in Simple.  
- [ ] Unit tests green; no PIN/password logged.  
- [ ] Gong still plays if Shelly unreachable.

---

## 12. Open points (non-blocking)

1. Whether Advanced still shows a **username** field (recommend hide if always unused).  
2. Exact ⓘ component (dialog vs modal bottom sheet) — pick one Material 3 pattern and reuse.  
3. Whether Simple Dashboard keeps a single “Amp: off/on” read-only chip (nice-to-have).

---

## 13. Approval

Amp placement: **A — Simple card in Setup; full screen Advanced-only.**  
UI mode: **Simple default; Advanced expands rail.**  
Network: **merged into Setup; tab removed.**

After human approval of this spec, next step is an implementation plan (`writing-plans`), then code.
