package com.dataflow.spec.spark;

import com.dataflow.spec.ColumnSpec;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.types.DataType;

import java.util.List;

import static org.apache.spark.sql.functions.expr;

public final class SpecProjection {

    private SpecProjection() {}

    public static Dataset<Row> project(Dataset<Row> source, List<ColumnSpec> columns) {
        return source.select(columns.stream()
                .map(column -> evaluate(column).alias(column.name()))
                .toArray(Column[]::new));
    }

    public static Dataset<Row> derive(Dataset<Row> dataset, List<ColumnSpec> columns) {
        Dataset<Row> derived = dataset;
        for (ColumnSpec column : columns) {
            derived = derived.withColumn(column.name(), evaluate(column));
        }
        return derived;
    }

    private static Column evaluate(ColumnSpec column) {
        return expr(column.from()).cast(DataType.fromDDL(column.type()));
    }
}
