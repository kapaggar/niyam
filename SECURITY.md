# Security Policy — Niyam

## Threat model (read this first)

Niyam is a **centre appliance**, not a multi-tenant internet service.

| Assumption | Implication |
|------------|-------------|
| Device stays on centre premises, usually on power | Physical access is the outer boundary |
| Optional centre Wi‑Fi / hotspot | Network is local; do not expose the app’s future LAN admin to the public internet without extra controls |
| Staff configure schedule and PIN | Wrong gong timing harms a course; integrity of schedule + clock matters more than fancy crypto |
| No cloud backend in this repo | No server credentials to steal from our API |

**Primary risks:** unauthorized schedule edits, silenced/disabled scheduler, wrong timezone/clock, OEM killing the process, redistribution of non-free audio.

## Supported versions

| Version | Support |
|---------|---------|
| `main` branch / debug `0.1.0-mvp` | Active development — report issues |
| Older extracted Gongserver android trees | Best effort only |

There is no LTS release channel yet.

## Reporting a vulnerability

Please **do not** open a public issue for exploitable security bugs that could harm centres in the field.

Preferred:

1. **GitHub Security Advisories** for [kapaggar/niyam](https://github.com/kapaggar/niyam/security/advisories/new) (private), or  
2. Contact the repository owner via GitHub with a clear description, impact, and reproduction steps.

Please include:

- App version / commit SHA  
- Device or emulator model and Android version  
- Whether the issue needs physical access, PIN knowledge, or only local network  

We will acknowledge when we can and coordinate disclosure after a fix or mitigation.

## What is in scope

- Bypass of PIN gate (when a PIN is set) without physical data wipe  
- Privilege issues that let a third-party app on the same device tamper with `gong.db` without expected Android sandbox limits  
- Logic bugs that cause **double fire**, **early fire**, or **wrong course day** under normal offline use  
- Insecure storage of secrets (e.g. PIN in plaintext)  
- Intentional backdoors or remote control without staff consent  

## What is out of scope (for now)

- Attacks that require **unlocking the tablet as staff** and then changing settings (by design the holder of the device is trusted unless PIN is set)  
- Google Play / OEM policy debates without a concrete technical vulnerability  
- Issues only in third-party forks  
- “The amp is always on” or missing smart-plug control (product gap, not a vuln)  
- Audio copyright redistribution (legal/licensing — see `LICENSE-NOTES.md`)

## Hardening already intended

- PIN stored as **salted PBKDF2 hash**, never plaintext (`admin_pin_hash`)  
- Foreground service + alarms for availability; not a security boundary by themselves  
- No analytics SDKs in the default tree  
- `android:allowBackup="false"` on the application (reduces adb backup exfil of DB on many devices)  
- Schedule semantics tested in unit tests (regression surface for “wrong gong” bugs)

## Hardening still planned / incomplete

See `PROGRESS.md` and `docs/FABLE-REVIEW.md`. Notable gaps:

- Full first-run for exact alarms and battery optimization  
- Backup/restore threat model (exported DB contains schedule + PIN hash)  
- Field hardening for OEM background kills  
- Optional LAN admin (if added) must not bind open on untrusted networks  

## Secure configuration for centres

1. Set a **PIN** on the device after install.  
2. Set appliance **timezone** for the centre (default `Asia/Kolkata` in settings — do not rely on a travel phone’s TZ).  
3. Keep the tablet on power; disable aggressive battery savers for Niyam.  
4. Prefer a dedicated tablet, not a personal phone with untrusted apps.  
5. Do not publish centre Wi‑Fi passwords in the repo or screenshots.  
6. Treat `gong.db` backups as sensitive (schedule + PIN hash).  

## Dependency and supply chain

- Build with the Gradle wrapper in this repository.  
- Review dependency updates; prefer minimal libraries.  
- Do not commit `local.properties`, keystores, or signing passwords.

## Safe failure principle

**Silence beats a wrong gong.** Clock-trust and missed-event logic prefer no automatic play over firing at the wrong wall time. Security and reliability reviews should preserve that principle.
