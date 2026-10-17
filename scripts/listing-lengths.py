#!/usr/bin/env python3
"""Check the Google Play listing texts against Play's length limits.

Reads specs/005-release/play-listing.md: every fenced block under a "### Short description"
heading must fit 80 characters, and under "### Full description" 4000.
"""

import re
import sys
from pathlib import Path

LIMITS = {"Short description": 80, "Full description": 4000}
LISTING = Path(__file__).resolve().parent.parent / "specs" / "005-release" / "play-listing.md"


def check(text: str) -> list[str]:
    problems = []
    locale = None
    field = None
    found = 0
    for block in re.split(r"(^##+ .*$)", text, flags=re.MULTILINE):
        heading = re.match(r"^(##+) (.*)$", block)
        if heading:
            level, title = heading.groups()
            if level == "##":
                locale, field = title, None
            elif level == "###":
                field = title if title in LIMITS else None
            continue
        if field is None:
            continue
        for body in re.findall(r"^```\n(.*?)\n```$", block, flags=re.MULTILINE | re.DOTALL):
            found += 1
            if len(body) > LIMITS[field]:
                problems.append(f"{locale} {field}: {len(body)} > {LIMITS[field]}")
    if found == 0:
        problems.append("no listing text found")
    return problems


def main() -> int:
    problems = check(LISTING.read_text(encoding="utf-8"))
    for problem in problems:
        print(f"listing-lengths: {problem}")
    if not problems:
        print("listing-lengths: every field fits")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
