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
| `skills/` | Canonical copy in this repo (commit this) |
| `.grok/skills/` | Grok Build (project) |
| `.claude/skills/` (repo) | Claude Code project mirror |
| `.agents/skills/` (repo) | Generic project mirror |
| **`$HOME/.claude-personal/skills/`** | **Claude Code personal config** (`CLAUDE_CONFIG_DIR`) — symlinks → `$HOME/.agents/skills/` |
| `$HOME/.agents/skills/` | Shared user skill store (personal Claude pattern) |

This machine does **not** use `~/.claude` for the personal Claude setup; skills for Claude go under **`~/.claude-personal/skills/`**.

Grok scans `.grok/skills/` in the project (and `~/.grok/skills/` user-wide).

## Reinstall / update

```bash
cd /Users/wizops/DIPI/niyam   # or your clone

# Project mirrors (Grok + repo skills/)
npx skills add android/skills -s android-cli -s adaptive -s testing-setup \
  -a grok -y --copy
npx skills add new-silvermoon/awesome-android-agent-skills -s android-data-layer \
  -a grok -y --copy
npx skills add thebushidocollective/han -s android-jetpack-compose \
  -a grok -y --copy
rsync -a --delete .grok/skills/ skills/
# keep the other project mirrors in step (they are not touched by the lines above)
rsync -a --delete skills/ .claude/skills/
rsync -a --delete skills/ .agents/skills/

# Claude personal (~/.claude-personal) — copy then symlink like other personal skills
for skill in android-cli adaptive testing-setup android-data-layer android-jetpack-compose; do
  rm -rf "$HOME/.agents/skills/$skill"
  cp -a "skills/$skill" "$HOME/.agents/skills/$skill"
  rm -rf "$HOME/.claude-personal/skills/$skill"
  ln -s "../../.agents/skills/$skill" "$HOME/.claude-personal/skills/$skill"
done
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
