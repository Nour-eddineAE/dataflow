package com.dataflow.workspace.impact;

import com.dataflow.spec.PipelineSpec;
import com.dataflow.workspace.Drafts;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SpecImpactTest {

    private final PipelineSpec earthquakes = Drafts.spec("earthquakes");

    @Test
    void compare_findsNothingForTheSameSpec() {
        assertThat(SpecImpact.compare(earthquakes, earthquakes)).isEmpty();
    }

    @Test
    void compare_callsAKeyChangeRisky() {
        List<Change> changes = SpecImpact.compare(
                earthquakes, Drafts.edited("earthquakes", "keys: [eventId]", "keys: [eventId, network]"));

        assertThat(changes).singleElement().satisfies(change -> {
            assertThat(change.severity()).isEqualTo(Severity.RISKY);
            assertThat(change.tables()).contains("history");
        });
    }

    @Test
    void compare_saysANewDropRuleChangesData() {
        List<Change> changes = SpecImpact.compare(
                earthquakes,
                Drafts.edited(
                        "earthquakes",
                        "    - { name: reviewed,",
                        "    - { name: has_place, check: \"place IS NOT NULL\", on-failure: drop }\n"
                                + "    - { name: reviewed,"));

        assertThat(changes).singleElement().satisfies(change -> {
            assertThat(change.severity()).isEqualTo(Severity.DATA);
            assertThat(change.title()).contains("has_place", "drop");
            assertThat(change.tables()).containsExactly("silver", "quarantine");
        });
    }

    @Test
    void compare_ordersTheRiskiestChangeFirst() {
        PipelineSpec draft = Drafts.edited(
                "earthquakes",
                "type: double,                    from: depth",
                "type: float,                     from: depth");

        assertThat(SpecImpact.compare(earthquakes, draft))
                .extracting(Change::severity)
                .first()
                .isEqualTo(Severity.RISKY);
    }

    @Test
    void compare_treatsANewDerivedColumnAsSafe() {
        PipelineSpec draft = Drafts.edited(
                "earthquakes",
                "  expectations:\n",
                "    - { name: deep, type: boolean, from: \"depthKm > 70\" }\n  expectations:\n");

        assertThat(SpecImpact.compare(earthquakes, draft))
                .extracting(Change::severity)
                .containsExactly(Severity.SAFE);
    }

    @Test
    void compare_describesANewDataset() {
        assertThat(SpecImpact.compare(null, earthquakes))
                .singleElement()
                .satisfies(change -> assertThat(change.title()).isEqualTo("New dataset earthquakes"));
    }
}
