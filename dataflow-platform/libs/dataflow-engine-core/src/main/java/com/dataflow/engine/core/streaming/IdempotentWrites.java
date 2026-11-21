package com.dataflow.engine.core.streaming;

import com.dataflow.engine.core.lake.DeltaTables;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SaveMode;
import org.apache.spark.sql.SparkSession;

import java.util.function.Supplier;

public final class IdempotentWrites {

    public static final String TXN_APP_ID = "txnAppId";
    public static final String TXN_VERSION = "txnVersion";
    static final String SESSION_TXN_APP_ID = "spark.databricks.delta.write.txnAppId";
    static final String SESSION_TXN_VERSION = "spark.databricks.delta.write.txnVersion";

    private IdempotentWrites() {}

    public static void append(
            Dataset<Row> rows, String path, MicroBatch batch, boolean mergeSchema, long maxRecordsPerFile) {
        rows.write()
                .format(DeltaTables.FORMAT)
                .mode(SaveMode.Append)
                .option(TXN_APP_ID, batch.appId())
                .option(TXN_VERSION, batch.id())
                .option("mergeSchema", mergeSchema)
                .option("maxRecordsPerFile", maxRecordsPerFile)
                .save(path);
    }

    public static <T> T dml(SparkSession spark, MicroBatch batch, Supplier<T> command) {
        spark.conf().set(SESSION_TXN_APP_ID, batch.appId());
        spark.conf().set(SESSION_TXN_VERSION, batch.id());
        try {
            return command.get();
        } finally {
            spark.conf().unset(SESSION_TXN_APP_ID);
            spark.conf().unset(SESSION_TXN_VERSION);
        }
    }
}
