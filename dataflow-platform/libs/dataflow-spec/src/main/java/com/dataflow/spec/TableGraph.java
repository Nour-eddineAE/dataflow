package com.dataflow.spec;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class TableGraph {

    private final Map<String, Kind> relations = new LinkedHashMap<>();
    private final Map<String, GoldSpec> tables = new LinkedHashMap<>();
    private final Map<String, Set<String>> reads = new LinkedHashMap<>();
    private final Map<String, String> inputs;

    private TableGraph(PipelineSpec spec) {
        if (spec.ingests()) {
            relations.put(ServeSpec.CURRENT, Kind.CURRENT);
        }
        spec.references().forEach(reference -> relations.putIfAbsent(reference.name(), Kind.REFERENCE));
        spec.inputs().keySet().forEach(alias -> relations.putIfAbsent(alias, Kind.INPUT));
        spec.gold().forEach(table -> {
            relations.putIfAbsent(table.name(), Kind.TABLE);
            tables.putIfAbsent(table.name(), table);
        });
        tables.values().forEach(table -> reads.put(table.name(), Collections.unmodifiableSet(directReads(table))));
        inputs = spec.inputs();
    }

    public static TableGraph of(PipelineSpec spec) {
        return new TableGraph(spec);
    }

    private static Set<String> directReads(GoldSpec table) {
        Set<String> names = new LinkedHashSet<>();
        switch (table.body()) {
            case SQL -> names.addAll(parsed(table.sql()));
            case TRANSFORMATION -> {
                if (table.inputs().isEmpty()) {
                    names.add(ServeSpec.CURRENT);
                }
                table.inputs().values().stream()
                        .filter(source -> source != null && TableRef.parse(source).isEmpty())
                        .map(TableGraph::relationOf)
                        .forEach(names::add);
            }
            case STEPS -> {
                names.add(table.from());
                table.steps().forEach(step -> names.addAll(step.reads()));
            }
            case INVALID -> {}
        }
        return names;
    }

    private static Set<String> parsed(String sql) {
        try {
            return SqlRelations.of(sql);
        } catch (Exception e) {
            return Set.of();
        }
    }

    private static String relationOf(String source) {
        return source.startsWith(StepSpec.REFERENCE) ? source.substring(StepSpec.REFERENCE.length()) : source;
    }

    public Optional<Kind> kind(String relation) {
        return Optional.ofNullable(relations.get(relation));
    }

    public Map<String, Kind> relations() {
        return Collections.unmodifiableMap(relations);
    }

    public Set<String> reads(String table) {
        return reads.getOrDefault(table, Set.of());
    }

    public Set<String> unknown(String table) {
        Set<String> missing = new LinkedHashSet<>(reads(table));
        missing.removeAll(relations.keySet());
        return missing;
    }

    public Optional<TableRef> input(String alias) {
        return Optional.ofNullable(inputs.get(alias)).flatMap(TableRef::parse);
    }

    public List<String> cycle() {
        Map<String, Mark> state = new LinkedHashMap<>();
        Deque<String> path = new ArrayDeque<>();
        for (String table : tables.keySet()) {
            List<String> found = visit(table, state, path);
            if (!found.isEmpty()) {
                return found;
            }
        }
        return List.of();
    }

    private List<String> visit(String table, Map<String, Mark> state, Deque<String> path) {
        Mark seen = state.get(table);
        if (seen == Mark.DONE) {
            return List.of();
        }
        if (seen != null) {
            List<String> loop = new ArrayList<>(path);
            Collections.reverse(loop);
            loop = loop.subList(loop.indexOf(table), loop.size());
            loop.add(table);
            return loop;
        }
        state.put(table, Mark.VISITING);
        path.push(table);
        for (String read : reads(table)) {
            if (tables.containsKey(read)) {
                List<String> found = visit(read, state, path);
                if (!found.isEmpty()) {
                    return found;
                }
            }
        }
        path.pop();
        state.put(table, Mark.DONE);
        return List.of();
    }

    public List<GoldSpec> ordered() {
        List<GoldSpec> order = new ArrayList<>();
        Set<String> placed = new HashSet<>();
        for (String table : tables.keySet()) {
            place(table, placed, order, new HashSet<>());
        }
        return order;
    }

    private void place(String table, Set<String> placed, List<GoldSpec> order, Set<String> visiting) {
        if (placed.contains(table) || !visiting.add(table)) {
            return;
        }
        reads(table).stream().filter(tables::containsKey).forEach(read -> place(read, placed, order, visiting));
        placed.add(table);
        order.add(tables.get(table));
    }

    public Set<String> sources(String table) {
        Set<String> found = new LinkedHashSet<>();
        collectSources(table, found, new HashSet<>());
        return found;
    }

    private void collectSources(String table, Set<String> found, Set<String> visited) {
        if (!visited.add(table)) {
            return;
        }
        for (String read : reads(table)) {
            GoldSpec upstream = tables.get(read);
            if (upstream != null && !upstream.materialize()) {
                collectSources(read, found, visited);
            } else {
                found.add(read);
            }
        }
    }

    public Set<String> readers(String relation) {
        Set<String> found = new LinkedHashSet<>();
        reads.forEach((table, names) -> {
            if (names.contains(relation)) {
                found.add(table);
            }
        });
        return found;
    }

    public enum Kind {
        CURRENT,
        REFERENCE,
        INPUT,
        TABLE
    }

    private enum Mark {
        VISITING,
        DONE
    }
}
