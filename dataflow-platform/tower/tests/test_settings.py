import dagster as dg
from dagster._core.remote_representation.external_data import RepositorySnap

from dataflow_tower.component import PipelineSpecs
from dataflow_tower.driver_job import SparkSettings
from dataflow_tower.settings import RESOURCE


def published(component: PipelineSpecs) -> dict[str, tuple[str, str, str]]:
    defs = component.build_defs(None)
    repository = dg.Definitions(jobs=defs.jobs, sensors=defs.sensors, resources=defs.resources).get_repository_def()
    resource = next(resource for resource in RepositorySnap.from_def(repository).resources if resource.name == RESOURCE)
    return {field.name: (field.type_key, field.default_value_as_json_str, field.description)
            for field in resource.config_field_snaps}


def test_the_tower_publishes_its_settings_in_effect_with_type_and_meaning():
    settings = published(PipelineSpecs.resolve_from_yaml("""
target: dev
docker:
  image: engine:1
compute_policy:
  max_executors: 3
"""))

    assert settings["tower.target"] == ("String", '"dev"', "The release target this tower runs")
    assert settings["tower.docker.image"] == ("String", '"engine:1"', "Engine image each run starts")
    assert settings["tower.docker.memory"][1] == '"1800m"'
    assert settings["tower.freshness_hours"][:2] == ("Int", "12")
    assert settings["tower.compute_policy.max_executors"][:2] == ("Int", "3")
    assert settings["tower.compute_policy.max_driver_memory"][1] == '""'
    assert not any(name.startswith("tower.kubernetes") for name in settings)


def test_a_docker_install_without_a_docker_block_shows_the_defaults_it_runs_with():
    assert published(PipelineSpecs())["tower.docker.image"][1] == '"dataflow-engine:latest"'


def test_a_kubernetes_install_shows_its_spark_settings_and_no_docker_ones():
    kubernetes = SparkSettings(namespace="jobs", image="engine", service_account="engine", config_map="engine",
                               secret="lake", pod_templates="engine-pods", single_pod=True)

    settings = published(PipelineSpecs(kubernetes=kubernetes))

    assert settings["tower.kubernetes.namespace"][1] == '"jobs"'
    assert settings["tower.kubernetes.single_pod"][:2] == ("Bool", "true")
    assert settings["tower.kubernetes.driver_template"][1] == '"{}"'
    assert not any(name.startswith("tower.docker") for name in settings)
