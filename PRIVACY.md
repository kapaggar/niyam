# Privacy Policy — Niyam (Dhamma Gong)

**Last updated:** 2026-08-08  
**Application id:** `org.dhamma.gong`  
**Repository:** [github.com/kapaggar/niyam](https://github.com/kapaggar/niyam)

This policy describes how the **Niyam** Android app (“the App”) handles information when used as a **centre appliance** (gong and morning doha scheduler).

## Summary

| Question | Answer |
|----------|--------|
| Does the App require an account? | **No** |
| Does the App need the internet for core features? | **No** — schedule and playback are offline |
| Does the App send data to our servers? | **No** — there is no Niyam backend |
| Does the App include analytics or advertising SDKs? | **No** (as shipped in this repository) |
| Where is data stored? | **Only on the device** (app-private storage) |

## What the App stores on the device

All durable state lives in a local SQLite database (`gong.db`) and related app files, including:

- Course calendar (course type, start date, notes)
- Gong schedule events and settings (volumes, timezone, toggles)
- Play history (`play_log`) for staff diagnostics
- Scheduler state (e.g. “already fired” guards, clock-trust markers)
- Optional **PIN hash** (salted PBKDF2) if staff enable a PIN — **not** the PIN in plaintext
- Optional media pack metadata (e.g. mapped doha slots / SAF URIs when that feature is used)

Debug builds may include synthetic test audio for development. Release builds may ship without doha audio until a centre installs a licensed pack (see `MEDIA.md`).

## What the App does **not** collect

As designed in this open-source tree, the App does **not**:

- Create user accounts or profiles
- Upload course, schedule, or log data to a cloud service operated by the project
- Track location for advertising or analytics
- Show third-party ads
- Sell personal data

## Network use

Core gong/doha operation is intended to work **offline**.

If a future build uses network features (for example Wi‑Fi configuration, optional LAN admin, or OS update channels), those are for local centre networking or device OS functions — not for sending schedule data to a project server. Always re-check this policy when you install a build that differs from this repository.

## Permissions (typical)

The App may request Android permissions such as:

- **Notifications** — persistent “scheduler running / next event” indicator
- **Foreground service (media playback)** — keep scheduler and audio alive with screen off
- **Exact alarms** — fire gongs near the scheduled wall-clock time
- **Boot completed** — restart the scheduler after power loss
- **Bluetooth** (when used) — route audio to a speaker/amp
- **Battery optimization exemption** (when requested) — reduce OEM kills of the appliance process

Permissions are used for appliance reliability and audio, not for advertising.

## Children

The App is operated by centre staff as equipment. It is not directed at children as a consumer social product. No child accounts are created.

## Data retention and deletion

Data remains on the device until:

- Staff delete courses/events/logs in-app (where supported), or
- The App is uninstalled / app storage is cleared, or
- A restore overwrites the local database (when backup/restore is available)

There is no project-side copy to delete.

## Sharing

We do not operate a service that receives App data. Staff may export or copy the device database themselves (e.g. for support); that is under the centre’s control.

## Security of the PIN

If a PIN is set, only a **salted hash** is stored. Physical access to an unlocked tablet remains the primary risk; treat the device as centre equipment (see `SECURITY.md`).

## Changes

Material changes to this policy will be reflected in this file and the `Last updated` date. For published Play Store builds, the store listing may link to a hosted copy of this document.

## Contact

Privacy questions about this open-source project: open a GitHub issue on  
[https://github.com/kapaggar/niyam](https://github.com/kapaggar/niyam)  
or contact the repository owner via GitHub.

For **course audio rights** (gong/doha masters), contact the relevant rights holders; this project does not re-license that material.
