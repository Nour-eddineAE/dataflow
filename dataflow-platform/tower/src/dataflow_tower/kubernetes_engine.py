from collections.abc import Callable
from http import HTTPStatus
from time import monotonic, sleep
from typing import TypeVar

from dagster import PipesClientCompletedInvocation, PipesEnvContextInjector, open_pipes_session
from dagster_k8s.job import get_job_name_from_run_id
from dagster_k8s.pipes import PipesK8sPodLogsMessageReader
from kubernetes import client, config
from kubernetes.client.exceptions import ApiException

from dataflow_tower.driver_job import SparkSettings, driver_job

JOB_NAME_LABEL = "batch.kubernetes.io/job-name"
COMPLETE = "Complete"
FAILED = "Failed"
STARTED = frozenset({"Running", "Succeeded", "Failed"})
REQUEST_TIMEOUT = 30
POLL_INTERVAL = 2

T = TypeVar("T")


class KubernetesEngine:
    def __init__(self, settings: SparkSettings, core_api=None, batch_api=None, poll_interval: float = POLL_INTERVAL):
        self._settings = settings
        self._core = core_api
        self._batch = batch_api
        self._startup_timeout = settings.startup_timeout_seconds
        self._run_timeout = settings.run_timeout_seconds
        self._poll_interval = poll_interval

    def run(self, context, *, dataset, arguments, payload, compute, task=False) -> PipesClientCompletedInvocation:
        self._connect()
        reader = PipesK8sPodLogsMessageReader()
        with open_pipes_session(context, PipesEnvContextInjector(), reader) as session:
            body = self._job(context.run_id, dataset, arguments, {**payload, **session.get_bootstrap_env_vars()}, compute)
            name = body["metadata"]["name"]
            self._batch.create_namespaced_job(self._namespace, body, _request_timeout=REQUEST_TIMEOUT)
            finished = False
            try:
                pod = self._wait(lambda: self._driver(name), self._startup_timeout, "Spark driver did not start")
                with reader.async_consume_pod_logs(context, self._core, pod, self._namespace):
                    outcome = self._wait(lambda: self._outcome(name), self._run_timeout,
                                         "Spark run exceeded its time limit")
                    finished = True
                if outcome == FAILED:
                    raise RuntimeError("Spark run failed; see the driver logs")
            finally:
                if not finished:
                    self._cancel(name, context)
        return PipesClientCompletedInvocation(session)

    @property
    def _namespace(self) -> str:
        return self._settings.namespace

    def _job(self, run_id, dataset, arguments, env, compute) -> dict:
        body = driver_job(self._settings, run_id, dataset, arguments, env, compute)
        body["spec"]["activeDeadlineSeconds"] = max(1, int(self._startup_timeout + self._run_timeout))
        if owner := self._run_job(run_id):
            body["metadata"]["ownerReferences"] = [owner]
        return body

    def _connect(self) -> None:
        if None in (self._core, self._batch):
            config.load_incluster_config()
            self._core = client.CoreV1Api()
            self._batch = client.BatchV1Api()

    def _run_job(self, run_id: str) -> dict | None:
        if self._settings.run_namespace not in ("", self._namespace):
            return None
        name = get_job_name_from_run_id(run_id)
        try:
            job = self._batch.read_namespaced_job(name, self._namespace, _request_timeout=REQUEST_TIMEOUT)
        except ApiException as error:
            if error.status == HTTPStatus.NOT_FOUND:
                return None
            raise
        return {"apiVersion": "batch/v1", "kind": "Job", "name": name, "uid": job.metadata.uid}

    def _driver(self, name: str) -> str | None:
        pods = self._core.list_namespaced_pod(self._namespace, label_selector=f"{JOB_NAME_LABEL}={name}",
                                              _request_timeout=REQUEST_TIMEOUT).items
        for pod in pods:
            if pod.status.phase in STARTED:
                return pod.metadata.name
        if self._outcome(name) == FAILED:
            raise RuntimeError("Spark run failed before its driver started")
        return None

    def _outcome(self, name: str) -> str | None:
        job = self._batch.read_namespaced_job(name, self._namespace, _request_timeout=REQUEST_TIMEOUT)
        for condition in job.status.conditions or []:
            if condition.status == "True" and condition.type in (COMPLETE, FAILED):
                return condition.type
        return None

    def _wait(self, read: Callable[[], T | None], timeout: float, message: str) -> T:
        deadline = monotonic() + timeout
        while (value := read()) is None:
            if monotonic() >= deadline:
                raise TimeoutError(message)
            sleep(min(self._poll_interval, max(0.0, deadline - monotonic())))
        return value

    def _cancel(self, name: str, context) -> None:
        try:
            self._batch.delete_namespaced_job(name, self._namespace, propagation_policy="Foreground",
                                              _request_timeout=REQUEST_TIMEOUT)
        except ApiException as error:
            if error.status != HTTPStatus.NOT_FOUND:
                context.log.warning("Could not cancel Spark driver job %s: HTTP %s", name, error.status)
