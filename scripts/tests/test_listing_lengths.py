"""Contract tests for scripts/listing-lengths.py."""

from __future__ import annotations

import importlib.util
import subprocess
import sys
import unittest
from pathlib import Path

SCRIPT = Path(__file__).resolve().parents[1] / "listing-lengths.py"
spec = importlib.util.spec_from_file_location("listing_lengths", SCRIPT)
listing = importlib.util.module_from_spec(spec)
spec.loader.exec_module(listing)


def page(short: str, full: str = "Full.", name: str = "DriveCheckUA") -> str:
    return f"| App name | 30 | {name} |\n\n## en-US\n\n### Short description\n\n```\n{short}\n```\n\n### Full description\n\n```\n{full}\n```\n"


class ListingLengthsTest(unittest.TestCase):
    def test_fields_within_limits_pass(self):
        self.assertEqual([], listing.check(page("x" * 80, "y" * 4000)))

    def test_a_long_short_description_is_reported_with_its_locale(self):
        self.assertEqual(["en-US Short description: 81 > 80"], listing.check(page("x" * 81)))

    def test_a_long_full_description_is_reported(self):
        self.assertEqual(["en-US Full description: 4001 > 4000"], listing.check(page("x", "y" * 4001)))

    def test_a_car_screen_promise_is_reported(self):
        self.assertEqual(
            ["en-US Full description: promises Android Auto, which 1.0 does not ship"],
            listing.check(page("x", "Works in android auto.")),
        )

    def test_a_ukrainian_car_screen_promise_is_reported(self):
        self.assertEqual(
            ["en-US Short description: promises екрані авто, which 1.0 does not ship"],
            listing.check(page("Статус на екрані авто")),
        )

    def test_a_long_app_name_is_reported(self):
        self.assertEqual(["App name: 31 > 30"], listing.check(page("x", name="n" * 31)))

    def test_a_page_without_texts_fails(self):
        self.assertEqual(["no listing text found", "no app name found"], listing.check("## en-US\n"))

    def test_the_real_listing_fits(self):
        result = subprocess.run([sys.executable, str(SCRIPT)], capture_output=True, text=True)
        self.assertEqual(0, result.returncode, result.stdout)


if __name__ == "__main__":
    unittest.main()
