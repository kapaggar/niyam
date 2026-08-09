# Amplifier relay via Shelly 1 Gen4 — design

**Date:** 2026-08-09
**Milestone:** new (the `relay_enabled` setting has existed inert since M1)
**Status:** approved design, not yet implemented
**Amended 2026-08-09** — major review fixes (back-to-back OFF suppression, miss
handling, network prerequisites, rising-edge ON)

## Problem

The centre amplifier is left powered all day, or switched by hand. The appliance
already knows exactly when a gong or doha will play, so it can switch the amp on
just before and off just after. `relay_enabled` exists in `SettingsDefaults` and
the Dashboard paints a Relay toggle at 50% opacity, inert. This makes it real.

## Hardware

**Shelly 1 Gen4**, dry-contact relay, driving amp mains. Amp draw < 8 A, well
inside the device rating. Gen4 speaks the Gen2+ JSON-RPC API.

Verified against the Shelly documentation:

- `GET /rpc/Switch.Set?id=0&on=true`, or `POST /rpc` with
  `{"id":1,"method":"Switch.Set","params":{"id":0,"on":true}}`
- `Switch.Set` accepts **`toggle_after`** (seconds) — a device-side flip-back
  timer. Response is `{"was_on": bool}`.
- Auth, when enabled, is **digest SHA-256 (RFC 7616)**, username `admin`,
  realm = device id. `Shelly.GetDeviceInfo` and `/shelly` remain unauthenticated.
- Discovery is `_shelly._tcp.local`, but **Gen4 mDNS is documented as unreliable**
  (units emitting no mDNS packets despite `discoverable:true`).

## Scope

**In:** local WiFi control of one Shelly, driven by the existing schedule, plus a
screen to configure and test it.

**Out — explicitly:**

- **In-app BLE provisioning.** The Shelly is joined to the centre WiFi using
  Shelly's own app (which is what its BLE stack is for). Niyam only fires over
  WiFi. Decided with the user; not a gap to be closed later by stealth.
- Cloud / Shelly Cloud API, Zigbee, Matter, multiple relays, per-event relay
  overrides, power metering.

## Non-negotiable rules

1. **The relay never blocks, delays, or fails a play.** All switching is
   fire-and-forget on its own coroutine with a short timeout. An unreachable
   Shelly logs and the gong still rings on time. Amp power is a convenience; the
   gong is the product. This is the rule that outranks every other goal here.
2. **Every switch-on carries `toggle_after`.** Device-side auto-off means a
   crashed or powered-off tablet cannot leave the amp energised overnight. The
   explicit OFF is the normal path; `toggle_after` is the backstop.
3. **No schedule semantics change.** Fire windows, the 120 s grace, `fired:`
   guards and clock-trust suppression are untouched. The relay reads the
   scheduler's existing output; it never influences whether or when a gong fires.
4. **Clock untrusted → no relay.** Automatic plays are suppressed, so automatic
   relay switching is suppressed with them. Manual override from the screen
   still works.
5. **Never switch OFF while the amp is still needed.** No OFF may be issued if a
   play is in progress, or if `nextDeadline` is within the same
   `heartbeat + lead` window used to pre-arm ON. A gong followed by doha a
   minute later must not power the amp down in between and re-warm it cold.
6. **ON is rising-edge only.** ON is issued when the desired state transitions
   off→on, not re-sent every 30 s heartbeat. Re-sending would spam the device and
   silently refresh `toggle_after`, which would defeat the watchdog by pushing
   its deadline forward forever.

## Trigger rule

Static **5 s lead-in**, **5 s lag-out**, per the user.

**Approach (C), chosen:** pre-arm from the existing 30 s heartbeat rather than a
new alarm. `SchedulerCore.tick` already returns `nextDeadline`. On each tick, if
`relay_enabled` and `nextDeadline` falls within the next heartbeat window plus
the lead, switch on.

Consequence, accepted deliberately: the amp comes on **5–35 s early** rather than
exactly 5 s. For amp warm-up that is harmless, and it avoids adding alarm
plumbing to the most safety-critical code in the app. Off is precise, driven by
play end.

Rejected: a dedicated pre-arm alarm at `fireAt − 5s` (accurate, but adds a second
alarm per occurrence to `AlarmScheduler` and needs extra care so a *missed* gong
does not still power the amp); and driving purely off `PlayerEngine` start/end
(simplest, but the amp switches on at play start, so a cold amp clips the first
strike — which defeats the point).

## Architecture

```
domain/RelayPlan.kt        pure: (now, nextDeadline, playing, armedFor, …) → Desired
relay/ShellyClient.kt      Gen2+ JSON-RPC over HTTP, digest auth, timeouts
relay/RelayController.kt   holds armedForDeadline + reachability; serialises calls
schedule/SchedulerEngine   tick → RelayController: pre-arm ON *and* OFF on miss/cancel
player/PlayerEngine        emits play end → RelayController (lag-off, subject to rule 5)
ui/RelayScreen.kt          config, test, manual override, status
```

The tick path owns **both** edges. Play-end alone is not enough: a **missed**
occurrence never reaches `PlayerEngine`, so if the tick only ever armed ON, a
missed gong would leave the amp on until `toggle_after` expired. That is the
crash backstop, not the normal path.

**Sticky arm.** `RelayController` remembers `armedForDeadline`. On each tick, if
we armed for deadline *D*, and *D* is no longer the live `nextDeadline` (fired,
missed, or superseded), and we are not playing, and no new deadline is inside the
pre-arm window → issue an explicit **OFF** and clear the arm.

`RelayPlan` inputs: `now`, `nextDeadline`, `playing`, `armedForDeadline`,
`relayEnabled`, `clockTrusted`, `leadSeconds`, `lagSeconds`, `heartbeat`,
`lastPlayEndedAt`. Output: `Desired.On(toggleAfter)`, `Desired.Off`, or
`Desired.NoChange` — `NoChange` is what makes ON rising-edge rather than chatty.

`domain/RelayPlan.kt` has **no Android imports** and is unit-tested on the JVM,
matching `FireRules` / `ClockTrust`.

### Implementation prerequisites (blocking)

The manifest today has no `INTERNET` permission and no network security config.
Both are required before any of this works on device:

1. **`android.permission.INTERNET`** in `AndroidManifest.xml`.
2. **Cleartext HTTP to the relay host.** Gen2+ RPC is plain HTTP on the LAN, and
   modern Android blocks cleartext by default. Add a network security config
   permitting cleartext to private LAN ranges (or the configured relay host).
   This is centre-local WiFi only — the app never exposes the relay API publicly
   and never talks to Shelly Cloud.
3. **mDNS convenience** may need multicast handling. It must stay non-blocking;
   manual IP remains the primary path (Gen4 mDNS is unreliable).

### `toggle_after` computation

`lead + estimatedPlaySeconds + lag + margin`.

- Gong: `repeats × (perStrikeSeconds + gapSeconds)`, with `perStrikeSeconds = 10`
  as a fixed conservative allowance (bundled gong tracks are a few seconds; 10
  leaves headroom for a longer sideloaded track).
- Doha: fixed ceiling of `1800` seconds. Doha length is not known before playback
  and a chant can run long.
- `margin = 60` seconds.

The value is a watchdog, not a schedule. Erring long is correct; erring short
would cut a doha off mid-play by de-powering the amp.

### Settings (new, in `SettingsDefaults.androidExtras`)

| Key | Default | Meaning |
|---|---|---|
| `relay_host` | `""` | IP or hostname. Empty = not configured |
| `relay_auth_user` | `admin` | Fixed by Shelly, editable for completeness |
| `relay_auth_pass` | `""` | Empty = no auth |
| `relay_switch_id` | `0` | Shelly switch component id |
| `relay_lead_seconds` | `5` | |
| `relay_lag_seconds` | `5` | |

`relay_enabled` already exists and becomes live. `relay_auth_pass` is a device
password on a LAN appliance, not a user credential; it is stored like other
settings and, like the PIN, is never logged.

### Discovery

**Manual IP is the primary path**, because Gen4 mDNS is unreliable. The screen
takes a typed host with a **Test** button hitting the unauthenticated
`Shelly.GetDeviceInfo`, showing model and MAC on success. mDNS `_shelly._tcp`
lookup is offered as a convenience that is allowed to fail and never blocks
setup. The QA notes will recommend a DHCP reservation so the IP is stable.

### Screen

`ui/RelayScreen.kt`, on a **new `Tab.POWER` ("Amp power")** nav entry rather
than folded into Audio out — Audio out is about where sound goes, and mains
switching is a different concern that should not hide behind that label. Adding
one enum entry is cheap now that the rail scrolls.

Shows: host + credentials, Test connection with result, reachability dot, manual
On/Off override, lead/lag values, and the last switch result with timestamp. Same
Nocturne chrome, ≥44 dp targets, immediate saves.

The Dashboard Relay toggle stops being inert: at 50% opacity when `relay_host`
is unset, live otherwise.

### Logging

Relay actions are **not** written to `play_log` — that table is the record of
what the appliance played, and diluting it with relay chatter would hurt the
diagnosis screen this beta just fixed. Relay state lives on the Relay screen
(last action, last error). If a persistent audit trail is wanted later, it gets
its own table.

## Error handling

| Case | Behaviour |
|---|---|
| Host unset | Relay logic inert; Dashboard toggle dimmed |
| Unreachable / timeout | Play proceeds unaffected. Screen shows unreachable + last error. No retry storm: one attempt per transition, bounded timeout |
| 401 | Screen says authentication required; play unaffected |
| Tablet dies mid-play | `toggle_after` switches the amp off device-side |
| Clock untrusted | No automatic switching (rule 4) |
| Relay on, gong then missed | Tick path issues an explicit OFF once the armed deadline goes stale; `toggle_after` backstops only if the process died |
| Gong then doha inside the pre-arm window | No OFF between them (rule 5); the amp stays warm |

## Testing

JVM unit tests on `domain/RelayPlan.kt`:

- deadline inside the heartbeat+lead window → on; outside → no action
- `relay_enabled = 0` → never on
- untrusted clock → never on automatically
- `toggle_after` always exceeds lead + play + lag
- play end → off after exactly the lag
- **rising edge: a second tick while already armed returns `NoChange`, not a
  repeated ON — `toggle_after` is never silently refreshed**
- **back-to-back occurrences: after the first play ends with the next deadline
  still inside the pre-arm window, the plan stays ON — no spurious OFF between a
  gong and a doha a minute later**
- **OFF is suppressed while `playing` is true**
- **missed occurrence: armed for *D*, *D* is no longer the live deadline, not
  playing, nothing new in the window → explicit `Desired.Off` while the process
  is alive, rather than waiting on `toggle_after`**

`ShellyClient` is tested against a local fake HTTP server (request shape, digest
challenge/response, timeout). No real device in unit tests. Real hardware is a
QA-checklist item.

## Acceptance

1. With a reachable Shelly, a scheduled gong switches the amp on beforehand and
   off after, and the gong fires at its correct time.
2. With the Shelly powered down, the gong still fires on time and the screen
   reports unreachable.
3. Killing the app mid-play leaves the amp off within the `toggle_after` window.
4. Test connection reports model and MAC for a correct host, and a clear error
   for a wrong one — **succeeding against `http://<ip>` on a cleartext-restricted
   Android version, which requires the manifest and network-config work above.**
5. A gong followed by a doha inside the pre-arm window does not power the amp
   down in between.
6. A missed occurrence powers the amp off from the tick path while the app is
   still running, without waiting for `toggle_after`.
7. `./gradlew :app:testDebugUnitTest` green, including new `RelayPlan` tests.
