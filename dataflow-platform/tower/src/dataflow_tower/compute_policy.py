import json
import re
from collections.abc import Mapping
from copy import deepcopy
from typing import Any

import dagster as dg
from pydantic import Field

SIZE = re.compile(r"(?P<amount>[1-9][0-9]*)(?P<unit>[mg])", re.IGNORECASE)
MIB_PER_GIB = 1024
DRIVER_MEMORY = "driver-memory"
EXECUTOR_MEMORY = "executor-memory"
DRIVER_CORES = "driver-cores"
EXECUTOR_CORES = "executor-cores"
EXECUTORS = "executors"
SPARK_CONF = "spark-conf"


class ComputePolicy(dg.Model, dg.Resolvable):
    max_driver_memory: str | None = Field(None, description="Most driver memory a spec may ask for; more is lowered")
    max_driver_cores: str | int | None = Field(None, description="Most driver cores a spec may ask for")
    max_executor_memory: str | None = Field(None, description="Most memory per executor a spec may ask for")
    max_executor_cores: str | int | None = Field(None, description="Most cores per executor a spec may ask for")
    max_executors: str | int | None = Field(None, description="Most executors a spec may ask for")

    def limit(self, compute: Mapping[str, Any]) -> tuple[dict[str, Any], list[str]]:
        limited = deepcopy(dict(compute))
        lowered: list[str] = []
        for key, maximum in ((DRIVER_MEMORY, self.max_driver_memory), (EXECUTOR_MEMORY, self.max_executor_memory)):
            if _given(maximum) and key in limited and mebibytes(limited[key]) > mebibytes(str(maximum)):
                lowered.append(f"{key} {limited[key]} lowered to the install's limit of {maximum}")
                limited[key] = str(maximum)
        for key, maximum in ((DRIVER_CORES, self.max_driver_cores), (EXECUTOR_CORES, self.max_executor_cores)):
            if _given(maximum) and key in limited and int(limited[key]) > int(maximum):
                lowered.append(f"{key} {limited[key]} lowered to the install's limit of {maximum}")
                limited[key] = int(maximum)
        executors = limited.get(EXECUTORS)
        if _given(self.max_executors) and isinstance(executors, Mapping):
            executors = dict(executors)
            for bound in ("min", "max"):
                if bound in executors and int(executors[bound]) > int(self.max_executors):
                    lowered.append(f"executors.{bound} {executors[bound]} lowered to the install's limit of {self.max_executors}")
                    executors[bound] = int(self.max_executors)
            limited[EXECUTORS] = executors
        return limited, lowered


def mebibytes(size: str) -> int:
    match = SIZE.fullmatch(str(size).strip())
    if not match:
        raise ValueError(f"Not a size such as 512m or 4g: {size}")
    amount = int(match["amount"])
    return amount * MIB_PER_GIB if match["unit"].lower() == "g" else amount


def spark_conf_env(compute: Mapping[str, Any]) -> dict[str, str]:
    conf = compute.get(SPARK_CONF) or {}
    if not conf:
        return {}
    return {"SPRING_APPLICATION_JSON": json.dumps(
        {f"dataflow.spark.config[{key}]": _text(value) for key, value in sorted(conf.items())})}


def _text(value: Any) -> str:
    return str(value).lower() if isinstance(value, bool) else str(value)


def _given(value: str | int | None) -> bool:
    return value is not None and str(value).strip() != ""
