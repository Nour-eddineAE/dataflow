import dagster as dg
import pytest

from conftest import EXAMPLES
from dataflow_tower.tasks import EngineTaskConfig, task_arguments, task_payload


def task(command, **settings):
    return EngineTaskConfig(command=command, dataset=settings.pop("dataset", "earthquakes"),
                            task_id=settings.pop("task_id", "t1"), **settings)


def test_a_task_passes_its_dataset_task_and_release():
    assert task_arguments(task("preview")) == ["preview", "--dataset=earthquakes", "--task=t1"]
    assert task_arguments(task("impact", release=EXAMPLES))[-1] == f"--release={EXAMPLES}"


def test_infer_reads_a_web_source_or_a_lake_path_only():
    assert task_arguments(task("infer", source="https://example.org/a.csv"))[-1] == "--source=https://example.org/a.csv"
    assert task_arguments(task("infer", source="inbox/sensors/readings.csv"))[-1] == "--source=inbox/sensors/readings.csv"
    for refused in ("file:///etc/passwd", "../x.csv", "system/tasks/x.json", "/etc/passwd"):
        with pytest.raises(dg.Failure):
            task_arguments(task("infer", source=refused))


def test_only_engine_tasks_are_accepted():
    with pytest.raises(dg.Failure, match="preview, impact, infer, clean"):
        task_arguments(task("run"))


def test_a_task_hands_the_engine_only_the_payload_it_carries():
    clean = task("clean", dataset="platform", task_id="t9", clean="c3lzdGVtL3Rhc2tzL2EuanNvbg==")

    assert task_arguments(clean) == ["clean", "--dataset=platform", "--task=t9"]
    assert task_payload(clean) == {"ENGINE_CLEAN": "c3lzdGVtL3Rhc2tzL2EuanNvbg=="}
