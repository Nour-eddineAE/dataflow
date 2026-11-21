import argparse
import json
import re
import sys
import textwrap
import yaml
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
APPS = [("Workspace", "apps/dataflow-workspace", "dataflow.workspace."),
        ("Engine", "apps/dataflow-engine", "dataflow.")]
PLACEHOLDER = re.compile(r"\$\{([^}:]+)(?::([^}]*))?\}")
MAPS = ("dataflow.spark.config", "dataflow.spark.stores", "dataflow.spark.checkpoint-managers",
        "dataflow.workspace.dashboards")
OUTPUT = ROOT / "docs/settings.md"
LINE_LENGTH = 120
HEADER = ("Setting", "Environment variable", "Default", "Meaning")


def flatten(node, prefix=""):
    if isinstance(node, dict):
        for key, value in node.items():
            yield from flatten(value, f"{prefix}{key}.")
    else:
        yield prefix[:-1], node


def load(path):
    documents = [document for document in yaml.safe_load_all(path.read_text()) if document] if path.exists() else []
    values = {}
    for document in documents:
        values.update(dict(flatten(document)))
    return values


def text(value):
    if value is None:
        return ""
    return str(value).lower() if isinstance(value, bool) else str(value)


def reference(value):
    match = PLACEHOLDER.fullmatch(str(value)) if value is not None else None
    return match.group(1) if match and match.group(1).startswith("env.") else None


def default_of(key, base):
    value = base.get(key, "")
    return default_of(reference(value), base) if reference(value) else text(value)


def resolve(value, base, docker):
    key = reference(value)
    if not key:
        return "", text(value)
    variable = PLACEHOLDER.match(str(docker.get(key, "")))
    return (variable.group(1) if variable else ""), default_of(key, base)


def aligned(rows):
    widths = [max(map(len, column)) for column in zip(*rows, strict=True)]
    cells = [" | ".join(cell.ljust(width) for cell, width in zip(row, widths, strict=True)) for row in rows]
    rule = "|".join(":" + "-" * (width + 1) for width in widths)
    return [f"| {cells[0]} |", f"|{rule}|", *(f"| {cell} |" for cell in cells[1:])]


def paragraph(prose):
    return textwrap.wrap(prose, LINE_LENGTH, break_long_words=False, break_on_hyphens=False)


def table(title, folder, prefix):
    app = ROOT / folder
    resources = app / "src/main/resources"
    described = json.loads((resources / "META-INF/additional-spring-configuration-metadata.json").read_text())[
        "properties"]
    base = load(resources / "application.yml")
    docker = load(resources / "application-docker.yml")
    rows = [HEADER]
    for entry in described:
        name = entry["name"]
        if name in MAPS:
            variable, default = "", "map, see the yml files"
        else:
            variable, default = resolve(base.get(name), base, docker)
        shown = f"`{default}`" if default not in ("", "map, see the yml files") else (default or "empty")
        rows.append((f"`{name.removeprefix(prefix) if prefix != 'dataflow.' else name}`",
                     f"`{variable}`" if variable else "", shown, entry["description"]))
    return [f"## {title}", "", *aligned(rows)]


def page():
    lines = [
        "# Settings",
        "",
        *paragraph("Every setting of the Workspace and the engine, with the environment variable that sets it on Docker and Kubernetes, its default and what it changes. Generated from the descriptions in each app's `META-INF/additional-spring-configuration-metadata.json` and the `application*.yml` files by `scripts/settings-reference.py`; run it again after adding a setting."),
        "",
        *paragraph("Settings are layered, broadest first: defaults in `application.yml`, then environment variables mapped in `application-docker.yml` (also read by the `k8s` profile), then a dataset's own `compute:` section for Spark. Beans only bind `dataflow.*`; `env.*` names exist inside the yml files to carry environment variables to them. Settings → Configuration in the Workspace shows the values in effect, with secrets masked."),
        "",
    ]
    for title, folder, prefix in APPS:
        lines += table(title, folder, prefix) + [""]
    return "\n".join(lines)


def main():
    parser = argparse.ArgumentParser(description="Write docs/settings.md from each app's setting descriptions.")
    parser.add_argument("--check", action="store_true", help="fail when docs/settings.md is out of date")
    check = parser.parse_args().check
    content = page()
    name = OUTPUT.relative_to(ROOT)
    if check:
        if OUTPUT.read_text(encoding="utf-8") != content:
            return f"{name} is out of date: run python3 scripts/settings-reference.py and commit it"
        return 0
    OUTPUT.write_text(content, encoding="utf-8")
    print(f"{name}: {sum(line.startswith('| `') for line in content.splitlines())} settings")
    return 0


if __name__ == "__main__":
    sys.exit(main())
