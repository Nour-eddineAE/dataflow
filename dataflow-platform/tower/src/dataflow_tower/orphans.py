import os
import time
from collections.abc import Callable
from pathlib import Path

import dagster as dg
import docker
from dagster._core.launcher import DefaultRunLauncher

from dataflow_tower.engine import LABEL

PROC = Path("/proc")
STATE_FIELD = 3
START_TIME_FIELD = 22
CLOCK_SKEW_SECONDS = 5
CHECK_EVERY_SECONDS = 60
REASON = ("The tower's code server restarted while this run was running, so its run worker is gone. "
          "The run is marked failed so its dataset is free again; run retries start it anew.")


def code_server_started_at(proc: Path = PROC, now: Callable[[], float] = time.time) -> float:
    uptime = float((proc / "uptime").read_text().split()[0])
    after_command = (proc / "1" / "stat").read_text().rsplit(")", 1)[1].split()
    started = int(after_command[START_TIME_FIELD - STATE_FIELD]) / os.sysconf("SC_CLK_TCK")
    return now() - (uptime - started)


def engine_containers(run_id: str) -> list:
    return docker.from_env().containers.list(all=True, filters={"label": f"{LABEL}.run={run_id}"})


def orphaned(runs: list[dg.DagsterRun], records: dict[str, float], started_at: float) -> list[dg.DagsterRun]:
    return [run for run in runs if records.get(run.run_id, started_at) < started_at - CLOCK_SKEW_SECONDS]


def orphaned_runs(started_at: Callable[[], float] = code_server_started_at,
                  engines: Callable[[str], list] = engine_containers) -> dg.SensorDefinition:
    @dg.sensor(name="orphaned_runs", minimum_interval_seconds=CHECK_EVERY_SECONDS,
               default_status=dg.DefaultSensorStatus.RUNNING)
    def orphaned_runs(context: dg.SensorEvaluationContext):
        instance = context.instance
        if not isinstance(instance.run_launcher, DefaultRunLauncher):
            return dg.SkipReason("Runs do not live in the code server")
        records = instance.get_run_records(filters=dg.RunsFilter(statuses=[dg.DagsterRunStatus.STARTED]))
        starts = {record.dagster_run.run_id: record.start_time for record in records if record.start_time}
        failed = 0
        for run in orphaned([record.dagster_run for record in records], starts, started_at()):
            containers = engines(run.run_id)
            if any(container.status == "running" for container in containers):
                context.log.info(f"Run {run.run_id} lost its worker; waiting for its engine to finish")
                continue
            for container in containers:
                container.remove(force=True)
            instance.report_run_failed(run, REASON)
            context.log.warning(f"Run {run.run_id} of {run.job_name} lost its worker; marked failed")
            failed += 1
        return dg.SkipReason(f"{failed} orphaned runs marked failed" if failed else "No orphaned runs")

    return orphaned_runs
