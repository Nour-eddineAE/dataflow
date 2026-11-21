package com.dataflow.engine.core.draft;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.spark.sql.SparkSession;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;

public final class TaskResults {

    private static final ObjectMapper JSON = new ObjectMapper();

    private TaskResults() {}

    public static void write(SparkSession spark, String location, TaskResult result) {
        Path path = new Path(location);
        try {
            FileSystem fs = path.getFileSystem(spark.sparkContext().hadoopConfiguration());
            try (FSDataOutputStream out = fs.create(path, true)) {
                out.write(JSON.writeValueAsBytes(result));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write the task result to " + location, e);
        }
    }

    public record TaskResult(
            String task, String kind, String dataset, String status, String error, Object result, String finishedAt) {

        public static TaskResult done(String task, String kind, String dataset, Object result) {
            return new TaskResult(
                    task, kind, dataset, "done", null, result, Instant.now().toString());
        }

        public static TaskResult failed(String task, String kind, String dataset, Throwable error) {
            return new TaskResult(
                    task,
                    kind,
                    dataset,
                    "failed",
                    Failures.message(error),
                    null,
                    Instant.now().toString());
        }
    }
}
