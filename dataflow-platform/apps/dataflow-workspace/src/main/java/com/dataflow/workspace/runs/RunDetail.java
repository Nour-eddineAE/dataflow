package com.dataflow.workspace.runs;

import java.util.List;

public record RunDetail(
        RunSummary run,
        List<FlowRun> flows,
        List<ExpectationResult> expectations,
        List<LogLine> log,
        String error,
        RunRuntime runtime,
        String sparkApp) {}
