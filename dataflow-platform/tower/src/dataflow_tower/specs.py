from collections.abc import Mapping
from dataclasses import dataclass, field
from enum import StrEnum
from pathlib import Path
from typing import Any, Self

import re

import yaml

SPEC_FILE = "pipeline.yml"
DATASET_TAG = "dataset"
LANDING = "landing"
BRONZE = "bronze"
SILVER = "silver"
QUARANTINE = "quarantine"
HISTORY = "history"
GOLD = "gold"
REFERENCE = "reference"
SERVING = "serving"
CURRENT = "current"
GOLD_PREFIX = "gold."
REFERENCE_PREFIX = "reference."
ROWS = "rows"
NAME_LENGTH = 63
NAME = rf"[a-z][a-z0-9_]{{0,{NAME_LENGTH - 1}}}"
SQL_RELATION = re.compile(r"\b(?:from|join)\s+([A-Za-z_][A-Za-z0-9_]*)", re.IGNORECASE)
TABLE_REF = re.compile(
    rf"(?P<dataset>{NAME})\.(?P<kind>bronze|silver|quarantine|history|current|gold|reference)"
    rf"(?:\.(?P<name>{NAME}))?")


class OnFailure(StrEnum):
    WARN = "warn"
    DROP = "drop"
    FAIL = "fail"


class PartitionKind(StrEnum):
    MONTHLY = "monthly"
    DAILY = "daily"


@dataclass(frozen=True)
class Expectation:
    name: str
    check: str
    on_failure: OnFailure = OnFailure.WARN


@dataclass(frozen=True)
class Partitions:
    kind: PartitionKind
    start: str


@dataclass(frozen=True)
class TableRef:
    dataset: str
    kind: str
    name: str | None = None

    @classmethod
    def parse(cls, text: str) -> Self | None:
        match = TABLE_REF.fullmatch(text or "")
        return cls(match["dataset"], match["kind"], match["name"]) if match else None

    @property
    def asset_path(self) -> list[str]:
        if self.kind == CURRENT:
            return [self.dataset, "history"]
        return [self.dataset, self.kind, self.name] if self.name else [self.dataset, self.kind]


@dataclass(frozen=True)
class Gold:
    name: str
    reads: frozenset[str] = frozenset()
    materialize: bool = True


@dataclass(frozen=True)
class Serve:
    table: str
    source: str
    mode: str = "view"

    @property
    def gold(self) -> str | None:
        return self.source.removeprefix(GOLD_PREFIX) if self.source.startswith(GOLD_PREFIX) else None


@dataclass(frozen=True)
class Spec:
    dataset: str
    owner: str | None = None
    description: str | None = None
    schedule: str | None = None
    ingests: bool = True
    inputs: Mapping[str, TableRef] = field(default_factory=dict)
    partitions: Partitions | None = None
    expectations: tuple[Expectation, ...] = ()
    references: tuple[str, ...] = ()
    gold: tuple[Gold, ...] = ()
    serve: tuple[Serve, ...] = ()
    compute: Mapping[str, Any] = field(default_factory=dict)
    release: str | None = None

    @property
    def quarantines(self) -> bool:
        return any(expectation.on_failure is OnFailure.DROP for expectation in self.expectations)

    @property
    def stored(self) -> tuple[Gold, ...]:
        return tuple(gold for gold in self.gold if gold.materialize)

    @property
    def runs_on_inputs(self) -> bool:
        return bool(self.inputs) and not self.schedule

    def sources(self, table: str) -> set[str]:
        tables = {gold.name: gold for gold in self.gold}
        found: set[str] = set()
        pending, seen = list(tables[table].reads), {table}
        while pending:
            name = pending.pop()
            upstream = tables.get(name)
            if upstream is not None and not upstream.materialize:
                if name not in seen:
                    seen.add(name)
                    pending.extend(upstream.reads)
            else:
                found.add(name)
        return found

    @classmethod
    def parse(cls, document: Mapping[str, Any], release: str | None = None) -> Self:
        source = document.get("source")
        silver = document.get("silver") or {}
        references = tuple(reference["name"] for reference in document.get("references") or ())
        inputs = {alias: table for alias, text in (document.get("inputs") or {}).items()
                  if (table := TableRef.parse(text)) is not None}
        gold_documents = document.get("gold") or ()
        names = {*references, *inputs, *(table["name"] for table in gold_documents)}
        if source:
            names.add(CURRENT)
        return cls(
            dataset=document["dataset"],
            owner=document.get("owner"),
            description=document.get("description"),
            schedule=document.get("schedule"),
            ingests=bool(source),
            inputs=inputs,
            partitions=_partitions((source or {}).get("partitions")),
            expectations=tuple(
                Expectation(rule["name"], rule["check"], OnFailure(rule.get("on-failure", OnFailure.WARN)))
                for rule in silver.get("expectations") or ()
            ),
            references=references,
            gold=tuple(_gold(table, names) for table in gold_documents),
            serve=tuple(
                Serve(table=served["table"], source=served["from"], mode=served.get("mode", "view"))
                for served in document.get("serve") or ()
            ),
            compute=document.get("compute") or {},
            release=release,
        )


def read_specs(folder: Path, release: str | None = None) -> list[Spec]:
    return [
        Spec.parse(yaml.safe_load(file.read_text()), release)
        for file in sorted(folder.glob(f"*/{SPEC_FILE}"))
    ]


def _gold(document: Mapping[str, Any], names: set[str]) -> Gold:
    return Gold(document["name"], frozenset(_reads(document, names) & names), document.get("materialize", True))


def _reads(document: Mapping[str, Any], names: set[str]) -> set[str]:
    if "sql" in document:
        return {name.lower() for name in SQL_RELATION.findall(document["sql"])}
    if "transformation" in document:
        sources = (document.get("inputs") or {}).values()
        return {CURRENT} if not sources else {_relation(source) for source in sources}
    reads = {document.get("from")}
    for step in document.get("steps") or ():
        if "join" in step:
            reads.add(step["join"].get("with"))
        reads.update(step.get("union") or ())
    return reads


def _relation(source: str) -> str:
    if source == ROWS:
        return CURRENT
    return source.removeprefix(REFERENCE_PREFIX)


def _partitions(document: Mapping[str, Any] | None) -> Partitions | None:
    if not document:
        return None
    (kind, window), = document.items()
    return Partitions(PartitionKind(kind), str(window["start"]))
