package com.dataflow.engine.core.events;

import java.time.Instant;

public record RunEvent(
        String eventId,
        Instant ts,
        String runId,
        String dataset,
        int planVersion,
        String trigger,
        String partition,
        String eventType,
        String flow,
        String status,
        Instant startedAt,
        Instant endedAt,
        Long rowsIn,
        Long rowsOut,
        Long rowsDropped,
        String details) {
}
