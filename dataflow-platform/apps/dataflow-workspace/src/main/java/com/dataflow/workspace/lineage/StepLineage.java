package com.dataflow.workspace.lineage;

import com.dataflow.spec.AggregateSpec;
import com.dataflow.spec.GoldSpec;
import com.dataflow.spec.GoldStepSpec;
import com.dataflow.spec.JoinSpec;
import com.dataflow.workspace.lineage.SqlReferences.Output;
import com.dataflow.workspace.lineage.SqlReferences.Reference;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

final class StepLineage {

    private final String gold;
    private final Tables tables;
    private final List<String> warnings;
    private Map<String, Set<Origin>> columns = new LinkedHashMap<>();
    private List<Open> open = new ArrayList<>();
    private int step;

    private StepLineage(String gold, Tables tables, List<String> warnings) {
        this.gold = gold;
        this.tables = tables;
        this.warnings = warnings;
    }

    static Result trace(GoldSpec gold, Tables tables, List<String> warnings) {
        StepLineage lineage = new StepLineage(gold.name(), tables, warnings);
        lineage.start(gold.from());
        for (GoldStepSpec spec : gold.steps()) {
            lineage.step++;
            lineage.apply(spec);
        }
        return new Result(lineage.columns, List.copyOf(lineage.open));
    }

    static List<Origin> origins(List<Open> open, String name) {
        List<Open> prefixed = open.stream().filter(source -> source.prefix() != null && source.source(name).isPresent()).toList();
        List<Open> candidates = prefixed.isEmpty()
                ? open.stream().filter(source -> source.prefix() == null).toList()
                : prefixed;
        return candidates.stream()
                .map(source -> new Origin(source.table(), source.source(name).orElseThrow()))
                .toList();
    }

    private void start(String relation) {
        String table = tables.id().apply(relation);
        if (table == null) {
            return;
        }
        tables.columns().apply(table).forEach(name -> columns.put(name, Set.of(new Origin(table, name))));
        if (!tables.complete().test(table)) {
            open.add(new Open(table, null));
        }
    }

    private void apply(GoldStepSpec spec) {
        switch (spec.operation()) {
            case JOIN -> join(spec.join());
            case UNION -> spec.union().forEach(this::union);
            case DERIVE -> spec.derive().forEach((name, expression) -> put(name, expression(expression)));
            case SELECT -> select(spec.select());
            case RENAME -> rename(spec.rename());
            case DROP -> spec.drop().forEach(name -> key(name).ifPresent(columns::remove));
            case AGGREGATE -> aggregate(spec.aggregate());
            case FILTER, DEDUPE -> {
            }
        }
    }

    private void join(JoinSpec join) {
        String right = tables.id().apply(join.with());
        if (right == null || !join.type().keepsRight()) {
            return;
        }
        Set<String> shared = new LinkedHashSet<>();
        join.on().forEach((left, other) -> {
            if (left.equalsIgnoreCase(join.prefixed(other))) {
                shared.add(left.toLowerCase(Locale.ROOT));
                Origin origin = new Origin(right, other);
                switch (join.type()) {
                    case FULL -> put(left, union(origins(left), Set.of(origin)));
                    case RIGHT -> put(left, Set.of(origin));
                    default -> {
                    }
                }
            }
        });
        for (String name : tables.columns().apply(right)) {
            String renamed = join.prefixed(name);
            if (!shared.contains(renamed.toLowerCase(Locale.ROOT))) {
                columns.put(renamed, Set.of(new Origin(right, name)));
            }
        }
        if (!tables.complete().test(right)) {
            open.add(new Open(right, join.prefix()));
        }
    }

    private void union(String relation) {
        String other = tables.id().apply(relation);
        if (other == null) {
            return;
        }
        for (String name : tables.columns().apply(other)) {
            String key = key(name).orElse(name);
            columns.put(key, union(columns.getOrDefault(key, Set.of()), Set.of(new Origin(other, name))));
        }
        if (!tables.complete().test(other)) {
            columns.replaceAll((name, origins) -> union(origins, Set.of(new Origin(other, name))));
        }
    }

    private void select(List<String> items) {
        Map<String, Set<Origin>> next = new LinkedHashMap<>();
        boolean star = false;
        for (String item : items) {
            Optional<Output> output = output(item);
            if (output.isEmpty()) {
                continue;
            }
            if (output.get().star()) {
                next.putAll(columns);
                star = true;
            } else {
                next.put(output.get().name(), references(output.get().inputs()));
            }
        }
        columns = next;
        if (!star) {
            open = new ArrayList<>();
        }
    }

    private void rename(Map<String, String> names) {
        Map<String, String> lower = new LinkedHashMap<>();
        names.forEach((from, to) -> lower.put(from.toLowerCase(Locale.ROOT), to));
        Map<String, Set<Origin>> next = new LinkedHashMap<>();
        columns.forEach((name, origins) -> next.put(lower.getOrDefault(name.toLowerCase(Locale.ROOT), name), origins));
        columns = next;
    }

    private void aggregate(AggregateSpec aggregate) {
        Map<String, Set<Origin>> next = new LinkedHashMap<>();
        for (String by : aggregate.by()) {
            output(by).ifPresent(output -> next.put(output.name(), references(output.inputs())));
        }
        aggregate.measures().forEach((name, expression) -> next.put(name, expression(expression)));
        columns = next;
        open = new ArrayList<>();
    }

    private Set<Origin> expression(String expression) {
        try {
            return references(SqlReferences.expression(expression));
        } catch (RuntimeException e) {
            warn(e.getMessage());
            return Set.of();
        }
    }

    private Optional<Output> output(String item) {
        try {
            return Optional.of(SqlReferences.item(item));
        } catch (RuntimeException e) {
            warn(e.getMessage());
            return Optional.empty();
        }
    }

    private Set<Origin> references(Collection<Reference> references) {
        Set<Origin> origins = new LinkedHashSet<>();
        references.forEach(reference -> origins.addAll(origins(reference.column())));
        return origins;
    }

    private Set<Origin> origins(String name) {
        Optional<String> key = key(name);
        if (key.isPresent()) {
            return columns.get(key.get());
        }
        List<Origin> found = origins(open, name);
        if (found.size() == 1) {
            return Set.of(found.getFirst());
        }
        warn(found.isEmpty()
                ? "reads '" + name + "', which is not a column there"
                : "reads '" + name + "', which could come from " + found.stream().map(Origin::table).toList());
        return Set.of();
    }

    private void put(String name, Set<Origin> origins) {
        columns.put(key(name).orElse(name), origins);
    }

    private Optional<String> key(String name) {
        return columns.keySet().stream().filter(name::equalsIgnoreCase).findFirst();
    }

    private static Set<Origin> union(Set<Origin> first, Set<Origin> second) {
        Set<Origin> both = new LinkedHashSet<>(first);
        both.addAll(second);
        return both;
    }

    private void warn(String message) {
        warnings.add("gold " + gold + " step " + step + " " + message);
    }

    record Tables(Function<String, String> id, Function<String, Collection<String>> columns, Predicate<String> complete) {}

    record Origin(String table, String column) {}

    record Open(String table, String prefix) {

        Optional<String> source(String name) {
            if (prefix == null) {
                return Optional.of(name);
            }
            boolean prefixed = name.length() > prefix.length()
                    && name.startsWith(prefix)
                    && Character.isUpperCase(name.charAt(prefix.length()));
            return prefixed ? Optional.of(name.substring(prefix.length())) : Optional.empty();
        }
    }

    record Result(Map<String, Set<Origin>> columns, List<Open> open) {}
}
