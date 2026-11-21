import pytest
from contextlib import contextmanager
from dagster import PipesClientCompletedInvocation
from itertools import repeat
from kubernetes.client.exceptions import ApiException
from types import SimpleNamespace
from unittest.mock import Mock

from dataflow_tower import kubernetes_engine
from dataflow_tower.component import PipelineSpecs
from dataflow_tower.driver_job import SparkSettings
from dataflow_tower.kubernetes_engine import KubernetesEngine

RUN_ID = "9fcc673b-fd12-4d82-88cc-fbb20360609d"
RUN_JOB = f"dagster-run-{RUN_ID}"
SETTINGS = SparkSettings(namespace="dataflow", image="engine:test", service_account="engine",
                         config_map="engine-config", secret="engine-secret", pod_templates="engine-pods")


def job(*conditions):
    return SimpleNamespace(
        metadata=SimpleNamespace(uid="job-uid"),
        status=SimpleNamespace(conditions=[SimpleNamespace(type=kind, status=status) for kind, status in conditions]))


def pods(*phases):
    return SimpleNamespace(items=[SimpleNamespace(metadata=SimpleNamespace(name="driver"),
                                                  status=SimpleNamespace(phase=phase)) for phase in phases])


@pytest.fixture
def cluster(monkeypatch):
    core = Mock()
    core.list_namespaced_pod.return_value = pods("Running")
    batch = Mock()
    outcomes = repeat(job(("Complete", "True")))
    batch.read_namespaced_job.side_effect = lambda name, *args, **kwargs: (
        job() if name == RUN_JOB else next(cluster_state.outcomes))
    logs = Mock()
    session = SimpleNamespace(get_bootstrap_env_vars=lambda: {"DAGSTER_PIPES_CONTEXT": "context"})

    @contextmanager
    def opened(*args):
        yield session

    @contextmanager
    def consume(*args):
        logs(*args)
        yield

    monkeypatch.setattr(kubernetes_engine, "open_pipes_session", opened)
    monkeypatch.setattr(kubernetes_engine, "PipesK8sPodLogsMessageReader",
                        lambda: SimpleNamespace(async_consume_pod_logs=consume))
    context = SimpleNamespace(run_id=RUN_ID, log=Mock(), resources=SimpleNamespace())
    runner = KubernetesEngine(SETTINGS.model_copy(update={"startup_timeout_seconds": 0, "run_timeout_seconds": 0}), core, batch,
                              poll_interval=0)
    cluster_state = SimpleNamespace(core=core, batch=batch, logs=logs, context=context, runner=runner, outcomes=outcomes)
    return cluster_state


def launch(cluster):
    return cluster.runner.run(cluster.context, dataset="earthquakes", arguments=["run", "--dataset=earthquakes"],
                              payload={}, compute={}, task=False)


def created(cluster):
    return cluster.batch.create_namespaced_job.call_args.args[1]


def test_a_successful_run_streams_pipes_logs_and_leaves_the_finished_job_for_its_ttl(cluster):
    assert isinstance(launch(cluster), PipesClientCompletedInvocation)

    cluster.logs.assert_called_once_with(cluster.context, cluster.core, "driver", "dataflow")
    body = created(cluster)
    assert cluster.batch.create_namespaced_job.call_args.args[0] == "dataflow"
    assert body["spec"]["activeDeadlineSeconds"] == 1
    env = body["spec"]["template"]["spec"]["containers"][0]["env"]
    assert {entry["name"]: entry.get("value") for entry in env}["DAGSTER_PIPES_CONTEXT"] == "context"
    cluster.batch.delete_namespaced_job.assert_not_called()


def test_the_driver_is_found_by_the_label_kubernetes_gives_a_jobs_pods(cluster):
    launch(cluster)

    name = created(cluster)["metadata"]["name"]
    assert cluster.core.list_namespaced_pod.call_args.kwargs["label_selector"] == f"batch.kubernetes.io/job-name={name}"


def test_a_driver_that_is_still_pending_is_not_followed_yet(cluster):
    cluster.core.list_namespaced_pod.side_effect = [pods("Pending"), pods("Running")]
    cluster.outcomes = iter([job(), job(("Complete", "True"))])
    cluster.runner._startup_timeout = 5

    launch(cluster)

    assert cluster.core.list_namespaced_pod.call_count == 2


def test_a_failed_driver_reports_failure_and_retains_logs_until_ttl(cluster):
    cluster.outcomes = iter([job(("Failed", "True"))])

    with pytest.raises(RuntimeError, match="see the driver logs"):
        launch(cluster)

    cluster.logs.assert_called_once()
    cluster.batch.delete_namespaced_job.assert_not_called()


def test_a_condition_that_is_not_true_is_not_an_outcome(cluster):
    cluster.outcomes = iter([job(("Failed", "False")), job(("Complete", "True"))])
    cluster.runner._run_timeout = 5

    assert isinstance(launch(cluster), PipesClientCompletedInvocation)


def test_a_job_that_fails_before_its_driver_starts_is_reported(cluster):
    cluster.core.list_namespaced_pod.return_value = pods()
    cluster.outcomes = iter([job(("Failed", "True"))])

    with pytest.raises(RuntimeError, match="before its driver started"):
        launch(cluster)

    cluster.logs.assert_not_called()


def test_a_startup_timeout_cancels_the_job_and_its_children(cluster):
    cluster.core.list_namespaced_pod.return_value = pods()
    cluster.outcomes = iter([job()])

    with pytest.raises(TimeoutError, match="did not start"):
        launch(cluster)

    assert cluster.batch.delete_namespaced_job.call_args.kwargs["propagation_policy"] == "Foreground"


def test_cancellation_stops_the_job(cluster):
    def interrupted():
        yield job()
        raise KeyboardInterrupt()

    cluster.outcomes = interrupted()
    cluster.runner._run_timeout = 5

    with pytest.raises(KeyboardInterrupt):
        launch(cluster)

    cluster.batch.delete_namespaced_job.assert_called_once()


def test_a_job_already_gone_is_not_an_error_when_cancelling(cluster):
    cluster.core.list_namespaced_pod.return_value = pods()
    cluster.outcomes = iter([job()])
    cluster.batch.delete_namespaced_job.side_effect = ApiException(status=404)

    with pytest.raises(TimeoutError):
        launch(cluster)

    cluster.context.log.warning.assert_not_called()


def test_a_conflicting_job_is_never_deleted(cluster):
    cluster.batch.create_namespaced_job.side_effect = ApiException(status=409)

    with pytest.raises(ApiException):
        launch(cluster)

    cluster.batch.delete_namespaced_job.assert_not_called()


def test_kubernetes_hands_the_driver_only_the_run_payload(cluster):
    cluster.runner.run(cluster.context, dataset="earthquakes", arguments=["preview"],
                       payload={"ENGINE_DRAFT": "draft"}, compute={}, task=True)

    env = created(cluster)["spec"]["template"]["spec"]["containers"][0]["env"]
    assert {entry["name"] for entry in env if "value" in entry} == {
        "ENGINE_DRAFT", "DAGSTER_PIPES_CONTEXT", "SPRING_PROFILES_ACTIVE"}


def test_the_kubernetes_engine_loads_without_connecting_to_a_cluster():
    component = PipelineSpecs(kubernetes=SETTINGS)

    assert component.build_defs(None).resources["engine"] is not None


def test_the_driver_job_belongs_to_the_run_job_so_kubernetes_removes_it_with_the_run(cluster):
    launch(cluster)

    assert created(cluster)["metadata"]["ownerReferences"] == [
        {"apiVersion": "batch/v1", "kind": "Job", "name": RUN_JOB, "uid": "job-uid"}]
    cluster.batch.read_namespaced_job.assert_any_call(RUN_JOB, "dataflow", _request_timeout=30)


def test_outside_a_run_job_the_driver_job_has_no_owner(cluster):
    finished = cluster.batch.read_namespaced_job.side_effect

    def missing(name, *args, **kwargs):
        if name == RUN_JOB:
            raise ApiException(status=404)
        return finished(name, *args, **kwargs)

    cluster.batch.read_namespaced_job.side_effect = missing

    launch(cluster)

    assert "ownerReferences" not in created(cluster)["metadata"]


def test_a_driver_job_in_another_namespace_than_its_run_has_no_owner(cluster):
    cluster.runner._settings = SETTINGS.model_copy(update={"namespace": "dataflow-jobs", "run_namespace": "dataflow"})

    launch(cluster)

    assert "ownerReferences" not in created(cluster)["metadata"]
    assert cluster.batch.create_namespaced_job.call_args.args[0] == "dataflow-jobs"
    names = [call.args[0] for call in cluster.batch.read_namespaced_job.call_args_list]
    assert RUN_JOB not in names
