package com.dataflow.workspace.runs;

import java.util.Map;

public record FlowRun(
        String flow,
        String status,
        String startedAt,
        String endedAt,
        Long durationMs,
        Long rowsIn,
        Long rowsOut,
        Long rowsDropped,
        Map<String, Object> details) {}
