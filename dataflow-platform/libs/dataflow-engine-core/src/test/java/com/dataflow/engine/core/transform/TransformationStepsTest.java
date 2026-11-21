package com.dataflow.engine.core.transform;

import com.dataflow.engine.core.SparkTest;
import com.dataflow.spec.GoldSpec;
import com.dataflow.spec.PipelineSpec;
import com.dataflow.spec.PipelineSpecLoader;
import com.dataflow.spec.lake.Lake;
import com.dataflow.spec.lake.Layer;
import com.dataflow.transformation.DataTransformation;
import com.dataflow.transformation.NoParams;
import com.dataflow.transformation.Transformation;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.RowFactory;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.StructType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static org.apache.spark.sql.functions.broadcast;
import static org.apache.spark.sql.functions.col;
import static org.apache.spark.sql.functions.count;
import static org.apache.spark.sql.functions.lit;
import static org.apache.spark.sql.functions.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SparkTest
class TransformationStepsTest {

    private static final String SPEC = """
        dataset: trips
        version: 3
        source: { path: /tmp/trips.csv, format: csv }
        silver:
          columns:
            - { name: tripId, type: string, required: true, from: trip_id }
            - { name: zoneId, type: int,                    from: zone_id }
            - { name: fare,   type: double,                 from: fare }
          steps:
            - name: zoned
              transformation: %s
              inputs: %s
              params: %s
              adds: %s
        history: { keys: [tripId] }
        references:
          - { name: zones, path: /tmp/zones.csv, format: csv }
        """;

    private static final StructType ROWS = new StructType()
            .add("tripId", DataTypes.StringType)
            .add("zoneId", DataTypes.IntegerType)
            .add("fare", DataTypes.DoubleType)
            .add("_source_file", DataTypes.StringType)
            .add("_ingested_at", DataTypes.TimestampType);
    private final SparkSession spark;

    @TempDir
    Path dir;

    private Lake lake;
    private Dataset<Row> rows;

    TransformationStepsTest(SparkSession spark) {
        this.spark = spark;
    }

    @BeforeEach
    void setUp() {
        lake = new Lake(dir.resolve("lake").toUri().toString());
        spark.createDataFrame(
                        List.of(RowFactory.create(161, "Manhattan"), RowFactory.create(132, "Queens")),
                        new StructType()
                                .add("LocationID", DataTypes.IntegerType)
                                .add("Borough", DataTypes.StringType))
                .write()
                .format("delta")
                .save(lake.table(Layer.REFERENCE, "trips", "zones"));
        Timestamp now = new Timestamp(0);
        rows = spark.createDataFrame(
                List.of(
                        RowFactory.create("t1", 161, 12.5, "f", now),
                        RowFactory.create("t2", 132, 30.0, "f", now),
                        RowFactory.create("t3", 7, 8.0, "f", now)),
                ROWS);
    }

    @Test
    void aStepJoinsAReferenceAndAddsItsDeclaredColumns() {
        PipelineSpec plan = spec(
                "zone_enrichment@1",
                "{ trips: rows, zones: reference.zones }",
                "{ prefix: pickup }",
                "[{ name: pickupBorough, type: string }]");
        TransformationSteps steps = steps(Map.of("zone_enrichment@1", lake -> new ZoneEnrichment()));

        Dataset<Row> out = steps.apply(plan, rows);

        assertThat(out.columns())
                .containsExactly("tripId", "zoneId", "fare", "_source_file", "_ingested_at", "pickupBorough");
        assertThat(out.orderBy("tripId")
                        .select("pickupBorough")
                        .as(org.apache.spark.sql.Encoders.STRING())
                        .collectAsList())
                .containsExactly("Manhattan", "Queens", null);
        assertThat(steps.reads()).containsOnlyKeys("trips.reference.zones");
        assertThat(steps.warnings()).isEmpty();
    }

    @Test
    void anAggregatingStepIsRefusedInSilver() {
        PipelineSpec plan = spec("per_zone@1", "{ trips: rows }", "{}", "[]");

        assertThatThrownBy(
                        () -> steps(Map.of("per_zone@1", lake -> new PerZone())).apply(plan, rows))
                .isInstanceOf(StepFailedException.class)
                .hasMessageContaining("Aggregate in a silver step works on one micro-batch");
    }

    @Test
    void theOutputMustMatchWhatTheStepDeclares() {
        PipelineSpec plan = spec(
                "zone_enrichment@1",
                "{ trips: rows, zones: reference.zones }",
                "{ prefix: pickup }",
                "[{ name: pickupZone, type: string }]");

        assertThatThrownBy(() -> steps(Map.of("zone_enrichment@1", lake -> new ZoneEnrichment()))
                        .apply(plan, rows))
                .isInstanceOf(StepFailedException.class)
                .hasMessageContaining("declares pickupZone but does not produce it")
                .hasMessageContaining("produces pickupBorough, which is not declared under adds");
    }

    @Test
    void paramsAreBoundByNameAndUnknownOnesRefused() {
        PipelineSpec plan = spec(
                "zone_enrichment@1",
                "{ trips: rows, zones: reference.zones }",
                "{ prefix: pickup, colour: blue }",
                "[{ name: pickupBorough, type: string }]");

        assertThatThrownBy(() -> steps(Map.of("zone_enrichment@1", lake -> new ZoneEnrichment()))
                        .apply(plan, rows))
                .isInstanceOf(StepFailedException.class)
                .hasMessageContaining("params do not fit Params")
                .hasMessageContaining("colour");
    }

    @Test
    void actionsAndRandomValuesAreReportedAsWarnings() {
        PipelineSpec plan = spec("chatty@1", "{}", "{}", "[{ name: tag, type: string }]");
        TransformationSteps steps = steps(Map.of("chatty@1", lake -> new Chatty()));

        steps.apply(plan, rows).collectAsList();

        assertThat(steps.warnings().get("zoned"))
                .anyMatch(warning -> warning.contains("Spark job(s) ran while the plan was built"))
                .anyMatch(warning -> warning.contains("non-deterministic expression (uuid)"));
    }

    @Test
    void whatTheInputsAlreadyDoIsNotBlamedOnTheStep() {
        PipelineSpec plan = spec(
                "zone_enrichment@1",
                "{ trips: rows, zones: reference.zones }",
                "{ prefix: pickup }",
                "[{ name: pickupBorough, type: string }]");
        TransformationSteps steps = steps(Map.of("zone_enrichment@1", lake -> new ZoneEnrichment()));

        steps.apply(
                plan,
                rows.withColumn("sampleRow", org.apache.spark.sql.functions.monotonically_increasing_id())
                        .drop("sampleRow"));

        assertThat(steps.warnings()).isEmpty();
    }

    @Test
    void aMissingTransformationNamesWhatTheReleaseHas() {
        PipelineSpec plan = spec("ghost@3", "{}", "{}", "[]");

        assertThatThrownBy(() -> steps(Map.of("chatty@1", lake -> new Chatty())).apply(plan, rows))
                .isInstanceOf(StepFailedException.class)
                .hasMessageContaining("no transformation ghost@3 in this release; found [chatty@1]");
    }

    @Test
    void aGoldStepMayAggregateTheWholeTable() {
        PipelineSpec plan = spec(
                "zone_enrichment@1",
                "{ trips: rows, zones: reference.zones }",
                "{ prefix: pickup }",
                "[{ name: pickupBorough, type: string }]");
        TransformationSteps steps = steps(Map.of("per_zone@1", lake -> new PerZone()));

        Dataset<Row> out =
                steps.gold(
                        plan,
                        new GoldSpec("per_zone", null, "per_zone@1", Map.of("trips", "current"), null, null, null, null),
                        source -> rows);

        assertThat(out.columns()).containsExactly("zoneId", "trips");
        assertThat(out.count()).isEqualTo(3);
        assertThat(steps.warnings()).isEmpty();
    }

    @Test
    void oneTaskForEveryRowIsReported() {
        PipelineSpec plan = spec(
                "zone_enrichment@1",
                "{ trips: rows, zones: reference.zones }",
                "{ prefix: pickup }",
                "[{ name: pickupBorough, type: string }]");
        TransformationSteps steps = steps(Map.of("funnel@1", lake -> new Funnel()));

        steps.gold(plan, new GoldSpec("funnel", null, "funnel@1", Map.of(), null, null, null, null), source -> rows);

        assertThat(steps.warnings().get("funnel"))
                .anyMatch(warning -> warning.contains("coalesce(1) or repartition(1)"));
    }

    private PipelineSpec spec(String transformation, String inputs, String params, String adds) {
        return new PipelineSpecLoader().parse(SPEC.formatted(transformation, inputs, params, adds), "trips");
    }

    private TransformationSteps steps(
            Map<String, Function<com.dataflow.transformation.Lake, Transformation<?, ?>>> known) {
        TransformationCatalog catalog = new TransformationCatalog() {
            @Override
            public Set<String> ids() {
                return known.keySet();
            }

            @Override
            public Transformation<?, ?> get(String id, com.dataflow.transformation.Lake lake) {
                return known.get(id).apply(lake);
            }
        };
        return new TransformationSteps(spark, catalog, new RunLake(spark, lake, "trips"));
    }

    @DataTransformation(name = "zone_enrichment")
    static final class ZoneEnrichment implements Transformation<ZoneEnrichment.In, ZoneEnrichment.Params> {

        @Override
        public Dataset<Row> apply(In in, Params params) {
            Dataset<Row> zones = in.zones()
                    .select(col("LocationID").as("zoneKey"), col("Borough").as(params.prefix() + "Borough"));
            return in.trips()
                    .join(broadcast(zones), col("zoneId").equalTo(col("zoneKey")), "left")
                    .drop("zoneKey");
        }

        record In(Dataset<Row> trips, Dataset<Row> zones) {}

        record Params(String prefix) {}
    }

    @DataTransformation(name = "per_zone")
    static final class PerZone implements Transformation<PerZone.In, NoParams> {

        @Override
        public Dataset<Row> apply(In in, NoParams params) {
            return in.trips().groupBy("zoneId").agg(count(lit(1)).as("trips"));
        }

        record In(Dataset<Row> trips) {}
    }

    @DataTransformation(name = "funnel")
    static final class Funnel implements Transformation<Funnel.In, NoParams> {

        @Override
        public Dataset<Row> apply(In in, NoParams params) {
            return in.trips().coalesce(1);
        }

        record In(Dataset<Row> trips) {}
    }

    @DataTransformation(name = "chatty")
    static final class Chatty implements Transformation<Chatty.In, NoParams> {

        @Override
        public Dataset<Row> apply(In in, NoParams params) {
            long seen = in.trips().count();
            return in.trips().withColumn("tag", seen > 0 ? uuid().cast("string") : lit("none"));
        }

        record In(Dataset<Row> trips) {}
    }
}
