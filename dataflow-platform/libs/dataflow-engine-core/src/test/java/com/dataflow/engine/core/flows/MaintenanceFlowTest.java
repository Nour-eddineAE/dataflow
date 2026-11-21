package com.dataflow.engine.core.flows;

import com.dataflow.engine.core.SparkTest;
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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SparkTest
class MaintenanceFlowTest {

    private final SparkSession spark;

    @TempDir
    Path dir;

    MaintenanceFlowTest(SparkSession spark) {
        this.spark = spark;
    }

    @Test
    void closedVersionsOlderThanTheLimitAreForgottenAndCurrentOnesKept() {
        String table = dir.resolve("history").toUri().toString();
        Instant now = Instant.now();
        spark.createDataFrame(
                        List.of(
                                RowFactory.create("a", 1, Timestamp.from(now.minus(400, ChronoUnit.DAYS))),
                                RowFactory.create("a", 2, Timestamp.from(now.minus(10, ChronoUnit.DAYS))),
                                RowFactory.create("a", 3, null),
                                RowFactory.create("b", 1, null)),
                        new StructType()
                                .add("key", DataTypes.StringType)
                                .add("revision", DataTypes.IntegerType)
                                .add("__END_AT", DataTypes.TimestampType))
                .write()
                .format("delta")
                .option("delta.enableDeletionVectors", "false")
                .save(table);

        long removed = MaintenanceFlow.forget(DeltaTable.forPath(spark, table), 365);

        assertThat(removed).isEqualTo(1);
        assertThat(spark.read()
                        .format("delta")
                        .load(table)
                        .orderBy("key", "revision")
                        .selectExpr("concat(key, revision)")
                        .as(Encoders.STRING())
                        .collectAsList())
                .containsExactly("a2", "a3", "b1");
    }

    @Test
    void nothingToForget_isZero() {
        String table = dir.resolve("fresh").toUri().toString();
        spark.createDataFrame(
                        List.of(RowFactory.create("a", 1, null)),
                        new StructType()
                                .add("key", DataTypes.StringType)
                                .add("revision", DataTypes.IntegerType)
                                .add("__END_AT", DataTypes.TimestampType))
                .write()
                .format("delta")
                .option("delta.enableDeletionVectors", "false")
                .save(table);

        assertThat(MaintenanceFlow.forget(DeltaTable.forPath(spark, table), 30)).isZero();
    }
}
