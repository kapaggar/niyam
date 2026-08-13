# Media layout

## Bundled in app assets (debug / scaffold)

```
app/src/main/assets/media/
  gongs/
    ting.mp3        # "single gong" — Single_Gong recording, ONE ring per play
    drum.mp3        # "sikkim gong" — sikkim_GONG recording, THREE hits per play
    gong-ting.mp3   # original filenames from gongserver (dormant)
    gong-drum.mp3
  doha/
    manifest.json   # slot → filename; audio files NOT shipped
```

Settings key `gong_track` uses the **stem** (`ting`, `drum`) → file `ting.mp3`.
The stems are legacy Pi ids kept for database compatibility; the recordings
behind them were replaced 2026-08-09 (sources:
`DHAMMA/03-Course-Playback/Gong-Bell/Single_Gong.mp3` and
`DHAMMA/Dhamma/Study/sikkim_GONG.mp3`). `domain/GongTracks.kt` maps stem →
staff-facing label and hits-per-play; a schedule row's `repeats` counts
audible hits, and the player divides by hits-per-play to decide how many
times to play the file.

## Centre media pack (install later)

Target on-device layout (app-specific storage):

```
files/media/
  gongs/{ting,drum,...}.mp3
  doha/
    manifest.json
    D01_....mp3 … D11_....mp3
```

Manifest format (from the Pi appliance):

```json
{
  "1": "D01_0632_Doha-Hindi-1_NA_NA.mp3",
  ...
  "11": "D11_0632_Doha-Homage_NA_NA.mp3"
}
```

See `LICENSE-NOTES.md` before redistributing any real course audio.
