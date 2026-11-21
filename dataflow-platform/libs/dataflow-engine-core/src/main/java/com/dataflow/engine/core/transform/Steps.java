package com.dataflow.engine.core.transform;

import com.dataflow.spec.GoldSpec;
import com.dataflow.spec.PipelineSpec;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

import java.util.function.Function;

@FunctionalInterface
public interface Steps {

    Steps NONE = (plan, rows) -> {
        if (!plan.silver().steps().isEmpty()) {
            throw new IllegalStateException(
                    plan.dataset() + " has transformation steps but no transformations were loaded");
        }
        return rows;
    };

    Dataset<Row> apply(PipelineSpec plan, Dataset<Row> rows);

    default Dataset<Row> gold(PipelineSpec plan, GoldSpec gold, Function<String, Dataset<Row>> sources) {
        throw new IllegalStateException(plan.dataset() + " gold " + gold.name() + " uses " + gold.transformation()
                + " but no transformations were loaded");
    }
}
