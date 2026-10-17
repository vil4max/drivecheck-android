#!/usr/bin/env python3
"""Check the Google Play listing texts against Play's length limits and the 1.0 surface list.

Reads specs/005-release/play-listing.md: the "App name" row must fit 30 characters, every fenced
block under a "### Short description" heading 80, and under "### Full description" 4000.
"""

import re
import sys
from pathlib import Path

LIMITS = {"Short description": 80, "Full description": 4000}
# The owner's decision of 2026-10-17 (spec 005): no car screen in 1.0, so no listing text names one.
CAR_SURFACES = [
    "Android Auto", "CarPlay", "Automotive",
    "екрані авто", "в авто", "автомобіл", "машин",
    "экране авто", "автомобил",
]
TITLE_LIMIT = 30
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
            for surface in CAR_SURFACES:
                if surface.lower() in body.lower():
                    problems.append(f"{locale} {field}: promises {surface}, which 1.0 does not ship")
    if found == 0:
        problems.append("no listing text found")
    title = re.search(r"^\| App name \| \d+ \| (.*?) \|$", text, flags=re.MULTILINE)
    if title is None:
        problems.append("no app name found")
    elif len(title.group(1)) > TITLE_LIMIT:
        problems.append(f"App name: {len(title.group(1))} > {TITLE_LIMIT}")
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
