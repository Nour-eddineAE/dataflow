package com.dataflow.workspace.dashboards;

public record Chart(
        String id, String title, ChartKind kind, String x, String measure, String split, String low, String high) {}
