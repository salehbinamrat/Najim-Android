"""Fail closed when any required JVM test variant lacks successful XML evidence."""
import sys
from pathlib import Path
import xml.etree.ElementTree as ET

VARIANTS = tuple(
    f"test{edition}{arch}{build}UnitTest"
    for edition in ("Full", "Lite")
    for arch in ("Arm32", "Arm64")
    for build in ("Debug", "Release")
)


def verify(root):
    totals = {}
    for variant in VARIANTS:
        files = sorted((root / variant).glob("TEST-*.xml"))
        if not files:
            raise ValueError(f"{variant}: missing test reports")
        total = 0
        for path in files:
            suite = ET.parse(path).getroot()
            if suite.tag != "testsuite":
                raise ValueError(f"{variant}: unexpected XML root")
            count = int(suite.attrib["tests"])
            cases = suite.findall("testcase")
            if count < 0 or count != len(cases):
                raise ValueError(f"{variant}: inconsistent test count")
            if any(int(suite.attrib[key]) != 0 for key in ("failures", "errors", "skipped")):
                raise ValueError(f"{variant}: failed, errored or skipped tests")
            if any(case.find(tag) is not None for case in cases
                   for tag in ("failure", "error", "skipped")):
                raise ValueError(f"{variant}: unsuccessful test case")
            total += count
        if total == 0:
            raise ValueError(f"{variant}: no executed tests")
        totals[variant] = total
    return totals


def main():
    if len(sys.argv) != 2:
        print("Usage: verify_unit_reports.py TEST_RESULTS_DIR", file=sys.stderr)
        return 2
    try:
        totals = verify(Path(sys.argv[1]))
    except (OSError, ET.ParseError, ValueError, KeyError) as exc:
        print(f"Unit report verification failed: {exc}", file=sys.stderr)
        return 1
    for variant, count in totals.items():
        print(f"{variant}: {count} passed")
    print("All eight JVM test variants have passing XML evidence.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
