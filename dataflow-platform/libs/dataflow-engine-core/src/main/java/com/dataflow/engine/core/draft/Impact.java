package com.dataflow.engine.core.draft;

import java.util.List;

public record Impact(
        String dataset,
        Sample.Origin origin,
        long rows,
        List<Rule> rules,
        List<ColumnChange> columns,
        Keys keys,
        long changedFacts,
        List<Gold> gold) {

    public enum State {
        ADDED,
        REMOVED,
        KEPT
    }

    public record Rule(String name, String actionBefore, String actionAfter, Long failingBefore, Long failingAfter) {}

    public record ColumnChange(String name, State state, long changedRows) {}

    public record Keys(boolean changed, long factsBefore, long factsAfter, long merged, long split) {}

    public record Gold(String name, Long rowsBefore, Long rowsAfter, String error) {}
}
