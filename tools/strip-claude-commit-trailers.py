#!/usr/bin/env python3
"""git filter-branch --msg-filter helper: drop Claude co-author / session trailers."""
import sys

text = sys.stdin.read()
out = []
for line in text.splitlines():
    s = line.strip()
    lower = s.lower()
    if lower.startswith("co-authored-by:") and (
        "claude" in lower or "anthropic.com" in lower or "noreply@anthropic" in lower
    ):
        continue
    if lower.startswith("claude-session:"):
        continue
    out.append(line.rstrip())

while out and out[-1] == "":
    out.pop()

cleaned = []
blank_run = 0
for line in out:
    if line == "":
        blank_run += 1
        if blank_run <= 2:
            cleaned.append(line)
    else:
        blank_run = 0
        cleaned.append(line)

sys.stdout.write("\n".join(cleaned))
if cleaned:
    sys.stdout.write("\n")
