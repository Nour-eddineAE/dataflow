from pathlib import Path

import dagster as dg

import dataflow_tower
from conftest import EXAMPLES, RELEASES
from dataflow_tower.assets import asset_specs, engine_arguments, in_dependency_order, job_tags, partitions, trigger


def keys(spec):
    return [asset.key.to_user_string() for asset in asset_specs(spec)]


def test_every_layer_of_a_spec_is_an_asset(specs):
    assert keys(specs["earthquakes"]) == [
        "earthquakes/landing",
        "earthquakes/bronze",
        "earthquakes/silver",
        "earthquakes/quarantine",
        "earthquakes/history",
        "earthquakes/gold/daily_activity",
        "earthquakes/gold/regions",
        "earthquakes/serving/earthquakes",
        "earthquakes/serving/earthquakes_daily_activity",
        "earthquakes/serving/earthquakes_regions",
    ]


def test_gold_reads_history_and_references(specs):
    assets = {asset.key.to_user_string(): asset for asset in asset_specs(specs["taxi_trips"])}

    deps = {dep.asset_key.to_user_string() for dep in assets["taxi_trips/gold/daily_zone_revenue"].deps}
    assert deps == {"taxi_trips/history", "taxi_trips/reference/taxi_zones"}


def test_a_pipeline_without_source_has_only_its_stored_tables(specs):
    assert keys(specs["zone_hours"]) == [
        "zone_hours/gold/borough_hours",
        "zone_hours/gold/busiest",
        "zone_hours/gold/trip_days",
        "zone_hours/serving/borough_hours",
    ]


def test_gold_depends_on_other_pipelines_through_unstored_tables(specs):
    assets = {asset.key.to_user_string(): asset for asset in asset_specs(specs["zone_hours"])}

    def deps(key):
        return {dep.asset_key.to_user_string() for dep in assets[key].deps}

    assert deps("zone_hours/gold/borough_hours") == {"taxi_trips/history", "taxi_trips/reference/taxi_zones"}
    assert deps("zone_hours/gold/busiest") == {"zone_hours/gold/borough_hours", "earthquakes/gold/daily_activity"}
    condition = assets["zone_hours/gold/busiest"].automation_condition
    assert condition.get_label() == "inputs landed"
    assert "any_deps_missing" not in repr(condition).lower().replace(" ", "_")


def test_results_are_returned_in_dependency_order_whatever_order_they_came_in(specs):
    assets = asset_specs(specs["earthquakes"])
    key = {asset.key.to_user_string(): asset.key for asset in assets}
    served = dg.MaterializeResult(asset_key=key["earthquakes/serving/earthquakes_regions"])
    gold = dg.MaterializeResult(asset_key=key["earthquakes/gold/regions"])
    history = dg.MaterializeResult(asset_key=key["earthquakes/history"])
    check = dg.AssetCheckResult(asset_key=key["earthquakes/silver"], check_name="rule", passed=True)
    silver = dg.MaterializeResult(asset_key=key["earthquakes/silver"])

    ordered = in_dependency_order([served, check, gold, history, silver], assets)

    assert ordered == (silver, check, history, gold, served)


def test_a_multi_asset_accepts_results_in_dependency_order():
    first = dg.AssetSpec("first")
    second = dg.AssetSpec("second", deps=[first])

    @dg.multi_asset(specs=[first, second])
    def pipeline():
        results = [dg.MaterializeResult(asset_key=second.key), dg.MaterializeResult(asset_key=first.key)]
        return in_dependency_order(results, [first, second])

    assert dg.materialize([pipeline]).success


def test_a_derived_pipeline_with_nothing_to_report_stays_in_order(specs):
    assets = asset_specs(specs["zone_hours"])
    implicit = [dg.MaterializeResult(asset_key=asset.key) for asset in reversed(assets)]

    ordered = in_dependency_order(implicit, assets)

    position = {result.asset_key: index for index, result in enumerate(ordered)}
    for asset in assets:
        for dep in asset.deps:
            if dep.asset_key in position:
                assert position[dep.asset_key] < position[asset.key]


def test_a_scheduled_pipeline_is_not_run_by_its_inputs(specs):
    assert all(asset.automation_condition is None for asset in asset_specs(specs["earthquakes"]))
    assert not specs["earthquakes"].runs_on_inputs
    assert specs["zone_hours"].runs_on_inputs


def test_monthly_partitions_start_where_the_spec_says(specs):
    assert partitions(specs["taxi_trips"]).get_first_partition_key() == "2024-01"
    assert partitions(specs["earthquakes"]) is None


def test_jobs_are_tagged_with_dataset_and_release(specs):
    assert job_tags(specs["taxi_trips"]) == {
        "dataset": "taxi_trips",
        "project": "examples",
        "release": EXAMPLES.split("@")[1],
    }


def test_a_full_refresh_reaches_the_engine_only_when_asked_for(specs):
    class Context:
        has_partition_key = False

        def __init__(self, tags):
            self.run = type("Run", (), {"tags": tags})

    asked = engine_arguments(specs["earthquakes"], Context({"workspace/full-refresh": "true"}))
    plain = engine_arguments(specs["earthquakes"], Context({}))

    assert "--full-refresh" in asked
    assert "--full-refresh" not in plain
    assert plain[:2] == ["run", "--dataset=earthquakes"]


def test_the_trigger_comes_from_the_run_tags():
    class Context:
        def __init__(self, tags):
            self.run = type("Run", (), {"tags": tags})

    assert trigger(Context({"dagster/schedule_name": "s"})) == "schedule"
    assert trigger(Context({"dagster/backfill": "b"})) == "backfill"
    assert trigger(Context({"dagster/from_automation_condition": "true", "dagster/sensor_name": "s"})) == "inputs"
    assert trigger(Context({})) == "manual"


def test_definitions_load_with_a_check_per_expectation_and_a_schedule(monkeypatch):
    for name, value in {"RELEASES_ROOT": f"file://{RELEASES}", "RELEASES_TARGET": "prod",
                        "ENGINE_IMAGE": "dataflow-engine:test", "ENGINE_MEMORY": "1g", "TASK_MEMORY": "1g",
                        "HOST_DATA": "/data"}.items():
        monkeypatch.setenv(name, value)

    defs = dg.load_from_defs_folder(path_within_project=Path(dataflow_tower.__file__).parent)
    repository = defs.get_repository_def()

    checks = {key.name for key in repository.asset_graph.asset_check_keys}
    assert {"has_magnitude", "plausible_magnitude", "pickup_in_file_month", "dropoff_after_pickup"} <= checks
    assert [schedule.name for schedule in repository.schedule_defs] == ["earthquakes_schedule"]
    assert {job.name for job in repository.get_all_jobs()} >= {"earthquakes_job", "taxi_trips_job", "engine_task"}
    sensors = {sensor.name: sensor for sensor in repository.sensor_defs}
    assert set(sensors) == {"run_failure_email", "orphaned_runs", "zone_hours_on_inputs"}
    assert sensors["zone_hours_on_inputs"].default_status == dg.DefaultSensorStatus.RUNNING
    assert sensors["zone_hours_on_inputs"].run_tags["dataset"] == "zone_hours"
    assert repository.asset_graph.get(dg.AssetKey(["zone_hours", "gold", "busiest"])).parent_keys >= {
        dg.AssetKey(["earthquakes", "gold", "daily_activity"])}
    assert repository.get_job("engine_task").tags["dagster/max_retries"] == "0"
    engine = defs.resources["engine"].resource_fn(None)
    assert (engine._settings.image, engine._settings.memory, engine._settings.data_dir) == (
        "dataflow-engine:test", "1g", "/data")
