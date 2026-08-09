# Doha media folder — design

**Date:** 2026-08-09
**Milestone:** M6 slice (doha SAF pack)
**Status:** approved design, not yet implemented
**Amended 2026-08-09** — major review fixes (scan depth, permission lifecycle,
source precedence, slot labelling)

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

`ActivityResultContracts.OpenDocumentTree`. Persistable permissions survive
reboot, which matters for an appliance. If the folder later becomes unreadable
(SD card pulled, permission revoked), the screen must say so rather than
silently showing stale rows.

**Which folder to pick.** Staff must pick the folder that **directly contains**
the `D01`…`D11` files, not an ancestor. `MEDIA.md` shows real packs living under
a `doha/` directory, so picking the pack root is an easy mistake that would
otherwise scan to zero files and look broken.

One concession, no more: if the picked tree has **no matching audio at the top
level** and its immediate children contain **exactly one** subdirectory named
`doha` (case-insensitive), scan that child's immediate children instead. **Do not
recurse further.** Anything deeper is a wrong pick and gets the empty state.

**Allowed extensions (v1):** `.mp3` only, case-insensitive. A finite explicit
list keeps "why didn't my file appear" answerable.

### Permission lifecycle on re-pick

Order matters, because a half-applied re-pick can strand the appliance with a
tree URI it cannot read.

1. Take persistable read permission on the **new** URI first.
2. Only if that succeeds: release the persistable permission on the **previous**
   `doha_tree_uri` (when set and different), then persist the new URI, then remap.
3. If `takePersistableUriPermission` throws: show a banner and **change nothing** —
   do not replace `doha_tree_uri`, do not touch `media_slots`. A failed re-pick
   must leave a working appliance working.

Releasing the old grant matters because persisted URI permissions are a limited
per-app resource; re-picking repeatedly over a course season would otherwise leak
them.

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

### Source precedence — what auto-map may touch

`MediaSlotDao.put` is an upsert keyed on `slot`, so auto-map must be told which
slots it is allowed to write. The rule:

> **Auto-map may only write a slot that is empty or currently `source = auto`.
> It must never overwrite `source = manual` or `source = bundled`.**

`bundled` matters in debug builds, where the synthetic test tones occupy slots;
an auto-map over a sideloaded folder must not silently displace them. `manual`
matters because it is the staff's explicit correction.

Rescan therefore clears only `auto` rows (`deleteBySource(AUTO)`) and then writes
auto rows **only into slots not held by manual or bundled**. A file that claims a
slot already held manually is reported as skipped, not applied — visible, not silent.

### Manual reassignment

Staff can assign any scanned file to any slot, writing `source = manual`. Clearing
a manual row is an explicit staff action; nothing automatic removes it.

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

Layout: folder path + Change/Rescan at the top; an 11-row slot table (**`Slot 01`
… `Slot 11`**, filename, source tag, state dot, reassign control); an unassigned /
conflicts list below.

**No day column.** `DohaSlots.legacyModular(day, total, anapana)` maps course-day
→ slot through modular cycles — slot 3 serves anapana day 3 *and* part of the
vipassana cycle, and slot 11 only the last day. A slot does not own a calendar
day, and a "day it serves" column would state something false. Label by slot number
only.

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
- **a `bundled` or `manual` slot survives a rescan even when a scanned file
  claims that same slot — the file is reported skipped, not applied**
- **a tree whose only match is under a single `doha/` child resolves to those
  files; a two-level-deep tree does not**

SAF itself is not unit-tested; it is covered by the QA checklist.

## Acceptance

1. Picking a folder of 11 correctly-named files maps all 11 and clears GONGS ONLY.
2. Permission survives a reboot.
3. A manual override survives a rescan; so does a bundled slot.
4. A duplicate-slot folder produces a visible conflict, and nothing is auto-picked.
5. **Picking the wrong parent folder (no matches, no single `doha/` child) shows
   the empty state naming the `D01…D11` convention — not a silent no-op.**
6. **Re-picking a folder releases the previous grant and remaps from the new one,
   leaving no stale `doha_tree_uri`. A re-pick whose permission take fails leaves
   the existing folder and mapping untouched.**
7. `./gradlew :app:testDebugUnitTest` green, including the new mapper tests.
