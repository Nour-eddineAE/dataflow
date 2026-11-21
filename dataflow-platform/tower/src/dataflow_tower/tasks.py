import re
from enum import StrEnum

import dagster as dg

JOB = "engine_task"
OP = "run_engine_task"
LAKE_KEY = re.compile(r"(?!system/)(?!.*\.\.)[A-Za-z0-9._-]+(/[A-Za-z0-9._-]+)*")


class TaskKind(StrEnum):
    PREVIEW = "preview"
    IMPACT = "impact"
    INFER = "infer"
    CLEAN = "clean"


class EngineTaskConfig(dg.Config):
    command: str
    dataset: str
    task_id: str
    draft: str = ""
    release: str = ""
    transformations: str = ""
    source: str = ""
    clean: str = ""


def task_arguments(config: EngineTaskConfig) -> list[str]:
    try:
        kind = TaskKind(config.command)
    except ValueError:
        raise dg.Failure(f"Unknown engine task '{config.command}'; expected one of "
                         f"{', '.join(TaskKind)}") from None
    arguments = [kind.value, f"--dataset={config.dataset}", f"--task={config.task_id}"]
    if kind is TaskKind.INFER:
        if not config.source.startswith(("http://", "https://")) and not LAKE_KEY.fullmatch(config.source):
            raise dg.Failure(f"infer needs an http or https URL or a lake path, got '{config.source}'")
        return [*arguments, f"--source={config.source}"]
    return [*arguments, f"--release={config.release}"] if config.release else arguments


def task_payload(config: EngineTaskConfig) -> dict[str, str]:
    payload = {
        "ENGINE_DRAFT": config.draft,
        "ENGINE_TRANSFORMATIONS": config.transformations,
        "ENGINE_CLEAN": config.clean,
    }
    return {name: value for name, value in payload.items() if value}


def engine_task_job() -> dg.JobDefinition:
    @dg.op(name=OP, required_resource_keys={"engine"},
           description="Preview a draft spec, measure its impact, draft one from a source, or clean up")
    def run_engine_task(context: dg.OpExecutionContext, config: EngineTaskConfig) -> None:
        context.resources.engine.run(context, dataset=config.dataset, arguments=task_arguments(config),
                                     payload=task_payload(config), compute={}, task=True)

    @dg.job(name=JOB, tags={"dagster/max_retries": "0"},
            description="Engine tasks the Workspace starts for the spec editor and housekeeping")
    def engine_task() -> None:
        run_engine_task()

    return engine_task
