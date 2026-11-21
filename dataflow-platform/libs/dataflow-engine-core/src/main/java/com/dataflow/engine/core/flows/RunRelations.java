package com.dataflow.engine.core.flows;

import com.dataflow.engine.core.gold.GoldPlanner;
import com.dataflow.engine.core.lake.DeltaTables;
import com.dataflow.engine.core.run.RunContext;
import com.dataflow.spec.TableRef;
import com.dataflow.spec.lake.Layer;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

import java.util.Optional;

final class RunRelations implements GoldPlanner.Relations {

    private final RunContext context;

    RunRelations(RunContext context) {
        this.context = context;
    }

    @Override
    public Dataset<Row> current() {
        return GoldFlow.current(
                context.spark(), context.plan(), context.lake().table(Layer.HISTORY, context.dataset()));
    }

    @Override
    public Dataset<Row> reference(String name) {
        return delta(context.lake().table(Layer.REFERENCE, context.dataset(), name));
    }

    @Override
    public Dataset<Row> input(TableRef table) {
        return context.reads().table(table.toString());
    }

    @Override
    public Optional<Dataset<Row>> stored(String table) {
        String path = context.lake().table(Layer.GOLD, context.dataset(), table);
        return context.tables().exists(path) ? Optional.of(delta(path)) : Optional.empty();
    }

    private Dataset<Row> delta(String path) {
        return context.spark().read().format(DeltaTables.FORMAT).load(path);
    }
}
