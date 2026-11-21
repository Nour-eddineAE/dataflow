#!/usr/bin/env python3
import argparse
import ast
import collections
import json
import os
import re
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ElementTree
from dataclasses import dataclass
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
UI = ROOT / "apps/dataflow-workspace/ui"
BASELINE = ROOT / "scripts/literals-baseline.json"
PYTHON_SOURCES = ("tower/src", "cli/src")
JAVA_SOURCES = ("apps", "libs")
SKIPPED_PARTS = frozenset({"target", "node_modules", "dist", "build", "test", ".venv"})
SKIPPED_SUFFIXES = (".test.ts", ".test.tsx")
RUFF = "ruff@0.17.3"
OBVIOUS_NUMBERS = frozenset({-1, 0, 1, 2, 100})
SHORTEST_STRING = 5
FEWEST_USES = 3
JAVA_STRING = re.compile(r'"((?:[^"\\\n]|\\.)*)"')
PYTHON_STRING = re.compile(r'"((?:[^"\\\n]|\\.)*)"|\'((?:[^\'\\\n]|\\.)*)\'')
TS_STRING = re.compile(r"'((?:[^'\\\n]|\\.)*)'")
IMPORT_LINE = re.compile(r"^\s*(import|from|package|export \*|export \{)\b.*$", re.MULTILINE)
NAMED_CONSTANT = re.compile(
    r"^\s*(?:(?:public|protected|private)\s+)?(?:static\s+final\s+\S+\s+|(?:export\s+)?const\s+)?"
    r"(?P<name>[A-Z][A-Z0-9_]+)(?::\s*\w+)?\s*=\s*(?P<value>[^;\n]+?);?\s*$",
    re.MULTILINE,
)
SIMPLE_VALUE = re.compile(r'-?[0-9][0-9_.*\s]*|"[^"]*"|\'[^\']*\'')
TUNABLE_VALUE = re.compile(r"[0-9][0-9_L.*\s<]*|Duration\.of\w+\(\d+\)")
LOCAL_SETTINGS = "dataflow-local/settings.env"
CHART_VALUES = "dataflow-helm-charts/charts/dataflow/values.yaml"
IS_SETTING = re.compile(r"[A-Z0-9_]+=")
SHARED_IMAGES = {
    "POSTGRES_VERSION": "postgres",
    "CLICKHOUSE_VERSION": "clickhouse/clickhouse-server",
    "RUSTFS_VERSION": "rustfs/rustfs",
    "KEYCLOAK_VERSION": "quay.io/keycloak/keycloak",
    "OAUTH2_PROXY_VERSION": "quay.io/oauth2-proxy/oauth2-proxy",
}
TUNABLE_NAME = re.compile(r"TIMEOUT|INTERVAL|POLL|EVERY|MAX_|LIMIT|BUFFER|BYTES|GRACE|PENDING|EARLY|FRESH|AGE$")


@dataclass(frozen=True)
class Finding:
    kind: str
    place: str
    text: str


def run(*command, cwd=ROOT):
    return subprocess.run(command, cwd=cwd, capture_output=True, text=True)


def relative(path):
    return os.path.relpath(path, ROOT)


def sources(roots, *suffixes):
    for root in roots:
        for path in sorted((ROOT / root).rglob("*")):
            parts = path.relative_to(ROOT).parts
            if path.suffix in suffixes and not SKIPPED_PARTS.intersection(parts) \
                    and not path.name.endswith(SKIPPED_SUFFIXES):
                yield path


def checkstyle():
    maven = os.environ.get("MAVEN", "mvn")
    result = run(maven, "-q", "checkstyle:check", "-Dcheckstyle.failOnViolation=false",
                 "-Dcheckstyle.failsOnError=false", "-Dcheckstyle.consoleOutput=false")
    if result.returncode:
        sys.exit(f"Checkstyle did not finish:\n{result.stdout[-2000:]}")
    kinds = {"MagicNumberCheck": "java-magic-number", "MultipleStringLiteralsCheck": "java-duplicate-string"}
    for report in sorted(ROOT.glob("**/target/checkstyle-result.xml")):
        for file in ElementTree.parse(report).getroot().findall("file"):
            for error in file.findall("error"):
                kind = kinds.get(error.get("source").rsplit(".", 1)[-1])
                if kind:
                    yield Finding(kind, f"{relative(file.get('name'))}:{error.get('line')}", error.get("message"))


def eslint():
    result = run("npx", "eslint", "src", "--format", "json", cwd=UI)
    if not result.stdout.startswith("["):
        sys.exit(f"ESLint did not finish:\n{result.stdout[-2000:]}{result.stderr[-2000:]}")
    for report in json.loads(result.stdout):
        lines = Path(report["filePath"]).read_text().splitlines()
        for message in report["messages"]:
            kind = message["message"].split(":")[0] if message["ruleId"] == "no-restricted-syntax" \
                else message["ruleId"].rsplit("/", 1)[-1]
            yield Finding(f"ui-{kind}", f"{relative(report['filePath'])}:{message['line']}",
                          lines[message["line"] - 1].strip())


def stylelint():
    result = run("npx", "stylelint", "src/**/*.css", "--formatter", "json", cwd=UI)
    output = result.stderr or result.stdout
    if not output.startswith("["):
        sys.exit(f"Stylelint did not finish:\n{output[-2000:]}")
    for report in json.loads(output):
        lines = Path(report["source"]).read_text().splitlines()
        for warning in report["warnings"]:
            line = lines[warning["line"] - 1].strip()
            kind = "css-hex-color" if warning["rule"] == "color-no-hex" else \
                f"css-px-{re.split(r'[-:]', line)[0]}"
            yield Finding(kind, f"{relative(report['source'])}:{warning['line']}", line)


def ruff():
    result = run("uvx", RUFF, "check", *PYTHON_SOURCES, "--output-format", "json", "--exit-zero")
    if result.returncode:
        sys.exit(f"Ruff did not finish:\n{result.stderr[-2000:]}")
    for finding in json.loads(result.stdout):
        yield Finding(f"python-{finding['code']}", f"{relative(finding['filename'])}:{finding['location']['row']}",
                      finding["message"])


def python_numbers():
    for path in sources(PYTHON_SOURCES, ".py"):
        tree = ast.parse(path.read_text())
        for node in ast.walk(tree):
            for child in ast.iter_child_nodes(node):
                child.parent = node
        for node in ast.walk(tree):
            if isinstance(node, ast.Constant) and type(node.value) in (int, float) \
                    and node.value not in OBVIOUS_NUMBERS and not named(node):
                yield Finding("python-magic-number", f"{relative(path)}:{node.lineno}", str(node.value))


def named(node):
    while hasattr(node, "parent"):
        node = node.parent
        if isinstance(node, ast.AnnAssign):
            return isinstance(node.target, ast.Name)
        if isinstance(node, ast.Assign):
            return all(isinstance(target, ast.Name) and target.id.isupper() for target in node.targets)
    return False


def without_imports(text):
    return IMPORT_LINE.sub("", text)


def strings(path):
    text = without_imports(path.read_text(errors="ignore"))
    pattern = {".java": JAVA_STRING, ".py": PYTHON_STRING}.get(path.suffix, TS_STRING)
    return [next(group for group in match.groups() if group is not None) for match in pattern.finditer(text)]


def shared_strings():
    languages = {"java": sources(JAVA_SOURCES, ".java"), "python": sources(PYTHON_SOURCES, ".py"),
                 "ui": sources(("apps/dataflow-workspace/ui/src",), ".ts", ".tsx")}
    for language, paths in languages.items():
        users = collections.defaultdict(list)
        for path in paths:
            uses = collections.Counter(strings(path))
            for value, count in uses.items():
                if len(value) >= SHORTEST_STRING:
                    users[value].append(relative(path))
                    if language != "java" and count >= FEWEST_USES:
                        yield Finding(f"{language}-duplicate-string", f"{relative(path)}", f"{value!r} {count} times")
        for value, files in sorted(users.items()):
            if len(files) >= FEWEST_USES:
                yield Finding(f"{language}-string-in-{FEWEST_USES}-files", f"{len(files)} files", repr(value))


def repeated_constants():
    users = collections.defaultdict(list)
    roots = (*JAVA_SOURCES, *PYTHON_SOURCES)
    for path in sources(roots, ".java", ".py", ".ts", ".tsx"):
        for match in NAMED_CONSTANT.finditer(path.read_text(errors="ignore")):
            value = match.group("value").strip()
            if SIMPLE_VALUE.fullmatch(value):
                users[(match.group("name"), value)].append(relative(path))
    for (name, value), files in sorted(users.items()):
        if len(files) > 1:
            yield Finding("constant-repeated", f"{len(files)} files", f"{name} = {value}: {', '.join(files)}")


def version_mismatches():
    settings, values = ROOT.parent / LOCAL_SETTINGS, ROOT.parent / CHART_VALUES
    if not settings.exists() or not values.exists():
        return
    pinned = dict(line.split("=", 1) for line in settings.read_text().splitlines() if IS_SETTING.match(line))
    chart = values.read_text()
    for variable, image in SHARED_IMAGES.items():
        tags = set(re.findall(rf"{re.escape(image)}:([\w.-]+)", chart))
        if variable in pinned and tags and tags != {pinned[variable]}:
            yield Finding("deployment-version-mismatch", image, f"{variable}={pinned[variable]}, chart has {sorted(tags)}")


def tunable_constants():
    for path in sources((*JAVA_SOURCES, *PYTHON_SOURCES), ".java", ".py"):
        for match in NAMED_CONSTANT.finditer(path.read_text(errors="ignore")):
            value = match.group("value")
            if TUNABLE_VALUE.fullmatch(value) and TUNABLE_NAME.search(match.group("name")):
                yield Finding("setting-candidate", relative(path), f"{match.group('name')} = {value}")


TOOLS = (checkstyle, eslint, stylelint, ruff)
SCANS = (python_numbers, shared_strings, repeated_constants, tunable_constants, version_mismatches)


def findings(scans_only=False):
    return [finding for finder in (SCANS if scans_only else (*TOOLS, *SCANS)) for finding in finder()]


def counts(found):
    return dict(sorted(collections.Counter(finding.kind for finding in found).items()))


def report(found):
    tally = counts(found)
    width = max(map(len, tally), default=0)
    for kind, count in tally.items():
        print(f"{kind:<{width}}  {count:>5}")
    print(f"{'total':<{width}}  {sum(tally.values()):>5}")


def check(found):
    allowed = json.loads(BASELINE.read_text())
    grew = {kind: (allowed.get(kind, 0), count) for kind, count in counts(found).items()
            if count > allowed.get(kind, 0)}
    for kind, (limit, count) in grew.items():
        print(f"{kind}: {count} found, baseline allows {limit}")
    return 1 if grew else 0


def main():
    parser = argparse.ArgumentParser(description="Find hardcoded literals in Java, TypeScript, CSS and Python.")
    parser.add_argument("--list", metavar="KIND", help="print each finding of a kind; a prefix or 'all' works too")
    parser.add_argument("--check", action="store_true", help="fail when a kind has more findings than the baseline")
    parser.add_argument("--update-baseline", action="store_true", help="write today's counts as the baseline")
    parser.add_argument("--scans-only", action="store_true",
                        help="skip Checkstyle, ESLint, Stylelint and Ruff, which fail the build on their own")
    arguments = parser.parse_args()
    found = findings(arguments.scans_only)
    if arguments.update_baseline:
        BASELINE.write_text(json.dumps(counts(found), indent=2) + "\n")
    if arguments.check:
        return check(found)
    if arguments.list:
        for finding in found:
            if arguments.list == "all" or finding.kind.startswith(arguments.list):
                print(f"{finding.kind}\t{finding.place}\t{finding.text}")
        return 0
    report(found)
    return 0


if __name__ == "__main__":
    sys.exit(main())
