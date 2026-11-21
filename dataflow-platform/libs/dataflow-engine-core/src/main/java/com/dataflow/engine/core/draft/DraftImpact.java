package com.dataflow.engine.core.draft;

import com.dataflow.engine.core.flows.SilverFlow;
import com.dataflow.engine.core.gold.GoldPlanner;
import com.dataflow.engine.core.lake.DeltaTables;
import com.dataflow.engine.core.quality.Expectations;
import com.dataflow.engine.core.transform.Steps;
import com.dataflow.spec.ColumnSpec;
import com.dataflow.spec.ExpectationSpec;
import com.dataflow.spec.GoldSpec;
import com.dataflow.spec.PipelineSpec;
import com.dataflow.spec.SystemColumns;
import com.dataflow.spec.lake.Lake;
import com.dataflow.spec.spark.CanonicalEncoding;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.storage.StorageLevel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.apache.spark.sql.functions.col;
import static org.apache.spark.sql.functions.countDistinct;
import static org.apache.spark.sql.functions.not;
import static org.apache.spark.sql.functions.sum;
import static org.apache.spark.sql.functions.when;

public class DraftImpact {

    private static final String BEFORE = "before_";
    private static final String AFTER = "after_";

    private final DraftViews views;
    private final Steps steps;
    private final Expectations expectations = new Expectations();

    public DraftImpact(SparkSession spark, Lake lake, DeltaTables tables, Steps steps) {
        this.views = new DraftViews(spark, lake, tables);
        this.steps = steps;
    }

    private static Dataset<Row> side(Dataset<Row> silver, String prefix, List<String> shared) {
        List<Column> columns = new ArrayList<>();
        columns.add(col(Sample.ROW));
        columns.add(col(SystemColumns.FACT_UNIQUE_ID).alias(prefix + SystemColumns.FACT_UNIQUE_ID));
        columns.add(col(SystemColumns.CONTENT_HASH).alias(prefix + SystemColumns.CONTENT_HASH));
        shared.forEach(name -> columns.add(col(name).alias(prefix + name)));
        return silver.select(columns.toArray(Column[]::new));
    }

    private static List<String> shared(PipelineSpec saved, PipelineSpec draft) {
        Set<String> before = new LinkedHashSet<>(names(saved));
        return names(draft).stream().filter(before::contains).toList();
    }

    private static List<String> names(PipelineSpec spec) {
        return Stream.concat(spec.silver().columns().stream(), spec.silver().derive().stream())
                .map(ColumnSpec::name)
                .toList();
    }

    private static List<Impact.Rule> rules(
            PipelineSpec saved, PipelineSpec draft, Expectations.Counts before, Expectations.Counts after) {
        Map<String, Impact.Rule> rules = new LinkedHashMap<>();
        for (ExpectationSpec rule : saved.silver().expectations()) {
            rules.put(
                    rule.name(),
                    new Impact.Rule(
                            rule.name(), action(rule), null, before.failures().get(rule.name()), null));
        }
        for (ExpectationSpec rule : draft.silver().expectations()) {
            rules.compute(
                    rule.name(),
                    (k, known) -> new Impact.Rule(
                            rule.name(),
                            known == null ? null : known.actionBefore(),
                            action(rule),
                            known == null ? null : known.failingBefore(),
                            after.failures().get(rule.name())));
        }
        return List.copyOf(rules.values());
    }

    private static String action(ExpectationSpec rule) {
        return rule.onFailure().name().toLowerCase(Locale.ROOT);
    }

    private static List<Impact.ColumnChange> columns(
            PipelineSpec saved, PipelineSpec draft, Dataset<Row> pairs, List<String> shared) {
        List<Impact.ColumnChange> changes = new ArrayList<>();
        if (!shared.isEmpty()) {
            Column[] differences = shared.stream()
                    .map(name -> sum(when(not(col(BEFORE + name).eqNullSafe(col(AFTER + name))), 1)
                                    .otherwise(0))
                            .alias(name))
                    .toArray(Column[]::new);
            Row counts = pairs.agg(differences[0], Arrays.copyOfRange(differences, 1, differences.length))
                    .first();
            for (int i = 0; i < shared.size(); i++) {
                changes.add(new Impact.ColumnChange(
                        shared.get(i), Impact.State.KEPT, counts.isNullAt(i) ? 0 : counts.getLong(i)));
            }
        }
        names(draft).stream()
                .filter(name -> !shared.contains(name))
                .forEach(name -> changes.add(new Impact.ColumnChange(name, Impact.State.ADDED, 0)));
        names(saved).stream()
                .filter(name -> !shared.contains(name))
                .forEach(name -> changes.add(new Impact.ColumnChange(name, Impact.State.REMOVED, 0)));
        return changes;
    }

    private static Impact.Keys keys(PipelineSpec saved, PipelineSpec draft, Dataset<Row> pairs) {
        String before = BEFORE + SystemColumns.FACT_UNIQUE_ID;
        String after = AFTER + SystemColumns.FACT_UNIQUE_ID;
        Row facts =
                pairs.agg(countDistinct(col(before)), countDistinct(col(after))).first();
        long merged = pairs.groupBy(after)
                .agg(countDistinct(col(before)).alias("sources"))
                .where(col("sources").gt(1))
                .count();
        long split = pairs.groupBy(before)
                .agg(countDistinct(col(after)).alias("targets"))
                .where(col("targets").gt(1))
                .count();
        return new Impact.Keys(
                !saved.history().keys().equals(draft.history().keys()),
                facts.getLong(0),
                facts.getLong(1),
                merged,
                split);
    }

    private static long changedFacts(Dataset<Row> pairs) {
        return pairs.where(col(BEFORE + SystemColumns.FACT_UNIQUE_ID)
                        .equalTo(col(AFTER + SystemColumns.FACT_UNIQUE_ID))
                        .and(not(col(BEFORE + SystemColumns.CONTENT_HASH)
                                .equalTo(col(AFTER + SystemColumns.CONTENT_HASH)))))
                .select(AFTER + SystemColumns.FACT_UNIQUE_ID)
                .distinct()
                .count();
    }

    public Impact compare(PipelineSpec saved, PipelineSpec draft, Sample sample, String runId) {
        Dataset<Row> before = refine(sample, saved, runId);
        Dataset<Row> after = refine(sample, draft, runId);
        try {
            Expectations.Counts countsBefore = expectations.measure(before, saved);
            Expectations.Counts countsAfter = expectations.measure(after, draft);
            List<String> shared = shared(saved, draft);
            Dataset<Row> pairs = side(before, BEFORE, shared)
                    .join(side(after, AFTER, shared), Sample.ROW)
                    .persist(StorageLevel.DISK_ONLY());
            try {
                return new Impact(
                        draft.dataset(),
                        sample.origin(),
                        countsAfter.evaluated(),
                        rules(saved, draft, countsBefore, countsAfter),
                        columns(saved, draft, pairs, shared),
                        keys(saved, draft, pairs),
                        changedFacts(pairs),
                        gold(saved, draft, before, after));
            } finally {
                pairs.unpersist();
            }
        } finally {
            before.unpersist();
            after.unpersist();
        }
    }

    public Impact derived(PipelineSpec saved, PipelineSpec draft) {
        return new Impact(draft.dataset(), null, 0, List.of(), List.of(), new Impact.Keys(false, 0, 0, 0, 0), 0,
                gold(saved, draft, null, null));
    }

    private Dataset<Row> refine(Sample sample, PipelineSpec spec, String runId) {
        Dataset<Row> silver = SilverFlow.project(sample.rows(), spec, runId, steps, Sample.ROW);
        return silver.withColumn(
                        SystemColumns.CONTENT_HASH, CanonicalEncoding.sha256(silver.schema(), spec.changeColumns()))
                .persist(StorageLevel.DISK_ONLY());
    }

    private List<Impact.Gold> gold(PipelineSpec saved, PipelineSpec draft, Dataset<Row> before, Dataset<Row> after) {
        Map<String, Long> rowsBefore = new LinkedHashMap<>();
        GoldPlanner previous = views.planner(saved, before, steps);
        for (GoldSpec gold : saved.gold()) {
            try {
                rowsBefore.put(gold.name(), previous.table(gold.name()).count());
            } catch (Exception e) {
                rowsBefore.put(gold.name(), null);
            }
        }
        GoldPlanner next = views.planner(draft, after, steps);
        List<Impact.Gold> results = new ArrayList<>();
        for (GoldSpec gold : draft.gold()) {
            try {
                results.add(new Impact.Gold(
                        gold.name(), rowsBefore.get(gold.name()), next.table(gold.name()).count(), null));
            } catch (Exception e) {
                results.add(new Impact.Gold(gold.name(), rowsBefore.get(gold.name()), null, Failures.message(e)));
            }
        }
        return results;
    }
}
