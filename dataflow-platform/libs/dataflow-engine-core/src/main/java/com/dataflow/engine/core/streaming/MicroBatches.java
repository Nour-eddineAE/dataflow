package com.dataflow.engine.core.streaming;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.spark.api.java.function.VoidFunction2;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.streaming.StreamingQuery;
import org.apache.spark.sql.streaming.StreamingQueryException;
import org.apache.spark.sql.streaming.Trigger;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

@SuppressWarnings("resource")
public final class MicroBatches {

    private static final String METADATA = "metadata";
    private static final String ID = "id";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Set<String> WRAPPERS = Set.of("ForeachBatchUserFuncException", "StreamingQueryException");

    private MicroBatches() {}

    public static void drain(Dataset<Row> stream, String checkpoint, String flow, Handler handler) {
        SparkSession spark = stream.sparkSession();
        AtomicReference<String> appId = new AtomicReference<>();
        VoidFunction2<Dataset<Row>, Long> sink = (rows, batchId) -> {
            if (appId.get() == null) {
                appId.set(flow + "." + queryId(spark, checkpoint));
            }
            handler.handle(new MicroBatch(rows, batchId, appId.get()));
        };
        try {
            StreamingQuery query = stream.writeStream()
                    .queryName(flow + "-" + Long.toHexString(System.nanoTime()))
                    .option("checkpointLocation", checkpoint)
                    .trigger(Trigger.AvailableNow())
                    .foreachBatch(sink)
                    .start();
            query.awaitTermination();
        } catch (StreamingQueryException e) {
            throw unwrap(e);
        } catch (TimeoutException e) {
            throw new IllegalStateException("Stream " + flow + " did not start", e);
        }
    }

    static RuntimeException unwrap(StreamingQueryException failure) {
        Throwable cause = failure.getCause();
        while (cause != null
                && cause.getCause() != null
                && WRAPPERS.contains(cause.getClass().getSimpleName())) {
            cause = cause.getCause();
        }
        return cause instanceof RuntimeException runtime
                ? runtime
                : new IllegalStateException(failure.getMessage(), failure);
    }

    static String queryId(SparkSession spark, String checkpoint) {
        Path path = new Path(checkpoint, METADATA);
        try {
            FileSystem fs = path.getFileSystem(spark.sparkContext().hadoopConfiguration());
            try (FSDataInputStream in = fs.open(path)) {
                JsonNode metadata = JSON.readTree(in.readAllBytes());
                return metadata.get(ID).asText();
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read the stream id from " + path, e);
        }
    }

    public interface Handler {
        void handle(MicroBatch batch) throws Exception;
    }
}
