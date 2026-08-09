# Media layout

## Bundled in app assets (debug / scaffold)

```
app/src/main/assets/media/
  gongs/
    ting.mp3
    drum.mp3
    gong-ting.mp3   # original filenames from gongserver
    gong-drum.mp3
  doha/
    manifest.json   # slot → filename; audio files NOT shipped
```

Settings key `gong_track` uses the **stem** (`ting`, `drum`) → file `ting.mp3`.

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
