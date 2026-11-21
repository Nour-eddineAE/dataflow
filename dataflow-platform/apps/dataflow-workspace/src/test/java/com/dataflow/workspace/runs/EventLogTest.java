package com.dataflow.workspace.runs;

import com.dataflow.workspace.Workspace;
import com.dataflow.workspace.catalog.DeltaLog;
import com.dataflow.workspace.catalog.LakeIndex;
import com.dataflow.workspace.serving.LakeFunctions;
import com.dataflow.workspace.serving.RecordingClickHouse;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EventLogTest {

    private final RecordingClickHouse serving = new RecordingClickHouse();
    private final LakeFunctions lake = new LakeFunctions(Workspace.properties());
    private final EventLog events =
            new EventLog(serving, lake, new LakeIndex(new DeltaLog(serving, lake, Workspace.properties()), Clock.systemUTC()));

    @Test
    void runs_summarisesTheRunEventsOfADataset() {
        written();
        serving.answer(
                sql -> sql.contains("argMax(\"status\""),
                List.of("runId", "status", "trigger", "partition", "startedAt", "endedAt", "durationMs"),
                List.of(Arrays.asList(
                        "run-1",
                        "COMPLETED",
                        "schedule",
                        null,
                        "2026-10-25 00:00:11.128904",
                        "2026-10-25 00:00:37.493880",
                        26365L)));

        assertThat(events.runs("earthquakes", 10))
                .containsExactly(new EventRun(
                        "run-1",
                        "COMPLETED",
                        "schedule",
                        null,
                        "2026-10-25 00:00:11.128904",
                        "2026-10-25 00:00:37.493880",
                        26365L));
        assertThat(serving.statements().getLast())
                .contains("from deltaLake(lake, filename = 'system/events/earthquakes/') where \"event_type\" = 'run'")
                .endsWith("limit 10");
    }

    @Test
    void sparkApp_isTheApplicationTheRunRecorded() {
        written();
        serving.answer(sql -> sql.contains("'spark_app'"), List.of("app"), List.of(List.of("local-1762437000000")));

        assertThat(events.sparkApp("earthquakes", "run-1")).contains("local-1762437000000");
        assertThat(serving.statements().getLast()).contains("\"run_id\" = 'run-1'").endsWith("limit 1");
    }

    @Test
    void expectations_readTheCountsOfARun() {
        written();
        serving.answer(
                sql -> sql.contains("'expectation'"),
                List.of("name", "check", "action", "evaluated", "failing"),
                List.of(List.of("has_magnitude", "magnitude IS NOT NULL", "drop", 9992L, 3L)));

        assertThat(events.expectations("earthquakes", "run-'1"))
                .containsExactly(new ExpectationResult("has_magnitude", "magnitude IS NOT NULL", "drop", 9992L, 3L));
        assertThat(serving.statements().getLast()).contains("\"run_id\" = 'run-''1'");
    }

    @Test
    void flowCells_readEveryFlowOfTheLastRuns() {
        written();
        serving.answer(
                sql -> sql.contains("'flow_progress'"),
                List.of("runId", "flow", "status", "startedAt", "durationMs", "rowsOut"),
                List.of(
                        List.of("run-1", "ingest", "COMPLETED", "2026-10-25 00:00:11.128904", 1200L, 1L),
                        Arrays.asList("run-1", "gold.daily", "SKIPPED", "2026-10-25 00:00:30.000000", null, null)));

        assertThat(events.flowCells("earthquakes", 20)).containsExactly(
                new FlowCell("run-1", "ingest", "COMPLETED", "2026-10-25 00:00:11.128904", 1200L, 1L),
                new FlowCell("run-1", "gold.daily", "SKIPPED", "2026-10-25 00:00:30.000000", null, null));
        assertThat(serving.statements().getLast())
                .contains("\"event_type\" = 'flow_progress'")
                .contains("\"run_id\" in (select \"run_id\"")
                .contains("limit 20)");
    }

    @Test
    void runtime_readsTheVersionsARunRecorded() {
        written();
        serving.answer(
                sql -> sql.contains("JSONExtractString(\"details\", 'spark')"),
                List.of("dataflow", "spark", "delta", "scala", "java"),
                List.of(List.of("3.0.0", "4.2.0", "4.4.1", "2.13.16", "21.0.7")));

        assertThat(events.runtime("earthquakes", "run-1"))
                .contains(new RunRuntime("3.0.0", "4.2.0", "4.4.1", "2.13.16", "21.0.7"));
        assertThat(serving.statements().getLast())
                .contains("\"run_id\" = 'run-1'")
                .contains("JSONExtractString(\"details\", 'dataflow') <> ''")
                .contains("JSONExtractString(\"details\", 'java') <> ''")
                .endsWith("limit 1");
        assertThat(events.latestRuntime("earthquakes")).isPresent();
    }

    @Test
    void latestEngineSettings_comesFromTheMostRecentlyWrittenEventLogThatRecordedThem() {
        serving.answer(
                sql -> sql.contains("format = 'One'"),
                List.of("key", "commits", "updatedAt"),
                List.of(
                        List.of("system/events/earthquakes/", 4L, "2026-11-09T09:00:00Z"),
                        List.of("system/events/taxi_trips/", 2L, "2026-11-09T10:00:00Z"),
                        List.of("history/earthquakes/", 9L, "2026-11-09T11:00:00Z")));
        serving.answer(
                sql -> sql.contains("JSONHas") && sql.contains("system/events/taxi_trips/"),
                List.of("runId", "at", "settings"),
                List.of());
        serving.answer(
                sql -> sql.contains("JSONHas") && sql.contains("system/events/earthquakes/"),
                List.of("runId", "at", "settings"),
                List.of(List.of("run-1", "2026-11-09T08:59:00Z", "[]")));

        assertThat(events.latestEngineSettings())
                .contains(new EngineSettingsRun("earthquakes", "run-1", "2026-11-09T08:59:00Z", "[]"));
        assertThat(serving.statements()).noneMatch(sql -> sql.contains("history/earthquakes/") && sql.contains("JSONHas"));
    }

    @Test
    void everyRead_isEmptyWhileTheDatasetHasNoEventLog() {
        assertThat(events.runs("earthquakes", 10)).isEmpty();
        assertThat(events.latestFlows("earthquakes")).isEmpty();
        assertThat(events.ruleRuns("earthquakes", 5)).isEmpty();
        assertThat(events.flowCells("earthquakes", 5)).isEmpty();
        assertThat(events.latestRuntime("earthquakes")).isEmpty();
        assertThat(serving.statements()).singleElement().asString().contains("format = 'One'");
    }

    private void written() {
        serving.answer(
                sql -> sql.contains("format = 'One'"),
                List.of("key", "commits"),
                List.of(List.of("system/events/earthquakes/", 4L)));
    }
}
