package com.dataflow.workspace.queries;

import com.dataflow.workspace.access.Caller;
import com.dataflow.workspace.serving.QueryResult;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record QueryAlert(
        UUID queryId,
        String owner,
        List<String> groups,
        boolean admin,
        int everyMinutes,
        Comparison comparison,
        BigDecimal threshold,
        State state,
        Instant checkedAt,
        Instant since,
        String value,
        String error) {

    public boolean due(Instant now) {
        return checkedAt == null || !checkedAt.plus(Duration.ofMinutes(everyMinutes)).isAfter(now);
    }

    public Caller caller() {
        return new Caller(owner, groups, admin);
    }

    public QueryAlert checked(State next, Instant at, String newValue, String newError) {
        Instant changed = next == state ? since : at;
        return new QueryAlert(
                queryId, owner, groups, admin, everyMinutes, comparison, threshold, next, at, changed, newValue, newError);
    }

    public enum State {
        UNKNOWN,
        OK,
        FIRING,
        ERROR
    }

    public enum Comparison {
        ANY_ROWS,
        NO_ROWS,
        ABOVE,
        BELOW,
        EQUAL;

        public boolean compares() {
            return this != ANY_ROWS && this != NO_ROWS;
        }

        public boolean holds(QueryResult result, BigDecimal threshold) {
            return switch (this) {
                case ANY_ROWS -> result.rowCount() > 0;
                case NO_ROWS -> result.rowCount() == 0;
                case ABOVE -> first(result).compareTo(threshold) > 0;
                case BELOW -> first(result).compareTo(threshold) < 0;
                case EQUAL -> first(result).compareTo(threshold) == 0;
            };
        }

        public String value(QueryResult result) {
            return compares() ? first(result).toPlainString() : String.valueOf(result.rowCount());
        }

        private static BigDecimal first(QueryResult result) {
            if (result.rows().isEmpty() || result.rows().getFirst().isEmpty()) {
                throw new IllegalArgumentException("The query returned no value to compare");
            }
            Object value = result.rows().getFirst().getFirst();
            try {
                return new BigDecimal(String.valueOf(value));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("The first value is not a number: " + value, e);
            }
        }
    }
}
