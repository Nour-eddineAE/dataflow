package com.dataflow.workspace.runs;

public record FlowEvent(
        String flow,
        String status,
        String startedAt,
        String endedAt,
        Long durationMs,
        Long rowsIn,
        Long rowsOut,
        Long rowsDropped,
        String details) {}
