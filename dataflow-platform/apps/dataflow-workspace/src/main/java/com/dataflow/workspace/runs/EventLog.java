package com.dataflow.workspace.runs;

import com.dataflow.spec.lake.EventTypes;
import com.dataflow.spec.lake.Lake;
import com.dataflow.spec.settings.RecordedSetting;
import com.dataflow.workspace.catalog.LakeIndex;
import com.dataflow.workspace.catalog.TableLog;
import com.dataflow.workspace.serving.ClickHouse;
import com.dataflow.workspace.serving.LakeFunctions;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Record1;
import org.jooq.Select;
import org.jooq.SelectJoinStep;
import org.jooq.Table;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static com.dataflow.workspace.serving.ClickHouseDsl.argMax;
import static com.dataflow.workspace.serving.ClickHouseDsl.column;
import static com.dataflow.workspace.serving.ClickHouseDsl.isoUtc;
import static com.dataflow.workspace.serving.ClickHouseDsl.jsonHas;
import static com.dataflow.workspace.serving.ClickHouseDsl.jsonRaw;
import static com.dataflow.workspace.serving.ClickHouseDsl.jsonText;
import static com.dataflow.workspace.serving.ClickHouseDsl.millisBetween;
import static org.jooq.impl.DSL.and;
import static org.jooq.impl.DSL.anyValue;
import static org.jooq.impl.DSL.max;
import static org.jooq.impl.DSL.min;
import static org.jooq.impl.DSL.noCondition;
import static org.jooq.impl.DSL.select;
import static org.jooq.impl.DSL.sum;

@Component
public class EventLog {

    private static final String STARTED_AS = "startedAt";
    private static final String DURATION_AS = "durationMs";
    private static final String RUN_AS = "runId";
    private static final Field<String> TS = column("ts", String.class);
    private static final Field<String> RUN_ID = column("run_id", String.class);
    private static final Field<String> TRIGGER = column("trigger", String.class);
    private static final Field<String> PARTITION = column("partition", String.class);
    private static final Field<String> EVENT_TYPE = column("event_type", String.class);
    private static final Field<String> FLOW = column("flow", String.class);
    private static final Field<String> STATUS = column("status", String.class);
    private static final Field<String> STARTED_AT = column("started_at", String.class);
    private static final Field<String> ENDED_AT = column("ended_at", String.class);
    private static final Field<Long> ROWS_IN = column("rows_in", Long.class);
    private static final Field<Long> ROWS_OUT = column("rows_out", Long.class);
    private static final Field<Long> ROWS_DROPPED = column("rows_dropped", Long.class);
    private static final Field<String> DETAILS = column("details", String.class);
    private static final Field<String> SPARK_APP = jsonText(DETAILS, "spark_app");

    private final ClickHouse serving;
    private final LakeFunctions lake;
    private final LakeIndex index;

    public EventLog(ClickHouse serving, LakeFunctions lake, LakeIndex index) {
        this.serving = serving;
        this.lake = lake;
        this.index = index;
    }

    private static SelectJoinStep<?> flowEvents(Table<Record> events) {
        return select(
                        FLOW,
                        STATUS,
                        STARTED_AT.as(STARTED_AS),
                        ENDED_AT.as("endedAt"),
                        millisBetween(STARTED_AT, ENDED_AT).as(DURATION_AS),
                        ROWS_IN.as("rowsIn"),
                        ROWS_OUT.as("rowsOut"),
                        ROWS_DROPPED.as("rowsDropped"),
                        DETAILS)
                .from(events);
    }

    public List<EventRun> runs(String dataset, int limit) {
        if (!written(dataset)) {
            return List.of();
        }
        Field<String> started = min(STARTED_AT).as(STARTED_AS);
        return serving.fetchInto(
                select(
                                RUN_ID.as(RUN_AS),
                                argMax(STATUS, TS).as("status"),
                                anyValue(TRIGGER).as("trigger"),
                                anyValue(PARTITION).as("partition"),
                                started,
                                max(ENDED_AT).as("endedAt"),
                                millisBetween(min(STARTED_AT), max(ENDED_AT)).as(DURATION_AS))
                        .from(events(dataset))
                        .where(EVENT_TYPE.eq(EventTypes.RUN))
                        .groupBy(RUN_ID)
                        .orderBy(started.desc())
                        .limit(limit),
                EventRun.class);
    }

    public List<FlowEvent> latestFlows(String dataset) {
        if (!written(dataset)) {
            return List.of();
        }
        Table<Record> events = events(dataset);
        return serving.fetchInto(
                flowEvents(events)
                        .where(EVENT_TYPE.eq(EventTypes.FLOW_PROGRESS))
                        .and(RUN_ID.eq(select(argMax(RUN_ID, TS)).from(events).where(EVENT_TYPE.eq(EventTypes.RUN)))),
                FlowEvent.class);
    }

    public List<FlowEvent> flows(String dataset, String runId) {
        if (!written(dataset)) {
            return List.of();
        }
        return serving.fetchInto(
                flowEvents(events(dataset))
                        .where(EVENT_TYPE.eq(EventTypes.FLOW_PROGRESS))
                        .and(RUN_ID.eq(runId))
                        .orderBy(STARTED_AT, FLOW),
                FlowEvent.class);
    }

    public List<FlowCell> flowCells(String dataset, int runs) {
        if (!written(dataset)) {
            return List.of();
        }
        Table<Record> events = events(dataset);
        return serving.fetchInto(
                select(
                                RUN_ID.as(RUN_AS),
                                FLOW,
                                STATUS,
                                STARTED_AT.as(STARTED_AS),
                                millisBetween(STARTED_AT, ENDED_AT).as(DURATION_AS),
                                ROWS_OUT.as("rowsOut"))
                        .from(events)
                        .where(EVENT_TYPE.eq(EventTypes.FLOW_PROGRESS))
                        .and(RUN_ID.in(lastRuns(events, runs)))
                        .orderBy(STARTED_AT, FLOW),
                FlowCell.class);
    }

    public Optional<RunRuntime> runtime(String dataset, String runId) {
        return runtimes(dataset, RUN_ID.eq(runId));
    }

    public Optional<String> sparkApp(String dataset, String runId) {
        if (!written(dataset)) {
            return Optional.empty();
        }
        return serving.fetch(
                        select(SPARK_APP)
                                .from(events(dataset))
                                .where(EVENT_TYPE.eq(EventTypes.RUN))
                                .and(RUN_ID.eq(runId))
                                .and(SPARK_APP.ne(""))
                                .limit(1),
                        1)
                .rows()
                .stream()
                .map(row -> String.valueOf(row.getFirst()))
                .findFirst();
    }

    public Optional<RunRuntime> latestRuntime(String dataset) {
        return runtimes(dataset, noCondition());
    }

    public Optional<EngineSettingsRun> latestEngineSettings() {
        return index.logs().values().stream()
                .sorted(Comparator.comparing(TableLog::updatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(log -> Lake.eventsDataset(log.key()))
                .flatMap(Optional::stream)
                .map(this::engineSettings)
                .flatMap(Optional::stream)
                .findFirst();
    }

    private Optional<EngineSettingsRun> engineSettings(String dataset) {
        return serving.fetchInto(
                        select(RUN_ID.as(RUN_AS), isoUtc(TS).as("at"), jsonRaw(DETAILS, RecordedSetting.RUN_DETAIL).as(RecordedSetting.RUN_DETAIL))
                                .from(events(dataset))
                                .where(EVENT_TYPE.eq(EventTypes.RUN))
                                .and(jsonHas(DETAILS, RecordedSetting.RUN_DETAIL))
                                .orderBy(TS.desc())
                                .limit(1),
                        EngineSettingsRun.Recorded.class)
                .stream()
                .findFirst()
                .map(recorded -> new EngineSettingsRun(dataset, recorded));
    }

    private Optional<RunRuntime> runtimes(String dataset, Condition which) {
        if (!written(dataset)) {
            return Optional.empty();
        }
        return serving.fetchInto(
                        select(RunRuntime.KEYS.stream().map(key -> jsonText(DETAILS, key).as(key)).toList())
                                .from(events(dataset))
                                .where(EVENT_TYPE.eq(EventTypes.RUN))
                                .and(which)
                                .and(and(RunRuntime.KEYS.stream().map(key -> jsonText(DETAILS, key).ne("")).toList()))
                                .orderBy(TS.desc())
                                .limit(1),
                        RunRuntime.class)
                .stream()
                .findFirst();
    }

    public List<ExpectationResult> expectations(String dataset, String runId) {
        if (!written(dataset)) {
            return List.of();
        }
        return serving.fetchInto(
                select(
                                jsonText(DETAILS, EventTypes.EXPECTATION).as("name"),
                                jsonText(DETAILS, "check").as("check"),
                                jsonText(DETAILS, "action").as("action"),
                                ROWS_IN.as("evaluated"),
                                ROWS_DROPPED.as("failing"))
                        .from(events(dataset))
                        .where(EVENT_TYPE.eq(EventTypes.EXPECTATION))
                        .and(RUN_ID.eq(runId))
                        .orderBy(STARTED_AT),
                ExpectationResult.class);
    }

    public List<RuleRun> ruleRuns(String dataset, int runs) {
        if (!written(dataset)) {
            return List.of();
        }
        Table<Record> events = events(dataset);
        Field<String> rule = jsonText(DETAILS, EventTypes.EXPECTATION).as("rule");
        Field<String> at = max(ENDED_AT).as("at");
        return serving.fetchInto(
                select(
                                rule,
                                RUN_ID.as(RUN_AS),
                                at,
                                sum(ROWS_DROPPED).as("failing"),
                                sum(ROWS_IN).as("evaluated"))
                        .from(events)
                        .where(EVENT_TYPE.eq(EventTypes.EXPECTATION))
                        .and(RUN_ID.in(lastRuns(events, runs)))
                        .groupBy(rule, RUN_ID)
                        .orderBy(at),
                RuleRun.class);
    }

    private static Select<Record1<String>> lastRuns(Table<Record> events, int runs) {
        return select(RUN_ID)
                .from(events)
                .where(EVENT_TYPE.eq(EventTypes.RUN))
                .groupBy(RUN_ID)
                .orderBy(max(TS).desc())
                .limit(runs);
    }

    private boolean written(String dataset) {
        return index.logs().containsKey(Lake.eventsKey(dataset));
    }

    private Table<Record> events(String dataset) {
        return lake.delta(Lake.eventsKey(dataset));
    }
}
