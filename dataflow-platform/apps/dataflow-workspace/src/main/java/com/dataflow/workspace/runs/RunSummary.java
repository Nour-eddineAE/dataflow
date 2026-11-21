package com.dataflow.workspace.runs;

import java.time.Instant;

public record RunSummary(
        String runId,
        String dataset,
        String job,
        String status,
        String trigger,
        String partition,
        Instant createdAt,
        Instant startedAt,
        Instant endedAt,
        Long durationMs,
        String task,
        String startedBy) {}
