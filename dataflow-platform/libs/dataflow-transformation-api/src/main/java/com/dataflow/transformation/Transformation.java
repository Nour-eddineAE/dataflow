package com.dataflow.transformation;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

@FunctionalInterface
public interface Transformation<I extends Record, P extends Record> {

    Dataset<Row> apply(I inputs, P params);
}
