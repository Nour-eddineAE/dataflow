package com.dataflow.workspace.catalog;

import com.dataflow.spec.PipelineSpecLoader;
import com.dataflow.workspace.Workspace;
import com.dataflow.workspace.serving.LakeFunctions;
import com.dataflow.workspace.serving.RecordingClickHouse;
import com.dataflow.workspace.serving.SystemTables;
import com.dataflow.workspace.specs.Parsing;
import com.dataflow.workspace.specs.SpecStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogViewsTest {

    private final Parsing parsing = new Parsing();
    private final SpecStore specs = new SpecStore(Workspace.deployed(), new PipelineSpecLoader(), parsing);
    private final LakeFunctions lake = new LakeFunctions(Workspace.properties());
    private final RecordingClickHouse serving = new RecordingClickHouse();
    private final CatalogViews views = new CatalogViews(
            specs,
            new LakeIndex(new DeltaLog(serving, lake, Workspace.properties()), Clock.systemUTC()),
            serving,
            lake,
            new SystemTables(serving));

    @AfterEach
    void stop() {
        parsing.stop();
    }

    @Test
    void syncDropsViewsTheirSpecNoLongerDeclaresAndKeepsTablesNotWrittenYet() {
        String silver = Catalog.table(specs.spec("earthquakes"), "silver").orElseThrow().key();
        serving.answer(sql -> sql.contains("format = 'One'"), List.of("key", "commits"), List.of(List.of(silver, 3L)))
                .answer(
                        sql -> sql.contains("'View'"),
                        List.of("database", "name"),
                        List.of(
                                List.of("earthquakes", "silver"),
                                List.of("earthquakes", "history"),
                                List.of("earthquakes", "gold_retired"),
                                List.of("taxi_trips", "reference_old_zones")));

        views.sync(false);

        assertThat(serving.statements())
                .filteredOn(sql -> sql.startsWith("drop view"))
                .containsExactly(
                        "drop view if exists \"earthquakes\".\"gold_retired\"",
                        "drop view if exists \"taxi_trips\".\"reference_old_zones\"");
        assertThat(serving.statements())
                .filteredOn(sql -> sql.contains("'View'"))
                .singleElement()
                .satisfies(sql -> assertThat(sql).contains("'earthquakes'").doesNotContain("'serving'"));
    }
}
