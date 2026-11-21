SELECT count(), countIf(isCurrent), min(versionId), max(versionId)
FROM deltaLake(minio, filename = 'history/taxi_trips/');
DESCRIBE deltaLake(minio, filename = 'history/taxi_trips/') FORMAT TSV;
SELECT *
FROM deltaLake(minio, filename = 'gold/taxi_trips/daily_zone_revenue/') LIMIT 2 FORMAT Vertical;
SELECT count()
FROM deltaLake(minio, filename = 'gold/taxi_trips/daily_zone_revenue/');
SELECT count(), countIf(isCurrent), uniqExact(factUniqueId)
FROM deltaLake(minio, filename = 'history/earthquakes/');
DESCRIBE deltaLake(minio, filename = 'history/earthquakes/') FORMAT TSV;
