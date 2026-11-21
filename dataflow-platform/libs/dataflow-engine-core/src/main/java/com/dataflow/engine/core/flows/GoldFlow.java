package com.dataflow.engine.core.flows;

import com.dataflow.engine.core.gold.GoldPlanner;
import com.dataflow.engine.core.lake.DeltaTables;
import com.dataflow.engine.core.run.AssetKeys;
import com.dataflow.engine.core.run.Flow;
import com.dataflow.engine.core.run.FlowResult;
import com.dataflow.engine.core.run.RunContext;
import com.dataflow.spec.GoldSpec;
import com.dataflow.spec.PipelineSpec;
import com.dataflow.spec.SystemColumns;
import com.dataflow.spec.TableGraph;
import com.dataflow.spec.TableRef;
import com.dataflow.spec.lake.FlowNames;
import com.dataflow.spec.lake.Layer;
import com.dataflow.spec.spark.StageDataset;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SaveMode;
import org.apache.spark.sql.SparkSession;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.apache.spark.sql.functions.col;
import static org.apache.spark.sql.functions.current_timestamp;

public class GoldFlow implements Flow {

    private final GoldSpec gold;
    private final Set<String> inputs;

    public GoldFlow(GoldSpec gold, Set<String> inputs) {
        this.gold = gold;
        this.inputs = Set.copyOf(inputs);
    }

    public static Dataset<Row> current(SparkSession spark, PipelineSpec plan, String history) {
        Dataset<Row> rows = spark.read()
                .format(DeltaTables.FORMAT)
                .load(history)
                .where(col(SystemColumns.END_AT).isNull());
        return StageDataset.conform(rows, plan.currentSchema()).dataset();
    }

    @Override
    public String name() {
        return FlowNames.gold(gold.name());
    }

    @Override
    public Set<String> inputs() {
        return inputs;
    }

    @Override
    public FlowResult run(RunContext context) {
        Optional<String> waiting = missing(context);
        if (waiting.isPresent()) {
            return new FlowResult().detail("skipped", waiting.get());
        }
        String table = context.lake().table(Layer.GOLD, context.dataset(), gold.name());
        Dataset<Row> computed = new GoldPlanner(context.spark(), context.plan(), new RunRelations(context), context.steps())
                .table(gold.name());
        computed.withColumn(SystemColumns.COMPUTED_AT, current_timestamp())
                .write()
                .format(DeltaTables.FORMAT)
                .mode(SaveMode.Overwrite)
                .option("overwriteSchema", true)
                .option(DeltaTables.DELETION_VECTORS, "false")
                .save(table);
        long rows = context.spark().read().format(DeltaTables.FORMAT).load(table).count();
        return new FlowResult()
                .rows(rows, rows, 0)
                .detail("reads", context.reads().reads())
                .materialized(
                        AssetKeys.of(context.dataset(), Layer.GOLD, gold.name()), Map.of("rows", rows, "table", table));
    }

    private Optional<String> missing(RunContext context) {
        TableGraph graph = context.plan().tables();
        for (String source : graph.sources(gold.name())) {
            TableGraph.Kind kind = graph.kind(source).orElse(TableGraph.Kind.TABLE);
            if (kind == TableGraph.Kind.CURRENT
                    && !context.tables().exists(context.lake().table(Layer.HISTORY, context.dataset()))) {
                return Optional.of("no history yet");
            }
            if (kind == TableGraph.Kind.TABLE
                    && !context.tables().exists(context.lake().table(Layer.GOLD, context.dataset(), source))) {
                return Optional.of("gold " + source + " has no data yet");
            }
            if (kind == TableGraph.Kind.INPUT) {
                Optional<TableRef> input = graph.input(source);
                if (input.isPresent() && !context.reads().exists(input.get().toString())) {
                    return Optional.of("input " + source + " (" + input.get() + ") has no data yet");
                }
            }
        }
        return Optional.empty();
    }
}
