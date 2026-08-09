#!/usr/bin/env python3
"""Generate tiny synthetic doha tones for debug builds and CI.

The APK must never ship real VRI doha masters (design doc §07), but the doha
code path still needs something playable on a dev machine. These are 1.5 s
decaying sine tones, one per slot, named with the D01..D11 prefix the slot
auto-mapper looks for, written into the *debug* source set only.

    python3 tools/make_test_tones.py
"""
from __future__ import annotations

import math
import struct
import wave
from pathlib import Path

OUT = Path(__file__).resolve().parent.parent / "app/src/debug/assets/media/doha-test"
RATE = 22050
SECONDS = 1.5
# A pentatonic-ish walk so the 11 slots are audibly distinguishable when testing.
FREQS = [196, 220, 247, 262, 294, 330, 349, 392, 440, 494, 523]


def tone(path: Path, freq: float) -> None:
    frames = bytearray()
    n = int(RATE * SECONDS)
    for i in range(n):
        t = i / RATE
        # exponential decay, plus a quiet octave for a bell-ish timbre
        env = math.exp(-2.5 * t)
        sample = 0.6 * math.sin(2 * math.pi * freq * t)
        sample += 0.2 * math.sin(2 * math.pi * freq * 2 * t)
        value = int(max(-1.0, min(1.0, sample * env)) * 26000)
        frames += struct.pack("<h", value)
    with wave.open(str(path), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(bytes(frames))


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    for slot, freq in enumerate(FREQS, start=1):
        path = OUT / f"D{slot:02d}_test_tone.wav"
        tone(path, freq)
        print(f"{path.relative_to(OUT.parent.parent.parent.parent.parent)}  {path.stat().st_size} bytes")
    print(f"\n{len(FREQS)} tones written to {OUT}")


if __name__ == "__main__":
    main()
