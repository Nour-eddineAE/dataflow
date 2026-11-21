package com.dataflow.engine.core.streaming;

import com.dataflow.engine.core.SparkTest;
import com.dataflow.engine.core.history.ScdMerge;
import io.delta.tables.DeltaTable;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.apache.spark.sql.functions.col;
import static org.apache.spark.sql.functions.current_timestamp;
import static org.apache.spark.sql.functions.lit;
import static org.assertj.core.api.Assertions.assertThat;

@SparkTest
class IdempotentWritesTest {

    private final SparkSession spark;

    @TempDir
    Path dir;

    IdempotentWritesTest(SparkSession spark) {
        this.spark = spark;
    }

    @Test
    void replayedBatch_afterALostStreamCommit_isWrittenOnce() throws Exception {
        String source = dir.resolve("source").toUri().toString();
        String target = dir.resolve("target").toUri().toString();
        String checkpoint = dir.resolve("checkpoint").toUri().toString();
        spark.range(0, 30).write().format("delta").save(source);

        drain(source, target, checkpoint);
        Files.delete(dir.resolve("checkpoint/commits/0"));
        Files.deleteIfExists(dir.resolve("checkpoint/commits/.0.crc"));
        drain(source, target, checkpoint);

        assertThat(spark.read().format("delta").load(target).count()).isEqualTo(30);
    }

    @Test
    void replayedMerge_withTheSameTransaction_isIgnored() {
        String history = dir.resolve("history").toUri().toString();
        facts("a", "h1")
                .withColumn("__END_AT", lit(null).cast("timestamp"))
                .write()
                .format("delta")
                .save(history);
        MicroBatch batch = new MicroBatch(null, 7, "history.test");
        ScdMerge merge = new ScdMerge(spark);

        IdempotentWrites.dml(spark, batch, () -> merge.merge(facts("a", "h2"), history, false));
        IdempotentWrites.dml(spark, batch, () -> merge.merge(facts("a", "h3"), history, false));

        Dataset<Row> rows = DeltaTable.forPath(spark, history).toDF();
        assertThat(rows.count()).isEqualTo(2);
        assertThat(rows.where(col("__END_AT").isNull())
                        .select("contentHash")
                        .first()
                        .getString(0))
                .isEqualTo("h2");
    }

    private void drain(String source, String target, String checkpoint) {
        MicroBatches.drain(
                spark.readStream().format("delta").load(source),
                checkpoint,
                "test",
                batch -> IdempotentWrites.append(batch.rows(), target, batch, false, 1_000));
    }

    private Dataset<Row> facts(String key, String hash) {
        return spark.range(1)
                .select(
                        lit(key).alias("factUniqueId"),
                        lit(hash).alias("contentHash"),
                        current_timestamp().alias("__START_AT"));
    }
}
