package com.dataflow.spec;

import org.apache.spark.sql.types.DataType;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.Metadata;
import org.apache.spark.sql.types.StructField;
import org.apache.spark.sql.types.StructType;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public record PipelineSpec(
        String dataset,
        int version,
        String owner,
        String description,
        SourceSpec source,
        Map<String, String> inputs,
        String schedule,
        BronzeSpec bronze,
        SilverSpec silver,
        HistorySpec history,
        List<ReferenceSpec> references,
        List<GoldSpec> gold,
        List<ServeSpec> serve,
        ComputeSpec compute) {

    public PipelineSpec {
        inputs = inputs == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(inputs));
        bronze = bronze == null ? BronzeSpec.DEFAULT : bronze;
        silver = silver == null ? new SilverSpec(null, null, null, null) : silver;
        history = history == null ? new HistorySpec(null, null, null, null, null) : history;
        references = orEmpty(references);
        gold = orEmpty(gold);
        serve = orEmpty(serve);
        compute = compute == null ? ComputeSpec.DEFAULT : compute;
    }

    private static StructType struct(Stream<ColumnSpec> columns) {
        return new StructType(columns.map(column -> new StructField(
                        column.name(), DataType.fromDDL(column.type()), !column.required(), Metadata.empty()))
                .toArray(StructField[]::new));
    }

    static <T> List<T> orEmpty(List<T> list) {
        return list == null ? List.of() : List.copyOf(list);
    }

    public StructType factSchema() {
        return struct(Stream.of(silver.columns(), silver.added(), silver.derive())
                        .flatMap(List::stream))
                .add(SystemColumns.FACT_UNIQUE_ID, DataTypes.StringType)
                .add(SystemColumns.PROCESSED_AT, DataTypes.TimestampType);
    }

    public StructType silverSchema() {
        return factSchema()
                .add(SystemColumns.SOURCE_FILE, DataTypes.StringType)
                .add(SystemColumns.INGESTED_AT, DataTypes.TimestampType)
                .add(SystemColumns.RUN_ID, DataTypes.StringType);
    }

    public StructType currentSchema() {
        return factSchema().add(SystemColumns.START_AT, DataTypes.TimestampType);
    }

    public List<String> changeColumns() {
        return silver.columns().stream()
                .map(ColumnSpec::name)
                .filter(name -> !history.trackHistoryExcept().contains(name))
                .toList();
    }

    public boolean ingests() {
        return source != null;
    }

    public boolean partitioned() {
        return ingests() && source.partitioned();
    }

    public TableGraph tables() {
        return TableGraph.of(this);
    }

    public List<GoldSpec> materialized() {
        return gold.stream().filter(GoldSpec::materialize).toList();
    }

    public boolean usesTransformations() {
        return !silver().steps().isEmpty() || gold().stream().anyMatch(GoldSpec::usesTransformation);
    }

    public boolean silverReadsReferences() {
        return silver.steps().stream().anyMatch(step -> !step.references().isEmpty());
    }

    public boolean quarantines() {
        return silver.expectations().stream()
                .anyMatch(expectation -> expectation.onFailure() == ExpectationSpec.OnFailure.DROP);
    }
}
