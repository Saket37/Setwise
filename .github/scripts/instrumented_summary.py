"""Instrumented test results (JUnit XML) as a Markdown table, for the workflow run's summary page."""
import glob
import sys
import xml.etree.ElementTree as ET


def main(folder):
    files = glob.glob(f"{folder}/**/*.xml", recursive=True)
    if not files:
        print("No instrumented test results (the build or the emulator failed first).")
        return
    rows, totals = [], [0, 0, 0]
    for path in files:
        root = ET.parse(path).getroot()
        suites = [root] if root.tag == "testsuite" else root.findall("testsuite")
        for suite in suites:
            counts = [int(suite.get(k, 0)) for k in ("tests", "failures", "skipped")]
            counts[1] += int(suite.get("errors", 0))
            totals = [a + b for a, b in zip(totals, counts)]
            rows.append((suite.get("name", "?").rsplit(".", 1)[-1], *counts))
    print(f"## Instrumented tests: {totals[0]} run, {totals[1]} failed, {totals[2]} skipped\n")
    print("| Class | Tests | Failed | Skipped |")
    print("|---|---:|---:|---:|")
    for name, tests, failed, skipped in sorted(rows):
        print(f"| `{name}` | {tests} | {failed} | {skipped} |")


main(sys.argv[1])
