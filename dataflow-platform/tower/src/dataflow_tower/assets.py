from collections.abc import Sequence
from graphlib import TopologicalSorter

import dagster as dg

from dataflow_tower.releases import Release
from dataflow_tower.specs import (
    BRONZE,
    CURRENT,
    DATASET_TAG,
    GOLD,
    HISTORY,
    LANDING,
    QUARANTINE,
    REFERENCE,
    SERVING,
    SILVER,
    OnFailure,
    PartitionKind,
    Spec,
)

DELTA = "delta"
INPUTS_LANDED = "inputs landed"
FULL_REFRESH_TAG = "workspace/full-refresh"


def partitions(spec: Spec) -> dg.PartitionsDefinition | None:
    window = spec.partitions
    if window is None:
        return None
    if window.kind is PartitionKind.MONTHLY:
        return dg.MonthlyPartitionsDefinition(start_date=window.start, fmt="%Y-%m")
    return dg.DailyPartitionsDefinition(start_date=window.start, fmt="%Y-%m-%d")


def asset_specs(spec: Spec, freshness: dg.FreshnessPolicy | None = None) -> Sequence[dg.AssetSpec]:
    dataset = spec.dataset
    partitioned = partitions(spec)
    automation = inputs_landed(dataset) if spec.runs_on_inputs else None

    def asset(path: Sequence[str], depends_on: Sequence[Sequence[str]], kind: str, description: str,
              **extra) -> dg.AssetSpec:
        return dg.AssetSpec(
            [dataset, *path],
            deps=list(depends_on),
            group_name=dataset,
            kinds={kind},
            description=description,
            owners=[f"team:{spec.owner}"] if spec.owner else None,
            partitions_def=partitioned,
            **extra,
        )

    def own(*path: str) -> list[str]:
        return [dataset, *path]

    def upstream(relation: str) -> list[str]:
        if relation == CURRENT:
            return own(HISTORY)
        if relation in spec.inputs:
            return spec.inputs[relation].asset_path
        if relation in spec.references:
            return own(REFERENCE, relation)
        return own(GOLD, relation)

    ingestion = [
        asset([LANDING], [], "s3", "Raw files as fetched, named by content hash"),
        asset([BRONZE], [own(LANDING)], DELTA, "Rows as received, plus _rescued_data"),
        asset([SILVER], [own(BRONZE)], DELTA, "Typed, derived and checked rows"),
        *([asset([QUARANTINE], [own(BRONZE)], DELTA, "Rows a drop rule rejected")] if spec.quarantines else []),
        asset([HISTORY], [own(SILVER)], DELTA, "Every version of every fact", freshness_policy=freshness),
    ] if spec.ingests else []
    return [
        *ingestion,
        *(asset([REFERENCE, name], [], DELTA, f"Lookup table {name}") for name in spec.references),
        *(
            asset([GOLD, gold.name], [upstream(source) for source in sorted(spec.sources(gold.name))], DELTA,
                  f"Gold table {gold.name}", automation_condition=automation)
            for gold in spec.stored
        ),
        *(
            asset([SERVING, served.table], [own(GOLD, served.gold) if served.gold else own(HISTORY)],
                  "clickhouse", f"ClickHouse {served.mode} serving.{served.table}")
            for served in spec.serve
        ),
    ]


def in_dependency_order(results: Sequence, specs: Sequence[dg.AssetSpec]) -> tuple:
    own = {asset.key for asset in specs}
    graph = {asset.key: {dep.asset_key for dep in asset.deps} & own for asset in specs}
    rank = {key: position for position, key in enumerate(TopologicalSorter(graph).static_order())}
    return tuple(sorted(results, key=lambda result: (rank.get(result.asset_key, len(rank)),
                                                     isinstance(result, dg.AssetCheckResult))))


def expectation_checks(spec: Spec) -> list[dg.AssetCheckSpec]:
    return [
        dg.AssetCheckSpec(
            expectation.name,
            asset=[spec.dataset, SILVER],
            description=f"{expectation.check} (on failure: {expectation.on_failure})",
            blocking=expectation.on_failure is OnFailure.FAIL,
        )
        for expectation in spec.expectations
    ]


def inputs_landed(dataset: str) -> dg.AutomationCondition:
    own = dg.AssetSelection.groups(dataset)
    handled = dg.AutomationCondition.newly_requested() | dg.AutomationCondition.newly_updated()
    return (
        (dg.AutomationCondition.newly_missing() | dg.AutomationCondition.any_deps_updated().ignore(own)).since(handled)
        & ~dg.AutomationCondition.any_deps_in_progress().ignore(own)
        & ~dg.AutomationCondition.in_progress()
    ).with_label(INPUTS_LANDED)


def input_automation(spec: Spec) -> dg.AutomationConditionSensorDefinition:
    return dg.AutomationConditionSensorDefinition(
        f"{spec.dataset}_on_inputs",
        target=dg.AssetSelection.groups(spec.dataset),
        run_tags=job_tags(spec),
        default_status=dg.DefaultSensorStatus.RUNNING,
        description=f"Runs {spec.dataset} when one of its inputs lands",
    )


def job_tags(spec: Spec) -> dict[str, str]:
    if not spec.release:
        return {DATASET_TAG: spec.dataset}
    release = Release.parse(spec.release)
    return {DATASET_TAG: spec.dataset, "project": release.project, "release": release.commit}


def engine_arguments(spec: Spec, context: dg.AssetExecutionContext) -> list[str]:
    arguments = ["run", f"--dataset={spec.dataset}", f"--trigger={trigger(context)}"]
    if context.has_partition_key:
        arguments.append(f"--partition={context.partition_key}")
    if spec.release:
        arguments.append(f"--release={spec.release}")
    if context.run.tags.get(FULL_REFRESH_TAG) == "true":
        arguments.append("--full-refresh")
    return arguments


def trigger(context: dg.AssetExecutionContext) -> str:
    tags = context.run.tags
    for tag, name in (("dagster/backfill", "backfill"), ("dagster/schedule_name", "schedule"),
                      ("dagster/from_automation_condition", "inputs"), ("dagster/sensor_name", "sensor")):
        if tag in tags:
            return name
    return "manual"
