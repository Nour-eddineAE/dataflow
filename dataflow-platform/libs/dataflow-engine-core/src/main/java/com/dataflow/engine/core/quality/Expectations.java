package com.dataflow.engine.core.quality;

import com.dataflow.spec.ExpectationSpec;
import com.dataflow.spec.ExpectationSpec.OnFailure;
import com.dataflow.spec.PipelineSpec;
import com.dataflow.spec.SystemColumns;
import com.dataflow.spec.spark.StageDataset;
import com.dataflow.spec.stage.Silver;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.apache.spark.sql.functions.array;
import static org.apache.spark.sql.functions.coalesce;
import static org.apache.spark.sql.functions.count;
import static org.apache.spark.sql.functions.expr;
import static org.apache.spark.sql.functions.filter;
import static org.apache.spark.sql.functions.lit;
import static org.apache.spark.sql.functions.not;
import static org.apache.spark.sql.functions.sum;
import static org.apache.spark.sql.functions.when;

public class Expectations {

    private static final String STRING_ARRAY = "array<string>";

    static Column failed(ExpectationSpec expectation) {
        return not(coalesce(expr(expectation.check()), lit(true)));
    }

    private static Column dropped(List<ExpectationSpec> expectations) {
        return expectations.stream()
                .filter(expectation -> expectation.onFailure() == OnFailure.DROP)
                .map(Expectations::failed)
                .reduce(Column::or)
                .orElse(lit(false));
    }

    private static Column failedNames(List<ExpectationSpec> expectations) {
        if (expectations.isEmpty()) {
            return array().cast(STRING_ARRAY);
        }
        Column[] names = expectations.stream()
                .map(expectation -> when(failed(expectation), lit(expectation.name())))
                .toArray(Column[]::new);
        return filter(array(names), name -> name.isNotNull());
    }

    private static long longAt(Row row, int index) {
        return row.isNullAt(index) ? 0L : row.getLong(index);
    }

    public ExpectationResult evaluate(StageDataset<Silver> stage, PipelineSpec plan) {
        Dataset<Row> rows = stage.dataset();
        List<ExpectationSpec> expectations = plan.silver().expectations();
        Counts counts = measure(rows, plan);
        Map<String, Long> violated = new LinkedHashMap<>();
        expectations.stream()
                .filter(expectation -> expectation.onFailure() == OnFailure.FAIL)
                .filter(expectation -> counts.failures().get(expectation.name()) > 0)
                .forEach(expectation ->
                        violated.put(expectation.name(), counts.failures().get(expectation.name())));
        if (!violated.isEmpty()) {
            throw new ExpectationViolationException(plan.dataset(), violated, counts.failures(), counts.evaluated());
        }
        Column dropped = dropped(expectations);
        return new ExpectationResult(
                StageDataset.of(rows.where(not(dropped)), plan.silverSchema()),
                quarantined(rows, plan),
                counts.evaluated(),
                counts.dropped(),
                counts.failures());
    }

    public Counts measure(Dataset<Row> rows, PipelineSpec plan) {
        List<ExpectationSpec> expectations = plan.silver().expectations();
        List<Column> aggregates = new ArrayList<>();
        aggregates.add(sum(when(dropped(expectations), 1).otherwise(0)));
        expectations.forEach(
                expectation -> aggregates.add(sum(when(failed(expectation), 1).otherwise(0))));
        Row counts = rows.agg(count(lit(1)), aggregates.toArray(Column[]::new)).first();
        Map<String, Long> failures = new LinkedHashMap<>();
        for (int i = 0; i < expectations.size(); i++) {
            failures.put(expectations.get(i).name(), longAt(counts, i + 2));
        }
        return new Counts(counts.getLong(0), longAt(counts, 1), failures);
    }

    public Dataset<Row> quarantined(Dataset<Row> rows, PipelineSpec plan) {
        List<ExpectationSpec> expectations = plan.silver().expectations();
        return rows.where(dropped(expectations))
                .withColumn(SystemColumns.FAILED_EXPECTATIONS, failedNames(expectations));
    }

    public record Counts(long evaluated, long dropped, Map<String, Long> failures) {}
}
