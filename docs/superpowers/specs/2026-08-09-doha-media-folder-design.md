# Doha media folder — design

**Date:** 2026-08-09
**Milestone:** M6 slice (doha SAF pack)
**Status:** approved design, not yet implemented

## Problem

Release builds ship with no doha audio, so `media_slots` is empty and the
Dashboard shows the **GONGS ONLY** chip. Staff have no way to point the
appliance at the doha recordings that live on the device. `MediaResolver.doha()`
already resolves a slot to a persisted SAF URI, and `SettingsDefaults.androidExtras`
already declares `doha_tree_uri` — but nothing ever writes it. This closes that gap.

Doha masters are copyrighted and never committed (`MEDIA.md`, `LICENSE-NOTES.md`).
Sideloading a folder is the only supported path.

## What exists already

| Piece | State |
|---|---|
| `MediaSlotEntity(slot, uri, filename, source, verifiedAt)` | exists |
| `MediaSlotSource.AUTO / MANUAL / BUNDLED` | exists |
| `MediaSlotDao` — `observeAll`, `put`, `putAll`, `deleteBySource`, `get` | exists |
| `MediaResolver.doha(slot)` → persisted SAF URI | exists |
| `doha_tree_uri` setting key | declared, never written |
| `DohaSlots.pickSlot` (which slot a given day wants) | exists, pure domain |

So this is wiring, not new architecture. No schema change.

## Scope

**In:** a Sounds-adjacent screen to pick a folder, scan it, auto-map by filename
prefix, reassign manually, and see what is missing.

**Out:** copying files into app storage, cloud sync, transcoding, editing the
doha *schedule* (that is `doha_time` / `doha_strategy`, a different screen), and
any change to `DohaSlots` selection rules.

## Design

### Picking the folder

`ActivityResultContracts.OpenDocumentTree`. On result, take persistable
read permission:

```
contentResolver.takePersistableUriPermission(uri, FLAG_GRANT_READ_URI_PERMISSION)
```

Persist the tree URI to `doha_tree_uri` via `repo.putSetting`. Persistable
permissions survive reboot, which matters for an appliance. If the folder later
becomes unreadable (SD card pulled, permission revoked), the screen must say so
rather than silently showing stale rows.

### Auto-mapping

Scan the tree's immediate children for audio files. Map by **`D01`…`D11` prefix**,
case-insensitive, matching the leading token of the filename. `D01 morning.mp3`,
`d01-morning.mp3` and `D01.mp3` all map to slot 1.

Rules:

- A file whose prefix does not parse, or whose slot is outside 1..11, goes to
  **unassigned** — never guessed. This mirrors the existing "never guess" stance
  in `SeedLoader`'s corrupt-row handling.
- Two files claiming the same slot: neither is auto-assigned; both are listed as
  a **conflict** for staff to resolve manually. Silently picking one would be a
  coin flip on which recording plays at 04:30.
- Auto-mapping writes `source = auto`.

### Manual reassignment

Staff can assign any scanned file to any slot. That write uses `source = manual`.

**A rescan must never overwrite a `manual` row.** Rescan clears and rewrites only
`source = auto` rows (`deleteBySource(AUTO)`), so a staff override survives
re-scanning the folder. Clearing a manual row is an explicit action.

### Verification

`verified_at` is stamped when a slot's URI is confirmed openable. The screen shows
per-slot state: mapped and verified, mapped but unreadable, or empty. "Mapped" is
not the same claim as "will play", and the screen must not conflate them — this is
the same honesty rule the Dashboard health rows follow.

### Screen

`ui/DohaMediaScreen.kt`, wired to the existing locked **Sounds** tab
(`Tab.SOUNDS`, `GongApp.kt`), reusing `ScreenTitle` / `SurfaceCard` / `Tag` /
`Dot`. Landscape 1280×800, ≥44 dp targets, immediate saves with a toast.

**This implements only the doha-slot-mapping part of Sounds.** The rest of that
screen's remit — track choice, volumes, burst gap, doha time, no-course mode —
stays unbuilt. The screen must not imply otherwise, and `PROGRESS.md` should keep
Sounds listed as partial rather than shipped.

Layout: folder path + Change/Rescan at the top; an 11-row slot table (slot, day
it serves, filename, source tag, state dot, reassign control); an unassigned /
conflicts list below.

### Data flow

```
staff picks folder
  → persist tree uri  → settings.doha_tree_uri
  → scan children
  → classify: matched | unassigned | conflict
  → mediaSlots.deleteBySource(AUTO); putAll(auto rows)
  → MediaResolver.doha(slot) resolves at play time
  → Dashboard GONGS ONLY chip clears when count > 0
```

### Error handling

| Case | Behaviour |
|---|---|
| Folder unreadable / permission lost | Banner with Re-pick action; slots keep last known rows, marked unverified |
| File deleted after mapping | Slot shows unreadable; play logs `error` via existing `MediaResolver.Missing` path |
| No files match | Empty state naming the expected `D01…D11` convention |
| Fewer than 11 slots mapped | Allowed. Dashboard already shows `n / 11 doha slots mapped` |

Playback behaviour on a missing slot is unchanged — `MediaResolver` already
returns `Missing` and the player logs it.

### Testing

Pure prefix/conflict classification is extracted to `domain/DohaPackMapper.kt`
(no Android imports) and unit-tested on the JVM:

- `D01`…`D11` parse, case-insensitive, with and without separators
- slot 0 and slot 12 rejected as unassigned
- two files claiming one slot produce a conflict, not a silent winner
- non-audio and prefix-less files land in unassigned
- rescan preserves `manual` rows and replaces `auto` rows

SAF itself is not unit-tested; it is covered by the QA checklist.

## Acceptance

1. Picking a folder of 11 correctly-named files maps all 11 and clears GONGS ONLY.
2. Permission survives a reboot.
3. A manual override survives a rescan.
4. A duplicate-slot folder produces a visible conflict, and nothing is auto-picked.
5. `./gradlew :app:testDebugUnitTest` green, including the new mapper tests.
