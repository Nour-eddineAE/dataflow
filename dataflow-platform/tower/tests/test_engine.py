from types import SimpleNamespace

import docker
import pytest

from dataflow_tower.compute_policy import spark_conf_env
from dataflow_tower.engine import DockerEngine, DockerSettings, container_identity, container_limits

RUN_ID = "1eb488b7-d672-4329-8224-0e374ef41fb9"


class Pipes:
    def __init__(self, failure=None):
        self.failure = failure

    def run(self, **_):
        if self.failure:
            raise self.failure
        return "completed"


class Containers:
    def __init__(self, present=True):
        self.present = present
        self.removed = []

    def get(self, name):
        if not self.present:
            raise docker.errors.NotFound(name)
        return SimpleNamespace(remove=lambda force: self.removed.append((name, force)))


def docker_engine(pipes, containers):
    return DockerEngine(DockerSettings(), {}, pipes=lambda: pipes, docker_client=lambda: SimpleNamespace(containers=containers))


def context():
    return SimpleNamespace(run_id=RUN_ID, log=SimpleNamespace(warning=lambda message: None))


def test_docker_runs_forward_engine_settings_only():
    engine = DockerEngine(DockerSettings(), {
        "LAKE_ROOT": "abfss://lake@acme.dfs.core.windows.net/prod",
        "CLICKHOUSE_URL": "http://clickhouse:8123",
        "DAGSTER_PG_PASSWORD": "kept-in-the-tower",
    })

    assert engine._env == {
        "LAKE_ROOT": "abfss://lake@acme.dfs.core.windows.net/prod",
        "CLICKHOUSE_URL": "http://clickhouse:8123",
        "SPRING_PROFILES_ACTIVE": "docker,spark",
        "ENGINE_CACHE_DIR": "/data",
    }


def test_a_run_gets_its_compute_and_a_task_keeps_its_own_memory():
    settings = DockerSettings()
    compute = {"driver-memory": "3g", "driver-cores": 2, "executors": {"max": 4}}

    assert container_limits(settings, {}, task=False) == {"mem_limit": "1800m"}
    assert container_limits(settings, compute, task=False) == {"mem_limit": "3g", "nano_cpus": 2_000_000_000}
    assert container_limits(settings, compute, task=True) == {"mem_limit": "1200m"}


def test_spark_settings_of_a_run_override_the_engine_defaults():
    assert spark_conf_env({}) == {}
    assert spark_conf_env({"spark-conf": {"spark.sql.shuffle.partitions": "16"}}) == {
        "SPRING_APPLICATION_JSON": '{"dataflow.spark.config[spark.sql.shuffle.partitions]": "16"}',
    }
    assert spark_conf_env({"spark-conf": {"spark.sql.adaptive.enabled": False}}) == {
        "SPRING_APPLICATION_JSON": '{"dataflow.spark.config[spark.sql.adaptive.enabled]": "false"}',
    }


def test_engine_containers_are_named_and_labelled_by_run():
    identity = container_identity("1eb488b7-d672-4329-8224-0e374ef41fb9", "taxi_trips", task=False)

    assert identity["name"] == "dataflow-run-taxi-trips-1eb488b7"
    assert identity["labels"] == {
        "org.dataflow.component": "engine-run",
        "org.dataflow.dataset": "taxi_trips",
        "org.dataflow.run": "1eb488b7-d672-4329-8224-0e374ef41fb9",
    }
    assert container_identity("2f", "earthquakes", task=True)["name"] == "dataflow-task-earthquakes-2f"


def test_a_finished_engine_container_is_removed():
    pipes, containers = Pipes(), Containers()

    result = docker_engine(pipes, containers).run(
        context(), dataset="taxi_trips", arguments=["run"], payload={}, compute={}, task=False)

    assert result == "completed"
    assert containers.removed == [("dataflow-run-taxi-trips-1eb488b7", True)]


def test_a_failed_engine_container_is_removed_and_the_failure_kept():
    pipes, containers = Pipes(failure=RuntimeError("exit 1")), Containers()

    with pytest.raises(RuntimeError, match="exit 1"):
        docker_engine(pipes, containers).run(context(), dataset="earthquakes", arguments=["run"], payload={}, compute={}, task=True)

    assert containers.removed == [("dataflow-task-earthquakes-1eb488b7", True)]


def test_a_container_that_never_started_is_not_an_error():
    result = docker_engine(Pipes(), Containers(present=False)).run(
        context(), dataset="earthquakes", arguments=["run"], payload={}, compute={}, task=False)

    assert result == "completed"
