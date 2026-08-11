#!/usr/bin/env python3
"""Convert a centre's course-calendar .sql into the JSON asset the app seeds from.

The .sql file stays the source of truth — it is what the centre's calendar page
was transcribed into, and it is diffable against next year's schedule. The app
does not parse SQL: shipping a parser to read four columns would be a liability
for no gain, so this script flattens it once, at author time.

    python3 tools/export_courses_json.py \
        seed/courses-sudha-2026-2027.sql \
        app/src/main/assets/seed/courses.json

Deliberately strict. A silently-dropped row here is a course that never rings.
"""

from __future__ import annotations

import json
import re
import sys
from datetime import date
from pathlib import Path

# (type_id, 'YYYY-MM-DD') with an optional trailing comment.
ROW = re.compile(r"\(\s*(\d+)\s*,\s*'(\d{4}-\d{2}-\d{2})'\s*\)")


def parse(sql: str) -> list[dict]:
    rows = []
    for type_id, start in ROW.findall(sql):
        # Reject a date the calendar cannot mean, rather than seeding it.
        date.fromisoformat(start)
        rows.append({"course_type_id": int(type_id), "start_date": start})
    return rows


def main() -> int:
    if len(sys.argv) != 3:
        print(__doc__)
        return 2

    src, dest = Path(sys.argv[1]), Path(sys.argv[2])
    sql = src.read_text()
    rows = parse(sql)
    if not rows:
        print(f"error: no course rows found in {src}", file=sys.stderr)
        return 1

    starts = [r["start_date"] for r in rows]
    duplicates = {d for d in starts if starts.count(d) > 1}
    if duplicates:
        # Two courses on one arrival date is an overlap the appliance would
        # have to resolve every day. Catch it here, not on a tablet.
        print(f"error: duplicate start dates: {sorted(duplicates)}", file=sys.stderr)
        return 1

    rows.sort(key=lambda r: r["start_date"])
    payload = {
        "version": 1,
        "source": src.name,
        "centre": "Dhamma Sudha",
        "courses": rows,
    }
    dest.write_text(json.dumps(payload, indent=2) + "\n")
    print(f"{len(rows)} courses -> {dest} ({rows[0]['start_date']} … {rows[-1]['start_date']})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
