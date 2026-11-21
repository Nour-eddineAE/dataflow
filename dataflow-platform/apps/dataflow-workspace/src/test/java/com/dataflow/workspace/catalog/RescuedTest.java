package com.dataflow.workspace.catalog;

import com.dataflow.workspace.Drafts;
import com.dataflow.workspace.Workspace;
import com.dataflow.workspace.serving.LakeFunctions;
import com.dataflow.workspace.serving.QueryResult;
import com.dataflow.workspace.serving.RecordingClickHouse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class RescuedTest {

    @Test
    void unknownKeysAreSuggestedAsColumnsAndKnownOnesAreCastFailures() {
        RecordingClickHouse serving = new RecordingClickHouse()
                .answer(
                        sql -> sql.endsWith("limit 0"),
                        new QueryResult(
                                List.of(
                                        new QueryResult.Column("mag", "Nullable(String)"),
                                        new QueryResult.Column("_rescued_data", "Nullable(String)")),
                                List.of(),
                                false,
                                1,
                                0,
                                0))
                .answer(
                        sql -> sql.contains("arrayJoin"),
                        List.of("key", "rows", "sample"),
                        List.of(List.of("tsunami_flag", 12, "\"1\""), List.of("Mag", 3, "\"n/a\"")));
        Rescued rescued = new Rescued(serving);

        List<Rescued.Key> keys = rescued.keys(Catalog.source(
                new LakeFunctions(Workspace.properties()),
                Catalog.table(Drafts.spec("earthquakes"), Catalog.BRONZE).orElseThrow()));

        assertThat(keys)
                .extracting(Rescued.Key::key, Rescued.Key::known)
                .containsExactly(tuple("tsunami_flag", false), tuple("Mag", true));
        assertThat(keys.getFirst().suggestion())
                .isEqualTo("- { name: tsunamiFlag, type: string, from: \"get_json_object(_rescued_data,"
                        + " '$.tsunami_flag')\" }");
        assertThat(keys.get(1).suggestion()).isNull();
        assertThat(serving.statements().get(1))
                .contains("arrayJoin(JSONExtractKeys(assumeNotNull(\"source\".\"_rescued_data\")))");
    }
}
