package com.dataflow.workspace.runs;

public record EventRun(
        String runId,
        String status,
        String trigger,
        String partition,
        String startedAt,
        String endedAt,
        Long durationMs) {

    EventRun withStatus(String settled) {
        return new EventRun(runId, settled, trigger, partition, startedAt, endedAt, durationMs);
    }
}
