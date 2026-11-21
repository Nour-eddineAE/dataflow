package com.dataflow.workspace.catalog;

import com.dataflow.workspace.Workspace;
import com.dataflow.workspace.serving.LakeFunctions;
import com.dataflow.workspace.serving.RecordingClickHouse;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.regex.Pattern;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;

class DeltaLogTest {

    private final RecordingClickHouse serving = new RecordingClickHouse();
    private final DeltaLog log = new DeltaLog(serving, new LakeFunctions(Workspace.properties()), Workspace.properties());

    @Test
    void listingsKeepTableKeysRelativeToTheLakeRoot() {
        Pattern key = Pattern.compile(DeltaLog.TABLE_KEY);
        for (String root :
                List.of("lake/", "lake/prod/", "lake/prod/data/", "https://acme.blob.core.windows.net/lake/prod/")) {
            for (String table :
                    List.of("history/earthquakes/", "gold/earthquakes/regions/", "system/events/earthquakes/")) {
                assertThat(key.matcher(root + table + "_delta_log/00000000000000000001.json")
                                .replaceFirst("$1"))
                        .isEqualTo(table);
            }
        }
    }

    @Test
    void tables_readsOneListingOfEveryDeltaLog() {
        serving.answer(
                sql -> sql.contains("format = 'One'"),
                List.of("key", "commits", "updatedAt"),
                List.of(
                        List.of("silver/earthquakes/", 7L, "2026-10-25T07:13:33Z"),
                        List.of("history/earthquakes/", 11L, "2026-10-25T07:13:40Z")));

        assertThat(log.tables())
                .containsOnly(
                        entry(
                                "silver/earthquakes/",
                                new TableLog("silver/earthquakes/", 7, Instant.parse("2026-10-25T07:13:33Z"))),
                        entry(
                                "history/earthquakes/",
                                new TableLog("history/earthquakes/", 11, Instant.parse("2026-10-25T07:13:40Z"))));
        assertThat(serving.statements())
                .singleElement()
                .asString()
                .contains("formatDateTime(max(\"_time\"), '%Y-%m-%dT%H:%i:%SZ', 'UTC') \"updatedAt\"")
                .contains("from s3(lake, filename = '**/_delta_log/*.json', format = 'One')");
    }

    @Test
    void commits_mapsTheCommitInfoOfTheTable() {
        serving.answer(
                sql -> sql.contains("JSONHas"),
                List.of("version", "timestamp", "operation", "parameters", "metrics", "engine"),
                List.of(List.of(
                        11L,
                        "2026-10-25 00:00:30.544",
                        "MERGE",
                        "{}",
                        "{\"numTargetRowsInserted\":\"3\"}",
                        "Apache-Spark/4.2.0")));

        List<Commit> commits = log.commits("history/earthquakes/", 5);

        assertThat(commits)
                .containsExactly(new Commit(
                        11,
                        "2026-10-25 00:00:30.544",
                        "MERGE",
                        "{}",
                        "{\"numTargetRowsInserted\":\"3\"}",
                        "Apache-Spark/4.2.0"));
        assertThat(serving.statements())
                .singleElement()
                .asString()
                .contains("filename = 'history/earthquakes/_delta_log/*.json', format = 'JSONAsString'")
                .endsWith("order by \"version\" desc limit 5");
    }

    @Test
    void liveFiles_countsAddedFilesNotRemovedSince() {
        serving.answer(
                sql -> sql.startsWith("with \"log\""),
                List.of("files", "bytes", "smallFiles", "reclaimedBytes"),
                List.of(List.of(2L, 1000L, 1L, 500L)));

        assertThat(log.liveFiles("silver/earthquakes/")).isEqualTo(new LiveFiles(2, 1000, 1, 500));
        assertThat(serving.statements())
                .singleElement()
                .asString()
                .contains("< 33554432")
                .contains("toUInt64OrZero(JSONExtractString(\"json\", 'commitInfo', 'operationMetrics', 'sizeOfDataToDelete'))");
    }

    @Test
    void storedBytes_sumsTheDataObjectsOfTheTableOnly() {
        serving.answer(sql -> sql.contains("_size"), List.of("sum"), List.of(List.of(1500L)));

        assertThat(log.storedBytes("silver/earthquakes/")).isEqualTo(1500);
        assertThat(serving.statements())
                .singleElement()
                .asString()
                .contains("filename = 'silver/earthquakes/*.parquet', format = 'One'");
    }

    @Test
    void sizeChanges_netsAddedAndRemovedBytesPerDay() {
        serving.answer(
                sql -> sql.contains("group by"),
                List.of("day", "bytes"),
                List.of(List.of("2026-10-24", 900L), List.of("2026-10-25", -100L)));

        assertThat(log.sizeChanges("silver/earthquakes/"))
                .containsExactly(new SizeChange("2026-10-24", 900), new SizeChange("2026-10-25", -100));
        assertThat(serving.statements())
                .singleElement()
                .asString()
                .contains("'%Y-%m-%d', 'UTC'")
                .contains("JSONExtractUInt(\"json\", 'remove', 'size')");
    }
}
