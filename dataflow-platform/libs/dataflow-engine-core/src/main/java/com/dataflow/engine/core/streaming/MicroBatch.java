package com.dataflow.engine.core.streaming;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

public record MicroBatch(Dataset<Row> rows, long id, String appId) {}
