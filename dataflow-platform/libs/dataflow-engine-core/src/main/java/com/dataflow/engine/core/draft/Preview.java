package com.dataflow.engine.core.draft;

import java.util.List;

public record Preview(
        String dataset,
        Sample.Origin origin,
        long rows,
        List<Tabular.Field> rawColumns,
        Tabular silver,
        List<Rule> rules,
        Tabular quarantine,
        List<Gold> gold) {

    public record Rule(String name, String action, long failing, long evaluated) {}

    public record Gold(String name, Long rows, Tabular sample, String error) {}
}
