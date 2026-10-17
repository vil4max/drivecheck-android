#!/usr/bin/env python3
"""Requirement trace check: every approved requirement in specs/*/spec.md is cited by a host test.

A test cites a requirement when its name starts with the ID in underscored form
(`REQ_PROVIDER_001_reads_the_feed`). Exit 0 when the trace holds, 1 when it does not,
2 on a usage error. Rules: specs/001-skeleton-and-status-core/plan.md, "Requirement trace check".
"""

from __future__ import annotations

import argparse
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

REQ_HEADING = re.compile(r"^#{2,4}\s+(?P<id>REQ-[A-Z][A-Z0-9]*-\d{3})\b")
ANY_HEADING = re.compile(r"^#{1,6}\s")
STATUS_LINE = re.compile(r"^Status:\s*(?P<word>[A-Za-z]+)")
# Any mention in a test file, dashed or underscored, so a stale comment fails like a stale name.
ANY_REF = re.compile(r"(?<![A-Za-z0-9])REQ[-_](?P<area>[A-Z][A-Z0-9]*)[-_](?P<num>\d{3})(?![0-9])")
# Only IDs that open the name cite; one later in the name ("..._unlike_REQ_X_002") is prose.
LEADING_REFS = re.compile(r"^(?:REQ_[A-Z][A-Z0-9]*_\d{3}_)+")
LEADING_REF = re.compile(r"REQ_(?P<area>[A-Z][A-Z0-9]*)_(?P<num>\d{3})_")
# An annotation block, then modifiers, then the function name.
TEST_FUNCTION = re.compile(
    r"(?P<annotations>(?:@\w+(?:\([^)]*\))?\s+)+)"
    r"(?:(?:public|internal|private|protected|open|override|suspend)\s+)*"
    r"fun\s+(?P<name>[A-Za-z_][A-Za-z0-9_]*)\s*\("
)
TEST_ANNOTATION = re.compile(r"@Test\b")
IGNORE_ANNOTATION = re.compile(r"@Ignore\b")
TARGET_SUFFIX = re.compile(r"(\(\))?(\[[^\]]*\])?$")

MODULES = ("shared", "androidApp")
# Device tests need an emulator and are not part of the gate, so they never cover a requirement.
DEVICE_SOURCE_SETS = {"androidTest", "androidDeviceTest", "androidInstrumentedTest"}


def requirements(root: Path) -> tuple[dict[str, str], list[str]]:
    """Map each defined ID to its status word; also return errors for duplicate definitions."""
    found: dict[str, str] = {}
    where: dict[str, Path] = {}
    errors: list[str] = []
    for spec in sorted(root.glob("specs/*/spec.md")):
        current: str | None = None
        for line in spec.read_text(encoding="utf-8").splitlines():
            heading = REQ_HEADING.match(line)
            if heading:
                current = heading.group("id")
                if current in where:
                    errors.append(f"{current} is defined twice: {where[current]} and {spec}")
                where[current] = spec
                found[current] = ""
                continue
            if ANY_HEADING.match(line):
                current = None
                continue
            status = STATUS_LINE.match(line)
            if current and status and not found[current]:
                found[current] = status.group("word").lower()
    return found, errors


def test_files(root: Path) -> list[Path]:
    files: list[Path] = []
    for module in MODULES:
        for source_set in sorted((root / module / "src").glob("*")):
            name = source_set.name
            if not source_set.is_dir() or name in DEVICE_SOURCE_SETS:
                continue
            if name == "test" or name.endswith("Test"):
                files.extend(sorted(source_set.rglob("*.kt")))
    return files


def citing_tests(files: list[Path]) -> tuple[dict[str, set[str]], dict[str, set[Path]]]:
    """Map each requirement to the test functions that cite it, and every mentioned ID to its files."""
    cited: dict[str, set[str]] = {}
    mentioned: dict[str, set[Path]] = {}
    for path in files:
        text = path.read_text(encoding="utf-8")
        for ref in ANY_REF.finditer(text):
            mentioned.setdefault(f"REQ-{ref.group('area')}-{ref.group('num')}", set()).add(path)
        for function in TEST_FUNCTION.finditer(text):
            annotations = function.group("annotations")
            if not TEST_ANNOTATION.search(annotations) or IGNORE_ANNOTATION.search(annotations):
                continue
            name = function.group("name")
            prefix = LEADING_REFS.match(name)
            if not prefix:
                continue
            for ref in LEADING_REF.finditer(prefix.group(0)):
                cited.setdefault(f"REQ-{ref.group('area')}-{ref.group('num')}", set()).add(name)
    return cited, mentioned


def passed_tests(results_root: Path) -> set[str] | None:
    """Names of test cases that passed in every run; None when no report exists."""
    reports = sorted(results_root.glob("**/build/test-results/**/TEST-*.xml"))
    if not reports:
        return None
    passed: set[str] = set()
    failed: set[str] = set()
    for report in reports:
        for case in ET.parse(report).getroot().iter("testcase"):
            name = TARGET_SUFFIX.sub("", case.get("name", ""))
            if case.find("skipped") is not None:
                continue
            if case.find("failure") is not None or case.find("error") is not None:
                failed.add(name)
            else:
                passed.add(name)
    return passed - failed


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument(
        "--results",
        type=Path,
        help="folder holding Gradle JUnit XML reports; a citing test must also have run and passed",
    )
    args = parser.parse_args(argv)

    defined, errors = requirements(args.root)
    if not defined:
        print(f"spec-trace: no requirement found under {args.root / 'specs'}", file=sys.stderr)
        return 2

    cited, mentioned = citing_tests(test_files(args.root))
    if args.results is not None:
        passed = passed_tests(args.results)
        if passed is None:
            print(f"spec-trace: no JUnit XML report under {args.results}", file=sys.stderr)
            return 2
        cited = {req: names & passed for req, names in cited.items()}

    for req_id in sorted(mentioned.keys() - defined.keys()):
        files = ", ".join(str(path.relative_to(args.root)) for path in sorted(mentioned[req_id]))
        errors.append(f"{req_id} is cited by a test but defined in no spec: {files}")

    approved = sorted(req for req, status in defined.items() if status == "approved")
    for req_id in approved:
        if not cited.get(req_id):
            how = "a test that ran and passed" if args.results is not None else "a test"
            errors.append(f"{req_id} is approved but not cited by {how}")

    if errors:
        for error in errors:
            print(f"spec-trace: {error}")
        return 1
    print(f"spec-trace: {len(approved)} approved requirements, each cited by a test")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
