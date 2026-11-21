package com.dataflow.workspace.runs;

import java.time.Instant;

public record Trigger(
        String name,
        Kind kind,
        String dataset,
        String cron,
        String timezone,
        boolean running,
        String lastStatus,
        Instant lastAt,
        String lastMessage,
        Instant nextAt) {

    public enum Kind {
        SCHEDULE,
        INPUTS,
        PLATFORM
    }
}
