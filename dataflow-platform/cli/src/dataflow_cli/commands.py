import time
from argparse import Namespace
from collections.abc import Callable, Iterator
from pathlib import Path

from dataflow_cli.workspace import Workspace

SPEC_FILE = "pipeline.yml"
FINISHED = frozenset({"SUCCESS", "FAILURE", "CANCELED"})
POLL_SECONDS = 5
SHORT_RUN_ID = 8
DATE_AND_TIME = slice(0, 19)
TIME_OF_DAY = slice(11, 19)

Print = Callable[[str], None]


def whoami(workspace: Workspace, args: Namespace, out: Print) -> int:
    me = workspace.me()
    out(f"{me['id']}  {me.get('email', '')}  groups: {', '.join(me.get('groups', [])) or '-'}")
    return 0


def validate(workspace: Workspace, args: Namespace, out: Print) -> int:
    failed = 0
    for spec in _specs(args.paths):
        result = workspace.check(spec.parent.name, spec.read_text())
        failed += not result["valid"]
        out(f"{'ok  ' if result['valid'] else 'FAIL'}  {spec}")
        for problem in result.get("problems", []):
            line = f":{problem['line']}" if problem.get("line") else ""
            out(f"      {spec}{line}  {problem['message']}")
        for warning in result.get("warnings", []):
            out(f"      warning: {warning}")
    return 1 if failed else 0


def run(workspace: Workspace, args: Namespace, out: Print, sleep: Callable[[float], None] = time.sleep) -> int:
    run_id = workspace.launch(args.dataset, args.partition)
    out(f"Run {run_id} started for {args.dataset}")
    if not args.wait:
        return 0
    status = None
    while status not in FINISHED:
        sleep(POLL_SECONDS)
        status = workspace.status(run_id)
        out(f"  {status}")
    return 0 if status == "SUCCESS" else 1


def runs(workspace: Workspace, args: Namespace, out: Print) -> int:
    for summary in workspace.runs(args.dataset)[: args.limit]:
        out(f"{summary['runId'][:SHORT_RUN_ID]}  {summary['status']:<9} {summary.get('dataset') or '-':<16} "
            f"{summary.get('createdAt', '')[DATE_AND_TIME]}  {summary.get('trigger') or ''}")
    return 0


def logs(workspace: Workspace, args: Namespace, out: Print) -> int:
    for line in workspace.output(args.run):
        flow = f"[{line['flow']}] " if line.get("flow") else ""
        out(f"{(line.get('timestamp') or '')[TIME_OF_DAY]} {line.get('level') or '':<5} {flow}{line.get('message', '')}")
    return 0


def sql(workspace: Workspace, args: Namespace, out: Print) -> int:
    result = workspace.sql(args.query, args.limit)
    names = [column["name"] for column in result["columns"]]
    rows = [["" if cell is None else str(cell) for cell in row] for row in result["rows"]]
    widths = [max(len(name), *(len(row[index]) for row in rows)) for index, name in enumerate(names)]
    for row in (names, *rows):
        out("  ".join(cell.ljust(width) for cell, width in zip(row, widths, strict=True)))
    truncated = ", truncated" if result.get("truncated") else ""
    out(f"({len(rows)} rows{truncated}, {result.get('elapsedMs', 0)} ms)")
    return 0


def _specs(paths: list[str]) -> Iterator[Path]:
    for given in map(Path, paths):
        yield from sorted(given.rglob(SPEC_FILE)) if given.is_dir() else [given]
