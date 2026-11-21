SELECT count() total, countIf(__END_AT IS NOT NULL) closed
FROM deltaLake(lake, filename = 's1/taxi_history/');
SELECT toDate(pickupTime) d, count() trips, round(sum(totalAmount)) revenue
FROM deltaLake(lake, filename = 's1/taxi_history/')
WHERE __END_AT IS NULL
GROUP BY d
ORDER BY d LIMIT 3;
SELECT pickupLocationId, count() trips, round(avg(tripDistance), 2) dist
FROM deltaLake(lake, filename = 's1/taxi_history/')
GROUP BY 1
ORDER BY trips DESC LIMIT 3;
SELECT toHour(pickupTime) h, round(quantile(0.5)(fareAmount), 2) p50
FROM deltaLake(lake, filename = 's1/taxi_history/')
WHERE pickupTime >= '2024-01-15 00:00:00'
  AND pickupTime < '2024-01-22 00:00:00'
GROUP BY h
ORDER BY h LIMIT 3;
SELECT *
FROM deltaLake(lake, filename = 's1/taxi_history/')
WHERE factUniqueId = (SELECT any (factUniqueId)
FROM deltaLake(lake, filename = 's1/taxi_history/')
WHERE pickupLocationId = 161) FORMAT Vertical;
