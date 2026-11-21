SELECT 'v5 count' k, count()
FROM deltaLake(lake, filename = 's1/history/') SETTINGS delta_lake_snapshot_version = 5;
SELECT 'v5 sum(1)' k, sum(1)
FROM deltaLake(lake, filename = 's1/history/') SETTINGS delta_lake_snapshot_version = 5;
SELECT 'v4 count' k, count()
FROM deltaLake(lake, filename = 's1/history/') SETTINGS delta_lake_snapshot_version = 4;
SELECT 'v4 sum(1)' k, sum(1)
FROM deltaLake(lake, filename = 's1/history/') SETTINGS delta_lake_snapshot_version = 4;
SELECT 'v2 sum(1)' k, sum(1)
FROM deltaLake(lake, filename = 's1/history/') SETTINGS delta_lake_snapshot_version = 2;
SELECT 'v2 count no trivial' k, count()
FROM deltaLake(lake, filename = 's1/history/') SETTINGS delta_lake_snapshot_version = 2, optimize_trivial_count_query = 0;
SELECT _change_type, _commit_version, count()
FROM deltaLake(lake, filename = 's1/history/')
GROUP BY ALL
ORDER BY ALL SETTINGS delta_lake_snapshot_start_version = 4, delta_lake_snapshot_end_version = 5;
