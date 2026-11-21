import os
import time

import dagster as dg
import pytest
from dagster._core.events import DagsterEvent, DagsterEventType

from dataflow_tower.orphans import REASON, code_server_started_at, orphaned, orphaned_runs

DEFAULT_LAUNCHER = {"run_launcher": {"module": "dagster.core.launcher", "class": "DefaultRunLauncher"}}
IN_PROCESS_LAUNCHER = {"run_launcher": {"module": "dagster._core.launcher.sync_in_memory_run_launcher",
                                        "class": "SyncInMemoryRunLauncher"}}


class Engine:
    def __init__(self, status):
        self.status = status
        self.removed = False

    def remove(self, force):
        self.removed = force


def started_run(instance: dg.DagsterInstance) -> dg.DagsterRun:
    run = instance.add_run(dg.DagsterRun(job_name="earthquakes_job", status=dg.DagsterRunStatus.STARTED))
    instance.report_dagster_event(
        DagsterEvent(event_type_value=DagsterEventType.RUN_START.value, job_name=run.job_name), run.run_id)
    return run


def test_the_code_server_start_is_read_from_its_first_process(tmp_path):
    (tmp_path / "1").mkdir()
    (tmp_path / "uptime").write_text("1000.00 3000.00\n")
    ticks = 400 * os.sysconf("SC_CLK_TCK")
    (tmp_path / "1" / "stat").write_text("1 (dagster code) S 0 1 1 0 -1 4194560 " + "0 " * 12 + f"{ticks} 0 0\n")

    assert code_server_started_at(tmp_path, now=lambda: 5000.0) == pytest.approx(4400.0)


def test_only_runs_started_before_the_code_server_are_orphans():
    old, new, unknown = (dg.DagsterRun(job_name="j", run_id=name) for name in ("old", "new", "unknown"))

    assert orphaned([old, new, unknown], {"old": 100.0, "new": 196.0}, 200.0) == [old]


def test_a_run_whose_worker_died_with_the_code_server_is_failed_and_its_engine_removed():
    with dg.instance_for_test(overrides=DEFAULT_LAUNCHER) as instance:
        run = started_run(instance)
        engine = Engine("exited")
        sensor = orphaned_runs(started_at=lambda: time.time() + 60,
                               engines=lambda run_id: [engine] if run_id == run.run_id else [])

        result = sensor(dg.build_sensor_context(instance=instance))

        assert instance.get_run_by_id(run.run_id).status == dg.DagsterRunStatus.FAILURE
        assert engine.removed
        assert result.skip_message == "1 orphaned runs marked failed"
        failures = instance.get_records_for_run(run.run_id, of_type=DagsterEventType.RUN_FAILURE).records
        assert REASON in failures[0].event_log_entry.dagster_event.message


def test_an_orphan_waits_while_its_engine_still_writes():
    with dg.instance_for_test(overrides=DEFAULT_LAUNCHER) as instance:
        run = started_run(instance)
        engine = Engine("running")
        sensor = orphaned_runs(started_at=lambda: time.time() + 60, engines=lambda run_id: [engine])

        result = sensor(dg.build_sensor_context(instance=instance))

        assert instance.get_run_by_id(run.run_id).status == dg.DagsterRunStatus.STARTED
        assert not engine.removed
        assert result.skip_message == "No orphaned runs"


def test_a_run_started_by_this_code_server_is_left_alone():
    with dg.instance_for_test(overrides=DEFAULT_LAUNCHER) as instance:
        run = started_run(instance)
        sensor = orphaned_runs(started_at=lambda: time.time() - 3600, engines=lambda run_id: [])

        result = sensor(dg.build_sensor_context(instance=instance))

        assert instance.get_run_by_id(run.run_id).status == dg.DagsterRunStatus.STARTED
        assert result.skip_message == "No orphaned runs"


def test_runs_outside_the_code_server_are_not_checked():
    with dg.instance_for_test(overrides=IN_PROCESS_LAUNCHER) as instance:
        run = started_run(instance)

        result = orphaned_runs(started_at=lambda: time.time() + 60, engines=lambda run_id: [])(
            dg.build_sensor_context(instance=instance))

        assert instance.get_run_by_id(run.run_id).status == dg.DagsterRunStatus.STARTED
        assert result.skip_message == "Runs do not live in the code server"


def test_only_docker_installs_watch_for_orphans():
    from dataflow_tower.component import PipelineSpecs
    from dataflow_tower.driver_job import SparkSettings

    kubernetes = SparkSettings(namespace="jobs", image="engine", service_account="engine", config_map="engine",
                               secret="lake", pod_templates="engine-pods")

    assert [sensor.name for sensor in PipelineSpecs()._supervision()] == ["orphaned_runs"]
    assert PipelineSpecs(kubernetes=kubernetes)._supervision() == []
