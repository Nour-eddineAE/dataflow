package com.dataflow.engine.core.draft;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.types.StructField;
import org.apache.spark.sql.types.StructType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public record Tabular(List<Field> columns, List<List<String>> rows) {

    public static Tabular of(Dataset<Row> rows, int limit) {
        List<List<String>> values = new ArrayList<>();
        for (Row row : rows.limit(limit).collectAsList()) {
            List<String> cells = new ArrayList<>(row.length());
            for (int i = 0; i < row.length(); i++) {
                cells.add(row.isNullAt(i) ? null : String.valueOf(row.get(i)));
            }
            values.add(cells);
        }
        return new Tabular(fields(rows.schema()), values);
    }

    public static List<Field> fields(StructType schema) {
        return Arrays.stream(schema.fields()).map(Tabular::field).toList();
    }

    private static Field field(StructField field) {
        return new Field(field.name(), field.dataType().simpleString());
    }

    public record Field(String name, String type) {}
}
