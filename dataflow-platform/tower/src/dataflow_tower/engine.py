import os
from collections.abc import Callable, Mapping, Sequence
from typing import Any, Protocol

import dagster as dg
import docker
from dagster_docker import PipesDockerClient
from pydantic import Field

from dataflow_tower.compute_policy import DRIVER_CORES, DRIVER_MEMORY, spark_conf_env

ENGINE_SETTINGS = ("JASYPT_", "LAKE_", "RELEASES_", "ENGINE_", "SERVING_", "CLICKHOUSE_", "SPARK_")
LABEL = "org.dataflow"
RUN_ID_LENGTH = 8
NANO_CPUS = 1_000_000_000


class Engine(Protocol):
    def run(self, context: dg.OpExecutionContext, *, dataset: str, arguments: Sequence[str],
            payload: Mapping[str, str], compute: Mapping[str, Any], task: bool) -> dg.PipesClientCompletedInvocation:
        ...


class DockerSettings(dg.Model, dg.Resolvable):
    image: str = Field("dataflow-engine:latest", description="Engine image each run starts")
    memory: str = Field("1800m", description="Memory limit of a run's container")
    task_memory: str = Field("1200m", description="Memory limit of an engine task's container, such as a preview")
    network: str = Field("dataflow_default", description="Docker network the containers join")
    data_dir: str | None = Field(None, description="Host folder mounted at /data for the landing download cache")


class DockerEngine:
    def __init__(self, settings: DockerSettings, env: Mapping[str, str] = os.environ,
                 pipes: Callable[[], PipesDockerClient] = PipesDockerClient,
                 docker_client: Callable[[], docker.DockerClient] = docker.from_env):
        self._settings = settings
        self._pipes = pipes
        self._docker = docker_client
        self._env = {
            **{name: value for name, value in env.items() if name.startswith(ENGINE_SETTINGS)},
            "SPRING_PROFILES_ACTIVE": "docker,spark",
            "ENGINE_CACHE_DIR": "/data",
        }

    def run(self, context, *, dataset, arguments, payload, compute, task):
        settings = self._settings
        volumes = {settings.data_dir: {"bind": "/data", "mode": "rw"}} if settings.data_dir else {}
        identity = container_identity(context.run_id, dataset, task)
        try:
            return self._pipes().run(
                context=context,
                image=settings.image,
                command=list(arguments),
                env={**self._env, **spark_conf_env(compute), **payload},
                container_kwargs={
                    "network": settings.network,
                    "volumes": volumes,
                    **container_limits(settings, compute, task),
                    **identity,
                },
            )
        finally:
            self._remove(context, identity["name"])

    def _remove(self, context: dg.OpExecutionContext, name: str) -> None:
        try:
            self._docker().containers.get(name).remove(force=True)
        except docker.errors.NotFound:
            pass
        except docker.errors.APIError as error:
            context.log.warning(f"Container {name} was not removed: {error}")


def container_limits(settings: DockerSettings, compute: Mapping[str, Any], task: bool) -> dict[str, Any]:
    if task:
        return {"mem_limit": settings.task_memory}
    limits: dict[str, Any] = {"mem_limit": compute.get(DRIVER_MEMORY, settings.memory)}
    if DRIVER_CORES in compute:
        limits["nano_cpus"] = int(compute[DRIVER_CORES]) * NANO_CPUS
    return limits


def container_identity(run_id: str, dataset: str, task: bool) -> dict[str, Any]:
    kind = "task" if task else "run"
    return {
        "name": f"dataflow-{kind}-{dataset.replace('_', '-')}-{run_id[:RUN_ID_LENGTH]}",
        "labels": {f"{LABEL}.component": f"engine-{kind}", f"{LABEL}.dataset": dataset, f"{LABEL}.run": run_id},
    }
