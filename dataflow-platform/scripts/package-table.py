import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PACKAGES = ROOT / "apps/dataflow-workspace/src/main/java/com/dataflow/workspace"
PAGE = ROOT / "docs/development/internals.md"
HEADING = "### Workspace packages"
ROW = re.compile(r"^\| `([a-z]+)` \|")


def documented():
    _, found, rest = PAGE.read_text().partition(HEADING)
    if not found:
        sys.exit(f"{PAGE.relative_to(ROOT)} has no '{HEADING}' section: put the package table back under that heading")
    section = rest.split("\n#", 1)[0]
    return {match[1] for match in map(ROW.match, section.splitlines()) if match}


def packages():
    return {path.name for path in PACKAGES.iterdir() if path.is_dir()}


def main():
    listed, actual = documented(), packages()
    page = PAGE.relative_to(ROOT)
    for name in sorted(actual - listed):
        print(f"Package {name} has no row under '{HEADING}' in {page}: add one saying what it does")
    for name in sorted(listed - actual):
        print(f"'{HEADING}' in {page} lists {name}, which is no longer a package: remove its row")
    return 1 if actual != listed else 0


if __name__ == "__main__":
    sys.exit(main())
