package com.dataflow.workspace.dashboards;

import com.dataflow.spec.PipelineSpec;
import com.dataflow.workspace.config.WorkspaceProperties;
import com.dataflow.workspace.runs.RunService;
import com.dataflow.workspace.serving.ClickHouse;
import com.dataflow.workspace.specs.SpecStore;
import lombok.extern.slf4j.Slf4j;
import org.jooq.InsertValuesStep3;
import org.jooq.Record;
import org.jooq.Table;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.insertInto;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.query;
import static org.jooq.impl.DSL.table;

@Slf4j
@Component
public class VerdictMirror {

    private static final long FIRST_MIRROR_MS = 30_000;
    static final String TABLE = "run_verdicts";

    private final SpecStore specs;
    private final RunService runs;
    private final ClickHouse serving;

    public VerdictMirror(SpecStore specs, RunService runs, ClickHouse serving) {
        this.specs = specs;
        this.runs = runs;
        this.serving = serving;
    }

    @Scheduled(fixedDelayString = WorkspaceProperties.SYNC_EVERY, initialDelay = FIRST_MIRROR_MS)
    public void tryMirror() {
        try {
            mirror();
        } catch (RuntimeException e) {
            log.warn("Run verdicts not mirrored to serving.{}: {}", TABLE, e.getMessage());
        }
    }

    public void mirror() {
        Table<Record> target = table(name(serving.database(), TABLE));
        serving.execute(query(
                "CREATE TABLE IF NOT EXISTS {0} (run_id String, dataset String, status String,"
                    + " checked_at DateTime64(3, 'UTC') DEFAULT now64(3)) ENGINE = ReplacingMergeTree(checked_at)"
                    + " ORDER BY run_id",
                target));
        InsertValuesStep3<Record, String, String, String> insert = insertInto(
                target,
                field(name("run_id"), String.class),
                field(name("dataset"), String.class),
                field(name("status"), String.class));
        int rows = 0;
        for (PipelineSpec spec : specs.valid()) {
            Map<String, String> verdicts = runs.verdicts(spec.dataset(), FreshnessMirror.RECENT_RUNS);
            for (Map.Entry<String, String> verdict : verdicts.entrySet()) {
                insert = insert.values(verdict.getKey(), spec.dataset(), verdict.getValue());
                rows++;
            }
        }
        if (rows > 0) {
            serving.execute(insert);
        }
    }
}
