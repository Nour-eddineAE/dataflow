package com.dataflow.workspace.lineage;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public record Lineage(List<Table> tables, List<Edge> edges, List<String> warnings) {

    public List<Edge> upstream(String table, String column) {
        return walk(table + "|" + column, true);
    }

    public List<Edge> downstream(String table, String column) {
        return walk(table + "|" + column, false);
    }

    private List<Edge> walk(String start, boolean upstream) {
        List<Edge> found = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>(List.of(start));
        while (!queue.isEmpty()) {
            String node = queue.poll();
            if (!seen.add(node)) {
                continue;
            }
            for (Edge edge : edges) {
                if ((upstream ? edge.to() : edge.from()).equals(node)) {
                    found.add(edge);
                    queue.add(upstream ? edge.from() : edge.to());
                }
            }
        }
        return found;
    }

    public record Table(String id, String layer, String label, List<Column> columns) {}

    public record Column(String name, String type, String role, String expression) {}

    public record Edge(String fromTable, String fromColumn, String toTable, String toColumn) {

        String from() {
            return fromTable + "|" + fromColumn;
        }

        String to() {
            return toTable + "|" + toColumn;
        }
    }
}
