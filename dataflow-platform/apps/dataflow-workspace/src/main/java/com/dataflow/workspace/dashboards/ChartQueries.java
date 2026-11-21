package com.dataflow.workspace.dashboards;

import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.ResultQuery;
import org.jooq.Table;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.jooq.impl.DSL.count;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.function;
import static org.jooq.impl.DSL.greatest;
import static org.jooq.impl.DSL.inline;
import static org.jooq.impl.DSL.least;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.noCondition;
import static org.jooq.impl.DSL.select;
import static org.jooq.impl.DSL.sum;

final class ChartQueries {

    static final String X = "chart_x";
    static final String SERIES = "chart_series";
    static final String Y = "chart_y";
    static final int BUCKETS = 30;
    static final int MAX_BARS = 20;
    static final String EMPTY = "(empty)";
    private static final String SECONDS_PATTERN = "yyyy-MM-dd HH:mm:ss";
    private static final DateTimeFormatter SECONDS = DateTimeFormatter.ofPattern(SECONDS_PATTERN);
    private static final int SECONDS_LENGTH = SECONDS_PATTERN.length();
    private static final int DATE_LENGTH = "yyyy-MM-dd".length();
    private static final String HOUR = "hour";
    private static final String DAY = "day";
    private static final int HOURLY_DAYS = 3;
    private static final int DAILY_DAYS = 120;
    private static final int WEEKLY_DAYS = 1100;
    private static final Map<String, Duration> RANGES =
            Map.of("7d", Duration.ofDays(7), "30d", Duration.ofDays(30), "1y", Duration.ofDays(365));

    private ChartQueries() {}

    static Built build(Chart chart, Table<?> source, Window window) {
        Condition where = where(window);
        return switch (chart.kind()) {
            case TIME -> time(chart, source, where, window);
            case BARS ->
                new Built(
                        select(category(chart.x()).as(X), measure(chart).as(Y))
                                .from(source)
                                .where(where)
                                .groupBy(field(name(X)))
                                .orderBy(field(name(Y)).desc())
                                .limit(MAX_BARS),
                        null);
            case HISTOGRAM ->
                new Built(
                        select(bucket(chart).as(X), count().as(Y))
                                .from(source)
                                .where(where.and(field(name(chart.x())).isNotNull()))
                                .groupBy(field(name(X)))
                                .orderBy(field(name(X))),
                        null);
        };
    }

    private static Built time(Chart chart, Table<?> source, Condition where, Window window) {
        String grain = grain(chart.low(), chart.high(), window.span(), window.timeType());
        List<Field<?>> fields = new ArrayList<>();
        fields.add(function(truncation(grain), Object.class, field(name(chart.x())))
                .as(X));
        if (chart.split() != null) {
            fields.add(category(chart.split()).as(SERIES));
        }
        fields.add(measure(chart).as(Y));
        List<Field<?>> groups = new ArrayList<>(List.of(field(name(X))));
        if (chart.split() != null) {
            groups.add(field(name(SERIES)));
        }
        return new Built(
                select(fields)
                        .from(source)
                        .where(where.and(field(name(chart.x())).isNotNull()))
                        .groupBy(groups)
                        .orderBy(field(name(X))),
                grain);
    }

    private static Condition where(Window window) {
        Condition where = noCondition();
        Duration span = window.span();
        if (span != null && window.time() != null && window.latest() != null) {
            where = where.and(
                    field(name(window.time())).ge(inline(lowerBound(window.latest(), span, window.timeType()))));
        }
        for (Map.Entry<String, String> filter : window.filters().entrySet()) {
            where = where.and(category(filter.getKey()).eq(inline(filter.getValue())));
        }
        return where;
    }

    private static Field<String> category(String column) {
        return function("ifNull", String.class, function("toString", String.class, field(name(column))), inline(EMPTY));
    }

    private static Field<?> measure(Chart chart) {
        return chart.measure() == null ? count() : sum(field(name(chart.measure()), Double.class));
    }

    private static Field<Integer> bucket(Chart chart) {
        Field<Double> value = function("toFloat64", Double.class, field(name(chart.x())));
        Field<Integer> raw = function(
                "widthBucket",
                Integer.class,
                value,
                inline(Double.parseDouble(chart.low())),
                inline(Double.parseDouble(chart.high())),
                inline(BUCKETS));
        return least(greatest(raw, inline(1)), inline(BUCKETS));
    }

    static String grain(String low, String high, Duration window, String type) {
        boolean date = type != null && type.replace("Nullable(", "").startsWith("Date") && !type.contains("DateTime");
        Duration span = Duration.between(parse(low), parse(high));
        if (window != null && window.compareTo(span) < 0) {
            span = window;
        }
        long days = span.toDays();
        String grain = days <= HOURLY_DAYS ? HOUR : days <= DAILY_DAYS ? DAY : days <= WEEKLY_DAYS ? "week" : "month";
        return date && grain.equals(HOUR) ? DAY : grain;
    }

    private static String truncation(String grain) {
        return switch (grain) {
            case HOUR -> "toStartOfHour";
            case DAY -> "toDate";
            case "week" -> "toMonday";
            default -> "toStartOfMonth";
        };
    }

    static String lowerBound(String latest, Duration span, String type) {
        LocalDateTime bound = parse(latest).minus(span);
        boolean date = type != null && !type.contains("DateTime");
        return date ? bound.toLocalDate().toString() : bound.format(SECONDS);
    }

    static LocalDateTime parse(String value) {
        String text = value.strip().replace('T', ' ');
        if (text.length() == DATE_LENGTH) {
            return LocalDate.parse(text).atStartOfDay();
        }
        String seconds = text.length() > SECONDS_LENGTH ? text.substring(0, SECONDS_LENGTH) : text;
        return LocalDateTime.parse(seconds, SECONDS.withLocale(Locale.ROOT));
    }

    record Window(String time, String timeType, String latest, String range, Map<String, String> filters) {

        Window {
            if (range != null && !range.equals("all") && !RANGES.containsKey(range)) {
                throw new IllegalArgumentException("Unknown range '" + range + "'; use all, 7d, 30d or 1y");
            }
        }

        Duration span() {
            return range == null ? null : RANGES.get(range);
        }
    }

    record Built(ResultQuery<?> query, String grain) {}
}
