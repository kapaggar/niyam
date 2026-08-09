# Licensing notes for this Android tree

## Code and configuration

Scaffold Kotlin/Gradle code, seed JSON/SQL derived from the Pi appliance's conversion scripts,
and documentation under `docs/` follow the same intent as the parent Gongserver
project (MIT for software/docs unless noted otherwise in the parent LICENSE).

## Audio

- `media/gongs/ting.mp3`, `drum.mp3` (and `gong-*.mp3` copies) originate from the
  Gongserver appliance media tree.
- **These recordings are not MIT-licensed.** They remain the property of their
  rights holders (Vipassana Research Institute / the course tradition).
- Do **not** assume you may redistribute them on Google Play or other public stores
  without explicit permission.
- Full doha library (D01–D11) is **not** bundled here (large + same rights). Use
  `seed/doha-manifest.json` / `media/doha/manifest.json` as the filename contract
  when centres install a licensed media pack separately.

## Recommendation for a public repo / Play build

Ship **synthetic placeholder tones** generated for CI, and load real gong/doha via
a private centre media pack on the device.
