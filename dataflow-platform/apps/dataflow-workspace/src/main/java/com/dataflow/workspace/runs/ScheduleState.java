package com.dataflow.workspace.runs;

import java.time.Instant;

public record ScheduleState(String cron, String timezone, boolean running, Instant nextRun) {}
