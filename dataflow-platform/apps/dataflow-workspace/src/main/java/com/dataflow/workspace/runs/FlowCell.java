package com.dataflow.workspace.runs;

public record FlowCell(
        String runId,
        String flow,
        String status,
        String startedAt,
        Long durationMs,
        Long rowsOut) {}
