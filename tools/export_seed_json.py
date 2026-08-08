#!/usr/bin/env python3
"""Convert ng/seed/seed.sql (or seed/seed.sql) into seed/seed.json for the Android app."""
from __future__ import annotations

import argparse
import json
import re
from pathlib import Path


def convert(sql_text: str) -> dict:
    course_types = []
    events = []
    for line in sql_text.splitlines():
        m = re.match(
            r"INSERT INTO course_types \(id, name, total_days, anapana_days\) "
            r"VALUES \((\d+), '([^']*)', (\d+), (\d+)\);",
            line,
        )
        if m:
            course_types.append(
                {
                    "id": int(m.group(1)),
                    "name": m.group(2),
                    "total_days": int(m.group(3)),
                    "anapana_days": int(m.group(4)),
                }
            )
            continue
        m = re.match(
            r"INSERT INTO schedule_events \(course_type_id, day_no, time_local, repeats\) "
            r"VALUES \((NULL|\d+), (NULL|-?\d+), '([^']+)', (\d+)\);",
            line,
        )
        if m:
            events.append(
                {
                    "course_type_id": None if m.group(1) == "NULL" else int(m.group(1)),
                    "day_no": None if m.group(2) == "NULL" else int(m.group(2)),
                    "time_local": m.group(3),
                    "repeats": int(m.group(4)),
                }
            )
    return {
        "version": 1,
        "source": "seed.sql",
        "settings_defaults": {
            "enabled": "1",
            "gong_enabled": "1",
            "doha_enabled": "1",
            "relay_enabled": "0",
            "gong_track": "ting",
            "gong_volume": "90",
            "gong_gap_seconds": "4",
            "doha_time": "06:37",
            "doha_volume": "75",
            "doha_strategy": "legacy_modular",
            "no_course_doha": "random",
            "active_course_id": "",
        },
        "course_types": course_types,
        "schedule_events": events,
    }


def main() -> None:
    root = Path(__file__).resolve().parents[1]
    p = argparse.ArgumentParser()
    p.add_argument(
        "--sql",
        type=Path,
        default=root / "seed" / "seed.sql",
        help="Path to seed.sql",
    )
    p.add_argument(
        "--out",
        type=Path,
        default=root / "seed" / "seed.json",
    )
    p.add_argument(
        "--also-assets",
        action="store_true",
        help="Also write app/src/main/assets/seed/seed.json",
    )
    args = p.parse_args()
    data = convert(args.sql.read_text())
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(data, indent=2) + "\n")
    print(f"wrote {args.out} types={len(data['course_types'])} events={len(data['schedule_events'])}")
    if args.also_assets:
        assets = root / "app" / "src" / "main" / "assets" / "seed" / "seed.json"
        assets.parent.mkdir(parents=True, exist_ok=True)
        assets.write_text(json.dumps(data, indent=2) + "\n")
        print(f"wrote {assets}")


if __name__ == "__main__":
    main()
