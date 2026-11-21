import dagster as dg

from dataflow_tower.assets import inputs_landed

MONTHLY = dg.MonthlyPartitionsDefinition(start_date="2024-01", fmt="%Y-%m")
TRIPS = dg.AssetKey(["taxi_trips", "history"])
HOURS = dg.AssetKey(["zone_hours", "gold", "hours"])
BUSIEST = dg.AssetKey(["zone_hours", "gold", "busiest"])


def definitions() -> dg.Definitions:
    condition = inputs_landed("zone_hours")

    @dg.multi_asset(specs=[
        dg.AssetSpec(HOURS, deps=[TRIPS], group_name="zone_hours", automation_condition=condition),
        dg.AssetSpec(BUSIEST, deps=[HOURS], group_name="zone_hours", automation_condition=condition),
    ], can_subset=False)
    def zone_hours():
        ...

    return dg.Definitions(assets=[dg.AssetSpec(TRIPS, partitions_def=MONTHLY, group_name="taxi_trips"), zone_hours])


def materialize(instance: dg.DagsterInstance, key: dg.AssetKey, partition: str | None = None) -> None:
    instance.report_runless_asset_event(dg.AssetMaterialization(key, partition=partition))


def test_a_new_pipeline_is_built_once_even_though_most_upstream_months_never_ran():
    defs, instance = definitions(), dg.DagsterInstance.ephemeral()

    first = dg.evaluate_automation_conditions(defs=defs, instance=instance)
    second = dg.evaluate_automation_conditions(defs=defs, instance=instance, cursor=first.cursor)

    assert first.total_requested == 2
    assert second.total_requested == 0


def test_a_landed_upstream_month_runs_it_again_but_its_own_tables_do_not():
    defs, instance = definitions(), dg.DagsterInstance.ephemeral()
    cursor = dg.evaluate_automation_conditions(defs=defs, instance=instance).cursor
    materialize(instance, HOURS)
    materialize(instance, BUSIEST)

    quiet = dg.evaluate_automation_conditions(defs=defs, instance=instance, cursor=cursor)
    materialize(instance, TRIPS, "2024-03")
    landed = dg.evaluate_automation_conditions(defs=defs, instance=instance, cursor=quiet.cursor)

    assert quiet.total_requested == 0
    assert landed.get_num_requested(HOURS) == 1
