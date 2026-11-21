package com.dataflow.engine.core.history;

import com.dataflow.engine.core.SparkTest;
import com.dataflow.engine.core.transform.Steps;
import com.dataflow.spec.PipelineSpec;
import com.dataflow.spec.PipelineSpecLoader;
import io.delta.tables.DeltaTable;
import org.apache.spark.sql.Encoders;
import org.apache.spark.sql.RowFactory;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.StructType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Timestamp;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.apache.spark.sql.functions.col;
import static org.assertj.core.api.Assertions.assertThat;

@SparkTest
class StepBackfillTest {

    private static final String SPEC = """
        dataset: quakes
        version: 3
        source: { path: /tmp/quakes.csv, format: csv }
        silver:
          columns:
            - { name: eventId,   type: string, required: true, from: id }
            - { name: magnitude, type: double, from: mag }
          steps:
            - name: energy
              transformation: energy@%d
              adds:
                - { name: energy, type: double }
        history: { keys: [eventId] }
        """;

    private final SparkSession spark;

    @TempDir
    Path dir;

    StepBackfillTest(SparkSession spark) {
        this.spark = spark;
    }

    private static PipelineSpec spec(int version) {
        return new PipelineSpecLoader().parse(SPEC.formatted(version), "quakes");
    }

    @Test
    void existingVersionsGetTheStepColumnsOncePerStepSignature() {
        String history = dir.resolve("history").toUri().toString();
        Timestamp first = Timestamp.valueOf("2026-10-01 00:00:00");
        Timestamp second = Timestamp.valueOf("2026-10-02 00:00:00");
        spark.createDataFrame(
                        List.of(
                                RowFactory.create("a", 2.0, "fa", first, second),
                                RowFactory.create("a", 3.0, "fa", second, null),
                                RowFactory.create("b", 4.0, "fb", first, null)),
                        new StructType()
                                .add("eventId", DataTypes.StringType)
                                .add("magnitude", DataTypes.DoubleType)
                                .add("factUniqueId", DataTypes.StringType)
                                .add("__START_AT", DataTypes.TimestampType)
                                .add("__END_AT", DataTypes.TimestampType))
                .write()
                .format("delta")
                .option("delta.enableDeletionVectors", "false")
                .save(history);
        AtomicInteger calls = new AtomicInteger();
        Steps doubling = (plan, rows) -> {
            calls.incrementAndGet();
            return rows.withColumn("energy", col("magnitude").multiply(2));
        };

        StepBackfill.run(spark, history, spec(1), doubling);
        StepBackfill.run(spark, history, spec(1), doubling);

        assertThat(calls).hasValue(1);
        assertThat(DeltaTable.forPath(spark, history)
                        .toDF()
                        .orderBy("eventId", "__START_AT")
                        .selectExpr("concat(eventId, ':', energy)")
                        .as(Encoders.STRING())
                        .collectAsList())
                .containsExactly("a:4.0", "a:6.0", "b:8.0");

        StepBackfill.run(spark, history, spec(2), (plan, rows) -> {
            calls.incrementAndGet();
            return rows.withColumn("energy", col("magnitude").multiply(10));
        });

        assertThat(calls).hasValue(2);
        assertThat(DeltaTable.forPath(spark, history)
                        .toDF()
                        .where("eventId = 'b'")
                        .select("energy")
                        .as(Encoders.DOUBLE())
                        .first())
                .isEqualTo(40.0);
    }

    @Test
    void theSignatureFollowsVersionsAndParams() {
        assertThat(StepBackfill.signature(spec(1).silver().steps()))
                .isNotEqualTo(StepBackfill.signature(spec(2).silver().steps()))
                .isEqualTo(StepBackfill.signature(spec(1).silver().steps()));
    }
}
