package com.dataflow.spec.spark;

import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.functions;
import org.apache.spark.sql.types.StructField;
import org.apache.spark.sql.types.StructType;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public final class StageDataset<S> {

    private final Dataset<Row> dataset;

    private StageDataset(Dataset<Row> dataset) {
        this.dataset = dataset;
    }

    public static <S> StageDataset<S> of(Dataset<Row> dataset, StructType schema) {
        Objects.requireNonNull(dataset, "dataset");
        Objects.requireNonNull(schema, "schema");
        StructType actual = dataset.schema();
        if (!matchesIgnoringNullability(actual, schema)) {
            throw new IllegalArgumentException("StageDataset schema mismatch. expected=" + schema.catalogString()
                    + " actual=" + actual.catalogString());
        }
        return new StageDataset<>(dataset);
    }

    public static <S> StageDataset<S> conform(Dataset<Row> dataset, StructType schema) {
        Objects.requireNonNull(dataset, "dataset");
        List<String> present = Arrays.asList(dataset.columns());
        List<String> missing = Arrays.stream(schema.fieldNames())
                .filter(name -> !present.contains(name))
                .toList();
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("StageDataset is missing columns " + missing + ". expected="
                    + schema.catalogString() + " actual=" + dataset.schema().catalogString());
        }
        return of(
                dataset.select(
                        Arrays.stream(schema.fieldNames()).map(functions::col).toArray(Column[]::new)),
                schema);
    }

    private static boolean matchesIgnoringNullability(StructType actual, StructType expected) {
        StructField[] a = actual.fields();
        StructField[] e = expected.fields();
        if (a.length != e.length) {
            return false;
        }
        for (int i = 0; i < e.length; i++) {
            if (!a[i].name().equals(e[i].name()) || !a[i].dataType().equals(e[i].dataType())) {
                return false;
            }
        }
        return true;
    }

    public Dataset<Row> dataset() {
        return dataset;
    }

    @Override
    public String toString() {
        return "StageDataset(" + dataset.schema().catalogString() + ")";
    }
}
