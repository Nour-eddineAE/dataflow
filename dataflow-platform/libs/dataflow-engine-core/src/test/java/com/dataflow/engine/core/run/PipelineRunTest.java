package com.dataflow.engine.core.run;

import com.dataflow.engine.core.RecordingTower;
import com.dataflow.engine.core.SparkTest;
import com.dataflow.engine.core.events.EventLog;
import com.dataflow.engine.core.flows.FlowGraphs;
import com.dataflow.engine.core.flows.Sources;
import com.dataflow.engine.core.lake.DeltaTables;
import com.dataflow.engine.core.run.HttpTimeouts;
import com.dataflow.engine.core.serving.ServingDdl;
import com.dataflow.engine.core.serving.ServingSettings;
import com.dataflow.engine.core.serving.Warehouse;
import com.dataflow.engine.core.tower.Severity;
import com.dataflow.engine.core.transform.RunLake;
import com.dataflow.engine.core.transform.Steps;
import com.dataflow.spec.PipelineSpec;
import com.dataflow.spec.PipelineSpecLoader;
import com.dataflow.spec.PipelineSpecs;
import com.dataflow.spec.lake.Lake;
import com.dataflow.spec.lake.Layer;
import io.delta.tables.DeltaTable;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Encoders;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.apache.spark.sql.functions.col;
import static org.assertj.core.api.Assertions.assertThat;

@SparkTest
class PipelineRunTest {

    private static final String RELEASE = "lab@a41c9e2f6b1d4e8a9c3b2f1e0d9c8b7a6f5e4d3c";
    private static final String HEADER = "sensor_id,read_at,temp_c,revised_at\n";
    private static final ServingSettings SERVING = new ServingSettings("serving", "lake", "1 DAY");
    private final SparkSession spark;
    private final FakeWarehouse warehouse = new FakeWarehouse();
    private final RecordingTower tower = new RecordingTower();

    @TempDir
    Path dir;

    private Lake lake;
    private PipelineSpecs plans;
    private PipelineSpec plan;

    PipelineRunTest(SparkSession spark) {
        this.spark = spark;
    }

    private static FlowOutcome outcome(RunReport report, String flow) {
        return report.flows().stream()
                .filter(outcome -> outcome.flow().equals(flow))
                .findFirst()
                .orElseThrow();
    }

    @BeforeEach
    void setUp() throws IOException {
        Path source = Files.createDirectories(dir.resolve("source"));
        Files.writeString(source.resolve("sensors.csv"), "id,site\ns1,north\ns2,north\ns3,south\ns4,south\n");
        Path folder = Files.createDirectories(dir.resolve("plans/readings"));
        try (InputStream template =
                Objects.requireNonNull(getClass().getResourceAsStream("/plans/readings/pipeline.yml"))) {
            String text = new String(template.readAllBytes(), StandardCharsets.UTF_8)
                    .replace("__LANDING_SOURCE__", source.toString());
            Files.writeString(folder.resolve("pipeline.yml"), text);
        }
        plans = new PipelineSpecLoader().loadAll(dir.resolve("plans"));
        plan = plans.get("readings");
        lake = new Lake(dir.resolve("lake").toUri().toString());
    }

    @Test
    void firstRun_writesEveryLayerAndReportsToTheTower() throws IOException {
        file("readings_1.csv", """
            s1,2026-10-01 10:00:00,21.5,2026-10-01 10:05:00
            s2,2026-10-01 10:00:00,19.0,2026-10-01 10:05:00
            s3,2026-10-01 10:00:00,,2026-10-01 10:05:00
            s4,2026-10-01 10:00:00,99.0,2026-10-01 10:05:00
            """);

        RunReport report = runOnce("f1", true);

        assertThat(report.status()).isEqualTo(FlowStatus.COMPLETED);
        assertThat(report.flows())
                .extracting(FlowOutcome::flow)
                .containsExactlyInAnyOrder(
                        "ingest",
                        "references",
                        "bronze",
                        "silver",
                        "history",
                        "gold.per_site",
                        "gold.per_sensor",
                        "serving",
                        "maintenance");
        assertThat(read(Layer.BRONZE).count()).isEqualTo(4);
        assertThat(read(Layer.SILVER).count()).isEqualTo(3);
        assertThat(read(Layer.QUARANTINE).select("failedExpectations").first().<String>getList(0))
                .containsExactly("has_celsius");
        assertThat(current().count()).isEqualTo(3);
        assertThat(spark.read()
                        .format("delta")
                        .load(lake.table(Layer.GOLD, "readings", "per_site"))
                        .count())
                .isEqualTo(2);
        assertThat(tower.materialized)
                .contains(
                        "readings/landing",
                        "readings/bronze",
                        "readings/silver",
                        "readings/quarantine",
                        "readings/history",
                        "readings/gold/per_site",
                        "readings/reference/sensors",
                        "readings/serving/readings",
                        "readings/serving/readings_per_site");
        assertThat(tower.checks)
                .extracting(RecordingTower.Check::name, RecordingTower.Check::passed)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("has_celsius", false),
                        org.assertj.core.groups.Tuple.tuple("plausible", false),
                        org.assertj.core.groups.Tuple.tuple("no_poison", true));
        assertThat(warehouse.statements)
                .anyMatch(sql -> sql.startsWith(
                        "CREATE OR REPLACE VIEW serving.readings DEFINER = CURRENT_USER SQL SECURITY DEFINER AS"))
                .anyMatch(sql -> sql.startsWith("SYSTEM REFRESH VIEW serving.readings_per_site_refresh"))
                .anyMatch(sql -> sql.startsWith("CREATE OR REPLACE VIEW serving.run_events"));
        Dataset<Row> events = spark.read().format("delta").load(lake.events("readings"));
        assertThat(events.where(col("event_type").equalTo("run"))
                        .select("status")
                        .collectAsList())
                .extracting(row -> row.getString(0))
                .containsExactly("RUNNING", "COMPLETED");
        assertThat(events.where(col("event_type").equalTo("expectation")).count())
                .isEqualTo(3);
        assertThat(events.where(col("event_type").equalTo("run"))
                        .select("details")
                        .as(Encoders.STRING())
                        .collectAsList())
                .allSatisfy(details -> assertThat(details).contains("\"release\":\"" + RELEASE + "\""));
        assertThat(DeltaTable.forPath(spark, lake.table(Layer.HISTORY, "readings"))
                        .detail()
                        .select("properties")
                        .first()
                        .<String, String>getJavaMap(0))
                .containsEntry("delta.deletedFileRetentionDuration", "interval 168 hours");
    }

    @Test
    void secondRunOnTheSameFile_changesNothing() throws IOException {
        file("readings_1.csv", "s1,2026-10-01 10:00:00,21.5,2026-10-01 10:05:00\n");
        runOnce("f1");
        long historyVersion = new DeltaTables(spark).version(lake.table(Layer.HISTORY, "readings"));

        RunReport second = runOnce("f2");

        assertThat(second.status()).isEqualTo(FlowStatus.COMPLETED);
        assertThat(outcome(second, "ingest").result().details().get("already_landed"))
                .asList()
                .hasSize(1);
        assertThat(outcome(second, "bronze").result().rowsOut()).isZero();
        assertThat(outcome(second, "history").result().rowsOut()).isZero();
        assertThat(read(Layer.BRONZE).count()).isEqualTo(1);
        assertThat(new DeltaTables(spark).version(lake.table(Layer.HISTORY, "readings")))
                .isEqualTo(historyVersion);
    }

    @Test
    void revision_closesTheOldVersion_whileALateRevisionIsIgnored() throws IOException {
        file("readings_1.csv", "s1,2026-10-01 10:00:00,21.5,2026-10-01 10:05:00\n");
        runOnce("f1");
        file("readings_2.csv", "s1,2026-10-01 10:00:00,22.0,2026-10-01 11:00:00\n");
        runOnce("f2");
        file("readings_3.csv", "s1,2026-10-01 10:00:00,30.0,2026-10-01 10:30:00\n");

        RunReport third = runOnce("f3");

        assertThat(outcome(third, "history").result().details()).containsEntry("stale_versions", 1L);
        List<Row> versions = read(Layer.HISTORY).orderBy("__START_AT").collectAsList();
        assertThat(versions).hasSize(2);
        assertThat(versions.get(0).<Double>getAs("celsius")).isEqualTo(21.5);
        assertThat(versions.get(0).<Object>getAs("__END_AT"))
                .isEqualTo(versions.get(1).getAs("__START_AT"));
        assertThat(versions.get(1).<Double>getAs("celsius")).isEqualTo(22.0);
        assertThat(versions.get(1).<Object>getAs("__END_AT")).isNull();
        assertThat(versions.get(1).<Timestamp>getAs("__START_AT").toInstant())
                .isEqualTo(Instant.parse("2026-10-01T11:00:00Z"));
    }

    @Test
    void fullRefresh_rebuildsEveryLayerFromTheLandedFiles() throws IOException {
        file("readings_1.csv", """
            s1,2026-10-01 10:00:00,21.5,2026-10-01 10:05:00
            s2,2026-10-01 10:00:00,19.0,2026-10-01 10:05:00
            """);
        runOnce("r1");
        assertThat(current().count()).isEqualTo(2);

        FullRefresh.reset(context("r2"));

        DeltaTables tables = new DeltaTables(spark);
        assertThat(tables.exists(lake.table(Layer.BRONZE, "readings"))).isFalse();
        assertThat(tables.exists(lake.table(Layer.SILVER, "readings"))).isFalse();
        assertThat(tables.exists(lake.table(Layer.HISTORY, "readings"))).isFalse();

        RunReport rebuilt = runOnce("r2");

        assertThat(rebuilt.status()).isEqualTo(FlowStatus.COMPLETED);
        assertThat(read(Layer.BRONZE).count()).isEqualTo(2);
        assertThat(read(Layer.SILVER).count()).isEqualTo(2);
        assertThat(current().count()).isEqualTo(2);
    }

    @Test
    void failingExpectation_failsTheRunAndSkipsEverythingDownstream() throws IOException {
        file("readings_1.csv", "poison,2026-10-01 10:00:00,21.5,2026-10-01 10:05:00\n");

        RunReport report = runOnce("f1");

        assertThat(report.status()).isEqualTo(FlowStatus.FAILED);
        Map<String, FlowStatus> statuses =
                report.flows().stream().collect(Collectors.toMap(FlowOutcome::flow, FlowOutcome::status));
        assertThat(statuses)
                .containsEntry("bronze", FlowStatus.COMPLETED)
                .containsEntry("silver", FlowStatus.FAILED)
                .containsEntry("history", FlowStatus.SKIPPED)
                .containsEntry("gold.per_site", FlowStatus.SKIPPED)
                .containsEntry("serving", FlowStatus.SKIPPED);
        assertThat(tower.checks).anySatisfy(check -> {
            assertThat(check.name()).isEqualTo("no_poison");
            assertThat(check.passed()).isFalse();
            assertThat(check.severity()).isEqualTo(Severity.ERROR);
        });
        assertThat(new DeltaTables(spark).exists(lake.table(Layer.HISTORY, "readings")))
                .isFalse();
    }

    private RunReport runOnce(String runId) {
        return runOnce(runId, false);
    }

    private RunContext context(String runId) {
        return new RunContext(
                spark,
                lake,
                new DeltaTables(spark),
                new EventLog(spark, lake),
                plans,
                plan,
                runId,
                null,
                Clock.systemUTC(),
                new Batching(2, 1),
                new RunLake(spark, lake, plan.dataset()),
                Steps.NONE);
    }

    private RunReport runOnce(String runId, boolean maintain) {
        RunContext context = context(runId);
        FlowGraph graph = new FlowGraphs(
                        new Sources(dir.resolve("cache"), new HttpTimeouts(Duration.ofSeconds(5), Duration.ofSeconds(30))),
                        warehouse,
                        new ServingDdl(SERVING, "s3a://lake"),
                        maintain ? new FlowGraphs.Maintenance(Duration.ofHours(1), 168) : null)
                .forPlan(plan);
        return new PipelineRun(context, graph, tower, "manual", Map.of("release", RELEASE)).run();
    }

    private void file(String name, String rows) throws IOException {
        Files.writeString(dir.resolve("source").resolve(name), HEADER + rows);
    }

    private Dataset<Row> read(Layer layer) {
        return spark.read().format("delta").load(lake.table(layer, "readings"));
    }

    private Dataset<Row> current() {
        return read(Layer.HISTORY).where(col("__END_AT").isNull());
    }

    static final class FakeWarehouse implements Warehouse {

        final List<String> statements = new ArrayList<>();

        @Override
        public synchronized void execute(String sql) {
            statements.add(sql);
        }

        @Override
        public synchronized List<List<String>> query(String sql) {
            statements.add(sql);
            return sql.startsWith("SELECT count()") ? List.of(List.of("1")) : List.of();
        }
    }
}
