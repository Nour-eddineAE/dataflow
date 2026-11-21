SELECT _file AS f, JSONExtractString(line, 'commitInfo', 'operation') op
FROM s3(lake, filename = 's1/history/_delta_log/*.json', format = 'LineAsString', structure = 'line String')
WHERE op != ''
ORDER BY f FORMAT TSV;
SELECT count()
FROM deltaLake(lake, filename = 's1/history/') SETTINGS delta_lake_snapshot_version = 2;
SELECT _change_type, _commit_version, count()
FROM deltaLake(lake, filename = 's1/history/')
GROUP BY ALL
ORDER BY ALL SETTINGS delta_lake_snapshot_start_version = 2, delta_lake_snapshot_end_version = 2;
