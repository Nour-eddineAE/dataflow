from pathlib import Path

import fsspec
import pytest

from dataflow_tower.releases import ReleaseStore, released_specs

RELEASES = Path(__file__).resolve().parent / "fixtures" / "releases"
EXAMPLES = "examples@5ea326b0e8a3f2d14c6b9a7e0f1d2c3b4a5e6f70"


@pytest.fixture
def specs(tmp_path):
    store = ReleaseStore(fsspec.filesystem("file"), str(RELEASES))
    return {spec.dataset: spec for spec in released_specs(store, "prod", tmp_path / "cache")}
