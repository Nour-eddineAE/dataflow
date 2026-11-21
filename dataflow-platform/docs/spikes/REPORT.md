# DataFlow v3 · spike report

Run 2026-10-24 on the laptop (macOS host 10 cores / 24 GB; Docker VM 4 CPUs / 4 GB). Design under
test: [LAKEHOUSE-ARCHITECTURE.md](../docs/design/lakehouse-architecture.md) §14.1. Credentials live in
`spikes/.secrets/` (mode 700) and were never printed.

| Spike             | Verdict             | One line                                                                                                                                              |
|:------------------|:--------------------|:------------------------------------------------------------------------------------------------------------------------------------------------------|
| S1 · Hangar       | **PASS**            | RustFS 1.0.3 holds Delta 4.4.1 through s3a: 14/14 operations, including streaming checkpoints and replay-safe `foreachBatch`                          |
| S2 · Jet bridge   | **PARTIAL**         | ClickHouse 26.10.2 reads every table and feature, `timestamp_ntz` included; time travel and the change feed are wrong on tables with deletion vectors |
| S3 · Tower        | **PASS** (fallback) | Dagster idles at 443 MiB; `PipesDockerClient` + Java works with a hand-written protocol; the community Java client is broken                          |
| S4 · Engine stack | **PASS**            | `mvn verify` green (155 tests) on Spark 4.2.0 / Delta 4.4.1 / Hadoop 3.5.0 after four pins and one source fix                                         |

---

## S1 · Hangar: RustFS + Delta through s3a

**Setup.** `spikes/compose.yml` service `rustfs` (`rustfs/rustfs:1.0.3`, `19000:9000`, `19001:9001`, named volume,
`mem_limit 512m`), keys generated into `.secrets/rustfs.env`, bucket `lake` created with `mc mb`. Script `s1/s1.py` ran
in `apache/spark:4.2.0-java21-python3` (`-u 0`, driver 1500m, container 2 GB) with the package list from the brief and
`--exclude-packages software.amazon.awssdk:bundle,...`. Ivy resolved 111 jars (145 MB) once into the `spikes_ivy-cache`
volume.

| #  | Test                                                                                     | Result | Seconds | Evidence                                                                             |
|:---|:-----------------------------------------------------------------------------------------|:-------|--------:|:-------------------------------------------------------------------------------------|
| 1  | `CREATE TABLE` with `TIMESTAMP_NTZ`, CDF, deletion vectors, `CLUSTER BY (factUniqueId)`  | pass   |     4.6 | features `changeDataFeed, clustering, deletionVectors, domainMetadata, timestampNtz` |
| 2  | Append 1,000 rows                                                                        | pass   |     1.8 |                                                                                      |
| 3  | SCD2 MERGE (staged-updates pattern): 100 changed, 50 new, 850 unchanged                  | pass   |     3.8 | current 1,050, closed 100, `numTargetRowsCopied 0`                                   |
| 4  | Change feed, batch                                                                       | pass   |     0.3 | 150 insert, 100 pre-image, 100 post-image                                            |
| 5  | `OPTIMIZE` (liquid)                                                                      | pass   |     0.5 | 7 files → 1                                                                          |
| 6  | `VACUUM RETAIN 0 HOURS`                                                                  | pass   |     9.6 |                                                                                      |
| 7  | MERGE after OPTIMIZE                                                                     | pass   |     2.0 |                                                                                      |
| 8  | `DELETE` writes a deletion vector                                                        | pass   |     0.6 | `numDeletionVectorsAdded 1`                                                          |
| 9  | Delta stream, `Trigger.AvailableNow`, checkpoint on s3a                                  | pass   |     3.0 | first 500, rerun 0, after append 100, total 600                                      |
| 10 | Change-feed stream with checkpoint                                                       | pass   |     1.2 |                                                                                      |
| 11 | `foreachBatch` with `txnAppId`/`txnVersion`, batch replayed after deleting `commits/0`   | pass   |     2.3 | 300 → still 300 after replay; direct write twice with same version: 310, 310         |
| 12 | Full taxi month into Delta                                                               | pass   |     2.4 | 2,964,624 rows; `tpep_*_datetime` read as `timestamp_ntz`                            |
| 13 | History-shaped taxi table (ntz, CDF, DV, `CLUSTER BY`, `__START_AT`/`__END_AT`) + UPDATE | pass   |     5.5 | 676 rows closed, 3 DVs                                                               |

Whole run 140 s wall, first ivy resolve included. Only benign warnings (AQE disabled in streaming, plan string
truncated, native Hadoop missing).

**Surprises.**

- RustFS sat at ~395 MiB RSS after S1 + S2 (MinIO idles at ~86 MiB). It stayed under its 512 MiB limit; keep the limit
  and watch it.
- MERGE metrics name deletion vectors differently from DELETE (`numDeletionVectorsAdded` is absent on MERGE), so "did
  this MERGE write DVs" is not visible from the MERGE metrics alone.

**Impact.** ADR-08 holds: RustFS replaces MinIO, no SeaweedFS fallback. The s3a settings carry over unchanged plus
`fs.s3a.endpoint.region` (SDK v2 wants a region).

---

## S2 · Jet bridge: ClickHouse 26.10.2 reading Delta

**Setup.** `spike-clickhouse` (profile `s2`, `max_server_memory_usage` 1 GiB, container 1280m) on the spike network and
`dataflow_default`. Named collections `lake` (RustFS bucket) and `minio` (the real 2.0 bucket), keys `from_env`, so no
query carries a secret.

| Check                                                             | Result           | Evidence                                                                                                                                           |
|:------------------------------------------------------------------|:-----------------|:---------------------------------------------------------------------------------------------------------------------------------------------------|
| `timestamp_ntz`                                                   | pass             | `Nullable(DateTime64(6))`, values identical to Spark (`10:00:00` stays `10:00:00`, taxi `15:25:27` stays)                                          |
| Liquid clustering                                                 | pass             | clustered table reads normally                                                                                                                     |
| Deletion vectors, latest snapshot                                 | pass             | 1,169 rows, the DV-deleted fact absent, same as Spark                                                                                              |
| Deletion vectors, time travel (`delta_lake_snapshot_version`)     | **fail**         | v4 / v5 return 1,190 rows; Spark says 1,170 / 1,169. DVs are ignored                                                                               |
| Change feed (`delta_lake_snapshot_start_version` / `end_version`) | pass without DVs | `_change_type`, `_commit_version` virtual columns; matches Spark exactly on a DV-free copy                                                         |
| Change feed on a DV delete                                        | **fail**         | 151 `delete` rows for a 1-row delete (Spark: 1)                                                                                                    |
| Change feed past `VACUUM`                                         | expected error   | `_change_data` file vacuumed, `ParquetError ... not found`                                                                                         |
| `__START_AT` / `__END_AT` names                                   | pass             |                                                                                                                                                    |
| Real 2.0 tables on MinIO                                          | pass             | `history/taxi_trips` 199,991 rows (ntz), `gold/taxi_trips/daily_zone_revenue` 1,511, `history/earthquakes` 10,062 (10,041 current)                 |
| Bridge view sees new commits                                      | pass             | 1,049 → 1,059 on the next query after a Spark append, no refresh                                                                                   |
| Refreshable MV                                                    | pass             | `REFRESH EVERY 1 DAY TO t EMPTY AS SELECT ... FROM deltaLake(...)`; `SYSTEM REFRESH VIEW` + `SYSTEM WAIT VIEW`; 0 → 1,511 → 1,511 (atomic replace) |

Note the syntax: `EMPTY` goes after `TO <table>`; `REFRESH ... EMPTY TO ...` is a syntax error ("can't declare both
ENGINE and TO").

**Timings, 2,964,624-row taxi history table on RustFS** (seconds, after a server restart, then warm):

| Query                                                 | Cold |      Warm |
|:------------------------------------------------------|-----:|----------:|
| `count()`, `countIf(__END_AT IS NOT NULL)`            | 0.06 | 0.03–0.04 |
| Daily trips + revenue over current rows               | 0.08 | 0.05–0.08 |
| Top pickup zones by trips + avg distance              | 0.06 | 0.03–0.06 |
| Hourly p50 fare over one week                         | 0.03 |      0.03 |
| Point lookup by `factUniqueId` (subquery + full scan) | 0.51 |      0.52 |

Peak query memory 127 MiB; ClickHouse RSS 246 MiB.

**Impact.**

- **Deletion vectors stay off** on every lake table ClickHouse reads (history, gold, events). Latest-snapshot reads
  would be fine, but the shuttle and any ad-hoc time travel would silently return wrong rows. CDF and liquid clustering
  are on. Amends §10.3.
- **`timestamp_ntz` is fine**; the `timestamp` fallback is not needed and `WarehouseTypes.localize` goes away with the
  writer (§11.2).
- Bridge views need no refresh; the shuttle is a refreshable MV the arrivals flow refreshes per flight (§11.1 as
  designed).

---

## S3 · Tower: Dagster + Pipes + Java

**Setup.** `spike-dagster:1.13.26` (`python:3.12-slim` +
`dagster==1.13.26 dagster-webserver==1.13.26 dagster-postgres==0.29.26 dagster-docker==0.29.26 "sqlalchemy<2.1"`, 394
MB). Webserver (`13000:3000`), daemon, gRPC code server (`dagster code-server start`), spike Postgres.
`QueuedRunCoordinator` (max 2, `dataset` tag limit 1), `DefaultRunLauncher`, Docker socket mounted in the code server.

**Idle memory** (`docker stats`, 5 min after start): webserver 130 MiB, daemon 108 MiB, code server 182 MiB, Postgres 23
MiB = **443 MiB**. After a run: 431 MiB. Well under the ~1 GB threshold, so no `dagster dev` fallback.

**Pipes handshake.** Asset `java_hello` with check `rows_positive`, run through `PipesDockerClient` in
`eclipse-temurin:21-jre`.

| Attempt                                                                                                                                                                                | Result                                                                                                                                                                                                                                                                                          |
|:---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|:------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Community Java client `io.dagster:pipes:1.1.1` (on Maven Central, Aug 2025)                                                                                                            | **fails**: every message is written twice to stdout, plus debug `println`s (`writing message...`, `was: ...`, `resolved: ...`). Dagster sees two materializations: "Compute for op returned an output 'result' multiple times". It also pulls in AWS SDK `s3` 2.29 and picocli (51 jars, 16 MB) |
| Hand-written protocol: decode `DAGSTER_PIPES_CONTEXT` (base64 + zlib JSON), write `opened`, `log`, `report_asset_materialization`, `report_asset_check`, `closed` JSON lines to stdout | **pass**: run SUCCESS; materialization with typed metadata (`rows` int, `table` text) and check `SUCCEEDED`, both through GraphQL (`assetMaterializations`, `executionForLatestMaterialization`)                                                                                                |

**Surprises.**

- SQLAlchemy 2.1 (pulled by default) makes psycopg 3 the Postgres default; `dagster-postgres` 0.29.26 then fails (
  `No module named psycopg`, then "the connection is closed" once psycopg 3 is installed). Pin `sqlalchemy<2.1`.
- `java Main.java` (single-file source launch) needs `jdk.compiler`, absent from JRE images. Irrelevant for the engine (
  a jar).

**Impact.** ADR-05 holds. §8.2 changes from "community Java Pipes client, or at worst JSON lines" to "JSON lines": the
engine carries a ~100-line Pipes writer and no Dagster dependency, which also keeps ADR-05's "engine never imports
Dagster".

---

## S4 · Engine stack: our libraries on Spark 4.2

**Setup.** `rsync` of `dataflow-platform` to `spikes/s4/platform` (no `target/`, `data/`, `.git`). BOM: Spark 4.2.0,
Delta 4.4.1, AWS SDK 2.35.4, `io.netty:netty-bom:4.2.13.Final` imported before `spring-boot-dependencies`,
`jakarta.servlet-api` pin and the ClickHouse connector removed (BOM and writer). `mvn -B verify` on the host JVM.

| Run | Outcome                                                                                                                          | Fix                                                                                                                                                                                                  |
|:----|:---------------------------------------------------------------------------------------------------------------------------------|:-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 1   | Libraries green (datamodels, commons incl. `DeltaChunkStoreTest`, services-lib: 112 tests); feeder fails `dependencyConvergence` | `zookeeper-jute` 3.9.3 vs 3.9.5 → pin zookeeper + jute 3.9.5; `hadoop-client-api` / `-runtime` 3.5.0 vs 3.4.3 (via `orc-core`) → pin 3.5.0; `lz4-java` 1.10.1 (Kafka) vs 1.11.0 (Spark) → pin 1.11.0 |
| 2   | Feeder compile error: `package org.jetbrains.annotations does not exist`                                                         | Spark 4.0 leaked `org.jetbrains:annotations` transitively, 4.2 does not; drop the two `@NotNull`s                                                                                                    |
| 3   | **BUILD SUCCESS**, 155 tests, all modules (versioner Delta tests 54 s, writer change-feed tests 31 s)                            |                                                                                                                                                                                                      |

Resolved on the versioner classpath: Spring Boot 3.5.16, Jackson 2.21.4 (Boot's own line already matches Spark's 2.21),
Netty 4.2.13, antlr 4.13.2, Guava 32.0.0, protobuf 4.29.3.

**Impact.**

- Spark 4.2 + Delta 4.4 is a BOM change plus four convergence pins. Spring Boot stays on 3.5.x: with `netty-bom`
  imported first, Boot's Netty 4.1 management no longer applies, and Boot's Jackson already matches. Boot 4 stays a
  separate decision.
- The `jakarta.servlet-api` 5.0.0 pin must go (Spark 4.2's Jetty 12 needs Servlet 6). The engine has no web server, so
  the WebFlux workaround goes with the stage services.
- `lz4-java` disappears with Kafka; the pin is harmless until then.

---

## Changes to the design

| Where                | Was                                                | Now                                                                                                                    |
|:---------------------|:---------------------------------------------------|:-----------------------------------------------------------------------------------------------------------------------|
| §10.3 Delta features | Deletion vectors on history                        | **Off on every table ClickHouse reads**; CDF + liquid clustering on                                                    |
| §11.2                | `timestamp_ntz` unverified                         | Verified; plans keep `timestamp_ntz`                                                                                   |
| §8.2 Tower → engine  | Community Java Pipes client                        | Hand-written JSON-lines Pipes writer in the engine                                                                     |
| §13.2 Memory         | Tower unknown (0.5–2 GB)                           | Tower 443 MiB idle (4 containers)                                                                                      |
| §13.1 Tower image    | —                                                  | Pins `sqlalchemy<2.1`; `DefaultRunLauncher` (runs inside the code server, one engine container per flight)             |
| BOM                  | Spark 4.0.4, Delta 4.0.1, servlet 5 pin, connector | Spark 4.2.0, Delta 4.4.1, Hadoop 3.5.0, AWS SDK 2.35.4, netty-bom first, pins for zookeeper(-jute), hadoop-client, lz4 |
