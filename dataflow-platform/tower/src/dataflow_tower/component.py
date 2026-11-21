from datetime import timedelta
from pathlib import Path
from tempfile import gettempdir

import dagster as dg
from pydantic import Field

from dataflow_tower.alerts import run_failure_email
from dataflow_tower.assets import (
    asset_specs,
    engine_arguments,
    expectation_checks,
    in_dependency_order,
    input_automation,
    job_tags,
    partitions,
)
from dataflow_tower.compute_policy import ComputePolicy
from dataflow_tower.driver_job import SparkSettings
from dataflow_tower.engine import DockerEngine, DockerSettings, Engine
from dataflow_tower.lake import Lake
from dataflow_tower.orphans import orphaned_runs
from dataflow_tower.releases import ReleaseStore, released_specs
from dataflow_tower.settings import RESOURCE, settings_resource
from dataflow_tower.specs import Spec
from dataflow_tower.tasks import engine_task_job


class PipelineSpecs(dg.Component, dg.Model, dg.Resolvable):
    releases: str | None = Field(None, description="Where the tower reads releases from: a folder or an s3:// prefix")
    target: str = Field("prod", description="The release target this tower runs")
    release_cache: str = Field(str(Path(gettempdir()) / "dataflow" / "releases"),
                               description="Local folder the tower unpacks releases into")
    freshness_hours: int = Field(12, description="Hours after which a scheduled table counts as stale")
    docker: DockerSettings | None = Field(None, description="How runs start as Docker containers")
    kubernetes: SparkSettings | None = Field(None, description="How runs start as Spark on Kubernetes")
    compute_policy: ComputePolicy = Field(ComputePolicy(), description="The most compute a spec may ask for")

    def build_defs(self, context: dg.ComponentLoadContext) -> dg.Definitions:
        specs = self._specs()
        jobs = {spec.dataset: self._job(spec) for spec in specs}
        return dg.Definitions(
            assets=[self._pipeline(spec) for spec in specs],
            jobs=[*jobs.values(), engine_task_job()],
            schedules=[
                dg.ScheduleDefinition(name=f"{spec.dataset}_schedule", job=jobs[spec.dataset],
                                      cron_schedule=spec.schedule, default_status=dg.DefaultScheduleStatus.RUNNING)
                for spec in specs if spec.schedule
            ],
            sensors=[run_failure_email(), *self._supervision(),
                     *(input_automation(spec) for spec in specs if spec.runs_on_inputs)],
            resources={"engine": dg.ResourceDefinition.hardcoded_resource(self._engine()),
                       RESOURCE: settings_resource(self._in_effect())},
        )

    def _supervision(self) -> list[dg.SensorDefinition]:
        return [orphaned_runs()] if self.kubernetes is None else []

    def _in_effect(self) -> "PipelineSpecs":
        return self if self.kubernetes is not None else self.model_copy(update={"docker": self.docker or DockerSettings()})

    def _engine(self) -> Engine:
        if self.kubernetes is not None:
            from dataflow_tower.kubernetes_engine import KubernetesEngine
            return KubernetesEngine(self.kubernetes)
        return DockerEngine(self.docker or DockerSettings())

    def _specs(self) -> list[Spec]:
        if not self.releases:
            return []
        store = ReleaseStore.open(self.releases, Lake.from_env())
        return released_specs(store, self.target, Path(self.release_cache))

    def _pipeline(self, spec: Spec) -> dg.AssetsDefinition:
        freshness = dg.FreshnessPolicy.time_window(fail_window=timedelta(hours=self.freshness_hours)) \
            if spec.schedule else None
        specs = asset_specs(spec, freshness)

        @dg.multi_asset(
            name=f"{spec.dataset}_pipeline",
            specs=specs,
            check_specs=expectation_checks(spec),
            can_subset=False,
            pool=f"run-{spec.dataset}",
            description=spec.description,
            required_resource_keys={"engine"},
        )
        def pipeline(context: dg.AssetExecutionContext):
            compute, lowered = self.compute_policy.limit(spec.compute)
            for change in lowered:
                context.log.warning(f"Compute policy: {change}")
            invocation = context.resources.engine.run(context, dataset=spec.dataset,
                                                      arguments=engine_arguments(spec, context), payload={},
                                                      compute=compute, task=False)
            return in_dependency_order(invocation.get_results(), specs)

        return pipeline

    def _job(self, spec: Spec):
        return dg.define_asset_job(
            name=f"{spec.dataset}_job",
            selection=dg.AssetSelection.groups(spec.dataset),
            partitions_def=partitions(spec),
            tags=job_tags(spec),
        )
