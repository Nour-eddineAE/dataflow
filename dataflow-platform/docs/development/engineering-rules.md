# Engineering rules

The numbered rules (`RULE-*`) that every code, POM and YAML review checks against. [Code standards](code-standards.md) and [UI standards](ui-standards.md) cover how code and screens should read; these cover specific decisions.

### General

| Rule | Description |
|:---|:---|
| `RULE-G01` | Every module is a Maven module under one of the three repositories. |
| `RULE-G02` | No hardcoded credentials. |
| `RULE-G03` | Every repository has a README. |
| `RULE-G04` | Java 21 across all modules. Spring Boot 3.3.x or higher. |
| `RULE-G05` | Constructor injection only. |
| `RULE-G06` | Records for DTOs and event payloads. |

### Maven

| Rule | Description |
|:---|:---|
| `RULE-M01` | All dependency versions managed in `dataflow-bom`. No version numbers in module POMs. |
| `RULE-M02` | Every module inherits from `dataflow-parent`. Plugin versions in `pluginManagement`. |
| `RULE-M03` | Internal modules reference each other using `groupId`, `artifactId`, no version. |
| `RULE-M04` | Run `mvn dependency:analyze` before merging. Resolve all warnings. Enforced: `failOnWarning` in the parent; code that uses a library declares it; libraries loaded only at run time (starters, auto-configuration, Logback encoder, S3A, Delta extension, Flyway PostgreSQL) go in the module's `usedDependencies`, never a blanket ignore. |
| `RULE-M05` | Use Maven Enforcer Plugin with `dependencyConvergence`. |
| `RULE-M06` | Set `java.version` to 21 in the BOM. Use `release` configuration, not `source/target`. |
| `RULE-M07` | Build profiles documented in each module's README. Default profile must always work. |

### Spring Boot

| Rule | Description |
|:---|:---|
| `RULE-S01` | Use `@ConfigurationProperties` records with `@Validated`. No `@Value`. |
| `RULE-S02` | Constructor injection for all beans. |
| `RULE-S03` | Records for DTOs and event payloads. |
| `RULE-S04` | Profiles: `local`, `docker`, `ci`, `k8s`. Profile-specific config in `application-{profile}.yml`. |
| `RULE-S05` | The Workspace exposes actuator `health` for liveness and readiness probes. The engine, a one-shot run, has none. |
| `RULE-S07` | **[post-MVP]** Wrap external calls in Resilience4j `@CircuitBreaker` or `@Retry`. |
| `RULE-S08` | `@Transactional` only on service-layer mutating methods. Never on controllers. |
| `RULE-S09` | SLF4J + Logback with Logstash JSON encoder. MDC propagation of `dataset`, `runId`, `flow`. |

### Spark

| Rule | Description |
|:---|:---|
| `RULE-SP01` | `SparkSession` is a Spring singleton bean. Never create one inside a method. |
| `RULE-SP02` | Spark runs in `local[*]` mode. Cluster mode is configuration-only. |
| `RULE-SP03` | Use `Dataset<Row>` API exclusively. No RDD API. |
| `RULE-SP04` | Transformations are pure functions. No side effects. |
| `RULE-SP05` | Schemas are explicit. Validate against expected `StructType` before processing. |
| `RULE-SP08` | No `.collect()` or `.toLocalIterator()` of data in production. Collecting a micro-batch's list of new file paths (bronze) is allowed. |
| `RULE-SP09` | Cache only when reused twice or more. Always pair with `.unpersist()`. |
| `RULE-SP10` | Configure Kryo serialization explicitly. |
| `RULE-SP11` | Adaptive query execution enabled. |

### Retired

Not checked in reviews; listed so nobody brings them back by accident (v3 in lakehouse-architecture.md §15.4, ADR-14):

- RULE-K01..K08 (Kafka), RULE-N01..N06 (notifications), RULE-B01..B05 (Spring Batch), RULE-S06 (Kafka retry) and
  RULE-T07 (the notifications audit API): v3 has none of them; the tower retries runs.
- RULE-S10 (actuator metrics): runs report through the event log and the tower.
- RULE-SP06 (stage overwrite paths): replaced by RULE-F03. RULE-SP07 (Parquet intermediates): every lake table is Delta.
- RULE-C01 (bootstrap-only `application.yml`) and RULE-C04 (config server): base `application.yml` holds defaults and
  environments override them; there is no config server.
- RULE-D01, RULE-D03, RULE-D04 (a data-models module, schema versions by jar, MapStruct): shapes live in pipeline specs
  (`dataflow-spec`), versioned by the spec's `version`; stage typing uses the `StageDataset<S>` wrapper.

### Runs (v3)

| Rule | Description |
|:---|:---|
| `RULE-F01` | Every flow writes a `flow_progress` event to its dataset's event log. |
| `RULE-F02` | The event log is append-only; one Delta table per dataset. |
| `RULE-F03` | Streaming flows checkpoint under `system/checkpoints/<dataset>/<flow>/`; every `foreachBatch` write passes `txnAppId` + `txnVersion`. |
| `RULE-F04` | One active run per dataset (tower pool); one writer per Delta table. |
| `RULE-F05` | Bronze is raw + `_rescued_data`; typing happens in silver. |
| `RULE-F06` | Git is the source of truth for pipeline specs. They are authored in Git directly or through the Workspace, which validates, saves and commits them; the tower reloads after every save. (Amended 2026-10-25.) |
| `RULE-F07` | Deletion vectors stay off on tables ClickHouse reads, until ClickHouse handles them under time travel and the change feed. |

### Testing

| Rule | Description |
|:---|:---|
| `RULE-T01` | Every module has tests: spec (loader/validator), engine-core (runs on a local lake), engine (CLI), tower (pytest). |
| `RULE-T02` | **[post-MVP]** Integration tests use Testcontainers. No embedded H2 or in-memory Kafka. |
| `RULE-T03` | **[post-MVP]** JaCoCo coverage threshold of 70% on service classes. |
| `RULE-T04` | Tests are independent. Each test class boots its own containers (or shares via singletons with explicit cleanup). |
| `RULE-T05` | Test data in `src/test/resources`. No magic strings inline. |
| `RULE-T06` | Use AssertJ for assertions. |

### Helm **[post-MVP]**

All RULE-H01..H07 deferred. Docker Compose is the deployment target for MVP.

### CI/CD **[post-MVP]**

All RULE-CI01..CI06 deferred.

### Artifactory **[post-MVP]**

All RULE-A01..A03 deferred.

### Configuration

| Rule | MVP status | Description |
|:---|:---|:---|
| `RULE-C02` | Kept | `dataflow-environments` never contains secrets (External Secrets names them). |
| `RULE-C03` | Kept | Every configuration change of an install is a Git commit on `dataflow-environments`, applied by Argo CD. |

**MVP config approach:** each service has a self-contained `application.yml` with full inline configuration. `application-docker.yml` overrides for Docker Compose. No Spring Cloud Config client, no bootstrap context.

### Data Models

| Rule | Description |
|:---|:---|
| `RULE-D02` | Each layer has a `StructType` from the spec: `PipelineSpec.factSchema()` / `silverSchema()` / `currentSchema()`. |
