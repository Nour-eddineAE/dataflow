import re
from collections.abc import Collection, Mapping, Sequence
from copy import deepcopy
from typing import Any, Literal
from uuid import UUID

import dagster as dg
from pydantic import ConfigDict

from dataflow_tower.compute_policy import (
    DRIVER_CORES,
    DRIVER_MEMORY,
    EXECUTOR_CORES,
    EXECUTOR_MEMORY,
    EXECUTORS,
    SIZE,
    SPARK_CONF,
    mebibytes,
    spark_conf_env,
)

POSITIVE = re.compile(r"\+?0*[1-9][0-9]{0,9}")
BOOLEAN = re.compile(r"true|false")
BYTES = re.compile(r"-1|[0-9]+(?:b|k|kb|m|mb|g|gb)?", re.IGNORECASE)
ALLOWED_SPARK_CONF = {
    "spark.sql.shuffle.partitions": POSITIVE,
    "spark.default.parallelism": POSITIVE,
    "spark.sql.adaptive.enabled": BOOLEAN,
    "spark.sql.adaptive.coalescePartitions.enabled": BOOLEAN,
    "spark.sql.adaptive.skewJoin.enabled": BOOLEAN,
    "spark.sql.optimizer.dynamicPartitionPruning.enabled": BOOLEAN,
    "spark.sql.parquet.filterPushdown": BOOLEAN,
    "spark.eventLog.enabled": BOOLEAN,
    "spark.sql.autoBroadcastJoinThreshold": BYTES,
}
MAX_INT = 2_147_483_647
COMPUTE = frozenset({EXECUTORS, DRIVER_MEMORY, EXECUTOR_MEMORY, DRIVER_CORES, EXECUTOR_CORES, SPARK_CONF})
NAME_SUFFIX = 20
LABEL_LENGTH = 63
EXECUTOR_PREFIX = 16

KUBERNETES_API = "k8s://https://kubernetes.default.svc"
LOCAL_MASTER = "local[*]"
MAIN_CLASS = "com.dataflow.engine.EngineApplication"
ENGINE_JAR = "local:///opt/dataflow/dataflow-engine.jar"
DRIVER_CONTAINER = "spark-kubernetes-driver"
POD_IP = "SPARK_DRIVER_BIND_ADDRESS"
POD_NAME = "SPARK_DRIVER_POD_NAME"
POD_TEMPLATES = "pod-templates"
POD_TEMPLATES_PATH = "/opt/dataflow/pod-templates"
EXECUTOR_TEMPLATE = "executor.yaml"
SERVICE_ACCOUNT_PATH = "/var/run/secrets/kubernetes.io/serviceaccount"

Role = Literal["driver", "executor"]


class SparkSettings(dg.Model, dg.Resolvable):
    model_config = ConfigDict(frozen=True)

    namespace: str
    run_namespace: str = ""
    image: str
    service_account: str
    config_map: str
    secret: str
    pod_templates: str
    driver_memory: str = "2g"
    executor_memory: str = "2g"
    driver_cores: int = 1
    executor_cores: int = 1
    min_executors: int = 0
    max_executors: int = 4
    ttl_seconds: int = 600
    startup_timeout_seconds: int = 600
    run_timeout_seconds: int = 6 * 60 * 60
    memory_overhead: str = "512m"
    cpu_request: str = ""
    single_pod: bool = False
    driver_template: dict[str, Any] = {}


def driver_job(settings: SparkSettings, run_id: str, dataset: str, arguments: Sequence[str],
               env: Mapping[str, str], compute: Mapping[str, Any] | None = None) -> dict[str, Any]:
    compute = _mapping({} if compute is None else compute, "compute", COMPUTE)
    executors = _mapping(compute.get(EXECUTORS, {}), "compute.executors", {"min", "max"})
    minimum = _integer(executors.get("min", settings.min_executors), "compute.executors.min", 0)
    maximum = _integer(executors.get("max", settings.max_executors), "compute.executors.max", 1)
    if minimum > maximum:
        raise ValueError("compute.executors.min must not exceed max")
    spark_conf = _mapping(compute.get(SPARK_CONF, {}), "compute.spark-conf", ALLOWED_SPARK_CONF.keys())
    for key, value in spark_conf.items():
        if not _valid_conf(key, value):
            raise ValueError(f"compute.spark-conf has an invalid value for {key}")
    driver_memory, driver_cores = _size(settings, compute, "driver")
    executor_memory, executor_cores = _size(settings, compute, "executor")

    run = _uuid(run_id)
    labels = {"dataflow.io/run": str(run), "dataflow.io/dataset": _label(dataset)}
    conf = {**spark_conf, "spark.driver.cores": str(driver_cores), "spark.driver.host": f"$({POD_IP})"}
    if not settings.single_pod:
        conf |= {
            "spark.decommission.enabled": "true",
            "spark.storage.decommission.enabled": "true",
            "spark.storage.decommission.shuffleBlocks.enabled": "true",
            "spark.dynamicAllocation.enabled": "true",
            "spark.dynamicAllocation.shuffleTracking.enabled": "false",
            "spark.dynamicAllocation.minExecutors": str(minimum),
            "spark.dynamicAllocation.maxExecutors": str(maximum),
            "spark.dynamicAllocation.initialExecutors": str(max(1, minimum)),
            "spark.dynamicAllocation.executorIdleTimeout": "60s",
            "spark.executor.memory": executor_memory,
            "spark.executor.cores": str(executor_cores),
            "spark.executor.memoryOverhead": settings.memory_overhead,
            "spark.kubernetes.namespace": settings.namespace,
            "spark.kubernetes.container.image": settings.image,
            "spark.kubernetes.driver.pod.name": f"$({POD_NAME})",
            "spark.kubernetes.authenticate.caCertFile": f"{SERVICE_ACCOUNT_PATH}/ca.crt",
            "spark.kubernetes.authenticate.oauthTokenFile": f"{SERVICE_ACCOUNT_PATH}/token",
            "spark.kubernetes.authenticate.executor.serviceAccountName": settings.service_account,
            "spark.kubernetes.executor.deleteOnTermination": "true",
            "spark.kubernetes.executor.limit.cores": str(executor_cores),
            "spark.kubernetes.executor.request.cores": settings.cpu_request or str(executor_cores),
            "spark.kubernetes.executor.podNamePrefix": f"df-{run.hex[:EXECUTOR_PREFIX]}",
            "spark.kubernetes.executor.podTemplateFile": f"{POD_TEMPLATES_PATH}/{EXECUTOR_TEMPLATE}",
            **{f"spark.kubernetes.executor.label.{key}": value for key, value in labels.items()},
        }
    memory = _pod_memory(driver_memory, settings.memory_overhead)
    container = {
        "name": DRIVER_CONTAINER,
        "image": settings.image,
        "imagePullPolicy": "IfNotPresent",
        "args": [
            "driver", "--master", LOCAL_MASTER if settings.single_pod else KUBERNETES_API,
            "--class", MAIN_CLASS, "--driver-memory", driver_memory,
            *[part for key, value in sorted(conf.items()) for part in ("--conf", f"{key}={value}")],
            ENGINE_JAR, *arguments,
        ],
        "env": [
            {"name": POD_IP, "valueFrom": {"fieldRef": {"fieldPath": "status.podIP"}}},
            {"name": POD_NAME, "valueFrom": {"fieldRef": {"fieldPath": "metadata.name"}}},
            *[{"name": name, "value": value}
              for name, value in sorted({**env, **spark_conf_env(compute), "SPRING_PROFILES_ACTIVE": "k8s"}.items())],
        ],
        "envFrom": [{"configMapRef": {"name": settings.config_map}}, {"secretRef": {"name": settings.secret}}],
        "resources": {
            "requests": {"cpu": settings.cpu_request or str(driver_cores), "memory": memory},
            "limits": {"cpu": str(driver_cores), "memory": memory},
        },
        "volumeMounts": [{"name": POD_TEMPLATES, "mountPath": POD_TEMPLATES_PATH, "readOnly": True}],
    }
    template = deepcopy(dict(settings.driver_template))
    metadata = template.setdefault("metadata", {})
    metadata["labels"] = {**metadata.get("labels", {}), **labels}
    spec = template.setdefault("spec", {})
    spec.update(
        restartPolicy="Never",
        serviceAccountName=settings.service_account,
        containers=[container],
        volumes=[*spec.get("volumes", []), {"name": POD_TEMPLATES, "configMap": {"name": settings.pod_templates}}],
    )
    return {
        "apiVersion": "batch/v1",
        "kind": "Job",
        "metadata": {"name": _name(run, dataset), "namespace": settings.namespace, "labels": dict(labels)},
        "spec": {
            "backoffLimit": 0,
            "ttlSecondsAfterFinished": settings.ttl_seconds,
            "template": template,
        },
    }


def _size(settings: SparkSettings, compute: Mapping[str, Any], role: Role) -> tuple[str, int]:
    memory = compute.get(f"{role}-memory", getattr(settings, f"{role}_memory"))
    if not isinstance(memory, str) or not SIZE.fullmatch(memory):
        raise ValueError(f"compute.{role}-memory must be a positive size such as 512m or 4g")
    cores = _integer(compute.get(f"{role}-cores", getattr(settings, f"{role}_cores")), f"compute.{role}-cores", 1)
    return memory, cores


def _pod_memory(memory: str, overhead: str) -> str:
    return f"{_mebibytes(memory) + _mebibytes(overhead)}Mi"


def _mebibytes(size: str) -> int:
    if SIZE.fullmatch(size) is None:
        raise ValueError("Spark memory settings must be positive sizes such as 512m or 4g")
    return mebibytes(size)


def _name(run: UUID, dataset: str) -> str:
    suffix = re.sub(r"[^a-z0-9-]+", "-", dataset.lower()).strip("-") or "dataset"
    return f"df-{run.hex}-{suffix[:NAME_SUFFIX].rstrip('-')}"


def _label(dataset: str) -> str:
    return re.sub(r"[^A-Za-z0-9_.-]+", "-", dataset)[:LABEL_LENGTH].strip("_.-") or "dataset"


def _uuid(run_id: str) -> UUID:
    try:
        return UUID(run_id)
    except (ValueError, AttributeError, TypeError):
        raise ValueError("run_id must be a UUID") from None


def _valid_conf(key: str, value: Any) -> bool:
    if not isinstance(value, str) or not ALLOWED_SPARK_CONF[key].fullmatch(value):
        return False
    return ALLOWED_SPARK_CONF[key] is not POSITIVE or int(value) <= MAX_INT


def _mapping(value: Any, name: str, allowed: Collection[str]) -> Mapping[str, Any]:
    if not isinstance(value, Mapping):
        raise ValueError(f"{name} must be a mapping")
    if value.keys() - set(allowed):
        raise ValueError(f"{name} contains unsupported settings")
    return value


def _integer(value: Any, name: str, minimum: int) -> int:
    if type(value) is not int or value < minimum:
        raise ValueError(f"{name} must be an integer of at least {minimum}")
    return value
