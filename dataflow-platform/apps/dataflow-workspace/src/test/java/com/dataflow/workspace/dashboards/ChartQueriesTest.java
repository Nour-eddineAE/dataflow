package com.dataflow.workspace.dashboards;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static com.dataflow.workspace.serving.RecordingClickHouse.render;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.table;

class ChartQueriesTest {

    private static final String TIME = "Nullable(DateTime64(6))";

    @Test
    void time_bucketsByDayOverAMonthAndSplitsBySeries() {
        Chart chart = new Chart(
                "time",
                "Rows over time",
                ChartKind.TIME,
                "occurredAt",
                null,
                "magnitudeBand",
                "2026-09-25 00:00:01.000000",
                "2026-10-25 12:00:00.000000");

        ChartQueries.Built built = ChartQueries.build(
                chart,
                table(name("earthquakes", "current")),
                new ChartQueries.Window("occurredAt", TIME, "2026-10-25 12:00:00.000000", "all", Map.of()));

        assertThat(built.grain()).isEqualTo("day");
        assertThat(render(built.query()))
                .isEqualTo("select toDate(\"occurredAt\") \"chart_x\", "
                        + "ifNull(toString(\"magnitudeBand\"), '(empty)') \"chart_series\", count(*) \"chart_y\" "
                        + "from \"earthquakes\".\"current\" where \"occurredAt\" is not null "
                        + "group by \"chart_x\", \"chart_series\" order by \"chart_x\"");
    }

    @Test
    void range_andFilters_narrowEveryChartOfTheTable() {
        Chart chart = new Chart("bars-region", "Rows by region", ChartKind.BARS, "region", null, null, null, null);

        ChartQueries.Built built = ChartQueries.build(
                chart,
                table(name("earthquakes", "current")),
                new ChartQueries.Window(
                        "occurredAt", TIME, "2026-10-25 12:00:00.000000", "7d", Map.of("magnitudeType", "ml' OR 1=1")));

        assertThat(render(built.query()))
                .contains("where (\"occurredAt\" >= '2026-10-18 12:00:00' "
                        + "and ifNull(toString(\"magnitudeType\"), '(empty)') = 'ml'' OR 1=1')")
                .endsWith("order by \"chart_y\" desc limit 20");
    }

    @Test
    void histogram_clampsValuesIntoThirtyBuckets() {
        Chart chart = new Chart(
                "histogram-magnitude", "magnitude", ChartKind.HISTOGRAM, "magnitude", null, null, "-1.42", "7.7");

        String sql = render(ChartQueries.build(
                        chart,
                        table(name("earthquakes", "current")),
                        new ChartQueries.Window(null, null, null, "all", Map.of()))
                .query());

        assertThat(sql)
                .startsWith("select least(greatest(widthBucket(toFloat64(\"magnitude\"), -1.42E0, 7.7E0, 30), 1), 30)");
    }

    @Test
    void grain_followsTheSpanAndTheRange() {
        assertThat(ChartQueries.grain("2024-01-01", "2026-01-01", null, "Date")).isEqualTo("week");
        assertThat(ChartQueries.grain("2024-01-01", "2026-01-01", Duration.ofDays(7), "Date"))
                .isEqualTo("day");
        assertThat(ChartQueries.grain("2026-10-24 00:00:00", "2026-10-25 00:00:00", null, TIME))
                .isEqualTo("hour");
        assertThat(ChartQueries.grain("2020-01-01", "2026-01-01", null, "Date")).isEqualTo("month");
    }

    @Test
    void unknownRange_isRefused() {
        assertThatThrownBy(() -> new ChartQueries.Window(null, null, null, "2w", Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
