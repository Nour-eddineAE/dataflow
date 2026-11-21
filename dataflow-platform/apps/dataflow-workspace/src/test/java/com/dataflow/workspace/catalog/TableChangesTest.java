package com.dataflow.workspace.catalog;

import com.dataflow.workspace.Drafts;
import com.dataflow.workspace.Workspace;
import com.dataflow.workspace.serving.LakeFunctions;
import com.dataflow.workspace.serving.RecordingClickHouse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TableChangesTest {

    private final RecordingClickHouse serving = new RecordingClickHouse()
            .answer(
                    sql -> sql.contains("count(*)"),
                    List.of("_commit_version", "_change_type", "count"),
                    List.of(
                            List.of(22, "insert", 31),
                            List.of(22, "update_postimage", 4),
                            List.of(22, "update_preimage", 4)));
    private final TableChanges changes = new TableChanges(serving, new LakeFunctions(Workspace.properties()));

    @Test
    void theChangeFeedBetweenTwoVersionsIsCountedAndListed() {
        LakeTable history =
                Catalog.table(Drafts.spec("earthquakes"), Catalog.HISTORY).orElseThrow();

        TableChanges.Changes result = changes.between(history, 22, 22);

        assertThat(result.counts())
                .extracting(TableChanges.Count::change)
                .containsExactly("insert", "update_postimage", "update_preimage");
        assertThat(result.counts().getFirst().rows()).isEqualTo(31);
        assertThat(serving.settings())
                .allSatisfy(applied -> assertThat(applied)
                        .containsExactlyInAnyOrderEntriesOf(Map.of(
                                "delta_lake_snapshot_start_version", "22", "delta_lake_snapshot_end_version", "22")));
        assertThat(serving.statements().get(1))
                .startsWith("select \"_change_type\" \"change\", \"_commit_version\" \"version\", *")
                .contains("deltaLake(lake, filename = 'history/earthquakes/')")
                .endsWith("limit 500");
    }

    @Test
    void onlyHistoryTablesAndShortRangesAreCompared() {
        LakeTable silver =
                Catalog.table(Drafts.spec("earthquakes"), Catalog.SILVER).orElseThrow();
        LakeTable history =
                Catalog.table(Drafts.spec("earthquakes"), Catalog.HISTORY).orElseThrow();

        assertThatThrownBy(() -> changes.between(silver, 1, 2))
                .hasMessageContaining("Only history tables keep a change feed");
        assertThatThrownBy(() -> changes.between(history, 5, 3)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> changes.between(history, 0, 60)).hasMessageContaining("at most 50 versions");
    }
}
