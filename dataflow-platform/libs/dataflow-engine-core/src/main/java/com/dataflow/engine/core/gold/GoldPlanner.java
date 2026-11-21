package com.dataflow.engine.core.gold;

import com.dataflow.engine.core.transform.Steps;
import com.dataflow.spec.GoldSpec;
import com.dataflow.spec.PipelineSpec;
import com.dataflow.spec.ServeSpec;
import com.dataflow.spec.SqlQueries;
import com.dataflow.spec.StepSpec;
import com.dataflow.spec.TableGraph;
import com.dataflow.spec.TableRef;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class GoldPlanner {

    private static final Object VIEWS = new Object();

    private final SparkSession spark;
    private final PipelineSpec plan;
    private final TableGraph graph;
    private final Relations base;
    private final Steps steps;
    private final Map<String, Dataset<Row>> built = new HashMap<>();

    public GoldPlanner(SparkSession spark, PipelineSpec plan, Relations base, Steps steps) {
        this.spark = spark;
        this.plan = plan;
        this.graph = plan.tables();
        this.base = base;
        this.steps = steps;
    }

    public Dataset<Row> table(String name) {
        Dataset<Row> rows = built.get(name);
        if (rows == null) {
            rows = build(gold(name));
            built.put(name, rows);
        }
        return rows;
    }

    private GoldSpec gold(String name) {
        return plan.gold().stream()
                .filter(gold -> gold.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new GoldStepException("No gold table " + name + " in " + plan.dataset()));
    }

    private Dataset<Row> build(GoldSpec gold) {
        return switch (gold.body()) {
            case SQL -> query(gold);
            case TRANSFORMATION -> steps.gold(plan, gold, this::source);
            case STEPS -> GoldOperations.apply(gold.name(), relation(gold.from()), gold.steps(), this::relation);
            case INVALID -> throw new GoldStepException("gold " + gold.name() + " has no body");
        };
    }

    @SuppressWarnings("SqlSourceToSinkFlow")
    private Dataset<Row> query(GoldSpec gold) {
        String sql = SqlQueries.single(gold.sql())
                .orElseThrow(() -> new GoldStepException("gold " + gold.name() + ": " + SqlQueries.RULE));
        synchronized (VIEWS) {
            for (String name : graph.reads(gold.name())) {
                relation(name).createOrReplaceTempView(name);
            }
            return spark.sql(sql);
        }
    }

    private Dataset<Row> source(String source) {
        if (StepSpec.ROWS.equals(source)) {
            return relation(ServeSpec.CURRENT);
        }
        if (source.startsWith(StepSpec.REFERENCE)) {
            return relation(source.substring(StepSpec.REFERENCE.length()));
        }
        Optional<TableRef> table = TableRef.parse(source);
        return table.isPresent() ? base.input(table.get()) : relation(source);
    }

    private Dataset<Row> relation(String name) {
        TableGraph.Kind kind = graph.kind(name)
                .orElseThrow(() -> new GoldStepException(plan.dataset() + " has no table named " + name));
        return switch (kind) {
            case CURRENT -> base.current();
            case REFERENCE -> base.reference(name);
            case INPUT -> base.input(graph.input(name)
                    .orElseThrow(() -> new GoldStepException("Input " + name + " names no lake table")));
            case TABLE -> gold(name).materialize() ? base.stored(name).orElseGet(() -> table(name)) : table(name);
        };
    }

    public interface Relations {

        Dataset<Row> current();

        Dataset<Row> reference(String name);

        Dataset<Row> input(TableRef table);

        Optional<Dataset<Row>> stored(String table);
    }
}
