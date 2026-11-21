package com.dataflow.engine.core.run;

import java.time.Instant;

public record FlowOutcome(
        String flow, FlowStatus status, Instant startedAt, Instant endedAt, FlowResult result, String error) {}
