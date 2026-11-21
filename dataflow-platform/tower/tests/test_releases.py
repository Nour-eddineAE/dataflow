import fsspec
import pytest

from conftest import EXAMPLES
from dataflow_tower.lake import Lake
from dataflow_tower.releases import Release, ReleaseStore, released_specs


def test_released_specs_carry_their_release_and_keep_the_first_owner_of_a_dataset(specs):
    assert sorted(specs) == ["earthquakes", "taxi_trips", "zone_hours"]
    assert {spec.release for spec in specs.values()} == {EXAMPLES}


def test_a_pipeline_runs_on_its_target_compute_and_overrides_parts_of_it(specs):
    assert specs["earthquakes"].compute == {
        "driver-memory": "1g",
        "executors": {"min": 1, "max": 3},
        "spark-conf": {"spark.sql.shuffle.partitions": "16"},
    }
    assert specs["taxi_trips"].compute == {
        "driver-memory": "1g",
        "executors": {"min": 1, "max": 6},
        "spark-conf": {"spark.sql.shuffle.partitions": "16", "spark.sql.adaptive.enabled": "false"},
    }


def test_an_incomplete_release_is_refused(tmp_path):
    root = tmp_path / "releases"
    (root / "broken" / "targets").mkdir(parents=True)
    (root / "broken" / "targets" / "prod.json").write_text('{"project": "broken", "commit": "abc"}')

    with pytest.raises(ValueError, match="incomplete"):
        released_specs(ReleaseStore(fsspec.filesystem("file"), str(root)), "prod", tmp_path / "cache")


def test_a_release_id_names_project_and_commit():
    assert Release.parse(EXAMPLES) == Release("examples", EXAMPLES.split("@")[1])
    assert Release.parse(EXAMPLES).id == EXAMPLES


def test_a_store_opens_on_local_paths_and_buckets(tmp_path):
    assert ReleaseStore.open(f"file://{tmp_path}", Lake()).current("prod") == []
    assert ReleaseStore.open("s3a://releases", Lake(endpoint="http://rustfs:9000"))._root == "releases"
