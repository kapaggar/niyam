# Claude work assignment — beta screen review

**Full multi-agent plan (source of truth):**

→ [`docs/superpowers/plans/2026-08-09-beta-screen-review-assignment.md`](superpowers/plans/2026-08-09-beta-screen-review-assignment.md)

## Paste this into Claude Code (personal config)

```text
You are the Lead for Niyam beta screen review.

Read and follow entirely:
  docs/superpowers/plans/2026-08-09-beta-screen-review-assignment.md

Mandatory skills (load before work): android-jetpack-compose, adaptive,
android-cli, testing-setup, android-data-layer — from skills/ or
~/.claude-personal/skills/. Also AGENTS.md + CLAUDE.md.

Execution mode: MULTI-AGENT PARALLEL by wave.

Wave 0 (you): baseline tests, create audit matrix file, then SPAWN six
subagents in parallel for Wave 1 Agents A–F. Each subagent gets ONLY its
brief + file lock + global constraints from the assignment.

After Wave 1: triage P0/P1/P2. Spawn Wave 2 implementers in parallel on
disjoint files only. Optional Agent G for Time+Setup stubs if you judge
beta needs them.

Wave 3: one agent for 1280×800 screenshots via android-cli (reset wm after).

Wave 4 (you): integrate, full test suite, version 0.2.0-beta1, BETA-QA-CHECKLIST,
PROGRESS update, final APK path for human.

Hard rules: no schedule semantic invention; no Claude commit trailers;
domain stays pure; do not push to main without summarizing for the human
unless they asked for push.

Return a single executive summary: what changed per screen, test counts,
APK path, residual risks, and the human checklist path.
```

## Skills reminder

| Skill | Why |
|-------|-----|
| `android-jetpack-compose` | Screen Compose patterns |
| `adaptive` | 1280×800 tablet landscape, nav rail |
| `android-cli` | Emulator, install, screenshots |
| `testing-setup` | Test strategy when adding tests |
| `android-data-layer` | Only if Room/repo needs a fix |

On this machine: `CLAUDE_CONFIG_DIR=~/.claude-personal` and skills under `~/.claude-personal/skills/`.

## Output the human wants

1. Polished core screens (Dashboard, Courses, Schedule, Logs, PIN)
2. Optional Time + Setup stubs if Lead enables them
3. Debug APK `0.2.0-beta1`
4. Screenshots at 1280×800
5. `docs/BETA-QA-CHECKLIST.md` for tablet verification
