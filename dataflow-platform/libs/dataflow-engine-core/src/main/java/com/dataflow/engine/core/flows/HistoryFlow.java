package com.dataflow.engine.core.flows;

import com.dataflow.engine.core.history.MergeCounts;
import com.dataflow.engine.core.history.ScdMerge;
import com.dataflow.engine.core.history.StepBackfill;
import com.dataflow.engine.core.lake.DeltaTables;
import com.dataflow.engine.core.run.AssetKeys;
import com.dataflow.engine.core.run.Flow;
import com.dataflow.engine.core.run.FlowResult;
import com.dataflow.engine.core.run.RunContext;
import com.dataflow.engine.core.streaming.IdempotentWrites;
import com.dataflow.engine.core.streaming.MicroBatch;
import com.dataflow.engine.core.streaming.MicroBatches;
import com.dataflow.spec.PipelineSpec;
import com.dataflow.spec.SystemColumns;
import com.dataflow.spec.lake.FlowNames;
import com.dataflow.spec.lake.Layer;
import com.dataflow.spec.spark.CanonicalEncoding;
import com.dataflow.spec.spark.StageDataset;
import lombok.extern.slf4j.Slf4j;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.expressions.Window;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.StructType;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.apache.spark.sql.functions.coalesce;
import static org.apache.spark.sql.functions.col;
import static org.apache.spark.sql.functions.current_timestamp;
import static org.apache.spark.sql.functions.row_number;

@Slf4j
@SuppressWarnings("resource")
public class HistoryFlow implements Flow {

    static final List<String> CLUSTER_BY = List.of(SystemColumns.FACT_UNIQUE_ID);
    private static final String RANK = "versionRank";

    public static Dataset<Row> versions(Dataset<Row> silver, PipelineSpec plan) {
        Dataset<Row> facts = StageDataset.conform(silver, plan.factSchema()).dataset();
        String sequence = plan.history().sequenceBy();
        Column startAt = sequence == null
                ? current_timestamp()
                : coalesce(col(sequence).cast(DataTypes.TimestampType), current_timestamp());
        Dataset<Row> stamped = facts.withColumn(
                        SystemColumns.CONTENT_HASH, CanonicalEncoding.sha256(facts.schema(), plan.changeColumns()))
                .withColumn(SystemColumns.START_AT, startAt);
        return stamped.withColumn(
                        RANK,
                        row_number()
                                .over(Window.partitionBy(SystemColumns.FACT_UNIQUE_ID)
                                        .orderBy(
                                                col(SystemColumns.START_AT).desc(),
                                                col(SystemColumns.PROCESSED_AT).desc())))
                .where(col(RANK).equalTo(1))
                .drop(RANK);
    }

    static StructType historySchema(PipelineSpec plan) {
        return plan.factSchema()
                .add(SystemColumns.CONTENT_HASH, DataTypes.StringType)
                .add(SystemColumns.START_AT, DataTypes.TimestampType)
                .add(SystemColumns.END_AT, DataTypes.TimestampType);
    }

    @Override
    public String name() {
        return FlowNames.HISTORY;
    }

    @Override
    public Set<String> inputs() {
        return Set.of(FlowNames.SILVER);
    }

    @Override
    public FlowResult run(RunContext context) {
        String silver = context.lake().table(Layer.SILVER, context.dataset());
        String history = context.lake().table(Layer.HISTORY, context.dataset());
        AtomicReference<MergeCounts> totals = new AtomicReference<>(MergeCounts.NONE);
        if (context.tables().exists(silver)) {
            Dataset<Row> stream = context.spark()
                    .readStream()
                    .format(DeltaTables.FORMAT)
                    .option("maxFilesPerTrigger", context.batching().maxFilesPerBatch())
                    .load(silver);
            MicroBatches.drain(
                    stream,
                    context.lake().checkpoint(context.dataset(), name()),
                    name(),
                    batch -> totals.updateAndGet(counts -> counts.plus(record(context, batch, history))));
        }
        MergeCounts counts = totals.get();
        long backfilled = context.tables().exists(history)
                ? StepBackfill.run(context.spark(), history, context.plan(), context.steps())
                : 0;
        if (backfilled > 0) {
            log.info("Transformation steps recomputed for {} versions of {}", backfilled, history);
        }
        return new FlowResult()
                .rows(
                        counts.newFacts() + counts.changedFacts() + counts.staleVersions(),
                        counts.newFacts() + counts.changedFacts(),
                        counts.staleVersions())
                .detail("new_facts", counts.newFacts())
                .detail("changed_facts", counts.changedFacts())
                .detail("stale_versions", counts.staleVersions())
                .detail("steps_backfilled", backfilled)
                .materialized(
                        AssetKeys.of(context.dataset(), Layer.HISTORY),
                        Map.of(
                                "new_facts",
                                counts.newFacts(),
                                "changed_facts",
                                counts.changedFacts(),
                                "stale_versions",
                                counts.staleVersions(),
                                "table",
                                history));
    }

    private MergeCounts record(RunContext context, MicroBatch batch, String history) {
        PipelineSpec plan = context.plan();
        Dataset<Row> versions = versions(batch.rows(), plan);
        context.tables().ensure(history, historySchema(plan), CLUSTER_BY, true);
        ScdMerge merge = new ScdMerge(context.spark());
        List<String> backfilled = merge.addDerivedColumns(history, plan.silver().derive());
        if (!backfilled.isEmpty()) {
            log.info("Derived columns {} added to {} and computed for every existing version", backfilled, history);
        }
        return IdempotentWrites.dml(
                context.spark(),
                batch,
                () -> merge.merge(versions, history, plan.history().sequenceBy() != null));
    }
}
