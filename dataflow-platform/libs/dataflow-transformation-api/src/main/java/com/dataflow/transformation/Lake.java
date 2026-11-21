package com.dataflow.transformation;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

import java.util.Map;

public interface Lake {

    Dataset<Row> table(String name);

    Dataset<Row> files(String path, String format, Map<String, String> options);

    default Dataset<Row> files(String path, String format) {
        return files(path, format, Map.of());
    }
}
