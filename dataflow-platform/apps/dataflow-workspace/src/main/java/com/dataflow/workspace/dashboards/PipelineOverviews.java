package com.dataflow.workspace.dashboards;

import com.dataflow.spec.PipelineSpec;
import com.dataflow.workspace.catalog.Catalog;
import com.dataflow.workspace.catalog.LakeTable;
import com.dataflow.workspace.catalog.Quarantine;
import com.dataflow.workspace.catalog.TableService;
import com.dataflow.workspace.catalog.TableStorage;
import com.dataflow.workspace.runs.EventLog;
import com.dataflow.workspace.runs.EventRun;
import com.dataflow.workspace.runs.RunService;
import com.dataflow.workspace.runs.ScheduleState;
import com.dataflow.workspace.runs.Schedules;
import com.dataflow.workspace.specs.SpecStore;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class PipelineOverviews {

    private static final int RUNS = 30;
    private static final Duration GRACE = Duration.ofHours(1);

    private final SpecStore specs;
    private final TableService tables;
    private final EventLog events;
    private final RunService runs;
    private final Quarantine quarantine;
    private final Schedules schedules;
    private final Clock clock;
    private final LruCache storage = new LruCache();

    public PipelineOverviews(
            SpecStore specs,
            TableService tables,
            EventLog events,
            RunService runs,
            Quarantine quarantine,
            Schedules schedules,
            Clock clock) {
        this.specs = specs;
        this.tables = tables;
        this.events = events;
        this.runs = runs;
        this.quarantine = quarantine;
        this.schedules = schedules;
        this.clock = clock;
    }

    public Overview overview(String dataset) {
        PipelineSpec spec = specs.spec(dataset);
        List<EventRun> recent = runs.eventRuns(dataset, RUNS);
        LakeTable quarantined = tables.tables(dataset).stream()
                .filter(table -> table.id().equals(Catalog.QUARANTINE) && table.exists())
                .findFirst()
                .orElse(null);
        return new Overview(
                dataset,
                spec.schedule(),
                freshness(schedule(dataset, spec.schedule()), recent),
                recent,
                events.latestFlows(dataset),
                events.ruleRuns(dataset, RUNS),
                quarantined == null ? List.of() : quarantine.reasons(tables.source(dataset, Catalog.QUARANTINE)),
                storage(dataset));
    }

    private List<TableStorage> storage(String dataset) {
        List<TableStorage> measured = new ArrayList<>();
        for (LakeTable table : tables.tables(dataset)) {
            if (!table.delta() || !table.exists() || Catalog.CURRENT.equals(table.id())) {
                continue;
            }
            String key = String.join("|", dataset, table.id(), String.valueOf(table.commits()));
            TableStorage known = storage.get(key) instanceof TableStorage cached ? cached : tables.storage(table);
            storage.put(key, known);
            measured.add(known);
        }
        return measured;
    }

    Freshness freshness(ScheduleState schedule, List<EventRun> runs) {
        Instant last = runs.stream()
                .filter(run -> "COMPLETED".equals(run.status()) && run.endedAt() != null)
                .map(run -> ChartQueries.parse(run.endedAt()).toInstant(ZoneOffset.UTC))
                .max(Instant::compareTo)
                .orElse(null);
        String lastText = last == null ? null : last.toString();
        if (schedule == null || schedule.cron() == null || schedule.cron().isBlank()) {
            return new Freshness("manual", lastText, null);
        }
        if (!schedule.running()) {
            return new Freshness("paused", lastText, null);
        }
        if (last == null) {
            return new Freshness("never", null, null);
        }
        ZoneId zone = schedule.timezone() == null ? ZoneOffset.UTC : ZoneId.of(schedule.timezone());
        ZonedDateTime next = CronExpression.parse("0 " + schedule.cron()).next(last.atZone(zone));
        if (next == null) {
            return new Freshness("manual", lastText, null);
        }
        Instant expected = next.toInstant().plus(GRACE);
        return new Freshness(clock.instant().isAfter(expected) ? "late" : "fresh", lastText, expected.toString());
    }

    private ScheduleState schedule(String dataset, String cron) {
        if (cron == null) {
            return null;
        }
        try {
            return schedules.state(dataset);
        } catch (RuntimeException e) {
            return new ScheduleState(cron, null, true, null);
        }
    }
}
