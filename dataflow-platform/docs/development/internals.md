# Internals

Code-level facts for anyone changing DataFlow: where things are, and details too small for the other pages. How each part
works is in [Engine](../engine.md), [Tower](../tower.md), [History and serving](../history-and-serving.md),
[Workspace](../workspace.md) and [Pipeline specs](../pipeline-specs.md). If this page and the code disagree, the code is
right: fix the page.

## Modules

| Module | Role |
|:---|:---|
| `dataflow-bom` | Managed versions |
| `libs/dataflow-config` | Shared by both apps: `EncryptedPropertiesEnvironmentPostProcessor` decrypts `ENC(...)` values in every property source, `EncryptionAutoConfiguration` supplies the `StringEncryptor` (stored Git credentials); core Jasypt only; package `@NullMarked` |
| `libs/dataflow-spec` | `PipelineSpec` records, `PipelineSpecLoader` (kebab-case YAML, unknown keys rejected), `PipelineSpecValidator`, `PipelineSpecs` registry; `StageDataset`, `SpecProjection`, `CanonicalEncoding`; stage tags `Bronze`, `Silver`, `Current` |
| `libs/dataflow-engine-core` | Plain Java, no Spring: `PipelineRun`, `FlowGraph`, `FlowGraphs`, the flows (`IngestFlow`, `ReferencesFlow`, `BronzeFlow`, `SilverFlow`, `HistoryFlow`, `GoldFlow`, `ServingFlow`, `MaintenanceFlow`), `Expectations`, `ScdMerge`, `EventLog`, `ServingDdl` + `ClickHouseHttp`, `PipesTower`, `MicroBatches`, `IdempotentWrites`, `Lake`, `DeltaTables` |
| `apps/dataflow-engine` | Spring Boot command line (`run --dataset= [--partition=] [--trigger=]`, `preview|impact --dataset= --task=`, `validate`); `EngineProperties` (`dataflow.engine`), `SparkProperties` (`dataflow.spark`); exit code 0 completed, 1 failed, 2 usage |
| `apps/dataflow-workspace` | Spring Boot MVC + React UI; one package per area, listed under [Workspace packages](#workspace-packages). `WorkspaceProperties` (`dataflow.workspace`) |
| `tower/` | Dagster code location: the `PipelineSpecs` component (`defs/pipelines/defs.yaml`), job `engine_task` (op `run_engine_task`), `run_failure_email` sensor; pytest |

Deleted in v3: datamodels, commons, services-lib, feeder, processor, versioner, writer, Kafka, notifications, Spring Batch, WebFlux/actuator, the ClickHouse Spark connector, Prometheus, Loki, Alloy, exporters.
| `libs/dataflow-transformation-api` | What a Java transformation implements; see [Transformations](../transformations.md) |

## Configuration

Each app has its own `application.yml`; profile files `application-docker.yml` and `application-spark.yml` override it.
There is no Spring Cloud Config.

### Layering and namespaces

Apps are always containerized, never co-located with infra. No `localhost` literals in config.

**The binding prefix never changes.** Beans bind to `dataflow.*` (`dataflow.engine`, `dataflow.spark`). That is the final, resolved config.

`env.*` is not a binding prefix. It is the namespace of intermediate inputs that exist only inside the yml to resolve `dataflow.*` and `spring.*` via `${...}`. Base `application.yml` defines the final tree from `${env.*}` plus applicative `env.*` defaults (batching, maintenance, serving database, Spark knob defaults); `application-docker.yml` maps environment-specific `env.*` (endpoints, credentials, paths) to env vars. Example: `dataflow.engine.lake-root: ${env.lake.root}`, docker: `env.lake.root: s3a://${LAKE_BUCKET}`.

### Spark

`SparkSession` is built from `SparkProperties` (`dataflow.spark`): `new SparkConf().setAppName(...)`, then the `config` map on top. Precedence: `application-spark.yml` map > `spark-submit --conf` / system properties > Spark defaults. The map lives only in `application-spark.yml` behind the `spark` profile (local master, Kryo, AQE, FAIR scheduler, Delta extension and catalog, deletion vectors off by default, abortable checkpoint file manager, Parquet 32 MB row groups, s3a). On Kubernetes, do not activate `spark`. **Footgun:** any key in that map beats the manifest; never put `spark.*` in base `application.yml` (only `env.spark.*` defaults live there).

### Secrets

No plaintext secrets in config. The engine reads `LAKE_SECRET_KEY` and `CLICKHOUSE_PASSWORD` as `ENC(...)`, decrypted by `libs/dataflow-config` (core Jasypt 1.9.3) with the one external secret `JASYPT_ENCRYPTOR_PASSWORD`; `jasypt-spring-boot` was removed (CVE-2026-9370, no fix). Algorithm `PBEWITHHMACSHA512ANDAES_256` + `RandomIvGenerator`, 1,000 iterations, Base64 (defaults in `ConfiguredStringEncryptor`, overridable by `jasypt.encryptor.algorithm` / `iv-generator-classname`); old ciphertext stays readable. The master key is required only when an `ENC(...)` value is read. Encrypt with `./dataflow encrypt` (in `dataflow-local`). Servers that cannot decrypt (RustFS, ClickHouse, Postgres, Dagster storage) get plaintext from `dataflow-local/.env` (gitignored, written by `./dataflow init`), never from git or an image. Post-v3 on Kubernetes: Sealed Secrets or External Secrets Operator.

## Specs

- The validator checks types with `DataType.fromDDL` and parses expressions and gold queries with `CatalystSqlParser`.
- `StageDataset<S>` (phantom `Bronze`, `Silver`, `Current`) wraps `Dataset<Row>`. `of()` checks names and types in order;
  `conform()` selects by name first. Stages are safe at compile time, schemas are checked at run time, and rows are never
  converted to objects.
- `factUniqueId` is the SHA-256 of `history.keys`. `contentHash` is the SHA-256 of `changeColumns()`: the declared silver
  columns minus `track-history-except`; derived columns never count. Fields go in declared order with a `\x1F` separator,
  null becomes empty, doubles use `%.6f` and timestamps are UTC ISO-8601.
- A landing file is named `landing/<ds>/<stem>.<sha256[0:16]>.<ext>`, with the partition key in the file name.

## Engine

- Bronze reads a `binaryFile` stream over landing, using only `path`, and reads each new file on its own. `Rescue.conform`
  puts unknown columns and values that do not cast into `_rescued_data` as JSON; names match case-insensitively.
- Writes cap `maxRecordsPerFile` at 500,000 and Delta streams read 2 files per batch (`Batching`). A full taxi month of
  2.96 million rows takes about 3 minutes.
- One INFO line per flow: `Flow silver COMPLETED in 16895 ms: 2964624 rows in, 2964550 out, 74 dropped`. The log context
  (MDC) carries `dataset`, `runId` and `flow`.
- The `ResolveWriteToStream` and `MapPartitionsRDD` warnings from Spark streaming are harmless and silenced by logger name
  in `logback-spring.xml`.

## Tower

- Each dataset gets one multi-asset, `<ds>_pipeline`, with `can_subset=False` and pool `run-<ds>`, and one job, `<ds>_job`,
  tagged `dataset`.
- `run_failure_email` is a plain-SMTP `run_failure_sensor`.

## Serving

- A copy table refreshes through `CREATE MATERIALIZED VIEW IF NOT EXISTS <t>_refresh REFRESH EVERY 1 DAY TO <t> EMPTY AS
  <source>`; `EMPTY` goes after `TO`. The table is rebuilt when `DESCRIBE` of table and source differ.
- The named collections come from the compose config `clickhouse-lake`; `clickhouse-lake-access` grants
  `named_collection_control`.
- ClickHouse cannot put `DESCRIBE` inside a subquery.
- An alias shadows the column of the same name in the same `SELECT`: `multiIf(countIf(status = ...)) AS status` is a nested
  aggregate. Give a derived value its own alias.

## Workspace

### Workspace packages

One row per package under `com.dataflow.workspace`; `python3 scripts/package-table.py` fails when a package and this
table disagree, and CI runs it.

| Package | Job |
|:--|:--|
| `access` | Who may do what: grants on securables, team ownership and the audit log, turned into ClickHouse roles and RustFS policies |
| `alerts` | Firing alerts read from Grafana |
| `catalog` | The lake catalog from specs and Delta logs: tables, columns and their roles, sizes, changes, landing files, quarantine and rescued rows; the ClickHouse views over Delta |
| `compute` | Compute targets per project and the policy that bounds them |
| `config` | `WorkspaceProperties` (`dataflow.workspace`) and the other bound settings |
| `controlplane` | The control-plane Postgres database: the jOOQ context and table definitions |
| `dashboards` | Pipeline boards: charts planned from column profiles, overviews, and freshness and run verdicts mirrored into ClickHouse for Grafana |
| `datasets` | Pipeline summaries and graphs |
| `favorites` | What each person starred |
| `folders` | Workspace folders: Git clones, branches, drafts, files, Git credentials and the GitHub app (JGit) |
| `housekeeping` | Cleanup of what the platform no longer needs (old releases, removed catalogs, leftovers), on demand and on a schedule |
| `impact` | What a draft spec changes against the deployed one, per area and severity |
| `lineage` | Column lineage through SQL, steps and Java tables |
| `preflight` | Checks before a run: the source answers and the spec is sound |
| `queries` | Saved queries and query alerts |
| `releases` | Releasing a folder's specs: snapshots in the object store, the release log, what is deployed |
| `runs` | Runs from the tower (`Tower` GraphQL) and the event log (`EventLog`): detail, output, comparison, quality, partitions, automation |
| `security` | Sign-in through Keycloak (OpenID Connect) and the API's access rules |
| `serving` | ClickHouse: the `ClickHouse` executor, `ClickHouseDsl`, `LakeFunctions`, `SystemTables`, SQL functions, serving health |
| `setup` | Checks of the platform services, and the effective settings with where each was set |
| `specs` | Stored pipeline specs, their checks and the problems found, with line locations |
| `tasks` | Engine tasks (preview, impact, step preview) started through the tower, and their states |
| `templates` | Text templates on the classpath: the new spec, the tower's GraphQL documents |
| `uploads` | Files people upload into the lake under `uploads/<person>/` |
| `web` | REST controllers, one per area, and the error body (`ApiErrors`) |
| `why` | The Trace tab: why a row or column has its value, from the event log (`Facts`) |

### Notes

- Listings, Delta logs and task results are read with `s3(lake, …, 'One' | 'JSONAsString' | 'RawBLOB')`. The repositories
  that build queries are `DeltaLog`, `LandingFiles`, `SystemTables`, `EventLog` and `Facts`.
- The `ClickHouse` executor renders jOOQ queries in the `CLICKHOUSE` dialect, with backslash escaping on and values
  inlined, and maps rows to records.
- Object-store credentials are used only for release artifacts, uploaded files and optional RustFS policy administration.
  Lake reads stay in ClickHouse.
- The JDK `HttpClient` that calls the tower must stay on HTTP/1.1, because Dagster's uvicorn mishandles h2c.
- The POM sets `<parameters>true</parameters>`: there is no Boot parent, and MVC needs parameter names.
- Inject `ObjectMapper`; Boot supplies a `JsonMapper`. The differences from Jackson 2 are in
  [Security scan fixes](qodana-remediation.md).
- A run's Log tab reads `capturedLogs`, with the file key from the `LogsCapturedEvent`.
- Schedules pause and resume through `startSchedule` and `stopRunningSchedule`. A stored STOPPED state overrides the
  spec's RUNNING default.
- Quarantine reasons come from `arrayJoin(failedExpectations)`.
- Dev: jar on :8090 against the local stack (`--env.*` overrides, `--env.workspace.ui-location=file:…/ui/dist/`), run from a copy outside `target/` (a Maven rebuild under a running jar breaks class loading); `npm run dev` in `ui/` proxies `/api` to it. UI unit tests: `npm test` (Vitest, also run by the image build).

## Building on the laptop

GitHub runs the suites; see [Code standards](code-standards.md#tests). When you do need Maven here:

- Set `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home` and use
  `~/.sdkman/candidates/maven/current/bin/mvn` if the shell defaults differ.
- The full check is `mvn verify dependency:analyze-only`.
- iCloud copies under `target/` (`Name 2.class` to `Name 9.class`) are removed by the build itself, through
  `maven-clean-plugin` in the parent POM.
- Qodana's log shows `Cannot resolve property ${revision}` warnings. They are harmless: dependencies still resolve.
