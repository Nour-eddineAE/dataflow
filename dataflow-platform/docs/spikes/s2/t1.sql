DESCRIBE deltaLake(lake, filename = 's1/history/') FORMAT TSV;
SELECT count() AS rows, countIf(__END_AT IS NULL) AS current, countIf(factUniqueId = 'k999') AS k999_rows, countIf(factUniqueId='k999' AND __END_AT IS NULL) AS k999_current_deleted_by_dv
FROM deltaLake(lake, filename = 's1/history/');
SELECT factUniqueId, amount, occurredAt, toTypeName(occurredAt) t, __START_AT, __END_AT
FROM deltaLake(lake, filename = 's1/history/')
WHERE factUniqueId IN ('k5', 'k210')
ORDER BY factUniqueId, __START_AT;
SELECT count()
FROM deltaLake(lake, filename = 's1/history/')
WHERE occurredAt >= '2026-10-05 00:00:00';
