package com.dataflow.workspace.serving;

import org.jooq.Condition;
import org.jooq.Field;

import java.util.stream.Stream;

import static org.jooq.impl.DSL.condition;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.function;
import static org.jooq.impl.DSL.inline;
import static org.jooq.impl.DSL.name;

public final class ClickHouseDsl {

    private ClickHouseDsl() {}

    public static <T> Field<T> column(String name, Class<T> type) {
        return field(name(name), type);
    }

    public static <T> Field<T> argMax(Field<T> value, Field<?> by) {
        return function("argMax", value.getType(), value, by);
    }

    public static <T> Field<T> argMaxIf(Field<T> value, Field<?> by, Condition when) {
        return function("argMaxIf", value.getType(), value, by, field(when));
    }

    public static <T> Field<T> minIf(Field<T> value, Condition when) {
        return function("minIf", value.getType(), value, field(when));
    }

    public static Field<Long> millisBetween(Field<?> from, Field<?> to) {
        return function("dateDiff", Long.class, inline("millisecond"), from, to);
    }

    public static Field<String> text(Field<?> value) {
        return function("toString", String.class, value);
    }

    public static Field<String> isoUtc(Field<?> time) {
        return function("formatDateTime", String.class, time, inline("%Y-%m-%dT%H:%i:%SZ"), inline("UTC"));
    }

    public static Field<String> dayUtc(Field<?> time) {
        return function("formatDateTime", String.class, time, inline("%Y-%m-%d"), inline("UTC"));
    }

    public static Field<Long> uniq(Field<?> value) {
        return function("uniq", Long.class, value);
    }

    public static Field<String> preview(Field<?> value, int length) {
        return function("substringUTF8", String.class, text(value), inline(1), inline(length));
    }

    public static Field<String> firstMatch(Field<String> value, String pattern) {
        return function("extract", String.class, value, inline(pattern));
    }

    public static Field<Long> unsigned(Field<?> value) {
        return function("toUInt64", Long.class, value);
    }

    public static Field<Long> unsignedOrZero(Field<String> value) {
        return function("toUInt64OrZero", Long.class, value);
    }

    public static Field<String> fromEpochMillis(Field<Long> millis) {
        return function("fromUnixTimestamp64Milli", String.class, millis);
    }

    public static Field<String> jsonText(Field<String> json, String... path) {
        return function("JSONExtractString", String.class, arguments(json, path));
    }

    public static Field<Long> jsonLong(Field<String> json, String... path) {
        return function("JSONExtractInt", Long.class, arguments(json, path));
    }

    public static Field<Long> jsonUnsigned(Field<String> json, String... path) {
        return function("JSONExtractUInt", Long.class, arguments(json, path));
    }

    public static Field<String> jsonRaw(Field<String> json, String... path) {
        return function("JSONExtractRaw", String.class, arguments(json, path));
    }

    public static Condition jsonHas(Field<String> json, String key) {
        return condition(function("JSONHas", Boolean.class, json, inline(key)));
    }

    private static Field<?>[] arguments(Field<String> json, String[] path) {
        return Stream.concat(Stream.of(json), Stream.of(path).map(step -> inline(step)))
                .toArray(Field<?>[]::new);
    }
}
