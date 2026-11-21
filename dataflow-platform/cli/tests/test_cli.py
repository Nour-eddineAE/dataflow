import json
import threading
from http.server import BaseHTTPRequestHandler, HTTPServer

import pytest

from dataflow_cli import commands
from dataflow_cli.main import main


class FakeWorkspace(BaseHTTPRequestHandler):
    calls: list[tuple] = []

    def log_message(self, *args):
        pass

    def answer(self, code, body):
        data = json.dumps(body).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        self.calls.append(("GET", self.path, self.headers.get("Authorization")))
        if self.path.startswith("/api/runs/r1"):
            self.answer(200, {"run": {"runId": "r1", "status": "SUCCESS"}})
        elif self.path == "/api/me":
            self.answer(200, {"id": "ada", "email": "ada@example.org", "groups": ["engineers"]})
        else:
            self.answer(404, {"error": "no such thing"})

    def do_POST(self):
        body = json.loads(self.rfile.read(int(self.headers.get("Content-Length", 0))) or b"{}")
        self.calls.append(("POST", self.path, body))
        if self.path == "/api/specs/check":
            valid = "version: 3" in body["content"]
            self.answer(200, {"valid": valid, "warnings": [],
                              "problems": [] if valid else [{"message": "version must be 3 or more", "line": 2}]})
        elif self.path == "/api/runs":
            if body["dataset"] == "secret":
                self.answer(403, {"error": "ada lacks RUN on dataset:secret"})
            else:
                self.answer(200, {"runId": "r1"})
        elif self.path == "/api/sql":
            self.answer(200, {"columns": [{"name": "n", "type": "UInt64"}], "rows": [[10235]], "elapsedMs": 19})
        else:
            self.answer(404, {"error": "no such thing"})


@pytest.fixture(autouse=True)
def workspace(monkeypatch, tmp_path):
    server = HTTPServer(("127.0.0.1", 0), FakeWorkspace)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    monkeypatch.setenv("DATAFLOW_URL", f"http://127.0.0.1:{server.server_port}")
    monkeypatch.setenv("DATAFLOW_CONFIG", str(tmp_path / "config"))
    monkeypatch.setattr(commands.time, "sleep", lambda seconds: None)
    FakeWorkspace.calls.clear()
    yield FakeWorkspace.calls
    server.shutdown()


def test_validate_reports_each_spec_and_fails_on_problems(workspace, tmp_path, capsys):
    for dataset, version in (("good", 3), ("old", 1)):
        (tmp_path / dataset).mkdir()
        (tmp_path / dataset / "pipeline.yml").write_text(f"dataset: {dataset}\nversion: {version}\n")

    assert main(["validate", str(tmp_path)]) == 1

    out = capsys.readouterr().out
    assert f"ok    {tmp_path / 'good' / 'pipeline.yml'}" in out
    assert f"{tmp_path / 'old' / 'pipeline.yml'}:2  version must be 3 or more" in out
    assert [call[2]["dataset"] for call in workspace] == ["good", "old"]


def test_run_waits_for_the_outcome(capsys):
    assert main(["run", "earthquakes", "--wait"]) == 0

    out = capsys.readouterr().out
    assert "Run r1 started for earthquakes" in out
    assert "SUCCESS" in out


def test_a_refused_run_explains_why(capsys):
    assert main(["run", "secret"]) == 2

    assert "403: ada lacks RUN on dataset:secret" in capsys.readouterr().err


def test_sql_prints_a_table(capsys):
    assert main(["sql", "SELECT count() AS n FROM earthquakes.current"]) == 0

    out = capsys.readouterr().out
    assert "10235" in out
    assert "(1 rows, 19 ms)" in out


def test_without_saved_tokens_requests_go_unauthenticated(workspace):
    main(["whoami"])

    assert workspace[-1] == ("GET", "/api/me", None)
