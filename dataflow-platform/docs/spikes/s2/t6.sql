CREATE
OR REPLACE VIEW arrivals.s1_current AS
SELECT *
EXCEPT
(__START_AT, __END_AT)
FROM deltaLake(lake, filename = 's1/history/') WHERE __END_AT IS NULL;
SELECT count()
FROM arrivals.s1_current;
CREATE TABLE IF NOT EXISTS arrivals.taxi_daily_zone_revenue
(
    day
    Date,
    borough
    String,
    zone
    String,
    paymentType
    Int32,
    trips
    UInt64,
    revenue
    Float64,
    avgDistance
    Float64,
    avgMinutes
    Float64,
    computedAt
    DateTime64
(
    6
)) ENGINE = MergeTree ORDER BY
(
    day,
    borough,
    zone,
    paymentType
);
CREATE
MATERIALIZED VIEW IF NOT EXISTS arrivals.taxi_daily_zone_revenue_mv REFRESH EVERY 1 DAY TO arrivals.taxi_daily_zone_revenue EMPTY AS
SELECT day, ifNull(borough, '') borough, ifNull(zone, '') zone, ifNull(paymentType, 0) paymentType, ifNull(trips, 0) trips, ifNull(revenue, 0) revenue, ifNull(avgDistance, 0) avgDistance, ifNull(avgMinutes, 0) avgMinutes, computedAt
FROM deltaLake(minio, filename = 'gold/taxi_trips/daily_zone_revenue/');
SELECT count() AS before_refresh
FROM arrivals.taxi_daily_zone_revenue;
SYSTEM
REFRESH VIEW arrivals.taxi_daily_zone_revenue_mv;
SYSTEM
WAIT VIEW arrivals.taxi_daily_zone_revenue_mv;
SELECT count() AS after_refresh
FROM arrivals.taxi_daily_zone_revenue;
SYSTEM
REFRESH VIEW arrivals.taxi_daily_zone_revenue_mv;
SYSTEM
WAIT VIEW arrivals.taxi_daily_zone_revenue_mv;
SELECT count() AS after_second_refresh
FROM arrivals.taxi_daily_zone_revenue;
SELECT view, status, last_success_time, last_refresh_time, next_refresh_time, read_rows, written_rows, exception
FROM system.view_refreshes FORMAT Vertical;
