package com.dataflow.workspace.dashboards;

import com.dataflow.spec.PipelineSpec;
import com.dataflow.spec.SystemColumns;
import com.dataflow.workspace.catalog.ColumnProfile;
import com.dataflow.workspace.catalog.TableProfile;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

final class ChartPlanner {

    static final int MAX_CATEGORY = 50;
    static final int MAX_SERIES = 8;
    private static final int MAX_BAR_CHARTS = 3;
    private static final int MAX_HISTOGRAMS = 4;
    private static final double MOSTLY_UNIQUE = 0.9;
    private static final String ROWS = "Rows";
    private static final Pattern TIME = Pattern.compile("^(Nullable\\()?(DateTime|Date)");
    private static final Pattern NUMBER = Pattern.compile("^(Nullable\\()?(U?Int|Float|Decimal)");
    private static final Pattern COORDINATE = Pattern.compile("(?i)(lat|lon|lng|latitude|longitude)");
    private static final Pattern TEXT =
            Pattern.compile("^(Nullable\\()?(LowCardinality\\()?(Nullable\\()?(String|FixedString|Enum|Bool)");

    private ChartPlanner() {}

    static Plan plan(PipelineSpec spec, boolean aggregated, TableProfile profile) {
        List<ColumnProfile> columns = profile.columns().stream()
                .filter(column -> !excluded(spec, aggregated, profile.rows(), column))
                .toList();
        Optional<ColumnProfile> time = time(columns, spec.history().sequenceBy());
        List<ColumnProfile> categories = categories(columns);
        List<ColumnProfile> measures = measures(columns);
        String weight = aggregated && !measures.isEmpty() ? measures.getFirst().name() : null;
        String rows = weight == null ? ROWS : weight;

        List<Chart> charts = new ArrayList<>();
        time.ifPresent(column -> charts.add(timeChart(column, categories, rows, weight)));
        barred(columns, categories, weight).stream()
                .limit(MAX_BAR_CHARTS)
                .forEach(column -> charts.add(barChart(column, rows, weight)));
        if (!aggregated) {
            measures.stream()
                    .filter(column -> !Objects.equals(column.min(), column.max()))
                    .filter(column -> !COORDINATE.matcher(column.name()).matches())
                    .limit(MAX_HISTOGRAMS)
                    .forEach(column -> charts.add(histogram(column)));
        }
        return new Plan(
                time.map(ColumnProfile::name).orElse(null),
                time.map(ColumnProfile::max).orElse(null),
                categories.stream().map(ColumnProfile::name).toList(),
                charts);
    }

    private static Optional<ColumnProfile> time(List<ColumnProfile> columns, String sequenceBy) {
        return columns.stream()
                .filter(column -> TIME.matcher(column.type()).find() && column.max() != null)
                .min(Comparator.comparing((ColumnProfile column) -> column.name().equals(sequenceBy)));
    }

    private static List<ColumnProfile> categories(List<ColumnProfile> columns) {
        return columns.stream()
                .filter(column -> TEXT.matcher(column.type()).find() && column.distinct() != null)
                .filter(column -> column.distinct() >= 2 && column.distinct() <= MAX_CATEGORY)
                .toList();
    }

    private static List<ColumnProfile> measures(List<ColumnProfile> columns) {
        return columns.stream()
                .filter(column -> NUMBER.matcher(column.type()).find() && column.min() != null && column.max() != null)
                .filter(column -> column.distinct() == null || column.distinct() > 1)
                .toList();
    }

    private static List<ColumnProfile> barred(List<ColumnProfile> columns, List<ColumnProfile> categories, String weight) {
        if (!categories.isEmpty() || weight == null) {
            return categories;
        }
        return columns.stream()
                .filter(column -> TEXT.matcher(column.type()).find() && column.distinct() != null && column.distinct() >= 2)
                .limit(1)
                .toList();
    }

    private static Chart timeChart(ColumnProfile column, List<ColumnProfile> categories, String rows, String weight) {
        String split = categories.stream()
                .filter(category -> category.distinct() + (category.nulls() > 0 ? 1 : 0) <= MAX_SERIES)
                .map(ColumnProfile::name)
                .findFirst()
                .orElse(null);
        return new Chart(
                "time",
                rows + " over time" + (split == null ? "" : ", by " + split),
                ChartKind.TIME,
                column.name(),
                weight,
                split,
                column.min(),
                column.max());
    }

    private static Chart barChart(ColumnProfile column, String rows, String weight) {
        String top = column.distinct() > MAX_CATEGORY ? ", top " + ChartQueries.MAX_BARS : "";
        return new Chart(
                "bars-" + column.name(), rows + " by " + column.name() + top, ChartKind.BARS, column.name(), weight,
                null, null, null);
    }

    private static Chart histogram(ColumnProfile column) {
        return new Chart(
                "histogram-" + column.name(), column.name(), ChartKind.HISTOGRAM, column.name(), null, null,
                column.min(), column.max());
    }

    private static boolean excluded(PipelineSpec spec, boolean aggregated, long rows, ColumnProfile column) {
        String name = column.name();
        if (SystemColumns.reserved(name)) {
            return true;
        }
        if (!aggregated && spec.history().keys().contains(name)) {
            return true;
        }
        boolean idLike = name.toLowerCase(Locale.ROOT).endsWith("id");
        return idLike && column.distinct() != null && rows > 0 && column.distinct() >= rows * MOSTLY_UNIQUE;
    }

    record Plan(String time, String latest, List<String> filters, List<Chart> charts) {}
}
