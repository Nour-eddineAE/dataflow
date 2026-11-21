import urllib.parse
from typing import Any

from dataflow_cli.auth import Tokens
from dataflow_cli.config import Config
from dataflow_cli.http import call


class Workspace:
    def __init__(self, config: Config, tokens: Tokens):
        self._api = f"{config.workspace}/api"
        self._tokens = tokens

    def me(self) -> dict[str, Any]:
        return self._get("/me")

    def check(self, dataset: str, content: str) -> dict[str, Any]:
        return self._post("/specs/check", {"dataset": dataset, "content": content})

    def launch(self, dataset: str, partition: str | None) -> str:
        return self._post("/runs", {"dataset": dataset, "partition": partition})["runId"]

    def status(self, run_id: str) -> str:
        return self._get(f"/runs/{run_id}")["run"]["status"]

    def runs(self, dataset: str | None) -> list[dict[str, Any]]:
        return self._get(f"/runs?dataset={urllib.parse.quote(dataset)}" if dataset else "/runs")

    def output(self, run_id: str) -> list[dict[str, Any]]:
        return self._get(f"/runs/{run_id}/output")["lines"]

    def sql(self, query: str, limit: int) -> dict[str, Any]:
        return self._post("/sql", {"sql": query, "limit": limit})

    def _get(self, path: str) -> Any:
        return call("GET", self._api + path, self._tokens.access())

    def _post(self, path: str, body: Any) -> Any:
        return call("POST", self._api + path, self._tokens.access(), body)
