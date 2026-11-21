package com.dataflow.workspace.why;

import com.dataflow.spec.PipelineSpecLoader;
import com.dataflow.workspace.Workspace;
import com.dataflow.workspace.catalog.Catalog;
import com.dataflow.workspace.catalog.DeltaLog;
import com.dataflow.workspace.catalog.LakeIndex;
import com.dataflow.workspace.catalog.LakeTable;
import com.dataflow.workspace.catalog.LandingFiles;
import com.dataflow.workspace.catalog.TableService;
import com.dataflow.workspace.lineage.Lineage.Edge;
import com.dataflow.workspace.serving.LakeFunctions;
import com.dataflow.workspace.serving.RecordingClickHouse;
import com.dataflow.workspace.serving.SystemTables;
import com.dataflow.workspace.specs.Parsing;
import com.dataflow.workspace.specs.SpecStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WhyServiceTest {

    private static final String FACT = "a".repeat(64);
    private static final String FILE = "s3a://lake/landing/earthquakes/all_month.0123456789abcdef.csv";

    private final Parsing parsing = new Parsing();
    private final SpecStore specs = new SpecStore(Workspace.deployed(), new PipelineSpecLoader(), parsing);
    private final LakeFunctions lake = new LakeFunctions(Workspace.properties());
    private final RecordingClickHouse serving = new RecordingClickHouse();
    private final DeltaLog log = new DeltaLog(serving, lake, Workspace.properties());
    private final TableService tables = new TableService(
            specs,
            new LakeIndex(log, Clock.systemUTC()),
            serving,
            lake,
            log,
            new LandingFiles(serving, lake),
            new SystemTables(serving));
    private final WhyService why = new WhyService(specs, tables, new Facts(serving, lake));

    @AfterEach
    void stop() {
        parsing.stop();
    }

    @Test
    void explain_tracesAFactFromItsKeyToTheRawRow() {
        everyTableWritten();
        serving.answer(
                        sql -> sql.startsWith("select distinct \"factUniqueId\""),
                        List.of("factUniqueId"),
                        List.of(List.of(FACT)))
                .answer(
                        sql -> sql.startsWith("select * except (\"contentHash\")"),
                        List.of("factUniqueId", "eventId", "magnitude", "__START_AT", "__END_AT"),
                        List.of(Arrays.asList(FACT, "us6000u4qm", 4.8, "2026-10-24 23:55:56", null)))
                .answer(
                        sql -> sql.startsWith("select \"processedAt\", \"_run_id\""),
                        List.of("processedAt", "_run_id", "_source_file", "_ingested_at", "magnitude"),
                        List.of(List.of("2026-10-25 00:00:20", "run-1", FILE, "2026-10-25 00:00:12", 4.8)))
                .answer(
                        sql -> sql.contains("filename = 'bronze/earthquakes/'"),
                        List.of("id", "mag"),
                        List.of(List.of("us6000u4qm", "4.8")));

        Why why = this.why.explain("earthquakes", "us6000u4qm", "magnitude");

        assertThat(why.factUniqueId()).isEqualTo(FACT);
        assertThat(why.current()).containsEntry("magnitude", 4.8);
        assertThat(why.expression()).isEqualTo("mag");
        assertThat(why.rawNote()).isEqualTo("The row as it arrived in " + FILE);
        assertThat(why.raw().rows()).containsExactly(List.of("us6000u4qm", "4.8"));
        assertThat(why.upstream()).containsExactly(new Edge("bronze", "mag", "silver", "magnitude"));
        assertThat(why.downstream()).contains(new Edge("silver", "magnitude", "silver", "magnitudeBand"));
        assertThat(serving.statements())
                .anySatisfy(sql -> assertThat(sql)
                        .endsWith("where (\"_source_file\" = '" + FILE
                                + "' and toString(\"id\") = 'us6000u4qm') limit 5"));
    }

    @Test
    void explain_rejectsAColumnSilverDoesNotHave() {
        everyTableWritten();

        assertThatThrownBy(() -> why.explain("earthquakes", FACT, "magnitude) OR (1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("earthquakes silver has no column magnitude) OR (1");
    }

    private void everyTableWritten() {
        List<List<Object>> keys = Catalog.tables(specs.spec("earthquakes")).stream()
                .filter(LakeTable::delta)
                .<List<Object>>map(table -> List.of(table.key(), 3L))
                .distinct()
                .toList();
        serving.answer(sql -> sql.contains("format = 'One'"), List.of("key", "commits"), keys);
    }
}
