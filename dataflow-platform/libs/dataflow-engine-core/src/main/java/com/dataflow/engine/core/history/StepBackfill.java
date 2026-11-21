package com.dataflow.engine.core.history;

import com.dataflow.engine.core.transform.Steps;
import com.dataflow.spec.ColumnSpec;
import com.dataflow.spec.PipelineSpec;
import com.dataflow.spec.StepSpec;
import com.dataflow.spec.SystemColumns;
import io.delta.tables.DeltaTable;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.types.DataTypes;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.apache.spark.sql.functions.col;
import static org.apache.spark.sql.functions.lit;

public final class StepBackfill {

    public static final String PROPERTY = "dataflow.steps";
    private static final String TARGET = "t";
    private static final String SOURCE = "s";

    private StepBackfill() {}

    public static String signature(List<StepSpec> steps) {
        StringBuilder canonical = new StringBuilder();
        for (StepSpec step : steps) {
            canonical
                    .append(step.name())
                    .append('|')
                    .append(step.transformation())
                    .append('|')
                    .append(new TreeMap<>(step.inputs()))
                    .append('|')
                    .append(new TreeMap<>(step.params()))
                    .append('|')
                    .append(step.adds().stream()
                            .map(column -> column.name() + ' ' + column.type())
                            .toList())
                    .append('\n');
        }
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256")
                            .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static long run(SparkSession spark, String history, PipelineSpec plan, Steps steps) {
        List<ColumnSpec> adds = plan.silver().added();
        if (adds.isEmpty()) {
            return 0;
        }
        String signature = signature(plan.silver().steps());
        DeltaTable table = DeltaTable.forPath(spark, history);
        if (signature.equals(table.detail().select("properties").first().<String, String>getJavaMap(0).get(PROPERTY))) {
            return 0;
        }
        addMissing(spark, history, table, adds);
        merge(spark, history, computed(spark, history, plan, steps, adds), adds);
        spark.sql("ALTER TABLE delta.`%s` SET TBLPROPERTIES ('%s' = '%s')".formatted(quoted(history), PROPERTY, signature));
        return DeltaTable.forPath(spark, history).toDF().count();
    }

    private static void addMissing(SparkSession spark, String history, DeltaTable table, List<ColumnSpec> adds) {
        List<String> present = Arrays.asList(table.toDF().columns());
        for (ColumnSpec column : adds) {
            if (!present.contains(column.name())) {
                spark.sql("ALTER TABLE delta.`%s` ADD COLUMNS (`%s` %s)"
                        .formatted(quoted(history), column.name(), column.type()));
            }
        }
    }

    private static Dataset<Row> computed(
            SparkSession spark, String history, PipelineSpec plan, Steps steps, List<ColumnSpec> adds) {
        List<Column> input = new ArrayList<>();
        for (ColumnSpec column : plan.silver().columns()) {
            input.add(col(column.name()));
        }
        input.add(lit(null).cast(DataTypes.StringType).alias(SystemColumns.SOURCE_FILE));
        input.add(lit(null).cast(DataTypes.TimestampType).alias(SystemColumns.INGESTED_AT));
        input.add(col(SystemColumns.FACT_UNIQUE_ID));
        input.add(col(SystemColumns.START_AT));
        List<Column> output = new ArrayList<>(List.of(col(SystemColumns.FACT_UNIQUE_ID), col(SystemColumns.START_AT)));
        adds.forEach(column -> output.add(col(column.name())));
        return steps.apply(plan, DeltaTable.forPath(spark, history).toDF().select(input.toArray(Column[]::new)))
                .select(output.toArray(Column[]::new));
    }

    private static void merge(SparkSession spark, String history, Dataset<Row> computed, List<ColumnSpec> adds) {
        Map<String, String> set = new LinkedHashMap<>();
        adds.forEach(column -> set.put("`" + column.name() + "`", SOURCE + ".`" + column.name() + "`"));
        DeltaTable.forPath(spark, history)
                .as(TARGET)
                .merge(
                        computed.as(SOURCE),
                        "%1$s.%3$s = %2$s.%3$s AND %1$s.%4$s = %2$s.%4$s"
                                .formatted(TARGET, SOURCE, SystemColumns.FACT_UNIQUE_ID, SystemColumns.START_AT))
                .whenMatched()
                .updateExpr(set)
                .execute();
    }

    private static String quoted(String path) {
        return path.replace("`", "``");
    }
}
