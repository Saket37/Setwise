"""Kover's XML report as a Markdown table, for the workflow run's summary page."""
import sys
import xml.etree.ElementTree as ET


def percent(element, kind):
    for counter in element.findall("counter"):
        if counter.get("type") == kind:
            covered, missed = int(counter.get("covered")), int(counter.get("missed"))
            return 100 * covered / (covered + missed) if covered + missed else 100.0, missed
    return None, 0


def main(path):
    try:
        report = ET.parse(path).getroot()
    except (OSError, ET.ParseError):
        print("No coverage report (the build or tests failed first).")
        return
    lines, _ = percent(report, "LINE")
    branches, _ = percent(report, "BRANCH")
    print(f"## Coverage: {lines:.1f}% of lines, {branches:.1f}% of branches\n")
    print("<details><summary>By package (least covered first)</summary>\n")
    print("| Package | Lines | Missed lines |")
    print("|---|---:|---:|")
    rows = []
    for package in report.findall("package"):
        value, missed = percent(package, "LINE")
        if value is not None:
            rows.append((value, missed, package.get("name").replace("/", ".")))
    for value, missed, name in sorted(rows):
        print(f"| `{name}` | {value:.1f}% | {missed} |")
    print("\n</details>")


main(sys.argv[1])
