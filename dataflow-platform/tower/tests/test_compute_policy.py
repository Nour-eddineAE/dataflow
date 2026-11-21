import pytest

from dataflow_tower.compute_policy import ComputePolicy, mebibytes

POLICY = ComputePolicy(max_driver_memory="2g", max_driver_cores="2", max_executor_memory="4g", max_executor_cores=4,
                       max_executors="8")


def test_a_size_within_the_policy_runs_as_released():
    compute = {"driver-memory": "1800m", "driver-cores": 2, "executors": {"min": 1, "max": 8}}

    assert POLICY.limit(compute) == (compute, [])


def test_every_value_above_the_policy_is_lowered_and_named():
    compute = {"driver-memory": "3g", "executor-memory": "8g", "driver-cores": 4, "executor-cores": 5,
               "executors": {"min": 10, "max": 12}, "spark-conf": {"spark.sql.shuffle.partitions": "4"}}

    limited, lowered = POLICY.limit(compute)

    assert limited == {"driver-memory": "2g", "executor-memory": "4g", "driver-cores": 2, "executor-cores": 4,
                       "executors": {"min": 8, "max": 8}, "spark-conf": {"spark.sql.shuffle.partitions": "4"}}
    assert lowered == [
        "driver-memory 3g lowered to the install's limit of 2g",
        "executor-memory 8g lowered to the install's limit of 4g",
        "driver-cores 4 lowered to the install's limit of 2",
        "executor-cores 5 lowered to the install's limit of 4",
        "executors.min 10 lowered to the install's limit of 8",
        "executors.max 12 lowered to the install's limit of 8",
    ]
    assert compute["driver-memory"] == "3g"


@pytest.mark.parametrize("unset", [None, ""])
def test_unset_limits_change_nothing(unset):
    policy = ComputePolicy(max_driver_memory=unset, max_driver_cores=unset, max_executors=unset)
    compute = {"driver-memory": "64g", "driver-cores": 32, "executors": {"max": 500}}

    assert policy.limit(compute) == (compute, [])


def test_sizes_compare_in_mebibytes():
    assert mebibytes("1800m") == 1800
    assert mebibytes("2G") == 2048
    with pytest.raises(ValueError):
        mebibytes("2 gigs")
