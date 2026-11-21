package com.dataflow.engine.core.flows;

import com.dataflow.engine.core.lake.DeltaTables;
import com.dataflow.engine.core.quality.ExpectationResult;
import com.dataflow.engine.core.quality.ExpectationViolationException;
import com.dataflow.engine.core.quality.Expectations;
import com.dataflow.engine.core.run.AssetKeys;
import com.dataflow.engine.core.run.CheckResult;
import com.dataflow.engine.core.run.Flow;
import com.dataflow.engine.core.run.FlowFailedException;
import com.dataflow.engine.core.run.FlowResult;
import com.dataflow.engine.core.run.RunContext;
import com.dataflow.engine.core.streaming.IdempotentWrites;
import com.dataflow.engine.core.streaming.MicroBatch;
import com.dataflow.engine.core.streaming.MicroBatches;
import com.dataflow.engine.core.tower.Severity;
import com.dataflow.engine.core.transform.Steps;
import com.dataflow.engine.core.transform.TransformationSteps;
import com.dataflow.spec.ColumnSpec;
import com.dataflow.spec.ExpectationSpec;
import com.dataflow.spec.ExpectationSpec.OnFailure;
import com.dataflow.spec.PipelineSpec;
import com.dataflow.spec.SystemColumns;
import com.dataflow.spec.lake.FlowNames;
import com.dataflow.spec.lake.Layer;
import com.dataflow.spec.spark.CanonicalEncoding;
import com.dataflow.spec.spark.SpecProjection;
import com.dataflow.spec.spark.StageDataset;
import com.dataflow.spec.stage.Silver;
import lombok.RequiredArgsConstructor;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.types.DataType;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.storage.StorageLevel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.apache.spark.sql.functions.col;
import static org.apache.spark.sql.functions.current_timestamp;
import static org.apache.spark.sql.functions.expr;
import static org.apache.spark.sql.functions.lit;

@RequiredArgsConstructor
@SuppressWarnings("resource")
public class SilverFlow implements Flow {

    static final List<String> CLUSTER_BY = List.of(SystemColumns.INGESTED_AT);

    private final Expectations expectations;
    private final boolean readsReferences;

    public SilverFlow(Expectations expectations) {
        this(expectations, false);
    }

    public static StageDataset<Silver> toSilver(Dataset<Row> bronze, PipelineSpec plan, String runId, Steps steps) {
        return StageDataset.conform(project(bronze, plan, runId, steps), plan.silverSchema());
    }

    public static Dataset<Row> project(
            Dataset<Row> bronze, PipelineSpec plan, String runId, Steps steps, String... carried) {
        List<Column> columns = new ArrayList<>();
        for (ColumnSpec column : plan.silver().columns()) {
            columns.add(
                    expr(column.from()).cast(DataType.fromDDL(column.type())).alias(column.name()));
        }
        columns.add(col(SystemColumns.SOURCE_FILE));
        columns.add(col(SystemColumns.INGESTED_AT));
        for (String name : carried) {
            columns.add(col(name));
        }
        Dataset<Row> stepped = steps.apply(plan, bronze.select(columns.toArray(Column[]::new)));
        Dataset<Row> projected = SpecProjection.derive(stepped, plan.silver().derive());
        return projected
                .withColumn(
                        SystemColumns.FACT_UNIQUE_ID,
                        CanonicalEncoding.sha256(
                                projected.schema(), plan.history().keys()))
                .withColumn(SystemColumns.PROCESSED_AT, current_timestamp())
                .withColumn(SystemColumns.RUN_ID, lit(runId).cast(DataTypes.StringType));
    }

    @Override
    public String name() {
        return FlowNames.SILVER;
    }

    @Override
    public Set<String> inputs() {
        return readsReferences ? Set.of(FlowNames.BRONZE, FlowNames.REFERENCES) : Set.of(FlowNames.BRONZE);
    }

    @Override
    public FlowResult run(RunContext context) {
        PipelineSpec plan = context.plan();
        String bronze = context.lake().table(Layer.BRONZE, context.dataset());
        String silver = context.lake().table(Layer.SILVER, context.dataset());
        String quarantine = context.lake().table(Layer.QUARANTINE, context.dataset());
        Totals totals = new Totals(plan);

        try {
            if (context.tables().exists(bronze)) {
                Dataset<Row> stream = context.spark()
                        .readStream()
                        .format(DeltaTables.FORMAT)
                        .option("maxFilesPerTrigger", context.batching().maxFilesPerBatch())
                        .load(bronze);
                MicroBatches.drain(
                        stream,
                        context.lake().checkpoint(context.dataset(), name()),
                        name(),
                        batch -> refine(context, batch, silver, quarantine, totals));
            }
        } catch (ExpectationViolationException e) {
            totals.add(e.evaluated(), 0, e.failures());
            throw new FlowFailedException(e.getMessage(), totals.result(context, silver, quarantine));
        }
        return totals.result(context, silver, quarantine);
    }

    private void refine(RunContext context, MicroBatch batch, String silver, String quarantine, Totals totals) {
        PipelineSpec plan = context.plan();
        StageDataset<Silver> stage = toSilver(batch.rows(), plan, context.runId(), context.steps());
        Dataset<Row> candidates = stage.dataset().persist(StorageLevel.DISK_ONLY());
        try {
            ExpectationResult quality = expectations.evaluate(StageDataset.of(candidates, plan.silverSchema()), plan);
            context.tables().ensure(silver, plan.silverSchema(), CLUSTER_BY, false);
            IdempotentWrites.append(
                    quality.kept().dataset(),
                    silver,
                    batch,
                    true,
                    context.batching().maxRecordsPerFile());
            if (plan.quarantines()) {
                context.tables().ensure(quarantine, quality.quarantined().schema(), CLUSTER_BY, false);
                IdempotentWrites.append(
                        quality.quarantined(),
                        quarantine,
                        batch,
                        true,
                        context.batching().maxRecordsPerFile());
            }
            totals.add(quality.evaluated(), quality.dropped(), quality.failures());
        } finally {
            candidates.unpersist();
        }
    }

    private static final class Totals {

        private final PipelineSpec plan;
        private final AtomicLong evaluated = new AtomicLong();
        private final AtomicLong dropped = new AtomicLong();
        private final Map<String, AtomicLong> failures = new LinkedHashMap<>();

        Totals(PipelineSpec plan) {
            this.plan = plan;
            plan.silver().expectations().forEach(expectation -> failures.put(expectation.name(), new AtomicLong()));
        }

        void add(long rows, long drops, Map<String, Long> failing) {
            evaluated.addAndGet(rows);
            dropped.addAndGet(drops);
            failing.forEach((name, count) -> failures.get(name).addAndGet(count));
        }

        FlowResult result(RunContext context, String silver, String quarantine) {
            String silverKey = AssetKeys.of(context.dataset(), Layer.SILVER);
            FlowResult result = new FlowResult()
                    .rows(evaluated.get(), evaluated.get() - dropped.get(), dropped.get())
                    .materialized(silverKey, Map.of("new_rows", evaluated.get() - dropped.get(), "table", silver));
            if (plan.quarantines()) {
                result.materialized(
                        AssetKeys.of(context.dataset(), Layer.QUARANTINE),
                        Map.of("new_rows", dropped.get(), "table", quarantine));
            }
            Map<String, Object> perExpectation = new LinkedHashMap<>();
            for (ExpectationSpec expectation : plan.silver().expectations()) {
                long failing = failures.get(expectation.name()).get();
                Map<String, Object> metadata = new LinkedHashMap<>();
                metadata.put("failing_rows", failing);
                metadata.put("evaluated", evaluated.get());
                metadata.put("action", expectation.onFailure().name().toLowerCase());
                metadata.put("check", expectation.check());
                perExpectation.put(expectation.name(), failing);
                result.checked(new CheckResult(
                        silverKey,
                        expectation.name(),
                        failing == 0,
                        expectation.onFailure() == OnFailure.FAIL ? Severity.ERROR : Severity.WARN,
                        metadata));
            }
            result.detail("expectations", perExpectation);
            if (context.steps() instanceof TransformationSteps steps) {
                if (!steps.warnings().isEmpty()) {
                    result.detail("step_warnings", steps.warnings());
                }
                if (!steps.reads().isEmpty()) {
                    result.detail("step_reads", steps.reads());
                }
            }
            return result;
        }
    }
}
