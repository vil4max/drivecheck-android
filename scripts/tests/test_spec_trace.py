"""Contract tests for scripts/spec-trace.py, run with `python3 -m unittest discover -s scripts/tests`."""

from __future__ import annotations

import subprocess
import sys
import tempfile
import textwrap
import unittest
from pathlib import Path

SCRIPT = Path(__file__).resolve().parents[1] / "spec-trace.py"


def requirement(req_id: str, status: str = "approved") -> str:
    return f"### {req_id} — Title\n\nStatus: {status}\n\nGiven a\\\nWhen b\\\nThen c\n"


def kotlin_test(*names: str, annotations: str = "@Test") -> str:
    functions = "\n".join(f"    {annotations}\n    fun {name}() {{}}\n" for name in names)
    return f"package app\n\nimport kotlin.test.Test\n\nclass SomeTest {{\n{functions}}}\n"


def junit_xml(cases: dict[str, str]) -> str:
    """cases maps a test case name to passed, failed or skipped."""
    body = []
    for name, outcome in cases.items():
        inner = {"passed": "", "failed": "<failure message='x'/>", "skipped": "<skipped/>"}[outcome]
        body.append(f'  <testcase name="{name}" classname="app.SomeTest" time="0.01">{inner}</testcase>')
    return '<?xml version="1.0"?>\n<testsuite name="app.SomeTest">\n' + "\n".join(body) + "\n</testsuite>\n"


class SpecTraceTest(unittest.TestCase):
    def setUp(self) -> None:
        self._tmp = tempfile.TemporaryDirectory()
        self.root = Path(self._tmp.name)

    def tearDown(self) -> None:
        self._tmp.cleanup()

    def write(self, relative: str, text: str) -> None:
        path = self.root / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(textwrap.dedent(text), encoding="utf-8")

    def spec(self, text: str, feature: str = "001-first") -> None:
        self.write(f"specs/{feature}/spec.md", "# Feature\n\nStatus: approved by the owner\n\n" + text)

    def run_trace(self, *extra: str) -> subprocess.CompletedProcess[str]:
        return subprocess.run(
            [sys.executable, "-I", str(SCRIPT), "--root", str(self.root), *extra],
            capture_output=True,
            text=True,
            check=False,
        )

    def assert_fails_naming(self, result: subprocess.CompletedProcess[str], req_id: str) -> None:
        self.assertEqual(result.returncode, 1, result.stdout + result.stderr)
        self.assertIn(req_id, result.stdout + result.stderr)

    def assert_passes(self, result: subprocess.CompletedProcess[str]) -> None:
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)

    # Definitions

    def test_an_approved_requirement_without_a_citing_test_fails(self) -> None:
        self.spec(requirement("REQ-AREA-001"))
        self.assert_fails_naming(self.run_trace(), "REQ-AREA-001")

    def test_a_cited_approved_requirement_passes(self) -> None:
        self.spec(requirement("REQ-AREA-001"))
        self.write("shared/src/commonTest/kotlin/app/SomeTest.kt", kotlin_test("REQ_AREA_001_does_a_thing"))
        self.assert_passes(self.run_trace())

    def test_a_proposed_requirement_need_not_be_cited(self) -> None:
        self.spec(requirement("REQ-AREA-001", status="proposed"))
        self.assert_passes(self.run_trace())

    def test_a_status_with_trailing_text_still_counts_as_approved(self) -> None:
        self.spec(requirement("REQ-AREA-001", status="approved — partial, see below"))
        self.assert_fails_naming(self.run_trace(), "REQ-AREA-001")

    def test_the_file_header_status_is_not_a_requirement_status(self) -> None:
        self.spec(requirement("REQ-AREA-001", status="proposed"))
        self.assert_passes(self.run_trace())

    def test_a_duplicate_definition_fails(self) -> None:
        self.spec(requirement("REQ-AREA-001"), feature="001-first")
        self.spec(requirement("REQ-AREA-001"), feature="002-second")
        self.write("shared/src/commonTest/kotlin/app/SomeTest.kt", kotlin_test("REQ_AREA_001_does_a_thing"))
        self.assert_fails_naming(self.run_trace(), "REQ-AREA-001")

    def test_no_specs_is_a_usage_error(self) -> None:
        self.assertEqual(self.run_trace().returncode, 2)

    # Citations

    def test_a_test_naming_an_undefined_id_fails(self) -> None:
        self.spec(requirement("REQ-AREA-001"))
        self.write(
            "shared/src/commonTest/kotlin/app/SomeTest.kt",
            kotlin_test("REQ_AREA_001_does_a_thing", "REQ_AREA_002_does_another_thing"),
        )
        self.assert_fails_naming(self.run_trace(), "REQ-AREA-002")

    def test_an_undefined_id_in_a_test_comment_fails(self) -> None:
        self.spec(requirement("REQ-AREA-001"))
        self.write(
            "shared/src/commonTest/kotlin/app/SomeTest.kt",
            "// Also pins REQ-AREA-009.\n" + kotlin_test("REQ_AREA_001_does_a_thing"),
        )
        self.assert_fails_naming(self.run_trace(), "REQ-AREA-009")

    def test_one_name_can_cite_two_requirements(self) -> None:
        self.spec(requirement("REQ-AREA-001") + requirement("REQ-OTHER-002"))
        self.write(
            "shared/src/commonTest/kotlin/app/SomeTest.kt",
            kotlin_test("REQ_AREA_001_REQ_OTHER_002_does_both"),
        )
        self.assert_passes(self.run_trace())

    def test_an_id_later_in_the_name_is_not_a_citation(self) -> None:
        self.spec(requirement("REQ-AREA-001") + requirement("REQ-OTHER-002"))
        self.write(
            "shared/src/commonTest/kotlin/app/SomeTest.kt",
            kotlin_test("REQ_AREA_001_unlike_REQ_OTHER_002"),
        )
        self.assert_fails_naming(self.run_trace(), "REQ-OTHER-002")

    def test_an_ignored_test_does_not_cover(self) -> None:
        self.spec(requirement("REQ-AREA-001"))
        self.write(
            "shared/src/commonTest/kotlin/app/SomeTest.kt",
            kotlin_test("REQ_AREA_001_does_a_thing", annotations="@Ignore\n    @Test"),
        )
        self.assert_fails_naming(self.run_trace(), "REQ-AREA-001")

    def test_a_function_without_the_test_annotation_does_not_cover(self) -> None:
        self.spec(requirement("REQ-AREA-001"))
        self.write(
            "shared/src/commonTest/kotlin/app/SomeTest.kt",
            kotlin_test("REQ_AREA_001_helper", annotations="@Suppress(\"unused\")"),
        )
        self.assert_fails_naming(self.run_trace(), "REQ-AREA-001")

    def test_host_tests_of_both_modules_count(self) -> None:
        self.spec(requirement("REQ-AREA-001") + requirement("REQ-AREA-002"))
        self.write("shared/src/androidHostTest/kotlin/app/SomeTest.kt", kotlin_test("REQ_AREA_001_on_the_jvm"))
        self.write("androidApp/src/test/kotlin/app/SomeTest.kt", kotlin_test("REQ_AREA_002_in_the_app"))
        self.assert_passes(self.run_trace())

    def test_instrumented_tests_do_not_count(self) -> None:
        self.spec(requirement("REQ-AREA-001"))
        self.write("androidApp/src/androidTest/kotlin/app/SomeTest.kt", kotlin_test("REQ_AREA_001_on_a_device"))
        self.assert_fails_naming(self.run_trace(), "REQ-AREA-001")

    # Results

    def test_with_results_a_passed_citing_test_counts(self) -> None:
        self.spec(requirement("REQ-AREA-001"))
        self.write("shared/src/commonTest/kotlin/app/SomeTest.kt", kotlin_test("REQ_AREA_001_does_a_thing"))
        self.write(
            "shared/build/test-results/testAndroidHostTest/TEST-app.SomeTest.xml",
            junit_xml({"REQ_AREA_001_does_a_thing": "passed"}),
        )
        self.assert_passes(self.run_trace("--results", str(self.root)))

    def test_with_results_a_target_suffix_in_the_case_name_still_matches(self) -> None:
        self.spec(requirement("REQ-AREA-001"))
        self.write("shared/src/commonTest/kotlin/app/SomeTest.kt", kotlin_test("REQ_AREA_001_does_a_thing"))
        self.write(
            "shared/build/test-results/testAndroidHostTest/TEST-app.SomeTest.xml",
            junit_xml({"REQ_AREA_001_does_a_thing[android]": "passed"}),
        )
        self.assert_passes(self.run_trace("--results", str(self.root)))

    def test_with_results_a_skipped_citing_test_does_not_count(self) -> None:
        self.spec(requirement("REQ-AREA-001"))
        self.write("shared/src/commonTest/kotlin/app/SomeTest.kt", kotlin_test("REQ_AREA_001_does_a_thing"))
        self.write(
            "shared/build/test-results/testAndroidHostTest/TEST-app.SomeTest.xml",
            junit_xml({"REQ_AREA_001_does_a_thing": "skipped"}),
        )
        self.assert_fails_naming(self.run_trace("--results", str(self.root)), "REQ-AREA-001")

    def test_with_results_a_failed_citing_test_does_not_count(self) -> None:
        self.spec(requirement("REQ-AREA-001"))
        self.write("shared/src/commonTest/kotlin/app/SomeTest.kt", kotlin_test("REQ_AREA_001_does_a_thing"))
        self.write(
            "shared/build/test-results/testAndroidHostTest/TEST-app.SomeTest.xml",
            junit_xml({"REQ_AREA_001_does_a_thing": "failed"}),
        )
        self.assert_fails_naming(self.run_trace("--results", str(self.root)), "REQ-AREA-001")

    def test_with_results_a_citing_test_that_never_ran_does_not_count(self) -> None:
        self.spec(requirement("REQ-AREA-001"))
        self.write("shared/src/commonTest/kotlin/app/SomeTest.kt", kotlin_test("REQ_AREA_001_does_a_thing"))
        self.write(
            "shared/build/test-results/testAndroidHostTest/TEST-app.SomeTest.xml",
            junit_xml({"unrelated_test": "passed"}),
        )
        self.assert_fails_naming(self.run_trace("--results", str(self.root)), "REQ-AREA-001")

    def test_with_results_and_no_reports_is_a_usage_error(self) -> None:
        self.spec(requirement("REQ-AREA-001"))
        self.write("shared/src/commonTest/kotlin/app/SomeTest.kt", kotlin_test("REQ_AREA_001_does_a_thing"))
        self.assertEqual(self.run_trace("--results", str(self.root)).returncode, 2)


if __name__ == "__main__":
    unittest.main()
