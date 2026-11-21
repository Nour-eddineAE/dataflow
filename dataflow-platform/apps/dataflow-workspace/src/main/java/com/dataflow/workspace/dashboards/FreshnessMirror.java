package com.dataflow.workspace.dashboards;

import com.dataflow.spec.PipelineSpec;
import com.dataflow.workspace.config.WorkspaceProperties;
import com.dataflow.workspace.runs.RunService;
import com.dataflow.workspace.runs.ScheduleState;
import com.dataflow.workspace.runs.Schedules;
import com.dataflow.workspace.serving.ClickHouse;
import com.dataflow.workspace.specs.SpecStore;
import lombok.extern.slf4j.Slf4j;
import org.jooq.Field;
import org.jooq.InsertValuesStep8;
import org.jooq.Record;
import org.jooq.Table;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.insertInto;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.query;
import static org.jooq.impl.DSL.table;

@Slf4j
@Component
public class FreshnessMirror {

    private static final long FIRST_MIRROR_MS = 60_000;
    static final int RECENT_RUNS = 30;
    static final String TABLE = "schedules";
    private static final String UTC = "UTC";
    private static final DateTimeFormatter SECONDS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneOffset.UTC);

    private final SpecStore specs;
    private final Schedules schedules;
    private final RunService runs;
    private final PipelineOverviews overviews;
    private final ClickHouse serving;
    private final Clock clock;

    public FreshnessMirror(
            SpecStore specs,
            Schedules schedules,
            RunService runs,
            PipelineOverviews overviews,
            ClickHouse serving,
            Clock clock) {
        this.specs = specs;
        this.schedules = schedules;
        this.runs = runs;
        this.overviews = overviews;
        this.serving = serving;
        this.clock = clock;
    }

    private static String text(Instant instant) {
        return instant == null ? null : SECONDS.format(instant);
    }

    @Scheduled(fixedDelayString = WorkspaceProperties.SYNC_EVERY, initialDelay = FIRST_MIRROR_MS)
    public void tryMirror() {
        try {
            mirror();
        } catch (RuntimeException e) {
            log.warn("Freshness not mirrored to serving.{}: {}", TABLE, e.getMessage());
        }
    }

    public void mirror() {
        Table<Record> target = table(name(serving.database(), TABLE));
        serving.execute(query(
                "CREATE TABLE IF NOT EXISTS {0} (dataset String, cron String, running UInt8, state String,"
                    + " last_success Nullable(DateTime64(3, 'UTC')), expected_by Nullable(DateTime64(3, 'UTC')),"
                    + " hours_late Float64, checked_at DateTime64(3, 'UTC')) ENGINE = ReplacingMergeTree(checked_at)"
                    + " ORDER BY dataset",
                target));
        Map<String, Boolean> running = schedules.running();
        Instant now = clock.instant();
        Field<String> dataset = field(name("dataset"), String.class);
        InsertValuesStep8<Record, String, String, Integer, String, String, String, Double, String> insert = insertInto(
                target,
                dataset,
                field(name("cron"), String.class),
                field(name("running"), Integer.class),
                field(name("state"), String.class),
                field(name("last_success"), String.class),
                field(name("expected_by"), String.class),
                field(name("hours_late"), Double.class),
                field(name("checked_at"), String.class));
        int rows = 0;
        for (PipelineSpec spec : specs.valid()) {
            if (spec.schedule() == null) {
                continue;
            }
            boolean on = running.getOrDefault(spec.dataset(), true);
            Freshness freshness = overviews.freshness(
                    new ScheduleState(spec.schedule(), UTC, on, null), runs.eventRuns(spec.dataset(), RECENT_RUNS));
            Instant last = freshness.lastSuccess() == null ? null : Instant.parse(freshness.lastSuccess());
            Instant expected = freshness.expectedBy() == null ? null : Instant.parse(freshness.expectedBy());
            double late = "late".equals(freshness.state()) && expected != null
                    ? Duration.between(expected, now).toMinutes() / (double) Duration.ofHours(1).toMinutes()
                    : 0.0;
            insert = insert.values(
                    spec.dataset(),
                    spec.schedule(),
                    on ? 1 : 0,
                    freshness.state(),
                    text(last),
                    text(expected),
                    late,
                    text(now));
            rows++;
        }
        if (rows > 0) {
            serving.execute(insert);
        }
    }
}
