import tempfile
import unittest
from pathlib import Path
import xml.etree.ElementTree as ET

from verify_unit_reports import VARIANTS, verify


class UnitReportGateTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        for variant in VARIANTS:
            self.write(variant)

    def write(self, variant, xml=None):
        path = self.root / variant / "TEST-example.xml"
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(xml if xml is not None else
                        '<testsuite tests="1" failures="0" errors="0" skipped="0">'
                        '<testcase name="example"/></testsuite>', encoding="utf-8")
        return path

    def test_eight_variants_are_required(self):
        self.assertEqual(8, len(VARIANTS))
        self.assertEqual(dict.fromkeys(VARIANTS, 1), verify(self.root))

    def test_missing_variant_fails(self):
        self.write(VARIANTS[-1]).unlink()
        with self.assertRaisesRegex(ValueError, "missing test reports"):
            verify(self.root)

    def test_empty_suite_fails(self):
        self.write(VARIANTS[0], '<testsuite tests="0" failures="0" errors="0" skipped="0"/>')
        with self.assertRaisesRegex(ValueError, "no executed tests"):
            verify(self.root)

    def test_failed_errored_or_skipped_suite_fails(self):
        for key in ("failures", "errors", "skipped"):
            with self.subTest(key=key):
                attrs = " ".join(f'{k}="{int(k == key)}"' for k in ("failures", "errors", "skipped"))
                self.write(VARIANTS[0], f'<testsuite tests="1" {attrs}><testcase/></testsuite>')
                with self.assertRaises(ValueError):
                    verify(self.root)

    def test_inconsistent_count_fails(self):
        self.write(VARIANTS[0], '<testsuite tests="2" failures="0" errors="0" skipped="0"><testcase/></testsuite>')
        with self.assertRaisesRegex(ValueError, "inconsistent"):
            verify(self.root)

    def test_nested_failure_cannot_be_hidden_by_zero_summary(self):
        self.write(VARIANTS[0], '<testsuite tests="1" failures="0" errors="0" skipped="0"><testcase><failure/></testcase></testsuite>')
        with self.assertRaisesRegex(ValueError, "unsuccessful"):
            verify(self.root)

    def test_malformed_report_fails(self):
        self.write(VARIANTS[0], "<testsuite")
        with self.assertRaises(ET.ParseError):
            verify(self.root)


if __name__ == "__main__":
    unittest.main()
