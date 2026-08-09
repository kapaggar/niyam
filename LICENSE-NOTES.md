# Licensing notes — Niyam

## Software and documentation

Unless noted below, source code, Gradle project files, seed **structure** (JSON/SQL
schedules), and documentation (including `README.md`, `PRIVACY.md`, `SECURITY.md`,
`AGENTS.md`, `CLAUDE.md`, and `docs/`) are licensed under the **MIT License** —
see [LICENSE](LICENSE).

## Audio media (not MIT)

- Files under `media/gongs/` (e.g. `ting.mp3`, `drum.mp3`) and matching app assets
  originate from the traditional Gongserver media tree.
- **These recordings are not MIT-licensed.** They remain the property of their
  rights holders (including Vipassana Research Institute / the course tradition
  where applicable).
- Do **not** assume you may redistribute them on Google Play or other public
  stores without explicit permission from the rights holders.
- A full doha library (D01–D11) is **not** required in git. Use
  `seed/doha-manifest.json` / `media/doha/manifest.json` as the filename contract
  when a centre installs a licensed pack on the device.

## Recommendation for public / Play builds

Prefer **synthetic placeholder tones** for CI and public demo builds. Load real
gong/doha audio via a **private centre media pack** on the device. Keep that pack
out of the public git history unless rights are cleared in writing.
