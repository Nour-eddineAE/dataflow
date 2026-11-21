package com.dataflow.engine.core.run;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class FlowGraph {

    private final Map<String, Flow> flows = new LinkedHashMap<>();

    public FlowGraph(List<Flow> flows) {
        for (Flow flow : flows) {
            if (this.flows.putIfAbsent(flow.name(), flow) != null) {
                throw new IllegalArgumentException("Flow " + flow.name() + " is declared twice");
            }
        }
        for (Flow flow : flows) {
            for (String input : flow.inputs()) {
                if (!this.flows.containsKey(input)) {
                    throw new IllegalArgumentException("Flow " + flow.name() + " reads unknown flow " + input);
                }
            }
        }
    }

    public List<Flow> flows() {
        return List.copyOf(flows.values());
    }

    public List<List<Flow>> levels() {
        List<List<Flow>> levels = new ArrayList<>();
        Set<String> placed = new HashSet<>();
        List<Flow> remaining = new ArrayList<>(flows.values());
        while (!remaining.isEmpty()) {
            List<Flow> level = remaining.stream()
                    .filter(flow -> placed.containsAll(flow.inputs()))
                    .toList();
            if (level.isEmpty()) {
                throw new IllegalStateException("Flows form a cycle: "
                        + remaining.stream().map(Flow::name).toList());
            }
            levels.add(level);
            level.forEach(flow -> placed.add(flow.name()));
            remaining.removeAll(level);
        }
        return levels;
    }

    public Set<String> downstreamOf(String flow) {
        Set<String> downstream = new HashSet<>();
        boolean grew = true;
        while (grew) {
            grew = false;
            for (Flow candidate : flows.values()) {
                boolean reads = candidate.inputs().contains(flow)
                        || candidate.inputs().stream().anyMatch(downstream::contains);
                if (reads && downstream.add(candidate.name())) {
                    grew = true;
                }
            }
        }
        return downstream;
    }
}
