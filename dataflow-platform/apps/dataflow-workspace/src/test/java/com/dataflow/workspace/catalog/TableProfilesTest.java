package com.dataflow.workspace.catalog;

import com.dataflow.workspace.Workspace;
import com.dataflow.workspace.serving.LakeFunctions;
import com.dataflow.workspace.serving.QueryResult;
import com.dataflow.workspace.serving.RecordingClickHouse;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jooq.impl.DSL.selectFrom;

class TableProfilesTest {

    private static final List<QueryResult.Column> COLUMNS = List.of(
            new QueryResult.Column("region", "Nullable(String)"),
            new QueryResult.Column("magnitude", "Nullable(Float64)"),
            new QueryResult.Column("tags", "Array(String)"));

    private final RecordingClickHouse serving = new RecordingClickHouse()
            .answer(statement -> statement.endsWith("limit 0"), new QueryResult(COLUMNS, List.of(), false, 1, 0, 0))
            .answer(
                    statement -> statement.contains("uniq"),
                    new QueryResult(
                            List.of(
                                    column("rows"),
                                    column("nulls_0"),
                                    column("distinct_0"),
                                    column("min_0"),
                                    column("max_0"),
                                    column("nulls_1"),
                                    column("distinct_1"),
                                    column("min_1"),
                                    column("max_1"),
                                    column("nulls_2")),
                            List.of(Arrays.asList(10583, 12, 287, "Alaska", "Wyoming", 0, 640, "-1.2", "7.1", 3)),
                            false,
                            40,
                            10583,
                            1_900_000));
    private final LakeFunctions lake = new LakeFunctions(Workspace.properties());

    private static QueryResult.Column column(String name) {
        return new QueryResult.Column(name, "UInt64");
    }

    @Test
    void profile_measuresEveryColumnInOneScan() {
        TableProfile profile = new TableProfiles(serving).profile(selectFrom(lake.delta("history/earthquakes/")));

        assertThat(profile.rows()).isEqualTo(10583);
        assertThat(profile.columns())
                .containsExactly(
                        new ColumnProfile("region", "Nullable(String)", 12, 287L, "Alaska", "Wyoming"),
                        new ColumnProfile("magnitude", "Nullable(Float64)", 0, 640L, "-1.2", "7.1"),
                        new ColumnProfile("tags", "Array(String)", 3, null, null, null));
        assertThat(serving.statements()).hasSize(2);
        assertThat(serving.statements().getLast())
                .contains("count() filter (where \"source\".\"tags\" is null) \"nulls_2\"")
                .contains("uniq(\"source\".\"region\") \"distinct_0\"")
                .contains("substringUTF8(toString(min(\"source\".\"magnitude\")), 1, 64) \"min_1\"")
                .doesNotContain("\"distinct_2\"");
    }
}
