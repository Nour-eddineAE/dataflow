package com.dataflow.engine.core.flows;

import com.dataflow.engine.core.events.RunEvent;
import com.dataflow.engine.core.run.Flow;
import com.dataflow.engine.core.run.FlowResult;
import com.dataflow.engine.core.run.FlowStatus;
import com.dataflow.engine.core.run.RunContext;
import com.dataflow.spec.GoldSpec;
import com.dataflow.spec.PipelineSpec;
import com.dataflow.spec.SystemColumns;
import com.dataflow.spec.lake.EventTypes;
import com.dataflow.spec.lake.FlowNames;
import com.dataflow.spec.lake.Layer;
import io.delta.tables.DeltaTable;
import lombok.extern.slf4j.Slf4j;
import org.apache.spark.sql.Row;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.apache.spark.sql.functions.col;
import static org.apache.spark.sql.functions.expr;

@Slf4j
@SuppressWarnings("resource")
public class MaintenanceFlow implements Flow {

    private static final String RETENTION = "delta.deletedFileRetentionDuration";

    private final Duration interval;
    private final int retentionHours;
    private final Set<String> inputs;

    public MaintenanceFlow(Duration interval, int retentionHours, Set<String> inputs) {
        this.interval = interval;
        this.retentionHours = retentionHours;
        this.inputs = inputs;
    }

    static long forget(DeltaTable delta, int days) {
        delta.delete(col(SystemColumns.END_AT)
                .isNotNull()
                .and(col(SystemColumns.END_AT)
                        .lt(expr("current_timestamp() - make_interval(0, 0, 0, %d)".formatted(days)))));
        Row last = delta.history(1).select("operation", "operationMetrics").first();
        if (!"DELETE".equals(last.getString(0))) {
            return 0;
        }
        Map<String, String> metrics = last.getJavaMap(1);
        return Long.parseLong(metrics.getOrDefault("numDeletedRows", "0"));
    }

    private static List<String> tables(RunContext context) {
        PipelineSpec plan = context.plan();
        String dataset = plan.dataset();
        List<String> tables = new ArrayList<>();
        for (Layer layer : List.of(Layer.BRONZE, Layer.SILVER, Layer.QUARANTINE, Layer.HISTORY)) {
            tables.add(context.lake().table(layer, dataset));
        }
        for (GoldSpec gold : plan.materialized()) {
            tables.add(context.lake().table(Layer.GOLD, dataset, gold.name()));
        }
        tables.add(context.lake().events(dataset));
        return tables;
    }

    @Override
    public String name() {
        return FlowNames.MAINTENANCE;
    }

    @Override
    public Set<String> inputs() {
        return inputs;
    }

    @Override
    public FlowResult run(RunContext context) {
        Instant now = context.clock().instant();
        Optional<Instant> last = context.events().lastCompleted(context.dataset(), EventTypes.MAINTENANCE);
        FlowResult result = new FlowResult();
        if (last.isPresent() && now.isBefore(last.get().plus(interval))) {
            return result.detail("due_at", last.get().plus(interval).toString());
        }
        Map<String, Object> compacted = new LinkedHashMap<>();
        for (String table : tables(context)) {
            if (context.tables().exists(table)) {
                maintain(context, table, compacted);
            }
        }
        record(context, now, compacted);
        return result.detail("maintained", compacted).detail("ran", true);
    }

    private void maintain(RunContext context, String table, Map<String, Object> compacted) {
        log.info("Maintaining {}", table);
        try {
            DeltaTable delta = DeltaTable.forPath(context.spark(), table);
            Integer keep = context.plan().history().keepClosedDays();
            if (keep != null && table.equals(context.lake().table(Layer.HISTORY, context.dataset()))) {
                compacted.put(table + " closed versions removed", forget(delta, keep));
            }
            Row metrics = delta.optimize().executeCompaction().select("metrics").first().getStruct(0);
            retain(context, delta, table);
            vacuum(context, table);
            compacted.put(table, metrics.<Long>getAs("numFilesRemoved") + " -> " + metrics.<Long>getAs("numFilesAdded"));
        } catch (RuntimeException e) {
            log.warn("Maintenance of {} failed, retrying next window: {}", table, e.getMessage());
            compacted.put(table, "failed: " + e.getMessage());
        }
    }

    private void record(RunContext context, Instant started, Map<String, Object> compacted) {
        context.events()
                .record(new RunEvent(
                        UUID.randomUUID().toString(),
                        context.clock().instant(),
                        context.runId(),
                        context.dataset(),
                        context.plan().version(),
                        null,
                        context.partition(),
                        EventTypes.MAINTENANCE,
                        name(),
                        FlowStatus.COMPLETED.name(),
                        started,
                        context.clock().instant(),
                        null,
                        null,
                        null,
                        compacted.toString()));
    }

    private void retain(RunContext context, DeltaTable delta, String table) {
        String wanted = "interval %d hours".formatted(retentionHours);
        Map<String, String> properties =
                delta.detail().select("properties").first().getJavaMap(0);
        if (!wanted.equals(properties.get(RETENTION))) {
            context.spark()
                    .sql("ALTER TABLE delta.`%s` SET TBLPROPERTIES ('%s' = '%s')".formatted(table, RETENTION, wanted));
        }
    }

    private void vacuum(RunContext context, String table) {
        try {
            context.spark().sql("VACUUM delta.`%s` LITE".formatted(table));
        } catch (RuntimeException e) {
            log.info("VACUUM LITE of {} not possible ({}); running a full VACUUM", table, e.getMessage());
            context.spark().sql("VACUUM delta.`%s`".formatted(table));
        }
    }
}
