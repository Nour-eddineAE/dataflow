import json
import logging
import shutil
from collections.abc import Mapping
from dataclasses import dataclass, replace
from pathlib import Path
from typing import Any, Self

import fsspec
import yaml

from dataflow_tower.compute_policy import EXECUTORS, SPARK_CONF
from dataflow_tower.lake import Lake
from dataflow_tower.specs import Spec, read_specs

MANIFEST = "release.json"
PROJECT_FILE = "dataflow.yml"
SPECS_FOLDER = "pipelines"

log = logging.getLogger(__name__)


@dataclass(frozen=True)
class Release:
    project: str
    commit: str

    @property
    def id(self) -> str:
        return f"{self.project}@{self.commit}"

    @classmethod
    def parse(cls, release_id: str) -> Self:
        project, commit = release_id.split("@", 1)
        return cls(project, commit)


class ReleaseStore:
    def __init__(self, fs: fsspec.AbstractFileSystem, root: str):
        self._fs = fs
        self._root = root.rstrip("/")

    @classmethod
    def open(cls, root: str, lake: Lake) -> Self:
        scheme, _, path = root.partition("://")
        if scheme in ("s3", "s3a"):
            return cls(lake.filesystem(), path)
        return cls(fsspec.filesystem("file"), path if scheme == "file" else root)

    def current(self, target: str) -> list[Release]:
        return [
            Release(pointer["project"], pointer["commit"])
            for pointer in map(self._json, sorted(self._fs.glob(f"{self._root}/*/targets/{target}.json")))
        ]

    def materialize(self, release: Release, cache: Path) -> Path:
        local = cache / release.project / release.commit
        if (local / MANIFEST).is_file():
            return local
        source = f"{self._root}/{release.project}/{release.commit}"
        if not self._fs.exists(f"{source}/{MANIFEST}"):
            raise ValueError(f"Release {release.id} is incomplete: no {MANIFEST} under {source}")
        partial = local.with_name(f"{local.name}.partial")
        shutil.rmtree(partial, ignore_errors=True)
        partial.parent.mkdir(parents=True, exist_ok=True)
        self._fs.get(f"{source}/", f"{partial}/", recursive=True)
        partial.rename(local)
        return local

    def _json(self, path: str) -> dict:
        return json.loads(self._fs.cat_file(path))


def released_specs(store: ReleaseStore, target: str, cache: Path) -> list[Spec]:
    specs: dict[str, Spec] = {}
    for release in store.current(target):
        folder = store.materialize(release, cache)
        document = yaml.safe_load((folder / PROJECT_FILE).read_text()) or {}
        project = document.get("project")
        if project != release.project:
            log.warning("Release %s holds project '%s'; skipped", release.id, project)
            continue
        defaults = ((document.get("targets") or {}).get(target) or {}).get("compute") or {}
        for spec in read_specs(folder / SPECS_FOLDER, release.id):
            spec = replace(spec, compute=merged_compute(defaults, spec.compute))
            if spec.dataset in specs:
                log.warning("Dataset %s of %s is already released by %s; skipped",
                            spec.dataset, release.id, specs[spec.dataset].release)
                continue
            specs[spec.dataset] = spec
    return [specs[dataset] for dataset in sorted(specs)]


def merged_compute(defaults: Mapping[str, Any], own: Mapping[str, Any]) -> dict[str, Any]:
    merged = {**defaults, **own}
    for nested in (EXECUTORS, SPARK_CONF):
        if nested in defaults and nested in own:
            merged[nested] = {**defaults[nested], **own[nested]}
    return merged
