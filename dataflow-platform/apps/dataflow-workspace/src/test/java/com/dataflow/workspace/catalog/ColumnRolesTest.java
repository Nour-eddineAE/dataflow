package com.dataflow.workspace.catalog;

import com.dataflow.spec.PipelineSpec;
import com.dataflow.spec.SystemColumns;
import com.dataflow.workspace.Drafts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class ColumnRolesTest {

    private static final PipelineSpec EARTHQUAKES = Drafts.spec("earthquakes");

    @ParameterizedTest
    @ValueSource(strings = {"silver", "quarantine", "history", "current"})
    void silverShapedTablesShowHowSilverDerivesAColumn(String table) {
        assertThat(ColumnRoles.of(EARTHQUAKES, table).get("region").expression())
                .isEqualTo("trim(element_at(split(place, ', '), -1))");
    }

    @ParameterizedTest
    @ValueSource(strings = {"gold.regions", "events", "bronze"})
    void otherTablesNeverBorrowSilverDerivations(String table) {
        assertThat(ColumnRoles.of(EARTHQUAKES, table)).doesNotContainKey("region");
    }

    @Test
    void goldTablesStillDescribeSystemColumns() {
        assertThat(ColumnRoles.of(EARTHQUAKES, "gold.regions").get(SystemColumns.COMPUTED_AT).role())
                .isEqualTo("system");
    }
}
