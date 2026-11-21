package com.dataflow.workspace.dashboards;

import com.dataflow.spec.BoardSpec;
import com.dataflow.spec.PipelineSpec;
import com.dataflow.workspace.Drafts;
import com.dataflow.workspace.catalog.ColumnProfile;
import com.dataflow.workspace.catalog.TableProfile;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChartPlannerTest {

    private static final String TIME = "Nullable(DateTime64(6))";
    private static final String TEXT = "Nullable(String)";
    private static final String NUMBER = "Nullable(Float64)";

    private final PipelineSpec spec = Drafts.spec("earthquakes");

    private static Chart chart(String id) {
        return new Chart(id, id, ChartKind.BARS, "x", null, null, null, null);
    }

    @Test
    void current_chartsTimeCategoriesAndMeasuresButNeverKeys() {
        TableProfile profile = new TableProfile(
                10_000,
                List.of(
                        new ColumnProfile("eventId", TEXT, 0, 10_000L, "ak1", "us9"),
                        new ColumnProfile(
                                "occurredAt",
                                TIME,
                                0,
                                9_990L,
                                "2026-09-25 00:00:01.000000",
                                "2026-10-25 12:00:00.000000"),
                        new ColumnProfile(
                                "updatedAt",
                                TIME,
                                0,
                                9_990L,
                                "2026-09-25 00:10:00.000000",
                                "2026-10-25 12:30:00.000000"),
                        new ColumnProfile("latitude", NUMBER, 0, 9_000L, "-60.1", "71.2"),
                        new ColumnProfile("magnitude", NUMBER, 0, 500L, "-1.42", "7.7"),
                        new ColumnProfile("magnitudeType", TEXT, 0, 9L, "mb", "mww"),
                        new ColumnProfile("magnitudeBand", TEXT, 0, 5L, "1 micro (<2)", "5 strong (6+)"),
                        new ColumnProfile("region", TEXT, 0, 2_000L, "Alaska", "Utah"),
                        new ColumnProfile("factUniqueId", TEXT, 0, 10_000L, "00", "ff"),
                        new ColumnProfile(
                                "__START_AT",
                                TIME,
                                0,
                                9_000L,
                                "2026-09-25 00:00:01.000000",
                                "2026-10-25 12:00:00.000000")),
                0,
                0);

        ChartPlanner.Plan plan = ChartPlanner.plan(spec, false, profile);

        assertThat(plan.time()).isEqualTo("occurredAt");
        assertThat(plan.filters()).containsExactly("magnitudeType", "magnitudeBand");
        assertThat(plan.charts())
                .extracting(Chart::id)
                .containsExactly("time", "bars-magnitudeType", "bars-magnitudeBand", "histogram-magnitude");
        assertThat(plan.charts().getFirst()).satisfies(chart -> {
            assertThat(chart.kind()).isEqualTo(ChartKind.TIME);
            assertThat(chart.measure()).isNull();
            assertThat(chart.split()).isEqualTo("magnitudeBand");
            assertThat(chart.title()).isEqualTo("Rows over time, by magnitudeBand");
        });
    }

    @Test
    void gold_sumsItsFirstMeasureAndDrawsNoDistribution() {
        TableProfile profile = new TableProfile(
                120,
                List.of(
                        new ColumnProfile("day", "Nullable(Date32)", 0, 30L, "2026-09-25", "2026-10-25"),
                        new ColumnProfile("eventType", TEXT, 0, 3L, "earthquake", "quarry blast"),
                        new ColumnProfile("events", "Nullable(Int64)", 0, 80L, "1", "412"),
                        new ColumnProfile(
                                "computedAt", TIME, 0, 1L, "2026-10-25 12:00:00.000000", "2026-10-25 12:00:00.000000")),
                0,
                0);

        ChartPlanner.Plan plan = ChartPlanner.plan(spec, true, profile);

        assertThat(plan.time()).isEqualTo("day");
        assertThat(plan.charts()).extracting(Chart::id).containsExactly("time", "bars-eventType");
        assertThat(plan.charts())
                .allSatisfy(chart -> assertThat(chart.measure()).isEqualTo("events"));
        assertThat(plan.charts().get(1).title()).isEqualTo("events by eventType");
    }

    @Test
    void aGoldTableWithManyValues_showsItsTopTwenty() {
        TableProfile profile = new TableProfile(
                142,
                List.of(
                        new ColumnProfile("region", TEXT, 0, 142L, "Alaska", "Utah"),
                        new ColumnProfile("events", "Nullable(Int64)", 0, 90L, "1", "2400")),
                0,
                0);

        ChartPlanner.Plan plan = ChartPlanner.plan(spec, true, profile);

        assertThat(plan.charts()).singleElement().satisfies(chart -> {
            assertThat(chart.id()).isEqualTo("bars-region");
            assertThat(chart.title()).isEqualTo("events by region, top 20");
        });
        assertThat(plan.filters()).isEmpty();
    }

    @Test
    void overrides_hideChartsAndPutTheChosenOnesFirst() {
        List<Chart> charts =
                List.of(chart("time"), chart("bars-status"), chart("histogram-depthKm"), chart("bars-network"));

        List<Chart> arranged = Dashboards.arrange(
                "current",
                charts,
                new BoardSpec(
                        List.of("current/histogram-depthKm", "gold.x/time"),
                        List.of("current/bars-network", "current/missing")));

        assertThat(arranged).extracting(Chart::id).containsExactly("bars-network", "time", "bars-status");
    }
}
