import json
import pytest
import re
from copy import deepcopy
from pydantic import ValidationError

from dataflow_tower.driver_job import SparkSettings, driver_job

RUN_ID = "9fcc673b-fd12-4d82-88cc-fbb20360609d"
SETTINGS = SparkSettings(namespace="dataflow", image="dataflow-engine:test", service_account="engine",
                         config_map="engine-config", secret="engine-env", pod_templates="engine-pods")
COMMAND = ["run", "--dataset=taxi_trips"]
BOOLEAN_SETTINGS = [
    "spark.sql.adaptive.enabled",
    "spark.sql.adaptive.coalescePartitions.enabled",
    "spark.sql.adaptive.skewJoin.enabled",
    "spark.sql.optimizer.dynamicPartitionPruning.enabled",
    "spark.sql.parquet.filterPushdown",
    "spark.eventLog.enabled",
]
PROTECTED_SETTINGS = {
    "spark.decommission.enabled": "true",
    "spark.storage.decommission.enabled": "true",
    "spark.storage.decommission.shuffleBlocks.enabled": "true",
    "spark.kubernetes.executor.deleteOnTermination": "true",
    "spark.dynamicAllocation.enabled": "true",
    "spark.dynamicAllocation.shuffleTracking.enabled": "false",
    "spark.dynamicAllocation.executorIdleTimeout": "60s",
}


def container(job):
    return job["spec"]["template"]["spec"]["containers"][0]


def conf(job):
    args = container(job)["args"]
    return dict(args[i + 1].split("=", 1) for i, arg in enumerate(args) if arg == "--conf")


def test_defaults_build_a_job_that_runs_the_engine_as_the_driver():
    job = driver_job(SETTINGS, RUN_ID, "taxi_trips", COMMAND, {})

    assert job["apiVersion"] == "batch/v1"
    assert job["kind"] == "Job"
    assert job["metadata"] == {
        "name": f"df-{RUN_ID.replace('-', '')}-taxi-trips",
        "namespace": SETTINGS.namespace,
        "labels": {"dataflow.io/run": RUN_ID, "dataflow.io/dataset": "taxi_trips"},
    }
    assert job["spec"]["backoffLimit"] == 0
    assert job["spec"]["ttlSecondsAfterFinished"] == 600
    pod = job["spec"]["template"]["spec"]
    assert pod["restartPolicy"] == "Never"
    assert pod["serviceAccountName"] == SETTINGS.service_account
    args = container(job)["args"]
    assert container(job)["image"] == SETTINGS.image
    assert container(job)["imagePullPolicy"] == "IfNotPresent"
    assert args[:7] == ["driver", "--master", "k8s://https://kubernetes.default.svc", "--class",
                        "com.dataflow.engine.EngineApplication", "--driver-memory", "2g"]
    assert args[-3:] == ["local:///opt/dataflow/dataflow-engine.jar", *COMMAND]


def test_the_driver_tells_spark_how_executors_reach_it_from_its_own_pod():
    job = driver_job(SETTINGS, RUN_ID, "taxi_trips", COMMAND, {})

    env = {entry["name"]: entry for entry in container(job)["env"]}
    assert env["SPARK_DRIVER_BIND_ADDRESS"]["valueFrom"] == {"fieldRef": {"fieldPath": "status.podIP"}}
    assert env["SPARK_DRIVER_POD_NAME"]["valueFrom"] == {"fieldRef": {"fieldPath": "metadata.name"}}
    settings = conf(job)
    assert settings["spark.driver.host"] == "$(SPARK_DRIVER_BIND_ADDRESS)"
    assert settings["spark.kubernetes.driver.pod.name"] == "$(SPARK_DRIVER_POD_NAME)"
    assert settings["spark.kubernetes.namespace"] == SETTINGS.namespace
    assert settings["spark.kubernetes.container.image"] == SETTINGS.image
    assert settings["spark.kubernetes.authenticate.executor.serviceAccountName"] == SETTINGS.service_account
    assert settings["spark.kubernetes.authenticate.oauthTokenFile"].endswith("/serviceaccount/token")
    assert settings["spark.kubernetes.authenticate.caCertFile"].endswith("/serviceaccount/ca.crt")


def test_executors_get_their_template_from_a_mounted_config_map():
    job = driver_job(SETTINGS, RUN_ID, "taxi_trips", COMMAND, {})

    pod = job["spec"]["template"]["spec"]
    assert pod["volumes"] == [{"name": "pod-templates", "configMap": {"name": "engine-pods"}}]
    assert container(job)["volumeMounts"] == [
        {"name": "pod-templates", "mountPath": "/opt/dataflow/pod-templates", "readOnly": True}]
    assert conf(job)["spark.kubernetes.executor.podTemplateFile"] == "/opt/dataflow/pod-templates/executor.yaml"


def test_executors_carry_the_run_labels():
    settings = conf(driver_job(SETTINGS, RUN_ID, "taxi_trips", COMMAND, {}))

    assert settings["spark.kubernetes.executor.label.dataflow.io/run"] == RUN_ID
    assert settings["spark.kubernetes.executor.label.dataflow.io/dataset"] == "taxi_trips"


def test_the_executor_name_prefix_is_short_enough_for_spark():
    prefix = conf(driver_job(SETTINGS, RUN_ID, "a" * 1000, COMMAND, {}))["spark.kubernetes.executor.podNamePrefix"]

    assert len(prefix) <= 47
    assert re.fullmatch(r"[a-z0-9](?:[-a-z0-9]*[a-z0-9])?", prefix)


def test_the_defaults_scale_executors_with_the_load():
    settings = conf(driver_job(SETTINGS, RUN_ID, "taxi_trips", COMMAND, {}))

    assert PROTECTED_SETTINGS.items() <= settings.items()
    assert settings["spark.dynamicAllocation.minExecutors"] == "0"
    assert settings["spark.dynamicAllocation.maxExecutors"] == "4"
    assert settings["spark.dynamicAllocation.initialExecutors"] == "1"


def test_the_driver_pod_asks_for_its_heap_plus_the_overhead():
    assert container(driver_job(SETTINGS, RUN_ID, "taxi_trips", COMMAND, {}))["resources"] == {
        "requests": {"cpu": "1", "memory": "2560Mi"},
        "limits": {"cpu": "1", "memory": "2560Mi"},
    }
    small = SETTINGS.model_copy(update=dict(driver_memory="512m", memory_overhead="384m"))
    assert container(driver_job(small, RUN_ID, "taxi_trips", COMMAND, {}))["resources"]["limits"]["memory"] == "896Mi"


def test_a_cpu_request_reserves_less_than_the_pods_may_use():
    settings = SETTINGS.model_copy(update=dict(cpu_request="250m", driver_cores=2, executor_cores=3))

    job = driver_job(settings, RUN_ID, "taxi_trips", COMMAND, {})

    assert container(job)["resources"]["requests"]["cpu"] == "250m"
    assert container(job)["resources"]["limits"]["cpu"] == "2"
    assert conf(job)["spark.kubernetes.executor.request.cores"] == "250m"
    assert conf(job)["spark.kubernetes.executor.limit.cores"] == "3"


def test_without_a_cpu_request_pods_reserve_what_they_may_use():
    job = driver_job(SETTINGS, RUN_ID, "taxi_trips", COMMAND, {})

    assert container(job)["resources"]["requests"]["cpu"] == "1"
    assert conf(job)["spark.kubernetes.executor.request.cores"] == "1"


def test_a_single_pod_runs_spark_inside_the_driver_with_no_executors():
    settings = SETTINGS.model_copy(update=dict(single_pod=True, driver_memory="1080m", memory_overhead="720m"))

    job = driver_job(settings, RUN_ID, "taxi_trips", COMMAND, {}, {"spark-conf": {"spark.sql.shuffle.partitions": "8"}})

    args = container(job)["args"]
    assert args[args.index("--master") + 1] == "local[*]"
    assert container(job)["resources"]["limits"]["memory"] == "1800Mi"
    spark = conf(job)
    assert spark["spark.sql.shuffle.partitions"] == "8"
    assert not [key for key in spark if key.startswith(("spark.kubernetes.", "spark.dynamicAllocation.", "spark.executor."))]
    assert args[-3:] == ["local:///opt/dataflow/dataflow-engine.jar", *COMMAND]


def test_a_single_pod_still_checks_what_a_spec_asks_for():
    settings = SETTINGS.model_copy(update=dict(single_pod=True))

    with pytest.raises(ValueError, match="min must not exceed max"):
        driver_job(settings, RUN_ID, "taxi_trips", COMMAND, {}, {"executors": {"min": 3, "max": 1}})


def test_a_bad_overhead_is_rejected_without_being_repeated():
    settings = SETTINGS.model_copy(update=dict(memory_overhead="not-for-errors"))

    with pytest.raises(ValueError) as error:
        driver_job(settings, RUN_ID, "taxi_trips", COMMAND, {})

    assert "not-for-errors" not in str(error.value)


def test_settings_are_frozen():
    with pytest.raises(ValidationError):
        SETTINGS.image = "changed"


def test_admin_pod_templates_keep_identity_and_scheduling_settings_without_shared_mutation():
    template = {"metadata": {"labels": {"azure.workload.identity/use": "true"}},
                "spec": {"nodeSelector": {"pool": "spot"}, "volumes": [{"name": "extra", "emptyDir": {}}]}}
    settings = SETTINGS.model_copy(update=dict(driver_template=template))

    job = driver_job(settings, RUN_ID, "taxi_trips", COMMAND, {})
    pod = job["spec"]["template"]
    pod["spec"]["nodeSelector"]["pool"] = "drivers"

    assert template["spec"]["nodeSelector"] == {"pool": "spot"}
    assert "containers" not in template["spec"]
    assert [volume["name"] for volume in template["spec"]["volumes"]] == ["extra"]
    assert [volume["name"] for volume in pod["spec"]["volumes"]] == ["extra", "pod-templates"]
    assert pod["metadata"]["labels"] == {"azure.workload.identity/use": "true", **job["metadata"]["labels"]}


@pytest.mark.parametrize("dataset", ["Taxi_Trips", "A" * 1000, "---", "", " /Évents.?!", "a" * 26 + "_tail"])
def test_names_and_labels_are_valid_and_deterministic(dataset):
    first = driver_job(SETTINGS, RUN_ID, dataset, COMMAND, {})
    second = driver_job(SETTINGS, RUN_ID, dataset, COMMAND, {})

    assert first == second
    name = first["metadata"]["name"]
    assert name.startswith(f"df-{RUN_ID.replace('-', '')}-")
    assert len(name) + len("-xxxxx") <= 63
    assert re.fullmatch(r"[a-z0-9](?:[-a-z0-9]*[a-z0-9])?", name)
    for value in first["metadata"]["labels"].values():
        assert len(value) <= 63
        assert re.fullmatch(r"[A-Za-z0-9](?:[A-Za-z0-9_.-]*[A-Za-z0-9])?", value)
    assert driver_job(SETTINGS, RUN_ID.replace("-", "").upper(), dataset, COMMAND, {}) == first


def test_distinct_runs_have_distinct_names():
    first = driver_job(SETTINGS, RUN_ID, "taxi_trips", COMMAND, {})
    second = driver_job(SETTINGS, "00000000-0000-0000-0000-000000000001", "taxi_trips", COMMAND, {})

    assert first["metadata"]["name"] != second["metadata"]["name"]


def test_the_driver_receives_the_install_config_references():
    job = driver_job(SETTINGS, RUN_ID, "taxi_trips", COMMAND, {})

    assert container(job)["envFrom"] == [
        {"configMapRef": {"name": SETTINGS.config_map}},
        {"secretRef": {"name": SETTINGS.secret}},
    ]
    assert job["spec"]["template"]["metadata"]["labels"] == job["metadata"]["labels"]


def test_driver_uses_only_explicit_env_and_the_protected_k8s_profile(monkeypatch):
    monkeypatch.setenv("UNRELATED_TOWER_ENV", "not-forwarded")
    monkeypatch.setenv("DAGSTER_PIPES_CONTEXT", "not-forwarded")
    env = {
        "DAGSTER_PIPES_CONTEXT": "explicit-context",
        "DAGSTER_PIPES_MESSAGES": "explicit-messages",
        "ENGINE_DRAFT": "explicit-draft",
        "SPRING_PROFILES_ACTIVE": "spark",
    }

    job = driver_job(SETTINGS, RUN_ID, "taxi_trips", COMMAND, env)

    assert {entry["name"]: entry["value"] for entry in container(job)["env"] if "value" in entry} == {
        **env, "SPRING_PROFILES_ACTIVE": "k8s",
    }
    bare = driver_job(SETTINGS, RUN_ID, "taxi_trips", COMMAND, {})
    assert [entry for entry in container(bare)["env"] if "value" in entry] == [
        {"name": "SPRING_PROFILES_ACTIVE", "value": "k8s"},
    ]


def test_spark_tuning_also_reaches_the_engine_settings_that_would_override_the_command_line():
    compute = {"spark-conf": {"spark.eventLog.enabled": "false", "spark.sql.shuffle.partitions": "16"}}

    job = driver_job(SETTINGS, RUN_ID, "taxi_trips", COMMAND, {"SPRING_APPLICATION_JSON": "{}"}, compute)

    env = {entry["name"]: entry.get("value") for entry in container(job)["env"]}
    assert json.loads(env["SPRING_APPLICATION_JSON"]) == {
        "dataflow.spark.config[spark.eventLog.enabled]": "false",
        "dataflow.spark.config[spark.sql.shuffle.partitions]": "16",
    }


@pytest.mark.parametrize(
    "executors,expected",
    [({}, (2, 8)), ({"min": 0}, (0, 8)), ({"max": 3}, (2, 3)), ({"min": 4, "max": 6}, (4, 6))],
)
def test_executor_limits_inherit_independently(executors, expected):
    settings = SETTINGS.model_copy(update=dict(min_executors=2, max_executors=8))

    allocation = conf(driver_job(settings, RUN_ID, "taxi_trips", COMMAND, {}, {"executors": executors}))

    assert allocation["spark.dynamicAllocation.minExecutors"] == str(expected[0])
    assert allocation["spark.dynamicAllocation.maxExecutors"] == str(expected[1])
    assert allocation["spark.dynamicAllocation.initialExecutors"] == str(max(1, expected[0]))


@pytest.mark.parametrize(
    "defaults,executors",
    [((0, 4), {"min": 5}), ((3, 8), {"max": 2}), ((0, 4), {"min": 2, "max": 1}), ((5, 4), {})],
)
def test_invalid_effective_executor_bounds_are_rejected(defaults, executors):
    settings = SETTINGS.model_copy(update=dict(min_executors=defaults[0], max_executors=defaults[1]))

    with pytest.raises(ValueError, match="min must not exceed max"):
        driver_job(settings, RUN_ID, "taxi_trips", COMMAND, {}, {"executors": executors})


@pytest.mark.parametrize("key,values", [("min", [-1, True, 0.0, "0", None]), ("max", [0, -1, False, 1.5, "1", None])])
def test_executor_limits_require_integers(key, values):
    for value in values:
        with pytest.raises(ValueError, match=f"compute.executors.{key}"):
            driver_job(SETTINGS, RUN_ID, "taxi_trips", COMMAND, {}, {"executors": {key: value}})


def test_resources_and_ttl_inherit_settings_and_compute_overrides():
    settings = SETTINGS.model_copy(update=dict(driver_memory="3g", executor_memory="5g", driver_cores=2, executor_cores=3,
                                               ttl_seconds=120))
    compute = {"driver-memory": "768M", "executor-cores": 4}

    job = driver_job(settings, RUN_ID, "taxi_trips", COMMAND, {}, compute)

    args = container(job)["args"]
    assert args[args.index("--driver-memory") + 1] == "768M"
    assert container(job)["resources"]["limits"] == {"cpu": "2", "memory": "1280Mi"}
    spark = conf(job)
    assert spark["spark.driver.cores"] == "2"
    assert spark["spark.executor.memory"] == "5g"
    assert spark["spark.executor.cores"] == "4"
    assert spark["spark.kubernetes.executor.limit.cores"] == "4"
    assert spark["spark.executor.memoryOverhead"] == "512m"
    assert job["spec"]["ttlSecondsAfterFinished"] == 120


@pytest.mark.parametrize("role", ["driver", "executor"])
@pytest.mark.parametrize("value",
                         ["0g", "-1g", "1.5g", "2Gi", "1k", "2g\n", "2g --conf=spark.master=local", "", 2, None])
def test_memory_requires_a_positive_spark_size(role, value):
    with pytest.raises(ValueError, match=f"compute.{role}-memory must be a positive size"):
        driver_job(SETTINGS, RUN_ID, "taxi_trips", COMMAND, {}, {f"{role}-memory": value})


@pytest.mark.parametrize("role", ["driver", "executor"])
@pytest.mark.parametrize("value", [0, -1, True, 1.5, "2", None])
def test_cores_require_a_positive_integer(role, value):
    with pytest.raises(ValueError, match=f"compute.{role}-cores must be an integer"):
        driver_job(SETTINGS, RUN_ID, "taxi_trips", COMMAND, {}, {f"{role}-cores": value})


@pytest.mark.parametrize(
    "compute",
    [[], "invalid", {"env": {}}, {"image": "other"}, {"executors": {"initial": 1}}, {"executors": []},
     {"spark-conf": None}],
)
def test_unknown_compute_keys_and_malformed_sections_are_rejected(compute):
    with pytest.raises(ValueError, match="must be a mapping|contains unsupported settings"):
        driver_job(SETTINGS, RUN_ID, "taxi_trips", COMMAND, {}, compute)


@pytest.mark.parametrize("key", ["spark.sql.shuffle.partitions", "spark.default.parallelism"])
@pytest.mark.parametrize("value", ["1", "64", "+2", "0004", "2147483647"])
def test_positive_integer_tuning_is_allowed(key, value):
    spark = conf(driver_job(SETTINGS, RUN_ID, "taxi_trips", COMMAND, {}, {"spark-conf": {key: value}}))

    assert spark[key] == value
    assert PROTECTED_SETTINGS.items() <= spark.items()


@pytest.mark.parametrize("key", BOOLEAN_SETTINGS)
@pytest.mark.parametrize("value", ["true", "false"])
def test_exact_boolean_tuning_is_allowed(key, value):
    assert conf(driver_job(SETTINGS, RUN_ID, "taxi_trips", COMMAND, {}, {"spark-conf": {key: value}}))[key] == value


@pytest.mark.parametrize("value", ["-1", "0", "10", "10b", "2k", "2KB", "32m", "32mb", "1g", "1GB"])
def test_broadcast_threshold_accepts_only_java_compute_sizes(value):
    key = "spark.sql.autoBroadcastJoinThreshold"

    assert conf(driver_job(SETTINGS, RUN_ID, "taxi_trips", COMMAND, {}, {"spark-conf": {key: value}}))[key] == value


@pytest.mark.parametrize(
    "key,values",
    [
        ("spark.sql.shuffle.partitions",
         ["0", "-1", "1.5", "2147483648", "1\n", "1 --conf=spark.master=local", 4, True]),
        ("spark.default.parallelism", ["0", "false", "1; spark.jars=other", None]),
        *[(key, ["TRUE", "True", "1", "true\n", True]) for key in BOOLEAN_SETTINGS],
        ("spark.sql.autoBroadcastJoinThreshold", ["-2", "1.5g", "1t", "1GiB", "-1mb", "32mb\n", 32]),
    ],
)
def test_invalid_tuning_and_configuration_escape_values_are_rejected(key, values):
    for value in values:
        with pytest.raises(ValueError, match="invalid value") as error:
            driver_job(SETTINGS, RUN_ID, "taxi_trips", COMMAND, {}, {"spark-conf": {key: value}})
        assert str(error.value) == f"compute.spark-conf has an invalid value for {key}"


@pytest.mark.parametrize(
    "key",
    [
        "spark.master", "spark.jars", "spark.hadoop.fs.s3a.secret.key", "spark.driver.extraJavaOptions",
        "spark.kubernetes.driverEnv.SPRING_PROFILES_ACTIVE", "spark.kubernetes.driver.pod.name",
        "spark.sql.adaptive.enabled\n", *PROTECTED_SETTINGS,
    ],
)
def test_forbidden_settings_cannot_override_platform_controls_or_expose_values(key):
    with pytest.raises(ValueError) as error:
        driver_job(SETTINGS, RUN_ID, "taxi_trips", COMMAND, {}, {"spark-conf": {key: "not-for-errors"}})

    assert str(error.value) == "compute.spark-conf contains unsupported settings"


def test_bad_resource_values_and_unknown_keys_do_not_appear_in_errors():
    for compute in (
            {"driver-memory": "not-for-errors"},
            {"driver-cores": "not-for-errors"},
            {"executors": {"max": "not-for-errors"}},
            {"not-for-errors": {}},
            {"spark-conf": {"not-for-errors": "not-for-errors"}},
    ):
        with pytest.raises(ValueError) as error:
            driver_job(SETTINGS, RUN_ID, "taxi_trips", COMMAND, {}, compute)
        assert "not-for-errors" not in str(error.value)


def test_builder_preserves_inputs_and_returns_independent_manifests():
    command = list(COMMAND)
    env = {"ENGINE_DRAFT": 'value with "quotes", backslash \\ and\nnewline'}
    compute = {"executors": {"min": 1}, "spark-conf": {"spark.sql.shuffle.partitions": "32"}}
    before = deepcopy((command, env, compute))

    first = driver_job(SETTINGS, RUN_ID, "taxi_trips", command, env, compute)
    second = driver_job(SETTINGS, RUN_ID, "taxi_trips", command, env, compute)

    assert (command, env, compute) == before
    assert json.loads(json.dumps(first)) == first
    assert first == second
    assert json.dumps(first) == json.dumps(
        driver_job(SETTINGS, RUN_ID, "taxi_trips", command, dict(reversed(list(env.items()))), compute))
    container(first)["args"].append("changed")
    container(first)["env"][0]["value"] = "changed"
    container(first)["envFrom"][0]["configMapRef"]["name"] = "changed"
    first["metadata"]["labels"]["dataflow.io/dataset"] = "changed"
    assert (command, env, compute) == before
    assert second == driver_job(SETTINGS, RUN_ID, "taxi_trips", command, env, compute)
