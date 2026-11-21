#!/usr/bin/env bash
exec docker exec -i spike-clickhouse sh -c 'clickhouse-client -u "$CLICKHOUSE_USER" --password "$CLICKHOUSE_PASSWORD" --time --multiquery --format PrettyCompactMonoBlock "$@"' sh "$@"
