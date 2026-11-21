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

public final class PipelineLinks {

    private final Map<String, PipelineSpec> plans = new LinkedHashMap<>();
    private final List<String> problems = new ArrayList<>();

    private PipelineLinks(PipelineSpecs specs) {
        specs.all().forEach(plan -> plans.put(plan.dataset(), plan));
    }

    public static List<String> problems(PipelineSpecs specs) {
        PipelineLinks links = new PipelineLinks(specs);
        links.plans.values().forEach(links::checkInputs);
        links.checkLoops();
        return List.copyOf(links.problems);
    }

    public static Map<String, Set<String>> upstream(PipelineSpecs specs) {
        Map<String, Set<String>> edges = new LinkedHashMap<>();
        specs.all().forEach(plan -> edges.put(plan.dataset(), Collections.unmodifiableSet(upstreamOf(plan))));
        return Collections.unmodifiableMap(edges);
    }

    private static Set<String> upstreamOf(PipelineSpec plan) {
        Set<String> datasets = new LinkedHashSet<>();
        plan.inputs().values().stream()
                .map(TableRef::parse)
                .flatMap(Optional::stream)
                .map(TableRef::dataset)
                .filter(dataset -> !dataset.equals(plan.dataset()))
                .forEach(datasets::add);
        return datasets;
    }

    private void checkInputs(PipelineSpec plan) {
        plan.inputs().forEach((alias, text) -> TableRef.parse(text).ifPresent(table -> {
            PipelineSpec owner = plans.get(table.dataset());
            if (owner != null) {
                missing(owner, table).ifPresent(reason -> problems.add(
                        plan.dataset() + " input " + alias + " reads " + table + ", but " + reason));
            }
        }));
    }

    private static Optional<String> missing(PipelineSpec owner, TableRef table) {
        String dataset = owner.dataset();
        return switch (table.kind()) {
            case BRONZE, SILVER, HISTORY, CURRENT ->
                owner.ingests()
                        ? Optional.empty()
                        : Optional.of(dataset + " has no source, so no " + table.kind().segment());
            case QUARANTINE ->
                owner.quarantines()
                        ? Optional.empty()
                        : Optional.of(dataset + " has no drop rule, so no quarantine");
            case GOLD ->
                owner.materialized().stream().anyMatch(gold -> gold.name().equals(table.name()))
                        ? Optional.empty()
                        : Optional.of(dataset + " stores no gold table " + table.name());
            case REFERENCE ->
                owner.references().stream().anyMatch(reference -> reference.name().equals(table.name()))
                        ? Optional.empty()
                        : Optional.of(dataset + " declares no reference " + table.name());
        };
    }

    private void checkLoops() {
        Map<String, Set<String>> edges = new LinkedHashMap<>();
        plans.values().forEach(plan -> edges.put(plan.dataset(), upstreamOf(plan)));
        Set<String> done = new HashSet<>();
        for (String dataset : edges.keySet()) {
            List<String> loop = loop(dataset, edges, done, new ArrayDeque<>());
            if (!loop.isEmpty()) {
                problems.add("pipelines read each other in a loop: " + String.join(" -> ", loop));
                return;
            }
        }
    }

    private static List<String> loop(
            String dataset, Map<String, Set<String>> edges, Set<String> done, Deque<String> path) {
        if (done.contains(dataset)) {
            return List.of();
        }
        if (path.contains(dataset)) {
            List<String> found = new ArrayList<>(path);
            Collections.reverse(found);
            found = new ArrayList<>(found.subList(found.indexOf(dataset), found.size()));
            found.add(dataset);
            return found;
        }
        path.push(dataset);
        for (String upstream : edges.getOrDefault(dataset, Set.of())) {
            if (edges.containsKey(upstream)) {
                List<String> found = loop(upstream, edges, done, path);
                if (!found.isEmpty()) {
                    return found;
                }
            }
        }
        path.pop();
        done.add(dataset);
        return List.of();
    }
}
