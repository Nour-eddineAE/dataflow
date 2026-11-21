package com.dataflow.workspace.datasets;

public record GraphNode(
        String id, String label, String layer, String flow, String status, Long rows, Long durationMs, String note) {}
