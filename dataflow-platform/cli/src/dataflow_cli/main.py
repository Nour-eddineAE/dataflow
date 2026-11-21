import argparse
import sys
import urllib.error
from collections.abc import Sequence

from dataflow_cli import commands
from dataflow_cli.auth import Tokens, device_login
from dataflow_cli.config import Config
from dataflow_cli.http import Failure
from dataflow_cli.workspace import Workspace

DEFAULT_RUNS = 20


def main(argv: Sequence[str] | None = None) -> int:
    args = _parser().parse_args(argv)
    config = Config.from_env()
    tokens = Tokens(config)
    workspace = Workspace(config, tokens)
    try:
        match args.command:
            case "login":
                device_login(config, tokens, print)
                print(f"Signed in as {workspace.me()['id']}")
                return 0
            case "logout":
                tokens.forget()
                print("Signed out")
                return 0
            case _:
                return args.handler(workspace, args, print)
    except Failure as failure:
        print(f"dataflow: {failure}", file=sys.stderr)
        return 2
    except urllib.error.URLError as error:
        print(f"dataflow: cannot reach {config.workspace} ({error.reason})", file=sys.stderr)
        return 2


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(prog="dataflow", description="DataFlow from the command line")
    sub = parser.add_subparsers(dest="command", required=True)
    sub.add_parser("login", help="sign in through your browser")
    sub.add_parser("logout", help="forget the saved sign-in")
    sub.add_parser("whoami", help="who the Workspace sees").set_defaults(handler=commands.whoami)

    validate = sub.add_parser("validate", help="check pipeline specs without saving them")
    validate.add_argument("paths", nargs="+", help="pipeline.yml files or folders holding them")
    validate.set_defaults(handler=commands.validate)

    run = sub.add_parser("run", help="start a run")
    run.add_argument("dataset")
    run.add_argument("--partition")
    run.add_argument("--wait", action="store_true", help="wait for the run to finish; exit 1 if it fails")
    run.set_defaults(handler=commands.run)

    runs = sub.add_parser("runs", help="recent runs")
    runs.add_argument("--dataset")
    runs.add_argument("--limit", type=int, default=DEFAULT_RUNS)
    runs.set_defaults(handler=commands.runs)

    logs = sub.add_parser("logs", help="the engine output of a run")
    logs.add_argument("run")
    logs.set_defaults(handler=commands.logs)

    sql = sub.add_parser("sql", help="run a query in the SQL editor's sandbox")
    sql.add_argument("query")
    sql.add_argument("--limit", type=int, default=100)
    sql.set_defaults(handler=commands.sql)
    return parser
