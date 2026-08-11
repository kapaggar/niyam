#!/usr/bin/env python3
"""Turn dhamma.org's centre directory HTML into the app's centre list.

    curl -s https://www.dhamma.org/en/locations/directory > /tmp/directory.html
    python3 tools/export_centres_json.py /tmp/directory.html \
        app/src/main/assets/seed/centres.json

Why a script and not a hand-written list: there are 150+ Indian centres and the
set changes every year as new ones open. Transcribing them once guarantees the
file is wrong by next season; re-running this against the live directory takes a
second and is diffable.

What it extracts, per centre:

    subdomain   "sudha"                      — the stable key
    name        "Dhamma Sudha"               — what staff recognise
    place       "Hastinapur"                 — disambiguates same-named centres
    region      "Uttar Pradesh"
    schedule    "/en/schedules/schsudha"     — where the calendar lives

Non-centres ("Vipassana Courses in Nalanda") are kept but flagged, because they
run courses too and a tablet could plausibly live at one.

This does NOT fetch schedules. See docs for the runtime design — the schedule
pages are HTML meant for humans and parsing them on-device is a separate
decision with its own failure modes.
"""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path

# Each centre is a <div class="location" ...> carrying data-* attributes, then a
# schedule link of the form /en/schedules/sch<subdomain> (or /noncenter/<sub>).
LOCATION = re.compile(
    r'<div id="location-\d+"[^>]*?data-subdomain="(?P<sub>[^"]+)"[^>]*?'
    r'class="(?P<cls>[^"]*)"[^>]*?data-name="(?P<dataname>[^"]*)"',
    re.S,
)
DISPLAY = re.compile(r'<span class="display-name">\s*(?P<v>.*?)\s*</span>', re.S)
DHAMMA = re.compile(r'<span class="dhamma-name">\s*(?P<v>.*?)\s*</span>', re.S)
SCHEDULE = re.compile(r'href="(/en/schedules/(?:noncenter/)?[^"]+)"')
REGION = re.compile(r'<span class="name region-name">\s*(?P<v>.*?)\s*</span>', re.S)

TAGS = re.compile(r"<[^>]+>")


def clean(raw: str) -> str:
    text = TAGS.sub("", raw)
    for entity, char in (("&amp;", "&"), ("&nbsp;", " "), ("&#39;", "'"), ("&quot;", '"')):
        text = text.replace(entity, char)
    return " ".join(text.split())


def parse(html: str) -> list[dict]:
    # Region headers appear before the centres they contain, so walking the
    # document in order and remembering the last one attributes correctly.
    marks: list[tuple[int, str]] = [
        (m.start(), clean(m.group("v"))) for m in REGION.finditer(html)
    ]

    def region_at(pos: int) -> str:
        name = ""
        for start, value in marks:
            if start > pos:
                break
            name = value
        return name

    out: list[dict] = []
    seen: set[str] = set()
    for m in LOCATION.finditer(html):
        sub = m.group("sub")
        if sub in seen:
            continue
        seen.add(sub)

        # The block for this centre ends where the next location begins.
        block = html[m.start(): m.start() + 4000]
        display = DISPLAY.search(block)
        dhamma = DHAMMA.search(block)
        schedule = SCHEDULE.search(block)

        out.append(
            {
                "subdomain": sub,
                "name": clean(dhamma.group("v")) if dhamma else clean(m.group("dataname")),
                "place": clean(display.group("v")) if display else "",
                "region": region_at(m.start()),
                "schedule": schedule.group(1) if schedule else "",
                # A "non-centre" still runs courses; it just is not a permanent
                # centre. Flagged rather than dropped.
                "centre": "noncenter" not in m.group("cls"),
            }
        )
    return out


def main() -> int:
    if len(sys.argv) != 3:
        print(__doc__)
        return 2

    src, dest = Path(sys.argv[1]), Path(sys.argv[2])
    rows = parse(src.read_text(errors="ignore"))
    if not rows:
        print(f"error: no centres found in {src} — did the markup change?", file=sys.stderr)
        return 1

    missing = [r["subdomain"] for r in rows if not r["schedule"]]
    if missing:
        # Loud, not silent: a centre without a schedule URL is one a tablet
        # could be pointed at and then never get a calendar for.
        print(f"warning: {len(missing)} without a schedule link: {missing[:5]}", file=sys.stderr)

    rows.sort(key=lambda r: (r["region"], r["name"]))
    dest.write_text(
        json.dumps({"version": 1, "source": "dhamma.org directory", "centres": rows}, indent=2)
        + "\n"
    )
    print(f"{len(rows)} centres -> {dest}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
