# Agent skills for Niyam

Project-local **Agent Skills** (`SKILL.md`) tailored to this appliance app:
Compose UI, Room/data layer, testing, AVD/CLI, and adaptive (tablet) layouts.

## Installed set (5)

| Skill | Source | Why for Niyam |
|-------|--------|----------------|
| **android-cli** | [android/skills](https://github.com/android/skills) (Google) | AVDs, install/run, screenshots, SDK, official skill discovery |
| **adaptive** | android/skills | Tablet landscape, nav rails, multi-window / large screens |
| **testing-setup** | android/skills | Unit / UI / screenshot / E2E harness strategy |
| **android-data-layer** | [new-silvermoon/awesome-android-agent-skills](https://github.com/new-silvermoon/awesome-android-agent-skills) | Room, repository, offline-first patterns |
| **android-jetpack-compose** | [thebushidocollective/han](https://github.com/thebushidocollective/han) | Compose state, declarative UI patterns |

Lockfile: [`skills-lock.json`](../skills-lock.json) (reproducible installs via `npx skills experimental_install`).

## Where they live

| Path | Agent |
|------|--------|
| `skills/` | Canonical copy (commit this) |
| `.claude/skills/` | Claude Code |
| `.grok/skills/` | Grok Build |
| `.agents/skills/` | Generic / other agents |

Grok also scans `.grok/skills/` per project; Claude Code scans `.claude/skills/`.

## Reinstall / update

```bash
cd /Users/wizops/DIPI/niyam   # or your clone

# Restore from lockfile
npx skills experimental_install

# Or re-add explicitly (Claude Code + Grok only — do NOT use -a '*')
npx skills add android/skills -s android-cli -s adaptive -s testing-setup \
  -a claude-code -a grok -y --copy
npx skills add new-silvermoon/awesome-android-agent-skills -s android-data-layer \
  -a claude-code -a grok -y --copy
npx skills add thebushidocollective/han -s android-jetpack-compose \
  -a claude-code -a grok -y --copy

# Keep trees in sync
rsync -a --delete .claude/skills/ skills/
rsync -a --delete .claude/skills/ .grok/skills/
rsync -a --delete .claude/skills/ .agents/skills/
```

## Intentionally not installed (for now)

| Skill | Reason |
|-------|--------|
| Clean architecture (full multi-module) | Niyam is a single-app appliance; data-layer skill is enough |
| Expo / React Native packs | Native Kotlin/Compose only |
| Pentest / ASO packs | Out of scope for core gong appliance work |
| Argent emulator MCP | Useful if you use Argent MCP; android-cli covers AVD basics |

## When agents should load them

- **Compose UI / Nocturne / nav rail** → `android-jetpack-compose`, `adaptive`
- **Room, SeedLoader, repository** → `android-data-layer`
- **Emulator, install, screencap, wm size** → `android-cli` (and reset `wm size` after capture!)
- **New tests / coverage plan** → `testing-setup`
- **Always** also follow project `AGENTS.md` / `CLAUDE.md` / domain parity rules
