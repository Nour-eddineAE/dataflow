package com.dataflow.engine.core.draft;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

public record Sample(Dataset<Row> rows, Origin origin) implements AutoCloseable {

    public static final String ROW = "_row";

    @Override
    public void close() {
        rows.unpersist();
    }

    public enum Origin {
        BRONZE,
        SOURCE
    }
}
