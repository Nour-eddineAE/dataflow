package com.dataflow.engine.core.draft;

import com.dataflow.engine.core.flows.SilverFlow;
import com.dataflow.engine.core.gold.GoldPlanner;
import com.dataflow.engine.core.lake.DeltaTables;
import com.dataflow.engine.core.quality.Expectations;
import com.dataflow.engine.core.transform.Steps;
import com.dataflow.spec.ExpectationSpec;
import com.dataflow.spec.GoldSpec;
import com.dataflow.spec.PipelineSpec;
import com.dataflow.spec.lake.Lake;
import com.dataflow.spec.spark.StageDataset;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.storage.StorageLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DraftPreview {

    static final int SILVER_ROWS = 100;
    static final int QUARANTINE_ROWS = 25;
    static final int GOLD_ROWS = 25;

    private final DraftViews views;
    private final Steps steps;
    private final Expectations expectations = new Expectations();

    public DraftPreview(SparkSession spark, Lake lake, DeltaTables tables, Steps steps) {
        this.views = new DraftViews(spark, lake, tables);
        this.steps = steps;
    }

    public Preview preview(PipelineSpec draft, Sample sample, String runId) {
        Dataset<Row> silver =
                SilverFlow.project(sample.rows(), draft, runId, steps).persist(StorageLevel.DISK_ONLY());
        try {
            Expectations.Counts counts = expectations.measure(silver, draft);
            List<Preview.Rule> rules = new ArrayList<>();
            for (ExpectationSpec rule : draft.silver().expectations()) {
                rules.add(new Preview.Rule(
                        rule.name(),
                        rule.onFailure().name().toLowerCase(Locale.ROOT),
                        counts.failures().get(rule.name()),
                        counts.evaluated()));
            }
            Dataset<Row> conformed =
                    StageDataset.conform(silver, draft.silverSchema()).dataset();
            return new Preview(
                    draft.dataset(),
                    sample.origin(),
                    counts.evaluated(),
                    Tabular.fields(sample.rows().drop(Sample.ROW).schema()),
                    Tabular.of(conformed, SILVER_ROWS),
                    rules,
                    Tabular.of(expectations.quarantined(conformed, draft), QUARANTINE_ROWS),
                    gold(draft, silver));
        } finally {
            silver.unpersist();
        }
    }

    public Preview derived(PipelineSpec draft) {
        return new Preview(
                draft.dataset(), null, 0, List.of(), null, List.of(), null, gold(draft, null));
    }

    private List<Preview.Gold> gold(PipelineSpec draft, Dataset<Row> silver) {
        GoldPlanner planner = views.planner(draft, silver, steps);
        List<Preview.Gold> results = new ArrayList<>();
        for (GoldSpec gold : draft.gold()) {
            try {
                Dataset<Row> rows = planner.table(gold.name());
                results.add(new Preview.Gold(gold.name(), rows.count(), Tabular.of(rows, GOLD_ROWS), null));
            } catch (Exception e) {
                results.add(new Preview.Gold(gold.name(), null, null, Failures.message(e)));
            }
        }
        return results;
    }
}
