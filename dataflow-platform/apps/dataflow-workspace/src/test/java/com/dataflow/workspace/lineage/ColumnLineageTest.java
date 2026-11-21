package com.dataflow.workspace.lineage;

import com.dataflow.workspace.Drafts;
import com.dataflow.workspace.lineage.Lineage.Edge;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ColumnLineageTest {

    private static final String STEPPED = """
              - name: strong
                from: current
                steps:
                  - filter: "magnitude >= 5"
                  - select: [region, "magnitude AS peak", depthKm]
                  - rename: { depthKm: depth }
                  - drop: [depth]
                  - union: [regions]
                  - aggregate: { by: [region], measures: { strongest: "max(peak)" } }
              - name: joined
                from: current
                steps:
                  - join: { with: regions, type: left, on: { region: region }, prefix: region }
                  - derive: { broken: "nothere + 1" }
            """;

    private static final String JAVA = """
              - name: region_energy
                transformation: region_energy@1
                inputs: { events: current }
            """;

    @Test
    void of_tracesARawColumnToTheGoldAggregateAndItsServedTable() {
        Lineage lineage = ColumnLineage.of(Drafts.spec("earthquakes"));

        assertThat(lineage.edges())
                .contains(
                        new Edge("bronze", "mag", "silver", "magnitude"),
                        new Edge("silver", "magnitude", "silver", "magnitudeBand"),
                        new Edge("current", "magnitude", "gold.regions", "maxMagnitude"),
                        new Edge("gold.regions", "maxMagnitude", "serving.earthquakes_regions", "maxMagnitude"));
        assertThat(lineage.warnings()).isEmpty();
    }

    @Test
    void of_resolvesAliasedReferenceColumns() {
        Lineage lineage = ColumnLineage.of(Drafts.spec("taxi_trips"));

        assertThat(lineage.edges())
                .contains(new Edge("reference.taxi_zones", "Zone", "gold.daily_zone_revenue", "zone"));
        assertThat(lineage.tables()).extracting(Lineage.Table::id).contains("reference.taxi_zones");
    }

    @Test
    void of_warnsAboutAGoldColumnCurrentDoesNotHave() {
        Lineage lineage = ColumnLineage.of(
                Drafts.edited("earthquakes", "coalesce(region, 'Unknown')", "coalesce(regoin, 'Unknown')"));

        assertThat(lineage.warnings()).containsExactly("gold regions reads 'regoin', which current does not have");
    }

    @Test
    void of_tracesAPipelineWithoutSourceThroughItsInputsAndTables() {
        Lineage lineage = ColumnLineage.of(Drafts.spec("regions"));

        assertThat(lineage.edges())
                .contains(
                        new Edge("input.quakes", "region", "gold.strong", "region"),
                        new Edge("input.quakes", "magnitude", "gold.strong", "peak"),
                        new Edge("gold.strong", "peak", "gold.top", "peak"));
        assertThat(lineage.tables()).extracting(Lineage.Table::id).doesNotContain("bronze", "silver", "current");
        assertThat(lineage.tables()).extracting(Lineage.Table::id).contains("gold.strong", "gold.top");
        assertThat(lineage.warnings()).isEmpty();
    }

    @Test
    void of_tracesStepBuiltTablesThroughJoinPrefixesAndUnstoredTables() {
        Lineage lineage = ColumnLineage.of(Drafts.spec("zone_hours"));

        assertThat(lineage.edges())
                .contains(
                        new Edge("input.trips", "pickupTime", "gold.zoned_trips", "hour"),
                        new Edge("input.zones", "Borough", "gold.zoned_trips", "zoneBorough"),
                        new Edge("gold.zoned_trips", "zoneBorough", "gold.borough_hours", "zoneBorough"),
                        new Edge("input.trips", "fareAmount", "gold.zoned_trips", "fareAmount"),
                        new Edge("gold.zoned_trips", "fareAmount", "gold.borough_hours", "revenue"),
                        new Edge("gold.borough_hours", "revenue", "serving.zone_hours", "revenue"));
        assertThat(lineage.warnings()).isEmpty();
    }

    @Test
    void of_followsEveryStepOperation() {
        Lineage lineage = ColumnLineage.of(Drafts.edited("earthquakes", "\nserve:\n", STEPPED + "\nserve:\n"));

        assertThat(lineage.edges())
                .contains(
                        new Edge("current", "region", "gold.strong", "region"),
                        new Edge("gold.regions", "region", "gold.strong", "region"),
                        new Edge("current", "magnitude", "gold.strong", "strongest"),
                        new Edge("gold.regions", "events", "gold.joined", "regionEvents"),
                        new Edge("current", "eventId", "gold.joined", "eventId"));
        assertThat(lineage.edges())
                .noneMatch(edge -> edge.toTable().equals("gold.strong") && edge.toColumn().equals("depth"));
        assertThat(lineage.warnings()).containsExactly("gold joined step 2 reads 'nothere', which is not a column there");
    }

    @Test
    void of_doesNotWarnAboutTheUntracedColumnsOfAJavaGoldTable() {
        Lineage lineage = ColumnLineage.of(Drafts.edited("earthquakes", "\nserve:\n", JAVA + "\nserve:\n"));

        assertThat(lineage.edges()).noneMatch(edge -> edge.toTable().equals("gold.region_energy"));
        assertThat(lineage.warnings()).isEmpty();
    }
}
