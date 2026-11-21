package com.dataflow.engine.core.run;

import com.dataflow.engine.core.RecordingTower;
import com.dataflow.engine.core.SparkTest;
import com.dataflow.engine.core.draft.DraftImpact;
import com.dataflow.engine.core.draft.Impact;
import com.dataflow.engine.core.events.EventLog;
import com.dataflow.engine.core.flows.FlowGraphs;
import com.dataflow.engine.core.flows.Sources;
import com.dataflow.engine.core.lake.DeltaTables;
import com.dataflow.engine.core.run.HttpTimeouts;
import com.dataflow.engine.core.serving.ServingDdl;
import com.dataflow.engine.core.serving.ServingSettings;
import com.dataflow.engine.core.transform.RunLake;
import com.dataflow.engine.core.transform.Steps;
import com.dataflow.spec.PipelineSpec;
import com.dataflow.spec.PipelineSpecLoader;
import com.dataflow.spec.PipelineSpecs;
import com.dataflow.spec.lake.Lake;
import com.dataflow.spec.lake.Layer;
import java.time.Duration;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SparkTest
class DerivedRunTest {

    private static final String SPEC = """
            dataset: regions
            version: 3
            inputs:
              orders: shop.gold.orders
              customers: shop.reference.customers
            gold:
              - name: latest_orders
                from: orders
                materialize: false
                steps:
                  - dedupe: { keys: [orderId], latest-by: updatedAt }
                  - join: { with: customers, type: left, on: { customerId: customerId } }
                  - derive: { big: "amount >= 100" }
              - name: per_region
                from: latest_orders
                steps:
                  - filter: "region IS NOT NULL"
                  - aggregate: { by: [region], measures: { orders: "count(*)", revenue: "sum(amount)" } }
              - name: top_region
                sql: |
                  WITH ranked AS (SELECT region, rank() OVER (ORDER BY revenue DESC) AS position FROM per_region)
                  SELECT region FROM ranked WHERE position = 1
              - name: tagged
                from: latest_orders
                steps:
                  - select: [orderId, "region AS area"]
                  - rename: { area: zone }
                  - union: [orders]
                  - drop: [amount]
            serve:
              - { from: gold.per_region, table: region_revenue }
            """;

    private final SparkSession spark;
    private final RecordingTower tower = new RecordingTower();
    private final PipelineRunTest.FakeWarehouse warehouse = new PipelineRunTest.FakeWarehouse();

    @TempDir
    Path dir;

    private Lake lake;

    DerivedRunTest(SparkSession spark) {
        this.spark = spark;
    }

    @BeforeEach
    void setUp() {
        lake = new Lake(dir.resolve("lake").toUri().toString());
        write(lake.table(Layer.GOLD, "shop", "orders"), """
                SELECT * FROM VALUES
                  ('o1', 'c1', 50.0,  TIMESTAMP'2026-10-01 10:00:00'),
                  ('o1', 'c1', 120.0, TIMESTAMP'2026-10-01 11:00:00'),
                  ('o2', 'c2', 80.0,  TIMESTAMP'2026-10-01 10:00:00'),
                  ('o3', 'c3', 30.0,  TIMESTAMP'2026-10-01 10:00:00'),
                  ('o4', 'c9', 10.0,  TIMESTAMP'2026-10-01 10:00:00')
                AS t(orderId, customerId, amount, updatedAt)
                """);
        write(lake.table(Layer.REFERENCE, "shop", "customers"), """
                SELECT * FROM VALUES ('c1', 'north'), ('c2', 'north'), ('c3', 'south') AS t(customerId, region)
                """);
    }

    @Test
    void aPipelineWithoutSourceJoinsAndDistilsOtherDatasetsTables() {
        RunReport report = run(SPEC);

        assertThat(report.status()).isEqualTo(FlowStatus.COMPLETED);
        assertThat(report.flows())
                .extracting(FlowOutcome::flow)
                .containsExactlyInAnyOrder("gold.per_region", "gold.top_region", "gold.tagged", "serving");
        assertThat(new DeltaTables(spark).exists(lake.table(Layer.GOLD, "regions", "latest_orders")))
                .isFalse();
        assertThat(rows("per_region", "region, orders, revenue"))
                .containsExactly("north|2|200.0", "south|1|30.0");
        assertThat(rows("top_region", "region")).containsExactly("north");
        assertThat(gold("tagged").columns())
                .containsExactly("orderId", "zone", "customerId", "updatedAt", "computedAt");
        assertThat(gold("tagged").count()).isEqualTo(9);
        assertThat(warehouse.statements).anyMatch(sql -> sql.contains("region_revenue"));
    }

    @Test
    void aTableWaitsForItsInputsFirstRun() {
        RunReport report = run(SPEC.replace("shop.gold.orders", "shop.gold.returns"));

        assertThat(report.status()).isEqualTo(FlowStatus.COMPLETED);
        assertThat(report.flows())
                .filteredOn(outcome -> outcome.flow().equals("gold.per_region"))
                .singleElement()
                .satisfies(outcome -> assertThat(outcome.result().details())
                        .containsEntry("skipped", "input orders (shop.gold.returns) has no data yet"));
    }

    @Test
    void aJoinRefusesColumnsThatWouldClash() {
        write(lake.table(Layer.REFERENCE, "shop", "customers"), """
                SELECT * FROM VALUES ('c1', 'north', 1.0) AS t(customerId, region, amount)
                """);

        RunReport report = run(SPEC);

        assertThat(report.status()).isEqualTo(FlowStatus.FAILED);
        assertThat(report.flows())
                .filteredOn(outcome -> outcome.flow().equals("gold.per_region"))
                .singleElement()
                .satisfies(outcome -> assertThat(outcome.error())
                        .contains("gold latest_orders step 2 (join): both sides have [amount]; set prefix"));
    }

    @Test
    void aPrefixKeepsBothSidesApart() {
        write(lake.table(Layer.REFERENCE, "shop", "customers"), """
                SELECT * FROM VALUES ('c1', 'north', 1.0) AS t(customerId, region, amount)
                """);

        run(SPEC.replace("on: { customerId: customerId } }", "on: { customerId: customerId }, prefix: customer }")
                .replace("region IS NOT NULL", "customerRegion IS NOT NULL")
                .replace("by: [region]", "by: [customerRegion]")
                .replace("SELECT region, rank()", "SELECT customerRegion AS region, rank()")
                .replace("\"region AS area\"", "\"customerRegion AS area\""));

        assertThat(rows("per_region", "customerRegion, orders")).containsExactly("north|1");
    }

    @Test
    void theImpactOfADraftComparesEveryTableOnTheWholeInputs() {
        PipelineSpecLoader loader = new PipelineSpecLoader();
        PipelineSpec saved = loader.parse(SPEC, "regions");
        PipelineSpec draft = loader.parse(SPEC.replace("region IS NOT NULL", "region = 'north'"), "regions");

        Impact impact = new DraftImpact(spark, lake, new DeltaTables(spark), Steps.NONE).derived(saved, draft);

        assertThat(impact.origin()).isNull();
        assertThat(impact.columns()).isEmpty();
        assertThat(impact.gold())
                .filteredOn(gold -> gold.name().equals("per_region"))
                .singleElement()
                .returns(2L, Impact.Gold::rowsBefore)
                .returns(1L, Impact.Gold::rowsAfter);
        assertThat(impact.gold()).allMatch(gold -> gold.error() == null);
    }

    private RunReport run(String yaml) {
        PipelineSpec plan = new PipelineSpecLoader().parse(yaml, "regions");
        PipelineSpecs plans = new PipelineSpecs(Map.of(plan.dataset(), plan));
        RunContext context = new RunContext(
                spark,
                lake,
                new DeltaTables(spark),
                new EventLog(spark, lake),
                plans,
                plan,
                "derived-run",
                null,
                Clock.systemUTC(),
                new Batching(2, 1),
                new RunLake(spark, lake, plan.dataset()),
                Steps.NONE);
        FlowGraph graph = new FlowGraphs(
                        new Sources(dir.resolve("cache"), new HttpTimeouts(Duration.ofSeconds(5), Duration.ofSeconds(30))),
                        warehouse,
                        new ServingDdl(new ServingSettings("serving", "lake", "1 DAY"), "s3a://lake"),
                        null)
                .forPlan(plan);
        return new PipelineRun(context, graph, tower, "manual", Map.of()).run();
    }

    private void write(String path, String sql) {
        spark.sql(sql).write().format("delta").mode("overwrite").option("overwriteSchema", true).save(path);
    }

    private Dataset<Row> gold(String table) {
        return spark.read().format("delta").load(lake.table(Layer.GOLD, "regions", table));
    }

    private List<String> rows(String table, String columns) {
        return gold(table).selectExpr(columns.split(", ")).orderBy(columns.split(", ")[0]).collectAsList().stream()
                .map(row -> row.mkString("|"))
                .toList();
    }
}
