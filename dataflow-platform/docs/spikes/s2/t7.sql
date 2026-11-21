SELECT 'bridge view after spark append' k, count()
FROM arrivals.s1_current;
SELECT 'nodv latest' k, count(), countIf(__END_AT IS NULL)
FROM deltaLake(lake, filename = 's1/history_nodv/');
SELECT 'nodv time travel v1' k, count()
FROM deltaLake(lake, filename = 's1/history_nodv/') SETTINGS delta_lake_snapshot_version = 1;
SELECT 'nodv time travel v0' k, count()
FROM deltaLake(lake, filename = 's1/history_nodv/') SETTINGS delta_lake_snapshot_version = 0;
SELECT _change_type, _commit_version, count()
FROM deltaLake(lake, filename = 's1/history_nodv/')
GROUP BY ALL
ORDER BY ALL SETTINGS delta_lake_snapshot_start_version = 1, delta_lake_snapshot_end_version = 2;
