package com.dataflow.workspace.catalog;

import com.dataflow.workspace.Workspace;
import com.dataflow.workspace.serving.LakeFunctions;
import com.dataflow.workspace.serving.RecordingClickHouse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jooq.impl.DSL.selectFrom;

class QuarantineTest {

    @Test
    void reasons_countsRowsPerBrokenRule() {
        RecordingClickHouse serving = new RecordingClickHouse()
                .answer(
                        sql -> sql.contains("arrayJoin"),
                        List.of("rule", "rows"),
                        List.of(List.of("plausible_magnitude", 12L), List.of("has_magnitude", 3L)));

        List<RuleCount> reasons = new Quarantine(serving)
                .reasons(selectFrom(new LakeFunctions(Workspace.properties()).delta("quarantine/earthquakes/")));

        assertThat(reasons)
                .containsExactly(new RuleCount("plausible_magnitude", 12), new RuleCount("has_magnitude", 3));
        assertThat(serving.statements())
                .singleElement()
                .asString()
                .startsWith("select arrayJoin(\"source\".\"failedExpectations\") \"rule\", count(*) \"rows\"")
                .endsWith("group by \"rule\" order by \"rows\" desc");
    }
}
