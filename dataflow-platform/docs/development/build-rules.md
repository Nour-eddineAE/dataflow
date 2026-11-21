# Build rules

How versions and build settings are kept in the Maven monorepo, and which library versions are pinned and why. Read this before changing a POM. The reasons behind the security pins are in [Security scan fixes](qodana-remediation.md).

## Project Versioning (Single Source of Truth)

The DataFlow project version lives in exactly **one** place: `dataflow-platform/.mvn/maven.config`, as a single line:

```
-Drevision=3.0.0-SNAPSHOT
```

Every POM in the monorepo references `${revision}` — never a literal version:

- The parent POM's own `<version>` is `${revision}`.
- Every child's `<parent>/<version>` is `${revision}`.
- The BOM's `<dependencyManagement>` entries for the internal libraries (`dataflow-spec`, `dataflow-engine-core`) use `<version>${revision}</version>`.

To bump the project version, change the single number in `.mvn/maven.config`. Maven reads that file before parsing any POM, so `${revision}` resolves to the new value across the entire reactor.

**`flatten-maven-plugin` is mandatory.** Without it, the published POM (the one Maven installs to `~/.m2` or deploys to a registry) contains the literal string `${revision}` and breaks downstream consumers. The plugin is configured in the parent POM's `<pluginManagement>` and applied in `<build><plugins>` so every module flattens at install/deploy. Use `<flattenMode>resolveCiFriendliesOnly</flattenMode>` — this resolves `${revision}`, `${sha1}`, `${changelist}` only, leaving everything else for normal inheritance.

Any POM that hardcodes a project or internal-artifact version (anywhere except the value inside `.mvn/maven.config`) is a violation and the agent must flag it during review.

## Property Placement Convention (Parent POM vs BOM)

The build configuration is conceptually one thing split across two files by Maven mechanics. Properties go in different places depending on where they're *referenced*:

| Belongs in the **parent POM** | Belongs in the **BOM** |
|:---|:---|
| Properties referenced by name (`${...}`) in any child POM | Managed dependency versions (groupId/artifactId/version in `<dependencyManagement>`) |
| Build-wide constants: encoding, `maven.compiler.release`, Scala binary suffix | Imported upstream BOMs (Spring Boot, Jackson 2 and 3, Jersey, AWS SDK) |
| Plugin versions and `<pluginManagement>` | Internal-library declarations using `${revision}` |
| Enforcer rules, build profiles, `<build>` config | Third-party library versions not covered by upstream BOMs |

**Why the split:** Maven properties travel through the `<parent>` chain but **not** through `<dependencyManagement>` `<scope>import</scope>`. A property defined only in the BOM is invisible to children that merely import the BOM. So any property name a child writes must be defined somewhere the child inherits from — that means the parent POM.

**Examples:**
- `spark.scala.binary` lives in the **parent POM** because children write `<artifactId>spark-sql_${spark.scala.binary}</artifactId>` in their dependencies.
- `spark.version` lives in the **BOM** because it only appears inside the BOM's `<dependencyManagement>` entries — children never type it (RULE-M01).

When in doubt: if the property name appears in a child POM as `${name}`, define it in the parent. If it only appears in `<dependencyManagement>` entries inside the BOM, define it in the BOM.

## Dependency Stack (decisions as of 2026-10-28)

Boot 4 and the security pins below came with PR #3 (`fix/qodana-findings`, merged 2026-10-28 as `11b12d4`; Qodana went from 212 findings to 0). Rationale and the CVE behind each pin: `dataflow-platform/docs/development/qodana-remediation.md`.

| Component | Version | Where pinned | Notes |
|:---|:---|:---|:---|
| Java | 21.0.7 | Enforcer `[21,22)` | |
| Maven | 3.9.15 | Enforcer `[3.9.15,)` | |
| Spring Boot | 4.1.2 | BOM (`spring-boot.version`) | Framework 7.0.10, Security 7.1.2, Tomcat 11.0.27, Netty 4.2.19 (no Netty pin). Needed for security fixes: Spring ships free fixes only on its newest line (6.2/6.5 fixes are Enterprise-only) |
| Apache Spark | 4.2.0 | BOM (`spark.version`) | Scala 2.13, Hadoop 3.5.0, Netty 4.2, Jetty 12 (Servlet 6); `spark-catalyst`, `spark-common-utils(-java)` also managed (declared directly per RULE-M04) |
| Jackson 2 | 2.21.8 | BOM: `com.fasterxml.jackson:jackson-bom` imported before Boot | Spark, `dataflow-spec`, engine; Boot 4.1.2 brings 2.21.7. YAML merge-depth advisory still lists 2.21.x but the fix is in (`YamlConstraintsTest`) |
| Jackson 3 | 3.1.8 | BOM: `tools.jackson:jackson-bom` imported before Boot | The Workspace (`tools.jackson`); Boot brings 3.1.7 |
| Jersey | 3.1.12 | BOM: `jersey-bom` imported before Boot | Not a CVE: Boot 4 manages Jersey 4.0, Spark 4.2 needs 3.1 |
| Scala binary suffix | 2.13 | **Parent** (`spark.scala.binary`) | Referenced by children |
| Delta Lake | 4.4.1 | BOM (`delta-lake.version`) | Excludes `log4j-slf4j2-impl` (via `unitycatalog-client`) |
| Hadoop client | 3.5.0 | BOM (`hadoop.version`: `hadoop-client-api`, `-runtime`) | Convergence: `orc-core` asks for 3.4.3 |
| AWS SDK v2 | 2.35.4 | BOM (imports `software.amazon.awssdk:bom`) | Slim `s3`, `s3-transfer-manager`, `sts` (runtime in the engine) instead of the ~600 MB `bundle` |
| ZooKeeper + jute | 3.9.6 | BOM | Convergence and CVEs (Spark declares 3.9.3) |
| lz4-java (`at.yawk.lz4`) | 1.12.1 | BOM | Convergence and CVE |
| antlr4-runtime | 4.13.2 | BOM | Convergence; Spark's parser and Hibernate's share 4.13 |
| Security pins (Spark/Hadoop transitives) | Parquet 1.18.1, Tink 1.24.0, Guava 33.7.2-jre, ASM 9.11.1, Ivy 2.6.0, XmlSchema 2.3.3, snappy-java 1.1.10.12, zstd-jni 1.5.7-24 | BOM | Each clears a published CVE; drop a pin once Spark or Boot brings the fixed version |
| Jasypt (core) | 1.9.3 | BOM | Used only by `libs/dataflow-config`; `jasypt-spring-boot` removed (CVE-2026-9370, unfixed) |
| Logback | 1.6.6 | BOM (`logback-core`, `logback-classic`) | CVE-2026-19880 fixed only from 1.6.3; Boot 4.1.2 brings 1.5.38, Boot 4.2 moves to 1.6 |
| Logstash Logback encoder | 8.0 | BOM | JSON logs (RULE-S09); verified with Logback 1.6.6 |
| Dagster | 1.13.26 (+ dagster-webserver; dagster-postgres, dagster-docker 0.29.26) | `tower/pyproject.toml` | `sqlalchemy<2.1` (2.1 makes psycopg 3 the default driver) |
| RustFS | 1.0.4 | `RUSTFS_VERSION` | |
| ClickHouse | 26.10.2 | `CLICKHOUSE_VERSION` | `deltaLake()`, change feed settings, refreshable MVs |
| Grafana | 11.4.0 | monitoring `.env` | ClickHouse plugin only; `GF_SECURITY_ALLOW_EMBEDDING` on |
| JGit | 7.8.0.202609011348-r | BOM | Workspace commits |
| jOOQ | 3.21.9 | BOM (overrides Boot's 3.19.35) | ClickHouse dialect is open source since 3.20; used only to build and render queries, no JDBC |
| Workspace UI | React 19.3, Mantine 9.7, React Query 5, Monaco 0.57 (+ `@monaco-editor/react` 4.7, self-hosted), React Flow 12, Vite 8, TypeScript 5.9, Vitest 5.0.5, ESLint 10.14, typescript-eslint 8.72, Stylelint 17.16 (`npm run lint`) | `ui/package.json` (exact pins) | Node 22.17 in the image's build stage; `overrides` raises Monaco's DOMPurify to 3.4.17 and ESLint's minimatch to 11.0.3 (`npm audit` clean) |

Removed from the BOM with v3: the ClickHouse Spark connector (no Spark 4.1/4.2 build), the `jakarta.servlet-api` 5.0.0 pin (Spark 4.2 needs Servlet 6). Removed with Boot 4: the Spring Cloud BOM (unused), the Netty, protobuf-java (now 4.36.2 from Boot) and `nimbus-jose-jwt` pins, `jasypt-spring-boot`.

**Excluded on purpose** (unfixed CVEs; `SecurityLibrariesTest` fails if json-smart, the Nimbus SDK or `jasypt-spring-boot` reappear on the Workspace classpath): `com.nimbusds:oauth2-oidc-sdk` from the Workspace's OAuth2 client (pulls `json-smart`, vulnerable through 2.6.0); `json-path`, `jsonassert`, `json-smart` from `spring-boot-starter-test` (`jsonassert` pulls `android-json`). Re-adding OIDC issuer discovery or JSON-path test assertions means reassessing these.

**Plugin versions** (parent POM, `<pluginManagement>`): flatten 1.7.3, enforcer 3.6.2, compiler 3.15.0, surefire and failsafe 3.5.5, dependency 3.8.1 (`failOnWarning`), checkstyle 3.6.0 with Checkstyle 14.3.0 (bound at `verify`, `checkstyle.xml`), spring-boot 4.1.2, spotless 3.4.0, jacoco 0.8.14.

**Override policy:** the BOM redeclares versions only for libraries Spring Boot does not manage, convergence pins, and security pins for a vulnerable version Boot or Spark brings (an imported BOM placed before Boot's wins, a direct `<dependencyManagement>` entry beats any import). For everything else Boot manages, inherit (RULE-M01).
