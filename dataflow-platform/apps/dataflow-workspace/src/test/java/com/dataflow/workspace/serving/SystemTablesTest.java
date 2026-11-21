package com.dataflow.workspace.serving;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SystemTablesTest {

    @Test
    void usageCountsFinishedQueriesPerTableAndLeavesSystemTablesOut() {
        RecordingClickHouse serving = new RecordingClickHouse()
                .answer(
                        sql -> sql.contains("query_log"),
                        List.of("table", "queries", "last"),
                        List.of(
                                List.of("earthquakes.current", 41L, "2026-10-28 11:00:00"),
                                List.of("system.parts", 300L, "2026-10-28 11:00:00"),
                                List.of("serving.taxi_trips", 7L, "2026-10-27 09:00:00")));

        List<TableUsage> usage = new SystemTables(serving).usage(LocalDate.parse("2026-09-28"));

        assertThat(usage)
                .extracting(TableUsage::table, TableUsage::queries)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("earthquakes.current", 41L),
                        org.assertj.core.groups.Tuple.tuple("serving.taxi_trips", 7L));
        assertThat(serving.statements().getFirst())
                .contains(
                        "arrayJoin(\"tables\")",
                        "\"system\".\"query_log\"",
                        "\"type\" = 'QueryFinish'",
                        "\"event_date\" >= '2026-09-28'",
                        "order by count(*) desc");
    }

    @Test
    void healthReadsTheLastHourResidentMemoryAndFailedRefreshes() {
        RecordingClickHouse serving = new RecordingClickHouse()
                .answer(
                        sql -> sql.contains("query_log"),
                        List.of("queries", "failed", "averageMs", "slowestMs"),
                        List.of(List.of(40L, 2L, 12.5, 900L)))
                .answer(sql -> sql.contains("MemoryResident"), List.of("value"), List.of(List.of(1024L)))
                .answer(
                        sql -> sql.contains("view_refreshes"),
                        List.of("database", "view", "error"),
                        List.of(List.of("serving", "trips_refresh", "Timeout")));

        ServingHealth health = new SystemTables(serving).health();

        assertThat(health)
                .isEqualTo(new ServingHealth(
                        new ServingHealth.Activity(40, 2, 12.5, 900),
                        1024,
                        List.of(new ServingHealth.FailedRefresh("serving", "trips_refresh", "Timeout"))));
        assertThat(serving.statements().getFirst())
                .contains("subtractHours(now(), 1)", "\"is_initial_query\" = 1", "\"type\" <> 'QueryFinish'");
    }

    @Test
    void columnUsageKeepsTheColumnsOfThatTable() {
        RecordingClickHouse serving = new RecordingClickHouse()
                .answer(
                        sql -> sql.contains("query_log"),
                        List.of("name", "queries"),
                        List.of(
                                List.of("earthquakes.current.magnitude", 12L),
                                List.of("zones.current.borough", 9L),
                                List.of("earthquakes.current.place", 3L)));

        List<ColumnUsage> usage =
                new SystemTables(serving).columnUsage("earthquakes.current", LocalDate.parse("2026-09-28"));

        assertThat(usage)
                .containsExactly(new ColumnUsage("magnitude", 12L), new ColumnUsage("place", 3L));
        assertThat(serving.statements().getFirst())
                .contains("arrayJoin(\"columns\")", "has(\"tables\", 'earthquakes.current')", "'QueryFinish'");
    }

    @Test
    void historyListsEditorQueriesOfOnePersonOrEveryone() {
        RecordingClickHouse serving = new RecordingClickHouse()
                .answer(
                        sql -> sql.contains("query_log"),
                        List.of("time", "person", "query", "durationMs", "rowsRead", "bytesRead", "resultRows", "status", "error"),
                        List.of(List.of("2026-10-30 14:00:00", "ada", "SELECT 1", 3L, 1L, 1L, 1L, "QueryFinish", "")));
        SystemTables system = new SystemTables(serving);

        List<QueryRun> own = system.history("ada", LocalDate.parse("2026-10-23"), 200);
        system.history(null, LocalDate.parse("2026-10-23"), 200);

        assertThat(own).singleElement().returns("ada", QueryRun::person).returns("QueryFinish", QueryRun::status);
        assertThat(serving.statements().getFirst())
                .contains("\"log_comment\" = 'sql:ada'", "\"is_initial_query\" = 1", "\"event_date\" >= '2026-10-23'",
                        "order by \"event_time_microseconds\" desc", "limit 200");
        assertThat(serving.statements().get(1)).contains("startsWith(\"log_comment\", 'sql:')").doesNotContain("'sql:ada'");
    }
}
